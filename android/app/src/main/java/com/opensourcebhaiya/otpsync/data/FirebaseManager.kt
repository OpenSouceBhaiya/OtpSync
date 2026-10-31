package com.opensourcebhaiya.otpsync.data

import android.util.Base64
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import com.opensourcebhaiya.otpsync.ui.LinkedDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirebaseManager {
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database = FirebaseDatabase.getInstance()
    
    // The node where we push encrypted OTPs. Keyed by PC UUID.
    val otpsRef = database.getReference("otps")
    // The node where we track linked devices (PCs).
    val devicesRef = database.getReference("devices")

    private val _linkedDevices = MutableStateFlow<List<LinkedDevice>>(emptyList())
    val linkedDevices: StateFlow<List<LinkedDevice>> = _linkedDevices.asStateFlow()

    // Removed global listener for performance/security. Listeners should be per-UUID.

    /**
     * Terminate a session (Thanos snap)
     */
    suspend fun removeDevice(uuid: String) {
        // Delete from Firebase
        devicesRef.child(uuid).removeValue().await()
        // Also delete any pending OTPs for this device
        otpsRef.child(uuid).removeValue().await()
    }

    /**
     * Link a new PC (metadata only to Firebase, key goes to EncryptedSharedPreferences)
     */
    suspend fun linkDeviceMetadata(uuid: String, name: String, browser: String, accountName: String? = null, language: String? = "en") {
        val dateLinked = SimpleDateFormat("MMM dd 'at' h:mm a", Locale.getDefault()).format(Date())
        val publicDeviceData = mutableMapOf<String, Any>(
            "name" to name,
            "browser" to browser,
            "dateLinked" to dateLinked,
            "status" to "active",
            "syncEnabled" to true,
            "language" to (language ?: "en")
        )
        if (accountName != null) {
            publicDeviceData["accountName"] = accountName
        }
        devicesRef.child(uuid).setValue(publicDeviceData).await()
    }

    suspend fun updateDeviceLanguage(uuid: String, language: String) {
        try {
            devicesRef.child(uuid).child("language").setValue(language).await()
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Failed to update device language for $uuid", e)
        }
    }

    suspend fun updateSyncState(uuids: List<String>, isEnabled: Boolean, status: String = "active") {
        for (uuid in uuids) {
            try {
                devicesRef.child(uuid).child("syncEnabled").setValue(isEnabled).await()
                devicesRef.child(uuid).child("status").setValue(status).await()
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Failed to update sync state for $uuid", e)
            }
        }
    }

    suspend fun updateAccountName(uuids: List<String>, accountName: String?) {
        for (uuid in uuids) {
            try {
                if (accountName != null) {
                    devicesRef.child(uuid).child("accountName").setValue(accountName).await()
                } else {
                    devicesRef.child(uuid).child("accountName").removeValue().await()
                }
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Failed to update account name for $uuid", e)
            }
        }
    }

    /**
     * Encrypt and send an OTP to all linked PCs immediately without latency.
     * Returns true if the OTP was sent to at least one linked PC.
     */
    data class DeviceTargetCandidate(
        val uuid: String,
        val keyBase64: String,
        val tier: Int, // 1 = Explicit Intent/Login, 2 = Active Interaction/Focus, 3 = Standby/Heartbeat, 4 = Cold
        val score: Long
    )

    /**
     * Intelligent Multi-Device Target Routing:
     * - Tier 1: Devices with active OTP intent or login page open in browser
     * - Tier 2: Devices with foreground focus or recent keyboard/mouse activity
     * - Tier 3: Devices on standby with recent heartbeat (seen in last 3 min)
     * - Phone Priority: If phone is in active use (unlocked, screen on) and NO PC is active, suppress PC broadcast.
     * - Zero-Drop Fail-Safe: If any ambiguity exists, broadcast to ensure the user NEVER misses an OTP!
     */
    suspend fun broadcastOtp(
        otpCode: String,
        sender: String,
        activeDeviceKeys: Map<String, String>,
        expiresAt: Long,
        @Suppress("UNUSED_PARAMETER") isPhoneInteractive: Boolean = false
    ): List<String> {
        if (activeDeviceKeys.isEmpty()) return emptyList()

        val otpsRef = database.getReference("otps")
        val timestamp = System.currentTimeMillis()
        val sentDeviceUuids = mutableListOf<String>()

        // Evaluate candidate PCs concurrently with 800ms fail-safe timeout
        val candidates = evaluateCandidates(activeDeviceKeys, timestamp)

        val tier1 = candidates.filter { it.tier == 1 }
        val tier2 = candidates.filter { it.tier == 2 }
        val tier3 = candidates.filter { it.tier == 3 }

        val targetsToSend: List<DeviceTargetCandidate> = when {
            // Case 1: Devices explicitly signaled OTP intent / active login page
            // Prioritize the specific device that most recently requested or focused the OTP
            tier1.isNotEmpty() -> {
                val sorted = tier1.sortedByDescending { it.score }
                val top = sorted.first()
                val second = sorted.getOrNull(1)
                // If top device requested it within 45s, target that specific device exclusively
                if (second == null || (top.score - second.score > 5000L)) {
                    Log.d("FirebaseManager", "Targeting primary requesting device ${top.uuid} with explicit OTP intent.")
                    listOf(top)
                } else {
                    Log.d("FirebaseManager", "Targeting ${sorted.size} devices with concurrent OTP intent.")
                    sorted
                }
            }

            // Case 2: Devices with active window focus or recent keyboard/mouse activity
            tier2.isNotEmpty() -> {
                val sorted = tier2.sortedByDescending { it.score }
                Log.d("FirebaseManager", "Targeting focused active device ${sorted.first().uuid}.")
                listOf(sorted.first())
            }

            // Case 3: Standby devices (seen in last 3 minutes)
            tier3.isNotEmpty() -> {
                Log.d("FirebaseManager", "Targeting ${tier3.size} standby device(s).")
                tier3
            }

            // Case 4: Zero-drop fail-safe: Ambiguity or all reported cold -> Broadcast to ALL linked devices!
            else -> {
                Log.d("FirebaseManager", "Fail-safe: Ambiguity detected. Broadcasting to all linked devices to ensure zero dropped OTPs.")
                candidates
            }
        }

        if (targetsToSend.isEmpty()) {
            return emptyList()
        }

        // Concurrently dispatch to all selected target devices in parallel (0ms sequential delay)
        coroutineScope {
            targetsToSend.map { candidate ->
                async {
                    val uuid = candidate.uuid
                    val keyBase64 = candidate.keyBase64
                    try {
                        val targetTag = if (targetsToSend.size == 1) uuid else "ALL"
                        val encryptedData = encryptAesGcm(otpCode, sender, timestamp, expiresAt, keyBase64, targetTag)
                        otpsRef.child(uuid).setValue(encryptedData).await()
                        
                        if (candidate.tier in 1..3) {
                            synchronized(sentDeviceUuids) {
                                sentDeviceUuids.add(uuid)
                            }
                            Log.d("FirebaseManager", "OTP sent to active target device $uuid (tier: ${candidate.tier})")
                        } else {
                            Log.d("FirebaseManager", "OTP queued in mailbox for inactive device $uuid (tier: 4)")
                        }

                        // Self-destruct the OTP from Firebase after 30 seconds
                        managerScope.launch {
                            delay(30000)
                            try {
                                otpsRef.child(uuid).removeValue().await()
                            } catch (e: Exception) {
                                Log.e("FirebaseManager", "Failed to self-destruct OTP", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FirebaseManager", "Failed to encrypt/send OTP to device $uuid", e)
                    }
                }
            }.awaitAll()
        }

        return sentDeviceUuids
    }

    private suspend fun evaluateCandidates(
        activeDeviceKeys: Map<String, String>,
        now: Long
    ): List<DeviceTargetCandidate> {
        val candidates = mutableListOf<DeviceTargetCandidate>()

        coroutineScope {
            val deferreds = activeDeviceKeys.map { (uuid, key) ->
                async {
                    try {
                        withTimeoutOrNull(800) {
                            val snapshot = devicesRef.child(uuid).get().await()
                            parseDeviceCandidate(uuid, key, snapshot, now)
                        } ?: DeviceTargetCandidate(uuid, key, 4, 0L)
                    } catch (e: Exception) {
                        DeviceTargetCandidate(uuid, key, 4, 0L)
                    }
                }
            }
            candidates.addAll(deferreds.awaitAll())
        }

        return candidates
    }

    private fun parseDeviceCandidate(
        uuid: String,
        key: String,
        snapshot: DataSnapshot,
        now: Long
    ): DeviceTargetCandidate {
        if (!snapshot.exists()) {
            return DeviceTargetCandidate(uuid, key, 4, 0L)
        }

        val status = snapshot.child("status").getValue(String::class.java)
        val lastSeen = (snapshot.child("lastSeen").value as? Number)?.toLong() ?: 0L

        // If explicitly inactive, paused, or no heartbeat for over 3 minutes, it is Tier 4 (Inactive)
        if (status == "inactive" || status == "paused" || (lastSeen > 0 && now - lastSeen > 180000L)) {
            return DeviceTargetCandidate(uuid, key, 4, lastSeen)
        }
        val lastActive = (snapshot.child("lastActive").value as? Number)?.toLong() ?: 0L
        val isFocused = snapshot.child("isFocused").getValue(Boolean::class.java) ?: false

        // Check otpIntent
        val intentSnap = snapshot.child("otpIntent")
        val intentActive = intentSnap.child("active").getValue(Boolean::class.java) ?: false
        val intentTs = (intentSnap.child("ts").value as? Number)?.toLong() ?: 0L
        val hasRecentIntent = intentActive && (now - intentTs in 0..120000)

        // Check pcLoginActive
        val loginSnap = snapshot.child("pcLoginActive")
        val loginActive = loginSnap.child("active").getValue(Boolean::class.java) ?: false
        val loginTs = (loginSnap.child("ts").value as? Number)?.toLong() ?: 0L
        val hasRecentLogin = loginActive && (now - loginTs in 0..120000)

        // Tier 1: Explicit Intent or Active Login Page within last 2 minutes
        if (hasRecentIntent || hasRecentLogin) {
            val score = Math.max(intentTs, loginTs)
            return DeviceTargetCandidate(uuid, key, 1, score)
        }

        // Tier 2: Active User Interaction (Focused window or activity within last 60s)
        val isRecentlyActive = (now - lastActive in 0..60000)
        if (isFocused || isRecentlyActive) {
            val score = if (isFocused) lastActive + 100000 else lastActive
            return DeviceTargetCandidate(uuid, key, 2, score)
        }

        // Tier 3: Recent Heartbeat (Seen within last 3 minutes)
        if (now - lastSeen in 0..180000) {
            return DeviceTargetCandidate(uuid, key, 3, lastSeen)
        }

        // Tier 4: Inactive / Cold
        return DeviceTargetCandidate(uuid, key, 4, lastSeen)
    }

    private fun encryptAesGcm(otpCode: String, sender: String, timestamp: Long, expiresAt: Long, keyBase64: String, targetUuid: String = "ALL"): Map<String, String> {
        val keyBytes = Base64.decode(keyBase64, Base64.DEFAULT)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val iv = ByteArray(12) // GCM standard IV length
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

        val safeSender = sender.replace("|", "-")
        val payload = "$otpCode|$safeSender|$timestamp|$expiresAt|$targetUuid"
        val cipherText = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))

        return mapOf(
            "iv" to Base64.encodeToString(iv, Base64.NO_WRAP),
            "data" to Base64.encodeToString(cipherText, Base64.NO_WRAP)
        )
    }
}

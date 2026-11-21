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
        val tier: Int, // 1 = Explicit Intent/Login/Domain Match, 2 = Active Interaction/Focus, 3 = Standby/Heartbeat, 4 = Cold
        val score: Long,
        val lastActive: Long = 0L,
        val lastSeen: Long = 0L,
        val hasRequestedOtp: Boolean = false,
        val hasDomainMatch: Boolean = false,
        val isSystemActive: Boolean = true,
        val matchedToken: String = ""
    )

    /**
     * Intelligent Multi-Device Target Routing:
     * - Tier 1: Devices with domain/brand match or active OTP intent/login page open
     * - Tier 2: Devices with foreground focus or recent keyboard/mouse activity (within 3 min)
     * - Tier 3: Devices on standby with recent heartbeat (seen in last 5 min)
     * - Phone Priority: If all PCs are idle/asleep and NO PC requested the OTP, keep OTP strictly on phone.
     */
    private val shorthandMap = mapOf(
        "airtl" to "airtel",
        "icicib" to "icici",
        "hdfcbk" to "hdfc",
        "sbinb" to "sbi",
        "axisbk" to "axis",
        "kotakb" to "kotak",
        "pnb" to "pnb",
        "bob" to "bob",
        "idbi" to "idbi",
        "amzn" to "amazon",
        "flpkrt" to "flipkart",
        "swgy" to "swiggy",
        "zmto" to "zomato",
        "pytm" to "paytm",
        "myntra" to "myntra",
        "tatasky" to "tataplay",
        "tmango" to "tataplay",
        "tata" to "tataplay",
        "jio" to "jio",
        "cred" to "cred",
        "zerodh" to "zerodha",
        "groww" to "groww",
        "upstox" to "upstox",
        "uber" to "uber",
        "ola" to "ola",
        "irctc" to "irctc"
    )

    private fun extractBrandTokens(sender: String, body: String): Set<String> {
        val tokens = mutableSetOf<String>()

        // 1. Clean sender: "AD-AIRTEL" -> "airtel", "VK-ICICI" -> "icici", "AX-TMANGO-S" -> "tmango", "tataplay", "578021"
        val cleanSender = sender.lowercase().replace(Regex("[^a-z0-9]"), " ")
        for (part in cleanSender.split(" ")) {
            if (part.length >= 3 && part !in setOf("sms", "otp", "code", "app", "txt", "msg", "verif", "alert", "notice", "info")) {
                tokens.add(part)
                shorthandMap[part]?.let { tokens.add(it) }
                // Handle common prefixes like 't' for Tata (e.g. tmango -> mango, tataplay)
                if (part.startsWith("t") && part.length >= 5) {
                    val stripped = part.substring(1)
                    tokens.add(stripped)
                    shorthandMap[stripped]?.let { tokens.add(it) }
                }
            }
        }

        // 2. Extract words and domains from body
        val cleanBody = body.lowercase()
        // Extract URLs or domains in body (e.g. airtelxstream.in, amazon.in)
        val urlMatches = Regex("([a-z0-9-]+\\.[a-z]{2,})").findAll(cleanBody)
        for (m in urlMatches) {
            val fullHost = m.value
            val root = fullHost.split(".").firstOrNull() ?: fullHost
            tokens.add(fullHost)
            if (root.length >= 3) tokens.add(root)
        }

        val words = cleanBody.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
        val ignore = setOf("your", "this", "code", "verification", "one", "time", "password", "valid", "minutes", "seconds", "please", "enter", "with", "from", "share", "anyone", "call", "bank", "dear", "customer", "account", "login", "portal", "device", "is", "for", "use", "never", "confidential")
        for (w in words) {
            if (w !in ignore && w.length >= 3) {
                tokens.add(w)
                shorthandMap[w]?.let { tokens.add(it) }
            }
        }
        return tokens
    }

    suspend fun broadcastOtp(
        otpCode: String,
        sender: String,
        activeDeviceKeys: Map<String, String>,
        expiresAt: Long,
        isPhoneInteractive: Boolean = false,
        messageBody: String = ""
    ): List<String> {
        if (activeDeviceKeys.isEmpty()) return emptyList()

        val otpsRef = database.getReference("otps")
        val timestamp = System.currentTimeMillis()
        val sentDeviceUuids = mutableListOf<String>()

        val brandTokens = extractBrandTokens(sender, messageBody)

        // Evaluate candidate PCs concurrently with 2500ms fail-safe timeout
        val candidates = evaluateCandidates(activeDeviceKeys, timestamp, brandTokens)

        // 1. Identify connected PCs that actually requested an OTP or are on a matching login page
        val requestingDevices = candidates.filter { it.hasRequestedOtp }

        val targetsToSend = if (requestingDevices.isNotEmpty()) {
            // Priority 1: Domain/Brand Match (e.g. airtelxstream.in matches Airtel SMS)
            // Priority 2: Highest score / most recent action timestamp
            val primaryTarget = requestingDevices.sortedWith(
                compareByDescending<DeviceTargetCandidate> { it.hasDomainMatch }
                    .thenByDescending { it.score }
                    .thenByDescending { it.lastActive }
            ).first()

            Log.d("FirebaseManager", "Targeting requesting PC ${primaryTarget.uuid} (domainMatch: ${primaryTarget.hasDomainMatch}, matchedToken: '${primaryTarget.matchedToken}', score: ${primaryTarget.score}). Zero leakage to other laptops.")
            listOf(primaryTarget)
        } else {
            // NO device had an explicit domain match or active OTP intent/login page.
            // Check if user is actively working on a single PC or has only 1 PC connected
            val activePcs = candidates.filter { it.tier <= 2 && it.isSystemActive }
            if (activePcs.size == 1) {
                val activePc = activePcs.first()
                Log.d("FirebaseManager", "User is actively working on PC ${activePc.uuid} (tier ${activePc.tier}). Routing OTP directly to active PC.")
                listOf(activePc)
            } else if (candidates.size == 1 && candidates.first().isSystemActive && (timestamp - candidates.first().lastSeen in 0..600_000)) {
                val singlePc = candidates.first()
                Log.d("FirebaseManager", "Single linked PC is online (${singlePc.uuid}). Routing OTP to user's PC.")
                listOf(singlePc)
            } else {
                Log.d("FirebaseManager", "No PC requested an OTP and no single active PC. Keeping OTP strictly on phone.")
                emptyList()
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
                        
                        synchronized(sentDeviceUuids) {
                            sentDeviceUuids.add(uuid)
                        }
                        Log.d("FirebaseManager", "OTP sent to active target device $uuid (tier: ${candidate.tier})")

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
        now: Long,
        brandTokens: Set<String> = emptySet()
    ): List<DeviceTargetCandidate> {
        val candidates = mutableListOf<DeviceTargetCandidate>()

        coroutineScope {
            val deferreds = activeDeviceKeys.map { (uuid, key) ->
                async {
                    try {
                        withTimeoutOrNull(2500) {
                            val snapshot = devicesRef.child(uuid).get().await()
                            parseDeviceCandidate(uuid, key, snapshot, now, brandTokens)
                        } ?: DeviceTargetCandidate(uuid, key, 4, 0L, 0L, 0L, isSystemActive = false)
                    } catch (e: Exception) {
                        DeviceTargetCandidate(uuid, key, 4, 0L, 0L, 0L, isSystemActive = false)
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
        now: Long,
        brandTokens: Set<String> = emptySet()
    ): DeviceTargetCandidate {
        if (!snapshot.exists()) {
            return DeviceTargetCandidate(uuid, key, 4, 0L, 0L, 0L, isSystemActive = false)
        }

        val status = snapshot.child("status").getValue(String::class.java)
        if (status == "terminated" || status == "paused" || status == "inactive") {
            return DeviceTargetCandidate(uuid, key, 4, 0L, 0L, 0L, isSystemActive = false)
        }

        val isIdle = snapshot.child("isIdle").getValue(Boolean::class.java) ?: false
        val systemState = snapshot.child("systemState").getValue(String::class.java) ?: "active"
        val isDeviceSystemIdle = isIdle || systemState == "idle" || systemState == "locked"

        val lastSeen = (snapshot.child("lastSeen").value as? Number)?.toLong() ?: 0L
        val lastActive = (snapshot.child("lastActive").value as? Number)?.toLong() ?: 0L

        // Active domain & open tabs reported by extension
        val activeDomain = (snapshot.child("activeDomain").getValue(String::class.java) ?: "").lowercase()
        val activeTitle = (snapshot.child("activeTitle").getValue(String::class.java) ?: "").lowercase()
        val openDomains = snapshot.child("openDomains").children.mapNotNull { it.getValue(String::class.java)?.lowercase() }

        // Check otpIntent (within last 5 minutes)
        val intentSnap = snapshot.child("otpIntent")
        val intentActive = intentSnap.child("active").getValue(Boolean::class.java) ?: false
        val intentTs = (intentSnap.child("ts").value as? Number)?.toLong() ?: 0L
        val intentHost = (intentSnap.child("host").getValue(String::class.java) ?: "").lowercase()
        val intentTitle = (intentSnap.child("title").getValue(String::class.java) ?: "").lowercase()
        val hasRecentIntent = intentActive && (now - intentTs in 0..300_000)

        // Check pcLoginActive (within last 5 minutes)
        val loginSnap = snapshot.child("pcLoginActive")
        val loginActive = loginSnap.child("active").getValue(Boolean::class.java) ?: false
        val loginTs = (loginSnap.child("ts").value as? Number)?.toLong() ?: 0L
        val loginHost = (loginSnap.child("host").getValue(String::class.java) ?: "").lowercase()
        val loginTitle = (loginSnap.child("title").getValue(String::class.java) ?: "").lowercase()
        val hasRecentLogin = loginActive && (now - loginTs in 0..300_000)

        // Aggregate all domains associated with this PC
        val allPcHosts = mutableSetOf<String>()
        if (activeDomain.isNotBlank()) allPcHosts.add(activeDomain)
        if (intentHost.isNotBlank()) allPcHosts.add(intentHost)
        if (loginHost.isNotBlank()) allPcHosts.add(loginHost)
        allPcHosts.addAll(openDomains)

        // Aggregate all titles associated with this PC
        val allPcTitles = mutableSetOf<String>()
        if (activeTitle.isNotBlank()) allPcTitles.add(activeTitle)
        if (intentTitle.isNotBlank()) allPcTitles.add(intentTitle)
        if (loginTitle.isNotBlank()) allPcTitles.add(loginTitle)

        var matchedToken = ""
        var hasDomainMatch = false
        if (brandTokens.isNotEmpty()) {
            for (token in brandTokens) {
                val hostMatched = allPcHosts.any { host -> host.contains(token) || token.contains(host.split(".").firstOrNull() ?: "") }
                val titleMatched = allPcTitles.any { title -> title.contains(token) }
                if (hostMatched || titleMatched) {
                    hasDomainMatch = true
                    matchedToken = token
                    break
                }
            }
        }

        val domainBonus = if (hasDomainMatch) 50_000_000_000L else 0L

        // Device qualifies as having requested the OTP if:
        // 1. It has a domain/brand match with the SMS, OR
        // 2. User clicked Send/Resend OTP, focused an input, or triggered an intent (within 5 min), OR
        // 3. User is actively on a login/auth page (within 5 min)
        val hasRequestedOtp = hasDomainMatch || hasRecentIntent || hasRecentLogin

        // Tier 1: Explicit Intent, Domain Match, or Active Login Page
        if (hasDomainMatch || hasRecentIntent || hasRecentLogin) {
            val score = Math.max(intentTs, loginTs) + domainBonus + (if (hasDomainMatch) 10_000_000_000L else 0L)
            return DeviceTargetCandidate(uuid, key, 1, score, lastActive, lastSeen, true, hasDomainMatch, !isDeviceSystemIdle, matchedToken)
        }

        // Tier 2: Active User Interaction (Focused window or activity within last 3 minutes)
        val isRecentlyActive = (now - lastActive in 0..180_000) && !isDeviceSystemIdle
        if (isRecentlyActive) {
            val score = 1_000_000_000L + lastActive
            return DeviceTargetCandidate(uuid, key, 2, score, lastActive, lastSeen, false, false, true)
        }

        // Tier 3: Recent Heartbeat / Online Standby (Seen in last 5 minutes)
        val isOnline = (now - lastSeen in 0..300_000) && !isDeviceSystemIdle
        if (isOnline) {
            return DeviceTargetCandidate(uuid, key, 3, lastActive, lastActive, lastSeen, false, false, true)
        }

        // Tier 4: Inactive / Cold
        return DeviceTargetCandidate(uuid, key, 4, 0L, lastActive, lastSeen, false, false, false)
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

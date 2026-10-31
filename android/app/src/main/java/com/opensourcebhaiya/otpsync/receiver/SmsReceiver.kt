package com.opensourcebhaiya.otpsync.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.opensourcebhaiya.otpsync.data.AppDatabase
import com.opensourcebhaiya.otpsync.data.FirebaseManager
import com.opensourcebhaiya.otpsync.data.OtpEntity
import com.opensourcebhaiya.otpsync.data.OtpExtractor
import com.opensourcebhaiya.otpsync.data.SettingsManager
import com.opensourcebhaiya.otpsync.utils.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val settingsManager = SettingsManager(context)
        FileLogger.log(context, "SmsReceiver.onReceive: SMS broadcast received! isSyncEnabled=${settingsManager.isSyncEnabled()}")

        if (!settingsManager.isSyncEnabled()) {
            FileLogger.log(context, "SmsReceiver: Sync engine is disabled in settings. Skipping SMS extraction.")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            FileLogger.log(context, "SmsReceiver: Intent returned empty or null SMS message bundle.")
            return
        }

        // Group messages by sender in case of multi-part SMS
        val senderToMessage = mutableMapOf<String, StringBuilder>()
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: "Unknown"
            val body = sms.displayMessageBody ?: sms.messageBody ?: continue
            senderToMessage.getOrPut(sender) { StringBuilder() }.append(body)
        }

        val scope = CoroutineScope(Dispatchers.IO)

        for ((sender, bodyBuilder) in senderToMessage) {
            val fullText = bodyBuilder.toString()
            FileLogger.log(context, "SMS Received - Sender: $sender, Text: $fullText")

            val extractedOtp = try {
                OtpExtractor.extractOtp(
                    context = context,
                    subject = sender,
                    bodyText = fullText,
                    bodyHtml = null,
                    receivedTimeMs = System.currentTimeMillis()
                )
            } catch (t: Throwable) {
                FileLogger.log(context, "SmsReceiver: Error extracting OTP from SMS: ${t.message}")
                Log.e("SmsReceiver", "Error extracting OTP from SMS", t)
                null
            }

            if (extractedOtp != null) {
                val otpCode = extractedOtp.code
                FileLogger.log(context, "Success! Extracted SMS OTP: $otpCode from $sender")
                Log.d("SmsReceiver", "Found OTP: $otpCode from $sender")

                val status = com.opensourcebhaiya.otpsync.utils.OtpCache.recordSmsArrival(otpCode, sender)
                if (status == com.opensourcebhaiya.otpsync.utils.OtpArrivalStatus.SAME_SMS_DUPLICATE) {
                    FileLogger.log(context, "SMS duplicate burst for $otpCode suppressed.")
                    return
                }
                val isDualArrival = (status == com.opensourcebhaiya.otpsync.utils.OtpArrivalStatus.SIMULTANEOUS_BURST)
                if (isDualArrival) {
                    FileLogger.log(context, "Dual arrival OTP $otpCode already captured via email. Merging SMS channel into database.")
                    com.opensourcebhaiya.otpsync.utils.ToastManager.show(context, "Same OTP also arrived on SMS", android.widget.Toast.LENGTH_SHORT)
                }
                val isResentDuplicate = (status == com.opensourcebhaiya.otpsync.utils.OtpArrivalStatus.RESENT_SAME_CODE)
                if (isResentDuplicate) {
                    FileLogger.log(context, "Re-sent identical SMS OTP $otpCode from $sender detected. Re-copying and broadcasting.")
                    Log.d("SmsReceiver", "Re-sent identical SMS OTP $otpCode from $sender detected.")
                }

                // 1. INSTANT Clipboard Copy (Sub-millisecond execution, zero DB blocking!)
                if (settingsManager.isClipboardCopyEnabled()) {
                    try {
                        val clipIntent = Intent(context, com.opensourcebhaiya.otpsync.ui.TransparentClipboardActivity::class.java).apply {
                            putExtra("EXTRA_OTP_CODE", otpCode)
                            putExtra("EXTRA_IS_DUPLICATE", isResentDuplicate)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        }
                        context.startActivity(clipIntent)
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Clipboard failed", e)
                    }
                }

                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val db = AppDatabase.getDatabase(context)

                        // 1. INSTANT Room Database Insertion & Channel Merge (0ms latency, runs FIRST before network)
                        AppDatabase.insertMutex.withLock {
                            val existing = db.otpDao().getOtpByCodeRecent(otpCode, System.currentTimeMillis() - 6 * 60 * 1000L)
                            if (existing == null) {
                                db.otpDao().insertOtp(OtpEntity(
                                    id = UUID.randomUUID().toString(),
                                    code = otpCode,
                                    sender = sender,
                                    subject = sender,
                                    account = "SMS",
                                    receivedAt = System.currentTimeMillis(),
                                    expiresAt = extractedOtp.expiresAt ?: (System.currentTimeMillis() + 5 * 60 * 1000L),
                                    sourcePackage = "sms",
                                    sentToDevices = null
                                ))
                            } else {
                                val mergedAccount = if (!existing.account.contains("SMS")) "${existing.account} + SMS" else existing.account
                                val updated = existing.copy(
                                    account = mergedAccount,
                                    receivedAt = System.currentTimeMillis()
                                )
                                db.otpDao().updateOtp(updated)
                            }
                        }

                        // 2. INSTANT Firebase Cloud Broadcast (Parallel, non-blocking)
                        val firebaseManager = FirebaseManager()
                        val currentTime = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                        val allKeys = settingsManager.getAllLinkedDeviceKeys()
                        for (deviceId in allKeys.keys) {
                            settingsManager.updateLinkedDeviceLastOtpTime(deviceId, currentTime)
                        }

                        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                        val isScreenOn = powerManager?.isInteractive ?: false
                        val isLocked = keyguardManager?.isKeyguardLocked ?: false
                        val isPhoneInUse = isScreenOn && !isLocked

                        val sentDeviceUuids = firebaseManager.broadcastOtp(
                            otpCode = otpCode,
                            sender = sender,
                            activeDeviceKeys = allKeys,
                            expiresAt = extractedOtp.expiresAt ?: (System.currentTimeMillis() + 5 * 60 * 1000L),
                            isPhoneInteractive = isPhoneInUse
                        )

                        val sentDeviceNames = if (sentDeviceUuids.isNotEmpty()) {
                            sentDeviceUuids.map { settingsManager.getLinkedDeviceName(it) }.distinct()
                        } else {
                            emptyList()
                        }
                        val sentToDevicesString = if (sentDeviceNames.isNotEmpty()) {
                            sentDeviceNames.joinToString(", ")
                        } else {
                            null
                        }

                        if (sentToDevicesString != null) {
                            AppDatabase.insertMutex.withLock {
                                val current = db.otpDao().getOtpByCodeRecent(otpCode, System.currentTimeMillis() - 6 * 60 * 1000L)
                                if (current != null) {
                                    db.otpDao().updateOtp(current.copy(sentToDevices = sentToDevicesString))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SmsReceiver", "Error saving SMS OTP", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else {
                FileLogger.log(context, "Failed: No OTP found in SMS from $sender text")
            }
        }
    }
}

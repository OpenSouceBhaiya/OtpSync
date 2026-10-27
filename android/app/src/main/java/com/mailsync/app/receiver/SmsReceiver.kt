package com.mailsync.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.mailsync.app.data.AppDatabase
import com.mailsync.app.data.FirebaseManager
import com.mailsync.app.data.OtpEntity
import com.mailsync.app.data.OtpExtractor
import com.mailsync.app.data.SettingsManager
import com.mailsync.app.utils.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val settingsManager = SettingsManager(context)
        if (!settingsManager.isSyncEnabled()) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Group messages by sender in case of multi-part SMS
        val senderToMessage = mutableMapOf<String, StringBuilder>()
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: "Unknown"
            val body = sms.displayMessageBody ?: continue
            senderToMessage.getOrPut(sender) { StringBuilder() }.append(body)
        }

        val scope = CoroutineScope(Dispatchers.IO)

        for ((sender, bodyBuilder) in senderToMessage) {
            val fullText = bodyBuilder.toString()
            FileLogger.log(context, "SMS Received - Sender: $sender, Text length: ${fullText.length}")

            val extractedOtp = OtpExtractor.extractOtp(
                context = context,
                subject = sender,
                bodyText = fullText,
                bodyHtml = null,
                receivedTimeMs = System.currentTimeMillis()
            )

            if (extractedOtp != null) {
                val otpCode = extractedOtp.code
                FileLogger.log(context, "Success! Extracted SMS OTP: $otpCode from $sender")
                Log.d("SmsReceiver", "Found OTP: $otpCode from $sender")

                val status = com.mailsync.app.utils.OtpCache.checkStatus(otpCode, sender, "SMS")
                if (status == com.mailsync.app.utils.OtpArrivalStatus.SIMULTANEOUS_BURST) {
                    FileLogger.log(context, "Simultaneous burst SMS OTP $otpCode from $sender suppressed (arrived concurrently).")
                    Log.d("SmsReceiver", "Simultaneous burst SMS OTP $otpCode from $sender suppressed.")
                    
                    val pendingResult = goAsync()
                    scope.launch {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            AppDatabase.insertMutex.withLock {
                                val existing = db.otpDao().getOtpByCodeRecent(otpCode, System.currentTimeMillis() - 3 * 60 * 1000L)
                                if (existing != null) {
                                    val mergedAccount = if (!existing.account.contains("SMS")) "${existing.account} + SMS" else existing.account
                                    val updated = existing.copy(account = mergedAccount, receivedAt = System.currentTimeMillis())
                                    db.otpDao().updateOtp(updated)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "Error merging SMS OTP", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                    continue
                }

                val isResentDuplicate = (status == com.mailsync.app.utils.OtpArrivalStatus.RESENT_SAME_CODE)
                if (isResentDuplicate) {
                    FileLogger.log(context, "Re-sent identical SMS OTP $otpCode from $sender detected. Re-copying and broadcasting.")
                    Log.d("SmsReceiver", "Re-sent identical SMS OTP $otpCode from $sender detected.")
                }

                // 1. INSTANT Clipboard Copy (Sub-millisecond execution, zero DB blocking!)
                if (settingsManager.isClipboardCopyEnabled()) {
                    try {
                        val clipIntent = Intent(context, com.mailsync.app.ui.TransparentClipboardActivity::class.java).apply {
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
                        // 2. INSTANT Firebase Cloud Broadcast
                        val firebaseManager = FirebaseManager()
                        val currentTime = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                        val allKeys = settingsManager.getAllLinkedDeviceKeys()
                        for (deviceId in allKeys.keys) {
                            settingsManager.updateLinkedDeviceLastOtpTime(deviceId, currentTime)
                        }

                        firebaseManager.broadcastOtp(
                            otpCode = otpCode,
                            sender = sender,
                            activeDeviceKeys = allKeys,
                            expiresAt = extractedOtp.expiresAt ?: (System.currentTimeMillis() + 5 * 60 * 1000L)
                        )

                        // 3. ASYNC Room Database Insertion (Never delays user clipboard/cloud broadcast)
                        val db = AppDatabase.getDatabase(context)
                        AppDatabase.insertMutex.withLock {
                            var existing = db.otpDao().getOtpByCodeRecent(otpCode, System.currentTimeMillis() - 6 * 60 * 1000L)
                            if (existing == null) {
                                db.otpDao().insertOtp(OtpEntity(
                                    id = UUID.randomUUID().toString(),
                                    code = otpCode,
                                    sender = sender,
                                    subject = sender,
                                    account = "SMS",
                                    receivedAt = System.currentTimeMillis(),
                                    expiresAt = extractedOtp.expiresAt ?: (System.currentTimeMillis() + 5 * 60 * 1000L),
                                    sourcePackage = "sms"
                                ))
                            } else {
                                existing.receivedAt = System.currentTimeMillis()
                                db.otpDao().updateOtp(existing)
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

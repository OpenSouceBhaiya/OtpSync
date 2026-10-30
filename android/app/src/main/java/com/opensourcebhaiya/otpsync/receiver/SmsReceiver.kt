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
                if (status == com.opensourcebhaiya.otpsync.utils.OtpArrivalStatus.SIMULTANEOUS_BURST) {
                    FileLogger.log(context, "Dual arrival OTP $otpCode already captured via email.")
                    com.opensourcebhaiya.otpsync.utils.ToastManager.show(context, "Same OTP also arrived on SMS", android.widget.Toast.LENGTH_SHORT)
                    return
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
                        // 2. INSTANT Firebase Cloud Broadcast
                        val firebaseManager = FirebaseManager()
                        val currentTime = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                        val allKeys = settingsManager.getAllLinkedDeviceKeys()
                        for (deviceId in allKeys.keys) {
                            settingsManager.updateLinkedDeviceLastOtpTime(deviceId, currentTime)
                        }

                        val sentDeviceUuids = firebaseManager.broadcastOtp(
                            otpCode = otpCode,
                            sender = sender,
                            activeDeviceKeys = allKeys,
                            expiresAt = extractedOtp.expiresAt ?: (System.currentTimeMillis() + 5 * 60 * 1000L)
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
                                    sourcePackage = "sms",
                                    sentToDevices = sentToDevicesString
                                ))
                            } else {
                                existing.receivedAt = System.currentTimeMillis()
                                if (sentToDevicesString != null) {
                                    existing.sentToDevices = sentToDevicesString
                                }
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

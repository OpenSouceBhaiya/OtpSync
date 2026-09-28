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
                subject = sender,
                bodyText = fullText,
                bodyHtml = null,
                receivedTimeMs = System.currentTimeMillis()
            )

            if (extractedOtp != null) {
                FileLogger.log(context, "Success! Extracted SMS OTP: ${extractedOtp.code} from $sender")
                Log.d("SmsReceiver", "Found OTP: ${extractedOtp.code} from $sender")

                scope.launch {
                    val db = AppDatabase.getDatabase(context)
                    var isNewInsertion = false
                    AppDatabase.insertMutex.withLock {
                        val existing = db.otpDao().getOtpByCodeRecent(extractedOtp.code, System.currentTimeMillis() - 6 * 60 * 1000L)
                        if (existing == null) {
                            isNewInsertion = true
                            db.otpDao().insertOtp(OtpEntity(
                                id = UUID.randomUUID().toString(),
                                code = extractedOtp.code,
                                sender = sender,
                                subject = sender,
                                account = "SMS",
                                receivedAt = System.currentTimeMillis(),
                                expiresAt = extractedOtp.expiresAt,
                                sourcePackage = "sms"
                            ))
                        }
                    }

                    if (isNewInsertion) {
                        val firebaseManager = FirebaseManager()
                        val currentTime = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())

                        val allKeys = settingsManager.getAllLinkedDeviceKeys()
                        for (deviceId in allKeys.keys) {
                            settingsManager.updateLinkedDeviceLastOtpTime(deviceId, currentTime)
                        }

                        firebaseManager.broadcastOtp(
                            otpCode = extractedOtp.code,
                            sender = sender,
                            activeDeviceKeys = allKeys,
                            expiresAt = extractedOtp.expiresAt ?: 0L
                        )

                        if (settingsManager.isClipboardCopyEnabled() && com.mailsync.app.utils.OtpCache.shouldCopy(extractedOtp.code)) {
                            try {
                                val clipIntent = Intent(context, com.mailsync.app.ui.TransparentClipboardActivity::class.java).apply {
                                    putExtra("EXTRA_OTP_CODE", extractedOtp.code)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                }
                                context.startActivity(clipIntent)
                            } catch (e: Exception) { Log.e("SmsReceiver", "Clipboard failed", e) }
                        }
                    }
                }
            } else {
                FileLogger.log(context, "Failed: No OTP found in SMS from $sender text")
            }
        }
    }
}

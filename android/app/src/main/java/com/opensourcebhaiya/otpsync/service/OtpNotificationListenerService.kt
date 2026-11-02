package com.opensourcebhaiya.otpsync.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.opensourcebhaiya.otpsync.data.OtpExtractor
import com.opensourcebhaiya.otpsync.data.SettingsManager
import com.opensourcebhaiya.otpsync.data.AppDatabase
import com.opensourcebhaiya.otpsync.data.OtpEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class OtpNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var settingsManager: SettingsManager

    private var connectedListener: com.google.firebase.database.ValueEventListener? = null
    private var smsObserver: android.database.ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        Log.d("OtpNotification", "Notification Listener Service Created")
        registerSmsObserver()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("OtpNotification", "Notification Listener Connected")
        registerSmsObserver()
        connectedListener = com.google.firebase.database.FirebaseDatabase.getInstance().getReference(".info/connected")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (connected) {
                        val uuids = settingsManager.getLinkedDevicesMetadata().map { it.id }
                        if (uuids.isNotEmpty()) {
                            val enabled = settingsManager.isSyncEnabled()
                            val status = if (enabled) "active" else "paused"
                            scope.launch {
                                com.opensourcebhaiya.otpsync.data.FirebaseManager().updateSyncState(uuids, enabled, status)
                            }
                        }
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connectedListener?.let {
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference(".info/connected").removeEventListener(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterSmsObserver()
        Log.d("OtpNotification", "Notification Listener Service Destroyed")
    }

    private fun registerSmsObserver() {
        if (smsObserver != null) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                    super.onChange(selfChange, uri)
                    if (!settingsManager.isSyncEnabled()) return
                    checkLatestSmsInbox()
                }
            }
            contentResolver.registerContentObserver(
                android.provider.Telephony.Sms.CONTENT_URI,
                true,
                observer
            )
            smsObserver = observer
            com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "ContentObserver registered on SMS inbox successfully.")
        } catch (e: Exception) {
            Log.e("OtpNotification", "Failed to register SMS ContentObserver", e)
        }
    }

    private fun unregisterSmsObserver() {
        smsObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {}
            smsObserver = null
        }
    }

    private fun checkLatestSmsInbox() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        scope.launch {
            try {
                val cursor = contentResolver.query(
                    android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf("address", "body", "date"),
                    null,
                    null,
                    "date DESC LIMIT 3"
                )
                cursor?.use { c ->
                    val now = System.currentTimeMillis()
                    while (c.moveToNext()) {
                        val address = c.getString(c.getColumnIndexOrThrow("address")) ?: ""
                        val body = c.getString(c.getColumnIndexOrThrow("body")) ?: ""
                        val date = c.getLong(c.getColumnIndexOrThrow("date"))
                        if (kotlin.math.abs(now - date) < 300_000L) { // Within last 5 minutes (handles clock skew)
                            val result = OtpExtractor.extractOtp(this@OtpNotificationListenerService, address, body, null, date)
                            if (result != null) {
                                val otpCode = result.code
                                val status = com.opensourcebhaiya.otpsync.utils.OtpCache.recordSmsArrival(otpCode, address)
                                if (status == com.opensourcebhaiya.otpsync.utils.OtpArrivalStatus.SAME_SMS_DUPLICATE) {
                                    // Already processed via SmsReceiver or notification
                                    return@launch
                                }
                                com.opensourcebhaiya.otpsync.utils.FileLogger.log(this@OtpNotificationListenerService, "ContentObserver extracted SMS OTP: $otpCode from $address")
                                handleExtractedOtp(result, address, "SMS", "sms_inbox")
                                return@launch
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OtpNotification", "Error checking SMS inbox from ContentObserver", e)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (smsObserver == null) {
            registerSmsObserver()
        }
        if (sbn == null) return
        val packageName = sbn.packageName ?: return

        val systemPackages = setOf("android", "com.android.systemui", "com.android.settings",
            "com.android.packageinstaller", applicationContext.packageName)
        if (packageName in systemPackages) return

        if (!settingsManager.isSyncEnabled()) {
            com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "onNotificationPosted: Pkg $packageName skipped because sync engine is disabled in settings.")
            return
        }
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        val conversationTitle = extras.getCharSequence("android.conversationTitle")?.toString() ?: ""
        
        val messagingTexts = extractMessagingTexts(sbn.notification)
        val textLinesList = extractTextLines(extras)

        val allTextBuilder = StringBuilder()
        
        // Append standard fields first to maintain order
        val ticker = sbn.notification.tickerText?.toString() ?: ""
        if (ticker.isNotBlank()) allTextBuilder.append(ticker).append("\n")
        if (title.isNotBlank()) allTextBuilder.append(title).append("\n")
        if (text.isNotBlank()) allTextBuilder.append(text).append("\n")
        if (bigText.isNotBlank() && bigText != text) allTextBuilder.append(bigText).append("\n")
        for (m in messagingTexts) {
            allTextBuilder.append(m).append("\n")
        }
        for (l in textLinesList) {
            allTextBuilder.append(l).append("\n")
        }
        if (subText.isNotBlank() && subText != title) allTextBuilder.append(subText).append("\n")
        if (summaryText.isNotBlank() && summaryText != title) allTextBuilder.append(summaryText).append("\n")
        if (conversationTitle.isNotBlank() && conversationTitle != title) allTextBuilder.append(conversationTitle).append("\n")
        
        // Loop through all other extras to catch hidden fields used by verified businesses / RCS
        try {
            val handledKeys = setOf(
                Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG,
                Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_MESSAGES, "android.messages",
                Notification.EXTRA_HISTORIC_MESSAGES, "android.messages.historic",
                Notification.EXTRA_TEXT_LINES, "android.textLines",
                Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT,
                "android.conversationTitle"
            )
            for (key in extras.keySet()) {
                if (key in handledKeys) continue
                val value = extras.get(key)
                when (value) {
                    is CharSequence -> {
                        val strValue = value.toString()
                        if (strValue.isNotBlank()) allTextBuilder.append(strValue).append("\n")
                    }
                    is Array<*> -> {
                        for (item in value) {
                            if (item is CharSequence && item.isNotBlank()) {
                                allTextBuilder.append(item.toString()).append("\n")
                            }
                        }
                    }
                    is Iterable<*> -> {
                        for (item in value) {
                            if (item is CharSequence && item.isNotBlank()) {
                                allTextBuilder.append(item.toString()).append("\n")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val actions = sbn.notification.actions
        val actionTitles = mutableListOf<String>()
        if (actions != null) {
            for (action in actions) {
                val actionTitle = action.title?.toString()
                if (!actionTitle.isNullOrBlank()) {
                    actionTitles.add(actionTitle)
                    allTextBuilder.append(actionTitle).append("\n")
                }
            }
        }

        val fullText = allTextBuilder.toString().trim()
        if (fullText.isBlank() && title.isBlank()) return
        
        val isTruecaller = packageName.contains("truecaller")
        val isSmsApp = !isTruecaller && (packageName.contains("messaging") || packageName.contains("mms") || packageName.contains(".sms") || packageName.contains("telephony") || packageName.contains("message"))
        com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "Notification Received - Pkg: $packageName, Title: $title, Text length: ${fullText.length}, MessagingCount: ${messagingTexts.size}, ActionsCount: ${actionTitles.size}")
        
        if (fullText.contains("Sensitive notification content hidden") || title.contains("Sensitive notification content hidden")) {
            com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "REDACTION DETECTED! OS blocked OTP extraction (Android 15+ restricted). Pkg: $packageName")
            if (isSmsApp) {
                trySmsInboxFallback(packageName, delayMs = 1000)
            }
            return // Skip further extraction because the text is redacted anyway!
        }

        var resolvedSender = title.takeIf { it.isNotBlank() } ?: packageName

        // 1. Check clean, unpolluted message fields first
        var extractedOtp: com.opensourcebhaiya.otpsync.data.ExtractionResult? = null

        // 1a. Check MessagingStyle chat messages (newest message first!) - Handles Google Messages & WhatsApp
        if (messagingTexts.isNotEmpty()) {
            for (mText in messagingTexts.asReversed()) {
                val res = OtpExtractor.extractOtp(this, resolvedSender, mText, null, System.currentTimeMillis())
                if (res != null) {
                    extractedOtp = res
                    com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "Found OTP in messagingText: ${res.code}")
                    break
                }
            }
        }

        // 1b. Check bigText & text
        if (extractedOtp == null && bigText.isNotBlank()) {
            extractedOtp = OtpExtractor.extractOtp(this, resolvedSender, bigText, null, System.currentTimeMillis())
            if (extractedOtp != null) {
                com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "Found OTP in bigText: ${extractedOtp.code}")
            }
        }
        if (extractedOtp == null && text.isNotBlank() && text != bigText) {
            extractedOtp = OtpExtractor.extractOtp(this, resolvedSender, text, null, System.currentTimeMillis())
            if (extractedOtp != null) {
                com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "Found OTP in text: ${extractedOtp.code}")
            }
        }

        // 2. Check individual lines (handles Gmail, Outlook, grouped summaries)
        if (extractedOtp == null && textLinesList.isNotEmpty()) {
            for (lineStr in textLinesList.asReversed()) {
                val res = OtpExtractor.extractOtp(this, resolvedSender, lineStr, null, System.currentTimeMillis())
                if (res != null) {
                    extractedOtp = res
                    val lineSender = extractSenderFromLine(lineStr)
                    if (!lineSender.isNullOrBlank()) {
                        resolvedSender = lineSender
                    }
                    com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "Found OTP in grouped line: ${res.code} (Resolved Sender: $resolvedSender)")
                    break
                }
            }
        }

        // 3. Check notification quick-actions (e.g. "Copy 888618", "Copy OTP 888618")
        if (extractedOtp == null && actionTitles.isNotEmpty()) {
            for (aTitle in actionTitles) {
                val res = OtpExtractor.extractOtp(this, resolvedSender, aTitle, null, System.currentTimeMillis())
                if (res != null) {
                    extractedOtp = res
                    com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "Found OTP in action button: ${res.code} (\"$aTitle\")")
                    break
                }
            }
        }

        // 4. Fallback to fullText evaluated as a whole
        if (extractedOtp == null && fullText.isNotBlank()) {
            extractedOtp = OtpExtractor.extractOtp(this, resolvedSender, fullText, null, System.currentTimeMillis())
        }

        val appName = resolveSourceLabel(packageName)

        // Resolve clean company sender if title is a generic group counter (e.g. "2 new messages", "3 unread emails")
        if (isGenericGroupTitle(resolvedSender)) {
            val betterSender = conversationTitle.takeIf { it.isNotBlank() && !isGenericGroupTitle(it) }
                ?: subText.takeIf { it.isNotBlank() && !isGenericGroupTitle(it) }
                ?: appName
            resolvedSender = betterSender
        }

        val finalAccountName = when {
            isTruecaller -> "Truecaller"
            isSmsApp -> "SMS"
            packageName.contains("com.google.android.gm") -> {
                if (subText.isNotBlank() && subText.contains("@")) "$subText (Gmail)" else "Gmail"
            }
            subText.isNotBlank() -> "$subText ($appName)"
            else -> appName
        }

        if (extractedOtp != null) {
            Log.d("OtpNotification", "Found OTP: ${extractedOtp.code} from $packageName (Sender: $resolvedSender)")
            com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "[NOTIF_SUCCESS] Extracted OTP: ${extractedOtp.code} from $packageName (Sender: $resolvedSender, Channel: $finalAccountName)")
            handleExtractedOtp(extractedOtp, resolvedSender, finalAccountName, packageName)
        } else {
            val snippet = fullText.take(150).replace("\n", " ")
            com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "[NOTIF_FAIL] No OTP recognized in notification from $packageName. Snippet: \"$snippet\"")
            if (isSmsApp) {
                trySmsInboxFallback(packageName, delayMs = 500)
            }
        }
    }

    private fun trySmsInboxFallback(sourcePkg: String, delayMs: Long = 500) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return
        }
        com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "Attempting SMS inbox fallback extraction for $sourcePkg (delay ${delayMs}ms)...")
        scope.launch {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            try {
                val cursor = contentResolver.query(
                    android.net.Uri.parse("content://sms/inbox"),
                    arrayOf("address", "body", "date"),
                    null,
                    null,
                    "date DESC LIMIT 5"
                )
                cursor?.use { c ->
                    val currentT = System.currentTimeMillis()
                    while (c.moveToNext()) {
                        val address = c.getString(c.getColumnIndexOrThrow("address")) ?: ""
                        val body = c.getString(c.getColumnIndexOrThrow("body")) ?: ""
                        val date = c.getLong(c.getColumnIndexOrThrow("date"))
                        
                        com.opensourcebhaiya.otpsync.utils.FileLogger.log(this@OtpNotificationListenerService, "Fallback check: $address - ${currentT - date}ms ago")
                        
                        if (kotlin.math.abs(currentT - date) < 300000L) { // Consider SMS received in the last 5 minutes (resilient to carrier clock skew)
                            val fallbackOtp = OtpExtractor.extractOtp(this@OtpNotificationListenerService, address, body, null, date)
                            if (fallbackOtp != null) {
                                com.opensourcebhaiya.otpsync.utils.FileLogger.log(this@OtpNotificationListenerService, "Success! Instant fallback extracted SMS OTP: ${fallbackOtp.code} from $address")
                                handleExtractedOtp(fallbackOtp, address, "SMS", sourcePkg)
                                return@launch
                            }
                        }
                    }
                    com.opensourcebhaiya.otpsync.utils.FileLogger.log(this@OtpNotificationListenerService, "Fallback check finished without finding OTP.")
                }
            } catch (e: Exception) {
                com.opensourcebhaiya.otpsync.utils.FileLogger.log(this@OtpNotificationListenerService, "Instant fallback failed: ${e.message}")
            }
        }
    }

    private fun handleExtractedOtp(extractedOtp: com.opensourcebhaiya.otpsync.data.ExtractionResult, senderName: String, finalAccountName: String, packageName: String) {
        val otpCode = extractedOtp.code
        val status = com.opensourcebhaiya.otpsync.utils.OtpCache.checkNotificationArrival(otpCode, senderName, packageName, finalAccountName)

        if (status == com.opensourcebhaiya.otpsync.utils.OtpArrivalStatus.SAME_SMS_DUPLICATE) {
            com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "SMS app notification for already-captured SMS OTP $otpCode suppressed.")
            Log.d("OtpNotification", "SMS app notification for already-captured SMS OTP $otpCode suppressed.")
            return
        }

        if (status == com.opensourcebhaiya.otpsync.utils.OtpArrivalStatus.SIMULTANEOUS_BURST) {
            com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "Dual arrival OTP $otpCode from $finalAccountName merged.")
            Log.d("OtpNotification", "Dual arrival OTP $otpCode from $finalAccountName merged.")
            
            val allSources = com.opensourcebhaiya.otpsync.utils.OtpCache.getSourcesForCode(otpCode)
            val channelText = if (allSources.size > 1) allSources.joinToString(" + ") else finalAccountName
            com.opensourcebhaiya.otpsync.utils.ToastManager.show(this, "Same OTP also arrived on $channelText", android.widget.Toast.LENGTH_SHORT)

            // Update Room Database to merge account labels (e.g. "SMS" -> "SMS + Truecaller + Gmail")
            scope.launch {
                try {
                    val db = AppDatabase.getDatabase(this@OtpNotificationListenerService)
                    AppDatabase.insertMutex.withLock {
                        val existing = db.otpDao().getOtpByCodeRecent(otpCode, System.currentTimeMillis() - 10 * 60 * 1000L)
                        if (existing != null) {
                            val existingParts = existing.account.split(" + ").map { it.trim() }.toMutableList()
                            val cleanNewSource = when {
                                finalAccountName.contains("truecaller", ignoreCase = true) -> "Truecaller"
                                finalAccountName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                                finalAccountName.contains("telegram", ignoreCase = true) -> "Telegram"
                                finalAccountName.contains("gmail", ignoreCase = true) -> "Gmail"
                                finalAccountName.contains("sms", ignoreCase = true) -> "SMS"
                                else -> finalAccountName
                            }
                            if (cleanNewSource !in existingParts) {
                                existingParts.add(cleanNewSource)
                            }
                            val mergedAccount = existingParts.distinct().joinToString(" + ")
                            val updated = existing.copy(account = mergedAccount, receivedAt = System.currentTimeMillis())
                            db.otpDao().updateOtp(updated)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OtpNotification", "Database merge failed", e)
                }
            }
            return
        }

        val isResentDuplicate = (status == com.opensourcebhaiya.otpsync.utils.OtpArrivalStatus.RESENT_SAME_CODE)
        if (isResentDuplicate) {
            com.opensourcebhaiya.otpsync.utils.FileLogger.log(this, "Re-sent identical OTP $otpCode from $senderName detected. Re-copying and broadcasting.")
            Log.d("OtpNotification", "Re-sent identical OTP $otpCode from $senderName detected.")
        }

        // 1. INSTANT Clipboard Copy (Sub-millisecond execution, zero DB delay!)
        if (settingsManager.isClipboardCopyEnabled()) {
            try {
                val intent = android.content.Intent(this@OtpNotificationListenerService, com.opensourcebhaiya.otpsync.ui.TransparentClipboardActivity::class.java).apply {
                    putExtra("EXTRA_OTP_CODE", otpCode)
                    putExtra("EXTRA_IS_DUPLICATE", isResentDuplicate)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("OtpNotification", "Clipboard activity launch failed", e)
            }
        }

        // 2. INSTANT Firebase Cloud Broadcast & Room Database Insertion
        scope.launch {
            try {
                val firebaseManager = com.opensourcebhaiya.otpsync.data.FirebaseManager()
                val keys = settingsManager.getAllLinkedDeviceKeys()
                val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                val keyguardManager = getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                val isScreenOn = powerManager?.isInteractive ?: false
                val isLocked = keyguardManager?.isKeyguardLocked ?: false
                val isPhoneInUse = isScreenOn && !isLocked

                val defaultExpiry = System.currentTimeMillis() + 10 * 60 * 1000L
                val validExpiry = extractedOtp.expiresAt?.takeIf { it > System.currentTimeMillis() + 30_000L } ?: defaultExpiry

                val sentDeviceUuids = firebaseManager.broadcastOtp(
                    otpCode = otpCode,
                    sender = senderName,
                    activeDeviceKeys = keys,
                    expiresAt = validExpiry,
                    isPhoneInteractive = isPhoneInUse
                )

                val sentDeviceNames = if (sentDeviceUuids.isNotEmpty()) {
                    val currentTime = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                    for (deviceId in sentDeviceUuids) {
                        settingsManager.updateLinkedDeviceLastOtpTime(deviceId, currentTime)
                    }
                    sentDeviceUuids.map { settingsManager.getLinkedDeviceName(it) }.distinct()
                } else {
                    emptyList()
                }
                val sentToDevicesString = if (sentDeviceNames.isNotEmpty()) {
                    sentDeviceNames.joinToString(", ")
                } else {
                    null
                }

                // 3. ASYNC Room Database Insertion
                val db = AppDatabase.getDatabase(this@OtpNotificationListenerService)
                AppDatabase.insertMutex.withLock {
                    var existing = db.otpDao().getOtpByCodeRecent(otpCode, System.currentTimeMillis() - 10 * 60 * 1000L)
                    if (existing == null) {
                        db.otpDao().insertOtp(OtpEntity(
                            id = UUID.randomUUID().toString(),
                            code = otpCode,
                            sender = senderName,
                            subject = senderName,
                            account = finalAccountName,
                            receivedAt = System.currentTimeMillis(),
                            expiresAt = validExpiry,
                            sourcePackage = packageName,
                            sentToDevices = sentToDevicesString
                        ))
                    } else {
                        existing.receivedAt = System.currentTimeMillis()
                        val existingParts = existing.account.split(" + ").map { it.trim() }.toMutableList()
                        val cleanSource = when {
                            finalAccountName.contains("truecaller", ignoreCase = true) -> "Truecaller"
                            finalAccountName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                            finalAccountName.contains("telegram", ignoreCase = true) -> "Telegram"
                            finalAccountName.contains("gmail", ignoreCase = true) -> "Gmail"
                            finalAccountName.contains("sms", ignoreCase = true) -> "SMS"
                            else -> finalAccountName
                        }
                        if (cleanSource !in existingParts) {
                            existingParts.add(cleanSource)
                        }
                        existing.sentToDevices = sentToDevicesString ?: existing.sentToDevices
                        db.otpDao().updateOtp(existing.copy(account = existingParts.distinct().joinToString(" + ")))
                    }
                }
            } catch (e: Exception) {
                Log.e("OtpNotification", "Firebase broadcast or DB insert failed", e)
            }
        }
    }

    private fun resolveSourceLabel(pkg: String): String = when {
        pkg.contains("truecaller") -> "Truecaller"
        pkg.contains("whatsapp") -> "WhatsApp"
        pkg.contains("telegram") -> "Telegram"
        pkg.contains("com.google.android.gm") -> "Gmail"
        pkg.contains("mms") || pkg.contains("messaging") || pkg.contains(".sms") -> "SMS"
        pkg.contains("jio") -> "Jio SMS"
        pkg.contains("paytm") -> "Paytm"
        pkg.contains("phonepe") -> "PhonePe"
        pkg.contains("gpay") || pkg.contains("tez") -> "Google Pay"
        pkg.contains("amazon") -> "Amazon"
        pkg.contains("hdfc") -> "HDFC Bank"
        pkg.contains("icici") -> "ICICI Bank"
        pkg.contains("sbi") -> "SBI"
        pkg.contains("axis") -> "Axis Bank"
        pkg.contains("kotak") -> "Kotak Bank"
        pkg.contains("airtel") -> "Airtel"
        pkg.contains("vi.") || pkg.contains("vodafone") -> "Vi"
        else -> try {
            val info = applicationContext.packageManager.getApplicationInfo(pkg, 0)
            applicationContext.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) { pkg.substringAfterLast(".") }
    }

    private fun isGenericGroupTitle(title: String): Boolean {
        val t = title.trim().lowercase()
        if (t.matches(Regex("^\\d+\\s+(?:new|unread)\\s+(?:messages?|emails?|mails?).*"))) return true
        if (t.matches(Regex("^\\d+\\s+(?:nuevos?|novas?)\\s+(?:mensajes?|mensagens?).*"))) return true
        if (t.matches(Regex("^\\d+\\s+nouvelles?\\s+(?:messages?|e-mails?).*"))) return true
        if (t.matches(Regex("^\\d+\\s+neue\\s+nachrichten.*"))) return true
        if (t.matches(Regex("^\\d+\\s+नए\\s+संदेश.*"))) return true
        if (t in listOf("gmail", "messages", "inbox", "mail", "outlook", "email", "sms")) return true
        return false
    }

    private fun extractSenderFromLine(line: String): String? {
        val tabSplit = line.split('\t')
        if (tabSplit.size > 1 && tabSplit[0].isNotBlank()) {
            return tabSplit[0].trim()
        }
        val spaceSplit = line.split(Regex("\\s{2,}"))
        if (spaceSplit.size > 1 && spaceSplit[0].isNotBlank()) {
            return spaceSplit[0].trim()
        }
        val colonSplit = line.split(':')
        if (colonSplit.size > 1 && colonSplit[0].isNotBlank() && colonSplit[0].length < 40) {
            return colonSplit[0].trim()
        }
        return null
    }

    private fun extractMessagingTexts(notification: Notification): List<String> {
        val list = mutableListOf<String>()

        // 1. AndroidX NotificationCompat.MessagingStyle
        try {
            val compatStyle = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            if (compatStyle != null) {
                for (msg in compatStyle.messages) {
                    val t = msg.text?.toString()
                    if (!t.isNullOrBlank()) list.add(t)
                }
                for (msg in compatStyle.historicMessages) {
                    val t = msg.text?.toString()
                    if (!t.isNullOrBlank()) list.add(t)
                }
            }
        } catch (_: Exception) {}

        // 2. Framework extras (Notification.EXTRA_MESSAGES & Notification.EXTRA_HISTORIC_MESSAGES)
        val extras = notification.extras ?: return list
        val keys = listOf(
            Notification.EXTRA_MESSAGES,
            "android.messages",
            Notification.EXTRA_HISTORIC_MESSAGES,
            "android.messages.historic"
        )

        for (k in keys) {
            val raw = extras.get(k) ?: continue
            val bundles = mutableListOf<android.os.Bundle>()
            when (raw) {
                is Array<*> -> {
                    for (item in raw) {
                        if (item is android.os.Bundle) bundles.add(item)
                    }
                }
                is Iterable<*> -> {
                    for (item in raw) {
                        if (item is android.os.Bundle) bundles.add(item)
                    }
                }
            }

            // Framework Notification.MessagingStyle.Message (API 24+)
            if (bundles.isNotEmpty() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                try {
                    val frameworkMessages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles.toTypedArray())
                    for (m in frameworkMessages) {
                        val t = m.text?.toString()
                        if (!t.isNullOrBlank()) list.add(t)
                    }
                } catch (_: Exception) {}
            }

            // Direct Bundle inspection
            for (b in bundles) {
                val directText = b.getCharSequence("text")?.toString()
                    ?: b.getCharSequence("android.text")?.toString()
                    ?: b.getCharSequence("body")?.toString()
                    ?: b.getCharSequence("message")?.toString()
                if (!directText.isNullOrBlank()) {
                    list.add(directText)
                }
                for (subKey in b.keySet()) {
                    if (subKey in listOf("text", "android.text", "body", "message", "time", "sender", "type", "uri")) continue
                    val v = b.get(subKey)
                    if (v is CharSequence && v.isNotBlank()) {
                        list.add(v.toString())
                    }
                }
            }
        }

        return list.distinct()
    }

    private fun extractTextLines(extras: android.os.Bundle?): List<String> {
        if (extras == null) return emptyList()
        val list = mutableListOf<String>()
        val raw = extras.get(Notification.EXTRA_TEXT_LINES) ?: extras.get("android.textLines")
        when (raw) {
            is Array<*> -> {
                for (item in raw) {
                    if (item is CharSequence && item.isNotBlank()) list.add(item.toString())
                }
            }
            is Iterable<*> -> {
                for (item in raw) {
                    if (item is CharSequence && item.isNotBlank()) list.add(item.toString())
                }
            }
        }
        return list
    }
}


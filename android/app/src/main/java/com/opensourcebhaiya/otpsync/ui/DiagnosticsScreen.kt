package com.opensourcebhaiya.otpsync.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.opensourcebhaiya.otpsync.BuildConfig
import com.opensourcebhaiya.otpsync.data.ExtractionResult
import com.opensourcebhaiya.otpsync.data.OtpExtractor
import com.opensourcebhaiya.otpsync.data.SettingsManager
import com.opensourcebhaiya.otpsync.ui.theme.*
import com.opensourcebhaiya.otpsync.utils.FileLogger
import com.opensourcebhaiya.otpsync.utils.ToastManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Real-time log state
    var rawLogs by remember { mutableStateOf(FileLogger.getAllLogs(context)) }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, SMS, NOTIF, ERROR
    var autoRefresh by remember { mutableStateOf(true) }

    // Test Extractor state
    var testInputText by remember { mutableStateOf("") }
    var testResultExtraction by remember { mutableStateOf<ExtractionResult?>(null) }
    var testResultHasRun by remember { mutableStateOf(false) }

    // System Permissions State
    var isNotificationAccessGranted by remember { mutableStateOf(false) }
    var isReadSmsGranted by remember { mutableStateOf(false) }
    var isReceiveSmsGranted by remember { mutableStateOf(false) }
    var isBatteryOptIgnored by remember { mutableStateOf(false) }
    var canDrawOverlays by remember { mutableStateOf(false) }
    val settingsManager = remember { SettingsManager(context) }
    val isSyncEnabled = settingsManager.isSyncEnabled()

    fun refreshSystemStatus() {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        isNotificationAccessGranted = flat?.contains(context.packageName) == true

        isReadSmsGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        isReceiveSmsGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isBatteryOptIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        rawLogs = FileLogger.getAllLogs(context)
    }

    LaunchedEffect(Unit) {
        refreshSystemStatus()
        while (true) {
            delay(2000)
            if (autoRefresh) {
                rawLogs = FileLogger.getAllLogs(context)
            }
        }
    }

    fun copyLogsToClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val recentLogs = if (rawLogs.length > 80_000) {
                "[... Earlier logs truncated for clipboard size limits (${rawLogs.length / 1024} KB total). Use 'Export Logs' for complete file ...]\n\n" + rawLogs.takeLast(80_000)
            } else {
                rawLogs
            }
            val sysSummary = buildString {
                appendLine("=== OTP SYNC DIAGNOSTIC REPORT ===")
                appendLine("App Package: ${context.packageName}")
                appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})")
                appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Sync Engine: ${if (isSyncEnabled) "ACTIVE" else "DISABLED"}")
                appendLine("Notification Listener: ${if (isNotificationAccessGranted) "CONNECTED" else "ACCESS MISSING"}")
                if (BuildConfig.SUPPORTS_DIRECT_SMS) {
                    appendLine("READ_SMS Permission: ${if (isReadSmsGranted) "GRANTED" else "PERMISSION DENIED"}")
                    appendLine("RECEIVE_SMS Permission: ${if (isReceiveSmsGranted) "GRANTED" else "PERMISSION DENIED"}")
                } else {
                    appendLine("SMS Extraction Mode: NOTIFICATION LISTENER (Play Store Compliant)")
                }
                appendLine("Overlay Permission: ${if (canDrawOverlays) "GRANTED" else "DENIED"}")
                appendLine("Battery Optimization Ignored: $isBatteryOptIgnored")
                appendLine("==================================")
                appendLine()
                appendLine(recentLogs)
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("OTP Sync Diagnostics", sysSummary))
            ToastManager.showCopyToast(context, "Diagnostic report copied to clipboard")
        } catch (e: Exception) {
            ToastManager.show(context, "Clipboard error: ${e.message}. Use 'Export Logs' button.", android.widget.Toast.LENGTH_LONG)
        }
    }

    fun runExtractionTest() {
        if (testInputText.isBlank()) {
            ToastManager.show(context, "Please paste or type text to test", android.widget.Toast.LENGTH_SHORT)
            return
        }
        val startTime = System.currentTimeMillis()
        FileLogger.log(context, "[MANUAL_TEST] Triggered extraction test on: \"${testInputText.take(120)}\"")
        val res = OtpExtractor.extractOtp(context, "MANUAL-TEST", testInputText, null, startTime)
        testResultExtraction = res
        testResultHasRun = true
        rawLogs = FileLogger.getAllLogs(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Engine Diagnostics",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Live Subsystem Status & Debug Terminal",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { copyLogsToClipboard() }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report", tint = PurpleAccent)
                    }
                    IconButton(onClick = { FileLogger.exportLog(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export Log File", tint = PurpleAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ─── Device & Build Info Header ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF26194E), Color(0xFF140D2B))
                        )
                    )
                    .border(1.dp, Color(0xFF7B61FF).copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(PurpleAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · v${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR.replaceFirstChar { it.uppercase() }})",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            softWrap = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ─── 1. Subsystem Health Status ──────────────────────────────────────────
            Text(
                text = "SUBSYSTEM HEALTH & PERMISSIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = PurpleAccent,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                StatusRow(
                    label = "Sync Engine",
                    description = "Master engine controlling background sync & PC forwarding",
                    statusText = if (isSyncEnabled) "ACTIVE" else "PAUSED",
                    isGood = isSyncEnabled,
                    onClick = {
                        settingsManager.setSyncEnabled(!isSyncEnabled)
                        refreshSystemStatus()
                    }
                )
                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 10.dp))
                StatusRow(
                    label = "Notification Listener",
                    description = "Captures OTP notifications from banking, carrier, and messaging apps",
                    statusText = if (isNotificationAccessGranted) "CONNECTED" else "ACCESS MISSING",
                    isGood = isNotificationAccessGranted,
                    onClick = {
                        try {
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        } catch (_: Exception) {}
                    }
                )
                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 10.dp))

                if (BuildConfig.SUPPORTS_DIRECT_SMS) {
                    StatusRow(
                        label = "SMS Extraction (READ_SMS)",
                        description = "Direct telephony SMS extraction for 1-second instant delivery",
                        statusText = if (isReadSmsGranted) "GRANTED" else "PERMISSION DENIED",
                        isGood = isReadSmsGranted,
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                } else {
                    StatusRow(
                        label = "SMS Extraction Mode",
                        description = "Google Play edition: Captures OTP notifications without sensitive SMS permissions",
                        statusText = "NOTIFICATION LISTENER",
                        isGood = true,
                        onClick = {
                            ToastManager.show(context, "Google Play compliant mode active", android.widget.Toast.LENGTH_SHORT)
                        }
                    )
                }

                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 10.dp))
                StatusRow(
                    label = "Display Over Other Apps",
                    description = "Enables auto-copying OTP to clipboard when screen is locked or in other apps",
                    statusText = if (canDrawOverlays) "GRANTED" else "DENIED",
                    isGood = canDrawOverlays,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                            } catch (_: Exception) {}
                        }
                    }
                )
                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 10.dp))
                StatusRow(
                    label = "Battery Optimization",
                    description = "Prevents OEM aggressive battery managers from killing the listener",
                    statusText = if (isBatteryOptIgnored) "UNRESTRICTED" else "OPTIMIZED (May Delay)",
                    isGood = isBatteryOptIgnored,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (_: Exception) {}
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── 2. Interactive SMS Extractor Tester ─────────────────────────────────
            Text(
                text = "INTERACTIVE OTP EXTRACTOR TESTER",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = PurpleAccent,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Paste any incoming SMS or notification text in any of the 19 supported languages to test real-time regex & scoring logic:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp,
                    softWrap = true
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = testInputText,
                    onValueChange = { 
                        testInputText = it 
                        testResultHasRun = false
                    },
                    placeholder = { 
                        Text(
                            "e.g. 482910 is your OTP for Internet Lifestyle Hub...", 
                            color = TextSecondary.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        ) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    trailingIcon = {
                        if (testInputText.isNotBlank()) {
                            IconButton(onClick = { 
                                testInputText = ""
                                testResultExtraction = null
                                testResultHasRun = false
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear input", tint = TextSecondary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PurpleAccent,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        cursorColor = PurpleAccent
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = cb.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                testInputText = clip.getItemAt(0).text?.toString() ?: ""
                                testResultHasRun = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste Clip", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { runExtractionTest() },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analyze OTP", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (testResultHasRun) {
                    Spacer(modifier = Modifier.height(14.dp))
                    val res = testResultExtraction
                    if (res != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SuccessGreen.copy(alpha = 0.12f))
                                .border(1.dp, SuccessGreen.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle, 
                                            contentDescription = null, 
                                            tint = SuccessGreen, 
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "OTP Detected Successfully",
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cb.setPrimaryClip(ClipData.newPlainText("OTP", res.code))
                                            ToastManager.showCopyToast(context, "Copied ${res.code}")
                                        },
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = SuccessGreen)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0D1B14))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = res.code,
                                        color = Color.White,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 2.sp
                                    )
                                    Text(
                                        text = "${res.code.length} digits",
                                        color = SuccessGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                val expiryText = res.expiresAt?.let {
                                    val remainingSecs = (it - System.currentTimeMillis()) / 1000
                                    if (remainingSecs > 0) "Expires in ${remainingSecs / 60} min" else "Expiry timestamp detected"
                                } ?: "No expiry limit specified in message"

                                Text(
                                    text = "• $expiryText",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ErrorRed.copy(alpha = 0.12f))
                                .border(1.dp, ErrorRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Cancel, 
                                        contentDescription = null, 
                                        tint = ErrorRed, 
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "No Genuine OTP Accepted",
                                        color = ErrorRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "The engine analyzed the snippet but found no genuine OTP candidate. Inspect the live log terminal below for detailed candidate scoring and validator checks.",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    softWrap = true
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── 3. Live Log Terminal ────────────────────────────────────────────────
            val infiniteTransition = rememberInfiniteTransition(label = "terminal_pulse")
            val liveAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "live_alpha"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LIVE ENGINE LOGS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PurpleAccent,
                        letterSpacing = 1.sp
                    )
                    if (autoRefresh) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen.copy(alpha = liveAlpha))
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (autoRefresh) "Auto-Refresh ON" else "Auto-Refresh OFF",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = autoRefresh,
                        onCheckedChange = { autoRefresh = it },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Filter lines
            val allLogLines = remember(rawLogs) { rawLogs.lines().filter { it.isNotBlank() } }
            val smsCount = remember(allLogLines) { allLogLines.count { it.contains("sms", ignoreCase = true) || it.contains("telephony", ignoreCase = true) } }
            val notifCount = remember(allLogLines) { allLogLines.count { it.contains("notification", ignoreCase = true) } }
            val errorCount = remember(allLogLines) { allLogLines.count { it.contains("error", ignoreCase = true) || it.contains("fail", ignoreCase = true) || it.contains("crash", ignoreCase = true) || it.contains("rejected", ignoreCase = true) } }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${allLogLines.size})", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "SMS",
                    onClick = { selectedFilter = "SMS" },
                    label = { Text("SMS ($smsCount)", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "NOTIF",
                    onClick = { selectedFilter = "NOTIF" },
                    label = { Text("Notifications ($notifCount)", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "ERROR",
                    onClick = { selectedFilter = "ERROR" },
                    label = { Text("Errors / Fails ($errorCount)", fontSize = 12.sp) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            val filteredLines = remember(allLogLines, selectedFilter) {
                when (selectedFilter) {
                    "SMS" -> allLogLines.filter { it.contains("sms", ignoreCase = true) || it.contains("telephony", ignoreCase = true) }
                    "NOTIF" -> allLogLines.filter { it.contains("notification", ignoreCase = true) }
                    "ERROR" -> allLogLines.filter { it.contains("error", ignoreCase = true) || it.contains("fail", ignoreCase = true) || it.contains("crash", ignoreCase = true) || it.contains("rejected", ignoreCase = true) }
                    else -> allLogLines
                }
            }

            // Log Console Box with Terminal Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF090A0F))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            ) {
                // Terminal window topbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141620))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF27C93F)))
                    }
                    Text(
                        text = "otpsync_engine.log (${filteredLines.size} records)",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 460.dp)
                        .padding(12.dp)
                ) {
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (filteredLines.isEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Outlined.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No log records found for '$selectedFilter'",
                                            color = TextSecondary,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Trigger an SMS, notification, or use the tester above to see live events.",
                                            color = TextSecondary.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            softWrap = true,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            } else {
                                items(filteredLines) { line ->
                                    val lineColor = when {
                                        line.contains("SUCCESS", ignoreCase = true) || line.contains("Phase 1 Anchor Matched", ignoreCase = true) -> SuccessGreen
                                        line.contains("FAILED", ignoreCase = true) || line.contains("ERROR", ignoreCase = true) || line.contains("CRASH", ignoreCase = true) -> ErrorRed
                                        line.contains("SMS", ignoreCase = true) -> Color(0xFF60A5FA) // Blue
                                        line.contains("Notification", ignoreCase = true) -> Color(0xFFFBBF24) // Yellow
                                        line.contains("rejected", ignoreCase = true) -> Color(0xFFFF9800) // Orange
                                        else -> Color(0xFFD1D5DB)
                                    }
                                    Text(
                                        text = line,
                                        color = lineColor,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 16.sp,
                                        softWrap = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─── 4. Action Buttons ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { copyLogsToClipboard() },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Logs", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { FileLogger.exportLog(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Logs", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    FileLogger.clearLog(context)
                    rawLogs = FileLogger.getAllLogs(context)
                    ToastManager.show(context, "Log history cleared", android.widget.Toast.LENGTH_SHORT)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = ErrorRed)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear Engine Log Terminal", color = ErrorRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ─── 5. Privacy & Security Guarantee Card ────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(PurpleAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Diagnostic Privacy & Security Guarantee",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All event entries and diagnostic logs reside strictly in your phone's memory and encrypted local storage. They are never transmitted over the internet or shared with external servers unless you manually tap 'Export Logs' or 'Copy Logs'.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            softWrap = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    description: String,
    statusText: String,
    isGood: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                softWrap = true
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isGood) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f))
                .border(1.dp, if (isGood) SuccessGreen.copy(alpha = 0.4f) else ErrorRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = statusText,
                color = if (isGood) SuccessGreen else ErrorRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

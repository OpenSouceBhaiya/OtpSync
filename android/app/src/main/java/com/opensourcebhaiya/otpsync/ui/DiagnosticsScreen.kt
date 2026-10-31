package com.opensourcebhaiya.otpsync.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.opensourcebhaiya.otpsync.data.OtpExtractor
import com.opensourcebhaiya.otpsync.data.SettingsManager
import com.opensourcebhaiya.otpsync.ui.theme.*
import com.opensourcebhaiya.otpsync.utils.FileLogger
import com.opensourcebhaiya.otpsync.utils.ToastManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Real-time log state
    var rawLogs by remember { mutableStateOf(FileLogger.getAllLogs(context)) }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, SMS, NOTIF, ERROR
    var autoRefresh by remember { mutableStateOf(true) }

    // Test Extractor state
    var testInputText by remember { mutableStateOf("") }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testResultSuccess by remember { mutableStateOf<Boolean?>(null) }

    // System Permissions State
    var isNotificationAccessGranted by remember { mutableStateOf(false) }
    var isReadSmsGranted by remember { mutableStateOf(false) }
    var isReceiveSmsGranted by remember { mutableStateOf(false) }
    var isBatteryOptIgnored by remember { mutableStateOf(false) }
    var canDrawOverlays by remember { mutableStateOf(false) }
    val settingsManager = remember { SettingsManager(context) }
    val isSyncEnabled = settingsManager.isSyncEnabled()

    fun refreshSystemStatus() {
        // Notification access
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        isNotificationAccessGranted = flat?.contains(context.packageName) == true

        // SMS permissions
        isReadSmsGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        isReceiveSmsGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED

        // Battery optimization
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isBatteryOptIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        // Overlay permission
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
                appendLine("Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Sync Engine: ${if (isSyncEnabled) "ACTIVE" else "DISABLED"}")
                appendLine("Notification Listener: ${if (isNotificationAccessGranted) "CONNECTED" else "ACCESS MISSING"}")
                appendLine("READ_SMS Permission: ${if (isReadSmsGranted) "GRANTED" else "PERMISSION DENIED"}")
                appendLine("RECEIVE_SMS Permission: ${if (isReceiveSmsGranted) "GRANTED" else "PERMISSION DENIED"}")
                appendLine("Overlay (Clipboard): ${if (canDrawOverlays) "GRANTED" else "DENIED"}")
                appendLine("Battery Optimization Ignored: $isBatteryOptIgnored")
                appendLine("==================================")
                appendLine()
                appendLine(recentLogs)
            }
            clipboard.setPrimaryClip(ClipData.newPlainText("OTP Sync Diagnostics", sysSummary))
            ToastManager.show(context, "Recent logs copied to clipboard!", android.widget.Toast.LENGTH_SHORT)
        } catch (e: Exception) {
            ToastManager.show(context, "Clipboard error: ${e.message}. Use 'Export Logs' button.", android.widget.Toast.LENGTH_LONG)
        }
    }

    fun runExtractionTest() {
        if (testInputText.isBlank()) {
            ToastManager.show(context, "Enter or paste SMS text to test", android.widget.Toast.LENGTH_SHORT)
            return
        }
        val startTime = System.currentTimeMillis()
        FileLogger.log(context, "[MANUAL_TEST] Triggered extraction test on text snippet: \"${testInputText.take(100)}\"")
        val res = OtpExtractor.extractOtp(context, "MANUAL-TEST", testInputText, null, startTime)
        if (res != null) {
            testResultSuccess = true
            testResultText = "SUCCESSFUL EXTRACTION!\n\nOTP Code: ${res.code}\nLength: ${res.code.length} digits\nExpiry: ${res.expiresAt?.let { "$it ms" } ?: "Not specified"}"
        } else {
            testResultSuccess = false
            testResultText = "NO OTP CANDIDATE ACCEPTED\n\nCheck the live engine logs terminal below for exact rejection reasons, scoring breakdown, and safety validator details."
        }
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
                            text = "Live SMS & Notification Engine Logs",
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
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs", tint = PurpleAccent)
                    }
                    IconButton(onClick = { FileLogger.exportLog(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export Logs", tint = PurpleAccent)
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
                .padding(16.dp)
        ) {
            // ─── 1. Subsystem Health Status ──────────────────────────────────────────
            Text(
                text = "SUBSYSTEM STATUS",
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
                    .padding(12.dp)
            ) {
                StatusRow(
                    label = "Sync Engine",
                    statusText = if (isSyncEnabled) "ACTIVE" else "DISABLED",
                    isGood = isSyncEnabled,
                    onClick = {
                        settingsManager.setSyncEnabled(!isSyncEnabled)
                        refreshSystemStatus()
                    }
                )
                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))
                StatusRow(
                    label = "Notification Listener",
                    statusText = if (isNotificationAccessGranted) "CONNECTED" else "ACCESS MISSING",
                    isGood = isNotificationAccessGranted,
                    onClick = {
                        try {
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        } catch (_: Exception) {}
                    }
                )
                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))
                StatusRow(
                    label = "SMS Extraction (READ_SMS)",
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
                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))
                StatusRow(
                    label = "Appear on Top (Clipboard)",
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
                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 8.dp))
                StatusRow(
                    label = "Battery Optimization",
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
                text = "TEST SMS / NOTIFICATION EXTRACTOR",
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
                Text(
                    text = "Paste any incoming SMS text below to test real-time OTP extraction logic:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = testInputText,
                    onValueChange = { testInputText = it },
                    placeholder = { Text("e.g. 482910 is your OTP for Internet Lifestyle Hub...", color = TextSecondary.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    trailingIcon = {
                        if (testInputText.isNotBlank()) {
                            IconButton(onClick = { 
                                testInputText = ""
                                testResultText = null
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
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste Clip", fontWeight = FontWeight.Medium)
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
                        Text("Analyze OTP", fontWeight = FontWeight.Bold)
                    }
                }

                if (testResultText != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val isSuccess = testResultSuccess == true
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSuccess) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f))
                            .border(1.dp, if (isSuccess) SuccessGreen else ErrorRed, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isSuccess) SuccessGreen else ErrorRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSuccess) "Extraction Succeeded!" else "Extraction Failed",
                                    color = if (isSuccess) SuccessGreen else ErrorRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = testResultText ?: "",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── 3. Live Log Terminal ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE ENGINE LOGS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PurpleAccent,
                    letterSpacing = 1.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (autoRefresh) "Auto: ON" else "Auto: OFF",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = autoRefresh,
                        onCheckedChange = { autoRefresh = it },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All Logs") }
                )
                FilterChip(
                    selected = selectedFilter == "SMS",
                    onClick = { selectedFilter = "SMS" },
                    label = { Text("SMS Only") }
                )
                FilterChip(
                    selected = selectedFilter == "NOTIF",
                    onClick = { selectedFilter = "NOTIF" },
                    label = { Text("Notifications") }
                )
                FilterChip(
                    selected = selectedFilter == "ERROR",
                    onClick = { selectedFilter = "ERROR" },
                    label = { Text("Errors / Fails") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Filter lines
            val filteredLines = remember(rawLogs, selectedFilter) {
                val lines = rawLogs.lines().filter { it.isNotBlank() }
                when (selectedFilter) {
                    "SMS" -> lines.filter { it.contains("sms", ignoreCase = true) || it.contains("telephony", ignoreCase = true) }
                    "NOTIF" -> lines.filter { it.contains("notification", ignoreCase = true) }
                    "ERROR" -> lines.filter { it.contains("error", ignoreCase = true) || it.contains("failed", ignoreCase = true) || it.contains("crash", ignoreCase = true) || it.contains("rejected", ignoreCase = true) }
                    else -> lines
                }
            }

            // Log Console Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 250.dp, max = 450.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0B0D13))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    .padding(10.dp)
            ) {
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = false
                    ) {
                        if (filteredLines.isEmpty()) {
                            item {
                                Text(
                                    text = "No log records found for filter '$selectedFilter'. Trigger an SMS or notification to see events.",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            items(filteredLines) { line ->
                                val lineColor = when {
                                    line.contains("SUCCESS", ignoreCase = true) -> SuccessGreen
                                    line.contains("FAILED", ignoreCase = true) || line.contains("ERROR", ignoreCase = true) || line.contains("CRASH", ignoreCase = true) -> ErrorRed
                                    line.contains("SMS", ignoreCase = true) -> Color(0xFF60A5FA) // Blue
                                    line.contains("Notification", ignoreCase = true) -> Color(0xFFFBBF24) // Yellow
                                    else -> TextSecondary
                                }
                                Text(
                                    text = line,
                                    color = lineColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
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
                    Text("Copy Logs", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                    Text("Export Logs", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    FileLogger.clearLog(context)
                    rawLogs = FileLogger.getAllLogs(context)
                    ToastManager.show(context, "All logs cleared", android.widget.Toast.LENGTH_SHORT)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = ErrorRed)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear Engine Log History", color = ErrorRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isGood) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
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

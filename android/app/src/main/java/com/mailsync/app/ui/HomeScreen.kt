package com.mailsync.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import android.os.Build
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import android.content.Intent
import com.mailsync.app.ui.components.CustomToggle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import com.mailsync.app.data.OtpEntity
import java.text.SimpleDateFormat
import java.util.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.services.gmail.GmailScopes
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: OtpViewModel,
    historyViewModel: OtpHistoryViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToSettings: (String?) -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToDevices: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val lastScanTime by viewModel.lastScanTime.collectAsState()
    val currentLang by settingsViewModel.appLanguage.collectAsState()

    val isSyncEnabled by settingsViewModel.isSyncEnabled.collectAsState()
    val context = LocalContext.current
    val isNotificationAccessGranted = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.provider.Settings.canDrawOverlays(context) else true
    val isTrulyActive = isSyncEnabled && isNotificationAccessGranted && canDrawOverlays
    val historyState by historyViewModel.uiState.collectAsState()
    val allOtps = if (historyState is HistoryUiState.Success) {
        (historyState as HistoryUiState.Success).otps
    } else {
        emptyList()
    }
    val isSyncing by viewModel.isSyncing.collectAsState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var lifecycleTrigger by remember { mutableStateOf(0) }
    var snoozeTrigger by remember { mutableStateOf(0) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                lifecycleTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    val _trigger = lifecycleTrigger // ensure recomposition on resume
    
    val isInstantSyncEnabled by settingsViewModel.isInstantSyncEnabled.collectAsState()
    val isClipboardCopyEnabled by settingsViewModel.isClipboardCopyEnabled.collectAsState()
    
    val needsClipboardPermission = !isClipboardCopyEnabled || !canDrawOverlays
    val needsNotificationPermission = !isInstantSyncEnabled || !isNotificationAccessGranted
    val isSmsGranted = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.READ_SMS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val anyPermissionMissing = needsNotificationPermission || needsClipboardPermission || !isSmsGranted
    
    var showPermissionPrompt by remember { mutableStateOf(false) }
    var hasPromptedBatteryOpt by remember { mutableStateOf(false) }

    var showAddMenu by remember { mutableStateOf(false) }
    @OptIn(ExperimentalMaterial3Api::class)
    val sheetState = rememberModalBottomSheetState()
    
    LaunchedEffect(lifecycleTrigger, anyPermissionMissing) {
        if (anyPermissionMissing) {
            if (System.currentTimeMillis() >= com.mailsync.app.AppState.permissionPromptSnoozeUntilMs) {
                showPermissionPrompt = true
            }
        } else {
            showPermissionPrompt = false
        }
    }

    // 15-minute snooze reminder: if user tapped 'Remind Me Later', prompt again after 15 minutes while app is running
    LaunchedEffect(snoozeTrigger, anyPermissionMissing) {
        val snoozeUntil = com.mailsync.app.AppState.permissionPromptSnoozeUntilMs
        val remaining = snoozeUntil - System.currentTimeMillis()
        if (remaining > 0L && anyPermissionMissing) {
            kotlinx.coroutines.delay(remaining)
            if (anyPermissionMissing) {
                showPermissionPrompt = true
            }
        }
    }
    
    // ─── In-App Update Check ──────────────────────────────────────────────────
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateApkUrl by remember { mutableStateOf("") }
    var latestVersion by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        // Only check once per session, delay slightly so UI renders first
        kotlinx.coroutines.delay(3000)
        try {
            val url = java.net.URL("https://raw.githubusercontent.com/OpenSouceBhaiya/MailSync/main/version.json")
            val json = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                url.readText()
            }
            val versionMatch = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(json)
            val apkUrlMatch = Regex("\"apkUrl\"\\s*:\\s*\"([^\"]+)\"").find(json)
            val remoteVersion = versionMatch?.groupValues?.get(1) ?: ""
            val apkUrl = apkUrlMatch?.groupValues?.get(1) ?: ""
            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            if (remoteVersion.isNotEmpty() && remoteVersion != currentVersion && apkUrl.isNotEmpty()) {
                latestVersion = remoteVersion
                updateApkUrl = apkUrl
                showUpdateDialog = true
            }
        } catch (e: Exception) {
            // Silent — network not available, or file not found
        }
    }
    
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color(0xFFE2C4FF), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Update Available!", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column {
                    Text(
                        "OTP Sync $latestVersion is out! 🎉",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2C4FF)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "A new and improved version is ready for you. Update now to get the latest bug fixes, improvements, and new features.",
                        color = Color.LightGray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateDialog = false
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(updateApkUrl))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2C4FF))
                ) {
                    Text("Update Now", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Later", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1E1926)
        )
    }

    
    if (showPermissionPrompt) {
        val isSmsGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        // Build list of missing permissions
        data class PermInfo(val icon: ImageVector, val title: String, val desc: String, val highlightTarget: String)
        val missingPerms = buildList {
            if (!isNotificationAccessGranted) add(PermInfo(
                icon = Icons.Default.Notifications,
                title = "Notification Access",
                desc = "Required to capture OTPs from all apps. Without this, no OTPs can be extracted.",
                highlightTarget = "notifications"
            ))
            if (!canDrawOverlays) add(PermInfo(
                icon = Icons.Default.Layers,
                title = "Display Over Other Apps",
                desc = "Required to auto-copy OTPs to clipboard when your phone is locked or in another app.",
                highlightTarget = "overlay"
            ))
            if (!isSmsGranted) add(PermInfo(
                icon = Icons.Default.Sms,
                title = "SMS Permission",
                desc = "Required to extract OTPs from SMS messages (bank OTPs, carrier messages).",
                highlightTarget = "sms"
            ))
        }

        if (missingPerms.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { 
                    showPermissionPrompt = false 
                    com.mailsync.app.AppState.permissionPromptSnoozeUntilMs = System.currentTimeMillis() + (15 * 60 * 1000L)
                    snoozeTrigger++
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF6B35).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null,
                                tint = Color(0xFFFF6B35), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Action Required",
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${missingPerms.size} permission${if (missingPerms.size != 1) "s" else ""} missing",
                                color = Color(0xFFFF6B35), fontSize = 12.sp)
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "OTP Sync needs these permissions to extract and copy OTPs automatically:",
                            color = Color(0xFF9E9E9E), fontSize = 13.sp, lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        missingPerms.forEach { perm ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF2A2438))
                                    .clickable {
                                        showPermissionPrompt = false
                                        onNavigateToSettings(perm.highlightTarget)
                                    }
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFE2C4FF).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(perm.icon, contentDescription = null,
                                            tint = Color(0xFFE2C4FF), modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(perm.title, color = Color.White,
                                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(perm.desc, color = Color(0xFF9E9E9E),
                                            fontSize = 12.sp, lineHeight = 16.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFE2C4FF))
                                                .clickable {
                                                    showPermissionPrompt = false
                                                    onNavigateToSettings(perm.highlightTarget)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 5.dp)
                                        ) {
                                            Text("Fix Now →", color = Color.Black,
                                                fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (missingPerms.size == 3) {
                        Button(
                            onClick = {
                                showPermissionPrompt = false
                                onNavigateToSettings("both")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2C4FF)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Fix All in Settings", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showPermissionPrompt = false 
                        com.mailsync.app.AppState.permissionPromptSnoozeUntilMs = System.currentTimeMillis() + (15 * 60 * 1000L)
                        snoozeTrigger++
                    }) {
                        Text("Remind Me Later", color = Color(0xFF9E9E9E), fontSize = 13.sp)
                    }
                },
                containerColor = Color(0xFF1E1828),
                shape = RoundedCornerShape(24.dp)
            )
        }
    }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF120F17))
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(16.dp)) }

            // Greeting
            item {
                val fullName = settingsViewModel.getManualUserName() ?: "User"
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WavingHandGreeting(fullName = fullName, currentLang = currentLang)
                    
                    IconButton(
                        onClick = { showAddMenu = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFE2C4FF).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Link PC",
                            tint = Color(0xFFE2C4FF)
                        )
                    }
                }
            }

            // Removed state collections from here


            // Removed old revoked accounts banner (no accounts mode)





            // Show a compact notification engine status if sync is disabled
            if (!isSyncEnabled) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFFB020).copy(alpha = 0.15f))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Sync Disabled", tint = Color(0xFFFFB020))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.mailsync.app.data.AppStrings.get("notification_engine_paused_title", currentLang), color = Color(0xFFFFB020), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(com.mailsync.app.data.AppStrings.get("notification_engine_paused_sub", currentLang), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                            }
                            Button(
                                onClick = { settingsViewModel.setSyncEnabled(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB020))
                            ) {
                                Text(com.mailsync.app.data.AppStrings.get("fix_btn", currentLang), color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                val infinitePulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "system_dot_pulse")
                val dotAlpha by infinitePulse.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1.0f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "dot_alpha"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (isTrulyActive) listOf(Color(0xFF26194E), Color(0xFF140D2B)) else listOf(Color(0xFF232326), Color(0xFF151517))
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = if (isTrulyActive) Color(0xFF7B61FF).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(24.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isTrulyActive) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981).copy(alpha = dotAlpha * 0.4f))
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isTrulyActive) Color(0xFF10B981) else Color.Gray)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                val statusText = if (!isNotificationAccessGranted || !canDrawOverlays) com.mailsync.app.data.AppStrings.get("setup_required", currentLang).uppercase() else if (isTrulyActive) com.mailsync.app.data.AppStrings.get("system_active", currentLang).uppercase() else com.mailsync.app.data.AppStrings.get("system_paused", currentLang).uppercase()
                                val statusColor = if (!isNotificationAccessGranted || !canDrawOverlays) Color(0xFFE53935) else if (isTrulyActive) Color(0xFF10B981) else Color.Gray
                                Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF10B981), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    IconButton(
                                        onClick = { 
                                            if (isSyncEnabled) {
                                                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                                                val activeNetwork = cm.activeNetwork
                                                val capabilities = cm.getNetworkCapabilities(activeNetwork)
                                                if (capabilities != null && (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))) {
                                                    viewModel.fetchOtps()
                                                } else {
                                                    com.mailsync.app.utils.ToastManager.show(context, "No internet connection available.", android.widget.Toast.LENGTH_LONG)
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp),
                                        enabled = isSyncEnabled
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Manual Refresh", tint = if (isSyncEnabled) Color.White else Color.Gray, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                CustomToggle(
                                    checked = isTrulyActive,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked && anyPermissionMissing) {
                                            showPermissionPrompt = true
                                        } else {
                                            settingsViewModel.setSyncEnabled(isChecked)
                                            // No battery opt prompt here
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        
                        // 24-Hour Timer Rule: If last OTP is older than 24h, reset to clean ready state
                        val (titleText, subText) = if (allOtps.isNotEmpty()) {
                            val diff = System.currentTimeMillis() - allOtps.first().receivedAt
                            val seconds = diff / 1000
                            val minutes = seconds / 60
                            val hours = minutes / 60
                            if (hours >= 24) {
                                Pair("System Ready", "Listening for incoming OTPs")
                            } else {
                                val agoText = if (hours > 0) {
                                    val remMin = minutes % 60
                                    if (remMin > 0) "$hours hr $remMin min ago" else "$hours hr ago"
                                } else if (minutes > 0) {
                                    "$minutes min ago"
                                } else {
                                    "Just now"
                                }
                                Pair("Last OTP: $agoText", "Notification engine active")
                            }
                        } else {
                            Pair("System Ready", "Waiting for incoming OTPs")
                        }
                        
                        Text(titleText, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(subText, color = Color(0xFFA1A1AA), fontSize = 13.sp)
                    }
                }
            }

            // Stats Row 1
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = com.mailsync.app.data.AppStrings.get("stat_total_otps", currentLang),
                        value = allOtps.size.toString(),
                        subtext = com.mailsync.app.data.AppStrings.get("stat_extracted", currentLang),
                        icon = Icons.Default.Lock
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = com.mailsync.app.data.AppStrings.get("stat_success_rate", currentLang),
                        value = "99.9%",
                        subtext = com.mailsync.app.data.AppStrings.get("stat_extraction", currentLang),
                        icon = Icons.Default.CheckCircle
                    )
                }
            }

            // Live OTP Feed
            item {
                Text(com.mailsync.app.data.AppStrings.get("recent_otps", currentLang), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                
                if (uiState is OtpUiState.Loading && allOtps.isEmpty()) {
                    LoadingWithTips(modifier = Modifier.fillMaxWidth().height(300.dp))
                } else if (uiState is OtpUiState.Error && allOtps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Oops! Something went wrong.", color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text((uiState as OtpUiState.Error).message, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.fetchOtps() }) {
                                Text("Retry")
                            }
                        }
                    }
                } else if (allOtps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(com.mailsync.app.data.AppStrings.get("no_otps_yet", currentLang), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            // Render up to 4 recent OTPs
            items(allOtps.take(4)) { otp ->
                OtpListItemFeed(otp = otp, context = context, currentLang = currentLang)
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) } // Bottom padding for navbar
        }
    }

    if (showAddMenu) {
        ModalBottomSheet(
            onDismissRequest = { showAddMenu = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1E1926),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(com.mailsync.app.data.AppStrings.get("select_action_title", currentLang), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))

                
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showAddMenu = false
                        onNavigateToScanner()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2D2938)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Computer, contentDescription = "Link PC", tint = Color(0xFF00FFA3), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(com.mailsync.app.data.AppStrings.get("link_new_pc", currentLang), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(com.mailsync.app.data.AppStrings.get("scan_qr_to_link", currentLang), color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        showAddMenu = false
                        onNavigateToDevices()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2D2938)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Linked PCs", tint = Color(0xFF00FFA3), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(com.mailsync.app.data.AppStrings.get("manage_linked_pcs_title", currentLang), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(com.mailsync.app.data.AppStrings.get("manage_linked_pcs_sub", currentLang), color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, value: String, subtext: String, icon: ImageVector) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtext, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OtpListItemFeed(otp: OtpEntity, context: Context, currentLang: com.mailsync.app.data.AppLanguage) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    
    // Use actual expiresAt from DB (parsed from email), fallback to 10 min default
    val expiresAtMs = otp.expiresAt ?: (otp.receivedAt + 10 * 60 * 1000L)
    var remainingMs by remember { mutableStateOf(expiresAtMs - System.currentTimeMillis()) }
    val isExpired = remainingMs <= 0
    
    LaunchedEffect(otp.id) {
        while (remainingMs > 0) {
            delay(1000L)
            remainingMs = expiresAtMs - System.currentTimeMillis()
        }
    }
    
    val timerText = if (isExpired) {
        com.mailsync.app.data.AppStrings.get("expired_tag", currentLang)
    } else {
        val totalSecs = (remainingMs / 1000).coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }
    val timerColor = when {
        isExpired -> androidx.compose.ui.graphics.Color(0xFFEF4444)  // red
        remainingMs < 60_000 -> androidx.compose.ui.graphics.Color(0xFFEF4444) // red under 1 min
        remainingMs < 3 * 60_000 -> androidx.compose.ui.graphics.Color(0xFFFFAA00) // orange under 3 min
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Generic Letter Icon
            val firstLetter = otp.sender.take(1).uppercase()
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B2A6E)),
                contentAlignment = Alignment.Center
            ) {
                Text(firstLetter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                val tagRegex = Regex("(?i)(\\[|\\()\\s*(backend|instant)\\s*(\\]|\\))")
                val cleanSender = otp.sender.replace(tagRegex, "").trim()
                Text(cleanSender, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(otp.code, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                
                val cleanAccount = otp.account.replace(tagRegex, "").trim()
                if (cleanAccount.isNotEmpty()) {
                    Text(
                        text = cleanAccount,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        softWrap = true,
                        maxLines = 3,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                
                // 12-hour format time string
                val timeString = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(otp.receivedAt))
                Text(timeString, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Action Column: Copy Button on top, Timer Pill directly below
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("OTP", otp.code)
                        clipboard.setPrimaryClip(clip)
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) { com.mailsync.app.utils.ToastManager.show(context, "OTP Copied", android.widget.Toast.LENGTH_SHORT) }
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Text(com.mailsync.app.data.AppStrings.get("copy_action", currentLang), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Expiry timer pill placed cleanly beneath Copy button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(timerColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "⏱ $timerText",
                        color = timerColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

var globalHasPlayedStartup = false

@Composable
fun WavingHandGreeting(fullName: String?, currentLang: com.mailsync.app.data.AppLanguage) {
    var isWaving by remember { mutableStateOf(!globalHasPlayedStartup) } // Auto-wave only once per session
    val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
    
    // Animation for gradient text
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(3000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "gradientOffset"
    )

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    LaunchedEffect(isWaving) {
        if (isWaving) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            rotation.animateTo(20f, animationSpec = androidx.compose.animation.core.tween(150))
            rotation.animateTo(-20f, animationSpec = androidx.compose.animation.core.tween(150))
            rotation.animateTo(20f, animationSpec = androidx.compose.animation.core.tween(150))
            rotation.animateTo(-20f, animationSpec = androidx.compose.animation.core.tween(150))
            rotation.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(150))
            isWaving = false
            globalHasPlayedStartup = true
        }
    }

    // Fade + slide entrance
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(400)) + 
                androidx.compose.animation.slideInVertically(initialOffsetY = { it / 2 }, animationSpec = androidx.compose.animation.core.tween(400))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "👋",
                fontSize = 28.sp,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isWaving) isWaving = true
                    }
                    .graphicsLayer { 
                        rotationZ = rotation.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.7f, 0.7f)
                    }
            )
            Spacer(modifier = Modifier.width(8.dp))
            
            // Build the text with annotated string or just two texts
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val greetingTime = when {
                hour < 12 -> com.mailsync.app.data.AppStrings.get("good_morning", currentLang)
                hour < 17 -> com.mailsync.app.data.AppStrings.get("good_afternoon", currentLang)
                else -> com.mailsync.app.data.AppStrings.get("good_evening", currentLang)
            }
            val displayFullName = fullName?.split(" ")?.joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(java.util.Locale.getDefault()) else char.toString() } }
            
            val nameTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "name")
            val nameOffset by nameTransition.animateFloat(
                initialValue = 0f,
                targetValue = 2000f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(3000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "nameOffset"
            )

            Text(
                modifier = Modifier.weight(1f, fill = false).animateContentSize(),
                text = buildAnnotatedString {
                    append(if (displayFullName.isNullOrBlank()) greetingTime else "$greetingTime,\n")
                    if (!displayFullName.isNullOrBlank()) {
                        withStyle(
                            style = SpanStyle(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF40FFAA), 
                                        Color(0xFF4079FF), 
                                        Color(0xFF40FFAA), 
                                        Color(0xFF4079FF), 
                                        Color(0xFF40FFAA)
                                    ),
                                    start = androidx.compose.ui.geometry.Offset(nameOffset, 0f),
                                    end = androidx.compose.ui.geometry.Offset(nameOffset + 500f, 500f)
                                )
                            )
                        ) {
                            append(displayFullName)
                        }
                    }
                },
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White,
                lineHeight = 32.sp,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}


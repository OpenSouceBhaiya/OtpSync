package com.mailsync.app.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.mailsync.app.ui.theme.*

import androidx.compose.material.icons.filled.Computer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import com.google.api.services.gmail.GmailScopes
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, highlight: String? = null, onNavigateToDevices: () -> Unit) {
    val currentLang by viewModel.appLanguage.collectAsState()
    var expandedLanguageDropdown by remember { mutableStateOf(false) }
    val languageMenuScrollState = rememberScrollState()
    val languageBringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(expandedLanguageDropdown) {
        if (expandedLanguageDropdown) {
            languageMenuScrollState.scrollTo(0)
        }
    }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var isNotificationAccessGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    var canDrawOverlays = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.provider.Settings.canDrawOverlays(context) else true
    var isSmsPermissionGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var lifecycleTrigger by remember { mutableStateOf(0) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                lifecycleTrigger++
                val notifGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                val overlayGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.provider.Settings.canDrawOverlays(context) else true
                
                // Force sync the app state with OS state since SettingsScreen is where OS permissions are managed
                viewModel.setInstantSyncEnabled(notifGranted)
                viewModel.setClipboardCopyEnabled(overlayGranted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val _trigger = lifecycleTrigger
    isNotificationAccessGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    canDrawOverlays = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.provider.Settings.canDrawOverlays(context) else true
    isSmsPermissionGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val smsPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { _ ->
            isSmsPermissionGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    )

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { _ ->
            // Launch the notification listener settings unconditionally after the prompt
            try {
                context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                android.util.Log.e("Settings", "Failed to open notification settings", e)
            }
        }
    )

    val batteryOptLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        onResult = { _ ->
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("Settings", "Failed to open overlay settings", e)
            }
        }
    )

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = com.mailsync.app.data.AppStrings.get("app_name", currentLang),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = com.mailsync.app.data.AppStrings.get("settings_title", currentLang),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))

            // General Settings
            Text(com.mailsync.app.data.AppStrings.get("general_section", currentLang), style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            SettingsItem(
                title = com.mailsync.app.data.AppStrings.get("linked_pcs_title", currentLang),
                subtitle = com.mailsync.app.data.AppStrings.get("linked_pcs_sub", currentLang),
                icon = Icons.Default.Computer,
                onClick = onNavigateToDevices
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Sync Settings
            Text(com.mailsync.app.data.AppStrings.get("sync_permissions_section", currentLang), style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            var activeHighlight by remember { mutableStateOf(highlight) }
            val isFixAll = activeHighlight == "both"
            val allPermissionsGranted = isNotificationAccessGranted && canDrawOverlays && isSmsPermissionGranted

            LaunchedEffect(highlight, isNotificationAccessGranted, canDrawOverlays, isSmsPermissionGranted) {
                val targetIsAlreadyGranted = when (highlight) {
                    "notifications", "notification", "instant_sync" -> isNotificationAccessGranted
                    "overlay", "clipboard" -> canDrawOverlays
                    "sms" -> isSmsPermissionGranted
                    "both" -> allPermissionsGranted
                    else -> false
                }
                if (targetIsAlreadyGranted) {
                    activeHighlight = null
                } else if (highlight != null) {
                    activeHighlight = highlight
                    scrollState.animateScrollTo(0)
                    kotlinx.coroutines.delay(3500)
                    activeHighlight = null
                }
            }
            
            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
            val highlightAlpha by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 0.4f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                )
            )
            
            val isInstantSyncEnabled by viewModel.isInstantSyncEnabled.collectAsState()
            val isClipboardCopyEnabled by viewModel.isClipboardCopyEnabled.collectAsState()
            
            val permissionsHighlight = if (isFixAll && !allPermissionsGranted) highlightAlpha else 0f

            // When Fix All is clicked, animate the whole card only. When an individual permission is clicked, highlight only that row with red/coral accent (no green).
            // Do NOT highlight any permission that is already granted!
            val isHighlightNotif = !isFixAll && !isNotificationAccessGranted && activeHighlight in listOf("notifications", "notification", "instant_sync")
            val isHighlightOverlay = !isFixAll && !canDrawOverlays && activeHighlight in listOf("overlay", "clipboard")
            val isHighlightSms = !isFixAll && !isSmsPermissionGranted && activeHighlight == "sms"
            
            // Permissions Card (Merged Instant Sync & Clipboard)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (allPermissionsGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color(0xFFE53935).copy(alpha = (0.15f + permissionsHighlight).coerceIn(0f, 1f)))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (allPermissionsGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (allPermissionsGranted) MaterialTheme.colorScheme.primary else Color(0xFFE53935),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = com.mailsync.app.data.AppStrings.get("background_engine_title", currentLang),
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (allPermissionsGranted) com.mailsync.app.data.AppStrings.get("background_engine_active_sub", currentLang) else com.mailsync.app.data.AppStrings.get("background_engine_setup_sub", currentLang),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Notification Permission
                val notifBg = if (isHighlightNotif) Color(0xFFE53935).copy(alpha = (0.35f + (highlightAlpha * 0.4f)).coerceIn(0f, 1f)) else Color.Transparent
                val notifBorder = if (isHighlightNotif) BorderStroke(1.5.dp, Color(0xFFFF5252).copy(alpha = (0.5f + highlightAlpha).coerceIn(0f, 1f))) else null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(notifBg)
                        .then(if (notifBorder != null) Modifier.border(notifBorder, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable {
                            if (!isNotificationAccessGranted) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                    context.startActivity(intent)
                                }
                            } else {
                                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                context.startActivity(intent)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(com.mailsync.app.data.AppStrings.get("notification_access_title", currentLang), color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = isNotificationAccessGranted,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                }
                
                if (!isNotificationAccessGranted && android.os.Build.VERSION.SDK_INT >= 33) {
                    Text(
                        com.mailsync.app.data.AppStrings.get("notification_access_hint", currentLang),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFB020),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                // SMS Permission
                val smsBg = if (isHighlightSms) Color(0xFFE53935).copy(alpha = (0.35f + (highlightAlpha * 0.4f)).coerceIn(0f, 1f)) else Color.Transparent
                val smsBorder = if (isHighlightSms) BorderStroke(1.5.dp, Color(0xFFFF5252).copy(alpha = (0.5f + highlightAlpha).coerceIn(0f, 1f))) else null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(smsBg)
                        .then(if (smsBorder != null) Modifier.border(smsBorder, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable {
                            if (!isSmsPermissionGranted) {
                                smsPermissionLauncher.launch(arrayOf(android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.READ_SMS))
                            } else {
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                                context.startActivity(intent)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(com.mailsync.app.data.AppStrings.get("sms_extraction_title", currentLang), color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = isSmsPermissionGranted,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                }
                
                // Overlay Permission
                val overlayBg = if (isHighlightOverlay) Color(0xFFE53935).copy(alpha = (0.35f + (highlightAlpha * 0.4f)).coerceIn(0f, 1f)) else Color.Transparent
                val overlayBorder = if (isHighlightOverlay) BorderStroke(1.5.dp, Color(0xFFFF5252).copy(alpha = (0.5f + highlightAlpha).coerceIn(0f, 1f))) else null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(overlayBg)
                        .then(if (overlayBorder != null) Modifier.border(overlayBorder, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(com.mailsync.app.data.AppStrings.get("appear_on_top_title", currentLang), color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = canDrawOverlays,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Notification Engine Info Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Engine Active",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Captures OTPs from SMS, WhatsApp, Gmail & all other apps automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✓  Works without any Google account\n✓  Captures from any app sending OTPs\n✓  Zero latency — intercepted before you even see it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))


            // App Preferences Section
            Text(com.mailsync.app.data.AppStrings.get("app_preferences_title", currentLang), style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            val highlightLanguage by viewModel.highlightLanguage.collectAsState()
            val highlightLanguageColor: Color by animateColorAsState(
                targetValue = if (highlightLanguage) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                animationSpec = tween(durationMillis = 1000)
            )
            var highlightLanguageTriggered by remember { mutableStateOf(false) }

            LaunchedEffect(highlightLanguage) {
                if (highlightLanguage) {
                    kotlinx.coroutines.delay(200)
                    try {
                        languageBringIntoViewRequester.bringIntoView()
                    } catch (_: Exception) {}
                    if (scrollState.value < 1400) {
                        scrollState.animateScrollTo(minOf(scrollState.maxValue, 1800))
                    }
                    highlightLanguageTriggered = true
                    kotlinx.coroutines.delay(400)
                    highlightLanguageTriggered = false
                    kotlinx.coroutines.delay(1600)
                    viewModel.clearHighlightLanguage()
                }
            }

            val pulseLanguageScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (highlightLanguageTriggered) 1.05f else 1f,
                animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "pulse_language_scale"
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Row 1: Language
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(languageBringIntoViewRequester)
                            .graphicsLayer(scaleX = pulseLanguageScale, scaleY = pulseLanguageScale)
                            .background(highlightLanguageColor, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = com.mailsync.app.data.AppStrings.get("change_app_language_title", currentLang),
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = com.mailsync.app.data.AppStrings.get("change_app_language_sub", currentLang),
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E2333),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { expandedLanguageDropdown = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentLang.displayName,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Language",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = expandedLanguageDropdown,
                                onDismissRequest = { expandedLanguageDropdown = false },
                                scrollState = languageMenuScrollState,
                                modifier = Modifier
                                    .widthIn(min = 200.dp, max = 260.dp)
                                    .heightIn(max = 300.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1E1829))
                                    .border(1.dp, Color(0xFF433959), RoundedCornerShape(16.dp))
                            ) {
                                com.mailsync.app.data.AppLanguage.entries.forEach { lang ->
                                    val isSelected = lang == currentLang
                                    DropdownMenuItem(
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) Color(0xFFE2C4FF).copy(alpha = 0.12f) else Color.Transparent),
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = lang.displayName,
                                                        color = if (isSelected) Color(0xFFE2C4FF) else TextPrimary,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        text = lang.nativeName,
                                                        color = if (isSelected) Color(0xFFE2C4FF).copy(alpha = 0.7f) else TextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color(0xFFE2C4FF),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.setAppLanguage(lang)
                                            expandedLanguageDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Divider(color = Color.White.copy(alpha = 0.07f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2: App Lock
                    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = com.mailsync.app.data.AppStrings.get("app_lock_title", currentLang),
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = com.mailsync.app.data.AppStrings.get("app_lock_sub", currentLang),
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { enable ->
                                val activity = context as? androidx.fragment.app.FragmentActivity
                                if (enable) {
                                    if (activity != null) {
                                        val helper = BiometricHelper(activity)
                                        helper.authenticate(
                                            onSuccess = { viewModel.setBiometricEnabled(true) },
                                            onError = { 
                                                com.mailsync.app.utils.ToastManager.show(context, "Verification failed: $it", android.widget.Toast.LENGTH_SHORT)
                                            }
                                        )
                                    } else {
                                        viewModel.setBiometricEnabled(true)
                                    }
                                } else {
                                    if (activity != null) {
                                        val helper = BiometricHelper(activity)
                                        helper.authenticate(
                                            onSuccess = { viewModel.setBiometricEnabled(false) },
                                            onError = { 
                                                com.mailsync.app.utils.ToastManager.show(context, "Verification failed to disable lock: $it", android.widget.Toast.LENGTH_SHORT)
                                            }
                                        )
                                    } else {
                                        viewModel.setBiometricEnabled(false)
                                    }
                                }
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Help & Support Section
            Text(com.mailsync.app.data.AppStrings.get("help_support_section", currentLang), style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            val highlightContactUs by viewModel.highlightContactUs.collectAsState()
            val highlightContactUsColor: Color by animateColorAsState(
                targetValue = if (highlightContactUs) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                animationSpec = tween(durationMillis = 1000)
            )
            var highlightContactUsTriggered by remember { mutableStateOf(false) }
            LaunchedEffect(highlightContactUs) {
                if (highlightContactUs) {
                    scrollState.animateScrollTo(10000)
                    highlightContactUsTriggered = true
                    kotlinx.coroutines.delay(400)
                    highlightContactUsTriggered = false
                    kotlinx.coroutines.delay(1600)
                    viewModel.clearHighlightContactUs()
                }
            }
            val contactUsPulseScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (highlightContactUsTriggered) 1.05f else 1f,
                animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "contact_pulse_scale"
            )

            SettingsItem(
                title = com.mailsync.app.data.AppStrings.get("contact_us_title", currentLang),
                subtitle = com.mailsync.app.data.AppStrings.get("contact_us_sub", currentLang),
                icon = Icons.Default.Email,
                modifier = Modifier
                    .graphicsLayer(scaleX = contactUsPulseScale, scaleY = contactUsPulseScale)
                    .background(highlightContactUsColor, shape = RoundedCornerShape(8.dp)),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.opensourcebhaiya.com/contact"))
                    context.startActivity(intent)
                }
            )
            
            val highlightBugReport by viewModel.highlightBugReport.collectAsState()
            val highlightColor: Color by animateColorAsState(
                targetValue = if (highlightBugReport) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                animationSpec = tween(durationMillis = 1000)
            )

            var highlightTriggered by remember { mutableStateOf(false) }
            LaunchedEffect(highlightBugReport) {
                if (highlightBugReport) {
                    scrollState.animateScrollTo(10000)
                    highlightTriggered = true
                    kotlinx.coroutines.delay(400)
                    highlightTriggered = false
                    kotlinx.coroutines.delay(1600)
                    viewModel.clearHighlightBugReport()
                }
            }
            
            val pulseScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (highlightTriggered) 1.05f else 1f,
                animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "pulse_scale"
            )
            
            SettingsItem(
                title = com.mailsync.app.data.AppStrings.get("report_bug_title", currentLang),
                subtitle = com.mailsync.app.data.AppStrings.get("report_bug_sub", currentLang),
                icon = Icons.Default.BugReport,
                modifier = Modifier
                    .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                    .background(highlightColor, shape = RoundedCornerShape(8.dp)),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.opensourcebhaiya.com/bug-report"))
                    context.startActivity(intent)
                }
            )


                        // App Info
              Column(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
              ) {
                  Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.clickable {
                          val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.opensourcebhaiya.com"))
                          context.startActivity(intent)
                      }.padding(8.dp)
                  ) {
                      Icon(
                          Icons.Default.Language,
                          contentDescription = "Website",
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(16.dp)
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                          text = "www.opensourcebhaiya.com",
                          style = MaterialTheme.typography.labelLarge,
                          color = MaterialTheme.colorScheme.primary,
                          fontWeight = FontWeight.Bold
                      )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                      text = "OTP Sync v${com.mailsync.app.BuildConfig.VERSION_NAME}",
                      style = MaterialTheme.typography.bodySmall,
                      color = TextSecondary
                  )
              }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
    }
}


package com.mailsync.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mailsync.app.data.OtpEntity
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpInboxScreen(viewModel: OtpHistoryViewModel, settingsViewModel: SettingsViewModel? = null) {
    val currentLang by settingsViewModel?.appLanguage?.collectAsState() ?: remember { mutableStateOf(com.mailsync.app.data.AppLanguage.EN) }
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var groupBySender by remember { mutableStateOf(false) }
    
    val tabs = listOf(
        com.mailsync.app.data.AppStrings.get("tab_all", currentLang),
        com.mailsync.app.data.AppStrings.get("tab_unread", currentLang),
        com.mailsync.app.data.AppStrings.get("tab_used", currentLang),
        com.mailsync.app.data.AppStrings.get("tab_expired", currentLang)
    )

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = { Text(com.mailsync.app.data.AppStrings.get("inbox_title", currentLang)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(com.mailsync.app.data.AppStrings.get("search_otps", currentLang), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = RoundedCornerShape(24.dp), // Pill shaped
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), // Translucent
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                    )
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    edgePadding = 16.dp,
                    indicator = { },
                    divider = { }
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            modifier = Modifier
                                .padding(end = 8.dp, bottom = 8.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            text = { 
                                Text(
                                    title,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ) 
                            }
                        )
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(com.mailsync.app.data.AppStrings.get("group_by_sender", currentLang), fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = groupBySender, onCheckedChange = { groupBySender = it })
                }
            }
        }
    ) { paddingValues ->
        when (uiState) {
            is HistoryUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is HistoryUiState.Empty -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(com.mailsync.app.data.AppStrings.get("no_otps_found", currentLang), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is HistoryUiState.Success -> {
                val allOtps = (uiState as HistoryUiState.Success).otps
                val filteredOtps = allOtps.filter { otp ->
                    val matchesSearch = otp.sender.contains(searchQuery, ignoreCase = true) || 
                                        otp.subject.contains(searchQuery, ignoreCase = true) ||
                                        otp.code.contains(searchQuery, ignoreCase = true)
                    
                    val expiresAtMs = otp.expiresAt ?: (otp.receivedAt + 10 * 60 * 1000L)
                    val isExpired = System.currentTimeMillis() > expiresAtMs

                    val matchesTab = when (selectedTab) {
                        1 -> !otp.isUsed && !isExpired
                        2 -> otp.isUsed
                        3 -> isExpired && !otp.isUsed
                        else -> true
                    }
                    matchesSearch && matchesTab
                }

                if (filteredOtps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(com.mailsync.app.data.AppStrings.get("no_matching_otps", currentLang), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    var expandedSenders by remember { mutableStateOf(setOf<String>()) }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (groupBySender) {
                            val grouped = filteredOtps.groupBy { it.sender }
                            grouped.forEach { (sender, otps) ->
                                val isExpanded = expandedSenders.contains(sender)
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp, bottom = 4.dp)
                                            .clickable {
                                                expandedSenders = if (isExpanded) expandedSenders - sender else expandedSenders + sender
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "$sender (${otps.size})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Toggle",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (isExpanded) {
                                    items(otps) { otp ->
                                        InboxOtpCard(otp, viewModel, currentLang)
                                    }
                                }
                            }
                        } else {
                            item { Spacer(modifier = Modifier.height(8.dp)) }
                            items(filteredOtps) { otp ->
                                InboxOtpCard(otp, viewModel, currentLang)
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
fun InboxOtpCard(otp: OtpEntity, viewModel: OtpHistoryViewModel, currentLang: com.mailsync.app.data.AppLanguage) {
    val context = LocalContext.current
    val timeFormat = SimpleDateFormat("h:mm a MMMM dd", Locale.getDefault())
    val timeString = timeFormat.format(Date(otp.receivedAt))

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
        isExpired -> androidx.compose.ui.graphics.Color(0xFFEF4444)
        remainingMs < 60_000 -> androidx.compose.ui.graphics.Color(0xFFEF4444)
        remainingMs < 3 * 60_000 -> androidx.compose.ui.graphics.Color(0xFFFFAA00)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    
    val cardAlpha = if (otp.isUsed || isExpired) 0.6f else 1.0f

    Card(
        modifier = Modifier.fillMaxWidth().alpha(cardAlpha),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    val tagRegex = Regex("(?i)(\\[|\\()\\s*(backend|instant)\\s*(\\]|\\))")
                    val cleanSender = otp.sender.replace(tagRegex, "").trim()
                    Text(cleanSender, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    
                    val cleanAccount = otp.account.replace(tagRegex, "").trim()
                    if (cleanAccount.isNotEmpty()) {
                        Text(
                            text = cleanAccount,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            softWrap = true,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    if (!otp.sentToDevices.isNullOrBlank()) {
                        val prefix = com.mailsync.app.data.AppStrings.get("sent_to_prefix", currentLang).ifEmpty { "Sent to" }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00382B))
                                .border(1.dp, Color(0xFF00FFA3).copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Computer,
                                    contentDescription = null,
                                    tint = Color(0xFF00FFA3),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "$prefix ${otp.sentToDevices}",
                                    color = Color(0xFF00FFA3),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    softWrap = true
                                )
                            }
                        }
                    }
                }
                Text(timeString, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.align(Alignment.Top))
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            SegmentedOtpDisplay(code = otp.code)
            
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!otp.isUsed) {
                    if (isExpired) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(timerColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("⏱ $timerText", color = timerColor, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.markAsUsed(otp.id) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(com.mailsync.app.data.AppStrings.get("mark_used", currentLang))
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(com.mailsync.app.data.AppStrings.get("tab_used", currentLang), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    }
                }

                Row {
                    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("OTP", otp.code)
                        clipboard.setPrimaryClip(clip)
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                        com.mailsync.app.utils.ToastManager.show(context, "OTP Copied", android.widget.Toast.LENGTH_SHORT)
                    }
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    }) {
                        Text(com.mailsync.app.data.AppStrings.get("copy_action", currentLang), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    IconButton(onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Here is the ${otp.sender} OTP: ${otp.code}")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share OTP", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}


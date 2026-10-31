package com.opensourcebhaiya.otpsync.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha

import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications

import android.content.Intent

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object Inbox : Screen("inbox", "Inbox", Icons.Filled.Email)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    object Accounts : Screen("accounts", "Accounts", Icons.Filled.Email)
}

@Composable
fun AppNavigation(
    otpViewModel: OtpViewModel,
    historyViewModel: OtpHistoryViewModel,
    settingsViewModel: SettingsViewModel,

    currentIntent: Intent? = null
) {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isConfigured by settingsViewModel.isConfigured.collectAsState(initial = true)
    
    LaunchedEffect(currentIntent) {
        currentIntent?.data?.let { uri ->
            if (uri.scheme == "gmailotpsyncer") {
                when (uri.host) {
                    "scan" -> {
                        navController.navigate("home") {
                            popUpTo(0)
                        }
                        otpViewModel.fetchOtps()
                    }
                    "settings" -> {
                        navController.navigate("settings") {
                            popUpTo(0)
                        }
                    }
                    "sync_mode" -> {
                        if (isConfigured) {
                            navController.navigate("settings") {
                                popUpTo(0)
                            }
                            settingsViewModel.triggerHighlightSyncMode()
                        } else {
                            navController.navigate("onboarding") {
                                popUpTo(0)
                            }
                        }
                    }
                    "bug_report" -> {
                        if (isConfigured) {
                            navController.navigate("settings") {
                                popUpTo(0)
                            }
                            settingsViewModel.triggerHighlightBugReport()
                        } else {
                            com.opensourcebhaiya.otpsync.utils.ToastManager.show(
                                context, "Whoa buddy! \uD83D\uDE05 Please test the app first before reporting bugs!",
                                android.widget.Toast.LENGTH_LONG
                            )
                            navController.navigate("onboarding") {
                                popUpTo(0)
                            }
                        }
                    }
                    "contact" -> {
                        navController.navigate("settings") {
                            popUpTo(0)
                        }
                        settingsViewModel.triggerHighlightContactUs()
                    }
                    "language" -> {
                        navController.navigate("settings") {
                            popUpTo(0)
                        }
                        settingsViewModel.triggerHighlightLanguage()
                    }
                    "diagnostics", "logs" -> {
                        navController.navigate("diagnostics") {
                            popUpTo(0)
                        }
                    }
                }
                // Clear the data to prevent re-triggering navigation on recomposition
                currentIntent.data = null
            }
        }
    }
    val items = listOf(Screen.Home, Screen.Inbox, Screen.Settings)

    Scaffold(
        bottomBar = {
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val route = currentDestination?.route
            val showBottomBar = route != null && (
                route.startsWith(Screen.Home.route) ||
                route.startsWith(Screen.Inbox.route) ||
                route.startsWith(Screen.Settings.route)
            )

            val currentLang by settingsViewModel.appLanguage.collectAsState()

            if (showBottomBar) {
                NavigationBar(
                    containerColor = com.opensourcebhaiya.otpsync.ui.theme.DarkSurface,
                    contentColor = com.opensourcebhaiya.otpsync.ui.theme.TextPrimary
                ) {

                    items.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route?.startsWith(screen.route) == true } == true
                    val screenTitle = when(screen) {
                        Screen.Home -> com.opensourcebhaiya.otpsync.data.AppStrings.get("nav_home", currentLang)
                        Screen.Inbox -> com.opensourcebhaiya.otpsync.data.AppStrings.get("nav_inbox", currentLang)
                        Screen.Settings -> com.opensourcebhaiya.otpsync.data.AppStrings.get("nav_settings", currentLang)
                        else -> screen.title
                    }

                    // Animation 1: Smooth color transition
                    val iconColor by animateColorAsState(
                        targetValue = if (selected) com.opensourcebhaiya.otpsync.ui.theme.PurpleAccent else com.opensourcebhaiya.otpsync.ui.theme.TextSecondary
                    )
                    
                    // Animation 2: Spring scale-up bounce on select
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.2f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )

                    // Animation 3: Rotation for Settings
                    val iconRotation by animateFloatAsState(
                        targetValue = if (selected && screen == Screen.Settings) 90f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )

                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screenTitle,
                                modifier = Modifier
                                    .scale(iconScale)
                                    .rotate(iconRotation),
                                tint = iconColor
                            )
                        },
                        label = { Text(screenTitle) },
                        selected = selected,
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.opensourcebhaiya.otpsync.ui.theme.PurpleAccent,
                            unselectedIconColor = com.opensourcebhaiya.otpsync.ui.theme.TextSecondary,
                            selectedTextColor = com.opensourcebhaiya.otpsync.ui.theme.PurpleAccent,
                            unselectedTextColor = com.opensourcebhaiya.otpsync.ui.theme.TextSecondary,
                            indicatorColor = com.opensourcebhaiya.otpsync.ui.theme.DarkSurfaceVariant
                        )
                    )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(innerPadding),
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None },
            popExitTransition = { androidx.compose.animation.ExitTransition.None }
        ) {
            composable("splash") {
                val isConfigured by settingsViewModel.isConfigured.collectAsState()
                val hasSeenOnboarding by settingsViewModel.hasSeenOnboarding.collectAsState()
                if (!hasSeenOnboarding) {
                    LaunchedEffect(Unit) {
                        navController.navigate("onboarding") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                } else {
                    SplashScreen(onTimeout = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo("splash") { inclusive = true }
                        }
                    })
                }
            }
            composable("onboarding") {
                OnboardingScreen(
                    onFinishOnboarding = {
                        navController.navigate("complete_profile")
                    },
                    settingsViewModel = settingsViewModel
                )
            }
            composable("complete_profile") {
                CompleteProfileScreen(
                    settingsViewModel = settingsViewModel,
                    onContinue = {
                        settingsViewModel.setHasSeenOnboarding(true)
                        navController.navigate(Screen.Home.route) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                val hasSeenOnboarding by settingsViewModel.hasSeenOnboarding.collectAsState()
                if (!hasSeenOnboarding) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Welcome to MailSync!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Kindly onboard first for the best experience and understanding of the app.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = com.opensourcebhaiya.otpsync.ui.theme.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
                                navController.navigate("onboarding") {
                                    popUpTo(Screen.Home.route) { inclusive = true }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Start Onboarding", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    HomeScreen(viewModel = otpViewModel, historyViewModel = historyViewModel, settingsViewModel = settingsViewModel, onNavigateToSettings = { highlight ->
                        val route = if (highlight != null) "${Screen.Settings.route}?highlight=$highlight" else Screen.Settings.route
                        navController.navigate(route)
                    }, onNavigateToScanner = {
                        navController.navigate("qr_scanner")
                    }, onNavigateToDevices = {
                        navController.navigate("devices")
                    })
                }
            }
            composable("qr_scanner") {
                QRScannerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onQrCodeScanned = { qrContent ->
                        val isMailSync = qrContent.contains("connect?uuid=") && qrContent.contains("&key=")
                        if (isMailSync) {
                            try {
                                val uri = android.net.Uri.parse(qrContent)
                                val uuid = uri.getQueryParameter("uuid") ?: ""
                                val pcName = uri.getQueryParameter("name") ?: "PC"
                                val browser = uri.getQueryParameter("browser") ?: "Browser"
                                val keyBase64 = uri.getQueryParameter("key") ?: ""

                                if (uuid.isNotEmpty() && keyBase64.isNotEmpty()) {
                                    val dateLinked = java.text.SimpleDateFormat("MMM dd 'at' h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                                    settingsViewModel.linkDevice(uuid, keyBase64, pcName, browser, dateLinked)
                                    android.widget.Toast.makeText(context, "✅ PC Linked Successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    com.opensourcebhaiya.otpsync.utils.ToastManager.show(context, "Invalid MailSync QR Code", android.widget.Toast.LENGTH_SHORT)
                                }
                            } catch (e: Exception) {
                                com.opensourcebhaiya.otpsync.utils.ToastManager.show(context, "Error parsing QR Code", android.widget.Toast.LENGTH_SHORT)
                            }
                        } else if (android.util.Patterns.WEB_URL.matcher(qrContent).matches()) {
                            // Already handled by QRScannerScreen's LaunchedEffect, but kept here just in case
                        } else {
                            // Otherwise, copy to clipboard
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Scanned QR", qrContent)
                            clipboard.setPrimaryClip(clip)
                            com.opensourcebhaiya.otpsync.utils.ToastManager.show(context, "Text copied to clipboard", android.widget.Toast.LENGTH_SHORT)
                        }
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.Inbox.route) {
                OtpInboxScreen(viewModel = historyViewModel, settingsViewModel = settingsViewModel)
            }
            composable(
                route = Screen.Settings.route + "?highlight={highlight}",
                arguments = listOf(androidx.navigation.navArgument("highlight") { 
                    type = androidx.navigation.NavType.StringType
                    nullable = true 
                    defaultValue = null
                })
            ) { backStackEntry ->
                val highlight = backStackEntry.arguments?.getString("highlight")
                SettingsScreen(
                    viewModel = settingsViewModel,
                    highlight = highlight,
                    onNavigateToDevices = {
                        navController.navigate("devices")
                    },
                    onNavigateToDiagnostics = {
                        navController.navigate("diagnostics")
                    }
                )
            }
            composable("devices") {
                DevicesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToScanner = { navController.navigate("qr_scanner") },
                    viewModel = settingsViewModel
                )
            }
            composable("diagnostics") {
                DiagnosticsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = settingsViewModel
                )
            }

        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    
    val quotes = listOf(
        "Copying an OTP manually takes 5–10 seconds. OTP Sync delivers it in under 2.",
        "Your OTP on your PC clipboard in 2 seconds flat.",
        "Stop searching for your phone. Your code is already copied.",
        "Save 10 seconds per login — reclaim hours of focus every month.",
        "Zero typing, zero delay. Instant 2-second OTP synchronization.",
        "Why waste 10 seconds typing when you can paste in 2?",
        "From phone notification to PC clipboard in a split second.",
        "Never break your deep work flow for a 6-digit code again.",
        "Every second counts. Your OTP is ready before you reach for your phone.",
        "Frictionless authentication: 2 seconds from incoming SMS to screen.",
        "Automated OTP transfer: saving you 10 valuable seconds on every login.",
        "Instant OTP capture: no delays, no manual typing, pure speed.",
        "Security at the speed of thought — OTPs synced in under 2 seconds.",
        "Turn a 10-second manual distraction into a 2-second background breeze.",
        "Your time is too valuable for manual OTP copying.",
        "Interception in milliseconds, clipboard sync in 2 seconds.",
        "Bypass the phone-pickup tax: save 5 to 10 seconds every single time.",
        "One-time passwords shouldn't waste your precious time.",
        "Lightning-fast OTP delivery: 2 seconds is all it takes.",
        "Stay in your flow state — your PC clipboard is already armed with the OTP.",
        "10 seconds saved per code compounds into entire days of productivity.",
        "Instant authentication between phone and PC with zero friction.",
        "The fastest way to verify: 2-second encrypted OTP transfer.",
        "Don't let manual 6-digit typing slow your momentum down.",
        "Instant clipboard sync: because every second of your focus matters.",
        "Eliminate login friction. OTP delivered to your PC in under 2 seconds.",
        "From incoming code to clipboard paste in the blink of an eye.",
        "No more checking your phone mid-task. Your OTP is already on your PC.",
        "5 to 10 seconds saved on every login adds up to real peace of mind.",
        "Speed meets security: encrypted OTP delivery in under 2 seconds."
    )
    val randomQuote = remember { quotes.random() }
    
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val blinkScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    
    val alphaAnim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 800,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        )
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        kotlinx.coroutines.delay(3000) // Give them time to read the quote
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.opensourcebhaiya.otpsync.ui.theme.DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(2000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                )
            )

            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer { rotationZ = rotation }
            ) {
                // Draw a very thin, elegant arc (like an eclipse/crescent)
                drawArc(
                    color = androidx.compose.ui.graphics.Color.White,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    ),
                    alpha = blinkAlpha
                )
            }
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(64.dp))
            
            Text(
                text = "\"$randomQuote\"",
                style = MaterialTheme.typography.bodyLarge,
                color = com.opensourcebhaiya.otpsync.ui.theme.TextPrimary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.alpha(alphaAnim),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}


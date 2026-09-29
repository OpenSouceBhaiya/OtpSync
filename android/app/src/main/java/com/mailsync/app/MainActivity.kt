package com.mailsync.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mailsync.app.data.OtpRepository
import com.mailsync.app.data.SettingsManager
import com.mailsync.app.ui.AppNavigation
import com.mailsync.app.ui.OtpViewModel
import com.mailsync.app.ui.OtpHistoryViewModel
import com.mailsync.app.ui.SettingsViewModel
import com.mailsync.app.ui.theme.GmailOtpSyncerTheme
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat


import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background

import com.mailsync.app.ui.BiometricHelper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import kotlinx.coroutines.channels.awaitClose
class MainActivity : FragmentActivity() {

    // Lazy initialization of our dependencies
    private val settingsManager by lazy { SettingsManager(applicationContext) }
    private val firebaseManager by lazy { com.mailsync.app.data.FirebaseManager() }
    private val otpRepository by lazy { OtpRepository(applicationContext, settingsManager, firebaseManager) }
    private val biometricHelper by lazy { BiometricHelper(this) }

    // Initialize ViewModels using our custom Factories so we can pass in the repository
    private val otpViewModel: OtpViewModel by viewModels {
        OtpViewModel.Factory(applicationContext, otpRepository, settingsManager)
    }
    
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(applicationContext, otpRepository, settingsManager, firebaseManager)
    }

    private val historyViewModel: OtpHistoryViewModel by viewModels {
        OtpHistoryViewModel.Factory(otpRepository, settingsManager)
    }


    // Polling service removed since backend handles it

    override fun onResume() {
        super.onResume()
        // Reset adaptive polling to fast interval when app is opened
        com.mailsync.app.AppState.lastActiveTimeMs = System.currentTimeMillis()
    }

    private val currentIntent = kotlinx.coroutines.flow.MutableStateFlow<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntent.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen before super.onCreate()
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // --- GLOBAL CRASH REPORTER ---
        val crashPrefs = getSharedPreferences("crash_logs", android.content.Context.MODE_PRIVATE)
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            val stackTrace = android.util.Log.getStackTraceString(exception)
            crashPrefs.edit().putString("last_crash", stackTrace).commit()
            
            // Kill the process so Android doesn't show the generic ANR/Crash dialog
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }
        
        settingsManager.incrementAppOpenCount()
        
        if (settingsManager.isConfigured() && settingsManager.isSyncEnabled()) {
            com.mailsync.app.service.ServiceHelper.startForegroundService(this)
        }
        currentIntent.value = intent
        setContent {
            var isUnlocked by remember { mutableStateOf(!settingsManager.isBiometricLockEnabled()) }
            var authError by remember { mutableStateOf<String?>(null) }
            val intentState by currentIntent.collectAsState()
            
            val lastCrash = crashPrefs.getString("last_crash", null)
            var showCrashDialog by remember { mutableStateOf(lastCrash != null) }

            GmailOtpSyncerTheme {
                if (showCrashDialog && lastCrash != null) {
                    val context = LocalContext.current
                    AlertDialog(
                        onDismissRequest = { },
                        title = { 
                            Text("Oops! MailSync hit a bump \uD83D\uDE1E", fontWeight = FontWeight.Bold, color = Color.White) 
                        },
                        text = { 
                            Column {
                                Text("\"Every bug is a feature waiting to be born.\" \uD83D\uDE80", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color(0xFFA1A1AA))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("The app unexpectedly crashed last time. Please help us fix it by reporting this bug!", color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Steps to report:", fontWeight = FontWeight.Bold, color = Color.White)
                                Text("1. Click 'Copy & Report' below", color = Color(0xFFA1A1AA))
                                Text("2. Paste the log in our bug tracker", color = Color(0xFFA1A1AA))
                                Text("3. You'll be redirected to: www.opensourcebhaiya.online/bug-report", color = Color(0xFFA1A1AA))
                                Spacer(modifier = Modifier.height(16.dp))
                                Box(modifier = Modifier.background(Color(0xFF2D2938), RoundedCornerShape(8.dp)).padding(8.dp).fillMaxHeight(0.3f)) {
                                    LazyColumn {
                                        item { Text(lastCrash, style = MaterialTheme.typography.bodySmall, color = Color.LightGray) }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", lastCrash))
                                com.mailsync.app.utils.ToastManager.show(context, "Crash log copied! Opening browser...", android.widget.Toast.LENGTH_SHORT)
                                
                                val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.opensourcebhaiya.online/bug-report"))
                                context.startActivity(browserIntent)
                                
                                crashPrefs.edit().remove("last_crash").apply()
                                showCrashDialog = false
                            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                Text("Copy & Report")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                crashPrefs.edit().remove("last_crash").apply()
                                showCrashDialog = false
                            }) {
                                Text("Dismiss", color = Color.Gray)
                            }
                        },
                        containerColor = Color(0xFF1E1926)
                    )
                } else if (isUnlocked) {
                    val context = LocalContext.current
                    val networkObserver = remember { 
                        kotlinx.coroutines.flow.callbackFlow {
                            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                                override fun onAvailable(network: android.net.Network) { trySend(true) }
                                override fun onLost(network: android.net.Network) { trySend(false) }
                                override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) {
                                    trySend(networkCapabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET))
                                }
                            }
                            val request = android.net.NetworkRequest.Builder().addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
                            connectivityManager.registerNetworkCallback(request, callback)
                            val active = connectivityManager.activeNetwork
                            val caps = connectivityManager.getNetworkCapabilities(active)
                            trySend(caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
                            awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
                        }
                    }
                    val isOnline by networkObserver.collectAsState(initial = true)
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavigation(
                            otpViewModel = otpViewModel,
                            historyViewModel = historyViewModel,
                            settingsViewModel = settingsViewModel,
                            currentIntent = intentState
                        )

                        var showOverlay by remember { mutableStateOf(false) }
                        LaunchedEffect(isOnline) {
                            if (!isOnline) {
                                showOverlay = true
                            } else if (showOverlay) {
                                kotlinx.coroutines.delay(2000)
                                showOverlay = false
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showOverlay,
                            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(500)),
                            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(800))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xE60F0C16))
                                    .clickable(enabled = false) {}, 
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.animation.AnimatedContent(
                                    targetState = isOnline,
                                    transitionSpec = {
                                        androidx.compose.animation.core.tween<Float>(500).let { tween ->
                                            androidx.compose.animation.fadeIn(animationSpec = tween).togetherWith(androidx.compose.animation.fadeOut(animationSpec = tween))
                                        }
                                    },
                                    label = "offline_transition"
                                ) { currentlyOnline ->
                                    if (currentlyOnline) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Online", tint = Color(0xFF10B981), modifier = Modifier.size(80.dp))
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text("Internet Restored", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                        }
                                    } else {
                                        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
                                        val scale by infiniteTransition.animateFloat(
                                            initialValue = 0.9f,
                                            targetValue = 1.1f,
                                            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                                animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                            ),
                                            label = "scale"
                                        )
                                        val alpha by infiniteTransition.animateFloat(
                                            initialValue = 0.5f,
                                            targetValue = 1f,
                                            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                                animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                            ),
                                            label = "alpha"
                                        )
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Box(modifier = Modifier
                                                    .size(100.dp)
                                                    .androidx.compose.ui.graphics.graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                                                    .background(Color(0x33EF4444), shape = androidx.compose.foundation.shape.CircleShape)
                                                )
                                                Icon(Icons.Default.SignalWifiOff, contentDescription = "Offline", tint = Color(0xFFEF4444), modifier = Modifier.size(64.dp))
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text("No Internet Connection", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("OTP Sync requires internet to beam codes.", color = Color.Gray, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Lock Screen
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("App Locked", style = MaterialTheme.typography.headlineMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            if (authError != null) {
                                Text(authError!!, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                            Button(onClick = {
                                biometricHelper.authenticate(
                                    onSuccess = { isUnlocked = true },
                                    onError = { authError = it }
                                )
                            }) {
                                Text("Unlock")
                            }
                        }
                    }
                    
                    // Trigger auth on launch
                    LaunchedEffect(Unit) {
                        biometricHelper.authenticate(
                            onSuccess = { isUnlocked = true },
                            onError = { authError = it }
                        )
                    }
                }
            }
        }
    }
}


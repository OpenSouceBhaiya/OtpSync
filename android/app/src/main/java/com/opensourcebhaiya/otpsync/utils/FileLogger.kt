package com.opensourcebhaiya.otpsync.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object FileLogger {
    private const val TAG = "OTP-Sync-Logger"
    private const val FILE_NAME = "otp_debug.log"
    private const val MAX_MEMORY_LINES = 400
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024 // 2 MB

    private val memoryLogs = ConcurrentLinkedDeque<String>()

    var lastSmsEvent: String? = null
        private set
    var lastNotificationEvent: String? = null
        private set
    var lastExtractorEvent: String? = null
        private set
    var lastErrorEvent: String? = null
        private set

    fun log(context: Context?, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val logLine = "[$timestamp] $message"
        
        try {
            Log.d(TAG, logLine)
        } catch (_: Throwable) {
            println("$TAG: $logLine")
        }

        memoryLogs.addLast(logLine)
        while (memoryLogs.size > MAX_MEMORY_LINES) {
            memoryLogs.pollFirst()
        }

        val lower = message.lowercase()
        if (lower.contains("sms") || lower.contains("telephony")) {
            lastSmsEvent = logLine
        }
        if (lower.contains("notification")) {
            lastNotificationEvent = logLine
        }
        if (lower.contains("otpextractor") || lower.contains("extracted") || lower.contains("candidate")) {
            lastExtractorEvent = logLine
        }
        if (lower.contains("error") || lower.contains("failed") || lower.contains("exception") || lower.contains("crash")) {
            lastErrorEvent = logLine
        }

        if (context == null) return
        try {
            val logFile = File(context.cacheDir, FILE_NAME)
            if (logFile.exists() && logFile.length() > MAX_FILE_SIZE_BYTES) {
                val lines = logFile.readLines()
                val keepLines = lines.takeLast(lines.size / 2)
                logFile.writeText(keepLines.joinToString("\n") + "\n")
            }
            PrintWriter(FileWriter(logFile, true)).use { writer ->
                writer.println(logLine)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    fun logError(context: Context?, tag: String, message: String, throwable: Throwable? = null) {
        val errStr = if (throwable != null) {
            "$message | Exception: ${throwable.javaClass.simpleName} - ${throwable.message}\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        log(context, "[ERROR][$tag] $errStr")
    }

    fun logError(context: Context?, message: String, throwable: Throwable? = null) {
        logError(context, "ERROR", message, throwable)
    }

    fun getAllLogs(context: Context): String {
        return try {
            val logFile = File(context.cacheDir, FILE_NAME)
            if (logFile.exists() && logFile.length() > 0) {
                logFile.readText()
            } else {
                memoryLogs.joinToString("\n")
            }
        } catch (e: Exception) {
            "Error reading log file: ${e.message}\n" + memoryLogs.joinToString("\n")
        }
    }

    fun getRecentMemoryLogs(): List<String> {
        return memoryLogs.toList()
    }

    fun exportLog(context: Context) {
        try {
            val logFile = File(context.cacheDir, FILE_NAME)
            if (!logFile.exists() || logFile.length() == 0L) {
                // If empty or doesn't exist, dump in-memory logs to file
                logFile.writeText(memoryLogs.joinToString("\n") + "\n")
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "OTP Sync Diagnostic Log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Debug Log").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            ToastManager.show(context, "Failed to export logs: ${e.message}", android.widget.Toast.LENGTH_LONG)
        }
    }

    fun clearLog(context: Context) {
        try {
            memoryLogs.clear()
            val logFile = File(context.cacheDir, FILE_NAME)
            if (logFile.exists()) {
                logFile.delete()
            }
            log(context, "Logs cleared by user.")
        } catch (e: Exception) {
            // Ignore
        }
    }
}

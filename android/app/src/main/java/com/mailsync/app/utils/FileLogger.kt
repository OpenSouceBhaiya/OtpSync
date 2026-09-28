package com.mailsync.app.utils

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

object FileLogger {
    private const val TAG = "MailSync-FileLogger"
    private const val FILE_NAME = "otp_debug.log"

    fun log(context: Context, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val logLine = "[$timestamp] [API ${Build.VERSION.SDK_INT}] $message\n"
        Log.d(TAG, logLine)
        try {
            val logFile = File(context.cacheDir, FILE_NAME)
            val writer = PrintWriter(FileWriter(logFile, true))
            writer.append(logLine)
            writer.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    fun exportLog(context: Context) {
        try {
            val logFile = File(context.cacheDir, FILE_NAME)
            if (!logFile.exists()) {
                ToastManager.show(context, "No log file found.", android.widget.Toast.LENGTH_SHORT)
                return
            }

            // We need a FileProvider to share a file securely
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
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
            val logFile = File(context.cacheDir, FILE_NAME)
            if (logFile.exists()) {
                logFile.delete()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}

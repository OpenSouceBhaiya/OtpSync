package com.opensourcebhaiya.otpsync

object AppState {
    @Volatile var lastActiveTimeMs: Long = System.currentTimeMillis()
    @Volatile var permissionPromptSnoozeUntilMs: Long = 0L
}

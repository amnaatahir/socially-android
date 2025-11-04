package com.example.smd_a1

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenshotReceiver(private val onScreenshotDetected: (String) -> Unit) : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ScreenshotReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "android.intent.action.SCREENSHOT" -> {
                Log.d(TAG, "Screenshot detected")
                // For now, we'll use a generic chat ID
                // In a real implementation, you'd track which chat is currently active
                onScreenshotDetected("current_chat")
            }
        }
    }
}

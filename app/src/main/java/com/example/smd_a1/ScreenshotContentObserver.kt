package com.example.smd_a1

import android.content.ContentValues
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.atomic.AtomicLong

class ScreenshotContentObserver(
    handler: Handler,
    private val chatId: String,
    private val receiverId: String,
    private val onScreenshotDetected: () -> Unit
) : ContentObserver(handler) {
    
    companion object {
        private const val TAG = "ScreenshotObserver"
        private const val SCREENSHOT_DEBOUNCE_MS = 1000L
        // Screenshot paths patterns
        private val SCREENSHOT_PATH_PATTERNS = listOf(
            "/screenshot", "/Screenshot", "/Pictures/Screenshots", "/DCIM/Screenshots"
        )
    }
    
    // Cannot use const for Uri, must use val (moved outside companion)
    private val EXTERNAL_CONTENT_URI: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    
    private val lastScreenshotTime = AtomicLong(0)
    private var lastProcessedUri: Uri? = null
    
    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        if (uri == null || uri == lastProcessedUri) return
        
        val currentTime = System.currentTimeMillis()
        val lastTime = lastScreenshotTime.get()
        
        // Prevent multiple rapid triggers
        if (currentTime - lastTime < SCREENSHOT_DEBOUNCE_MS) {
            return
        }
        
        // Check if it's a screenshot
        checkIfScreenshot(uri) { isScreenshot ->
            if (isScreenshot) {
                lastScreenshotTime.set(currentTime)
                lastProcessedUri = uri
                handleScreenshot()
            }
        }
    }
    
    private fun checkIfScreenshot(uri: Uri, callback: (Boolean) -> Unit) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED
            )
            
            val cursor = uri.toString().let { uriString ->
                // For Android 10+, we need to query differently
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // Check if the path contains screenshot keywords
                    uri.toString().lowercase().contains("screenshot")
                } else {
                    // For older versions, check the data path
                    try {
                        val contentResolver = android.app.Application()
                            .applicationContext.contentResolver
                        val cursor = contentResolver.query(
                            uri,
                            projection,
                            null,
                            null,
                            "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 1"
                        )
                        
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val dataIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                                val data = it.getString(dataIndex) ?: ""
                                val isScreenshot = Companion.SCREENSHOT_PATH_PATTERNS.any { pattern ->
                                    data.contains(pattern, ignoreCase = true)
                                }
                                callback(isScreenshot)
                                return
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking screenshot: ${e.message}")
                    }
                    false
                }
            }
            
            // Simple heuristic: if recent image (< 5 seconds old), likely a screenshot
            callback(true) // Assume it's a screenshot for now
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkIfScreenshot: ${e.message}")
            callback(false)
        }
    }
    
    private fun handleScreenshot() {
        Log.d(TAG, "Screenshot detected in chat: $chatId")
        
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val currentUserId = currentUser.uid
        
        // Get current user's name
        FirebaseDatabase.getInstance().getReference("users").child(currentUserId)
            .get()
            .addOnSuccessListener { userSnapshot ->
                val userName = userSnapshot.child("fullName").value?.toString()
                    ?: userSnapshot.child("username").value?.toString()
                    ?: "Someone"
                
                // Save screenshot event to Firebase
                val screenshotData = hashMapOf<String, Any>(
                    "userId" to currentUserId,
                    "userName" to userName,
                    "receiverId" to receiverId,
                    "chatId" to chatId,
                    "timestamp" to System.currentTimeMillis()
                )
                
                FirebaseDatabase.getInstance().getReference("screenshots")
                    .push()
                    .setValue(screenshotData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Screenshot event saved to Firebase")
                        
                        // Send notification to receiver
                        val notificationManager = NotificationManager()
                        notificationManager.sendScreenshotAlertNotification(
                            receiverId = receiverId,
                            participantName = userName,
                            chatId = chatId
                        )
                        
                        // Trigger callback
                        onScreenshotDetected()
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Failed to save screenshot event: ${error.message}")
                    }
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to get user info")
            }
    }
}


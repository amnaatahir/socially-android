package com.example.smd_a1

import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class ScreenshotDetectionService : Service() {
    
    private lateinit var screenshotReceiver: ScreenshotReceiver
    
    companion object {
        private const val TAG = "ScreenshotDetectionService"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Screenshot detection service created")
        
        screenshotReceiver = ScreenshotReceiver { chatId ->
            handleScreenshotDetected(chatId)
        }
        
        // Note: Screenshot detection requires custom implementation
        // For now, we'll skip the receiver registration
        // val filter = IntentFilter(Intent.ACTION_SCREENSHOT)
        // registerReceiver(screenshotReceiver, filter)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Screenshot detection service started")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    private fun handleScreenshotDetected(chatId: String) {
        Log.d(TAG, "Screenshot detected in chat: $chatId")
        
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val currentUserId = currentUser.uid
        
        // Get current user's name
        val usersRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId)
        usersRef.get().addOnSuccessListener { userSnapshot ->
            val userName = userSnapshot.child("fullName").value?.toString() ?: "Someone"
            
            // Log the screenshot event (for now, just log it)
            // In a real implementation, you would send a notification or update the database
            Log.d(TAG, "Screenshot taken by $userName in chat: $chatId")
            
            // You could also save this to Firebase database for notification purposes
            val screenshotData = mapOf(
                "userId" to currentUserId,
                "userName" to userName,
                "chatId" to chatId,
                "timestamp" to System.currentTimeMillis()
            )
            
            FirebaseDatabase.getInstance().getReference("screenshots")
                .push()
                .setValue(screenshotData)
                .addOnSuccessListener {
                    Log.d(TAG, "Screenshot data saved to Firebase")
                }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Failed to save screenshot data: ${error.message}")
                }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            // Only unregister if receiver was registered
            if (::screenshotReceiver.isInitialized) {
                unregisterReceiver(screenshotReceiver)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering screenshot receiver", e)
        }
        Log.d(TAG, "Screenshot detection service destroyed")
    }
}

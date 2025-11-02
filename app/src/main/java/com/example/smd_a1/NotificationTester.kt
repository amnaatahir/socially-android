package com.example.smd_a1

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * Helper class to test push notifications
 * Use this to verify FCM token registration and send test notifications
 */
object NotificationTester {
    private const val TAG = "NotificationTester"
    
    /**
     * Test if FCM token is registered for current user
     */
    fun checkFCMToken(context: Context) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "No user logged in")
            return
        }
        
        val db = FirebaseDatabase.getInstance()
        db.getReference("users").child(currentUser.uid).child("fcmToken").get()
            .addOnSuccessListener { snapshot ->
                val token = snapshot.getValue(String::class.java)
                if (token != null && token.isNotEmpty()) {
                    Log.d(TAG, "✅ FCM Token found: ${token.take(20)}...")
                    Toast.makeText(context, "FCM Token registered!\n${token.take(20)}...", Toast.LENGTH_LONG).show()
                } else {
                    Log.w(TAG, "❌ No FCM Token found")
                    Toast.makeText(context, "No FCM Token found. Initializing...", Toast.LENGTH_SHORT).show()
                    
                    // Try to initialize token
                    val notificationManager = NotificationManager()
                    notificationManager.updateFCMToken()
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to check FCM token: ${error.message}")
                Toast.makeText(context, "Error checking token: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    /**
     * Test sending a notification to yourself
     */
    fun sendTestNotificationToSelf(context: Context) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val notificationManager = NotificationManager()
        try {
            // Test like notification
            notificationManager.sendLikeNotification(
                receiverId = currentUser.uid,
                likerName = "Test User",
                postId = "test_post_123",
                postType = "post"
            )
            Toast.makeText(context, "Test like notification sent!", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Test notification sent to ${currentUser.uid}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send test notification: ${e.message}", e)
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Get FCM token for a specific user (for testing)
     */
    fun getFCMTokenForUser(userId: String, onResult: (String?) -> Unit) {
        val db = FirebaseDatabase.getInstance()
        db.getReference("users").child(userId).child("fcmToken").get()
            .addOnSuccessListener { snapshot ->
                val token = snapshot.getValue(String::class.java)
                Log.d(TAG, "FCM Token for user $userId: ${token?.take(20)}...")
                onResult(token)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to get FCM token: ${error.message}")
                onResult(null)
            }
    }
    
    /**
     * Test notification permission
     */
    fun checkNotificationPermission(context: Context) {
        val notificationInitializer = NotificationInitializer(context)
        val isEnabled = notificationInitializer.areNotificationsEnabled()
        
        if (isEnabled) {
            Toast.makeText(context, "✅ Notifications are enabled", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Notifications are enabled")
        } else {
            Toast.makeText(context, "❌ Notifications are disabled. Please enable in Settings", Toast.LENGTH_LONG).show()
            Log.w(TAG, "Notifications are disabled")
        }
    }
    
    /**
     * Log all notification settings for debugging
     */
    fun logNotificationSettings(context: Context) {
        val notificationInitializer = NotificationInitializer(context)
        val settings = notificationInitializer.getNotificationSettings()
        
        Log.d(TAG, "=== Notification Settings ===")
        settings.forEach { (channel, enabled) ->
            Log.d(TAG, "$channel: $enabled")
        }
    }
}

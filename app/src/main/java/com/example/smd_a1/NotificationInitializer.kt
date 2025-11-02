package com.example.smd_a1

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

class NotificationInitializer(private val context: Context) {
    
    companion object {
        private const val TAG = "NotificationInitializer"
        
        // Notification channel IDs
        const val CHANNEL_ID_MESSAGE = "message_channel"
        const val CHANNEL_ID_FOLLOW = "follow_channel"
        const val CHANNEL_ID_SCREENSHOT = "screenshot_channel"
        const val CHANNEL_ID_LIKE = "like_channel"
        const val CHANNEL_ID_MENTION = "mention_channel"
        const val CHANNEL_ID_CALL = "call_channel"
        const val CHANNEL_ID_GENERAL = "general_channel"
    }
    
    /**
     * Initialize all notification channels and FCM token
     */
    fun initialize() {
        Log.i(TAG, "initialize: begin")
        createNotificationChannels()
        requestNotificationPermission()
        initializeFCMToken()
        Log.i(TAG, "initialize: invoked channel+permission+token setup")
    }
    
    /**
     * Create all notification channels for different types of notifications
     */
    private fun createNotificationChannels() {
        Log.i(TAG, "createNotificationChannels: SDK=${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Message notifications channel
            val messageChannel = NotificationChannel(
                CHANNEL_ID_MESSAGE,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New message notifications"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Follow notifications channel
            val followChannel = NotificationChannel(
                CHANNEL_ID_FOLLOW,
                "Follow Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Follow request notifications"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Screenshot notifications channel
            val screenshotChannel = NotificationChannel(
                CHANNEL_ID_SCREENSHOT,
                "Screenshot Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Screenshot detection notifications"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Like notifications channel
            val likeChannel = NotificationChannel(
                CHANNEL_ID_LIKE,
                "Likes",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Like notifications"
                enableLights(true)
                enableVibration(false)
                setShowBadge(true)
            }
            
            // Mention notifications channel
            val mentionChannel = NotificationChannel(
                CHANNEL_ID_MENTION,
                "Mentions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mention notifications"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Call notifications channel
            val callChannel = NotificationChannel(
                CHANNEL_ID_CALL,
                "Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Call notifications"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            
            // General notifications channel
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General notifications"
                enableLights(true)
                enableVibration(false)
                setShowBadge(true)
            }
            
            // Create all channels
            notificationManager.createNotificationChannel(messageChannel)
            notificationManager.createNotificationChannel(followChannel)
            notificationManager.createNotificationChannel(screenshotChannel)
            notificationManager.createNotificationChannel(likeChannel)
            notificationManager.createNotificationChannel(mentionChannel)
            notificationManager.createNotificationChannel(callChannel)
            notificationManager.createNotificationChannel(generalChannel)
            
            Log.d(TAG, "All notification channels created successfully")
        }
    }
    
    /**
     * Request notification permission for Android 13+
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = NotificationManagerCompat.from(context)
            if (!notificationManager.areNotificationsEnabled()) {
                Log.w(TAG, "Notifications are not enabled. User needs to enable them manually.")
            } else {
                Log.d(TAG, "Notifications are enabled")
            }
        } else {
            Log.d(TAG, "Notification permission: not required on this SDK")
        }
    }
    
    /**
     * Initialize FCM token and save it to Firebase Database
     */
    private fun initializeFCMToken() {
        Log.i(TAG, "initializeFCMToken: requesting token")
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "FCM Registration Token: $token")

            // Save token to Firebase Database
            saveTokenToFirebase(token)
        }
    }
    
    /**
     * Save FCM token to Firebase Database for the current user
     */
    private fun saveTokenToFirebase(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("users")
                .child(currentUser.uid)
            
        Log.i(TAG, "Saving FCM token to users/${currentUser.uid}/fcmToken")
        userRef.child("fcmToken").setValue(token)
                .addOnSuccessListener {
                    Log.d(TAG, "FCM token saved successfully for user ${currentUser.uid}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save FCM token", e)
                }
        } else {
            Log.w(TAG, "No authenticated user found, cannot save FCM token")
        }
    }
    
    /**
     * Check if notifications are enabled
     */
    fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            true // For older versions, assume enabled
        }
    }
    
    /**
     * Get notification settings for the current user
     */
    fun getNotificationSettings(): Map<String, Boolean> {
        val notificationManager = NotificationManagerCompat.from(context)
        val channels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemNotificationManager.notificationChannels
        } else {
            emptyList()
        }
        
        val settings = mutableMapOf<String, Boolean>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channels.forEach { channel ->
                settings[channel.id] = channel.importance != NotificationManager.IMPORTANCE_NONE
            }
        }
        
        return settings
    }
}

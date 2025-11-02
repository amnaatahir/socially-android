package com.example.smd_a1

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID_MESSAGE = "message_channel"
        private const val CHANNEL_ID_FOLLOW = "follow_channel"
        private const val CHANNEL_ID_SCREENSHOT = "screenshot_channel"
        private const val CHANNEL_ID_LIKE = "like_channel"
        private const val CHANNEL_ID_MENTION = "mention_channel"
        private const val CHANNEL_ID_CALL = "call_channel"
        private const val CHANNEL_ID_GENERAL = "general_channel"
        
        private const val NOTIFICATION_ID_MESSAGE = 1001
        private const val NOTIFICATION_ID_FOLLOW = 1002
        private const val NOTIFICATION_ID_SCREENSHOT = 1003
        private const val NOTIFICATION_ID_LIKE = 1004
        private const val NOTIFICATION_ID_MENTION = 1005
        private const val NOTIFICATION_ID_CALL = 1006
        private const val NOTIFICATION_ID_GENERAL = 1007
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "From: ${remoteMessage.from}")
        Log.d(TAG, "Message data payload: ${remoteMessage.data}")
        Log.d(TAG, "Message notification payload: ${remoteMessage.notification}")

        // Handle different notification types
        val notificationType = remoteMessage.data["type"] ?: "message"
        Log.i(TAG, "onMessageReceived: type=${notificationType} data=${remoteMessage.data}")
        
        when (notificationType) {
            "message" -> handleMessageNotification(remoteMessage)
            "follow_request" -> handleFollowRequestNotification(remoteMessage)
            "screenshot_alert" -> handleScreenshotAlertNotification(remoteMessage)
            "like" -> handleLikeNotification(remoteMessage)
            "mention" -> handleMentionNotification(remoteMessage)
            "call" -> handleCallNotification(remoteMessage)
            // Support server-driven incoming calls
            "call_incoming" -> handleCallNotification(remoteMessage)
            "post_shared" -> handlePostSharedNotification(remoteMessage)
            "story_viewed" -> handleStoryViewedNotification(remoteMessage)
            else -> handleDefaultNotification(remoteMessage)
        }
    }

    private fun handleMessageNotification(remoteMessage: RemoteMessage) {
        val senderName = remoteMessage.data["sender_name"] ?: "Someone"
        val messageText = remoteMessage.data["message_text"] ?: "New message"
        val chatId = remoteMessage.data["chat_id"] ?: ""
        val senderId = remoteMessage.data["sender_id"] ?: ""

        Log.d(TAG, "New message from: $senderName")
        
        val intent = Intent(this, chat::class.java).apply {
            putExtra("receiverId", senderId)
            putExtra("receiverName", senderName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val reqCode = (chatId + senderId + System.currentTimeMillis()).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.i(TAG, "handleMessage: pendingIntent reqCode=${reqCode} chatId=${chatId} senderId=${senderId}")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_MESSAGE)
            .setContentTitle("New message from $senderName")
            .setContentText(messageText)
            .setSmallIcon(R.drawable.chat_icon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        createNotificationChannel(CHANNEL_ID_MESSAGE, "Messages", "New message notifications")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = if (chatId.isNotEmpty()) chatId.hashCode() else System.currentTimeMillis().toInt()
        notificationManager.notify(id, notification)
        Log.i(TAG, "handleMessage: notified id=${id}")
    }

    private fun handleFollowRequestNotification(remoteMessage: RemoteMessage) {
        val requesterName = remoteMessage.data["requester_name"] ?: "Someone"
        val requesterId = remoteMessage.data["requester_id"] ?: ""

        Log.d(TAG, "Follow request from: $requesterName")
        
        val intent = Intent(this, profile::class.java).apply {
            putExtra("uid", requesterId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val reqCode = (requesterId + System.currentTimeMillis().toString()).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.i(TAG, "handleFollowRequest: pendingIntent reqCode=${reqCode} requesterId=${requesterId}")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_FOLLOW)
            .setContentTitle("New follow request")
            .setContentText("$requesterName wants to follow you")
            .setSmallIcon(R.drawable.profile_pic)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()

        createNotificationChannel(CHANNEL_ID_FOLLOW, "Follow Requests", "Follow request notifications")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = (requesterId.ifEmpty { System.currentTimeMillis().toString() }).hashCode()
        notificationManager.notify(id, notification)
        Log.i(TAG, "handleFollowRequest: notified id=${id}")
    }

    private fun handleScreenshotAlertNotification(remoteMessage: RemoteMessage) {
        val chatParticipant = remoteMessage.data["participant_name"] ?: "Someone"
        val chatId = remoteMessage.data["chat_id"] ?: ""

        Log.d(TAG, "Screenshot alert: $chatParticipant took a screenshot")
        
        val intent = Intent(this, chat::class.java).apply {
            putExtra("chatId", chatId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val reqCode = (chatId + System.currentTimeMillis().toString()).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.i(TAG, "handleScreenshot: pendingIntent reqCode=${reqCode} chatId=${chatId}")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SCREENSHOT)
            .setContentTitle("Screenshot Alert")
            .setContentText("$chatParticipant took a screenshot of your chat")
            .setSmallIcon(R.drawable.camera_switch)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        createNotificationChannel(CHANNEL_ID_SCREENSHOT, "Screenshot Alerts", "Screenshot detection notifications")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_SCREENSHOT, notification)
        Log.i(TAG, "handleScreenshot: notified id=${NOTIFICATION_ID_SCREENSHOT}")
    }

    private fun handleLikeNotification(remoteMessage: RemoteMessage) {
        val likerName = remoteMessage.data["liker_name"] ?: "Someone"
        val postType = remoteMessage.data["post_type"] ?: "post"
        val postId = remoteMessage.data["post_id"] ?: ""

        Log.d(TAG, "Like notification from: $likerName")
        
        val intent = Intent(this, feedpage::class.java).apply {
            putExtra("postId", postId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val reqCode = (likerName + postId + System.currentTimeMillis()).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.i(TAG, "handleLike: pendingIntent reqCode=${reqCode} postId=${postId}")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_LIKE)
            .setContentTitle("New like")
            .setContentText("$likerName liked your $postType")
            .setSmallIcon(R.drawable.red_heart)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()

        createNotificationChannel(CHANNEL_ID_LIKE, "Likes", "Like notifications")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_LIKE, notification)
        Log.i(TAG, "handleLike: notified id=${NOTIFICATION_ID_LIKE}")
    }

    private fun handleMentionNotification(remoteMessage: RemoteMessage) {
        val mentionerName = remoteMessage.data["mentioner_name"] ?: "Someone"
        val commentText = remoteMessage.data["comment_text"] ?: ""
        val postId = remoteMessage.data["post_id"] ?: ""

        Log.d(TAG, "Mention notification from: $mentionerName")
        
        val intent = Intent(this, feedpage::class.java).apply {
            putExtra("postId", postId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val reqCode = (postId + mentionerName + System.currentTimeMillis()).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.i(TAG, "handleMention: pendingIntent reqCode=${reqCode} postId=${postId}")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_MENTION)
            .setContentTitle("You were mentioned")
            .setContentText("$mentionerName mentioned you in a comment")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$mentionerName mentioned you: $commentText"))
            .setSmallIcon(R.drawable.profile_pic)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()

        createNotificationChannel(CHANNEL_ID_MENTION, "Mentions", "Mention notifications")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_MENTION, notification)
        Log.i(TAG, "handleMention: notified id=${NOTIFICATION_ID_MENTION}")
    }

    private fun handleCallNotification(remoteMessage: RemoteMessage) {
        val callerName = remoteMessage.data["caller_name"] ?: "Someone"
        val callType = remoteMessage.data["call_type"] ?: "voice"
        val callId = remoteMessage.data["call_id"] ?: ""
        val callerId = remoteMessage.data["caller_id"] ?: ""

        Log.d(TAG, "Call notification from: $callerName")
        
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("callId", callId)
            putExtra("callerName", callerName)
            putExtra("callerId", callerId)
            putExtra("callType", callType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Log.i(TAG, "handleCall: callId=${callId} callerId=${callerId} type=${callType}")
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.i(TAG, "handleCall: pendingIntent created")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_CALL)
            .setContentTitle("Incoming call")
            .setContentText("$callerName is calling you")
            .setSmallIcon(R.drawable.camera_switch)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        createNotificationChannel(CHANNEL_ID_CALL, "Calls", "Call notifications")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = if (callId.isNotEmpty()) callId.hashCode() else System.currentTimeMillis().toInt()
        notificationManager.notify(id, notification)
        Log.i(TAG, "handleCall: notified id=${id}")
    }

    private fun handlePostSharedNotification(remoteMessage: RemoteMessage) {
        val sharerName = remoteMessage.data["sharer_name"] ?: "Someone"
        val postId = remoteMessage.data["post_id"] ?: ""

        Log.d(TAG, "Post shared notification from: $sharerName")
        
        val intent = Intent(this, feedpage::class.java).apply {
            putExtra("postId", postId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.i(TAG, "handlePostShared: pendingIntent created postId=${postId}")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_GENERAL)
            .setContentTitle("Post shared")
            .setContentText("$sharerName shared your post")
            .setSmallIcon(R.drawable.profile_pic)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()

        createNotificationChannel(CHANNEL_ID_GENERAL, "General", "General notifications")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = (postId.ifEmpty { System.currentTimeMillis().toString() }).hashCode()
        notificationManager.notify(id, notification)
        Log.i(TAG, "handlePostShared: notified id=${id}")
    }

    private fun handleStoryViewedNotification(remoteMessage: RemoteMessage) {
        val viewerName = remoteMessage.data["viewer_name"] ?: "Someone"
        val storyId = remoteMessage.data["story_id"] ?: ""

        Log.d(TAG, "Story viewed notification from: $viewerName")
        
        val intent = Intent(this, viewstory::class.java).apply {
            putExtra("storyId", storyId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        Log.i(TAG, "handleStoryViewed: pendingIntent created storyId=${storyId}")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_GENERAL)
            .setContentTitle("Story viewed")
            .setContentText("$viewerName viewed your story")
            .setSmallIcon(R.drawable.profile_pic)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()

        createNotificationChannel(CHANNEL_ID_GENERAL, "General", "General notifications")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = (storyId.ifEmpty { System.currentTimeMillis().toString() }).hashCode()
        notificationManager.notify(id, notification)
        Log.i(TAG, "handleStoryViewed: notified id=${id}")
    }

    private fun handleDefaultNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "New notification"
        val body = remoteMessage.notification?.body ?: "You have a new notification"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_GENERAL)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.chat_icon)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        createNotificationChannel(CHANNEL_ID_GENERAL, "General", "General notifications")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_GENERAL, notification)
    }

    private fun createNotificationChannel(channelId: String, channelName: String, description: String) {
        Log.i(TAG, "createNotificationChannel: id=${channelId} name=${channelName}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                this.description = description
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        
        // Send token to Firebase Database for this user
        Log.i(TAG, "onNewToken: saving token to DB")
        sendTokenToServer(token)
    }

    private fun sendTokenToServer(token: String) {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("users")
                .child(currentUser.uid)
            
            userRef.child("fcmToken").setValue(token)
                .addOnSuccessListener {
                    Log.d(TAG, "FCM token saved successfully")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save FCM token", e)
                }
        }
    }
}

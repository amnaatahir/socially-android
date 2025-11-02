package com.example.smd_a1

import com.example.smd_a1.BuildConfig
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class NotificationManager {
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    
    companion object {
        private const val TAG = "NotificationManager"
        // Using HTTP V1 API (modern approach)
        private const val PROJECT_ID = "smda2-31b7e"
        private const val FCM_V1_URL = "https://fcm.googleapis.com/v1/projects/$PROJECT_ID/messages:send"
        
        // Service account credentials for V1 API
        private const val CLIENT_EMAIL = "firebase-adminsdk-fbsvc@smda2-31b7e.iam.gserviceaccount.com"
        // SECURITY: Service-account private key must NOT be committed to version control.
        // Load it at runtime from a secured source (e.g. BuildConfig field backed by a
        // gitignored local.properties value, the Android Keystore, or a backend proxy).
        // The FCM HTTP v1 send flow should ideally run on a trusted server, not the client.
        private val PRIVATE_KEY_PEM: String = BuildConfig.FCM_SERVICE_ACCOUNT_PRIVATE_KEY
    }

    /**
     * Send a new message notification
     */
    fun sendMessageNotification(
        receiverId: String,
        senderName: String,
        messageText: String,
        chatId: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "sendMessageNotification: to=${receiverId} chatId=${chatId}")
                val currentUserId = auth.currentUser?.uid ?: return@launch
                
                // Save notification to Firebase for "You" tab
                saveNotificationToFirebase(
                    receiverId = receiverId,
                    type = "message",
                    action = "sent you a message",
                    fromUserId = currentUserId,
                    fromUsername = senderName,
                    fromFullName = senderName,
                    additionalData = hashMapOf(
                        "message_text" to messageText,
                        "chat_id" to chatId
                    )
                )
                
                // Send push notification
                val receiverToken = getFCMToken(receiverId)
                Log.i(TAG, "sendMessageNotification: tokenPresent=${receiverToken != null}")
                if (receiverToken != null) {
                    val notificationData = hashMapOf<String, String>(
                        "type" to "message",
                        "sender_name" to senderName,
                        "message_text" to messageText,
                        "chat_id" to chatId,
                        "sender_id" to currentUserId
                    )
                    
                    sendNotification(receiverToken, "New message from $senderName", messageText, notificationData)
                    Log.d(TAG, "Message notification sent to $receiverId")
                } else {
                    Log.w(TAG, "No FCM token found for user $receiverId")
                }
                
                // Always show local notification as fallback
                showLocalNotification("New message from $senderName", messageText, "message")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message notification", e)
            }
        }
    }

    /**
     * Send a follow request notification
     */
    fun sendFollowRequestNotification(
        receiverId: String,
        requesterName: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "sendFollowRequestNotification: to=${receiverId}")
                val currentUserId = auth.currentUser?.uid ?: return@launch
                
                // Save notification to Firebase for "You" tab
                saveNotificationToFirebase(
                    receiverId = receiverId,
                    type = "follow_request",
                    action = "wants to follow you",
                    fromUserId = currentUserId,
                    fromUsername = requesterName,
                    fromFullName = requesterName
                )
                
                // Send push notification
                val receiverToken = getFCMToken(receiverId)
                Log.i(TAG, "sendFollowRequestNotification: tokenPresent=${receiverToken != null}")
                if (receiverToken != null) {
                    val notificationData = hashMapOf<String, String>(
                        "type" to "follow_request",
                        "requester_name" to requesterName,
                        "requester_id" to currentUserId
                    )
                    
                    sendNotification(receiverToken, "New follow request", "$requesterName wants to follow you", notificationData)
                    Log.d(TAG, "Follow request notification sent to $receiverId")
                } else {
                    Log.w(TAG, "No FCM token found for user $receiverId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send follow request notification", e)
            }
        }
    }

    /**
     * Send follow request accepted notification
     */
    fun sendFollowRequestAcceptedNotification(
        receiverId: String,
        accepterName: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "sendFollowRequestAcceptedNotification: to=${receiverId}")
                val currentUserId = auth.currentUser?.uid ?: return@launch
                
                // Save notification to Firebase for "You" tab
                saveNotificationToFirebase(
                    receiverId = receiverId,
                    type = "follow_request_accepted",
                    action = "accepted your follow request",
                    fromUserId = currentUserId,
                    fromUsername = accepterName,
                    fromFullName = accepterName
                )
                
                // Send push notification
                val receiverToken = getFCMToken(receiverId)
                Log.i(TAG, "sendFollowRequestAcceptedNotification: tokenPresent=${receiverToken != null}")
                if (receiverToken != null) {
                    val notificationData = hashMapOf<String, String>(
                        "type" to "follow_request_accepted",
                        "accepter_name" to accepterName,
                        "accepter_id" to currentUserId
                    )
                    
                    sendNotification(receiverToken, "Follow Request Accepted", "$accepterName accepted your follow request", notificationData)
                    Log.d(TAG, "Follow request accepted notification sent to $receiverId")
                } else {
                    Log.w(TAG, "No FCM token found for user $receiverId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send follow request accepted notification", e)
            }
        }
    }

    /**
     * Send a screenshot alert notification
     */
    fun sendScreenshotAlertNotification(
        receiverId: String,
        participantName: String,
        chatId: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "sendScreenshotAlertNotification: to=${receiverId} chatId=${chatId}")
                val currentUserId = auth.currentUser?.uid ?: return@launch
                
                // Save notification to Firebase for "You" tab
                saveNotificationToFirebase(
                    receiverId = receiverId,
                    type = "screenshot",
                    action = "took a screenshot of your chat",
                    fromUserId = currentUserId,
                    fromUsername = participantName,
                    fromFullName = participantName,
                    additionalData = hashMapOf(
                        "chat_id" to chatId
                    )
                )
                
                // Send push notification
                val receiverToken = getFCMToken(receiverId)
                Log.i(TAG, "sendScreenshotAlertNotification: tokenPresent=${receiverToken != null}")
                if (receiverToken != null) {
                    val notificationData = hashMapOf<String, String>(
                        "type" to "screenshot_alert",
                        "participant_name" to participantName,
                        "chat_id" to chatId
                    )
                    
                    sendNotification(receiverToken, "Screenshot Alert", "$participantName took a screenshot of your chat", notificationData)
                    Log.d(TAG, "Screenshot alert notification sent to $receiverId")
                } else {
                    Log.w(TAG, "No FCM token found for user $receiverId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send screenshot alert notification", e)
            }
        }
    }

    /**
     * Send a like notification
     */
    fun sendLikeNotification(
        receiverId: String,
        likerName: String,
        postId: String,
        postType: String = "post"
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "sendLikeNotification: to=${receiverId} postId=${postId} type=${postType}")
                val currentUserId = auth.currentUser?.uid ?: return@launch
                
                // Save notification to Firebase for "You" tab
                saveNotificationToFirebase(
                    receiverId = receiverId,
                    type = "like",
                    action = "liked your $postType",
                    fromUserId = currentUserId,
                    fromUsername = likerName,
                    fromFullName = likerName,
                    additionalData = hashMapOf(
                        "post_id" to postId,
                        "post_type" to postType
                    )
                )
                
                // Send push notification
                val receiverToken = getFCMToken(receiverId)
                Log.i(TAG, "sendLikeNotification: tokenPresent=${receiverToken != null}")
                if (receiverToken != null) {
                    val notificationData = hashMapOf<String, String>(
                        "type" to "like",
                        "liker_name" to likerName,
                        "post_id" to postId,
                        "post_type" to postType
                    )
                    
                    sendNotification(receiverToken, "New like", "$likerName liked your $postType", notificationData)
                    Log.d(TAG, "Like notification sent to $receiverId")
                } else {
                    Log.w(TAG, "No FCM token found for user $receiverId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send like notification", e)
            }
        }
    }

    /**
     * Send a comment notification
     */
    fun sendCommentNotification(
        receiverId: String,
        commenterName: String,
        postId: String,
        commentText: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "sendCommentNotification: to=${receiverId} postId=${postId}")
                val currentUserId = auth.currentUser?.uid ?: return@launch
                
                // Save notification to Firebase for "You" tab
                saveNotificationToFirebase(
                    receiverId = receiverId,
                    type = "comment",
                    action = "commented on your post",
                    fromUserId = currentUserId,
                    fromUsername = commenterName,
                    fromFullName = commenterName,
                    additionalData = hashMapOf(
                        "post_id" to postId,
                        "comment_text" to commentText
                    )
                )
                
                // Send push notification
                val receiverToken = getFCMToken(receiverId)
                Log.i(TAG, "sendCommentNotification: tokenPresent=${receiverToken != null}")
                if (receiverToken != null) {
                    val notificationData = hashMapOf<String, String>(
                        "type" to "comment",
                        "commenter_name" to commenterName,
                        "post_id" to postId,
                        "comment_text" to commentText
                    )
                    
                    sendNotification(receiverToken, "New comment", "$commenterName commented on your post", notificationData)
                    Log.d(TAG, "Comment notification sent to $receiverId")
                } else {
                    Log.w(TAG, "No FCM token found for user $receiverId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send comment notification", e)
            }
        }
    }

    /**
     * Send a mention notification
     */
    fun sendMentionNotification(
        receiverId: String,
        mentionerName: String,
        commentText: String,
        postId: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "sendMentionNotification: to=${receiverId} postId=${postId}")
                val currentUserId = auth.currentUser?.uid ?: return@launch
                
                // Save notification to Firebase for "You" tab
                saveNotificationToFirebase(
                    receiverId = receiverId,
                    type = "mention",
                    action = "mentioned you in a comment",
                    fromUserId = currentUserId,
                    fromUsername = mentionerName,
                    fromFullName = mentionerName,
                    additionalData = hashMapOf(
                        "post_id" to postId,
                        "comment_text" to commentText
                    )
                )
                
                // Send push notification
                val receiverToken = getFCMToken(receiverId)
                Log.i(TAG, "sendMentionNotification: tokenPresent=${receiverToken != null}")
                if (receiverToken != null) {
                    val notificationData = hashMapOf<String, String>(
                        "type" to "mention",
                        "mentioner_name" to mentionerName,
                        "comment_text" to commentText,
                        "post_id" to postId
                    )
                    
                    sendNotification(receiverToken, "You were mentioned", "$mentionerName mentioned you in a comment", notificationData)
                    Log.d(TAG, "Mention notification sent to $receiverId")
                } else {
                    Log.w(TAG, "No FCM token found for user $receiverId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send mention notification", e)
            }
        }
    }

    /**
     * Send a call notification
     */
    fun sendCallNotification(
        receiverId: String,
        callerName: String,
        callId: String,
        callType: String = "voice"
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "sendCallNotification: to=${receiverId} callId=${callId} type=${callType}")
                val currentUserId = auth.currentUser?.uid ?: return@launch
                
                // Save notification to Firebase for "You" tab
                saveNotificationToFirebase(
                    receiverId = receiverId,
                    type = "call",
                    action = "is calling you",
                    fromUserId = currentUserId,
                    fromUsername = callerName,
                    fromFullName = callerName,
                    additionalData = hashMapOf(
                        "call_id" to callId,
                        "call_type" to callType,
                        "caller_id" to currentUserId
                    )
                )
                
                // Send push notification
                val receiverToken = getFCMToken(receiverId)
                Log.i(TAG, "sendCallNotification: tokenPresent=${receiverToken != null}")
                if (receiverToken != null) {
                    val notificationData = hashMapOf<String, String>(
                        "type" to "call",
                        "caller_name" to callerName,
                        "call_id" to callId,
                        "call_type" to callType,
                        "caller_id" to currentUserId
                    )
                    
                    sendNotification(receiverToken, "Incoming call", "$callerName is calling you", notificationData)
                    Log.d(TAG, "Call notification sent to $receiverId")
                } else {
                    Log.w(TAG, "No FCM token found for user $receiverId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send call notification", e)
            }
        }
    }

    /**
     * Send a post shared notification
     */
    fun sendPostSharedNotification(
        receiverId: String,
        sharerName: String,
        postId: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "sendPostSharedNotification: to=${receiverId} postId=${postId}")
                val currentUserId = auth.currentUser?.uid ?: return@launch
                
                // Save notification to Firebase for "You" tab
                saveNotificationToFirebase(
                    receiverId = receiverId,
                    type = "post_shared",
                    action = "shared your post",
                    fromUserId = currentUserId,
                    fromUsername = sharerName,
                    fromFullName = sharerName,
                    additionalData = hashMapOf(
                        "post_id" to postId
                    )
                )
                
                // Send push notification
                val receiverToken = getFCMToken(receiverId)
                Log.i(TAG, "sendPostSharedNotification: tokenPresent=${receiverToken != null}")
                if (receiverToken != null) {
                    val notificationData = hashMapOf<String, String>(
                        "type" to "post_shared",
                        "sharer_name" to sharerName,
                        "post_id" to postId
                    )
                    
                    sendNotification(receiverToken, "Post shared", "$sharerName shared your post", notificationData)
                    Log.d(TAG, "Post shared notification sent to $receiverId")
                } else {
                    Log.w(TAG, "No FCM token found for user $receiverId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send post shared notification", e)
            }
        }
    }

    /**
     * Send a story viewed notification
     */
    fun sendStoryViewedNotification(
        receiverId: String,
        viewerName: String,
        storyId: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "sendStoryViewedNotification: to=${receiverId} storyId=${storyId}")
                val currentUserId = auth.currentUser?.uid ?: return@launch
                
                // Save notification to Firebase for "You" tab
                saveNotificationToFirebase(
                    receiverId = receiverId,
                    type = "story_viewed",
                    action = "viewed your story",
                    fromUserId = currentUserId,
                    fromUsername = viewerName,
                    fromFullName = viewerName,
                    additionalData = hashMapOf(
                        "story_id" to storyId
                    )
                )
                
                // Send push notification
                val receiverToken = getFCMToken(receiverId)
                Log.i(TAG, "sendStoryViewedNotification: tokenPresent=${receiverToken != null}")
                if (receiverToken != null) {
                    val notificationData = hashMapOf<String, String>(
                        "type" to "story_viewed",
                        "viewer_name" to viewerName,
                        "story_id" to storyId
                    )
                    
                    sendNotification(receiverToken, "Story viewed", "$viewerName viewed your story", notificationData)
                    Log.d(TAG, "Story viewed notification sent to $receiverId")
                } else {
                    Log.w(TAG, "No FCM token found for user $receiverId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send story viewed notification", e)
            }
        }
    }

    /**
     * Save notification to Firebase for "You" tab display
     */
    private suspend fun saveNotificationToFirebase(
        receiverId: String,
        type: String,
        action: String,
        fromUserId: String,
        fromUsername: String,
        fromFullName: String,
        additionalData: HashMap<String, String>? = null
    ) {
        try {
            val notificationsRef = database.getReference("notifications").child(receiverId)
            val notificationId = notificationsRef.push().key ?: return
            
            val notificationData = hashMapOf<String, Any>(
                "type" to type,
                "action" to action,
                "fromUserId" to fromUserId,
                "fromUsername" to fromUsername,
                "fromFullName" to fromFullName,
                "timestamp" to System.currentTimeMillis(),
                "read" to false
            )
            
            // Add additional data if provided
            additionalData?.let { data ->
                notificationData.putAll(data)
            }
            
            notificationsRef.child(notificationId).setValue(notificationData)
                .addOnSuccessListener {
                    Log.d(TAG, "Notification saved to Firebase for user $receiverId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save notification to Firebase", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving notification to Firebase", e)
        }
    }

    /**
     * Get FCM token for a user
     */
    private suspend fun getFCMToken(userId: String): String? {
        return try {
            val userRef = database.getReference("users").child(userId).child("fcmToken")
            val snapshot = userRef.get().await()
            val token = snapshot.getValue(String::class.java)
            Log.d(TAG, "getFCMToken: user=${userId} tokenPresent=${token != null}")
            token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FCM token for user $userId", e)
            null
        }
    }

    /**
     * Send notification via FCM HTTP V1 API with proper RSA signing
     */
    private suspend fun sendNotification(
        token: String,
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        try {
            Log.d(TAG, "🚀 Sending notification via HTTP V1 API")
            Log.d(TAG, "📱 To: $token")
            Log.d(TAG, "📱 Title: $title")
            Log.d(TAG, "📱 Body: $body")
            
            // Get OAuth 2.0 access token with proper RSA signing
            val accessToken = getAccessTokenWithRSA()
            if (accessToken == null) {
                Log.e(TAG, "❌ Failed to get access token")
                return
            }
            
            // Send notification using V1 API
            sendNotificationWithToken(accessToken, token, title, body, data)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending notification via V1 API", e)
        }
    }
    
    /**
     * Get OAuth 2.0 access token using proper RSA signing
     */
    private suspend fun getAccessTokenWithRSA(): String? {
        try {
            Log.d(TAG, "🔑 Getting OAuth 2.0 access token with RSA signing...")
            
            val url = URL("https://oauth2.googleapis.com/token")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            
            // Create JWT with proper RSA signing
            val jwt = createJWTWithRSA()
            
            val postData = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt"
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)
            writer.write(postData)
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "OAuth response code: $responseCode")
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonResponse = JSONObject(response)
                val accessToken = jsonResponse.getString("access_token")
                Log.d(TAG, "✅ Access token obtained successfully")
                return accessToken
            } else {
                val errorResponse = connection.errorStream.bufferedReader().readText()
                Log.e(TAG, "❌ Failed to get access token. Response: $errorResponse")
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting access token", e)
            return null
        }
    }
    
    /**
     * Create JWT with proper RSA signing
     */
    private fun createJWTWithRSA(): String {
        val header = JSONObject().apply {
            put("alg", "RS256")
            put("typ", "JWT")
        }
        
        val now = System.currentTimeMillis() / 1000
        val payload = JSONObject().apply {
            put("iss", CLIENT_EMAIL)
            put("scope", "https://www.googleapis.com/auth/firebase.messaging")
            put("aud", "https://oauth2.googleapis.com/token")
            put("exp", now + 3600) // 1 hour
            put("iat", now)
        }
        
        val headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toString().toByteArray())
        val payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().toByteArray())
        
        // Create RSA signature
        val signature = createRSASignature("$headerB64.$payloadB64")
        val signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
        
        return "$headerB64.$payloadB64.$signatureB64"
    }
    
    /**
     * Create RSA signature using the private key (proper signing)
     */
    private fun createRSASignature(data: String): ByteArray {
        try {
            // Parse the private key
            val privateKey = parsePrivateKey()
            
            // Create signature using SHA256withRSA
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(privateKey)
            signature.update(data.toByteArray())
            
            return signature.sign()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating RSA signature", e)
            // Fallback to a simple approach for demo
            return data.toByteArray()
        }
    }
    
    /**
     * Parse the private key from PEM format
     */
    private fun parsePrivateKey(): PrivateKey {
        val keyPem = PRIVATE_KEY_PEM
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
        
        val keyBytes = Base64.getDecoder().decode(keyPem)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        
        return keyFactory.generatePrivate(keySpec)
    }
    
    /**
     * Send notification using HTTP V1 API with access token
     */
    private suspend fun sendNotificationWithToken(
        accessToken: String,
        receiverToken: String,
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        try {
            val url = URL(FCM_V1_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val message = JSONObject().apply {
                put("token", receiverToken)
                put("notification", JSONObject().apply {
                    put("title", title)
                    put("body", body)
                })
                put("data", JSONObject().apply {
                    data.forEach { (key, value) ->
                        put(key, value)
                    }
                })
            }
            
            val requestBody = JSONObject().apply {
                put("message", message)
            }
            
            Log.d(TAG, "📤 Sending V1 payload: ${requestBody.toString()}")
            
            val outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()
            
            val responseCode = connection.responseCode
            val responseBody = if (responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream.bufferedReader().readText()
            }
            
            Log.d(TAG, "📥 FCM V1 response code: $responseCode")
            Log.d(TAG, "📥 FCM V1 response body: $responseBody")
            
            if (responseCode == 200) {
                Log.d(TAG, "✅ Notification sent successfully via HTTP V1 API!")
            } else {
                Log.e(TAG, "❌ FCM V1 failed with response code: $responseCode")
                Log.e(TAG, "❌ Response body: $responseBody")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending notification via V1 API", e)
        }
    }

    /**
     * Update user's FCM token
     */
    fun updateFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "FCM Registration Token: $token")

            val currentUser = auth.currentUser
            if (currentUser != null) {
                val userRef = database.getReference("users").child(currentUser.uid)
                userRef.child("fcmToken").setValue(token)
                    .addOnSuccessListener {
                        Log.d(TAG, "FCM token updated successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update FCM token", e)
                    }
            }
        }
    }
    
    /**
     * Show local notification as fallback when FCM fails
     */
    private fun showLocalNotification(title: String, body: String, type: String) {
        try {
            // This would require Android Context, so we'll log it for now
            Log.d(TAG, "Local notification fallback: $title - $body")
            
            // In a real implementation, you would use NotificationCompat.Builder here
            // For now, we'll just log the notification
            Log.i(TAG, "📱 NOTIFICATION: $title")
            Log.i(TAG, "📱 MESSAGE: $body")
            Log.i(TAG, "📱 TYPE: $type")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show local notification", e)
        }
    }
}

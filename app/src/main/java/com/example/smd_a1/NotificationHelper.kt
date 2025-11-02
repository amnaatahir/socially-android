package com.example.smd_a1

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

/**
 * Helper class for sending notifications throughout the app
 * This provides easy-to-use methods for common notification scenarios
 */
class NotificationHelper {
    
    private val auth = FirebaseAuth.getInstance()
    private val notificationManager = NotificationManager()
    
    companion object {
        private const val TAG = "NotificationHelper"
    }
    
    /**
     * Send notification when someone likes a post
     */
    fun notifyPostLiked(postOwnerId: String, likerName: String, postId: String, postType: String = "post") {
        if (postOwnerId == auth.currentUser?.uid) return // Don't notify self
        
        Log.d(TAG, "Sending like notification: $likerName liked $postOwnerId's $postType")
        notificationManager.sendLikeNotification(
            receiverId = postOwnerId,
            likerName = likerName,
            postId = postId,
            postType = postType
        )
    }
    
    /**
     * Send notification when someone sends a follow request
     */
    fun notifyFollowRequest(receiverId: String, requesterName: String) {
        if (receiverId == auth.currentUser?.uid) return // Don't notify self
        
        Log.d(TAG, "Sending follow request notification: $requesterName wants to follow $receiverId")
        notificationManager.sendFollowRequestNotification(
            receiverId = receiverId,
            requesterName = requesterName
        )
    }
    
    /**
     * Send notification when a follow request is accepted
     */
    fun notifyFollowRequestAccepted(requesterId: String, accepterName: String) {
        if (requesterId == auth.currentUser?.uid) return // Don't notify self
        
        Log.d(TAG, "Sending follow request accepted notification: $accepterName accepted $requesterId's request")
        notificationManager.sendFollowRequestAcceptedNotification(
            receiverId = requesterId,
            accepterName = accepterName
        )
    }
    
    /**
     * Send notification when someone mentions a user in a comment
     */
    fun notifyUserMentioned(mentionedUserId: String, mentionerName: String, commentText: String, postId: String) {
        if (mentionedUserId == auth.currentUser?.uid) return // Don't notify self
        
        Log.d(TAG, "Sending mention notification: $mentionerName mentioned $mentionedUserId")
        notificationManager.sendMentionNotification(
            receiverId = mentionedUserId,
            mentionerName = mentionerName,
            commentText = commentText,
            postId = postId
        )
    }
    
    /**
     * Send notification when someone shares a post
     */
    fun notifyPostShared(postOwnerId: String, sharerName: String, postId: String) {
        if (postOwnerId == auth.currentUser?.uid) return // Don't notify self
        
        Log.d(TAG, "Sending post shared notification: $sharerName shared $postOwnerId's post")
        notificationManager.sendPostSharedNotification(
            receiverId = postOwnerId,
            sharerName = sharerName,
            postId = postId
        )
    }
    
    /**
     * Send notification when someone views a story
     */
    fun notifyStoryViewed(storyOwnerId: String, viewerName: String, storyId: String) {
        if (storyOwnerId == auth.currentUser?.uid) return // Don't notify self
        
        Log.d(TAG, "Sending story viewed notification: $viewerName viewed $storyOwnerId's story")
        notificationManager.sendStoryViewedNotification(
            receiverId = storyOwnerId,
            viewerName = viewerName,
            storyId = storyId
        )
    }
    
    /**
     * Send notification when someone sends a message
     */
    fun notifyMessageReceived(receiverId: String, senderName: String, messageText: String, chatId: String) {
        if (receiverId == auth.currentUser?.uid) return // Don't notify self
        
        Log.d(TAG, "Sending message notification: $senderName sent message to $receiverId")
        notificationManager.sendMessageNotification(
            receiverId = receiverId,
            senderName = senderName,
            messageText = messageText,
            chatId = chatId
        )
    }
    
    /**
     * Send notification when someone takes a screenshot
     */
    fun notifyScreenshotTaken(receiverId: String, participantName: String, chatId: String) {
        if (receiverId == auth.currentUser?.uid) return // Don't notify self
        
        Log.d(TAG, "Sending screenshot notification: $participantName took screenshot in chat with $receiverId")
        notificationManager.sendScreenshotAlertNotification(
            receiverId = receiverId,
            participantName = participantName,
            chatId = chatId
        )
    }
    
    /**
     * Send notification when someone initiates a call
     */
    fun notifyIncomingCall(receiverId: String, callerName: String, callId: String, callType: String = "voice") {
        if (receiverId == auth.currentUser?.uid) return // Don't notify self
        
        Log.d(TAG, "Sending call notification: $callerName calling $receiverId")
        notificationManager.sendCallNotification(
            receiverId = receiverId,
            callerName = callerName,
            callId = callId,
            callType = callType
        )
    }
    
    /**
     * Get the current user's display name for notifications
     */
    private fun getCurrentUserName(): String {
        return auth.currentUser?.displayName ?: "Someone"
    }
    
    /**
     * Check if notifications should be sent (user preferences, etc.)
     */
    private fun shouldSendNotification(receiverId: String): Boolean {
        // Add logic here to check user notification preferences
        // For now, always send notifications
        return true
    }
}

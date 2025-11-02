package com.example.smd_a1

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Comprehensive notification system for Instagram-like app
 */
class RealNotificationManager(private val context: Context) {
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val notificationsRef = database.getReference("notifications")
    private val usersRef = database.getReference("users")
    
    /**
     * Create notification for various actions
     */
    fun createNotification(
        targetUserId: String,
        fromUserId: String,
        action: String,
        type: NotificationType,
        postId: String? = null,
        commentId: String? = null
    ) {
        if (targetUserId == fromUserId) return // Don't notify self
        
        val notificationId = notificationsRef.push().key ?: return
        
        // Get from user data
        usersRef.child(fromUserId).get().addOnSuccessListener { userSnapshot ->
            val fromUsername = userSnapshot.child("username").value?.toString() ?: "user"
            val fromFullName = userSnapshot.child("fullName").value?.toString() ?: "User"
            
            val notification = NotificationData(
                notificationId = notificationId,
                fromUserId = fromUserId,
                fromUsername = fromUsername,
                fromFullName = fromFullName,
                targetUserId = targetUserId,
                action = action,
                type = type,
                postId = postId,
                commentId = commentId,
                timestamp = System.currentTimeMillis(),
                read = false
            )
            
            notificationsRef.child(targetUserId).child(notificationId).setValue(notification)
                .addOnSuccessListener {
                    Log.d("NotificationManager", "Notification created: $action from $fromUsername")
                }
                .addOnFailureListener { error ->
                    Log.e("NotificationManager", "Failed to create notification: ${error.message}")
                }
        }
    }
    
    /**
     * Handle like notification
     */
    fun handleLikeNotification(postAuthorId: String, postId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        createNotification(
            targetUserId = postAuthorId,
            fromUserId = currentUserId,
            action = "liked your post",
            type = NotificationType.LIKE,
            postId = postId
        )
    }
    
    /**
     * Handle comment notification
     */
    fun handleCommentNotification(postAuthorId: String, postId: String, commentId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        createNotification(
            targetUserId = postAuthorId,
            fromUserId = currentUserId,
            action = "commented on your post",
            type = NotificationType.COMMENT,
            postId = postId,
            commentId = commentId
        )
    }
    
    /**
     * Handle follow notification
     */
    fun handleFollowNotification(targetUserId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        createNotification(
            targetUserId = targetUserId,
            fromUserId = currentUserId,
            action = "started following you",
            type = NotificationType.FOLLOW
        )
    }
    
    /**
     * Handle mention notification
     */
    fun handleMentionNotification(targetUserId: String, postId: String, commentText: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        createNotification(
            targetUserId = targetUserId,
            fromUserId = currentUserId,
            action = "mentioned you in a comment: $commentText",
            type = NotificationType.MENTION,
            postId = postId
        )
    }
    
    /**
     * Load notifications for user
     */
    fun loadNotifications(
        userId: String,
        onSuccess: (List<NotificationData>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        notificationsRef.child(userId)
            .orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val notifications = mutableListOf<NotificationData>()
                    
                    snapshot.children.forEach { child ->
                        val notification = child.getValue(NotificationData::class.java)
                        if (notification != null) {
                            notifications.add(notification)
                        }
                    }
                    
                    // Sort by timestamp (newest first)
                    notifications.sortByDescending { it.timestamp }
                    onSuccess(notifications)
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e("NotificationManager", "Failed to load notifications: ${error.message}")
                    onFailure(error.message)
                }
            })
    }
    
    /**
     * Mark notification as read
     */
    fun markAsRead(userId: String, notificationId: String) {
        notificationsRef.child(userId).child(notificationId).child("read").setValue(true)
    }
    
    /**
     * Mark all notifications as read
     */
    fun markAllAsRead(userId: String) {
        notificationsRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val updates = hashMapOf<String, Any>()
                snapshot.children.forEach { child ->
                    updates["${child.key}/read"] = true
                }
                if (updates.isNotEmpty()) {
                    notificationsRef.child(userId).updateChildren(updates)
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("NotificationManager", "Failed to mark all as read: ${error.message}")
            }
        })
    }
    
    /**
     * Get unread notification count
     */
    fun getUnreadCount(userId: String, onSuccess: (Int) -> Unit) {
        notificationsRef.child(userId)
            .orderByChild("read")
            .equalTo(false)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onSuccess(snapshot.children.count().toInt())
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e("NotificationManager", "Failed to get unread count: ${error.message}")
                    onSuccess(0)
                }
            })
    }
}

/**
 * Notification data class
 */
data class NotificationData(
    val notificationId: String = "",
    val fromUserId: String = "",
    val fromUsername: String = "",
    val fromFullName: String = "",
    val targetUserId: String = "",
    val action: String = "",
    val type: NotificationType = NotificationType.LIKE,
    val postId: String? = null,
    val commentId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false
)

/**
 * Notification types
 */
enum class NotificationType {
    LIKE, COMMENT, FOLLOW, MENTION, POST, STORY
}

/**
 * Format timestamp for display
 */
fun formatNotificationTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60 * 1000 -> "now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h"
        diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}d"
        diff < 30 * 24 * 60 * 60 * 1000 -> "${diff / (7 * 24 * 60 * 60 * 1000)}w"
        else -> {
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

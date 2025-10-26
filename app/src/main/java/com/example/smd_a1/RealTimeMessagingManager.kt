package com.example.smd_a1

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Real-time messaging system with proper Firebase integration
 */
class RealTimeMessagingManager(private val context: Context) {
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val messagesRef = database.getReference("messages")
    private val chatsRef = database.getReference("chats")
    private val usersRef = database.getReference("users")
    private val presenceRef = database.getReference("presence")
    
    /**
     * Send a text message
     */
    fun sendTextMessage(
        receiverId: String,
        messageText: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUser = auth.currentUser ?: return
        val messageId = messagesRef.push().key ?: return
        
        // Get user data
        usersRef.child(currentUser.uid).get().addOnSuccessListener { userSnapshot ->
            val username = userSnapshot.child("username").value?.toString() ?: "user"
            val fullName = userSnapshot.child("fullName").value?.toString() ?: "User"
            
            val message = RealChatMessage(
                messageId = messageId,
                senderId = currentUser.uid,
                senderUsername = username,
                senderFullName = fullName,
                receiverId = receiverId,
                messageText = messageText,
                messageType = MessageType.TEXT,
                timestamp = System.currentTimeMillis(),
                isEdited = false,
                editedAt = 0L
            )
            
            val chatId = generateChatId(currentUser.uid, receiverId)
            
            // Save message
            messagesRef.child(messageId).setValue(message)
                .addOnSuccessListener {
                    // Update chat with last message
                    updateChatLastMessage(chatId, messageText, currentUser.uid, username)
                    
                    // Create notification
                    createMessageNotification(receiverId, username, messageText)
                    
                    onSuccess()
                }
                .addOnFailureListener { error ->
                    onFailure(error.message ?: "Failed to send message")
                }
        }
    }
    
    /**
     * Send an image message
     */
    fun sendImageMessage(
        receiverId: String,
        imageUrl: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUser = auth.currentUser ?: return
        val messageId = messagesRef.push().key ?: return
        
        usersRef.child(currentUser.uid).get().addOnSuccessListener { userSnapshot ->
            val username = userSnapshot.child("username").value?.toString() ?: "user"
            val fullName = userSnapshot.child("fullName").value?.toString() ?: "User"
            
            val message = RealChatMessage(
                messageId = messageId,
                senderId = currentUser.uid,
                senderUsername = username,
                senderFullName = fullName,
                receiverId = receiverId,
                messageText = "📷 Photo",
                messageType = MessageType.IMAGE,
                imageUrl = imageUrl,
                timestamp = System.currentTimeMillis(),
                isEdited = false,
                editedAt = 0L
            )
            
            val chatId = generateChatId(currentUser.uid, receiverId)
            
            messagesRef.child(messageId).setValue(message)
                .addOnSuccessListener {
                    updateChatLastMessage(chatId, "📷 Photo", currentUser.uid, username)
                    createMessageNotification(receiverId, username, "sent a photo")
                    onSuccess()
                }
                .addOnFailureListener { error ->
                    onFailure(error.message ?: "Failed to send image")
                }
        }
    }
    
    /**
     * Load messages for a chat
     */
    fun loadMessages(
        otherUserId: String,
        onMessageReceived: (RealChatMessage) -> Unit,
        onError: (String) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        messagesRef.orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.forEach { child ->
                        val message = child.getValue(RealChatMessage::class.java)
                        if (message != null) {
                            // Check if message is for this chat
                            if ((message.senderId == currentUserId && message.receiverId == otherUserId) ||
                                (message.senderId == otherUserId && message.receiverId == currentUserId)) {
                                onMessageReceived(message)
                            }
                        }
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }
    
    /**
     * Load chat list for current user
     */
    fun loadChatList(
        onChatReceived: (RealChatRoom) -> Unit,
        onError: (String) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        chatsRef.orderByChild("participants/$currentUserId").equalTo(true)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.forEach { child ->
                        val chat = child.getValue(RealChatRoom::class.java)
                        if (chat != null) {
                            onChatReceived(chat)
                        }
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }
    
    /**
     * Update user presence
     */
    fun updatePresence(isOnline: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        val presence = mapOf(
            "isOnline" to isOnline,
            "lastSeen" to System.currentTimeMillis()
        )
        
        presenceRef.child(currentUserId).setValue(presence)
    }
    
    /**
     * Listen for user presence changes
     */
    fun listenForPresence(
        userId: String,
        onPresenceChanged: (Boolean, Long) -> Unit
    ) {
        presenceRef.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                onPresenceChanged(isOnline, lastSeen)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("MessagingManager", "Failed to listen for presence: ${error.message}")
            }
        })
    }
    
    /**
     * Mark messages as read
     */
    fun markMessagesAsRead(chatId: String, otherUserId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        messagesRef.orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val updates = hashMapOf<String, Any>()
                    
                    snapshot.children.forEach { child ->
                        val message = child.getValue(RealChatMessage::class.java)
                        if (message != null && 
                            message.senderId == otherUserId && 
                            message.receiverId == currentUserId &&
                            !message.read) {
                            updates["${child.key}/read"] = true
                            updates["${child.key}/readAt"] = System.currentTimeMillis()
                        }
                    }
                    
                    if (updates.isNotEmpty()) {
                        messagesRef.updateChildren(updates)
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e("MessagingManager", "Failed to mark messages as read: ${error.message}")
                }
            })
    }
    
    /**
     * Generate unique chat ID
     */
    private fun generateChatId(userId1: String, userId2: String): String {
        return listOf(userId1, userId2).sorted().joinToString("_")
    }
    
    /**
     * Update chat with last message
     */
    private fun updateChatLastMessage(chatId: String, lastMessage: String, senderId: String, senderUsername: String) {
        val chat = RealChatRoom(
            chatId = chatId,
            participants = listOf(senderId, chatId.split("_").first { it != senderId }),
            lastMessage = lastMessage,
            lastMessageTime = System.currentTimeMillis(),
            lastMessageSender = senderUsername,
            unreadCount = 0
        )
        
        chatsRef.child(chatId).setValue(chat)
    }
    
    /**
     * Create message notification
     */
    private fun createMessageNotification(receiverId: String, senderUsername: String, messageText: String) {
        val notificationId = database.getReference("notifications").push().key ?: return
        
        val notification = mapOf(
            "notificationId" to notificationId,
            "fromUsername" to senderUsername,
            "action" to "sent you a message: $messageText",
            "type" to "message",
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )
        
        database.getReference("notifications").child(receiverId).child(notificationId).setValue(notification)
    }
}

/**
 * Real chat message data class
 */
data class RealChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val senderFullName: String = "",
    val receiverId: String = "",
    val messageText: String = "",
    val messageType: MessageType = MessageType.TEXT,
    val imageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isEdited: Boolean = false,
    val editedAt: Long = 0L,
    val read: Boolean = false,
    val readAt: Long = 0L
)

/**
 * Real chat room data class
 */
data class RealChatRoom(
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTime: Long = 0,
    val lastMessageSender: String = "",
    val unreadCount: Int = 0
)

/**
 * Format message timestamp
 */
fun formatMessageTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60 * 1000 -> "now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h"
        else -> {
            val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

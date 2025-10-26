package com.example.smd_a1

import java.text.SimpleDateFormat
import java.util.*

// Data classes for chat functionality
data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val messageText: String = "",
    val messageType: MessageType = MessageType.TEXT,
    val imageUrl: String = "",
    val postId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isEdited: Boolean = false,
    val editedAt: Long = 0
)

enum class MessageType {
    TEXT, IMAGE, POST
}

data class ChatRoom(
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTime: Long = 0,
    val lastMessageSender: String = "",
    val unreadCount: Int = 0
)

data class UserPresence(
    val userId: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0
)

// Helper functions
object ChatUtils {
    fun generateChatId(userId1: String, userId2: String): String {
        return listOf(userId1, userId2).sorted().joinToString("_")
    }
    
    fun formatTimestamp(timestamp: Long): String {
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
    
    fun canEditOrDelete(timestamp: Long): Boolean {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return diff <= 5 * 60 * 1000 // 5 minutes in milliseconds
    }
}

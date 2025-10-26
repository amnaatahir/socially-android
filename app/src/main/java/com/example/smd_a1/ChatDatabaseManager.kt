package com.example.smd_a1

import android.content.Context
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ChatDatabaseManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val messagesRef = database.getReference("messages")
    private val chatsRef = database.getReference("chats")
    private val presenceRef = database.getReference("presence")
    private val usersRef = database.getReference("users")
    
    // Store listeners for cleanup
    private var messagesListener: ValueEventListener? = null
    private var chatsListener: ValueEventListener? = null
    private var presenceListener: ValueEventListener? = null

    // Send a text message
    fun sendTextMessage(receiverId: String, messageText: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val currentUser = auth.currentUser ?: return
        val messageId = messagesRef.push().key ?: return
        
        val message = ChatMessage(
            messageId = messageId,
            senderId = currentUser.uid,
            receiverId = receiverId,
            messageText = messageText,
            messageType = MessageType.TEXT,
            timestamp = System.currentTimeMillis()
        )
        
        val chatId = ChatUtils.generateChatId(currentUser.uid, receiverId)
        
        // Save message
        messagesRef.child(messageId).setValue(message)
            .addOnSuccessListener {
                // Update chat with last message
                updateChatLastMessage(chatId, messageText, currentUser.uid)
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onFailure(exception.message ?: "Failed to send message")
            }
    }
    
    // Send an image message
    fun sendImageMessage(receiverId: String, imageUrl: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val currentUser = auth.currentUser ?: return
        val messageId = messagesRef.push().key ?: return
        
        val message = ChatMessage(
            messageId = messageId,
            senderId = currentUser.uid,
            receiverId = receiverId,
            messageText = "📷 Image",
            messageType = MessageType.IMAGE,
            imageUrl = imageUrl,
            timestamp = System.currentTimeMillis()
        )
        
        val chatId = ChatUtils.generateChatId(currentUser.uid, receiverId)
        
        messagesRef.child(messageId).setValue(message)
            .addOnSuccessListener {
                updateChatLastMessage(chatId, "📷 Image", currentUser.uid)
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onFailure(exception.message ?: "Failed to send image")
            }
    }
    
    // Share a post in chat
    fun sharePost(receiverId: String, postId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val currentUser = auth.currentUser ?: return
        val messageId = messagesRef.push().key ?: return
        
        val message = ChatMessage(
            messageId = messageId,
            senderId = currentUser.uid,
            receiverId = receiverId,
            messageText = "📄 Shared a post",
            messageType = MessageType.POST,
            postId = postId,
            timestamp = System.currentTimeMillis()
        )
        
        val chatId = ChatUtils.generateChatId(currentUser.uid, receiverId)
        
        messagesRef.child(messageId).setValue(message)
            .addOnSuccessListener {
                updateChatLastMessage(chatId, "📄 Shared a post", currentUser.uid)
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onFailure(exception.message ?: "Failed to share post")
            }
    }
    
    // Edit a message (only within 5 minutes)
    fun editMessage(messageId: String, newText: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val currentUser = auth.currentUser ?: return
        
        messagesRef.child(messageId).get().addOnSuccessListener { snapshot ->
            val message = snapshot.getValue(ChatMessage::class.java) ?: return@addOnSuccessListener
            
            if (message.senderId != currentUser.uid) {
                onFailure("You can only edit your own messages")
                return@addOnSuccessListener
            }
            
            if (!ChatUtils.canEditOrDelete(message.timestamp)) {
                onFailure("Message can only be edited within 5 minutes")
                return@addOnSuccessListener
            }
            
            val updatedMessage = message.copy(
                messageText = newText,
                isEdited = true,
                editedAt = System.currentTimeMillis()
            )
            
            messagesRef.child(messageId).setValue(updatedMessage)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { exception ->
                    onFailure(exception.message ?: "Failed to edit message")
                }
        }
    }
    
    // Delete a message (only within 5 minutes)
    fun deleteMessage(messageId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val currentUser = auth.currentUser ?: return
        
        messagesRef.child(messageId).get().addOnSuccessListener { snapshot ->
            val message = snapshot.getValue(ChatMessage::class.java) ?: return@addOnSuccessListener
            
            if (message.senderId != currentUser.uid) {
                onFailure("You can only delete your own messages")
                return@addOnSuccessListener
            }
            
            if (!ChatUtils.canEditOrDelete(message.timestamp)) {
                onFailure("Message can only be deleted within 5 minutes")
                return@addOnSuccessListener
            }
            
            messagesRef.child(messageId).removeValue()
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { exception ->
                    onFailure(exception.message ?: "Failed to delete message")
                }
        }
    }
    
    // Get messages for a chat
    fun getMessages(receiverId: String, onMessagesReceived: (List<ChatMessage>) -> Unit) {
        val currentUser = auth.currentUser ?: return
        
        // Remove old listener if exists
        messagesListener?.let { messagesRef.removeEventListener(it) }
        
        messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = mutableListOf<ChatMessage>()
                
                for (child in snapshot.children) {
                    val message = child.getValue(ChatMessage::class.java) ?: continue
                    
                    // Check if message is between current user and receiver
                    if ((message.senderId == currentUser.uid && message.receiverId == receiverId) ||
                        (message.senderId == receiverId && message.receiverId == currentUser.uid)) {
                        messages.add(message)
                    }
                }
                
                onMessagesReceived(messages.sortedBy { it.timestamp })
            }
            
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("ChatDB", "Failed to load messages: ${error.message}")
                Toast.makeText(context, "Failed to load messages", Toast.LENGTH_SHORT).show()
            }
        }
        messagesRef.orderByChild("timestamp").addValueEventListener(messagesListener!!)
    }
    
    // Remove all listeners
    fun removeAllListeners() {
        messagesListener?.let { messagesRef.removeEventListener(it) }
        messagesListener = null
        
        chatsListener?.let { chatsRef.removeEventListener(it) }
        chatsListener = null
        
        presenceListener?.let { 
            val currentUser = auth.currentUser?.uid ?: return
            presenceRef.child(currentUser).removeEventListener(it)
        }
        presenceListener = null
    }
    
    // Get chat list with last messages
    fun getChatList(onChatsReceived: (List<ChatRoom>) -> Unit) {
        val currentUser = auth.currentUser ?: return
        
        // Remove old listener if exists
        chatsListener?.let { chatsRef.removeEventListener(it) }
        
        chatsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val chats = mutableListOf<ChatRoom>()
                
                for (child in snapshot.children) {
                    val chat = child.getValue(ChatRoom::class.java) ?: continue
                    
                    // Check if current user is participant
                    if (currentUser.uid in chat.participants) {
                        chats.add(chat)
                    }
                }
                
                onChatsReceived(chats.sortedByDescending { it.lastMessageTime })
            }
            
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("ChatDB", "Failed to load chats: ${error.message}")
                Toast.makeText(context, "Failed to load chats", Toast.LENGTH_SHORT).show()
            }
        }
        chatsRef.addValueEventListener(chatsListener!!)
    }
    
    // Update user presence
    fun updateUserPresence(isOnline: Boolean) {
        val currentUser = auth.currentUser ?: return
        
        val presence = UserPresence(
            userId = currentUser.uid,
            isOnline = isOnline,
            lastSeen = System.currentTimeMillis()
        )
        
        presenceRef.child(currentUser.uid).setValue(presence)
    }
    
    // Get user presence
    fun getUserPresence(userId: String, onPresenceReceived: (UserPresence?) -> Unit) {
        // Remove old listener if exists (for current user's presence)
        presenceListener?.let {
            val currentUser = auth.currentUser?.uid
            if (currentUser != null) {
                presenceRef.child(currentUser).removeEventListener(it)
            }
        }
        
        presenceListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val presence = snapshot.getValue(UserPresence::class.java)
                onPresenceReceived(presence)
            }
            
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("ChatDB", "Failed to load presence: ${error.message}")
                onPresenceReceived(null)
            }
        }
        presenceRef.child(userId).addValueEventListener(presenceListener!!)
    }
    
    // Private helper function to update chat last message
    private fun updateChatLastMessage(chatId: String, lastMessage: String, senderId: String) {
        val chat = ChatRoom(
            chatId = chatId,
            participants = chatId.split("_"),
            lastMessage = lastMessage,
            lastMessageTime = System.currentTimeMillis(),
            lastMessageSender = senderId
        )
        
        chatsRef.child(chatId).setValue(chat)
    }
    
    // Create or get chat between two users (only if both are following each other)
    fun createOrGetChat(otherUserId: String, onChatCreated: (String) -> Unit, onFailure: (String) -> Unit) {
        val currentUser = auth.currentUser ?: return
        val chatId = ChatUtils.generateChatId(currentUser.uid, otherUserId)
        
        // First check if both users are following each other
        checkMutualFollowing(currentUser.uid, otherUserId) { isMutualFollowing ->
            if (!isMutualFollowing) {
                onFailure("You can only message users who follow you back")
                return@checkMutualFollowing
            }
            
            // If mutual following confirmed, proceed with chat creation
            chatsRef.child(chatId).get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    val chat = ChatRoom(
                        chatId = chatId,
                        participants = listOf(currentUser.uid, otherUserId),
                        lastMessage = "",
                        lastMessageTime = 0,
                        lastMessageSender = ""
                    )
                    chatsRef.child(chatId).setValue(chat)
                }
                onChatCreated(chatId)
            }
        }
    }
    
    // Check if two users are following each other
    private fun checkMutualFollowing(userId1: String, userId2: String, onResult: (Boolean) -> Unit) {
        val following1Ref = database.getReference("following").child(userId1).child(userId2)
        val following2Ref = database.getReference("following").child(userId2).child(userId1)
        
        following1Ref.get().addOnSuccessListener { snap1 ->
            following2Ref.get().addOnSuccessListener { snap2 ->
                onResult(snap1.exists() && snap2.exists())
            }
        }
    }
}

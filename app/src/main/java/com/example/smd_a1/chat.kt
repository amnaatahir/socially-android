package com.example.smd_a1

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.*

class chat : AppCompatActivity() {
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var cameraButton: ImageButton
    private lateinit var galleryButton: ImageButton
    private lateinit var micButton: ImageButton
    private lateinit var moreButton: ImageButton
    private lateinit var chatDatabaseManager: ChatDatabaseManager
    private lateinit var auth: FirebaseAuth
    private lateinit var receiverId: String
    private lateinit var receiverName: String
    private lateinit var callManager: CallManager
    private lateinit var notificationManager: NotificationManager
    
    private val messageList = mutableListOf<ChatMessage>()
    private val storage = FirebaseStorage.getInstance()
    private val database = Firebase.database
    private val usersRef = database.getReference("users")
    
    companion object {
        private const val PICK_IMAGE_REQUEST = 1
        private const val PICK_POST_REQUEST = 2
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        
        auth = FirebaseAuth.getInstance()
        chatDatabaseManager = ChatDatabaseManager(this)
        callManager = CallManager()
        notificationManager = NotificationManager()
        
        // Get receiver info from intent
        receiverId = intent.getStringExtra("receiverId") 
            ?: intent.getStringExtra("peerUid") 
            ?: "default_user"
        receiverName = intent.getStringExtra("receiverName") ?: "User"
        
        initializeViews()
        
        // Load receiver name from Firebase if not provided or just "User"
        if (receiverId != "default_user" && (receiverName == "User" || receiverName.isEmpty())) {
            loadReceiverInfo(receiverId)
        } else {
            // Name was provided, update UI
            findViewById<TextView>(R.id.tvTitle)?.text = receiverName
        }
        
        setupRecyclerView()
        setupClickListeners()
        loadMessages()
        updateUserPresence()
        
        // Auto-share post if provided
        val sharePostId = intent.getStringExtra("sharePostId")
        if (!sharePostId.isNullOrEmpty()) {
            sharePostInChat(sharePostId)
        }

        // Handle insets safely
        findViewById<View>(R.id.main)?.let { root ->
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
    }
    
    private fun initializeViews() {
        chatRecyclerView = findViewById(R.id.messageRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        cameraButton = findViewById(R.id.btnCamera)
        galleryButton = findViewById(R.id.btnGallery)
        micButton = findViewById(R.id.btnMic)
        moreButton = findViewById(R.id.btnMore)
        
        // Title will be updated after loading from Firebase if needed
        
        // Setup message input
        messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                sendButton.isEnabled = !s.isNullOrEmpty()
            }
        })
    }
    
    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messageList) { message ->
            showMessageOptions(message)
        }
        
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = messageAdapter
    }
    
    private fun setupClickListeners() {
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        findViewById<ImageButton>(R.id.btncall)?.setOnClickListener {
            initiateCall()
        }
        
        sendButton.setOnClickListener {
            sendTextMessage()
        }
        
        cameraButton.setOnClickListener {
            // For now, just show a toast - in real app, you'd open camera
            Toast.makeText(this, "Camera feature coming soon", Toast.LENGTH_SHORT).show()
        }
        
        galleryButton.setOnClickListener {
            openImagePicker()
        }
        
        micButton.setOnClickListener {
            Toast.makeText(this, "Voice message feature coming soon", Toast.LENGTH_SHORT).show()
        }
        
        moreButton.setOnClickListener {
            showMoreOptions()
        }
    }
    
    private fun sendTextMessage() {
        val messageText = messageEditText.text.toString().trim()
        if (messageText.isEmpty()) return
        
        chatDatabaseManager.sendTextMessage(
            receiverId = receiverId,
            messageText = messageText,
            onSuccess = {
                messageEditText.text.clear()
                Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
                
                // Send push notification to receiver
                val senderName = auth.currentUser?.displayName ?: "Someone"
                notificationManager.sendMessageNotification(
                    receiverId = receiverId,
                    senderName = senderName,
                    messageText = messageText,
                    chatId = generateChatId(auth.currentUser?.uid ?: "", receiverId)
                )
            },
            onFailure = { error ->
                Toast.makeText(this, "Failed to send message: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    private fun generateChatId(userId1: String, userId2: String): String {
        return listOf(userId1, userId2).sorted().joinToString("_")
    }
    
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }
    
    private fun showMoreOptions() {
        val options = arrayOf("Share Post", "Share Location", "Contact Info")
        AlertDialog.Builder(this)
            .setTitle("More Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> sharePost()
                    1 -> Toast.makeText(this, "Location sharing coming soon", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "Contact info sharing coming soon", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
    
    private fun sharePost() {
        // Open feed to select a post to share
        val intent = Intent(this, feed::class.java)
        intent.putExtra("selectPostToShare", true)
        intent.putExtra("receiverId", receiverId)
        startActivityForResult(intent, PICK_POST_REQUEST)
    }
    
    
    private fun sharePostInChat(postId: String) {
        chatDatabaseManager.sharePost(
            receiverId = receiverId,
            postId = postId,
            onSuccess = {
                Toast.makeText(this, "Post shared", Toast.LENGTH_SHORT).show()
            },
            onFailure = { error ->
                Toast.makeText(this, "Failed to share post: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    private fun loadMessages() {
        chatDatabaseManager.getMessages(receiverId) { messages ->
            messageList.clear()
            messageList.addAll(messages)
            messageAdapter.notifyDataSetChanged()
            
            // Scroll to bottom
            if (messageList.isNotEmpty()) {
                chatRecyclerView.scrollToPosition(messageList.size - 1)
            }
        }
    }
    
    private fun showMessageOptions(message: ChatMessage) {
        if (message.senderId != auth.currentUser?.uid) return
        if (!ChatUtils.canEditOrDelete(message.timestamp)) {
            Toast.makeText(this, "Message can only be edited/deleted within 5 minutes", Toast.LENGTH_SHORT).show()
            return
        }
        
        val options = arrayOf("Edit", "Delete")
        AlertDialog.Builder(this)
            .setTitle("Message Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editMessage(message)
                    1 -> deleteMessage(message)
                }
            }
            .show()
    }
    
    private fun editMessage(message: ChatMessage) {
        val editText = EditText(this)
        editText.setText(message.messageText)
        
        AlertDialog.Builder(this)
            .setTitle("Edit Message")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newText = editText.text.toString().trim()
                if (newText.isNotEmpty()) {
                    chatDatabaseManager.editMessage(
                        messageId = message.messageId,
                        newText = newText,
                        onSuccess = {
                            Toast.makeText(this, "Message edited", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { error ->
                            Toast.makeText(this, "Failed to edit message: $error", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteMessage(message: ChatMessage) {
        AlertDialog.Builder(this)
            .setTitle("Delete Message")
            .setMessage("Are you sure you want to delete this message?")
            .setPositiveButton("Delete") { _, _ ->
                chatDatabaseManager.deleteMessage(
                    messageId = message.messageId,
                    onSuccess = {
                        Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(this, "Failed to delete message: $error", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateUserPresence() {
        chatDatabaseManager.updateUserPresence(true)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == Activity.RESULT_OK && data != null) {
            when (requestCode) {
                PICK_IMAGE_REQUEST -> {
                    val imageUri = data.data
                    if (imageUri != null) {
                        uploadImageAndSend(imageUri)
                    }
                }
                PICK_POST_REQUEST -> {
                    val postId = data.getStringExtra("selectedPostId")
                    if (!postId.isNullOrEmpty()) {
                        sharePostInChat(postId)
                    }
                }
            }
        }
    }
    
    private fun uploadImageAndSend(imageUri: Uri) {
        val storageRef = storage.reference
        val imageRef = storageRef.child("chat_images/${System.currentTimeMillis()}.jpg")
        
        imageRef.putFile(imageUri)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    chatDatabaseManager.sendImageMessage(
                        receiverId = receiverId,
                        imageUrl = downloadUri.toString(),
                        onSuccess = {
                            Toast.makeText(this, "Image sent", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { error ->
                            Toast.makeText(this, "Failed to send image: $error", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to upload image: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private var screenshotObserver: android.database.ContentObserver? = null
    
    override fun onPause() {
        super.onPause()
        chatDatabaseManager.updateUserPresence(false)
        stopScreenshotDetection()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        chatDatabaseManager.removeAllListeners()
        chatDatabaseManager.updateUserPresence(false)
        stopScreenshotDetection()
    }
    
    override fun onResume() {
        super.onResume()
        chatDatabaseManager.updateUserPresence(true)
        startScreenshotDetection()
        listenForReceiverPresence()
    }
    
    private fun startScreenshotDetection() {
        if (receiverId == "default_user") return
        // Ensure we have media read permission for screenshot detection
        try {
            val hasPerm = if (android.os.Build.VERSION.SDK_INT >= 33) {
                checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (!hasPerm) {
                val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE
                requestPermissions(arrayOf(perm), 9011)
                return
            }
        } catch (_: Exception) { /* best-effort */ }
        val chatId = ChatUtils.generateChatId(auth.currentUser?.uid ?: "", receiverId)
        try {
            screenshotObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                private var lastCheckTime = 0L
                override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                    super.onChange(selfChange, uri)
                    val now = System.currentTimeMillis()
                    if (now - lastCheckTime < 2000) return
                    lastCheckTime = now
                    checkForScreenshot(chatId, uri)
                }
            }
            contentResolver.registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, screenshotObserver!!)
        } catch (e: Exception) {
            android.util.Log.e("Chat", "Failed to register screenshot observer: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 9011 && grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startScreenshotDetection()
        }
    }
    
    private fun stopScreenshotDetection() {
        screenshotObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                android.util.Log.e("Chat", "Failed to unregister screenshot observer: ${e.message}")
            }
            screenshotObserver = null
        }
    }
    
    private fun checkForScreenshot(chatId: String, uri: android.net.Uri?) {
        val currentUserId = auth.currentUser?.uid ?: return
        try {
            if (uri != null && isScreenshotUri(uri)) {
                handleScreenshotDetected(chatId, currentUserId)
                return
            }
            // Fallback: recent image heuristic
            val selection = "${android.provider.MediaStore.Images.Media.DATE_ADDED} > ?"
            val selectionArgs = arrayOf((System.currentTimeMillis() / 1000 - 5).toString())
            val cursor = contentResolver.query(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                if (android.os.Build.VERSION.SDK_INT >= 29) arrayOf(android.provider.MediaStore.Images.Media.DISPLAY_NAME, android.provider.MediaStore.Images.Media.RELATIVE_PATH) else arrayOf(android.provider.MediaStore.Images.Media.DATA),
                selection,
                selectionArgs,
                "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val isShot = if (android.os.Build.VERSION.SDK_INT >= 29) {
                        val nameIdx = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
                        val pathIdx = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.RELATIVE_PATH)
                        val name = it.getString(nameIdx) ?: ""
                        val rel = it.getString(pathIdx) ?: ""
                        name.contains("screenshot", true) || rel.contains("screenshot", true)
                    } else {
                        val dataIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
                        val data = it.getString(dataIndex) ?: ""
                        data.contains("/Screenshot", true) || data.contains("/Screenshots", true)
                    }
                    if (isShot) handleScreenshotDetected(chatId, currentUserId)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Chat", "Error checking screenshot: ${e.message}")
        }
    }

    private fun isScreenshotUri(uri: android.net.Uri): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val projection = arrayOf(
                    android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH
                )
                contentResolver.query(uri, projection, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val name = c.getString(c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)) ?: ""
                        val rel = c.getString(c.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.RELATIVE_PATH)) ?: ""
                        return name.contains("screenshot", true) || rel.contains("screenshot", true)
                    }
                }
                false
            } else {
                uri.toString().contains("screenshot", true)
            }
        } catch (_: Exception) {
            false
        }
    }
    
    private fun handleScreenshotDetected(chatId: String, currentUserId: String) {
        val lastDetectionKey = "last_screenshot_$chatId"
        val prefs = getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)
        val lastDetection = prefs.getLong(lastDetectionKey, 0)
        val now = System.currentTimeMillis()
        if (now - lastDetection < 5000) return
        prefs.edit().putLong(lastDetectionKey, now).apply()
        usersRef.child(currentUserId).get().addOnSuccessListener { snapshot ->
            val userName = snapshot.child("fullName").value?.toString() ?: snapshot.child("username").value?.toString() ?: "Someone"
            val screenshotData = hashMapOf<String, Any>("userId" to currentUserId, "userName" to userName, "receiverId" to receiverId, "chatId" to chatId, "timestamp" to now)
            database.getReference("screenshots").push().setValue(screenshotData).addOnSuccessListener {
                notificationManager.sendScreenshotAlertNotification(receiverId = receiverId, participantName = userName, chatId = chatId)
            }
        }
    }
    
    private fun listenForReceiverPresence() {
        val dot = findViewById<View>(R.id.presenceDot)
        chatDatabaseManager.getUserPresence(receiverId) { presence ->
            val online = presence?.isOnline == true
            android.util.Log.d("Chat", "Receiver presence: ${if (online) "Online" else "Offline"}")
            try {
                val colorRes = if (online) R.color.green else R.color.lgrey
                dot?.setBackgroundColor(getColor(colorRes))
                dot?.visibility = View.VISIBLE
            } catch (_: Exception) {}
        }
    }
    
    private fun loadReceiverInfo(receiverId: String) {
        usersRef.child(receiverId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val fullName = snapshot.child("fullName").value?.toString()
                    val username = snapshot.child("username").value?.toString()
                    receiverName = fullName ?: username ?: "User"
                    
                    // Update UI with receiver name
                    findViewById<TextView>(R.id.tvTitle)?.text = receiverName
                } else {
                    findViewById<TextView>(R.id.tvTitle)?.text = receiverName
                }
            }
            .addOnFailureListener {
                // Keep default name if Firebase query fails
                findViewById<TextView>(R.id.tvTitle)?.text = receiverName
            }
    }
    
    private fun initiateCall() {
        callManager.initiateCall(
            receiverId = receiverId,
            receiverName = receiverName,
            onSuccess = { callId ->
                // Call initiated successfully, start the call activity
                val intent = Intent(this, calling::class.java)
                intent.putExtra("callId", callId)
                intent.putExtra("receiverId", receiverId)
                intent.putExtra("receiverName", receiverName)
                intent.putExtra("isIncomingCall", false)
                startActivity(intent)
            },
            onFailure = { error ->
                Toast.makeText(this, "Failed to initiate call: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

class MessageAdapter(
    private val messageList: List<ChatMessage>,
    private val onMessageClick: (ChatMessage) -> Unit
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messageList[position], onMessageClick)
    }
    
    override fun getItemCount(): Int = messageList.size
    
    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageBubble: LinearLayout = itemView.findViewById(R.id.messageBubble)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageTime: TextView = itemView.findViewById(R.id.messageTime)
        private val editedIndicator: TextView = itemView.findViewById(R.id.editedIndicator)
        
        fun bind(message: ChatMessage, onMessageClick: (ChatMessage) -> Unit) {
            messageText.text = when (message.messageType) {
                MessageType.TEXT -> message.messageText
                MessageType.IMAGE -> "📷 Image"
                MessageType.POST -> "📄 Shared a post"
            }
            
            messageTime.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp))
            
            // Show edited indicator
            editedIndicator.visibility = if (message.isEdited) View.VISIBLE else View.GONE
            
            // Set bubble background and layout based on sender
            val isOwnMessage = message.senderId == FirebaseAuth.getInstance().currentUser?.uid
            
            if (isOwnMessage) {
                // Sent message - right side
                messageBubble.setBackgroundResource(R.drawable.message_bubble_sent)
                messageBubble.layoutParams = (messageBubble.layoutParams as LinearLayout.LayoutParams).apply {
                    gravity = android.view.Gravity.END
                    marginStart = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_large)
                    marginEnd = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_small)
                }
                messageText.setTextColor(itemView.context.getColor(R.color.white))
                messageTime.setTextColor(itemView.context.getColor(R.color.white))
                editedIndicator.setTextColor(itemView.context.getColor(R.color.white))
            } else {
                // Received message - left side
                messageBubble.setBackgroundResource(R.drawable.message_bubble_received)
                messageBubble.layoutParams = (messageBubble.layoutParams as LinearLayout.LayoutParams).apply {
                    gravity = android.view.Gravity.START
                    marginStart = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_small)
                    marginEnd = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_large)
                }
                messageText.setTextColor(itemView.context.getColor(R.color.black))
                messageTime.setTextColor(itemView.context.getColor(R.color.grey))
                editedIndicator.setTextColor(itemView.context.getColor(R.color.grey))
            }
            
            // Handle click based on message type
            itemView.setOnClickListener {
                when (message.messageType) {
                    MessageType.POST -> {
                        // If it's a shared post, navigate to view the post
                        val postId = message.postId
                        if (!postId.isNullOrEmpty()) {
                            val intent = Intent(itemView.context, feedpage::class.java)
                            intent.putExtra("postId", postId)
                            itemView.context.startActivity(intent)
                        } else {
                            // Fallback to message options if postId missing
                            onMessageClick(message)
                        }
                    }
                    MessageType.IMAGE -> {
                        // Could open fullscreen image viewer
                        // For now, show message options
                        onMessageClick(message)
                    }
                    else -> {
                        // Text messages - show edit/delete options
                        onMessageClick(message)
                    }
                }
            }
        }
    }
}

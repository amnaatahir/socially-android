package com.example.smd_a1

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class ChatListActivity : AppCompatActivity() {
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var searchEditText: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var chatDatabaseManager: ChatDatabaseManager
    private lateinit var auth: FirebaseAuth
    private val database = Firebase.database
    private val usersRef = database.getReference("users")
    
    private val chatList = mutableListOf<ChatWithUserInfo>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)
        
        auth = FirebaseAuth.getInstance()
        chatDatabaseManager = ChatDatabaseManager(this)
        
        initializeViews()
        setupRecyclerView()
        loadChatList()
        setupSearch()
    }
    
    private fun initializeViews() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        searchEditText = findViewById(R.id.etSearch)
        searchButton = findViewById(R.id.btnSearch)
        val titleUser = findViewById<TextView>(R.id.titleUser)
        
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
        
        findViewById<ImageButton>(R.id.btnNewChat)?.setOnClickListener {
            startActivity(Intent(this, jacob_wSearch::class.java))
        }
        
        // Camera button
        findViewById<ImageButton>(R.id.btnCamera)?.setOnClickListener {
            startActivity(Intent(this, camera::class.java))
        }

        // Set current user's display name dynamically (no hardcoded title)
        val uid = auth.currentUser?.uid
        if (uid != null) {
            FirebaseDatabase.getInstance().getReference("users").child(uid).get()
                .addOnSuccessListener { snap ->
                    val display = snap.child("fullName").value?.toString()
                        ?: snap.child("username").value?.toString()
                        ?: ""
                    titleUser?.text = display
                }
        }
    }
    
    private fun setupRecyclerView() {
        val sharePost = intent.getBooleanExtra("sharePost", false)
        val postId = intent.getStringExtra("postId") ?: ""
        
        chatAdapter = ChatAdapter(emptyList(), chatDatabaseManager) { chat ->
            val intent = Intent(this, chat::class.java)
            intent.putExtra("receiverId", chat.otherUserId)
            intent.putExtra("receiverName", chat.otherUserName)
            
            // If sharing a post, pass post ID so it can be shared automatically
            if (sharePost && postId.isNotEmpty()) {
                intent.putExtra("sharePostId", postId)
            }
            
            startActivity(intent)
            finish()
        }
        
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter
    }
    
    private fun loadChatList() {
        // Clear all existing data immediately
        chatList.clear()
        chatAdapter.updateList(emptyList()) // Clear adapter immediately
        
        chatDatabaseManager.getChatList { chats ->
            // Clear list again to ensure no stale data
            chatList.clear()
            
            if (chats.isEmpty()) {
                chatAdapter.updateList(emptyList())
                return@getChatList
            }
            
            val currentUid = auth.currentUser?.uid ?: return@getChatList
            val loadedChats = mutableListOf<ChatWithUserInfo>()
            var pendingLoads = chats.size
            
            for (chat in chats) {
                val otherUserId = chat.participants.find { it != currentUid }
                
                if (otherUserId == null) {
                    pendingLoads--
                    if (pendingLoads == 0) {
                        updateChatList(loadedChats)
                    }
                } else {
                    // Get user info for the other participant
                    usersRef.child(otherUserId).get().addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            val username = snapshot.child("username").value?.toString() ?: "Unknown"
                            val fullName = snapshot.child("fullName").value?.toString() ?: username
                            
                            val chatWithUserInfo = ChatWithUserInfo(
                                chat = chat,
                                otherUserId = otherUserId,
                                otherUserName = fullName,
                                otherUserUsername = username
                            )
                            
                            loadedChats.add(chatWithUserInfo)
                        }
                        
                        pendingLoads--
                        if (pendingLoads == 0) {
                            updateChatList(loadedChats)
                        }
                    }.addOnFailureListener {
                        pendingLoads--
                        if (pendingLoads == 0) {
                            updateChatList(loadedChats)
                        }
                    }
                }
            }
            
            // If no chats to load, update immediately
            if (pendingLoads == 0 && loadedChats.isEmpty()) {
                updateChatList(loadedChats)
            }
        }
    }
    
    private fun updateChatList(loadedChats: List<ChatWithUserInfo>) {
        // Sort by last message time (newest first)
        val sortedChats = loadedChats.sortedByDescending { it.chat.lastMessageTime }
        chatList.clear()
        chatList.addAll(sortedChats)
        chatAdapter.updateList(sortedChats)
        
        // Show empty state if no chats
        if (sortedChats.isEmpty()) {
            android.util.Log.d("ChatList", "No chats found - showing empty state")
        } else {
            android.util.Log.d("ChatList", "Loaded ${sortedChats.size} real chats from Firebase")
        }
    }
    
    private fun setupSearch() {
        searchButton.setOnClickListener {
            val query = searchEditText.text.toString().trim().lowercase()
            if (query.isEmpty()) {
                loadChatList()
                return@setOnClickListener
            }
            
            val filteredList = chatList.filter { 
                it.otherUserName.lowercase().contains(query) || 
                it.otherUserUsername.lowercase().contains(query) ||
                it.chat.lastMessage.lowercase().contains(query)
            }
            
            chatAdapter.updateList(filteredList)
        }
    }
    
    override fun onResume() {
        super.onResume()
        chatDatabaseManager.updateUserPresence(true)
    }
    
    override fun onPause() {
        super.onPause()
        chatDatabaseManager.updateUserPresence(false)
        chatAdapter.cleanup()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        chatAdapter.cleanup()
    }
}

data class ChatWithUserInfo(
    val chat: ChatRoom,
    val otherUserId: String,
    val otherUserName: String,
    val otherUserUsername: String
)

class ChatAdapter(
    private var chatList: List<ChatWithUserInfo>,
    private val chatDatabaseManager: ChatDatabaseManager,
    private val onChatClick: (ChatWithUserInfo) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    
    private val presenceListeners = mutableMapOf<String, ValueEventListener>()
    
    fun updateList(newList: List<ChatWithUserInfo>) {
        chatList = newList
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_list, parent, false)
        return ChatViewHolder(view, chatDatabaseManager, presenceListeners)
    }
    
    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chatWithUserInfo = chatList[position]
        holder.bind(chatWithUserInfo, onChatClick)
    }
    
    override fun getItemCount(): Int = chatList.size
    
    fun cleanup() {
        // Clean up all presence listeners
        presenceListeners.forEach { (userId, listener) ->
            FirebaseDatabase.getInstance().getReference("presence").child(userId)
                .removeEventListener(listener)
        }
        presenceListeners.clear()
    }
    
    class ChatViewHolder(
        itemView: View,
        private val chatDatabaseManager: ChatDatabaseManager,
        private val presenceListeners: MutableMap<String, ValueEventListener>
    ) : RecyclerView.ViewHolder(itemView) {
        private val profileImage: ImageView = itemView.findViewById(R.id.profileImage)
        private val userName: TextView = itemView.findViewById(R.id.userName)
        private val lastMessage: TextView = itemView.findViewById(R.id.lastMessage)
        private val timestamp: TextView = itemView.findViewById(R.id.timestamp)
        private val presenceBadge: View = itemView.findViewById(R.id.presenceBadge)
        private val cameraIcon: ImageView = itemView.findViewById(R.id.cameraIcon)
        
        fun bind(chatWithUserInfo: ChatWithUserInfo, onChatClick: (ChatWithUserInfo) -> Unit) {
            val chat = chatWithUserInfo.chat
            
            // Set user name
            userName.text = chatWithUserInfo.otherUserName
            
            // Set last message
            lastMessage.text = chat.lastMessage.ifEmpty { "No messages yet" }
            
            // Set timestamp
            timestamp.text = ChatUtils.formatTimestamp(chat.lastMessageTime)
            
            // Load and display real-time presence status
            loadPresenceStatus(chatWithUserInfo.otherUserId)
            
            // Load profile picture from Firebase
            FirebaseDatabase.getInstance().getReference("users").child(chatWithUserInfo.otherUserId)
                .get().addOnSuccessListener { snapshot ->
                    val photoUrl = snapshot.child("photoUrl").getValue(String::class.java)
                    val photoBase64 = snapshot.child("photoBase64").getValue(String::class.java)
                    val updatedAt = snapshot.child("photoUpdatedAt").getValue(Long::class.java)
                    ImageUtils.loadProfilePictureUrlOrBase64(profileImage, photoUrl, photoBase64, R.drawable.circle, updatedAt)
                }.addOnFailureListener {
                    ImageUtils.loadProfilePicture(profileImage, null, R.drawable.circle)
                }
            
            // Show camera icon only if there's a message (optional - can hide if not needed)
            cameraIcon.visibility = if (chat.lastMessage.isNotEmpty()) View.VISIBLE else View.GONE
            
            itemView.setOnClickListener {
                onChatClick(chatWithUserInfo)
            }
        }
        
        private fun loadPresenceStatus(userId: String) {
            // Remove old listener if exists
            presenceListeners[userId]?.let { listener ->
                FirebaseDatabase.getInstance().getReference("presence").child(userId)
                    .removeEventListener(listener)
            }
            
            // Set default to offline (gray) while loading
            presenceBadge.visibility = View.VISIBLE
            presenceBadge.setBackgroundColor(
                itemView.context.getColor(R.color.lgrey)
            )
            
            val presenceRef = FirebaseDatabase.getInstance().getReference("presence").child(userId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false
                    
                    // Update presence badge color: green for online, gray for offline
                    val colorRes = if (isOnline) R.color.green else R.color.lgrey
                    presenceBadge.setBackgroundColor(itemView.context.getColor(colorRes))
                    presenceBadge.visibility = View.VISIBLE
                }
                
                override fun onCancelled(error: DatabaseError) {
                    // On error, show offline status
                    presenceBadge.setBackgroundColor(itemView.context.getColor(R.color.lgrey))
                    presenceBadge.visibility = View.VISIBLE
                }
            }
            
            presenceRef.addValueEventListener(listener)
            presenceListeners[userId] = listener
        }
    }
}

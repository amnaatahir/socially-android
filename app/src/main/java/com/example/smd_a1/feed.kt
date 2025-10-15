package com.example.smd_a1

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream
import android.graphics.Color

class feed : AppCompatActivity() {

    private lateinit var rvStories: RecyclerView
    private lateinit var adapter: StoryStripAdapter
    private val others = mutableListOf<Storysave>() // everyone except me

    // POSTS
    private lateinit var rvPosts: RecyclerView
    private lateinit var postsAdapter: PostsAdapter
    private val postItems = mutableListOf<Post>()

    // header state for the "Your Story" bubble
    private var headerHasStory = false
    private var headerThumbB64: String? = null
    private var headerThumbUrl: String? = null
    
    // Notification manager
    private lateinit var notificationManager: RealNotificationManager
    
    // Firebase listeners for cleanup
    private var followingListener: ValueEventListener? = null
    private var postsListener: ValueEventListener? = null
    private var storiesListener: ValueEventListener? = null
    private var followingForStoriesListener: ValueEventListener? = null
    private val followingSet = mutableSetOf<String>()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_feed)
        
        // Initialize notification manager
        notificationManager = RealNotificationManager(this)
        
        // Initialize notifications and ensure FCM token is registered
        initializeNotifications()

        findViewById<View?>(R.id.main)?.let { root ->
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        // top bar
        findViewById<ImageButton>(R.id.btnfcam)?.setOnClickListener {
            startActivity(Intent(this, addingstory::class.java))
        }

        // bottom nav
        findViewById<ImageButton>(R.id.navHome)?.setOnClickListener {
            // Home - already on feed, do nothing or refresh
            loadPosts()
        }
        findViewById<ImageButton>(R.id.navSearch)?.setOnClickListener {
            startActivity(Intent(this, internshala::class.java))
        }
        findViewById<ImageButton>(R.id.navAdd)?.setOnClickListener {
            startActivity(Intent(this, gallery::class.java))
        }
        findViewById<ImageButton>(R.id.navHeart)?.setOnClickListener {
            startActivity(Intent(this, followingyou::class.java))
        }
        findViewById<ImageButton>(R.id.navProfile)?.setOnClickListener {
            val intent = Intent(this, instaprof::class.java)
            intent.putExtra("uid", FirebaseAuth.getInstance().currentUser?.uid)
            startActivity(intent)
        }

        findViewById<ImageButton>(R.id.btnshare)?.setOnClickListener {
            startActivity(Intent(this, jacob_wSearch::class.java))
        }
        
        // TESTING: Long press on "Socially" title to test notifications
        findViewById<TextView>(R.id.tvSmd)?.setOnLongClickListener {
            showNotificationTestDialog()
            true
        }

        // Check if this is for post sharing
        val selectPostToShare = intent.getBooleanExtra("selectPostToShare", false)
        val shareReceiverId = intent.getStringExtra("receiverId")
        
        // POSTS list (RecyclerView is the scroller; no ScrollView needed)
        rvPosts = findViewById(R.id.rvPosts)
        rvPosts.layoutManager = LinearLayoutManager(this)
        postsAdapter = PostsAdapter(
            items = postItems,
            onLike = { post -> toggleLike(post) },
            onPromptComment = { post -> promptAddComment(post) },   // restored dialog flow
            onCommentInline = { post, text -> addComment(post, text) }, // inline box flow
            onPostClick = if (selectPostToShare && !shareReceiverId.isNullOrEmpty()) {
                { post -> 
                    // Return selected post for sharing
                    val resultIntent = Intent()
                    resultIntent.putExtra("selectedPostId", post.postId)
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            } else null,
            onShare = if (!selectPostToShare) {
                { post -> handleSharePost(post) }
            } else null
        )
        rvPosts.adapter = postsAdapter
        loadPosts()

        // STORIES row
        rvStories = findViewById(R.id.rvStories)
        rvStories.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        adapter = StoryStripAdapter(
            others = others,
            onTapHeader = {
                if (headerHasStory) {
                    startActivity(Intent(this, story::class.java).apply {
                        putExtra("mediaType", "image")
                        putExtra("mediaB64", headerThumbB64)
                        putExtra("mediaUrl", headerThumbUrl)
                        putExtra("ownerName", "You")
                    })
                } else {
                    startActivity(Intent(this, addingstory::class.java))
                }
            },
            onTapOther = { s ->
                startActivity(Intent(this, story::class.java).apply {
                    putExtra("mediaType", s.mediaType ?: "image")
                    putExtra("mediaB64", s.mediaBase64)
                    putExtra("mediaUrl", s.mediaUrl)
                    putExtra("ownerName", s.ownerName ?: "user")
                })
            }
        )
        rvStories.adapter = adapter

        loadStories()
    }

    override fun onResume() {
        super.onResume()
        try { PresenceManager(this).setOnline() } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try { PresenceManager(this).setOffline() } catch (_: Exception) {}
    }

    // -------------------- POSTS --------------------

    private fun loadPosts() {
        val my = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance().reference

        // Remove old listeners if they exist
        followingListener?.let { db.child("following").child(my).removeEventListener(it) }
        postsListener?.let { db.child("posts").removeEventListener(it) }

        followingListener = object : ValueEventListener {
            override fun onDataChange(followSnap: DataSnapshot) {
                val allowed = mutableSetOf<String>()
                allowed += my
                followSnap.children.forEach {
                    if (it.getValue(Boolean::class.java) == true) it.key?.let(allowed::add)
                }

                postsListener = object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        postItems.clear()
                        s.children.forEach { c ->
                            val p = c.getValue(Post::class.java) ?: return@forEach
                            val postId = if (p.postId.isBlank()) c.key ?: "" else p.postId
                            val updatedPost = p.copy(postId = postId)
                            val author = updatedPost.authorId
                            if (!author.isNullOrBlank() && allowed.contains(author)) {
                                postItems.add(updatedPost)
                            }
                        }
                        postItems.sortByDescending { it.createdAt }
                        postsAdapter.notifyDataSetChanged()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e("Feed", "Failed to load posts: ${error.message}")
                        Toast.makeText(this@feed, "Failed to load posts", Toast.LENGTH_SHORT).show()
                    }
                }
                db.child("posts").addValueEventListener(postsListener!!)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Feed", "Failed to load following: ${error.message}")
                Toast.makeText(this@feed, "Failed to load feed", Toast.LENGTH_SHORT).show()
            }
        }
        db.child("following").child(my).addValueEventListener(followingListener!!)
    }




    private fun toggleLike(post: Post) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (post.postId.isBlank()) return
        val ref = FirebaseDatabase.getInstance().reference.child("posts").child(post.postId)
        val likePath = ref.child("likes").child(uid)
        likePath.get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                // Unlike
                likePath.removeValue()
                ref.child("likeCount").setValue((post.likeCount - 1).coerceAtLeast(0))
            } else {
                // Like
                likePath.setValue(true)
                ref.child("likeCount").setValue(post.likeCount + 1)
                
                // Create notification for post author
                if (post.authorId != uid) {
                    notificationManager.handleLikeNotification(post.authorId, post.postId)
                    
                    // Also send push notification - get username from Firebase
                    FirebaseDatabase.getInstance().reference.child("users").child(uid).child("username").get()
                        .addOnSuccessListener { usernameSnap ->
                            val likerName = usernameSnap.getValue(String::class.java) ?: post.authorName
                            try {
                                val fcmNotificationManager = NotificationManager()
                                fcmNotificationManager.sendLikeNotification(
                                    receiverId = post.authorId,
                                    likerName = likerName,
                                    postId = post.postId,
                                    postType = "post"
                                )
                            } catch (e: Exception) {
                                Log.e("Feed", "Failed to send push notification for like: ${e.message}")
                            }
                        }
                        .addOnFailureListener {
                            // Fallback to post authorName
                            try {
                                val fcmNotificationManager = NotificationManager()
                                fcmNotificationManager.sendLikeNotification(
                                    receiverId = post.authorId,
                                    likerName = post.authorName,
                                    postId = post.postId,
                                    postType = "post"
                                )
                            } catch (e: Exception) {
                                Log.e("Feed", "Failed to send push notification for like: ${e.message}")
                            }
                        }
                }
            }
        }
    }

    private fun promptAddComment(post: Post) {
        val input = EditText(this).apply {
            hint = "Write a comment…"
            setPadding(24, 24, 24, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Add comment")
            .setView(input)
            .setPositiveButton("Post") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) addComment(post, text)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addComment(post: Post, text: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (post.postId.isBlank()) return
        val commentId = FirebaseDatabase.getInstance().reference.push().key ?: return
        val cmt = Comment(
            id = commentId,
            postId = post.postId,
            authorId = uid,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        FirebaseDatabase.getInstance().reference
            .child("posts").child(post.postId).child("comments").child(commentId)
            .setValue(cmt)
            .addOnSuccessListener {
                // Create notification for post author
                if (post.authorId != uid) {
                    notificationManager.handleCommentNotification(post.authorId, post.postId, commentId)
                    
                    // Also send push notification - get username from Firebase
                    FirebaseDatabase.getInstance().reference.child("users").child(uid).child("username").get()
                        .addOnSuccessListener { usernameSnap ->
                            val commenterName = usernameSnap.getValue(String::class.java) ?: post.authorName
                            try {
                                val fcmNotificationManager = NotificationManager()
                                fcmNotificationManager.sendCommentNotification(
                                    receiverId = post.authorId,
                                    commenterName = commenterName,
                                    postId = post.postId,
                                    commentText = text
                                )
                            } catch (e: Exception) {
                                Log.e("Feed", "Failed to send push notification for comment: ${e.message}")
                            }
                        }
                        .addOnFailureListener {
                            // Fallback to post authorName
                            try {
                                val fcmNotificationManager = NotificationManager()
                                fcmNotificationManager.sendCommentNotification(
                                    receiverId = post.authorId,
                                    commenterName = post.authorName,
                                    postId = post.postId,
                                    commentText = text
                                )
                            } catch (e: Exception) {
                                Log.e("Feed", "Failed to send push notification for comment: ${e.message}")
                            }
                        }
                }
                
                // Check for mentions in comment
                checkForMentions(text, post.postId)
            }
    }
    
    /**
     * Check for mentions in comment text
     */
    private fun checkForMentions(commentText: String, postId: String) {
        val mentionPattern = "@(\\w+)".toRegex()
        val mentions = mentionPattern.findAll(commentText).map { it.groupValues[1] }.toList()
        
        if (mentions.isNotEmpty()) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            
            // Find mentioned users and create notifications
            FirebaseDatabase.getInstance().reference.child("users")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        snapshot.children.forEach { userSnapshot ->
                            val username = userSnapshot.child("username").value?.toString()
                            if (username != null && mentions.contains(username)) {
                                val mentionedUserId = userSnapshot.key ?: return@forEach
                                if (mentionedUserId != currentUserId) {
                                    notificationManager.handleMentionNotification(
                                        mentionedUserId, 
                                        postId, 
                                        commentText
                                    )
                                }
                            }
                        }
                    }
                    
                    override fun onCancelled(error: DatabaseError) {
                        Log.e("Feed", "Failed to check mentions: ${error.message}")
                    }
                })
        }
    }

    // -------------------- STORIES --------------------

    private fun loadStories() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        val db = FirebaseDatabase.getInstance().reference

        // Remove old listener if it exists
        followingForStoriesListener?.let { 
            db.child("following").child(uid).removeEventListener(it) 
        }
        
        // Load and maintain the list of users the current user is following (update in real-time)
        followingForStoriesListener = object : ValueEventListener {
            override fun onDataChange(followingSnapshot: DataSnapshot) {
                followingSet.clear()
                followingSnapshot.children.forEach { child ->
                    child.key?.let { followingSet.add(it) }
                }
                
                // Reload stories when following list changes
                reloadStoriesWithFilter(uid, now)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("Feed", "Failed to load following list for stories: ${error.message}")
            }
        }
        
        db.child("following").child(uid).addValueEventListener(followingForStoriesListener!!)
        
        // Initial load and setup stories listener
        reloadStoriesWithFilter(uid, now)
    }
    
    private fun reloadStoriesWithFilter(uid: String, now: Long) {
        val db = FirebaseDatabase.getInstance().reference
        
        // Remove old stories listener if it exists
        storiesListener?.let {
            db.child("storiesPublic").removeEventListener(it)
        }
        
        // Now load stories and filter by following list
        storiesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                headerHasStory = false
                headerThumbB64 = null
                headerThumbUrl = null

                val list = mutableListOf<Storysave>()
                for (c in snapshot.children) {
                    val s = c.getValue(Storysave::class.java) ?: continue
                    if (s.expiresAt <= now) continue

                    if (s.ownerId == uid) {
                        // User's own story - show in header
                        headerHasStory = true
                        headerThumbB64 = s.mediaBase64
                        headerThumbUrl = s.mediaUrl
                    } else if (s.ownerId != null && followingSet.contains(s.ownerId)) {
                        // Only add stories from users we're following
                        list += s
                    }
                }

                list.sortByDescending { it.createdAt }
                others.clear()
                others.addAll(list)
                adapter.updateHeader(headerHasStory, headerThumbB64, headerThumbUrl)
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Feed", "Failed to load stories: ${error.message}")
                Toast.makeText(this@feed, "Failed to load stories", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Add listener to storiesPublic
        db.child("storiesPublic")
            .orderByChild("expiresAt").startAt(now.toDouble())
            .addValueEventListener(storiesListener!!)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        val my = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance().reference
        
        // Remove following listener
        followingListener?.let {
            db.child("following").child(my).removeEventListener(it)
            followingListener = null
        }
        
        // Remove posts listener
        postsListener?.let {
            db.child("posts").removeEventListener(it)
            postsListener = null
        }
        
        // Remove stories listener
        storiesListener?.let {
            db.child("storiesPublic").removeEventListener(it)
            storiesListener = null
        }
        
        // Remove following for stories listener
        followingForStoriesListener?.let {
            db.child("following").child(my).removeEventListener(it)
            followingForStoriesListener = null
        }
        followingSet.clear()
    }
    
    /**
     * Initialize notifications and register FCM token
     */
    private fun initializeNotifications() {
        try {
            val notificationInitializer = NotificationInitializer(this)
            notificationInitializer.initialize()
            Log.d("Feed", "Notification initialization started")
            
            // Also update FCM token directly to ensure it's saved
            val notificationManager = NotificationManager()
            notificationManager.updateFCMToken()
        } catch (e: Exception) {
            Log.e("Feed", "Failed to initialize notifications: ${e.message}", e)
        }
    }
    
    /**
     * Show notification test dialog
     */
    private fun showNotificationTestDialog() {
        val options = arrayOf(
            "Check FCM Token",
            "Test Like Notification",
            "Test Follow Request",
            "Test Comment",
            "Check Notification Permission",
            "View Logs"
        )
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🔔 Test Push Notifications")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> NotificationTester.checkFCMToken(this)
                    1 -> NotificationTester.sendTestNotificationToSelf(this)
                    2 -> testFollowRequestNotification()
                    3 -> testCommentNotification()
                    4 -> NotificationTester.checkNotificationPermission(this)
                    5 -> NotificationTester.logNotificationSettings(this)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun testFollowRequestNotification() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val notificationManager = NotificationManager()
        notificationManager.sendFollowRequestNotification(
            receiverId = currentUser.uid,
            requesterName = "Test Requester"
        )
        Toast.makeText(this, "Test follow request notification sent!", Toast.LENGTH_SHORT).show()
        Log.d("Feed", "Test follow request notification sent to ${currentUser.uid}")
    }
    
    private fun testCommentNotification() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val notificationManager = NotificationManager()
        notificationManager.sendCommentNotification(
            receiverId = currentUser.uid,
            commenterName = "Test Commenter",
            postId = "test_post_123",
            commentText = "This is a test comment!"
        )
        Toast.makeText(this, "Test comment notification sent!", Toast.LENGTH_SHORT).show()
        Log.d("Feed", "Test comment notification sent to ${currentUser.uid}")
    }
    
    /**
     * Handle sharing a post - opens chat list to select recipient
     */
    private fun handleSharePost(post: Post) {
        // Open chat list with post sharing mode
        val intent = Intent(this, ChatListActivity::class.java)
        intent.putExtra("sharePost", true)
        intent.putExtra("postId", post.postId)
        startActivity(intent)
    }
}

/** Stories strip with a *single* header bubble ("Your Story"), followed by others. */
private class StoryStripAdapter(
    private val others: List<Storysave>,
    private val onTapHeader: () -> Unit,
    private val onTapOther: (Storysave) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var headerHasStory: Boolean = false
    private var headerThumbB64: String? = null
    private var headerThumbUrl: String? = null

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_STORY = 1
    }

    fun updateHeader(has: Boolean, b64: String?, url: String?) {
        headerHasStory = has
        headerThumbB64 = b64
        headerThumbUrl = url
        if (itemCount > 0) notifyItemChanged(0)
    }

    override fun getItemViewType(position: Int) = if (position == 0) TYPE_HEADER else TYPE_STORY
    override fun getItemCount(): Int = 1 + others.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_story, parent, false)
        return if (viewType == TYPE_HEADER) HeaderVH(v) else StoryVH(v)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_HEADER) {
            (holder as HeaderVH).bind(headerHasStory, onTapHeader)
        } else {
            val item = others[position - 1]
            (holder as StoryVH).bind(item, onTapOther)
        }
    }

    private class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        private val img: ImageView = v.findViewById(R.id.img)
        private val name: TextView = v.findViewById(R.id.name)
        private val plus: ImageView = v.findViewById(R.id.plus)
        fun bind(hasStory: Boolean, onClick: () -> Unit) {
            name.text = "Your Story"
            plus.visibility = if (hasStory) View.GONE else View.VISIBLE
            // Load current user's profile picture for header bubble
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                FirebaseDatabase.getInstance().getReference("users").child(uid)
                    .get().addOnSuccessListener { snap ->
                        val url = snap.child("photoUrl").getValue(String::class.java)
                        val b64 = snap.child("photoBase64").getValue(String::class.java)
                        val updatedAt = snap.child("photoUpdatedAt").getValue(Long::class.java)
                        ImageUtils.loadProfilePictureUrlOrBase64(img, url, b64, R.drawable.oval, updatedAt)
                    }.addOnFailureListener {
                        Glide.with(img).load(R.drawable.oval).into(img)
                    }
            } else {
                Glide.with(img).load(R.drawable.oval).into(img)
            }
            itemView.setOnClickListener { onClick() }
        }
    }

    private class StoryVH(v: View) : RecyclerView.ViewHolder(v) {
        private val img: ImageView = v.findViewById(R.id.img)
        private val name: TextView = v.findViewById(R.id.name)
        private val plus: ImageView = v.findViewById(R.id.plus)
        private val presenceDot: View = v.findViewById(R.id.presenceDot)
        fun bind(s: Storysave, onOpen: (Storysave) -> Unit) {
            plus.visibility = View.GONE
            name.text = s.ownerName?.ifBlank { "user" } ?: "user"
            val ownerId = s.ownerId
            if (!ownerId.isNullOrBlank()) {
                FirebaseDatabase.getInstance().getReference("users").child(ownerId)
                    .get().addOnSuccessListener { snap ->
                        val url = snap.child("photoUrl").getValue(String::class.java)
                        val b64 = snap.child("photoBase64").getValue(String::class.java)
                        val updatedAt = snap.child("photoUpdatedAt").getValue(Long::class.java)
                        ImageUtils.loadProfilePictureUrlOrBase64(img, url, b64, R.drawable.oval, updatedAt)
                    }.addOnFailureListener {
                        Glide.with(img).load(R.drawable.oval).into(img)
                    }
                // presence listener for owner
                val presenceRef = FirebaseDatabase.getInstance().getReference("presence").child(ownerId)
                presenceRef.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val online = snapshot.child("isOnline").getValue(Boolean::class.java) == true
                        val colorRes = if (online) R.color.green else R.color.lgrey
                        presenceDot.setBackgroundColor(itemView.context.getColor(colorRes))
                        presenceDot.visibility = View.VISIBLE
                    }
                    override fun onCancelled(error: DatabaseError) { }
                })
            } else {
                Glide.with(img).load(R.drawable.oval).into(img)
                presenceDot.visibility = View.GONE
            }
            itemView.setOnClickListener { onOpen(s) }
        }
    }
}

private class PostsAdapter(
    private val items: List<Post>,
    private val onLike: (Post) -> Unit,
    private val onPromptComment: (Post) -> Unit,          // 🔙 restored
    private val onCommentInline: (Post, String) -> Unit, // inline box
    private val onPostClick: ((Post) -> Unit)? = null,    // For post sharing
    private val onShare: ((Post) -> Unit)? = null         // For sharing to chat
) : RecyclerView.Adapter<PostsAdapter.VH>() {

    private val uid: String? = FirebaseAuth.getInstance().currentUser?.uid
    private val likeListeners = mutableMapOf<String, ValueEventListener>()
    private val commentListeners = mutableMapOf<String, ValueEventListener>()

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivAvatar: ImageView = v.findViewById(R.id.ivAvatar)
        val tvAuthor: TextView  = v.findViewById(R.id.tvAuthor)
        val ivPhoto: ImageView  = v.findViewById(R.id.ivPhoto)
        val tvLikes: TextView   = v.findViewById(R.id.tvLikes)
        val tvCaption: TextView = v.findViewById(R.id.tvCaption)
        val btnLike: ImageView  = v.findViewById(R.id.btnLike)

        // comment icon (dialog)
        val btnComment: ImageView = v.findViewById(R.id.btnComment)   // 🔙 restored
        
        // share and bookmark buttons
        val btnShare: ImageView = v.findViewById(R.id.btnShare)
        val btnBookmark: ImageView = v.findViewById(R.id.btnBookmark)

        // inline input under caption
        val etComment: EditText = v.findViewById(R.id.etComment)
        val btnSend: ImageButton = v.findViewById(R.id.btnSend)

        // where comments render
        val commentsBox: LinearLayout = v.findViewById(R.id.commentsContainer)
    }

    override fun onCreateViewHolder(p: ViewGroup, vtype: Int): VH {
        val v = LayoutInflater.from(p.context).inflate(R.layout.item_post, p, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = items[pos]

        // avatar + author
        h.tvAuthor.text = p.authorName ?: "user"
        
        // Load author profile picture (URL or Base64, with cache-busting)
        val authorId = p.authorId
        if (!authorId.isNullOrBlank()) {
            FirebaseDatabase.getInstance().getReference("users").child(authorId)
                .get().addOnSuccessListener { snapshot ->
                    val photoUrl = snapshot.child("photoUrl").getValue(String::class.java)
                    val photoBase64 = snapshot.child("photoBase64").getValue(String::class.java)
                    val updatedAt = snapshot.child("photoUpdatedAt").getValue(Long::class.java)
                    ImageUtils.loadProfilePictureUrlOrBase64(h.ivAvatar, photoUrl, photoBase64, R.drawable.oval, updatedAt)
                }.addOnFailureListener {
                    ImageUtils.loadProfilePicture(h.ivAvatar, null, R.drawable.oval)
                }
        } else {
            ImageUtils.loadProfilePicture(h.ivAvatar, null, R.drawable.oval)
        }
        
        // Make avatar and author name clickable to open profile
        if (!authorId.isNullOrBlank()) {
            val profileClick = View.OnClickListener {
                val context = it.context
                val intent = Intent(context, instaprof::class.java)
                intent.putExtra("uid", authorId)
                context.startActivity(intent)
            }
            h.ivAvatar.setOnClickListener(profileClick)
            h.tvAuthor.setOnClickListener(profileClick)
        } else {
            // Disable clicks if no author ID
            h.ivAvatar.setOnClickListener(null)
            h.tvAuthor.setOnClickListener(null)
        }

        // photo
        val b64 = p.mediaBase64
        if (!b64.isNullOrBlank()) {
            try {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                h.ivPhoto.setImageBitmap(bmp)
            } catch (_: Throwable) {
                h.ivPhoto.setImageResource(R.drawable.d1)
            }
        } else {
            h.ivPhoto.setImageResource(R.drawable.d1)
        }

        // likes + caption
        h.tvLikes.text = "${p.likeCount} likes"
        h.tvCaption.text = p.caption ?: ""

        // like state (persisted)
        val postId = p.postId
        setHeartIcon(h.btnLike, false)
        if (uid != null && postId.isNotBlank()) {
            val likeRef = FirebaseDatabase.getInstance().reference
                .child("posts").child(postId).child("likes").child(uid)
            val likeListener = object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    setHeartIcon(h.btnLike, snap.exists())
                }
                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("PostsAdapter", "Failed to load like status: ${error.message}")
                }
            }
            likeRef.addValueEventListener(likeListener)
            likeListeners[postId] = likeListener
        }
        h.btnLike.setOnClickListener { onLike(p) }

        // open dialog on comment icon (restored flow)
        h.btnComment.setOnClickListener { onPromptComment(p) }  // 🔙 restored
        
        // share button - open chat list to select recipient
        h.btnShare.setOnClickListener { 
            onShare?.invoke(p)
        }
        
        // bookmark button (placeholder)
        h.btnBookmark.setOnClickListener {
            Toast.makeText(h.itemView.context, "Bookmark feature coming soon", Toast.LENGTH_SHORT).show()
        }

        // send comment inline
        h.btnSend.setOnClickListener {
            val text = h.etComment.text.toString().trim()
            if (text.isNotEmpty()) {
                onCommentInline(p, text)
                h.etComment.text.clear()
            }
        }
        
        // Post click handler (for sharing posts)
        if (onPostClick != null) {
            h.ivPhoto.setOnClickListener { onPostClick(p) }
            h.itemView.setOnClickListener { onPostClick(p) }
        }

        // live comments render
        val commentsRef = FirebaseDatabase.getInstance().reference
            .child("posts").child(postId).child("comments")
        val commentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                h.commentsBox.removeAllViews()
                val comments = snapshot.children.mapNotNull { it.getValue(Comment::class.java) }
                    .sortedBy { it.timestamp }
                for (c in comments) {
                    val tv = TextView(h.itemView.context).apply {
                        text = c.text
                        setTextColor(Color.BLACK)
                        textSize = 13f
                    }
                    h.commentsBox.addView(tv)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("PostsAdapter", "Failed to load comments: ${error.message}")
            }
        }
        commentsRef.addValueEventListener(commentListener)
        commentListeners[postId] = commentListener
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        val pos = holder.adapterPosition
        if (pos in items.indices) {
            val postId = items[pos].postId
            likeListeners.remove(postId)?.let {
                if (uid != null) {
                    FirebaseDatabase.getInstance().reference
                        .child("posts").child(postId).child("likes").child(uid)
                        .removeEventListener(it)
                }
            }
            commentListeners.remove(postId)?.let {
                FirebaseDatabase.getInstance().reference
                    .child("posts").child(postId).child("comments")
                    .removeEventListener(it)
            }
        }
    }

    // Heart icon swap (outline <-> filled)
    private fun setHeartIcon(view: ImageView, liked: Boolean) {
        view.setImageResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
    }
}

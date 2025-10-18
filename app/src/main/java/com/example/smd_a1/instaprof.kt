package com.example.smd_a1

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class instaprof : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instaprof)

        val root = findViewById<View>(R.id.main) ?: window.decorView
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Edit Profile button - only for own profile
        findViewById<android.widget.Button>(R.id.btnEditProfile)?.setOnClickListener {
            val intent = Intent(this, editprofile::class.java)
            intent.putExtra("uid", FirebaseAuth.getInstance().currentUser?.uid)
            startActivity(intent)
        }
        
        // Follow button toggle
        findViewById<android.widget.Button>(R.id.btnFollow)?.setOnClickListener {
            handleFollowButtonClick()
        }
        
        // Message button
        findViewById<android.widget.Button>(R.id.btnMessage)?.setOnClickListener {
            val viewingUid = getIntent().getStringExtra("uid")
            if (viewingUid != null) {
                FirebaseDatabase.getInstance().getReference("users").child(viewingUid)
                    .child("fullName").get().addOnSuccessListener { snap ->
                        val receiverName = snap.getValue(String::class.java) ?: "User"
                        val intent = Intent(this, chat::class.java)
                        intent.putExtra("receiverId", viewingUid)
                        intent.putExtra("receiverName", receiverName)
                        startActivity(intent)
                    }
            }
        }
        
        // Options button - show logout menu (only if viewing own profile)
        findViewById<ImageView>(R.id.btnOptions)?.setOnClickListener {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            val viewingUid = getIntent().getStringExtra("uid") ?: currentUid
            
            // Only show logout if viewing own profile
            if (currentUid == viewingUid) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Options")
                    .setItems(arrayOf("Logout")) { _, which ->
                        if (which == 0) {
                            // Logout
                            android.app.AlertDialog.Builder(this)
                                .setTitle("Logout")
                                .setMessage("Are you sure you want to logout?")
                                .setPositiveButton("Yes") { _, _ ->
                                    FirebaseAuth.getInstance().signOut()
                                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, login::class.java))
                                    finishAffinity() // Clear all activities
                                }
                                .setNegativeButton("No", null)
                                .show()
                        }
                    }
                    .show()
            } else {
                // If viewing someone else's profile, show other options
                android.app.AlertDialog.Builder(this)
                    .setTitle("Options")
                    .setItems(arrayOf("Report")) { _, _ ->
                        Toast.makeText(this, "Report feature coming soon", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }

        findViewById<ImageButton>(R.id.navHome)?.setOnClickListener {
            startActivity(Intent(this, feed::class.java))
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
            // Already on profile, refresh data
            loadUserData()
        }




        findViewById<ImageView>(R.id.picture31)?.setOnClickListener {
            startActivity(Intent(this, highlight::class.java))
        }

        // Setup back button
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
        
        // Make Followers/Following counts tappable to open lists
        val viewingUidForLists = intent.getStringExtra("uid") ?: FirebaseAuth.getInstance().currentUser?.uid
        findViewById<TextView>(R.id.followersCount)?.setOnClickListener {
            val target = viewingUidForLists ?: return@setOnClickListener
            val i = Intent(this, followingyou::class.java)
            i.putExtra("mode", "followers")
            i.putExtra("uid", target)
            startActivity(i)
        }
        findViewById<TextView>(R.id.followingCount)?.setOnClickListener {
            val target = viewingUidForLists ?: return@setOnClickListener
            val i = Intent(this, followingyou::class.java)
            i.putExtra("mode", "following")
            i.putExtra("uid", target)
            startActivity(i)
        }

        // Setup dropdown arrow click (show account options for own profile)
        findViewById<ImageView>(R.id.dropdownArrow)?.setOnClickListener {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            val viewingUid = getIntent().getStringExtra("uid") ?: currentUid
            
            if (currentUid != null && currentUid == viewingUid) {
                // Own profile - show account menu
                android.app.AlertDialog.Builder(this)
                    .setTitle("Account Options")
                    .setItems(arrayOf("Edit Profile", "Settings", "Logout")) { _, which ->
                        when (which) {
                            0 -> {
                                val intent = Intent(this, editprofile::class.java)
                                intent.putExtra("uid", currentUid)
                                startActivity(intent)
                            }
                            1 -> {
                                Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
                            }
                            2 -> {
                                FirebaseAuth.getInstance().signOut()
                                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, login::class.java))
                                finishAffinity()
                            }
                        }
                    }
                    .show()
            }
        }

        // Load user data from Firebase
        loadUserData()
    }
    
    private fun loadUserData() {
        val uid = getIntent().getStringExtra("uid") ?: FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.e("InstaProf", "No user ID available")
            Toast.makeText(this, "No user ID available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        Log.d("InstaProf", "Loading profile for user: $uid")
        
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        val isOwnProfile = currentUid == uid
        
        // Show/hide back button - hide for own profile, show for others
        findViewById<ImageButton>(R.id.btnBack)?.visibility = if (isOwnProfile) View.GONE else View.VISIBLE
        
        // Show lock icon for all profiles (all accounts are private)
        findViewById<ImageView>(R.id.lockIcon)?.visibility = View.VISIBLE
        
        val usersRef = FirebaseDatabase.getInstance().getReference("users").child(uid)
        
        // Load counts and follow status in parallel with user data (don't wait)
        loadUserCounts(uid)
        
        if (!isOwnProfile && currentUid != null) {
            setupFollowButton(currentUid, uid)
        }
        
        usersRef.get().addOnSuccessListener { snapshot ->
            Log.d("InstaProf", "Firebase query successful")
            Log.d("InstaProf", "Snapshot exists: ${snapshot.exists()}")
            Log.d("InstaProf", "Snapshot value: ${snapshot.value}")
            
            if (snapshot.exists()) {
                // Load user data from Firebase
                val username = snapshot.child("username").value?.toString() ?: "Unknown"
                val fullName = snapshot.child("fullName").value?.toString() ?: ""
                val bio = snapshot.child("bio").value?.toString() ?: ""
                val photoUrl = snapshot.child("photoUrl").value?.toString() ?: ""
                val photoBase64 = snapshot.child("photoBase64").value?.toString()
                val updatedAt = snapshot.child("photoUpdatedAt").getValue(Long::class.java)
                
                Log.d("InstaProf", "Loaded data - Username: $username, FullName: $fullName, Bio: $bio")
                
                // Update UI with real Firebase data
                findViewById<TextView>(R.id.username)?.text = username
                findViewById<TextView>(R.id.fullname)?.text = fullName
                findViewById<TextView>(R.id.bio)?.text = bio
                
                // Load profile picture in the main profile pic (not highlight)
                val imgProfile = findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.profilePic)
                imgProfile?.let {
                    ImageUtils.loadProfilePictureUrlOrBase64(it, photoUrl, photoBase64, R.drawable.profile_pic, updatedAt)
                }
                
                // Show/hide appropriate buttons
                setupActionButtons(isOwnProfile, uid)
                
                // Load posts from Firebase (counts already loading in parallel)
                loadUserPosts(uid)
                
                Log.d("InstaProf", "User data loaded successfully: $username")
                
            } else {
                // Fallback: maybe caller passed a username instead of UID
                FirebaseDatabase.getInstance().getReference("usernames").child(uid).get()
                    .addOnSuccessListener { mapSnap ->
                        val mappedUid = mapSnap.getValue(String::class.java)
                        if (!mappedUid.isNullOrBlank()) {
                            FirebaseDatabase.getInstance().getReference("users").child(mappedUid).get()
                                .addOnSuccessListener { userSnap ->
                                    if (userSnap.exists()) {
                                        val username = userSnap.child("username").value?.toString() ?: "Unknown"
                                        val fullName = userSnap.child("fullName").value?.toString() ?: ""
                                        val bio = userSnap.child("bio").value?.toString() ?: ""
                                        val photoUrl = userSnap.child("photoUrl").value?.toString() ?: ""
                                        val photoBase64 = userSnap.child("photoBase64").value?.toString()
                                        val updatedAt = userSnap.child("photoUpdatedAt").getValue(Long::class.java)
                                        findViewById<TextView>(R.id.username)?.text = username
                                        findViewById<TextView>(R.id.fullname)?.text = fullName
                                        findViewById<TextView>(R.id.bio)?.text = bio
                                        val imgProfile = findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.profilePic)
                                        imgProfile?.let { ImageUtils.loadProfilePictureUrlOrBase64(it, photoUrl, photoBase64, R.drawable.profile_pic, updatedAt) }
                                        // Reload counts/buttons/posts for the mapped UID
                                        loadUserCounts(mappedUid)
                                        if (!isOwnProfile && currentUid != null) { setupFollowButton(currentUid, mappedUid) }
                                        loadUserPosts(mappedUid)
                                    } else {
                                        showUserNotFound()
                                    }
                                }
                                .addOnFailureListener { showUserNotFound() }
                        } else {
                            showUserNotFound()
                        }
                    }
                    .addOnFailureListener { showUserNotFound() }
            }
        }.addOnFailureListener { error ->
            Log.e("InstaProf", "Failed to load user data: ${error.message}", error)
            Toast.makeText(this, "Failed to load profile: ${error.message}", Toast.LENGTH_SHORT).show()
            
            // Show error state
            findViewById<TextView>(R.id.username)?.text = "Error"
            findViewById<TextView>(R.id.fullname)?.text = ""
            findViewById<TextView>(R.id.bio)?.text = ""
        }
    }

    private fun showUserNotFound() {
        Log.e("InstaProf", "User data not found (after fallback)")
        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
        findViewById<TextView>(R.id.username)?.text = "Unknown"
        findViewById<TextView>(R.id.fullname)?.text = ""
        findViewById<TextView>(R.id.bio)?.text = ""
    }
    
    private fun loadUserCounts(uid: String) {
        val db = FirebaseDatabase.getInstance()
        
        // Load posts count
        db.getReference("posts").orderByChild("authorId").equalTo(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val postsCount = snapshot.childrenCount.toInt()
                    findViewById<TextView>(R.id.postsCount)?.text = postsCount.toString()
                    Log.d("InstaProf", "Posts count: $postsCount")
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("InstaProf", "Failed to load posts count: ${error.message}")
                    findViewById<TextView>(R.id.postsCount)?.text = "0"
                }
            })
        
        // Load followers count (normal path)
        db.getReference("followers").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val followersCount = snapshot.childrenCount.toInt()
                    findViewById<TextView>(R.id.followersCount)?.text = followersCount.toString()
                    Log.d("InstaProf", "Followers count: $followersCount")
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("InstaProf", "Failed to load followers count: ${error.message}")
                    findViewById<TextView>(R.id.followersCount)?.text = "0"
                }
            })
        
        // Load following count (normal path)
        db.getReference("following").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val followingCount = snapshot.childrenCount.toInt()
                    findViewById<TextView>(R.id.followingCount)?.text = followingCount.toString()
                    Log.d("InstaProf", "Following count: $followingCount")
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("InstaProf", "Failed to load following count: ${error.message}")
                    findViewById<TextView>(R.id.followingCount)?.text = "0"
                }
            })

        // Fallback for historical incorrect writes: compute followers by scanning following/*/{uid}
        db.getReference("following").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(root: DataSnapshot) {
                var extraFollowers = 0
                root.children.forEach { userNode ->
                    if (userNode.child(uid).exists()) extraFollowers++
                }
                // If current count shows 0 but fallback finds some, display the fallback
                val tv = findViewById<TextView>(R.id.followersCount)
                val shown = tv?.text?.toString()?.toIntOrNull() ?: 0
                if (extraFollowers > shown) tv?.text = extraFollowers.toString()
            }
            override fun onCancelled(error: DatabaseError) { }
        })
    }
    
    private fun loadUserPosts(uid: String) {
        val db = FirebaseDatabase.getInstance()
        Log.d("InstaProf", "Loading posts for user: $uid")
        
        // Optimize: Load posts efficiently - only get what we need for display
        db.getReference("posts").orderByChild("authorId").equalTo(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val posts = mutableListOf<Post>()
                    Log.d("InstaProf", "Query by authorId - Snapshot children count: ${snapshot.childrenCount}")
                    
                    snapshot.children.forEach { child ->
                        try {
                            val postId = child.key ?: ""
                            val authorId = child.child("authorId").getValue(String::class.java) ?: ""
                            // Only load Base64 if mediaUrl is not available (optimization: prefer URL)
                            val mediaUrl = child.child("mediaUrl").getValue(String::class.java)
                            val mediaBase64 = if (mediaUrl.isNullOrEmpty()) {
                                child.child("mediaBase64").getValue(String::class.java) ?: ""
                            } else {
                                "" // Skip Base64 if URL exists - much faster
                            }
                            val caption = child.child("caption").getValue(String::class.java)
                            val createdAt = child.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                            val authorName = child.child("authorName").getValue(String::class.java) ?: "User"
                            
                            if (postId.isNotEmpty()) {
                                val post = Post(
                                    postId = postId,
                                    authorId = authorId.ifEmpty { uid },
                                    authorName = authorName,
                                    mediaBase64 = mediaBase64,
                                    mediaUrl = mediaUrl ?: "",
                                    caption = caption,
                                    createdAt = createdAt,
                                    likeCount = child.child("likeCount").getValue(Int::class.java) ?: 0,
                                    commentCount = child.child("commentCount").getValue(Int::class.java) ?: 0
                                )
                                posts.add(post)
                            }
                        } catch (e: Exception) {
                            Log.e("InstaProf", "Error parsing post: ${e.message}", e)
                            // Try to get minimal data if full parsing fails
                            try {
                                val postId = child.key ?: ""
                                val createdAt = child.child("createdAt").getValue(Long::class.java) ?: 0L
                                val mediaUrl = child.child("mediaUrl").getValue(String::class.java) ?: ""
                                if (postId.isNotEmpty() && createdAt > 0) {
                                    // Create minimal post for display
                                    val post = Post(
                                        postId = postId,
                                        authorId = uid,
                                        authorName = "User",
                                        mediaBase64 = "",
                                        mediaUrl = mediaUrl,
                                        createdAt = createdAt
                                    )
                                    posts.add(post)
                                }
                            } catch (e2: Exception) {
                                Log.e("InstaProf", "Failed to parse post: ${e2.message}")
                            }
                        }
                    }
                    
                    // If no posts found via authorId, try userPosts index
                    if (posts.isEmpty()) {
                        Log.d("InstaProf", "No posts found via authorId, trying userPosts index")
                        db.getReference("userPosts").child(uid)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(userPostsSnapshot: DataSnapshot) {
                                    val postIds = mutableListOf<String>()
                                    userPostsSnapshot.children.forEach { postIdSnapshot ->
                                        postIdSnapshot.key?.let { postIds.add(it) }
                                    }
                                    Log.d("InstaProf", "Found ${postIds.size} post IDs in userPosts")
                                    
                                    if (postIds.isNotEmpty()) {
                                        loadPostsByIds(postIds) { loadedPosts ->
                                            updatePostsGrid(loadedPosts)
                                        }
                                    } else {
                                        Log.d("InstaProf", "No posts found for user")
                                        updatePostsGrid(emptyList())
                                    }
                                }
                                override fun onCancelled(error: DatabaseError) {
                                    Log.e("InstaProf", "Failed to load userPosts: ${error.message}")
                                    updatePostsGrid(emptyList())
                                }
                            })
                    } else {
                        posts.sortByDescending { it.createdAt }
                        Log.d("InstaProf", "Total posts loaded: ${posts.size}")
                        updatePostsGrid(posts)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("InstaProf", "Failed to load posts by authorId: ${error.message}")
                    // Try userPosts as fallback
                    db.getReference("userPosts").child(uid)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(userPostsSnapshot: DataSnapshot) {
                                val postIds = mutableListOf<String>()
                                userPostsSnapshot.children.forEach { postIdSnapshot ->
                                    postIdSnapshot.key?.let { postIds.add(it) }
                                }
                                if (postIds.isNotEmpty()) {
                                    loadPostsByIds(postIds) { loadedPosts ->
                                        updatePostsGrid(loadedPosts)
                                    }
                                } else {
                                    updatePostsGrid(emptyList())
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {
                                updatePostsGrid(emptyList())
                            }
                        })
                }
            })
    }
    
    private fun loadPostsByIds(postIds: List<String>, callback: (List<Post>) -> Unit) {
        val db = FirebaseDatabase.getInstance()
        val postsRef = db.getReference("posts")
        val posts = mutableListOf<Post>()
        var completed = 0
        
        if (postIds.isEmpty()) {
            callback(emptyList())
            return
        }
        
        postIds.forEach { postId ->
            postsRef.child(postId).get().addOnSuccessListener { snapshot ->
                try {
                    val post = snapshot.getValue(Post::class.java)
                    if (post != null && post.postId.isNotEmpty()) {
                        posts.add(post)
                    }
                } catch (e: Exception) {
                    Log.e("InstaProf", "Error loading post $postId: ${e.message}")
                }
                completed++
                if (completed == postIds.size) {
                    posts.sortByDescending { it.createdAt }
                    callback(posts)
                }
            }.addOnFailureListener {
                completed++
                if (completed == postIds.size) {
                    posts.sortByDescending { it.createdAt }
                    callback(posts)
                }
            }
        }
    }
    
    private fun updatePostsGrid(posts: List<Post>) {
        // Find the GridLayout in the layout
        val gridLayout = findViewById<android.widget.GridLayout>(R.id.postsGrid)
        gridLayout?.removeAllViews()
        
        if (posts.isEmpty()) {
            Log.d("InstaProf", "No posts to display")
            return
        }
        
        // Take only the first 9 posts for the 3x3 grid
        val displayPosts = posts.take(9)
        
        Log.d("InstaProf", "Displaying ${displayPosts.size} posts in grid")
        
        // Calculate cell size based on screen width (3 columns with margins)
        val screenWidth = resources.displayMetrics.widthPixels
        val cellSize = (screenWidth / 3.0f).toInt() - 8 // Account for margins
        
        displayPosts.forEachIndexed { index, post ->
            val imageView = ImageView(this)
            
            // Calculate row and column positions (3 columns)
            val row = index / 3
            val col = index % 3
            
            val layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = cellSize
                height = cellSize
                rowSpec = android.widget.GridLayout.spec(row)
                columnSpec = android.widget.GridLayout.spec(col)
                setMargins(2, 2, 2, 2)
            }
            
            imageView.layoutParams = layoutParams
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.adjustViewBounds = true
            imageView.setBackgroundColor(0xFFE8E8E8.toInt()) // Light gray background while loading
            
            // Load image - prioritize mediaUrl (faster), fallback to Base64
            if (!post.mediaUrl.isNullOrEmpty()) {
                // Use Glide for fast URL-based image loading (async and cached)
                Glide.with(this@instaprof)
                    .load(post.mediaUrl)
                    .placeholder(R.drawable.p1)
                    .error(R.drawable.p1)
                    .centerCrop()
                    .into(imageView)
            } else if (post.mediaBase64.isNotEmpty()) {
                // Fallback to Base64 (slower, but handle in background thread)
                loadBase64ImageAsync(imageView, post.mediaBase64)
            } else {
                imageView.setImageResource(R.drawable.p1)
            }
            
            // Add click listener to open post detail
            imageView.setOnClickListener {
                // Could navigate to post detail view if needed
                Log.d("InstaProf", "Clicked on post ${post.postId}")
            }
            
            gridLayout?.addView(imageView)
        }
        
        Log.d("InstaProf", "Grid updated with ${gridLayout?.childCount} views")
    }
    
    private fun loadBase64ImageAsync(imageView: ImageView, base64String: String) {
        // Load Base64 image in background to avoid blocking UI
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val imageBytes = try {
                    android.util.Base64.decode(base64String, android.util.Base64.NO_WRAP)
                } catch (e: Exception) {
                    android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
                }
                
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                
                // Switch back to main thread to update UI
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    } else {
                        imageView.setImageResource(R.drawable.p1)
                    }
                }
            } catch (e: Exception) {
                Log.e("InstaProf", "Failed to decode Base64 image: ${e.message}")
                withContext(Dispatchers.Main) {
                    imageView.setImageResource(R.drawable.p1)
                }
            }
        }
    }
    
    private fun formatCount(count: Int): String {
        return when {
            count >= 1000000 -> "${count / 1000000}M"
            count >= 1000 -> "${count / 1000}K"
            else -> count.toString()
        }
    }
    
    private fun setupActionButtons(isOwnProfile: Boolean, uid: String) {
        val btnFollow = findViewById<android.widget.Button>(R.id.btnFollow)
        val btnMessage = findViewById<android.widget.Button>(R.id.btnMessage)
        val btnMoreActions = findViewById<ImageView>(R.id.btnMoreActions)
        val btnEditProfile = findViewById<android.widget.Button>(R.id.btnEditProfile)
        
        if (isOwnProfile) {
            // Show Edit Profile, hide Follow/Message
            btnFollow?.visibility = View.GONE
            btnMessage?.visibility = View.GONE
            btnMoreActions?.visibility = View.GONE
            btnEditProfile?.visibility = View.VISIBLE
        } else {
            // Show Follow/Message, hide Edit Profile
            btnFollow?.visibility = View.VISIBLE
            btnMessage?.visibility = View.VISIBLE
            btnMoreActions?.visibility = View.VISIBLE
            btnEditProfile?.visibility = View.GONE
        }
    }
    
    private fun setupFollowButton(currentUid: String, targetUid: String) {
        val btnFollow = findViewById<android.widget.Button>(R.id.btnFollow) ?: return
        val db = FirebaseDatabase.getInstance()
        
        // Check if already following
        db.getReference("following").child(currentUid).child(targetUid).get()
            .addOnSuccessListener { followingSnap ->
                if (followingSnap.exists()) {
                    // Already following - show "Following" with toggle to unfollow
                    updateFollowButton(true)
                } else {
                    // Check if request is pending
                    db.getReference("followRequests").child(targetUid).child(currentUid).get()
                        .addOnSuccessListener { requestSnap ->
                            if (requestSnap.exists()) {
                                updateFollowButton(false, "Requested")
                            } else {
                                updateFollowButton(false)
                            }
                        }
                }
            }
    }
    
    private fun handleFollowButtonClick() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val viewingUid = getIntent().getStringExtra("uid") ?: return
        
        if (currentUid == viewingUid) {
            Toast.makeText(this, "You can't follow yourself", Toast.LENGTH_SHORT).show()
            return
        }
        
        val btnFollow = findViewById<android.widget.Button>(R.id.btnFollow) ?: return
        val db = FirebaseDatabase.getInstance()
        
        // Check current state
        db.getReference("following").child(currentUid).child(viewingUid).get()
            .addOnSuccessListener { followingSnap ->
                if (followingSnap.exists()) {
                    // Currently following - unfollow
                    unfollowUser(currentUid, viewingUid)
                } else {
                    // Not following - send follow request (all accounts are private)
                    sendFollowRequest(currentUid, viewingUid)
                }
            }
    }
    
    private fun unfollowUser(currentUid: String, targetUid: String) {
        val db = FirebaseDatabase.getInstance()
        
        db.getReference("following").child(currentUid).child(targetUid).removeValue()
            .addOnSuccessListener {
                db.getReference("followers").child(targetUid).child(currentUid).removeValue()
                    .addOnSuccessListener {
                        updateFollowButton(false)
                        Toast.makeText(this, "Unfollowed", Toast.LENGTH_SHORT).show()
                        // Refresh followers count
                        loadUserCounts(targetUid)
                    }
            }
    }
    
    private fun sendFollowRequest(currentUid: String, targetUid: String) {
        val db = FirebaseDatabase.getInstance()
        
        // Check if request already exists
        db.getReference("followRequests").child(targetUid).child(currentUid).get()
            .addOnSuccessListener { requestSnap ->
                if (requestSnap.exists()) {
                    Toast.makeText(this, "Follow request already sent", Toast.LENGTH_SHORT).show()
                    updateFollowButton(false, "Requested")
                } else {
                    // Create follow request
                    val followRequest = mapOf(
                        "fromUid" to currentUid,
                        "toUid" to targetUid,
                        "timestamp" to System.currentTimeMillis(),
                        "status" to "pending"
                    )
                    
                    db.getReference("followRequests").child(targetUid).child(currentUid)
                        .setValue(followRequest)
                        .addOnSuccessListener {
                            updateFollowButton(false, "Requested")
                            Toast.makeText(this, "Follow request sent!", Toast.LENGTH_SHORT).show()
                            
                            // Create notification
                            db.getReference("users").child(currentUid).get()
                                .addOnSuccessListener { userSnap ->
                                    val username = userSnap.child("username").value?.toString() ?: "Unknown"
                                    val fullName = userSnap.child("fullName").value?.toString() ?: username
                                    
                                    val notificationId = db.reference.child("notifications").push().key
                                    if (notificationId != null) {
                                        // Save notification to Firebase - use map with string type for proper display in YOU tab
                                        val notificationData = hashMapOf<String, Any>(
                                            "notificationId" to notificationId,
                                            "fromUserId" to currentUid,
                                            "fromUsername" to username,
                                            "fromFullName" to fullName,
                                            "targetUserId" to targetUid,
                                            "action" to "wants to follow you",
                                            "type" to "follow_request",  // Use string type for proper categorization
                                            "timestamp" to System.currentTimeMillis(),
                                            "read" to false
                                        )
                                        
                                        db.getReference("notifications").child(targetUid)
                                            .child(notificationId).setValue(notificationData)
                                    }
                                    
                                    // Send push notification
                                    try {
                                        val notificationManager = NotificationManager()
                                        notificationManager.sendFollowRequestNotification(
                                            receiverId = targetUid,
                                            requesterName = fullName.ifEmpty { username }
                                        )
                                    } catch (e: Exception) {
                                        Log.e("InstaProf", "Failed to send push notification: ${e.message}")
                                    }
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to send follow request: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
    }
    
    private fun updateFollowButton(isFollowing: Boolean, text: String? = null) {
        val button = findViewById<android.widget.Button>(R.id.btnFollow) ?: return
        
        when {
            isFollowing -> {
                button.text = "Following"
                button.setBackgroundResource(R.drawable.btn_following_border)
                button.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.brick))
            }
            text == "Requested" -> {
                button.text = "Requested"
                button.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.grey))
                button.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white))
                button.isEnabled = false
            }
            else -> {
                button.text = "Follow"
                button.setBackgroundResource(R.drawable.btn_primary)
                button.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white))
                button.isEnabled = true
            }
        }
    }
}

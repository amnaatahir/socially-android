package com.example.smd_a1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class profile : AppCompatActivity() {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        try {
            FirebaseApp.initializeApp(this)
            auth = FirebaseAuth.getInstance()
            db = FirebaseDatabase.getInstance()
        } catch (e: Exception) {
            Log.e("ProfileCrash", "❌ Firebase init failed: ${e.message}")
            Toast.makeText(this, "Firebase init error: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        
        // Get user ID from intent or current user
        val uid = intent.getStringExtra("uid") ?: auth.currentUser?.uid
        if (uid == null) {
            Log.e("Profile", "No user ID available")
            Toast.makeText(this, "No user ID available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        Log.d("Profile", "Loading profile for user: $uid")
        
        // Load user data from Firebase
        loadUserData(uid)
        
        // Set up click listeners
        setupClickListeners(uid)
    }
    
    // Removed any test-data or auto-create code. Everything is read-only from Firebase.
    
    private fun loadUserData(uid: String) {
        Log.d("Profile", "Starting to load user data for UID: $uid")
        
        val usersRef = db.getReference("users").child(uid)
        
        usersRef.get().addOnSuccessListener { snapshot ->
            Log.d("Profile", "Firebase query successful")
            
            if (snapshot.exists()) {
                // Load user data from Firebase
                val username = snapshot.child("username").value?.toString() ?: "Unknown"
                val fullName = snapshot.child("fullName").value?.toString() ?: "Unknown"
                val email = snapshot.child("email").value?.toString() ?: "Unknown"
                val bio = snapshot.child("bio").value?.toString() ?: "No bio available"
                val photoUrl = snapshot.child("photoUrl").value?.toString() ?: ""
                val photoBase64 = snapshot.child("photoBase64").value?.toString()
                val updatedAt = snapshot.child("photoUpdatedAt").getValue(Long::class.java)
                
                Log.d("Profile", "Loaded data - Username: $username, FullName: $fullName, Email: $email")
                
                // Update UI with real Firebase data
                findViewById<TextView>(R.id.tvUsernameTop)?.text = username
                findViewById<TextView>(R.id.tvFullName)?.text = fullName
                findViewById<TextView>(R.id.tvName)?.text = fullName
                findViewById<TextView>(R.id.tvEmail)?.text = username
                findViewById<TextView>(R.id.bioTextView)?.text = bio
                
                // Load profile picture from Firebase Storage
                val imgProfile = findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.imgAvatar)
                ImageUtils.loadProfilePictureUrlOrBase64(imgProfile, photoUrl, photoBase64, R.drawable.profile_pic, updatedAt)
                
                // Load counts and posts
                loadUserCounts(uid)
                loadUserPosts(uid)
                
                Log.d("Profile", "User data loaded successfully: $username")
                Toast.makeText(this, "Profile loaded: $username", Toast.LENGTH_SHORT).show()
                
            } else {
                Log.e("Profile", "User data not found in Firebase for UID: $uid")
                Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                
                // Show default data
                findViewById<TextView>(R.id.tvUsernameTop)?.text = "Unknown User"
                findViewById<TextView>(R.id.tvFullName)?.text = "Unknown User"
                findViewById<TextView>(R.id.tvName)?.text = "Unknown User"
                findViewById<TextView>(R.id.tvEmail)?.text = "unknown_user"
                findViewById<TextView>(R.id.bioTextView)?.text = "No bio available"
                val imgProfile = findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.imgAvatar)
                imgProfile?.setImageResource(R.drawable.profile_pic)
            }
        }.addOnFailureListener { error ->
            Log.e("Profile", "Failed to load user data: ${error.message}")
            Toast.makeText(this, "Failed to load profile data: ${error.message}", Toast.LENGTH_SHORT).show()
            
            // Show default data on error
            findViewById<TextView>(R.id.tvUsernameTop)?.text = "Error Loading"
            findViewById<TextView>(R.id.tvFullName)?.text = "Error Loading"
            findViewById<TextView>(R.id.tvName)?.text = "Error Loading"
            findViewById<TextView>(R.id.tvEmail)?.text = "error_user"
            findViewById<TextView>(R.id.bioTextView)?.text = "Failed to load bio"
            val imgProfile = findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.imgAvatar)
            imgProfile?.setImageResource(R.drawable.profile_pic)
        }
    }
    
    private fun loadUserCounts(uid: String) {
        // Load posts count
        db.getReference("posts").orderByChild("authorId").equalTo(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val postsCount = snapshot.childrenCount.toInt()
                    findViewById<TextView>(R.id.postsCount)?.text = postsCount.toString()
                    Log.d("Profile", "Posts count: $postsCount")
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Profile", "Failed to load posts count: ${error.message}")
                    findViewById<TextView>(R.id.postsCount)?.text = "0"
                }
            })
        
        // Load followers count
        db.getReference("followers").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val followersCount = snapshot.childrenCount.toInt()
                    findViewById<TextView>(R.id.followerCount)?.text = formatCount(followersCount)
                    Log.d("Profile", "Followers count: $followersCount")
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Profile", "Failed to load followers count: ${error.message}")
                    findViewById<TextView>(R.id.followerCount)?.text = "0"
                }
            })
        
        // Load following count
        db.getReference("following").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val followingCount = snapshot.childrenCount.toInt()
                    findViewById<TextView>(R.id.followingCount)?.text = followingCount.toString()
                    Log.d("Profile", "Following count: $followingCount")
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Profile", "Failed to load following count: ${error.message}")
                    findViewById<TextView>(R.id.followingCount)?.text = "0"
                }
            })
    }
    
    private fun loadUserPosts(uid: String) {
        db.getReference("posts").orderByChild("authorId").equalTo(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val posts = mutableListOf<Post>()
                    snapshot.children.forEach { child ->
                        val post = child.getValue(Post::class.java)
                        if (post != null) {
                            posts.add(post)
                        }
                    }
                    
                    // Sort by creation time (newest first)
                    posts.sortByDescending { it.createdAt }
                    
                    // Update the grid with user's posts
                    updatePostsGrid(posts)
                    Log.d("Profile", "Loaded ${posts.size} posts for user")
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("Profile", "Failed to load user posts: ${error.message}")
                }
            })
    }
    
    private fun updatePostsGrid(posts: List<Post>) {
        val gridLayout = findViewById<GridLayout>(R.id.postsGrid)
        gridLayout?.removeAllViews()
        
        // Take only the first 9 posts for the 3x3 grid
        val displayPosts = posts.take(9)
        
        displayPosts.forEach { post ->
            val imageView = ImageView(this)
            val layoutParams = GridLayout.LayoutParams()
            layoutParams.width = 0
            layoutParams.height = 0
            layoutParams.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            layoutParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            layoutParams.setMargins(4, 4, 4, 4)
            
            imageView.layoutParams = layoutParams
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.adjustViewBounds = true
            
            // Convert base64 to bitmap and set image
            try {
                val imageBytes = android.util.Base64.decode(post.mediaBase64, android.util.Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                imageView.setImageBitmap(bitmap)
            } catch (e: Exception) {
                Log.e("Profile", "Failed to decode image: ${e.message}")
                imageView.setImageResource(R.drawable.p1) // fallback image
            }
            
            gridLayout?.addView(imageView)
        }
    }
    
    private fun formatCount(count: Int): String {
        return when {
            count >= 1000000 -> "${count / 1000000}M"
            count >= 1000 -> "${count / 1000}K"
            else -> count.toString()
        }
    }
    
    private fun setupClickListeners(uid: String) {
        // Back button
        findViewById<ImageView>(R.id.btnBack)?.setOnClickListener {
            finish()
        }
        
        // Menu button - logout functionality
        findViewById<ImageView>(R.id.btnMenu)?.setOnClickListener {
            // Show logout confirmation
            android.app.AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes") { _, _ ->
                    auth.signOut()
                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@profile, login::class.java))
                    finish()
                }
                .setNegativeButton("No", null)
                .show()
        }
        
        // Follow button - check status first to determine action
        val btnFollow = findViewById<Button>(R.id.btnFollow)
        btnFollow?.setOnClickListener {
            val currentUid = auth.currentUser?.uid ?: return@setOnClickListener
            if (currentUid == uid) return@setOnClickListener // Can't follow yourself
            
            // Check if already following
            db.getReference("following").child(currentUid).child(uid).get()
                .addOnSuccessListener { followingSnap ->
                    if (followingSnap.exists()) {
                        // Already following - allow unfollow
                        unfollowUser(uid)
                    } else {
                        // Not following - check if request exists
                        db.getReference("followRequests").child(uid).child(currentUid).get()
                            .addOnSuccessListener { requestSnap ->
                                if (requestSnap.exists()) {
                                    // Request already sent
                                    Toast.makeText(this, "Follow request already sent", Toast.LENGTH_SHORT).show()
                                } else {
                                    // Send new follow request
                                    sendFollowRequest(uid)
                                }
                            }
                            .addOnFailureListener { error ->
                                Log.e("Profile", "Failed to check follow request: ${error.message}")
                                Toast.makeText(this, "Error checking follow status", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { error ->
                    Log.e("Profile", "Failed to check follow status: ${error.message}")
                    Toast.makeText(this, "Error checking follow status", Toast.LENGTH_SHORT).show()
                }
        }
        
        // Check follow status and update button
        checkFollowStatus(uid)
        
        // Message button
        findViewById<Button>(R.id.btnMessage)?.setOnClickListener {
            val intent = Intent(this@profile, chat::class.java)
            intent.putExtra("receiverId", uid)
            // Load receiver name from Firebase
            db.getReference("users").child(uid).get()
                .addOnSuccessListener { snapshot ->
                    val fullName = snapshot.child("fullName").value?.toString()
                    val username = snapshot.child("username").value?.toString()
                    val receiverName = fullName ?: username ?: "User"
                    intent.putExtra("receiverName", receiverName)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener {
                    intent.putExtra("receiverName", "User")
                    startActivity(intent)
                    finish()
                }
        }
        
        // Email button
        findViewById<Button>(R.id.btnEmail)?.setOnClickListener {
            val intent = Intent(this@profile, editprofile::class.java)
            startActivity(intent)
            finish()
        }
        
        // Navigation bar buttons
        findViewById<ImageButton>(R.id.navHome)?.setOnClickListener {
            // Home/Feed - go to main feed
            startActivity(Intent(this@profile, feed::class.java))
            finish()
        }
        
        findViewById<ImageButton>(R.id.navSearch)?.setOnClickListener {
            // Search - go to search/discover screen (internshala)
            startActivity(Intent(this@profile, internshala::class.java))
            finish()
        }
        
        findViewById<ImageButton>(R.id.navAdd)?.setOnClickListener {
            // Add/Create - go to gallery for creating posts
            startActivity(Intent(this@profile, gallery::class.java))
            finish()
        }
        
        findViewById<ImageButton>(R.id.navHeart)?.setOnClickListener {
            // Heart/Notifications - go to followers/notifications screen
            startActivity(Intent(this@profile, followingyou::class.java))
            finish()
        }
        
        findViewById<ImageButton>(R.id.navProfile)?.setOnClickListener {
            // Profile - go to instaprof (main profile screen)
            val intent = Intent(this@profile, instaprof::class.java)
            intent.putExtra("uid", auth.currentUser?.uid)
            startActivity(intent)
            finish()
        }
        
        // Followers count click listener
        findViewById<LinearLayout>(R.id.followersLayout)?.setOnClickListener {
            val intent = Intent(this@profile, followingyou::class.java)
            intent.putExtra("uid", uid)
            startActivity(intent)
        }
        
        // Following count click listener
        findViewById<LinearLayout>(R.id.followingLayout)?.setOnClickListener {
            val intent = Intent(this@profile, following::class.java)
            intent.putExtra("uid", uid)
            startActivity(intent)
        }
    }
    
    private fun checkFollowStatus(targetUid: String) {
        val currentUid = auth.currentUser?.uid ?: return
        
        if (currentUid == targetUid) {
            // Hide follow button for own profile
            findViewById<Button>(R.id.btnFollow)?.visibility = android.view.View.GONE
            return
        }
        
        val followingRef = db.getReference("following").child(currentUid).child(targetUid)
        val followRequestsRef = db.getReference("followRequests").child(targetUid).child(currentUid)
        
        followingRef.get().addOnSuccessListener { followingSnap ->
            if (followingSnap.exists()) {
                updateFollowButton(true)
            } else {
                // Check if there's a pending request
                followRequestsRef.get().addOnSuccessListener { requestSnap ->
                    if (requestSnap.exists()) {
                        updateFollowButton(false, "Requested")
                    } else {
                        updateFollowButton(false)
                    }
                }
            }
        }
    }
    
    private fun updateFollowButton(isFollowing: Boolean, text: String? = null) {
        val button = findViewById<Button>(R.id.btnFollow)
        when {
            isFollowing -> {
                button?.text = "Following"
                button?.setBackgroundResource(R.drawable.btn_following_border)
                button?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.brick))
            }
            text == "Requested" -> {
                button?.text = "Requested"
                button?.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.grey))
                button?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white))
                button?.isEnabled = false
            }
            else -> {
                button?.text = "Follow"
                button?.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.brick))
                button?.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.white))
                button?.isEnabled = true
            }
        }
    }
    
    private fun sendFollowRequest(targetUid: String) {
        val currentUid = auth.currentUser?.uid ?: return
        
        if (currentUid == targetUid) {
            Toast.makeText(this, "You can't follow yourself", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if user has private account (for now, assume all accounts are private)
        val followRequest = mapOf(
            "fromUid" to currentUid,
            "toUid" to targetUid,
            "timestamp" to System.currentTimeMillis(),
            "status" to "pending"
        )
        
        // Save follow request to Firebase
        db.getReference("followRequests").child(targetUid).child(currentUid).setValue(followRequest)
            .addOnSuccessListener {
                Toast.makeText(this, "Follow request sent!", Toast.LENGTH_SHORT).show()
                updateFollowButton(false, "Requested")
                
                // Create notification for the target user
                createFollowRequestNotification(targetUid, currentUid)
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Failed to send follow request: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun unfollowUser(targetUid: String) {
        val currentUid = auth.currentUser?.uid ?: return
        
        // Remove from following/followers
        db.getReference("following").child(currentUid).child(targetUid).removeValue()
            .addOnSuccessListener {
                db.getReference("followers").child(targetUid).child(currentUid).removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Unfollowed successfully", Toast.LENGTH_SHORT).show()
                        updateFollowButton(false)
                    }
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Failed to unfollow: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun createFollowRequestNotification(targetUid: String, fromUid: String) {
        // Get current user's info
        db.getReference("users").child(fromUid).get().addOnSuccessListener { userSnap ->
            val username = userSnap.child("username").value?.toString() ?: "Unknown"
            val fullName = userSnap.child("fullName").value?.toString() ?: "Unknown"
            
            // Create notification using RealNotificationManager for Firebase storage
            val notificationManager = RealNotificationManager(this)
            val notificationId = FirebaseDatabase.getInstance().reference.child("notifications").push().key
            if (notificationId == null) {
                Log.e("Profile", "Failed to generate notification ID")
                return@addOnSuccessListener
            }
            
            // Save notification to Firebase - use map with string type for proper display in YOU tab
            val notificationData = hashMapOf<String, Any>(
                "notificationId" to notificationId,
                "fromUserId" to fromUid,
                "fromUsername" to username,
                "fromFullName" to fullName,
                "targetUserId" to targetUid,
                "action" to "wants to follow you",
                "type" to "follow_request",  // Use string type for proper categorization
                "timestamp" to System.currentTimeMillis(),
                "read" to false
            )
            
            // Save notification to Firebase
            db.getReference("notifications").child(targetUid).child(notificationId).setValue(notificationData)
                .addOnSuccessListener {
                    Log.d("Profile", "Follow request notification created for $targetUid")
                }
                .addOnFailureListener { error ->
                    Log.e("Profile", "Failed to create follow request notification: ${error.message}")
                }
            
            // Also send push notification using NotificationManager
            try {
                val fcmNotificationManager = NotificationManager()
                fcmNotificationManager.sendFollowRequestNotification(
                    receiverId = targetUid,
                    requesterName = fullName.ifEmpty { username }
                )
            } catch (e: Exception) {
                Log.e("Profile", "Failed to send push notification: ${e.message}")
            }
        }
    }
}
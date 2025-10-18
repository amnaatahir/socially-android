package com.example.smd_a1

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class followingyou : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseDatabase.getInstance("https://smda2-31b7e-default-rtdb.firebaseio.com")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_followingyou)

        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return
        val layout = findViewById<LinearLayout>(R.id.followersContainer)
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnFollowing = findViewById<TextView>(R.id.btnFollowing)
        val btnYou = findViewById<TextView>(R.id.btnYou)

        btnBack.setOnClickListener { finish() }

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
            val intent = Intent(this, instaprof::class.java)
            intent.putExtra("uid", auth.currentUser?.uid)
            startActivity(intent)
        }
        
        // Tab switching functionality
        btnFollowing.setOnClickListener { 
            btnFollowing.setTextColor(ContextCompat.getColor(this@followingyou, R.color.brick))
            btnYou.setTextColor(ContextCompat.getColor(this@followingyou, R.color.grey))
            if (btnFollowing.text.toString().equals("Followers", ignoreCase = true)) {
                val targetUid = intent.getStringExtra("uid") ?: uid
                loadFollowersList(targetUid, layout)
            } else {
                val targetUid = intent.getStringExtra("uid") ?: uid
                loadFollowingData(targetUid, layout)
            }
        }
        
        btnYou.setOnClickListener {
            btnYou.setTextColor(ContextCompat.getColor(this@followingyou, R.color.brick))
            btnFollowing.setTextColor(ContextCompat.getColor(this@followingyou, R.color.grey))
            loadFollowersData(uid, layout)
        }
        
        // Mode routing from profile taps
        when (intent.getStringExtra("mode")) {
            "following" -> {
                btnFollowing.text = "Following"
                btnFollowing.setTextColor(ContextCompat.getColor(this@followingyou, R.color.brick))
                btnYou.setTextColor(ContextCompat.getColor(this@followingyou, R.color.grey))
                val targetUid = intent.getStringExtra("uid") ?: uid
                loadFollowingData(targetUid, layout)
            }
            "followers" -> {
                btnFollowing.text = "Followers"
                btnFollowing.setTextColor(ContextCompat.getColor(this@followingyou, R.color.brick))
                btnYou.setTextColor(ContextCompat.getColor(this@followingyou, R.color.grey))
                val targetUid = intent.getStringExtra("uid") ?: uid
                loadFollowersList(targetUid, layout)
            }
            else -> {
                // Default to YOU tab (notifications)
                loadFollowersData(uid, layout)
            }
        }
    }

    private fun loadFollowingData(uid: String, layout: LinearLayout) {
        db.getReference("following").child(uid).get().addOnSuccessListener { snap ->
            layout.removeAllViews()
            if (!snap.exists()) {
                val tv = TextView(this@followingyou)
                tv.text = "You are not following anyone yet."
                tv.setTextColor(ContextCompat.getColor(this@followingyou, R.color.grey))
                tv.textSize = 15f
                tv.setPadding(16, 32, 16, 32)
                layout.addView(tv)
                return@addOnSuccessListener
            }

            for (child in snap.children) {
                val userId = child.key ?: continue
                db.getReference("users").child(userId).get().addOnSuccessListener {
                    val name = it.child("fullName").value?.toString() ?: "Unknown"
                    val username = it.child("username").value?.toString() ?: "user"
                    val row = layoutInflater.inflate(R.layout.item_follower_row, layout, false)

                    val tvName = row.findViewById<TextView>(R.id.tvName)
                    val tvUsername = row.findViewById<TextView>(R.id.tvUsername)
                    val btnFollow = row.findViewById<Button>(R.id.btnFollow)
                    val imgProfile = row.findViewById<ImageView>(R.id.imgProfile)

                    tvName.text = name
                    tvUsername.text = "@$username"
                    imgProfile.setOnClickListener {
                        startActivity(Intent(this, profile::class.java).putExtra("uid", userId))
                    }

                    checkFollowStatus(uid, userId, btnFollow)
                    btnFollow.setOnClickListener {
                        toggleFollow(uid, userId, btnFollow)
                    }
                    layout.addView(row)
                }
            }
        }
    }

    private fun loadFollowersList(uid: String, layout: LinearLayout) {
        db.getReference("followers").child(uid).get().addOnSuccessListener { snap ->
            layout.removeAllViews()
            if (!snap.exists()) {
                val tv = TextView(this@followingyou)
                tv.text = "No followers yet."
                tv.setTextColor(ContextCompat.getColor(this@followingyou, R.color.grey))
                tv.textSize = 15f
                tv.setPadding(16, 32, 16, 32)
                layout.addView(tv)
                return@addOnSuccessListener
            }

            for (child in snap.children) {
                val userId = child.key ?: continue
                db.getReference("users").child(userId).get().addOnSuccessListener {
                    val name = it.child("fullName").value?.toString() ?: "Unknown"
                    val username = it.child("username").value?.toString() ?: "user"
                    val row = layoutInflater.inflate(R.layout.item_follower_row, layout, false)

                    val tvName = row.findViewById<TextView>(R.id.tvName)
                    val tvUsername = row.findViewById<TextView>(R.id.tvUsername)
                    val btnFollow = row.findViewById<Button>(R.id.btnFollow)
                    val imgProfile = row.findViewById<ImageView>(R.id.imgProfile)

                    tvName.text = name
                    tvUsername.text = "@${'$'}username"
                    imgProfile.setOnClickListener {
                        startActivity(Intent(this, profile::class.java).putExtra("uid", userId))
                    }

                    // For followers list: allow follow back or message if mutual
                    checkFollowStatus(uid, userId, btnFollow)
                    btnFollow.setOnClickListener {
                        toggleFollow(uid, userId, btnFollow)
                    }
                    layout.addView(row)
                }
            }
        }
    }

    private fun loadFollowersData(uid: String, layout: LinearLayout) {
        layout.removeAllViews()
        
        // Load notifications feed with categorized sections
        loadNotificationsFeed(uid, layout)
    }

    private fun loadNotificationsFeed(uid: String, layout: LinearLayout) {
        // Load actual notifications from Firebase database
        val notificationsRef = db.getReference("notifications").child(uid)
        
        notificationsRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists() || snapshot.children.count() == 0) {
                // No notifications yet
                val tv = TextView(this@followingyou)
                tv.text = "No notifications yet.\n\nWhen people interact with you, you'll see:\n• Who liked your posts\n• Who started following you\n• Who mentioned you in comments"
                tv.setTextColor(ContextCompat.getColor(this@followingyou, R.color.grey))
                tv.textSize = 14f
                tv.setPadding(16, 32, 16, 32)
                tv.gravity = android.view.Gravity.CENTER
                layout.addView(tv)
                return@addOnSuccessListener
            }

            // Categorize notifications by time
            val newNotifications = mutableListOf<NotificationItem>()
            val todayNotifications = mutableListOf<NotificationItem>()
            val weekNotifications = mutableListOf<NotificationItem>()
            val monthNotifications = mutableListOf<NotificationItem>()

            for (child in snapshot.children) {
                val notificationData = child.value as? Map<String, Any> ?: continue
                val type = notificationData["type"] as? String ?: "follow"
                val timestamp = (notificationData["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                val fromUserId = notificationData["fromUserId"] as? String ?: ""
                val fromUsername = notificationData["fromUsername"] as? String ?: "Unknown"
                val fromFullName = notificationData["fromFullName"] as? String ?: fromUsername
                val action = notificationData["action"] as? String ?: "started following you"
                val thumbnail = notificationData["thumbnail"] as? String

                val timeAgo = getTimeAgo(timestamp)
                val notificationType = when (type) {
                    "like" -> NotificationType.LIKE
                    "mention" -> NotificationType.MENTION
                    "follow", "follow_request", "follow_accepted", "followed" -> NotificationType.FOLLOW
                    "story_added" -> NotificationType.MENTION
                    else -> NotificationType.MENTION
                }

                val notification = NotificationItem(
                    notificationId = child.key ?: "",
                    fromUserId = fromUserId,
                    username = fromFullName,
                    action = action,
                    timestamp = timeAgo,
                    timestampMs = timestamp,
                    thumbnail = thumbnail,
                    type = notificationType
                )

                when {
                    timeAgo.contains("h") && timeAgo.replace("h", "").toIntOrNull() ?: 0 <= 24 -> newNotifications.add(notification)
                    timeAgo.contains("d") && timeAgo.replace("d", "").toIntOrNull() ?: 0 <= 7 -> todayNotifications.add(notification)
                    timeAgo.contains("w") && timeAgo.replace("w", "").toIntOrNull() ?: 0 <= 4 -> weekNotifications.add(notification)
                    else -> monthNotifications.add(notification)
                }
            }
            // Sort each bucket by most-recent-first
            val newSorted = newNotifications.sortedByDescending { it.timestampMs }
            val todaySorted = todayNotifications.sortedByDescending { it.timestampMs }
            val weekSorted = weekNotifications.sortedByDescending { it.timestampMs }
            val monthSorted = monthNotifications.sortedByDescending { it.timestampMs }

            displayNotifications(layout, newSorted, todaySorted, weekSorted, monthSorted)
        }.addOnFailureListener { error ->
            android.util.Log.e("FollowingYou", "Failed to load notifications: ${error.message}")
            // Show error message instead of dummy data
            val tv = TextView(this@followingyou)
            tv.text = "Unable to load notifications.\n\nPlease check your connection and try again."
            tv.setTextColor(ContextCompat.getColor(this@followingyou, R.color.grey))
            tv.textSize = 14f
            tv.setPadding(16, 32, 16, 32)
            tv.gravity = android.view.Gravity.CENTER
            layout.addView(tv)
        }
    }

    private fun displayNotifications(
        layout: LinearLayout,
        newNotifications: List<NotificationItem>,
        todayNotifications: List<NotificationItem>,
        weekNotifications: List<NotificationItem>,
        monthNotifications: List<NotificationItem>
    ) {
        val sections = listOf(
            "New" to newNotifications,
            "Today" to todayNotifications,
            "This Week" to weekNotifications,
            "This Month" to monthNotifications
        )

        for ((title, notifications) in sections) {
            if (notifications.isNotEmpty()) {
                // Add section header
                val headerTv = TextView(this@followingyou)
                headerTv.text = title
                headerTv.setTextColor(ContextCompat.getColor(this@followingyou, R.color.black))
                headerTv.textSize = 16f
                headerTv.setPadding(16, 16, 16, 8)
                headerTv.setTypeface(null, android.graphics.Typeface.BOLD)
                layout.addView(headerTv)

                // Add notifications for this section (already sorted desc)
                for (notification in notifications) {
                    val notificationRow = createNotificationRow(notification)
                    layout.addView(notificationRow)
                }
            }
        }
    }

    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "now"
            diff < 3600000 -> "${diff / 60000}m"
            diff < 86400000 -> "${diff / 3600000}h"
            diff < 604800000 -> "${diff / 86400000}d"
            diff < 2592000000 -> "${diff / 604800000}w"
            else -> "${diff / 2592000000}mo"
        }
    }


    private fun createNotificationRow(notification: NotificationItem): LinearLayout {
        val row = LinearLayout(this@followingyou)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(16, 12, 16, 12)
        
        // Profile picture
        val profilePic = ImageView(this@followingyou)
        profilePic.layoutParams = LinearLayout.LayoutParams(48, 48)
        profilePic.scaleType = ImageView.ScaleType.CENTER_CROP
        
        // Add circular background
        profilePic.background = ContextCompat.getDrawable(this@followingyou, R.drawable.hollowcircle)
        
        // Load profile picture from Firebase (URL first, then Base64 fallback)
        if (notification.fromUserId.isNotEmpty()) {
            db.getReference("users").child(notification.fromUserId)
                .get().addOnSuccessListener { snapshot ->
                    val photoUrl = snapshot.child("photoUrl").getValue(String::class.java)
                    val photoBase64 = snapshot.child("photoBase64").getValue(String::class.java)
                    val updatedAt = snapshot.child("photoUpdatedAt").getValue(Long::class.java)
                    ImageUtils.loadProfilePictureUrlOrBase64(profilePic, photoUrl, photoBase64, R.drawable.profile_pic, updatedAt)
                }.addOnFailureListener {
                    ImageUtils.loadProfilePicture(profilePic, null, R.drawable.profile_pic)
                }
        } else {
            ImageUtils.loadProfilePicture(profilePic, null, R.drawable.profile_pic)
        }
        
        // Notification content
        val contentLayout = LinearLayout(this@followingyou)
        contentLayout.orientation = LinearLayout.VERTICAL
        contentLayout.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        contentLayout.setPadding(12, 0, 0, 0)
        
        // Notification text
        val notificationText = TextView(this@followingyou)
        notificationText.text = "${notification.username} ${notification.action}."
        notificationText.textSize = 14f
        notificationText.setTextColor(ContextCompat.getColor(this@followingyou, R.color.black))
        
        // Timestamp
        val timestamp = TextView(this@followingyou)
        timestamp.text = notification.timestamp
        timestamp.textSize = 12f
        timestamp.setTextColor(ContextCompat.getColor(this@followingyou, R.color.grey))
        
        contentLayout.addView(notificationText)
        contentLayout.addView(timestamp)
        
        // Add reply button for mentions
        if (notification.type == NotificationType.MENTION) {
            val replyLayout = LinearLayout(this@followingyou)
            replyLayout.orientation = LinearLayout.HORIZONTAL
            replyLayout.setPadding(0, 8, 0, 0)
            
            val heartIcon = ImageView(this@followingyou)
            heartIcon.layoutParams = LinearLayout.LayoutParams(20, 20)
            heartIcon.setImageResource(R.drawable.red_heart)
            heartIcon.setPadding(0, 0, 8, 0)
            
            val replyText = TextView(this@followingyou)
            replyText.text = "Reply"
            replyText.textSize = 12f
            replyText.setTextColor(ContextCompat.getColor(this@followingyou, R.color.grey))
            
            replyLayout.addView(heartIcon)
            replyLayout.addView(replyText)
            contentLayout.addView(replyLayout)
        }
        
        row.addView(profilePic)
        row.addView(contentLayout)
        
        // Add action button or thumbnail on the right
        if (notification.thumbnail != null) {
            // Add thumbnail
            val thumb = ImageView(this)
            thumb.layoutParams = LinearLayout.LayoutParams(60, 60)
            thumb.setImageResource(R.drawable.profile_pic) // Default thumbnail
            thumb.scaleType = ImageView.ScaleType.CENTER_CROP
            thumb.background = ContextCompat.getDrawable(this, R.drawable.edittext_bg)
            row.addView(thumb)
        } else if (notification.type == NotificationType.FOLLOW) {
            // Check if this is a follow request (pending) or already followed
            val notificationId = notification.notificationId
            val fromUserId = notification.fromUserId
            
            // Check if there's a pending follow request
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUid != null) {
                db.getReference("followRequests").child(currentUid).child(fromUserId).get()
                    .addOnSuccessListener { requestSnap ->
                        if (requestSnap.exists()) {
                            // This is a pending follow request - show Accept/Reject buttons
                            val buttonLayout = LinearLayout(this@followingyou)
                            buttonLayout.orientation = LinearLayout.HORIZONTAL
                            buttonLayout.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            buttonLayout.setPadding(8, 0, 0, 0)
                            
                            // Accept button
                            val acceptBtn = Button(this@followingyou)
                            acceptBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                setMargins(0, 0, 12, 0)
                            }
                            acceptBtn.text = "Accept"
                            acceptBtn.isAllCaps = false
                            acceptBtn.textSize = 13f
                            acceptBtn.setPadding(24, 12, 24, 12)
                            acceptBtn.setBackgroundResource(R.drawable.btn_primary)
                            acceptBtn.setTextColor(Color.WHITE)
                            acceptBtn.setOnClickListener {
                                acceptFollowRequest(fromUserId, notificationId)
                            }
                            
                            // Reject button
                            val rejectBtn = Button(this@followingyou)
                            rejectBtn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            rejectBtn.text = "Reject"
                            rejectBtn.isAllCaps = false
                            rejectBtn.textSize = 13f
                            rejectBtn.setPadding(24, 12, 24, 12)
                            rejectBtn.setBackgroundResource(R.drawable.btn_secondary)
                            rejectBtn.setTextColor(ContextCompat.getColor(this@followingyou, R.color.black))
                            rejectBtn.setOnClickListener {
                                rejectFollowRequest(fromUserId, notificationId)
                            }
                            
                            buttonLayout.addView(acceptBtn)
                            buttonLayout.addView(rejectBtn)
                            row.addView(buttonLayout)
                        } else {
                            // Already following - show Message button
                            val button = Button(this@followingyou)
                            button.layoutParams = LinearLayout.LayoutParams(80, 32)
                            button.textSize = 12f
                            button.setPadding(12, 4, 12, 4)
                            button.text = "Message"
                            button.setBackgroundResource(R.drawable.btn_secondary)
                            button.setTextColor(ContextCompat.getColor(this@followingyou, R.color.black))
                            button.setOnClickListener {
                                val intent = Intent(this@followingyou, chat::class.java)
                                intent.putExtra("receiverId", fromUserId)
                                intent.putExtra("receiverName", notification.username)
                                this@followingyou.startActivity(intent)
                            }
                            row.addView(button)
                        }
                    }
            }
        }
        
        return row
    }

    data class NotificationSection(
        val title: String,
        val notifications: List<NotificationItem>
    )

    data class NotificationItem(
        val notificationId: String = "",
        val fromUserId: String = "",
        val username: String,
        val action: String,
        val timestamp: String,
        val timestampMs: Long,
        val thumbnail: String?,
        val type: NotificationType
    )
    
    private fun acceptFollowRequest(fromUserId: String, notificationId: String) {
        val currentUid = auth.currentUser?.uid ?: return
        
        // Remove follow request
        db.getReference("followRequests").child(currentUid).child(fromUserId).removeValue()
            .addOnSuccessListener {
                // Correct direction:
                // fromUserId requested to follow currentUid → after accept:
                //   following/fromUserId/currentUid = true
                //   followers/currentUid/fromUserId = true
                db.getReference("following").child(fromUserId).child(currentUid).setValue(true)
                db.getReference("followers").child(currentUid).child(fromUserId).setValue(true)
                
                // Send push notification to requester
                db.getReference("users").child(currentUid).get()
                    .addOnSuccessListener { userSnap ->
                        val accepterName = userSnap.child("fullName").value?.toString()
                            ?: userSnap.child("username").value?.toString() ?: "Someone"
                        
                        val notificationManager = NotificationManager()
                        notificationManager.sendFollowRequestAcceptedNotification(
                            receiverId = fromUserId,
                            accepterName = accepterName
                        )
                    }
                
                Toast.makeText(this@followingyou, "Follow request accepted", Toast.LENGTH_SHORT).show()
                
                // Create a YOU-tab notification for current user: "started following you"
                val youRef = db.getReference("notifications").child(currentUid)
                val youNotificationId = youRef.push().key
                if (youNotificationId != null) {
                    val notif = hashMapOf<String, Any>(
                        "notificationId" to youNotificationId,
                        "fromUserId" to fromUserId,
                        "fromUsername" to "",
                        "fromFullName" to "",
                        "action" to "started following you",
                        "type" to "follow",
                        "timestamp" to System.currentTimeMillis(),
                        "read" to false
                    )
                    youRef.child(youNotificationId).setValue(notif)
                }

                // Remove notification and refresh
                db.getReference("notifications").child(currentUid).child(notificationId).removeValue()
                    .addOnSuccessListener {
                        val layout = this@followingyou.findViewById<LinearLayout>(R.id.followersContainer)
                        loadNotificationsFeed(currentUid, layout)
                    }
            }
    }
    
    private fun rejectFollowRequest(fromUserId: String, notificationId: String) {
        val currentUid = auth.currentUser?.uid ?: return
        
        // Remove follow request
        db.getReference("followRequests").child(currentUid).child(fromUserId).removeValue()
            .addOnSuccessListener {
                // Remove notification
                db.getReference("notifications").child(currentUid).child(notificationId).removeValue()
                
                Toast.makeText(this@followingyou, "Follow request rejected", Toast.LENGTH_SHORT).show()
                
                // Refresh notifications
                loadNotificationsFeed(currentUid, findViewById(R.id.followersContainer))
            }
    }

    enum class NotificationType {
        LIKE, FOLLOW, MENTION
    }

    private fun checkFollowStatus(currentUid: String, targetUid: String, button: Button) {
        db.getReference("following").child(currentUid).child(targetUid).get()
            .addOnSuccessListener { snap -> updateFollowUI(button, snap.exists()) }
    }

    private fun toggleFollow(currentUid: String, targetUid: String, button: Button) {
        val followingRef = db.getReference("following").child(currentUid).child(targetUid)
        val followersRef = db.getReference("followers").child(targetUid).child(currentUid)

        followingRef.get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                // Unfollow - this is allowed
                followingRef.removeValue()
                followersRef.removeValue()
                updateFollowUI(button, false)
                // Refresh the following data to remove unfollowed users
                loadFollowingData(currentUid, findViewById(R.id.followersContainer))
            } else {
                // All accounts are private - cannot follow directly, must send request
                Toast.makeText(this@followingyou, "Please send a follow request first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFollowUI(button: Button, isFollowing: Boolean) {
        if (isFollowing) {
            button.text = "Following"
            button.setBackgroundResource(R.drawable.btn_following_border)
            button.setTextColor(ContextCompat.getColor(this@followingyou, R.color.brick))
        } else {
            button.text = "Follow"
            button.setBackgroundColor(ContextCompat.getColor(this@followingyou, R.color.brick))
            button.setTextColor(Color.WHITE)
        }
    }
}
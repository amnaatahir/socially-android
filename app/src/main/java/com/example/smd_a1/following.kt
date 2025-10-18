package com.example.smd_a1

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase

class following : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseDatabase.getInstance("https://smda2-31b7e-default-rtdb.firebaseio.com")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_following)

        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return
        val layout = findViewById<LinearLayout>(R.id.followingContainer)
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

        // Tabs
        btnFollowing.setOnClickListener {
            btnFollowing.setTextColor(ContextCompat.getColor(this, R.color.brick))
            btnYou.setTextColor(ContextCompat.getColor(this, R.color.grey))
            loadFollowingData(uid, layout)
        }
        btnYou.setOnClickListener {
            btnYou.setTextColor(ContextCompat.getColor(this, R.color.brick))
            btnFollowing.setTextColor(ContextCompat.getColor(this, R.color.grey))
            loadFollowersData(uid, layout)
        }

        // Default
        loadFollowingData(uid, layout)
    }

    private fun loadFollowingData(uid: String, layout: LinearLayout) {
        layout.removeAllViews()

        db.getReference("following").child(uid).get().addOnSuccessListener { snap ->
            if (!snap.exists() || snap.children.count() == 0) {
                val tv = TextView(this@following)
                tv.text = "You're not following anyone yet.\n\nStart following people to see their activities here!"
                tv.setTextColor(ContextCompat.getColor(this@following, R.color.grey))
                tv.textSize = 14f
                tv.setPadding(16, 32, 16, 32)
                tv.gravity = android.view.Gravity.CENTER
                layout.addView(tv)
                return@addOnSuccessListener
            }
            loadActivityFeed(uid, layout)
        }
    }

    private fun loadActivityFeed(uid: String, layout: LinearLayout) {
        layout.removeAllViews()

        db.getReference("following").child(uid).get().addOnSuccessListener { followingSnap ->
            if (!followingSnap.exists()) {
                val tv = TextView(this@following)
                tv.text = "You're not following anyone yet.\n\nStart following people to see their activities here!"
                tv.setTextColor(ContextCompat.getColor(this@following, R.color.grey))
                tv.textSize = 14f
                tv.setPadding(16, 32, 16, 32)
                tv.gravity = android.view.Gravity.CENTER
                layout.addView(tv)
                return@addOnSuccessListener
            }

            val followingUids = mutableSetOf<String>()
            followingSnap.children.forEach { child ->
                if (child.getValue(Boolean::class.java) == true) {
                    child.key?.let { followingUids.add(it) }
                }
            }

            if (followingUids.isEmpty()) {
                val tv = TextView(this@following)
                tv.text = "You're not following anyone yet.\n\nStart following people to see their activities here!"
                tv.setTextColor(ContextCompat.getColor(this@following, R.color.grey))
                tv.textSize = 14f
                tv.setPadding(16, 32, 16, 32)
                tv.gravity = android.view.Gravity.CENTER
                layout.addView(tv)
                return@addOnSuccessListener
            }

            val activities = mutableListOf<ActivityItem>()

            // Recent posts from people being followed
            db.getReference("posts")
                .orderByChild("createdAt")
                .limitToLast(50)
                .get()
                .addOnSuccessListener { postsSnap ->
                    val postsByUser = mutableMapOf<String, MutableList<String>>()

                    postsSnap.children.forEach { postChild ->
                        val authorId = postChild.child("authorId").getValue(String::class.java)
                        val postId = postChild.key
                        if (authorId != null && postId != null && followingUids.contains(authorId)) {
                            postsByUser.getOrPut(authorId) { mutableListOf() }.add(postId)
                        }
                    }

                    var processedCount = 0
                    val totalPosts = postsByUser.values.sumOf { it.size }

                    postsByUser.forEach { (authorId, postIds) ->
                        postIds.forEach { postId ->
                            db.getReference("posts").child(postId).child("likes")
                                .get()
                                .addOnSuccessListener { likesSnap ->
                                    if (likesSnap.exists()) {
                                        val likers = mutableListOf<String>()
                                        likesSnap.children.forEach { liker ->
                                            liker.key?.let { likerUid ->
                                                if (likerUid != uid && followingUids.contains(likerUid)) {
                                                    db.getReference("users").child(likerUid).child("username")
                                                        .get()
                                                        .addOnSuccessListener { usernameSnap ->
                                                            val username = usernameSnap.value?.toString() ?: "user"
                                                            likers.add(username)

                                                            db.getReference("users").child(authorId).child("username")
                                                                .get()
                                                                .addOnSuccessListener { authorSnap ->
                                                                    val authorUsername = authorSnap.value?.toString() ?: "user"
                                                                    val postTimestamp = postsSnap.child(postId).child("createdAt")
                                                                        .value as? Long ?: System.currentTimeMillis()
                                                                    val timeAgo = getTimeAgo(postTimestamp)

                                                                    val activityText = when {
                                                                        likers.size > 3 -> "${likers.take(2).joinToString(", ")} and ${likers.size - 2} others liked $authorUsername's photo"
                                                                        likers.size > 1 -> "${likers.joinToString(", ")} liked $authorUsername's photo"
                                                                        likers.size == 1 -> "${likers[0]} liked $authorUsername's photo"
                                                                        else -> null
                                                                    }

                                                                    if (activityText != null) {
                                                                        activities.add(
                                                                            ActivityItem(
                                                                                username = likers.joinToString(", "),
                                                                                action = "liked $authorUsername's photo",
                                                                                timestamp = timeAgo,
                                                                                thumbnails = listOf(postId)
                                                                            )
                                                                        )
                                                                    }

                                                                    processedCount++
                                                                    if (processedCount >= totalPosts || processedCount >= 20) {
                                                                        displayActivities(activities, layout)
                                                                    }
                                                                }
                                                        }
                                                }
                                            }
                                        }
                                    }

                                    processedCount++
                                    if (processedCount >= totalPosts || processedCount >= 20) {
                                        displayActivities(activities, layout)
                                    }
                                }
                        }
                    }

                    // Also load follow activities
                    db.getReference("followers")
                        .get()
                        .addOnSuccessListener { followersSnap ->
                            followersSnap.children.forEach { followerSnap ->
                                val followedUserId = followerSnap.key ?: return@forEach
                                if (followingUids.contains(followedUserId)) {
                                    followerSnap.children.forEach { follower ->
                                        val followerId = follower.key ?: return@forEach
                                        if (followerId != uid && followingUids.contains(followerId)) {
                                            db.getReference("users").child(followerId).child("username")
                                                .get()
                                                .addOnSuccessListener { usernameSnap ->
                                                    val username = usernameSnap.value?.toString() ?: "user"
                                                    db.getReference("users").child(followedUserId).child("username")
                                                        .get()
                                                        .addOnSuccessListener { followedSnap ->
                                                            val followedUsername = followedSnap.value?.toString() ?: "user"
                                                            val followTimestamp = System.currentTimeMillis()
                                                            activities.add(
                                                                ActivityItem(
                                                                    username = username,
                                                                    action = "started following $followedUsername",
                                                                    timestamp = getTimeAgo(followTimestamp),
                                                                    thumbnails = emptyList()
                                                                )
                                                            )
                                                            displayActivities(activities, layout)
                                                        }
                                                }
                                        }
                                    }
                                }
                            }
                        }
                }
        }
    }

    private fun displayActivities(activities: List<ActivityItem>, layout: LinearLayout) {
        val uniqueActivities = activities
            .distinctBy { "${it.username}_${it.action}" }
            .sortedByDescending { it.timestamp }
            .take(20)

        if (uniqueActivities.isEmpty()) {
            val tv = TextView(this@following)
            tv.text = "No recent activity from people you follow."
            tv.setTextColor(ContextCompat.getColor(this@following, R.color.grey))
            tv.textSize = 14f
            tv.setPadding(16, 32, 16, 32)
            tv.gravity = android.view.Gravity.CENTER
            layout.addView(tv)
            return
        }

        uniqueActivities.forEach { activity ->
            val activityRow = createActivityRow(activity)
            layout.addView(activityRow)
        }
    }

    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 3_600_000L -> "${diff / 60_000L}m"
            diff < 86_400_000L -> "${diff / 3_600_000L}h"
            diff < 604_800_000L -> "${diff / 86_400_000L}d"
            diff < 2_592_000_000L -> "${diff / 604_800_000L}w"
            else -> "${diff / 2_592_000_000L}mo"
        }
    }

    private fun createActivityRow(activity: ActivityItem): LinearLayout {
        val row = LinearLayout(this@following).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
        }

        val profilePic = ImageView(this@following).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48)
            setImageResource(R.drawable.profile_pic)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(this@following, R.drawable.hollowcircle)
        }

        val contentLayout = LinearLayout(this@following).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(12, 0, 0, 0)
        }

        val activityText = TextView(this@following).apply {
            text = "${activity.username} ${activity.action}."
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@following, R.color.black))
        }

        val timestamp = TextView(this@following).apply {
            text = activity.timestamp
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@following, R.color.grey))
        }

        contentLayout.addView(activityText)
        contentLayout.addView(timestamp)

        if (activity.thumbnails.isNotEmpty()) {
            val thumbnailLayout = LinearLayout(this@following).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 0)
            }
            val maxThumbs = minOf(activity.thumbnails.size, 8)
            repeat(maxThumbs) {
                val thumb = ImageView(this@following).apply {
                    layoutParams = LinearLayout.LayoutParams(60, 60)
                    setImageResource(R.drawable.profile_pic)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setPadding(0, 0, 4, 0)
                    background = ContextCompat.getDrawable(this@following, R.drawable.edittext_bg)
                }
                thumbnailLayout.addView(thumb)
            }
            contentLayout.addView(thumbnailLayout)
        }

        row.addView(profilePic)
        row.addView(contentLayout)
        return row
    }

    private fun loadFollowersData(uid: String, layout: LinearLayout) {
        db.getReference("followers").child(uid).get().addOnSuccessListener { snap ->
            layout.removeAllViews()
            if (!snap.exists()) {
                val tv = TextView(this@following)
                tv.text = "No followers yet."
                tv.setTextColor(ContextCompat.getColor(this@following, R.color.grey))
                tv.textSize = 15f
                tv.setPadding(16, 32, 16, 32)
                layout.addView(tv)
                return@addOnSuccessListener
            }

            for (child in snap.children) {
                val userId = child.key ?: continue
                db.getReference("users").child(userId).get().addOnSuccessListener { userSnap ->
                    val name = userSnap.child("fullName").value?.toString() ?: "Unknown"
                    val username = userSnap.child("username").value?.toString() ?: "user"
                    val row = this@following.layoutInflater.inflate(R.layout.item_follower_row, layout, false)

                    val tvName = row.findViewById<TextView>(R.id.tvName)
                    val tvUsername = row.findViewById<TextView>(R.id.tvUsername)
                    val btnFollow = row.findViewById<Button>(R.id.btnFollow)
                    val imgProfile = row.findViewById<ImageView>(R.id.imgProfile)

                    tvName.text = name
                    tvUsername.text = "@$username"
                    imgProfile.setOnClickListener {
                        startActivity(Intent(this@following, profile::class.java).putExtra("uid", userId))
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
                val layout = this@following.findViewById<LinearLayout>(R.id.followingContainer)
                loadFollowingData(currentUid, layout)
            } else {
                // All accounts are private - cannot follow directly, must send request
                Toast.makeText(this@following, "Please send a follow request first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFollowUI(button: Button, isFollowing: Boolean) {
        if (isFollowing) {
            button.text = "Following"
            button.setBackgroundResource(R.drawable.btn_following_border)
            button.setTextColor(ContextCompat.getColor(this@following, R.color.brick))
        } else {
            button.text = "Follow"
            button.setBackgroundColor(ContextCompat.getColor(this@following, R.color.brick))
            button.setTextColor(Color.WHITE)
        }
    }
}

// Keep data class at top level
data class ActivityItem(
    val username: String,
    val action: String,
    val timestamp: String,
    val thumbnails: List<String>
)

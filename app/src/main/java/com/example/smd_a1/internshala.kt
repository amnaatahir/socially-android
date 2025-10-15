package com.example.smd_a1

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class internshala : AppCompatActivity() {

    private lateinit var etQuery: EditText
    private lateinit var rvUsers: RecyclerView
    private lateinit var adapter: UsersAdapter

    private lateinit var tabTop: TextView
    private lateinit var tabAccounts: TextView
    private lateinit var tabTags: TextView
    private lateinit var tabPlaces: TextView

    private val allUsers = mutableListOf<UserProfile>()
    private val showing = mutableListOf<UserProfile>()
    private val followingSet = mutableSetOf<String>()
    private val followersSet = mutableSetOf<String>()

    private var activeTab: Tab = Tab.TOP
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private val myUid by lazy { FirebaseAuth.getInstance().currentUser?.uid }

    enum class Tab { TOP, ACCOUNTS, TAGS, PLACES }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_internshala)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etQuery = findViewById(R.id.etQuery)
        rvUsers = findViewById(R.id.rvUsers)
        tabTop = findViewById(R.id.tabTop)
        tabAccounts = findViewById(R.id.tabAccounts)
        tabTags = findViewById(R.id.tabTags)
        tabPlaces = findViewById(R.id.tabPlaces)

        rvUsers.layoutManager = LinearLayoutManager(this)
        adapter = UsersAdapter(showing)
        rvUsers.adapter = adapter

        // Load following/followers sets first
        loadFollowMaps {
            loadAllUsers()
        }

        // Tabs - set active tab and update UI
        tabTop.setOnClickListener { setActiveTab(Tab.TOP) }
        tabAccounts.setOnClickListener { setActiveTab(Tab.ACCOUNTS) }
        tabTags.setOnClickListener { setActiveTab(Tab.TAGS) }
        tabPlaces.setOnClickListener { setActiveTab(Tab.PLACES) }
        
        // Set initial active tab
        setActiveTab(Tab.TOP)

        // Search - clear button and text watcher
        val btnClear = findViewById<ImageView>(R.id.btnClear1)
        btnClear.setOnClickListener {
            etQuery.setText("")
            etQuery.clearFocus()
        }
        etQuery.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { 
                btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                filterAndShow() 
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        // Bottom navigation
        findViewById<ImageButton>(R.id.navHome)?.setOnClickListener {
            startActivity(Intent(this, feed::class.java))
            finish()
        }
        findViewById<ImageButton>(R.id.navSearch)?.setOnClickListener {
            // Already on search screen, do nothing or refresh
            filterAndShow()
        }
        findViewById<ImageButton>(R.id.navAdd)?.setOnClickListener {
            startActivity(Intent(this, gallery::class.java))
            finish()
        }
        findViewById<ImageButton>(R.id.navHeart)?.setOnClickListener {
            startActivity(Intent(this, followingyou::class.java))
            finish()
        }
        findViewById<ImageButton>(R.id.navProfile)?.setOnClickListener {
            val intent = Intent(this, instaprof::class.java)
            intent.putExtra("uid", myUid)
            startActivity(intent)
            finish()
        }
    }

    private fun loadFollowMaps(done: () -> Unit) {
        val uid = myUid ?: return done()
        db.child("following").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    followingSet.clear()
                    s.children.forEach {
                        if (it.getValue(Boolean::class.java) == true) it.key?.let(followingSet::add)
                    }
                    db.child("followers").child(uid)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(s2: DataSnapshot) {
                                followersSet.clear()
                                s2.children.forEach {
                                    if (it.getValue(Boolean::class.java) == true) it.key?.let(followersSet::add)
                                }
                                done()
                            }
                            override fun onCancelled(error: DatabaseError) { done() }
                        })
                }
                override fun onCancelled(error: DatabaseError) { done() }
            })
    }

    private var usersListener: ValueEventListener? = null
    
    private fun loadAllUsers() {
        // Remove old listener if exists
        usersListener?.let { db.child("users").removeEventListener(it) }
        
        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allUsers.clear()
                snapshot.children.forEach { c ->
                    c.getValue(UserProfile::class.java)?.let { allUsers += it }
                }
                filterAndShow()
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("Internshala", "Failed to load users: ${error.message}")
                android.widget.Toast.makeText(this@internshala, "Failed to load users", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        db.child("users").addValueEventListener(usersListener!!)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        usersListener?.let { 
            db.child("users").removeEventListener(it)
        }
    }

    private fun setActiveTab(tab: Tab) {
        activeTab = tab
        tabTop.setTextColor(getColor(if (tab == Tab.TOP) R.color.black else R.color.grey))
        tabAccounts.setTextColor(getColor(if (tab == Tab.ACCOUNTS) R.color.black else R.color.grey))
        tabTags.setTextColor(getColor(if (tab == Tab.TAGS) R.color.black else R.color.grey))
        tabPlaces.setTextColor(getColor(if (tab == Tab.PLACES) R.color.black else R.color.grey))
        filterAndShow()
    }

    private fun filterAndShow() {
        val q = etQuery.text.toString().trim().lowercase()

        val filtered = allUsers.asSequence()
            .filter { it.uid != myUid }
            .filter {
                // Only filter by search query if it's not empty and we're on Accounts tab
                when (activeTab) {
                    Tab.ACCOUNTS -> q.isEmpty() || it.username.lowercase().contains(q) || 
                                   "${it.firstName} ${it.lastName}".lowercase().contains(q)
                    Tab.TOP -> q.isEmpty() || it.username.lowercase().contains(q) || 
                              "${it.firstName} ${it.lastName}".lowercase().contains(q)
                    Tab.TAGS, Tab.PLACES -> false // For now, hide results for Tags/Places (can be implemented later)
                }
            }
            .toList()

        showing.clear()
        showing.addAll(filtered)
        adapter.notifyDataSetChanged()
    }
}

// internshala.kt  (only the UsersAdapter.onBindViewHolder changed)
private class UsersAdapter(
    private val items: List<UserProfile>
) : RecyclerView.Adapter<UsersAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val ivAvatar: ImageView = v.findViewById(R.id.ivAvatar)
        val tvUsername: TextView = v.findViewById(R.id.tvUsername)
        val tvDisplayName: TextView = v.findViewById(R.id.tvDisplayName)
        val btnFollow: Button = v.findViewById(R.id.btnFollow)
    }

    override fun onCreateViewHolder(p: ViewGroup, v: Int): VH {
        val view = LayoutInflater.from(p.context).inflate(R.layout.item_user_row, p, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val u = items[pos]
        h.tvUsername.text = u.username
        h.tvDisplayName.text = if (u.firstName.isNotEmpty() || u.lastName.isNotEmpty()) {
            "${u.firstName} ${u.lastName}".trim()
        } else {
            u.username
        }
        // Load profile picture from Firebase
        FirebaseDatabase.getInstance().getReference("users").child(u.uid)
            .get().addOnSuccessListener { snap ->
                val photoUrl = snap.child("photoUrl").getValue(String::class.java)
                val photoBase64 = snap.child("photoBase64").getValue(String::class.java)
                val updatedAt = snap.child("photoUpdatedAt").getValue(Long::class.java)
                ImageUtils.loadProfilePictureUrlOrBase64(h.ivAvatar, photoUrl, photoBase64, R.drawable.oval, updatedAt)
            }.addOnFailureListener {
                ImageUtils.loadProfilePicture(h.ivAvatar, null, R.drawable.oval)
            }

        // FOLLOW BUTTON CLICK - Always send follow request (all accounts are private)
        h.btnFollow.setOnClickListener { btnView ->
            btnView as Button
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            
            if (currentUid == u.uid) {
                Toast.makeText(btnView.context, "You can't follow yourself", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Check if already following or request already sent
            FirebaseDatabase.getInstance().getReference("following").child(currentUid).child(u.uid).get()
                .addOnSuccessListener { followingSnap ->
                    if (followingSnap.exists()) {
                        Toast.makeText(btnView.context, "You are already following this user", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                    
                    // Check if request already exists
                    FirebaseDatabase.getInstance().getReference("followRequests").child(u.uid).child(currentUid).get()
                        .addOnSuccessListener { requestSnap ->
                            if (requestSnap.exists()) {
                                Toast.makeText(btnView.context, "Follow request already sent", Toast.LENGTH_SHORT).show()
                                btnView.text = "Requested"
                                btnView.isEnabled = false
                                return@addOnSuccessListener
                            }
                            
                            // Send new follow request
                            val followRequest = mapOf(
                                "fromUid" to currentUid,
                                "toUid" to u.uid,
                                "timestamp" to System.currentTimeMillis(),
                                "status" to "pending"
                            )
                            
                            FirebaseDatabase.getInstance().getReference("followRequests").child(u.uid).child(currentUid).setValue(followRequest)
                                .addOnSuccessListener {
                                    Toast.makeText(btnView.context, "Follow request sent!", Toast.LENGTH_SHORT).show()
                                    btnView.text = "Requested"
                                    btnView.isEnabled = false
                                    
                                    // Create notification for the target user
                                    createFollowRequestNotification(btnView.context, u.uid, currentUid)
                                }
                                .addOnFailureListener { error ->
                                    Toast.makeText(btnView.context, "Failed to send follow request: ${error.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                }
        }

        // OPEN PROFILE ON CLICK - open instaprof (main profile screen)
        h.itemView.setOnClickListener {
            val ctx = it.context
            val i = Intent(ctx, instaprof::class.java)
            i.putExtra("uid", u.uid)   // Use "uid" to match instaprof's expected parameter
            ctx.startActivity(i)
        }
    }
    
    // Helper function to create follow request notification
    private fun createFollowRequestNotification(context: android.content.Context, targetUid: String, fromUid: String) {
        // Get current user's info
        FirebaseDatabase.getInstance().getReference("users").child(fromUid).get().addOnSuccessListener { userSnap ->
            val username = userSnap.child("username").value?.toString() ?: "Unknown"
            val fullName = userSnap.child("fullName").value?.toString() ?: "Unknown"
            
            // Create notification using RealNotificationManager for Firebase storage
            val notificationManager = RealNotificationManager(context)
            val notificationId = FirebaseDatabase.getInstance().reference.child("notifications").push().key
            if (notificationId == null) {
                android.util.Log.e("Internshala", "Failed to generate notification ID")
                return@addOnSuccessListener
            }
            
            // Save notification to Firebase - use map with string type for proper display
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
            FirebaseDatabase.getInstance().getReference("notifications").child(targetUid).child(notificationId).setValue(notificationData)
                .addOnSuccessListener {
                    android.util.Log.d("Internshala", "Follow request notification created for $targetUid")
                }
                .addOnFailureListener { error ->
                    android.util.Log.e("Internshala", "Failed to create follow request notification: ${error.message}")
                }
            
            // Also send push notification using NotificationManager
            try {
                val fcmNotificationManager = NotificationManager()
                fcmNotificationManager.sendFollowRequestNotification(
                    receiverId = targetUid,
                    requesterName = fullName.ifEmpty { username }
                )
            } catch (e: Exception) {
                android.util.Log.e("Internshala", "Failed to send push notification: ${e.message}")
            }
        }
    }
}


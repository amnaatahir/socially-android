package com.example.smd_a1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class jacob_wSearch : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: UserAdapterDM
    private val list = mutableListOf<UserProfile>()

    private lateinit var auth: FirebaseAuth
    private lateinit var dbUsers: DatabaseReference
    private lateinit var dbChats: DatabaseReference
    private var currentUid: String? = null
    private var usersListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_jacob_wsearch)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        val dbRoot = FirebaseDatabase.getInstance()
        dbUsers = dbRoot.getReference("users")
        dbChats = dbRoot.getReference("chats")
        currentUid = auth.currentUser?.uid

        // Load current user's username from Firebase and update title
        loadCurrentUserUsername()

        // GUI
        findViewById<ImageButton>(R.id.btnback).setOnClickListener {
            startActivity(Intent(this, feed::class.java))
        }

        rv = findViewById(R.id.rvDmUsers)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = UserAdapterDM(list) { user ->
            // onClick: open chat activity — pass the user's uid or username
            val intent = Intent(this, chat::class.java)
            intent.putExtra("peerUid", user.uid)
            startActivity(intent)
        }
        rv.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        // set current user online
        setUserStatus("online")
        attachUsersListener()
    }

    override fun onStop() {
        super.onStop()
        // set offline when leaving activity
        setUserStatus("offline")
        detachUsersListener()
    }

    private fun setUserStatus(status: String) {
        val uid = auth.currentUser?.uid ?: return
        dbUsers.child(uid).child("status").setValue(status)
            .addOnSuccessListener { Log.d("dm", "status set to $status") }
            .addOnFailureListener { Log.w("dm", "failed set status", it) }
    }

    private fun attachUsersListener() {
        // Load only users who are in an active chat with the current user
        val me = currentUid ?: return
        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val uniqueUsers = LinkedHashMap<String, UserProfile>()
                for (chatSnap in snapshot.children) {
                    val participants = chatSnap.child("participants").children.mapNotNull { it.getValue(String::class.java) }
                    if (participants.contains(me) && participants.size >= 2) {
                        val otherId = participants.firstOrNull { it != me } ?: continue
                        if (!uniqueUsers.containsKey(otherId)) {
                            // fetch user profile
                            dbUsers.child(otherId).get().addOnSuccessListener { userSnap ->
                                val up = userSnap.getValue(UserProfile::class.java)
                                if (up != null) {
                                    uniqueUsers[otherId] = up.copy(uid = otherId)
                                    adapter.update(uniqueUsers.values.toList())
                                }
                            }
                        }
                    }
                }
                // If no chats, show empty
                if (uniqueUsers.isEmpty()) adapter.update(emptyList())
            }

            override fun onCancelled(error: DatabaseError) { }
        }
        dbChats.addValueEventListener(usersListener!!)
    }

    private fun detachUsersListener() {
        usersListener?.let { dbChats.removeEventListener(it) }
        usersListener = null
    }
    
    private fun loadCurrentUserUsername() {
        currentUid?.let { uid ->
            dbUsers.child(uid).child("username")
                .get()
                .addOnSuccessListener { snapshot ->
                    val username = snapshot.value?.toString() ?: "User"
                    findViewById<TextView>(R.id.titleUser)?.setText(username)
                }
        }
    }
}
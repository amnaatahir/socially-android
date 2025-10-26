package com.example.smd_a1

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

class PresenceManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()
    private val presenceRef = db.getReference("presence")
    private val usersRef = db.getReference("users")
    private val infoConnectedRef = db.getReference(".info/connected")

    fun start() {
        val uid = auth.currentUser?.uid ?: return
        infoConnectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) == true
                if (!connected) return
                val userPresenceRef = presenceRef.child(uid)
                val now = ServerValue.TIMESTAMP
                userPresenceRef.onDisconnect().setValue(mapOf(
                    "isOnline" to false,
                    "lastSeen" to now
                ))
                userPresenceRef.setValue(mapOf(
                    "isOnline" to true,
                    "lastSeen" to now
                ))
                usersRef.child(uid).child("status").onDisconnect().setValue("offline")
                usersRef.child(uid).child("status").setValue("online")
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w("Presence", "info/connected cancelled: ${error.message}")
            }
        })
    }

    fun setOnline() {
        val uid = auth.currentUser?.uid ?: return
        val now = ServerValue.TIMESTAMP
        presenceRef.child(uid).updateChildren(mapOf(
            "isOnline" to true,
            "lastSeen" to now
        ))
        usersRef.child(uid).child("status").setValue("online")
    }

    fun setOffline() {
        val uid = auth.currentUser?.uid ?: return
        val now = ServerValue.TIMESTAMP
        presenceRef.child(uid).updateChildren(mapOf(
            "isOnline" to false,
            "lastSeen" to now
        ))
        usersRef.child(uid).child("status").setValue("offline")
    }
}



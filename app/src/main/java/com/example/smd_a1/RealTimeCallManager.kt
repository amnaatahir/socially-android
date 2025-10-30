package com.example.smd_a1

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Real-time calling system with Firebase integration
 */
class RealTimeCallManager(private val context: Context) {
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val callsRef = database.getReference("calls")
    private val usersRef = database.getReference("users")
    
    companion object {
        const val STATUS_RINGING = "ringing"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DECLINED = "declined"
        const val STATUS_ENDED = "ended"
        const val STATUS_MISSED = "missed"
        
        const val TYPE_VIDEO = "video"
        const val TYPE_AUDIO = "audio"
    }
    
    /**
     * Initiate a call
     */
    fun initiateCall(
        receiverId: String,
        callType: String = TYPE_VIDEO,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUserId = auth.currentUser?.uid ?: return
        val callId = callsRef.push().key ?: return
        
        // Get user data
        usersRef.child(currentUserId).get().addOnSuccessListener { userSnapshot ->
            val username = userSnapshot.child("username").value?.toString() ?: "user"
            val fullName = userSnapshot.child("fullName").value?.toString() ?: "User"
            
            val call = RealCall(
                callId = callId,
                callerId = currentUserId,
                callerUsername = username,
                callerFullName = fullName,
                receiverId = receiverId,
                callType = callType,
                status = STATUS_RINGING,
                startTime = System.currentTimeMillis(),
                endTime = 0L,
                duration = 0L,
                channelName = generateChannelName(currentUserId, receiverId)
            )
            
            // Save call to Firebase
            callsRef.child(callId).setValue(call)
                .addOnSuccessListener {
                    // Create call notification
                    createCallNotification(receiverId, username, callType, callId)
                    
                    Log.d("CallManager", "Call initiated: $callId")
                    onSuccess(callId)
                }
                .addOnFailureListener { error ->
                    Log.e("CallManager", "Failed to initiate call: ${error.message}")
                    onFailure(error.message ?: "Failed to initiate call")
                }
        }
    }
    
    /**
     * Accept a call
     */
    fun acceptCall(
        callId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val updates = mapOf(
            "status" to STATUS_ACCEPTED,
            "acceptedAt" to System.currentTimeMillis()
        )
        
        callsRef.child(callId).updateChildren(updates)
            .addOnSuccessListener {
                Log.d("CallManager", "Call accepted: $callId")
                onSuccess()
            }
            .addOnFailureListener { error ->
                Log.e("CallManager", "Failed to accept call: ${error.message}")
                onFailure(error.message ?: "Failed to accept call")
            }
    }
    
    /**
     * Decline a call
     */
    fun declineCall(
        callId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val updates = mapOf(
            "status" to STATUS_DECLINED,
            "endTime" to System.currentTimeMillis()
        )
        
        callsRef.child(callId).updateChildren(updates)
            .addOnSuccessListener {
                Log.d("CallManager", "Call declined: $callId")
                onSuccess()
            }
            .addOnFailureListener { error ->
                Log.e("CallManager", "Failed to decline call: ${error.message}")
                onFailure(error.message ?: "Failed to decline call")
            }
    }
    
    /**
     * End a call
     */
    fun endCall(
        callId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        callsRef.child(callId).get().addOnSuccessListener { snapshot ->
            val call = snapshot.getValue(RealCall::class.java)
            if (call != null) {
                val endTime = System.currentTimeMillis()
                val duration = endTime - call.startTime
                
                val updates = mapOf(
                    "status" to STATUS_ENDED,
                    "endTime" to endTime,
                    "duration" to duration
                )
                
                callsRef.child(callId).updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d("CallManager", "Call ended: $callId, duration: ${duration}ms")
                        onSuccess()
                    }
                    .addOnFailureListener { error ->
                        Log.e("CallManager", "Failed to end call: ${error.message}")
                        onFailure(error.message ?: "Failed to end call")
                    }
            }
        }
    }
    
    /**
     * Listen for incoming calls
     */
    fun listenForIncomingCalls(
        userId: String,
        onCallReceived: (RealCall) -> Unit,
        onCallStatusChanged: (String, String) -> Unit
    ) {
        callsRef.orderByChild("receiverId").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.forEach { child ->
                        val call = child.getValue(RealCall::class.java)
                        if (call != null && call.status == STATUS_RINGING) {
                            onCallReceived(call)
                        }
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e("CallManager", "Failed to listen for calls: ${error.message}")
                }
            })
        
        // Listen for call status changes
        callsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { child ->
                    val call = child.getValue(RealCall::class.java)
                    if (call != null && 
                        (call.callerId == userId || call.receiverId == userId)) {
                        onCallStatusChanged(call.callId, call.status)
                    }
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("CallManager", "Failed to listen for call status: ${error.message}")
            }
        })
    }
    
    /**
     * Get call history for user
     */
    fun getCallHistory(
        userId: String,
        onCallReceived: (RealCall) -> Unit,
        onError: (String) -> Unit
    ) {
        callsRef.orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val calls = mutableListOf<RealCall>()
                    
                    snapshot.children.forEach { child ->
                        val call = child.getValue(RealCall::class.java)
                        if (call != null && 
                            (call.callerId == userId || call.receiverId == userId)) {
                            calls.add(call)
                        }
                    }
                    
                    // Sort by start time (newest first)
                    calls.sortByDescending { it.startTime }
                    
                    calls.forEach { call ->
                        onCallReceived(call)
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }
    
    /**
     * Generate unique channel name for Agora
     */
    private fun generateChannelName(userId1: String, userId2: String): String {
        return "call_${listOf(userId1, userId2).sorted().joinToString("_")}"
    }
    
    /**
     * Create call notification
     */
    private fun createCallNotification(
        receiverId: String,
        callerUsername: String,
        callType: String,
        callId: String
    ) {
        val notificationId = database.getReference("notifications").push().key ?: return
        
        val callTypeText = if (callType == TYPE_VIDEO) "video call" else "voice call"
        
        val notification = mapOf(
            "notificationId" to notificationId,
            "fromUsername" to callerUsername,
            "action" to "is calling you ($callTypeText)",
            "type" to "call",
            "callId" to callId,
            "callType" to callType,
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )
        
        database.getReference("notifications").child(receiverId).child(notificationId).setValue(notification)
    }
}

/**
 * Real call data class
 */
data class RealCall(
    val callId: String = "",
    val callerId: String = "",
    val callerUsername: String = "",
    val callerFullName: String = "",
    val receiverId: String = "",
    val callType: String = "video",
    val status: String = "ringing",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = 0L,
    val duration: Long = 0L,
    val channelName: String = "",
    val acceptedAt: Long = 0L
)

/**
 * Format call duration
 */
fun formatCallDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
        minutes > 0 -> String.format("%02d:%02d", minutes, seconds % 60)
        else -> String.format("00:%02d", seconds)
    }
}

/**
 * Format call time
 */
fun formatCallTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60 * 1000 -> "now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
        else -> {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

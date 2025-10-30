package com.example.smd_a1

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*

class CallManager {
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val callsRef = database.getReference("calls")
    
    companion object {
        private const val TAG = "CallManager"
        
        // Call status constants
        const val STATUS_RINGING = "ringing"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DECLINED = "declined"
        const val STATUS_ENDED = "ended"
        const val STATUS_MISSED = "missed"
    }
    
    interface CallListener {
        fun onIncomingCall(callId: String, callerId: String, callerName: String)
        fun onCallAccepted(callId: String)
        fun onCallDeclined(callId: String)
        fun onCallEnded(callId: String)
        fun onCallMissed(callId: String)
    }
    
    private var callListener: CallListener? = null
    private var callStatusListener: ValueEventListener? = null
    
    fun setCallListener(listener: CallListener) {
        this.callListener = listener
    }
    
    /**
     * Initiate a call to another user
     */
    fun initiateCall(receiverId: String, receiverName: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        val currentUser = auth.currentUser ?: return
        
        Log.d(TAG, "=== INITIATING CALL ===")
        Log.d(TAG, "Caller: ${currentUser.uid} (${currentUser.displayName ?: "No name"})")
        Log.d(TAG, "Receiver: $receiverId ($receiverName)")
        
        val callId = callsRef.push().key ?: return
        val channelName = generateChannelName(currentUser.uid, receiverId)
        
        Log.d(TAG, "Call ID: $callId")
        Log.d(TAG, "Channel Name: $channelName")
        
        val callData = hashMapOf<String, Any>(
            "callId" to callId,
            "callerId" to currentUser.uid,
            "callerName" to (currentUser.displayName ?: "User"),
            "receiverId" to receiverId,
            "receiverName" to receiverName,
            "channelName" to channelName,
            "status" to STATUS_RINGING,
            "timestamp" to System.currentTimeMillis(),
            "callType" to "video"
        )
        
        Log.d(TAG, "Call data: $callData")
        Log.d(TAG, "Saving to Firebase...")
        
        callsRef.child(callId).setValue(callData)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Call initiated successfully: $callId")
                
                // Send call notification to receiver
                val notificationManager = NotificationManager()
                notificationManager.sendCallNotification(
                    receiverId = receiverId,
                    callerName = currentUser.displayName ?: "User",
                    callId = callId,
                    callType = "video"
                )
                
                onSuccess(callId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to initiate call: ${e.message}")
                onFailure(e.message ?: "Failed to initiate call")
            }
    }
    
    /**
     * Accept an incoming call
     */
    fun acceptCall(callId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        callsRef.child(callId).child("status").setValue(STATUS_ACCEPTED)
            .addOnSuccessListener {
                Log.d(TAG, "Call accepted: $callId")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to accept call", e)
                onFailure(e.message ?: "Failed to accept call")
            }
    }
    
    /**
     * Decline an incoming call
     */
    fun declineCall(callId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        callsRef.child(callId).child("status").setValue(STATUS_DECLINED)
            .addOnSuccessListener {
                Log.d(TAG, "Call declined: $callId")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to decline call", e)
                onFailure(e.message ?: "Failed to decline call")
            }
    }
    
    /**
     * End a call
     */
    fun endCall(callId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        callsRef.child(callId).child("status").setValue(STATUS_ENDED)
            .addOnSuccessListener {
                Log.d(TAG, "Call ended: $callId")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to end call", e)
                onFailure(e.message ?: "Failed to end call")
            }
    }
    
    /**
     * Listen for incoming calls
     */
    fun startListeningForCalls() {
        val currentUser = auth.currentUser ?: return
        
        Log.d(TAG, "Starting to listen for calls for user: ${currentUser.uid}")
        
        // Listen for calls where current user is the receiver
        val query = callsRef.orderByChild("receiverId").equalTo(currentUser.uid)
        
        callStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "Firebase data changed, snapshot exists: ${snapshot.exists()}")
                Log.d(TAG, "Number of children: ${snapshot.childrenCount}")
                
                for (callSnapshot in snapshot.children) {
                    val callId = callSnapshot.key ?: continue
                    val status = callSnapshot.child("status").value?.toString() ?: continue
                    val callerId = callSnapshot.child("callerId").value?.toString() ?: continue
                    val callerName = callSnapshot.child("callerName").value?.toString() ?: "Unknown"
                    
                    Log.d(TAG, "Processing call: $callId, status: $status, caller: $callerName")
                    
                    when (status) {
                        STATUS_RINGING -> {
                            Log.d(TAG, "Incoming call: $callId from $callerName")
                            callListener?.onIncomingCall(callId, callerId, callerName)
                        }
                        STATUS_ACCEPTED -> {
                            Log.d(TAG, "Call accepted: $callId")
                            callListener?.onCallAccepted(callId)
                        }
                        STATUS_DECLINED -> {
                            Log.d(TAG, "Call declined: $callId")
                            callListener?.onCallDeclined(callId)
                        }
                        STATUS_ENDED -> {
                            Log.d(TAG, "Call ended: $callId")
                            callListener?.onCallEnded(callId)
                        }
                    }
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to listen for calls", error.toException())
            }
        }
        
        query.addValueEventListener(callStatusListener!!)
        Log.d(TAG, "Firebase listener added successfully")
    }
    
    /**
     * Listen for call status changes (for the caller)
     */
    fun listenForCallStatus(callId: String, onStatusChange: (String) -> Unit) {
        callsRef.child(callId).child("status").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.value?.toString() ?: return
                Log.d(TAG, "Call status changed: $status for call $callId")
                onStatusChange(status)
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to listen for call status", error.toException())
            }
        })
    }
    
    /**
     * Stop listening for calls
     */
    fun stopListeningForCalls() {
        callStatusListener?.let { callsRef.removeEventListener(it) }
        callStatusListener = null
    }
    
    /**
     * Get call details
     */
    fun getCallDetails(callId: String, onSuccess: (CallData) -> Unit, onFailure: (String) -> Unit) {
        callsRef.child(callId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val callData = CallData(
                        callId = snapshot.child("callId").value?.toString() ?: "",
                        callerId = snapshot.child("callerId").value?.toString() ?: "",
                        callerName = snapshot.child("callerName").value?.toString() ?: "",
                        receiverId = snapshot.child("receiverId").value?.toString() ?: "",
                        receiverName = snapshot.child("receiverName").value?.toString() ?: "",
                        channelName = snapshot.child("channelName").value?.toString() ?: "",
                        status = snapshot.child("status").value?.toString() ?: "",
                        timestamp = snapshot.child("timestamp").value as? Long ?: 0L,
                        callType = snapshot.child("callType").value?.toString() ?: "video"
                    )
                    onSuccess(callData)
                } else {
                    onFailure("Call not found")
                }
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Failed to get call details")
            }
    }
    
    private fun generateChannelName(userId1: String, userId2: String): String {
        return listOf(userId1, userId2).sorted().joinToString("_")
    }
    
    /**
     * Clean up old calls (optional - for database maintenance)
     */
    fun cleanupOldCalls() {
        val cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // 24 hours ago
        
        callsRef.orderByChild("timestamp").endAt(cutoffTime.toDouble())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (callSnapshot in snapshot.children) {
                        callSnapshot.ref.removeValue()
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to cleanup old calls", error.toException())
                }
            })
    }
}

data class CallData(
    val callId: String,
    val callerId: String,
    val callerName: String,
    val receiverId: String,
    val receiverName: String,
    val channelName: String,
    val status: String,
    val timestamp: Long,
    val callType: String
)

package com.example.smd_a1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class CallService : Service() {
    
    private lateinit var callManager: CallManager
    private val auth = FirebaseAuth.getInstance()
    private val callsRef = FirebaseDatabase.getInstance().getReference("calls")
    
    companion object {
        private const val TAG = "CallService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "call_service_channel"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CallService created")
        
        callManager = CallManager()
        callManager.setCallListener(object : CallManager.CallListener {
            override fun onIncomingCall(callId: String, callerId: String, callerName: String) {
                Log.d(TAG, "Incoming call: $callId from $callerName")
                showIncomingCallNotification(callId, callerId, callerName)
            }
            
            override fun onCallAccepted(callId: String) {
                Log.d(TAG, "Call accepted: $callId")
                hideNotification()
            }
            
            override fun onCallDeclined(callId: String) {
                Log.d(TAG, "Call declined: $callId")
                hideNotification()
            }
            
            override fun onCallEnded(callId: String) {
                Log.d(TAG, "Call ended: $callId")
                hideNotification()
            }
            
            override fun onCallMissed(callId: String) {
                Log.d(TAG, "Call missed: $callId")
                hideNotification()
            }
        })
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createServiceNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "CallService started")
        
        // Start listening for incoming calls directly
        startListeningForIncomingCalls()
        
        // Return START_STICKY to restart service if killed
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    private fun startListeningForIncomingCalls() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found!")
            return
        }
        
        Log.d(TAG, "Starting direct Firebase listener for user: ${currentUser.uid}")
        Log.d(TAG, "Firebase database URL: ${FirebaseDatabase.getInstance().reference}")
        
        // Listen for calls where current user is the receiver
        val query = callsRef.orderByChild("receiverId").equalTo(currentUser.uid)
        
        query.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "=== Firebase Data Changed ===")
                Log.d(TAG, "Snapshot exists: ${snapshot.exists()}")
                Log.d(TAG, "Number of children: ${snapshot.childrenCount}")
                Log.d(TAG, "Snapshot value: ${snapshot.value}")
                
                if (!snapshot.exists()) {
                    Log.d(TAG, "No calls found for user ${currentUser.uid}")
                    return
                }
                
                for (callSnapshot in snapshot.children) {
                    val callId = callSnapshot.key ?: continue
                    val status = callSnapshot.child("status").value?.toString() ?: continue
                    val callerId = callSnapshot.child("callerId").value?.toString() ?: continue
                    val callerName = callSnapshot.child("callerName").value?.toString() ?: "Unknown"
                    val receiverId = callSnapshot.child("receiverId").value?.toString() ?: ""
                    
                    Log.d(TAG, "Processing call: $callId")
                    Log.d(TAG, "  Status: $status")
                    Log.d(TAG, "  Caller: $callerName ($callerId)")
                    Log.d(TAG, "  Receiver: $receiverId")
                    
                    if (status == CallManager.STATUS_RINGING && receiverId == currentUser.uid) {
                        Log.d(TAG, "🎉 INCOMING CALL DETECTED: $callId from $callerName")
                        showIncomingCallNotification(callId, callerId, callerName)
                    }
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase listener cancelled: ${error.message}")
                Log.e(TAG, "Error details: ${error.details}")
            }
        })
        
        Log.d(TAG, "Firebase listener added successfully")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CallService destroyed")
        callManager.stopListeningForCalls()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Service for handling incoming calls"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createServiceNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Call Service Active")
            .setContentText("Listening for incoming calls")
            .setSmallIcon(R.drawable.mic_on)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
    
    private fun showIncomingCallNotification(callId: String, callerId: String, callerName: String) {
        val acceptIntent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("callId", callId)
            putExtra("callerId", callerId)
            putExtra("callerName", callerName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val acceptPendingIntent = PendingIntent.getActivity(
            this, 0, acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val declineIntent = Intent(this, CallService::class.java).apply {
            action = "DECLINE_CALL"
            putExtra("callId", callId)
        }
        
        val declinePendingIntent = PendingIntent.getService(
            this, 1, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Incoming Call")
            .setContentText("$callerName is calling you")
            .setSmallIcon(R.drawable.mic_on)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(acceptPendingIntent, true)
            .addAction(R.drawable.mic_on, "Accept", acceptPendingIntent)
            .addAction(R.drawable.endcall, "Decline", declinePendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
    
    private fun hideNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID + 1)
    }
}

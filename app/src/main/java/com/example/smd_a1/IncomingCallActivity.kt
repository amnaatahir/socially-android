package com.example.smd_a1

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class IncomingCallActivity : AppCompatActivity() {
    
    private lateinit var callManager: CallManager
    private var callId: String = ""
    private var callerName: String = ""
    private var callerId: String = ""
    
    private lateinit var tvCallerName: TextView
    private lateinit var btnAccept: ImageButton
    private lateinit var btnDecline: ImageButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_incoming_call)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Get call details from intent
        callId = intent.getStringExtra("callId") ?: ""
        callerName = intent.getStringExtra("callerName") ?: "Unknown"
        callerId = intent.getStringExtra("callerId") ?: ""
        
        initializeViews()
        setupClickListeners()
        
        callManager = CallManager()
        callManager.setCallListener(object : CallManager.CallListener {
            override fun onIncomingCall(callId: String, callerId: String, callerName: String) {
                // This won't be called here since we're already handling an incoming call
            }
            
            override fun onCallAccepted(callId: String) {
                // Call was accepted, start the call activity
                startCallActivity()
            }
            
            override fun onCallDeclined(callId: String) {
                // Call was declined, finish this activity
                Toast.makeText(this@IncomingCallActivity, "Call declined", Toast.LENGTH_SHORT).show()
                finish()
            }
            
            override fun onCallEnded(callId: String) {
                // Call was ended, finish this activity
                Toast.makeText(this@IncomingCallActivity, "Call ended", Toast.LENGTH_SHORT).show()
                finish()
            }
            
            override fun onCallMissed(callId: String) {
                // Call was missed, finish this activity
                Toast.makeText(this@IncomingCallActivity, "Call missed", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        
        // Listen for call status changes
        if (callId.isNotEmpty()) {
            callManager.listenForCallStatus(callId) { status ->
                when (status) {
                    CallManager.STATUS_ACCEPTED -> {
                        startCallActivity()
                    }
                    CallManager.STATUS_DECLINED, CallManager.STATUS_ENDED -> {
                        finish()
                    }
                }
            }
        }
    }
    
    private fun initializeViews() {
        tvCallerName = findViewById(R.id.tvCallerName)
        btnAccept = findViewById(R.id.btnAccept)
        btnDecline = findViewById(R.id.btnDecline)
        
        tvCallerName.text = callerName
    }
    
    private fun setupClickListeners() {
        btnAccept.setOnClickListener {
            acceptCall()
        }
        
        btnDecline.setOnClickListener {
            declineCall()
        }
    }
    
    private fun acceptCall() {
        callManager.acceptCall(callId,
            onSuccess = {
                Toast.makeText(this, "Call accepted", Toast.LENGTH_SHORT).show()
                startCallActivity()
            },
            onFailure = { error ->
                Toast.makeText(this, "Failed to accept call: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    private fun declineCall() {
        callManager.declineCall(callId,
            onSuccess = {
                Toast.makeText(this, "Call declined", Toast.LENGTH_SHORT).show()
                finish()
            },
            onFailure = { error ->
                Toast.makeText(this, "Failed to decline call: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    private fun startCallActivity() {
        val intent = Intent(this, calling::class.java)
        intent.putExtra("callId", callId)
        intent.putExtra("receiverId", callerId)
        intent.putExtra("receiverName", callerName)
        intent.putExtra("isIncomingCall", true)
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        callManager.stopListeningForCalls()
    }
}

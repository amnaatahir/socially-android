package com.example.smd_a1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler.RtcStats
import java.text.SimpleDateFormat
import java.util.*

class calling : AppCompatActivity() {
    
    private var rtcEngine: RtcEngine? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var callManager: CallManager
    
    // UI Components
    private lateinit var btnEndCall: ImageButton
    private lateinit var btnMute: ImageButton
    private lateinit var btnSpeaker: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var tvName: TextView
    private lateinit var tvDuration: TextView
    private lateinit var localVideoContainer: FrameLayout
    private lateinit var remoteVideoContainer: FrameLayout
    
    // Call state
    private var isMuted = false
    private var isSpeakerOn = false
    private var isVideoEnabled = true
    private var callStartTime: Long = 0
    private var callDurationHandler: Handler? = null
    private var callDurationRunnable: Runnable? = null
    private var isJoined = false
    
    // Agora configuration
    private val APP_ID = "c5d71278a0ab443686c38670d64d9ecb" // Your Agora App ID
    private var channelName: String = ""
    private var callId: String = ""
    private var receiverId: String = ""
    private var receiverName: String = ""
    private var isIncomingCall: Boolean = false
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
        private const val TAG = "CallingActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_calling)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        callManager = CallManager()
        
        // Get call details from intent
        callId = intent.getStringExtra("callId") ?: ""
        receiverId = intent.getStringExtra("receiverId") ?: ""
        receiverName = intent.getStringExtra("receiverName") ?: "User"
        isIncomingCall = intent.getBooleanExtra("isIncomingCall", false)
        
        // Generate channel name based on chat participants
        val currentUserId = auth.currentUser?.uid ?: ""
        channelName = generateChannelName(currentUserId, receiverId)
        
        Log.d(TAG, "Call details - Current User: $currentUserId, Receiver: $receiverId, Channel: $channelName")
        
        initializeViews()
        
        // Load receiver name from Firebase if not provided or if it's just "User"
        if (receiverId.isNotEmpty() && (receiverName == "User" || receiverName.isEmpty())) {
            loadReceiverInfo(receiverId)
        } else {
            // Name was provided, update UI
            updateReceiverName(receiverName)
        }
        
        setupClickListeners()
        
        // Listen for call status changes
        if (callId.isNotEmpty()) {
            callManager.listenForCallStatus(callId) { status ->
                when (status) {
                    CallManager.STATUS_DECLINED -> {
                        Toast.makeText(this, "Call declined", Toast.LENGTH_SHORT).show()
                        endCall()
                    }
                    CallManager.STATUS_ENDED -> {
                        Toast.makeText(this, "Call ended", Toast.LENGTH_SHORT).show()
                        endCall()
                    }
                }
            }
        }
        
        checkPermissionsAndInitializeAgora()
    }
    
    private fun initializeViews() {
        btnEndCall = findViewById(R.id.btnEndCall)
        btnSpeaker = findViewById(R.id.btnSpeaker)
        btnMute = findViewById(R.id.btnMute)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        tvName = findViewById(R.id.tvName)
        tvDuration = findViewById(R.id.tvDuration)
        localVideoContainer = findViewById(R.id.local_video_view_container)
        remoteVideoContainer = findViewById(R.id.remote_video_view_container)
        
        // Name will be set after loading from Firebase if needed
    }
    
    private fun loadReceiverInfo(receiverId: String) {
        FirebaseDatabase.getInstance().getReference("users").child(receiverId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val fullName = snapshot.child("fullName").value?.toString()
                    val username = snapshot.child("username").value?.toString()
                    val name = fullName ?: username ?: "User"
                    receiverName = name
                    updateReceiverName(name)
                    
                    // Also load profile picture if available
                    val photoUrl = snapshot.child("photoUrl").value?.toString()
                    if (!photoUrl.isNullOrEmpty()) {
                        // Could load profile picture here if needed
                        // For now, using default drawable
                    }
                } else {
                    updateReceiverName("User")
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to load receiver info: ${it.message}")
                updateReceiverName(receiverName)
            }
    }
    
    private fun updateReceiverName(name: String) {
        tvName.text = name
    }
    
    private fun setupClickListeners() {
        btnEndCall.setOnClickListener {
            endCall()
        }
        
        btnSpeaker.setOnClickListener {
            toggleSpeaker()
        }
        
        btnMute.setOnClickListener {
            toggleMute()
        }
        
        btnSwitchCamera.setOnClickListener {
            switchCamera()
        }
    }
    
    private fun checkPermissionsAndInitializeAgora() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            initializeAgoraEngine()
        }
    }
    
    private fun initializeAgoraEngine() {
        try {
            val config = RtcEngineConfig()
            config.mContext = this
            config.mAppId = APP_ID
            config.mEventHandler = mRtcEventHandler

            rtcEngine = RtcEngine.create(config)

            // Enable video and audio
            rtcEngine?.enableVideo()
            rtcEngine?.enableAudio()
            rtcEngine?.enableLocalVideo(true)
            rtcEngine?.enableLocalAudio(true)

            // Set video profile for better quality
            rtcEngine?.setVideoEncoderConfiguration(io.agora.rtc2.video.VideoEncoderConfiguration(
                io.agora.rtc2.video.VideoEncoderConfiguration.VD_1280x720,
                io.agora.rtc2.video.VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                io.agora.rtc2.video.VideoEncoderConfiguration.STANDARD_BITRATE,
                io.agora.rtc2.video.VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
            ))

            // Setup local video
            setupLocalVideo()

            // Join channel
            val token = null // For testing, use null. In production, use proper token
            val result = rtcEngine?.joinChannel(token, channelName, 0, null)
            
            Log.d(TAG, "Agora engine initialized successfully, join result: $result")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Agora engine", e)
            Toast.makeText(this, "Failed to initialize call: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupLocalVideo() {
        Log.d(TAG, "Setting up local video")
        
        // Clear any existing local video
        localVideoContainer.removeAllViews()
        
        val surfaceView = SurfaceView(baseContext)
        surfaceView.setZOrderMediaOverlay(true) // Put local video on top
        localVideoContainer.addView(surfaceView)
        
        rtcEngine?.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, 0))
        rtcEngine?.startPreview()
        
        // Make sure local video is visible
        localVideoContainer.visibility = View.VISIBLE
        
        Log.d(TAG, "Local video setup complete")
    }
    
    private fun setupRemoteVideo(remoteUid: Int) {
        Log.d(TAG, "Setting up remote video for UID: $remoteUid")
        
        // Clear any existing remote video
        remoteVideoContainer.removeAllViews()
        
        val surfaceView = SurfaceView(baseContext)
        surfaceView.setZOrderMediaOverlay(false) // Put remote video behind local video
        remoteVideoContainer.addView(surfaceView)
        
        rtcEngine?.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, remoteUid))
        
        // Make sure remote video container is visible
        remoteVideoContainer.visibility = View.VISIBLE
        
        Log.d(TAG, "Remote video setup complete for UID: $remoteUid")
    }
    
    private val mRtcEventHandler: IRtcEngineEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.d(TAG, "Successfully joined channel: $channel")
            runOnUiThread {
                Toast.makeText(this@calling, "Call connected!", Toast.LENGTH_SHORT).show()
                isJoined = true
                startCallDurationTimer()
            }
        }
        
        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.d(TAG, "User joined: $uid")
            runOnUiThread {
                Toast.makeText(this@calling, "User joined the call", Toast.LENGTH_SHORT).show()
                setupRemoteVideo(uid)
            }
        }
        
        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d(TAG, "User offline: $uid, reason: $reason")
            runOnUiThread {
                Toast.makeText(this@calling, "User left the call", Toast.LENGTH_SHORT).show()
                remoteVideoContainer.removeAllViews()
            }
        }
        
        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            Log.d(TAG, "Remote video state changed: uid=$uid, state=$state, reason=$reason")
            runOnUiThread {
                when (state) {
                    0 -> {
                        Log.d(TAG, "Remote video stopped")
                        Toast.makeText(this@calling, "Remote video stopped", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        Log.d(TAG, "Remote video started")
                        Toast.makeText(this@calling, "Remote video started", Toast.LENGTH_SHORT).show()
                        setupRemoteVideo(uid)
                    }
                    2 -> {
                        Log.d(TAG, "Remote video failed to start")
                        Toast.makeText(this@calling, "Remote video failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        
        override fun onError(err: Int) {
            Log.e(TAG, "Agora error: $err")
            runOnUiThread {
                Toast.makeText(this@calling, "Call error: $err", Toast.LENGTH_SHORT).show()
                endCall()
            }
        }
        
        override fun onLeaveChannel(stats: RtcStats?) {
            Log.d(TAG, "Left channel")
            runOnUiThread {
                Toast.makeText(this@calling, "Left channel", Toast.LENGTH_SHORT).show()
                isJoined = false
                localVideoContainer.removeAllViews()
                remoteVideoContainer.removeAllViews()
            }
        }
    }
    
    private fun generateChannelName(userId1: String, userId2: String): String {
        return listOf(userId1, userId2).sorted().joinToString("_")
    }
    
    private fun toggleMute() {
        isMuted = !isMuted
        rtcEngine?.muteLocalAudioStream(isMuted)
        btnMute.setImageResource(if (isMuted) R.drawable.mic_off else R.drawable.mic_on)
        Toast.makeText(this, if (isMuted) "Microphone muted" else "Microphone unmuted", Toast.LENGTH_SHORT).show()
    }
    
    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        rtcEngine?.setEnableSpeakerphone(isSpeakerOn)
        btnSpeaker.setImageResource(if (isSpeakerOn) R.drawable.soundoff else R.drawable.soundon)
        Toast.makeText(this, if (isSpeakerOn) "Speaker off" else "Speaker on", Toast.LENGTH_SHORT).show()
    }
    
    private fun switchCamera() {
        rtcEngine?.switchCamera()
        Toast.makeText(this, "Camera switched", Toast.LENGTH_SHORT).show()
    }
    
    private fun startCallDurationTimer() {
        callStartTime = System.currentTimeMillis()
        callDurationHandler = Handler(Looper.getMainLooper())
        callDurationRunnable = object : Runnable {
            override fun run() {
                val duration = System.currentTimeMillis() - callStartTime
                val minutes = (duration / 60000).toInt()
                val seconds = ((duration % 60000) / 1000).toInt()
                tvDuration.text = String.format("%02d:%02d", minutes, seconds)
                callDurationHandler?.postDelayed(this, 1000)
            }
        }
        callDurationHandler?.post(callDurationRunnable!!)
    }
    
    private fun endCall() {
        callDurationHandler?.removeCallbacks(callDurationRunnable!!)
        
        // Update Firebase call status
        if (callId.isNotEmpty()) {
            callManager.endCall(callId,
                onSuccess = {
                    Log.d(TAG, "Call status updated to ended")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to update call status: $error")
                }
            )
        }
        
        // Clean up Agora
        try {
            if (isJoined) {
                rtcEngine?.leaveChannel()
            }
            rtcEngine?.stopPreview()
            RtcEngine.destroy()
            rtcEngine = null
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call", e)
        }
        
        finish()
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeAgoraEngine()
            } else {
                Toast.makeText(this, "Permissions required for call", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        endCall()
    }
}
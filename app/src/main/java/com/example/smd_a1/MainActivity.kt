package com.example.smd_a1

import android.content.Intent
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class MainActivity : AppCompatActivity() {
    private val notifPermissionLauncher by lazy {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // no-op; user choice is respected
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Enable Firebase App Check debug provider in debug builds to prevent 403s during Storage uploads
        try {
            val isDebuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.i("MainActivity", "Firebase App Check debug provider installed")
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "App Check init skipped: ${e.message}")
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Splash screen: Show for 5 seconds, then navigate based on auth state
        Handler(Looper.getMainLooper()).postDelayed({
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            
            if (currentUser != null) {
                // Initialize notifications and register FCM token
                ensureNotificationPermission()
                initializeNotifications()
                try {
                    PresenceManager(this).start()
                } catch (e: Exception) {
                    Log.w("MainActivity", "Presence init failed: ${e.message}")
                }
                
                // User is logged in - check if profile setup is done
                val db = FirebaseDatabase.getInstance().reference
                db.child("users").child(currentUser.uid).child("setupDone").get()
                    .addOnSuccessListener { snapshot ->
                        val setupDone = snapshot.getValue(Boolean::class.java) ?: false
                        val intent = if (setupDone) {
                            Intent(this@MainActivity, feed::class.java)
                        } else {
                            Intent(this@MainActivity, editprofile::class.java)
                        }
                        intent.putExtra("uid", currentUser.uid)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener {
                        // On failure, go to feed (default for logged-in users)
                        val intent = Intent(this@MainActivity, feed::class.java)
                        intent.putExtra("uid", currentUser.uid)
                        startActivity(intent)
                        finish()
                    }
            } else {
                // User not logged in - go to login
                startActivity(Intent(this@MainActivity, login::class.java))
                finish()
            }
        }, 5000) // 5 seconds as per requirements
    }
    
    /**
     * Initialize notifications and register FCM token
     */
    private fun initializeNotifications() {
        try {
            val notificationInitializer = NotificationInitializer(this)
            notificationInitializer.initialize()
            Log.d("MainActivity", "Notification initialization started")
            
            // Also update FCM token directly to ensure it's saved
            val notificationManager = NotificationManager()
            notificationManager.updateFCMToken()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to initialize notifications: ${e.message}", e)
        }
    }

    private fun ensureNotificationPermission() {
        Log.i("MainActivity", "ensureNotificationPermission: SDK=${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

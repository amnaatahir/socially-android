package com.example.smd_a1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        try {
            FirebaseApp.initializeApp(this)
            auth = FirebaseAuth.getInstance()
            db = FirebaseDatabase.getInstance()
        } catch (e: Exception) {
            Log.e("LoginCrash", "❌ Firebase init failed: ${e.message}")
            Toast.makeText(this, "Firebase init error: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        // Initialize views with null checks
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val tvSignUp = findViewById<TextView>(R.id.tvSignUp)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)

        // Check if all views are found
        if (btnBack == null || tvSignUp == null || btnLogin == null || 
            tvForgotPassword == null || etUsername == null || etPassword == null) {
            Log.e("Login", "One or more views not found!")
            Toast.makeText(this, "UI initialization error", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("Login", "All views initialized successfully")

        // Back arrow -> Register
        btnBack.setOnClickListener {
            startActivity(Intent(this@login, register::class.java))
            finish()
        }

        // Sign up link -> Register
        tvSignUp.setOnClickListener {
            startActivity(Intent(this@login, register::class.java))
            finish()
        }

        // Forgot password -> Show message
        tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Password reset feature coming soon", Toast.LENGTH_SHORT).show()
        }

        // Login button -> Authenticate and go to profile
        btnLogin.setOnClickListener {
            try {
                val username = etUsername.text.toString().trim()
                val password = etPassword.text.toString().trim()

                Log.d("Login", "Attempting login with username: $username")

                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Show loading
                btnLogin.isEnabled = false
                btnLogin.text = "Logging in..."

                // Check if input is email or username (with basic validation)
                val isEmail = username.contains("@") && username.contains(".")
                
                if (isEmail) {
                    // Direct login with email and password
                    Log.d("Login", "Attempting direct email login")
                    auth.signInWithEmailAndPassword(username, password)
                        .addOnSuccessListener { result ->
                            // Initialize notifications and save FCM token after successful login
                            initializeNotifications()
                            val uid = result.user?.uid
                            if (uid != null) {
                                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                                // After login, route based on setupDone flag
                                db.getReference("users").child(uid).child("setupDone").get()
                                    .addOnSuccessListener { s ->
                                        val next = if (s.getValue(Boolean::class.java) == true) Intent(this@login, feed::class.java) else Intent(this@login, editprofile::class.java)
                                        next.putExtra("uid", uid)
                                        startActivity(next)
                                        finish()
                                    }
                                    .addOnFailureListener {
                                        val intent = Intent(this@login, feed::class.java)
                                        intent.putExtra("uid", uid)
                                        startActivity(intent)
                                        finish()
                                    }
                            } else {
                                Toast.makeText(this, "Login failed: No user ID", Toast.LENGTH_SHORT).show()
                                btnLogin.isEnabled = true
                                btnLogin.text = "Log in"
                            }
                        }
                        .addOnFailureListener { error ->
                            Log.e("Login", "Email login failed: ${error.message}")
                            Toast.makeText(this, "Login failed: ${error.message}", Toast.LENGTH_SHORT).show()
                            btnLogin.isEnabled = true
                            btnLogin.text = "Log in"
                        }
                } else {
                    // Try to find user by username first
                    Log.d("Login", "Attempting username lookup")
                    db.getReference("usernames").child(username).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val uid = snapshot.value?.toString()
                        if (uid != null) {
                            // Get user's email from Firebase
                            db.getReference("users").child(uid).get()
                                .addOnSuccessListener { userSnapshot ->
                                    if (userSnapshot.exists()) {
                                        val email = userSnapshot.child("email").value?.toString()
                                        if (email != null) {
                                            // Sign in with email and password
                                            auth.signInWithEmailAndPassword(email, password)
                                                .addOnSuccessListener {
                                                                // Initialize notifications and save FCM token after successful login
                                                                initializeNotifications()
                                                                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                                                                db.getReference("users").child(uid).child("setupDone").get()
                                                                    .addOnSuccessListener { s ->
                                                                        val next = if (s.getValue(Boolean::class.java) == true) Intent(this@login, feed::class.java) else Intent(this@login, editprofile::class.java)
                                                                        next.putExtra("uid", uid)
                                                                        startActivity(next)
                                                                        finish()
                                                                    }
                                                                    .addOnFailureListener {
                                                                        val intent = Intent(this@login, feed::class.java)
                                                                        intent.putExtra("uid", uid)
                                                                        startActivity(intent)
                                                                        finish()
                                                                    }
                                                }
                                                .addOnFailureListener { error ->
                                                    Toast.makeText(this, "Login failed: ${error.message}", Toast.LENGTH_SHORT).show()
                                                    btnLogin.isEnabled = true
                                                    btnLogin.text = "Log in"
                                                }
                                        } else {
                                            Toast.makeText(this, "User email not found", Toast.LENGTH_SHORT).show()
                                            btnLogin.isEnabled = true
                                            btnLogin.text = "Log in"
                                        }
                                    } else {
                                        Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                                        btnLogin.isEnabled = true
                                        btnLogin.text = "Log in"
                                    }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this, "Failed to get user data", Toast.LENGTH_SHORT).show()
                                    btnLogin.isEnabled = true
                                    btnLogin.text = "Log in"
                                }
                        } else {
                            Toast.makeText(this, "Invalid username", Toast.LENGTH_SHORT).show()
                            btnLogin.isEnabled = true
                            btnLogin.text = "Log in"
                        }
                    } else {
                        Toast.makeText(this, "Username not found. Please sign up first.", Toast.LENGTH_SHORT).show()
                        btnLogin.isEnabled = true
                        btnLogin.text = "Log in"
                    }
                }
                .addOnFailureListener {
                    Log.e("Login", "Error checking username: ${it.message}")
                    Toast.makeText(this, "Error checking username: ${it.message}", Toast.LENGTH_SHORT).show()
                    btnLogin.isEnabled = true
                    btnLogin.text = "Log in"
                }
                } // Close the else block for username lookup
            } catch (e: Exception) {
                Log.e("Login", "Login button click error: ${e.message}")
                Toast.makeText(this, "Login error: ${e.message}", Toast.LENGTH_SHORT).show()
                btnLogin.isEnabled = true
                btnLogin.text = "Log in"
            }
        }
    }
    
    /**
     * Initialize notifications and register FCM token
     */
    private fun initializeNotifications() {
        try {
            val notificationInitializer = NotificationInitializer(this)
            notificationInitializer.initialize()
            Log.d("Login", "Notification initialization started")
            
            // Also update FCM token directly to ensure it's saved
            val notificationManager = NotificationManager()
            notificationManager.updateFCMToken()
        } catch (e: Exception) {
            Log.e("Login", "Failed to initialize notifications: ${e.message}", e)
        }
    }
}

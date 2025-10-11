package com.example.smd_a1

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.util.Calendar

class register : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val storageRef = FirebaseStorage.getInstance().reference
    private lateinit var etUsername: EditText
    private lateinit var etFirst: EditText
    private lateinit var etLast: EditText
    private lateinit var etDob: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnCreate: Button
    private lateinit var btnBack: ImageButton
    private lateinit var imgC1: ImageView
    private var imgEye: ImageView? = null
    private var selectedImageUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            Glide.with(this)
                .load(it)
                .placeholder(R.drawable.profile_pic)
                .circleCrop()
                .into(imgC1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        try {
            FirebaseApp.initializeApp(this)
            auth = FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.e("RegisterCrash", "❌ Firebase init failed: ${e.message}")
            Toast.makeText(this, "Firebase init error: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        // Initialize views
        etUsername = findViewById(R.id.etUsername)
        etFirst = findViewById(R.id.etFirst)
        etLast = findViewById(R.id.etLast)
        etDob = findViewById(R.id.etDob)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnCreate = findViewById(R.id.btnCreate)
        btnBack = findViewById(R.id.btnBack)
        imgC1 = findViewById(R.id.imgC1)
        
        // Safely find imgEye if it exists in layout (nullable - may not exist)
        imgEye = findViewById<ImageView?>(R.id.imgEye)
        imgEye?.setOnClickListener {
            togglePasswordVisibility()
        }

        // Set up click listeners
        btnBack.setOnClickListener {
            finishAffinity()
        }

        imgC1.setOnClickListener {
            pickImage.launch("image/*")
        }

        etDob.setOnClickListener {
            showDatePicker()
        }

        btnCreate.setOnClickListener {
            createAccount()
        }

        // Hardware/gesture back -> same behavior
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAffinity()
            }
        })
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                etDob.setText(String.format("%02d - %02d - %04d", dayOfMonth, month + 1, year))
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun togglePasswordVisibility() {
        val start = etPassword.selectionStart
        val end = etPassword.selectionEnd
        
        if (etPassword.inputType == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            etPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            imgEye?.setImageResource(android.R.drawable.ic_menu_view)
        } else {
            etPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            imgEye?.setImageResource(android.R.drawable.ic_menu_view)
        }
        
        // Keep cursor position
        etPassword.setSelection(start, end)
    }

    private fun isValidEmail(email: String): Boolean {
        val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"
        return email.matches(emailPattern.toRegex())
    }
    
    private fun isValidUsername(username: String): Boolean {
        // Username: 3-30 characters, alphanumeric and underscores only
        if (username.length < 3 || username.length > 30) return false
        return username.matches("^[a-zA-Z0-9_]+$".toRegex())
    }
    
    private fun createAccount() {
        val username = etUsername.text.toString().trim()
        val first = etFirst.text.toString().trim()
        val last = etLast.text.toString().trim()
        val dob = etDob.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (username.isEmpty() || first.isEmpty() || last.isEmpty() || 
            dob.isEmpty() || email.isEmpty() || password.length < 6) {
            Toast.makeText(this, "Please fill all fields (password ≥ 6 characters)", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Validate email format
        if (!isValidEmail(email)) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Validate username format
        if (!isValidUsername(username)) {
            Toast.makeText(this, "Username must be 3-30 characters (letters, numbers, underscores only)", Toast.LENGTH_SHORT).show()
            return
        }

        btnCreate.isEnabled = false
        btnCreate.text = "Creating Account..."

        val db = FirebaseDatabase.getInstance().reference

        // Create Firebase Auth user first (this authenticates the user)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: run {
                    Toast.makeText(this, "Could not get user ID", Toast.LENGTH_SHORT).show()
                    btnCreate.isEnabled = true
                    btnCreate.text = "Create an Account"
                    return@addOnSuccessListener
                }

                // Now check if username is available (user is authenticated now)
                db.child("usernames").child(username).get()
                    .addOnSuccessListener { snap ->
                        if (snap.exists()) {
                            // Username taken - delete the auth account we just created
                            result.user?.delete()?.addOnCompleteListener {
                                Toast.makeText(this, "Username already taken", Toast.LENGTH_SHORT).show()
                                btnCreate.isEnabled = true
                                btnCreate.text = "Create an Account"
                            }
                            return@addOnSuccessListener
                        }

                        // Upload profile picture if selected, then save user data
                        if (selectedImageUri != null) {
                            btnCreate.text = "Uploading photo..."
                            val imageRef = storageRef.child("profile_pictures/$uid.jpg")
                            imageRef.putFile(selectedImageUri!!)
                                .addOnSuccessListener { taskSnapshot ->
                                    imageRef.downloadUrl.addOnSuccessListener { photoUri ->
                                        saveUserDataWithPhoto(uid, username, first, last, dob, email, db, photoUri.toString())
                                    }.addOnFailureListener {
                                        // Continue without photo if upload fails
                                        saveUserDataWithPhoto(uid, username, first, last, dob, email, db, "")
                                    }
                                }
                                .addOnFailureListener {
                                    // Continue without photo if upload fails
                                    saveUserDataWithPhoto(uid, username, first, last, dob, email, db, "")
                                }
                        } else {
                            saveUserDataWithPhoto(uid, username, first, last, dob, email, db, "")
                        }
                    }
                    .addOnFailureListener { 
                        // If username check fails, still try to proceed (might be a permission issue)
                        // But we'll attempt to save the username mapping which will fail if duplicate
                        Log.w("Register", "Could not check username availability: ${it.message}. Proceeding anyway...")
                        
                        // Continue without photo if upload fails
                        saveUserDataWithPhoto(uid, username, first, last, dob, email, db, "")
                    }
            }
            .addOnFailureListener { 
                Toast.makeText(this, "Failed to create account: ${it.message}", Toast.LENGTH_SHORT).show()
                btnCreate.isEnabled = true
                btnCreate.text = "Create an Account"
            }
    }
    
    private fun saveUserDataWithPhoto(uid: String, username: String, first: String, last: String, 
                                      dob: String, email: String, db: com.google.firebase.database.DatabaseReference, photoUrl: String) {
        val userData = mapOf(
            "uid" to uid,
            "username" to username,
            "firstName" to first,
            "lastName" to last,
            "fullName" to "$first $last",
            "dob" to dob,
            "email" to email,
            "bio" to "",
            "photoUrl" to photoUrl,
            "setupDone" to false,
            "online" to false
        )

        db.child("users").child(uid).setValue(userData)
            .addOnSuccessListener {
                // Also save username mapping
                db.child("usernames").child(username).setValue(uid)
                    .addOnSuccessListener {
                        btnCreate.isEnabled = true
                        btnCreate.text = "Create an Account"
                        Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()
                        
                        // Initialize notifications and register FCM token after account creation
                        initializeNotifications()
                        
                        // First-time users go to Profile Setup
                        val intent = Intent(this@register, editprofile::class.java)
                        intent.putExtra("uid", uid)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { 
                        Toast.makeText(this, "Username mapping failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        btnCreate.isEnabled = true
                        btnCreate.text = "Create an Account"
                    }
            }
            .addOnFailureListener { 
                Toast.makeText(this, "Failed to save user data: ${it.message}", Toast.LENGTH_SHORT).show()
                btnCreate.isEnabled = true
                btnCreate.text = "Create an Account"
            }
    }
    
    /**
     * Initialize notifications and register FCM token
     */
    private fun initializeNotifications() {
        try {
            val notificationInitializer = NotificationInitializer(this)
            notificationInitializer.initialize()
            android.util.Log.d("Register", "Notification initialization started")
            
            // Also update FCM token directly to ensure it's saved
            val notificationManager = NotificationManager()
            notificationManager.updateFCMToken()
        } catch (e: Exception) {
            android.util.Log.e("Register", "Failed to initialize notifications: ${e.message}", e)
        }
    }
}

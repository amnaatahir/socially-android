package com.example.smd_a1

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class editprofile : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val dbRef = FirebaseDatabase.getInstance().getReference("users")
    private val storageRef = FirebaseStorage.getInstance().reference
    private lateinit var profileImageView: ImageView
    private var selectedImageUri: Uri? = null
    private var isUploading = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            profileImageView.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editprofile)

        auth = FirebaseAuth.getInstance()

        val tvCancel = findViewById<TextView>(R.id.cancel1)
        val tvDone = findViewById<TextView>(R.id.done1)
        val etName = findViewById<EditText>(R.id.value_name)
        val etUsername = findViewById<EditText>(R.id.value_username)
        val etBio = findViewById<EditText>(R.id.value_bio)
        val tvEmail = findViewById<TextView>(R.id.value_email) // Email is read-only
        val etPhone = findViewById<EditText>(R.id.value_phone)
        val etGender = findViewById<EditText>(R.id.value_gender)
        profileImageView = findViewById(R.id.storyProfilePic1)
        val changeProfileText = findViewById<TextView>(R.id.changeprofile1)

        val uid = auth.currentUser?.uid ?: return

        // Load current user data
        var emailMissingInDb = false
        dbRef.child(uid).get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                etName.setText(snap.child("fullName").value?.toString() ?: "")
                etUsername.setText(snap.child("username").value?.toString() ?: "")
                val emailFromDb = snap.child("email").value?.toString()
                if (!emailFromDb.isNullOrBlank()) {
                    tvEmail.text = emailFromDb
                } else {
                    // Fallback to Auth email if DB missing
                    tvEmail.text = auth.currentUser?.email ?: ""
                    emailMissingInDb = true
                }
                etBio.setText(snap.child("bio").value?.toString() ?: "")
                etPhone.setText(snap.child("phone").value?.toString() ?: "")
                etGender.setText(snap.child("gender").value?.toString() ?: "")
                
                // Load profile picture if available
                val photoUrl = snap.child("photoUrl").value?.toString()
                if (!photoUrl.isNullOrEmpty()) {
                    // Load from Firebase Storage using Glide
                    Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.profile_pic)
                        .error(R.drawable.profile_pic)
                        .circleCrop()
                        .into(profileImageView)
                } else {
                    // Try to load from Base64 (for backward compatibility)
                    val photoBase64 = snap.child("photoBase64").value?.toString()
                    if (!photoBase64.isNullOrEmpty()) {
                        try {
                            val imageBytes = Base64.decode(photoBase64, Base64.NO_WRAP)
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            profileImageView.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            android.util.Log.e("EditProfile", "Failed to load profile image: ${e.message}")
                            profileImageView.setImageResource(R.drawable.profile_pic)
                        }
                    } else {
                        profileImageView.setImageResource(R.drawable.profile_pic)
                    }
                }
            }
        }

        // Change profile picture click listener
        changeProfileText.setOnClickListener {
            pickImage.launch("image/*")
        }
        
        profileImageView.setOnClickListener {
            pickImage.launch("image/*")
        }

        tvCancel.setOnClickListener {
            finish()
        }

        tvDone.setOnClickListener {
            if (isUploading) {
                Toast.makeText(this, "Please wait for image upload to complete", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val updates = mutableMapOf<String, Any>(
                "fullName" to etName.text.toString().trim(),
                "username" to etUsername.text.toString().trim(),
                "bio" to etBio.text.toString().trim(),
                // Email is not editable - don't update it
                "phone" to etPhone.text.toString().trim(),
                "gender" to etGender.text.toString().trim(),
                "setupDone" to true
            )

            // If email was missing in DB, backfill it from Auth
            if (emailMissingInDb) {
                auth.currentUser?.email?.let { updates["email"] = it }
            }
            
            // Upload profile picture if a new one was selected
            if (selectedImageUri != null) {
                isUploading = true
                tvDone.isEnabled = false
                tvDone.text = "Uploading..."
                
                // Ensure user is authenticated before upload
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show()
                    isUploading = false
                    tvDone.isEnabled = true
                    tvDone.text = "Done"
                    return@setOnClickListener
                }
                
                val imageRef = storageRef.child("profile_pictures/${currentUser.uid}.jpg")
                
                // Upload file (Firebase Storage automatically detects content type from file extension)
                imageRef.putFile(selectedImageUri!!)
                    .addOnSuccessListener { taskSnapshot ->
                        // Get download URL
                        imageRef.downloadUrl.addOnSuccessListener { uri ->
                            updates["photoUrl"] = uri.toString()
                            updates["photoUpdatedAt"] = System.currentTimeMillis()
                            // Save to database
                            dbRef.child(uid).updateChildren(updates)
                                .addOnSuccessListener {
                                    isUploading = false
                                    tvDone.isEnabled = true
                                    tvDone.text = "Done"
                                    Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                                    // Reflect new photo immediately
                                    Glide.with(this)
                                        .load(uri)
                                        .placeholder(R.drawable.profile_pic)
                                        .error(R.drawable.profile_pic)
                                        .circleCrop()
                                        .into(profileImageView)
                                    startActivity(Intent(this, feed::class.java))
                                    finish()
                                }
                                .addOnFailureListener {
                                    isUploading = false
                                    tvDone.isEnabled = true
                                    tvDone.text = "Done"
                                    Toast.makeText(this, "Failed to update profile: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .addOnFailureListener { exception ->
                        isUploading = false
                        tvDone.isEnabled = true
                        tvDone.text = "Done"
                        
                        
                        // Fallback: save a downsized Base64 image in the user record so profile picture still updates
                        try {
                            val b64 = encodeImageToBase64(selectedImageUri!!)
                            if (b64 != null) {
                                updates["photoBase64"] = b64
                                updates["photoUpdatedAt"] = System.currentTimeMillis()
                                dbRef.child(uid).updateChildren(updates)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Profile updated with inline image.", Toast.LENGTH_SHORT).show()
                                        // Reflect new photo immediately
                                        try {
                                            val bytes = Base64.decode(b64, Base64.NO_WRAP)
                                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            profileImageView.setImageBitmap(bitmap)
                                        } catch (_: Exception) { /* ignore */ }
                                        startActivity(Intent(this, feed::class.java))
                                        finish()
                                    }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("EditProfile", "Base64 fallback failed: ${e.message}", e)
                        }
                    }
            } else {
                // No image selected, just update other fields
                dbRef.child(uid).updateChildren(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, feed::class.java))
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }
    private fun encodeImageToBase64(uri: Uri): String? {
        return try {
            // Downsample to keep DB size reasonable
            val optsProbe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, optsProbe) }
            var sample = 1
            var w = optsProbe.outWidth
            var h = optsProbe.outHeight
            while (w > 720 || h > 720) { sample *= 2; w /= 2; h /= 2 }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("EditProfile", "encodeImageToBase64 failed: ${e.message}", e)
            null
        }
    }
}

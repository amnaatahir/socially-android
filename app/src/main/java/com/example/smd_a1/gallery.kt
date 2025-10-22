package com.example.smd_a1

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream

class gallery : AppCompatActivity() {

    private lateinit var preview: ImageView
    private var picked: List<Uri> = emptyList()

    private val pickMulti =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            picked = uris ?: emptyList()
            if (picked.isNotEmpty()) {
                preview.setImageURI(picked.first())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        val root = findViewById<View?>(R.id.main)
        root?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        preview = findViewById(R.id.selectedImage1)

        // open picker immediately
        pickMulti.launch("image/*")

        findViewById<TextView>(R.id.tvCancel).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvNext).setOnClickListener {
            if (picked.isNotEmpty()) {
                createPostFromUri(picked.first(), caption = "My new post")
            } else {
                finish()
            }
        }
    }

    // Create a Post and write it to RTDB, then return to feed
    private fun createPostFromUri(uri: Uri, caption: String?) {
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val bmp = BitmapFactory.decodeStream(input) ?: return
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        } ?: return

        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val now = System.currentTimeMillis()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anon"
        val postId = FirebaseDatabase.getInstance().reference.push().key ?: "${uid}_$now"

        // Load username from Firebase
        FirebaseDatabase.getInstance().getReference("users").child(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val username = if (snapshot.exists()) {
                    snapshot.child("username").value?.toString() 
                        ?: snapshot.child("fullName").value?.toString()
                        ?: "User"
                } else {
                    "User"
                }
                
                val post = Post(
                    postId = postId,
                    authorId = uid,
                    authorName = username,
                    mediaBase64 = b64,
                    caption = caption,
                    createdAt = now,
                    likeCount = 0
                )

                val db = FirebaseDatabase.getInstance().reference
                val updates = hashMapOf<String, Any>(
                    "posts/$postId" to post,
                    "userPosts/$uid/$postId" to true
                )
                db.updateChildren(updates).addOnSuccessListener {
                    startActivity(
                        Intent(this, feed::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    finish()
                }
            }
            .addOnFailureListener {
                // Fallback if Firebase query fails
                val post = Post(
                    postId = postId,
                    authorId = uid,
                    authorName = "User",
                    mediaBase64 = b64,
                    caption = caption,
                    createdAt = now,
                    likeCount = 0
                )

                val db = FirebaseDatabase.getInstance().reference
                val updates = hashMapOf<String, Any>(
                    "posts/$postId" to post,
                    "userPosts/$uid/$postId" to true
                )
                db.updateChildren(updates).addOnSuccessListener {
                    startActivity(
                        Intent(this, feed::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                    finish()
                }
            }
    }
}

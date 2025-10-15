// feedpage.kt
package com.example.smd_a1
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class feedpage : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedpage)

        val root = findViewById<View>(R.id.main) ?: window.decorView
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        // Bottom navigation
        findViewById<ImageButton>(R.id.navHome)?.setOnClickListener {
            startActivity(Intent(this, feed::class.java))
        }
        findViewById<ImageButton>(R.id.navSearch)?.setOnClickListener {
            startActivity(Intent(this, internshala::class.java))
        }
        findViewById<ImageButton>(R.id.navAdd)?.setOnClickListener {
            startActivity(Intent(this, gallery::class.java))
        }
        findViewById<ImageButton>(R.id.navHeart)?.setOnClickListener {
            startActivity(Intent(this, followingyou::class.java))
        }
        findViewById<ImageButton>(R.id.navProfile)?.setOnClickListener {
            val intent = Intent(this, instaprof::class.java)
            intent.putExtra("uid", com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid)
            startActivity(intent)
        }
        // feedpage.kt
        findViewById<android.widget.TextView>(R.id.searchBox)?.setOnClickListener {
            startActivity(Intent(this, internshala::class.java))
        }
        
        // Check if postId is provided - if so, load and display that specific post
        val postId = intent.getStringExtra("postId")
        if (!postId.isNullOrEmpty()) {
            loadPostById(postId)
        }
    }
    
    private fun loadPostById(postId: String) {
        FirebaseDatabase.getInstance().getReference("posts").child(postId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val post = snapshot.getValue(Post::class.java)
                    if (post != null) {
                        // Display post details - main viewing is in feed.kt
                        // This is a confirmation that the post was found
                        Toast.makeText(this, "Viewing post by ${post.authorName}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Post not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load post", Toast.LENGTH_SHORT).show()
            }
    }
}

// story.kt
package com.example.smd_a1

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
// Removed Glide usage; falling back to placeholder when needed

class story : AppCompatActivity() {
    @SuppressLint("WrongViewCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_story)

        findViewById<ImageButton>(R.id.btnBack11)?.setOnClickListener { finish() }

        val type = intent.getStringExtra("mediaType") ?: "image"
        val b64  = intent.getStringExtra("mediaB64")
        val url  = intent.getStringExtra("mediaUrl")
        val ownerName = intent.getStringExtra("ownerName") ?: "User"
        
        // Update username from intent (not hardcoded)
        findViewById<android.widget.TextView>(R.id.storyUsername)?.text = ownerName

        val iv = findViewById<ImageView>(R.id.storyImage)
        if (type == "video") {
            val vv = findViewById<VideoView>(R.id.storyVideo)
            if (!url.isNullOrBlank()) {
                iv.visibility = android.view.View.GONE
                vv.visibility = android.view.View.VISIBLE
                vv.setVideoURI(Uri.parse(url))
                vv.setOnPreparedListener { it.isLooping = false; vv.start() }
            } else {
                // Fallback to placeholder image
                iv.visibility = android.view.View.VISIBLE
                iv.setImageResource(R.drawable.profile_pic)
            }
            return
        }

        when {
            !b64.isNullOrBlank() -> {
                runCatching {
                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    iv.setImageBitmap(bmp)
                }.onFailure {
                    if (!url.isNullOrBlank()) {
                        // You can add a simple URL loader later; for now show placeholder
                        iv.setImageResource(R.drawable.profile_pic)
                    }
                }
            }
            !url.isNullOrBlank() -> iv.setImageResource(R.drawable.profile_pic)
            else -> iv.setImageResource(R.drawable.profile_pic)
        }
    }
}

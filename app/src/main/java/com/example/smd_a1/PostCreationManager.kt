package com.example.smd_a1

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Manages post creation and Firebase operations
 */
class PostCreationManager(private val context: Context) {
    
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()
    private val postsRef = database.getReference("posts")
    private val usersRef = database.getReference("users")
    private val activitiesRef = database.getReference("activities")
    
    /**
     * Create a new post with image
     */
    fun createPost(
        imageUri: Uri,
        caption: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val currentUser = auth.currentUser ?: return
        val postId = postsRef.push().key ?: return
        
        // Convert image to base64
        val imageBase64 = convertImageToBase64(imageUri)
        if (imageBase64.isNullOrEmpty()) {
            onFailure("Failed to process image")
            return
        }
        
        // Get user data
        usersRef.child(currentUser.uid).get().addOnSuccessListener { userSnapshot ->
            val username = userSnapshot.child("username").value?.toString() ?: "user"
            val fullName = userSnapshot.child("fullName").value?.toString() ?: "User"
            
            // Create post object
            val post = Post(
                postId = postId,
                authorId = currentUser.uid,
                authorName = username,
                caption = caption,
                mediaBase64 = imageBase64,
                createdAt = System.currentTimeMillis(),
                likeCount = 0,
                commentCount = 0
            )
            
            // Save post to Firebase
            postsRef.child(postId).setValue(post)
                .addOnSuccessListener {
                    // Create activity entry
                    createPostActivity(currentUser.uid, username, postId)
                    
                    // Notify followers
                    notifyFollowers(currentUser.uid, username, "posted a new photo")
                    
                    Log.d("PostCreation", "Post created successfully: $postId")
                    onSuccess(postId)
                }
                .addOnFailureListener { error ->
                    Log.e("PostCreation", "Failed to create post: ${error.message}")
                    onFailure(error.message ?: "Failed to create post")
                }
        }.addOnFailureListener { error ->
            Log.e("PostCreation", "Failed to get user data: ${error.message}")
            onFailure("Failed to get user data")
        }
    }
    
    /**
     * Create activity entry for the post
     */
    private fun createPostActivity(userId: String, username: String, postId: String) {
        val activityId = activitiesRef.push().key ?: return
        
        val activity = mapOf(
            "activityId" to activityId,
            "userId" to userId,
            "username" to username,
            "action" to "posted a new photo",
            "postId" to postId,
            "timestamp" to System.currentTimeMillis()
        )
        
        activitiesRef.child(userId).child(activityId).setValue(activity)
    }
    
    /**
     * Notify followers about new post
     */
    private fun notifyFollowers(userId: String, username: String, action: String) {
        val followersRef = database.getReference("followers").child(userId)
        
        followersRef.get().addOnSuccessListener { snapshot ->
            snapshot.children.forEach { followerSnapshot ->
                val followerId = followerSnapshot.key ?: return@forEach
                if (followerSnapshot.getValue(Boolean::class.java) == true) {
                    // Create notification for follower
                    createNotification(followerId, userId, username, action, "post")
                }
            }
        }
    }
    
    /**
     * Create notification for user
     */
    private fun createNotification(
        targetUserId: String,
        fromUserId: String,
        fromUsername: String,
        action: String,
        type: String
    ) {
        val notificationId = database.getReference("notifications").push().key ?: return
        
        val notification = mapOf(
            "notificationId" to notificationId,
            "fromUserId" to fromUserId,
            "fromUsername" to fromUsername,
            "action" to action,
            "type" to type,
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )
        
        database.getReference("notifications").child(targetUserId).child(notificationId).setValue(notification)
    }
    
    /**
     * Convert image URI to base64 string
     */
    private fun convertImageToBase64(uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            
            // Compress bitmap
            val compressedBitmap = compressBitmap(bitmap, 800) // Max width 800px
            
            val byteArrayOutputStream = ByteArrayOutputStream()
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("PostCreation", "Error converting image to base64: ${e.message}")
            null
        }
    }
    
    /**
     * Compress bitmap to specified max width
     */
    private fun compressBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth) return bitmap
        
        val ratio = maxWidth.toFloat() / width
        val newHeight = (height * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
    }
}

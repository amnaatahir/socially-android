package com.example.smd_a1

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.database.ServerValue
import android.net.Uri

object StoryRepository {
    private val db = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    fun uploadStory(fileUri: Uri, mediaType: String, ownerName: String, onDone: (Boolean, String?) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return onDone(false, "Not signed in")
        val storyId = db.child("storiesPublic").push().key ?: return onDone(false, "No key")
        val ext = if (mediaType == "video") "mp4" else "jpg"
        val ref = storage.child("stories/$uid/$storyId.$ext")

        ref.putFile(fileUri).continueWithTask { ref.downloadUrl }
            .addOnSuccessListener { url ->
                val now = System.currentTimeMillis()
                val story = Storysave(
                    storyId = storyId,
                    ownerId = uid,
                    ownerName = ownerName,
                    mediaUrl = url.toString(),
                    mediaType = mediaType,
                    createdAt = now,
                    expiresAt = now + 24*60*60*1000
                )
                val updates = hashMapOf<String, Any>(
                    "storiesPublic/$storyId" to story,
                    "storiesUsers/$uid/$storyId" to true
                )
                db.updateChildren(updates).addOnSuccessListener { onDone(true, null) }
                    .addOnFailureListener { onDone(false, it.message) }
            }
            .addOnFailureListener { onDone(false, it.message) }
    }
}

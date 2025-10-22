package com.example.smd_a1

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
// Removed Glide – we use setImageURI or decoded bitmaps only
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.ByteArrayOutputStream
import com.google.firebase.storage.FirebaseStorage

class addingstory : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var btnPickImage: Button
    private lateinit var btnPickVideo: Button
    private lateinit var btnUpload: ImageButton
    private lateinit var ivPreview: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var stickersBtn: ImageButton

    private var pickedUri: Uri? = null
    private var pickedType = "image"

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            pickedUri = it
            pickedType = "image"
            ivPreview.setImageURI(it)
        }
    }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            pickedUri = it
            pickedType = "video"
            // show a simple placeholder icon for video preview
            ivPreview.setImageResource(R.drawable.reel)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_addingstory)  // XML is fine as-is
        // (activity_addingstory.xml) :contentReference[oaicite:5]{index=5}

        btnBack = findViewById(R.id.btnBack)
        btnPickImage = findViewById(R.id.btnPickImage)
        btnPickVideo = findViewById(R.id.btnPickVideo)
        btnUpload = findViewById(R.id.btnUpload)
        ivPreview = findViewById(R.id.ivPreview)
        progress = findViewById(R.id.progress)
        stickersBtn = findViewById(R.id.stickers)

        intent.getStringExtra("preselectUri")?.let { s ->
            runCatching { Uri.parse(s) }.getOrNull()?.let { u ->
                pickedUri = u
                pickedType = "image"
                ivPreview.setImageURI(u)
            }
        }

        btnBack.setOnClickListener { finish() }
        btnPickImage.setOnClickListener { pickImage.launch("image/*") }
        stickersBtn.setOnClickListener { pickImage.launch("image/*") }

        // Enable video picking (we will upload to Firebase Storage and save mediaUrl)
        btnPickVideo.isEnabled = true
        btnPickVideo.alpha = 1f
        btnPickVideo.setOnClickListener { pickVideo.launch("video/*") }

        btnUpload.setOnClickListener { uploadToRealtimeOnly() }
    }

    private fun uploadToRealtimeOnly() {
        val uri = pickedUri ?: run { toast("Pick a media file first"); return }
        setBusy(true)

        val now = System.currentTimeMillis()
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: "anon"
        val storyId = "${uid}_${now}"

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
                val db = FirebaseDatabase.getInstance().reference

                if (pickedType == "video") {
                    val storage = FirebaseStorage.getInstance().reference
                    val videoRef = storage.child("story_videos/${storyId}.mp4")
                    videoRef.putFile(uri)
                        .addOnSuccessListener {
                            videoRef.downloadUrl.addOnSuccessListener { url ->
                                val story = Storysave(
                                    storyId = storyId,
                                    ownerId = uid,
                                    ownerName = username,
                                    mediaUrl = url.toString(),
                                    mediaType = "video",
                                    createdAt = now,
                                    expiresAt = now + 24L * 60 * 60 * 1000
                                )
                                val updates = hashMapOf<String, Any>(
                                    "storiesPublic/$storyId" to story,
                                    "userStories/$uid/$storyId" to true
                                )
                                db.updateChildren(updates)
                                    .addOnSuccessListener {
                                        // Notify followers + self in YOU tab
                                        createStoryAddedNotifications(uid, username, storyId)
                                        setBusy(false)
                                        toast("Story uploaded!")
                                        startActivity(Intent(this, feed::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                                        finish()
                                    }
                                    .addOnFailureListener { e -> setBusy(false); toast("DB save failed: ${e.message}") }
                            }
                        }
                        .addOnFailureListener { e -> setBusy(false); toast("Upload failed: ${e.message}") }
                } else {
                    val bm = loadDownsampledBitmap(uri, maxDim = 720) ?: run { setBusy(false); toast("Could not read image"); return@addOnSuccessListener }
                    val baos = ByteArrayOutputStream()
                    bm.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    val bytes = baos.toByteArray()
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                    val story = Storysave(
                        storyId = storyId,
                        ownerId = uid,
                        ownerName = username,
                        mediaBase64 = b64,
                        mediaType = "image",
                        createdAt = now,
                        expiresAt = now + 24L * 60 * 60 * 1000
                    )
                    val updates = hashMapOf<String, Any>(
                        "storiesPublic/$storyId" to story,
                        "userStories/$uid/$storyId" to true
                    )
                    db.updateChildren(updates)
                        .addOnSuccessListener {
                            // Notify followers + self in YOU tab
                            createStoryAddedNotifications(uid, username, storyId)
                            setBusy(false)
                            toast("Story uploaded!")
                            startActivity(Intent(this, feed::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                            finish()
                        }
                        .addOnFailureListener { e -> setBusy(false); toast("DB save failed: ${e.message}") }
                }
            }
            .addOnFailureListener {
                // Fallback if Firebase query fails
                val username = "User"
                val db = FirebaseDatabase.getInstance().reference

                if (pickedType == "video") {
                    val storage = FirebaseStorage.getInstance().reference
                    val videoRef = storage.child("story_videos/${storyId}.mp4")
                    videoRef.putFile(uri)
                        .addOnSuccessListener {
                            videoRef.downloadUrl.addOnSuccessListener { url ->
                                val story = Storysave(
                                    storyId = storyId,
                                    ownerId = uid,
                                    ownerName = username,
                                    mediaUrl = url.toString(),
                                    mediaType = "video",
                                    createdAt = now,
                                    expiresAt = now + 24L * 60 * 60 * 1000
                                )
                                val updates = hashMapOf<String, Any>(
                                    "storiesPublic/$storyId" to story,
                                    "userStories/$uid/$storyId" to true
                                )
                                db.updateChildren(updates)
                                    .addOnSuccessListener {
                                        setBusy(false)
                                        toast("Story uploaded!")
                                        startActivity(Intent(this, feed::class.java)
                                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                                        finish()
                                    }
                                    .addOnFailureListener { e -> setBusy(false); toast("DB save failed: ${e.message}") }
                            }
                        }
                        .addOnFailureListener { e -> setBusy(false); toast("Upload failed: ${e.message}") }
                } else {
                    val bm = loadDownsampledBitmap(uri, maxDim = 720) ?: run { setBusy(false); toast("Could not read image"); return@addOnFailureListener }
                    val baos = ByteArrayOutputStream()
                    bm.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    val bytes = baos.toByteArray()
                    val b64Local = Base64.encodeToString(bytes, Base64.NO_WRAP)

                    val story = Storysave(
                        storyId = storyId,
                        ownerId = uid,
                        ownerName = username,
                        mediaBase64 = b64Local,
                        mediaType = "image",
                        createdAt = now,
                        expiresAt = now + 24L * 60 * 60 * 1000
                    )
                    val updates = hashMapOf<String, Any>(
                        "storiesPublic/$storyId" to story,
                        "userStories/$uid/$storyId" to true
                    )
                    db.updateChildren(updates)
                        .addOnSuccessListener {
                            // Notify followers + self in YOU tab
                            createStoryAddedNotifications(uid, username, storyId)
                            setBusy(false)
                            toast("Story uploaded!")
                            startActivity(Intent(this, feed::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                            finish()
                        }
                        .addOnFailureListener { e -> setBusy(false); toast("DB save failed: ${e.message}") }
                }
            }
    }

    private fun loadDownsampledBitmap(uri: Uri, maxDim: Int): Bitmap? {
        val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, probe)
        }
        val w = probe.outWidth
        val h = probe.outHeight
        if (w <= 0 || h <= 0) return null

        var sample = 1
        var cw = w
        var ch = h
        while (cw > maxDim || ch > maxDim) {
            sample *= 2
            cw /= 2
            ch /= 2
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        contentResolver.openInputStream(uri)?.use { inS ->
            return BitmapFactory.decodeStream(inS, null, opts)
        }
        return null
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        btnUpload.isEnabled = !busy
        btnPickImage.isEnabled = !busy
        btnBack.isEnabled = !busy
        btnPickVideo.isEnabled = !busy
    }

    private fun toast(m: String) =
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    // Create YOU-tab notifications for followers and for the uploader
    private fun createStoryAddedNotifications(ownerUid: String, ownerName: String, storyId: String) {
        val db = FirebaseDatabase.getInstance().reference
        // Notify followers
        db.child("followers").child(ownerUid).get().addOnSuccessListener { snap ->
            snap.children.forEach { follower ->
                val followerId = follower.key ?: return@forEach
                val notifRef = db.child("notifications").child(followerId).push()
                val data = hashMapOf<String, Any>(
                    "notificationId" to (notifRef.key ?: ""),
                    "fromUserId" to ownerUid,
                    "fromUsername" to ownerName,
                    "fromFullName" to ownerName,
                    "action" to "added a story",
                    "type" to "story_added",
                    "timestamp" to System.currentTimeMillis(),
                    "read" to false,
                    "story_id" to storyId
                )
                notifRef.setValue(data)
            }
        }
        // Also add a self notification if desired (so user sees it in THEIR You tab)
        val selfRef = db.child("notifications").child(ownerUid).push()
        val selfData = hashMapOf<String, Any>(
            "notificationId" to (selfRef.key ?: ""),
            "fromUserId" to ownerUid,
            "fromUsername" to ownerName,
            "fromFullName" to ownerName,
            "action" to "You added a story",
            "type" to "story_added",
            "timestamp" to System.currentTimeMillis(),
            "read" to false,
            "story_id" to storyId
        )
        selfRef.setValue(selfData)
    }
}

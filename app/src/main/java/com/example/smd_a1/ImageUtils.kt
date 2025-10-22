package com.example.smd_a1

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey

object ImageUtils {
    /**
     * Load profile picture from Firebase Storage URL or show default
     */
    fun loadProfilePicture(imageView: ImageView, photoUrl: String?, defaultResId: Int = R.drawable.profile_pic) {
        if (!photoUrl.isNullOrEmpty()) {
            Glide.with(imageView.context)
                .load(photoUrl)
                .placeholder(defaultResId)
                .error(defaultResId)
                .circleCrop()
                .into(imageView)
        } else {
            imageView.setImageResource(defaultResId)
        }
    }
    
    /**
     * Load profile picture with custom placeholder and error images
     */
    fun loadProfilePicture(
        imageView: ImageView, 
        photoUrl: String?, 
        placeholderResId: Int = R.drawable.profile_pic,
        errorResId: Int = R.drawable.profile_pic,
        useCircleCrop: Boolean = true
    ) {
        if (!photoUrl.isNullOrEmpty()) {
            val requestBuilder = Glide.with(imageView.context)
                .load(photoUrl)
                .placeholder(placeholderResId)
                .error(errorResId)
            
            if (useCircleCrop) {
                requestBuilder.circleCrop()
            }
            
            requestBuilder.into(imageView)
        } else {
            imageView.setImageResource(errorResId)
        }
    }

    /**
     * Load profile picture from URL, or fall back to Base64 if URL is missing.
     */
    fun loadProfilePictureUrlOrBase64(
        imageView: ImageView,
        photoUrl: String?,
        photoBase64: String?,
        defaultResId: Int = R.drawable.profile_pic,
        lastUpdatedMs: Long? = null
    ) {
        if (!photoUrl.isNullOrBlank()) {
            val req = Glide.with(imageView.context)
                .load(photoUrl)
                .placeholder(defaultResId)
                .error(defaultResId)
                .circleCrop()
            if (lastUpdatedMs != null) {
                req.signature(ObjectKey(lastUpdatedMs.toString()))
            }
            req.into(imageView)
            return
        }
        if (!photoBase64.isNullOrBlank()) {
            try {
                val bytes = android.util.Base64.decode(photoBase64, android.util.Base64.NO_WRAP)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                imageView.setImageBitmap(bmp)
            } catch (_: Exception) {
                imageView.setImageResource(defaultResId)
            }
            return
        }
        imageView.setImageResource(defaultResId)
    }
}


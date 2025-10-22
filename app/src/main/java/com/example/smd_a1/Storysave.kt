package com.example.smd_a1

data class Storysave(
    var storyId: String = "",
    var ownerId: String = "",
    var ownerName: String? = null,
    var mediaBase64: String? = null,
    var mediaUrl: String? = null,
    var mediaType: String? = null,
    var createdAt: Long = 0L,
    var expiresAt: Long = 0L
)
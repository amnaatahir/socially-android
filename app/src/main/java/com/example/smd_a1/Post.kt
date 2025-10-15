package com.example.smd_a1

data class Post(
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val mediaBase64: String = "",
    val mediaUrl: String = "",
    val caption: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val isLiked: Boolean = false
)

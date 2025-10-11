package com.example.smd_a1

// Firebase-mapped user profile model
data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val status: String = "offline",
    val photoUrl: String = ""
)



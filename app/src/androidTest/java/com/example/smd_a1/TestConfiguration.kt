package com.example.smd_a1

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import org.junit.BeforeClass

/**
 * Test configuration and utilities for Espresso tests
 */
object TestConfiguration {
    
    private var isInitialized = false
    
    @BeforeClass
    @JvmStatic
    fun initializeFirebase() {
        if (!isInitialized) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            FirebaseApp.initializeApp(context)
            isInitialized = true
        }
    }
    
    /**
     * Get test user credentials
     */
    fun getTestUserCredentials(): Pair<String, String> {
        val timestamp = System.currentTimeMillis()
        return Pair(
            "testuser$timestamp@example.com",
            "testpassword123"
        )
    }
    
    /**
     * Clean up test data
     */
    fun cleanupTestData(userId: String) {
        try {
            val database = FirebaseDatabase.getInstance()
            database.getReference("users").child(userId).removeValue()
            database.getReference("posts").orderByChild("authorId").equalTo(userId)
                .addListenerForSingleValueEvent { snapshot ->
                    snapshot.children.forEach { child ->
                        child.ref.removeValue()
                    }
                }
        } catch (e: Exception) {
            // Cleanup failed, but test should continue
        }
    }
    
    /**
     * Wait for Firebase operations to complete
     */
    fun waitForFirebaseOperation(timeoutMs: Long = 5000) {
        Thread.sleep(timeoutMs)
    }
    
    /**
     * Sign out current user
     */
    fun signOutCurrentUser() {
        try {
            FirebaseAuth.getInstance().signOut()
            Thread.sleep(1000)
        } catch (e: Exception) {
            // Sign out failed, but test should continue
        }
    }
}

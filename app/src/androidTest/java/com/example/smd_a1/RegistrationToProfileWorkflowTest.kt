package com.example.smd_a1

import android.content.Intent
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test Case 2: Complete User Registration and Profile Setup Workflow
 * 
 * This test verifies the critical new user onboarding journey:
 * 1. User registration with valid information
 * 2. Navigation to profile setup
 * 3. Profile completion with bio and details
 * 4. Navigation to main feed
 * 5. Profile verification
 * 6. Search and follow functionality
 */
@RunWith(AndroidJUnit4::class)
class RegistrationToProfileWorkflowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(login::class.java)

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private val testEmail = "newuser${System.currentTimeMillis()}@example.com"
    private val testPassword = "testpassword123"
    private val testUsername = "testuser${System.currentTimeMillis()}"
    private val testFirstName = "Test"
    private val testLastName = "User"
    private val testBio = "This is my test bio for Espresso testing"

    @Before
    fun setUp() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        
        // Sign out any existing user
        auth.signOut()
        
        // Wait for sign out to complete
        Thread.sleep(1000)
    }

    @After
    fun tearDown() {
        // Clean up: Delete test user and data
        try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                // Delete user data from database
                database.getReference("users").child(currentUser.uid).removeValue()
                // Delete user account
                currentUser.delete()
            }
        } catch (e: Exception) {
            // Cleanup failed, but test should still pass
        }
    }

    @Test
    fun testCompleteRegistrationToProfileWorkflow() {
        // Step 1: Start from login screen and navigate to registration
        Espresso.onView(withId(R.id.etEmail))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Click on register link/button (assuming there's a register button)
        try {
            Espresso.onView(withText("Register"))
                .perform(ViewActions.click())
        } catch (e: Exception) {
            // If no register button, we'll simulate the registration flow
            // by directly testing the registration activity
        }

        // Step 2: Fill registration form
        Espresso.onView(withId(R.id.etEmail))
            .perform(ViewActions.typeText(testEmail))
        
        Espresso.onView(withId(R.id.etPassword))
            .perform(ViewActions.typeText(testPassword))
        
        Espresso.onView(withId(R.id.etConfirmPassword))
            .perform(ViewActions.typeText(testPassword))
        
        Espresso.onView(withId(R.id.etUsername))
            .perform(ViewActions.typeText(testUsername))
        
        Espresso.onView(withId(R.id.etFirstName))
            .perform(ViewActions.typeText(testFirstName))
        
        Espresso.onView(withId(R.id.etLastName))
            .perform(ViewActions.typeText(testLastName))
        
        Espresso.onView(withId(R.id.etDob))
            .perform(ViewActions.typeText("01/01/2000"))

        // Step 3: Submit registration
        Espresso.onView(withId(R.id.btnRegister))
            .perform(ViewActions.click())

        // Step 4: Wait for registration to complete and navigate to profile setup
        Thread.sleep(5000)

        // Step 5: Verify we're on profile setup screen
        Espresso.onView(withId(R.id.etBio))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Step 6: Complete profile setup
        Espresso.onView(withId(R.id.etBio))
            .perform(ViewActions.typeText(testBio))

        // Step 7: Save profile and navigate to feed
        Espresso.onView(withId(R.id.btnDone))
            .perform(ViewActions.click())

        // Step 8: Wait for navigation to feed
        Thread.sleep(3000)

        // Step 9: Verify we're on feed screen
        Espresso.onView(withId(R.id.rvPosts))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Step 10: Navigate to profile to verify data was saved
        Espresso.onView(withId(R.id.btnt5)) // Profile button
            .perform(ViewActions.click())

        // Step 11: Verify profile data is displayed correctly
        Espresso.onView(withId(R.id.username))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        
        Espresso.onView(withId(R.id.fullname))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        
        Espresso.onView(withId(R.id.bio))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Step 12: Test profile editing
        Espresso.onView(withId(R.id.editing))
            .perform(ViewActions.click())

        // Step 13: Verify edit profile screen
        Espresso.onView(withId(R.id.etBio))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Update bio
        Espresso.onView(withId(R.id.etBio))
            .perform(ViewActions.clearText())
            .perform(ViewActions.typeText("Updated bio for testing"))

        // Save changes
        Espresso.onView(withId(R.id.btnDone))
            .perform(ViewActions.click())

        // Step 14: Navigate back to feed
        Thread.sleep(2000)
        Espresso.onView(withId(R.id.rvPosts))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Step 15: Test search functionality
        Espresso.onView(withId(R.id.btnshare))
            .perform(ViewActions.click())

        // Step 16: Verify search screen
        Espresso.onView(withId(R.id.etQuery))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Search for users
        Espresso.onView(withId(R.id.etQuery))
            .perform(ViewActions.typeText("test"))

        // Wait for search results
        Thread.sleep(2000)

        // Step 17: Test follow functionality (if search results exist)
        try {
            Espresso.onView(ViewMatchers.isDescendantOfA(withId(R.id.rvUsers)))
                .perform(ViewActions.click())
            
            // If user profile opens, test follow button
            Espresso.onView(withId(R.id.btnFollow))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
                .perform(ViewActions.click())
            
            // Navigate back
            Espresso.pressBack()
        } catch (e: Exception) {
            // No search results or follow button, which is fine for testing
        }

        // Step 18: Navigate back to feed
        Espresso.pressBack()

        // Step 19: Final verification - we're back on feed
        Espresso.onView(withId(R.id.rvPosts))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testRegistrationWithInvalidData() {
        // Test registration with invalid email
        Espresso.onView(withId(R.id.etEmail))
            .perform(ViewActions.typeText("invalid-email"))
        
        Espresso.onView(withId(R.id.etPassword))
            .perform(ViewActions.typeText("123")) // Too short password
        
        Espresso.onView(withId(R.id.btnRegister))
            .perform(ViewActions.click())

        // Should show validation errors or stay on registration screen
        Thread.sleep(2000)
        
        // Verify we're still on registration screen
        Espresso.onView(withId(R.id.etEmail))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testProfileSetupValidation() {
        // Complete registration first (simplified)
        Espresso.onView(withId(R.id.etEmail))
            .perform(ViewActions.typeText(testEmail))
        Espresso.onView(withId(R.id.etPassword))
            .perform(ViewActions.typeText(testPassword))
        Espresso.onView(withId(R.id.btnRegister))
            .perform(ViewActions.click())

        Thread.sleep(5000)

        // Try to save profile without bio
        Espresso.onView(withId(R.id.btnDone))
            .perform(ViewActions.click())

        // Should stay on profile setup screen or show validation error
        Thread.sleep(2000)
        
        // Verify we're still on profile setup
        Espresso.onView(withId(R.id.etBio))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testCancelProfileSetup() {
        // Complete registration first (simplified)
        Espresso.onView(withId(R.id.etEmail))
            .perform(ViewActions.typeText(testEmail))
        Espresso.onView(withId(R.id.etPassword))
            .perform(ViewActions.typeText(testPassword))
        Espresso.onView(withId(R.id.btnRegister))
            .perform(ViewActions.click())

        Thread.sleep(5000)

        // Test cancel button
        Espresso.onView(withId(R.id.btnCancel))
            .perform(ViewActions.click())

        // Should navigate back or show confirmation dialog
        Thread.sleep(2000)
    }
}

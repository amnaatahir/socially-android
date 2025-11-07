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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test Case 1: Complete Login to Feed Workflow
 * 
 * This test verifies the critical user journey from login to accessing the main feed.
 * It tests:
 * 1. User login with valid credentials
 * 2. Navigation to feed screen
 * 3. Feed elements are displayed correctly
 * 4. Bottom navigation works
 * 5. User can interact with feed features
 */
@RunWith(AndroidJUnit4::class)
class LoginToFeedWorkflowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(login::class.java)

    private lateinit var auth: FirebaseAuth
    private val testEmail = "test@example.com"
    private val testPassword = "testpassword123"

    @Before
    fun setUp() {
        auth = FirebaseAuth.getInstance()
        
        // Sign out any existing user
        auth.signOut()
        
        // Wait for sign out to complete
        Thread.sleep(1000)
    }

    @After
    fun tearDown() {
        // Clean up: Sign out after test
        auth.signOut()
    }

    @Test
    fun testCompleteLoginToFeedWorkflow() {
        // Step 1: Verify we're on login screen
        Espresso.onView(withId(R.id.etEmail))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(withId(R.id.etPassword))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(withId(R.id.btnLogin))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Step 2: Enter login credentials
        Espresso.onView(withId(R.id.etEmail))
            .perform(ViewActions.typeText(testEmail))
        Espresso.onView(withId(R.id.etPassword))
            .perform(ViewActions.typeText(testPassword))

        // Step 3: Click login button
        Espresso.onView(withId(R.id.btnLogin))
            .perform(ViewActions.click())

        // Step 4: Wait for navigation to feed (with timeout)
        Thread.sleep(3000)

        // Step 5: Verify we're on feed screen
        Espresso.onView(withId(R.id.rvPosts))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(withId(R.id.rvStories))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Step 6: Test bottom navigation
        Espresso.onView(withId(R.id.btnt2)) // Feed button
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
            .perform(ViewActions.click())

        Espresso.onView(withId(R.id.btnt3)) // Gallery button
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
            .perform(ViewActions.click())

        // Navigate back to feed
        Espresso.pressBack()

        Espresso.onView(withId(R.id.btnt4)) // Following button
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
            .perform(ViewActions.click())

        // Navigate back to feed
        Espresso.pressBack()

        Espresso.onView(withId(R.id.btnt5)) // Profile button
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
            .perform(ViewActions.click())

        // Step 7: Verify profile screen loaded
        Espresso.onView(withId(R.id.username))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Navigate back to feed
        Espresso.pressBack()

        // Step 8: Test feed interactions
        Espresso.onView(withId(R.id.btnshare)) // Share button
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
            .perform(ViewActions.click())

        // Verify search screen opened
        Espresso.onView(withId(R.id.etQuery))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Navigate back to feed
        Espresso.pressBack()

        // Step 9: Final verification - we're back on feed
        Espresso.onView(withId(R.id.rvPosts))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testLoginWithInvalidCredentials() {
        // Test with invalid credentials
        Espresso.onView(withId(R.id.etEmail))
            .perform(ViewActions.typeText("invalid@example.com"))
        Espresso.onView(withId(R.id.etPassword))
            .perform(ViewActions.typeText("wrongpassword"))

        Espresso.onView(withId(R.id.btnLogin))
            .perform(ViewActions.click())

        // Wait for error handling
        Thread.sleep(2000)

        // Should still be on login screen (not navigate to feed)
        Espresso.onView(withId(R.id.etEmail))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun testFeedStoriesInteraction() {
        // First login (simplified for this test)
        Espresso.onView(withId(R.id.etEmail))
            .perform(ViewActions.typeText(testEmail))
        Espresso.onView(withId(R.id.etPassword))
            .perform(ViewActions.typeText(testPassword))
        Espresso.onView(withId(R.id.btnLogin))
            .perform(ViewActions.click())

        Thread.sleep(3000)

        // Test stories interaction
        Espresso.onView(withId(R.id.rvStories))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

        // Try to tap on first story (if any exist)
        try {
            Espresso.onView(ViewMatchers.isDescendantOfA(withId(R.id.rvStories)))
                .perform(ViewActions.click())
            
            // If story viewer opens, press back
            Thread.sleep(1000)
            Espresso.pressBack()
        } catch (e: Exception) {
            // No stories available, which is fine for testing
        }
    }
}

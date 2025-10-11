package com.example.smd_a1

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class cred : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cred)
        auth = FirebaseAuth.getInstance()

        val etUserOrEmail = findViewById<EditText>(R.id.etUserOrEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnDoLogin = findViewById<Button>(R.id.btnDoLogin)
        val tvForgot = findViewById<TextView>(R.id.tvForgot)
        val signup= findViewById<TextView>(R.id.tvSignUp)
val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener {
            startActivity(Intent(this, login::class.java))
            finish()
        }
        btnDoLogin.setOnClickListener {
            val userOrEmail = etUserOrEmail.text.toString().trim()
            val pass = etPassword.text.toString()

            if (userOrEmail.isEmpty() || pass.isEmpty()) {
                toast("Enter username/email and password")
                return@setOnClickListener
            }

            // If input contains '@' treat as email; else resolve username → email via DB
            if (userOrEmail.contains("@")) {
                signInWithEmail(userOrEmail, pass)
            } else {
                // usernames/{username} -> uid
                val db = FirebaseDatabase.getInstance().reference
                db.child("usernames").child(userOrEmail).get().addOnSuccessListener { snap ->
                    if (!snap.exists()) {
                        toast("Username not found")
                        return@addOnSuccessListener
                    }
                    val uid = snap.value as String
                    db.child("users").child(uid).child("email").get().addOnSuccessListener { e ->
                        val email = e.value as? String ?: ""
                        if (email.isEmpty()) {
                            toast("No email for this user")
                        } else {
                            signInWithEmail(email, pass)
                        }
                    }
                }.addOnFailureListener { toast(it.message ?: "Error") }
            }
        }

        tvForgot.setOnClickListener {
            val email = etUserOrEmail.text.toString().trim()
            if (email.contains("@")) {
                auth.sendPasswordResetEmail(email)
                    .addOnSuccessListener { toast("Reset link sent") }
                    .addOnFailureListener { toast(it.message ?: "Failed") }
            } else {
                toast("Enter your email to reset")
            }
        }

        signup.setOnClickListener {
            startActivity(Intent(this, register::class.java))
            finish()
        }
    }

    private fun signInWithEmail(email: String, pass: String) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                startActivity(Intent(this, feed::class.java))
                finish()
            }
            .addOnFailureListener { toast(it.message ?: "Login failed") }
    }


    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

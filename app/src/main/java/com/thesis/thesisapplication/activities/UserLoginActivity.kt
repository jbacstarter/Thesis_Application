    package com.thesis.thesisapplication.activities

    import android.content.ContentValues.TAG
    import android.content.Intent
    import android.os.Bundle
    import android.os.Handler
    import android.os.Looper
    import android.util.Log
    import android.widget.EditText
    import androidx.activity.enableEdgeToEdge
    import androidx.appcompat.app.AppCompatActivity
    import androidx.core.view.ViewCompat
    import androidx.core.view.WindowInsetsCompat
    import com.google.android.material.button.MaterialButton
    import com.google.firebase.auth.FirebaseAuth
    import com.google.firebase.firestore.FirebaseFirestore
    import com.thesis.thesisapplication.R
    import com.thesis.thesisapplication.helpers.ParkingView
    import com.thesis.thesisapplication.helpers.hideLoading
    import com.thesis.thesisapplication.helpers.showCustomNotification
    import com.thesis.thesisapplication.helpers.showLoading

    class UserLoginActivity : AppCompatActivity() {

        private val auth = FirebaseAuth.getInstance()
        private val db = FirebaseFirestore.getInstance()

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            enableEdgeToEdge()
            setContentView(R.layout.activity_login)

            val rootView = findViewById<android.view.View>(android.R.id.content)
            ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            val loginButton = findViewById<MaterialButton>(R.id.login_button)

            loginButton.setOnClickListener {
                val emailComp = findViewById<EditText>(R.id.login_input_email)
                val passwordComp = findViewById<EditText>(R.id.login_input_password)

                val emailText = emailComp.text.toString().trim()
                val passwordText = passwordComp.text.toString().trim()

                if (emailText.isEmpty() || passwordText.isEmpty()) {
                    showCustomNotification("Please enter email and password", "warning")
                    return@setOnClickListener
                }

                loginButton.isEnabled = false
                showLoading("Signing in...")

                auth.signInWithEmailAndPassword(emailText, passwordText)
                    .addOnCompleteListener(this) { task ->

                        if (task.isSuccessful) {
                            val currentUserId = auth.currentUser?.uid

                            if (currentUserId != null) {
                                db.collection("Users").document(currentUserId).get()
                                    .addOnSuccessListener { document ->
                                        hideLoading()

                                        if (document.exists()) {
                                            // 1. Get the user's role and make it lowercase to avoid spelling bugs (Admin vs admin)
                                            val userRole =
                                                document.getString("role")?.lowercase() ?: "student"

                                            // 2. Route them based on their role
                                            if (userRole == "admin") {
                                                Log.d(TAG, "signInWithEmail:success:admin")
                                                showCustomNotification(
                                                    "Admin Access Granted",
                                                    "success"
                                                )

                                                Handler(Looper.getMainLooper()).postDelayed({
                                                    if (!isDestroyed && !isFinishing) {
                                                        val intent = Intent(
                                                            this@UserLoginActivity,
                                                            AdminDashboardActivity::class.java
                                                        )
                                                        startActivity(intent)
                                                        finish()
                                                    }
                                                }, 1200)

                                            } else {
                                                // They are a Student (or driver/other)
                                                Log.d(TAG, "signInWithEmail:success:student")
                                                showCustomNotification("Login Successful", "success")

                                                Handler(Looper.getMainLooper()).postDelayed({
                                                    if (!isDestroyed && !isFinishing) {
                                                        // NOTE: Change Navigation::class.java to whatever your Student Home Screen is!
                                                        val intent = Intent(
                                                            this@UserLoginActivity,
                                                            ParkingViewSelectorActivity::class.java
                                                        )
                                                        startActivity(intent)
                                                        finish()
                                                    }
                                                }, 1200)
                                            }

                                        } else {
                                            auth.signOut()
                                            showCustomNotification(
                                                "This account has been disabled or deleted.",
                                                "error"
                                            )
                                            loginButton.isEnabled = true
                                        }
                                    }
                                    .addOnFailureListener {
                                        hideLoading()
                                        auth.signOut()
                                        showCustomNotification(
                                            "Database error. Please try again.",
                                            "error"
                                        )
                                        loginButton.isEnabled = true
                                    }
                            }
                        } else {
                            hideLoading()
                            Log.w(TAG, "signInWithEmail:failure", task.exception)
                            val errorMessage = task.exception?.localizedMessage ?: "Login failed"
                            showCustomNotification(errorMessage, "error")
                            loginButton.isEnabled = true
                        }
                    }
            }
        }

        public override fun onStart() {
            super.onStart()

            // 1. Get the actual user from Firebase, not a hardcoded null
            val user = auth.currentUser

            // 2. Check if the user is logged in
            if (user != null) {
                // 3. Now 'user' is safe to use, and 'user.uid' will work perfectly!
                db.collection("Users").document(user.uid).get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val userRole = document.getString("role")?.lowercase() ?: "student"

                            if (userRole == "admin") {
                                val intent = Intent(this, AdminDashboardActivity::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                val intent = Intent(this, ParkingViewSelectorActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                        } else {
                            // User exists in Auth but not in Database (Deleted)
                            auth.signOut()
                        }
                    }
                    .addOnFailureListener {
                        // Database error during auto-login
                        showCustomNotification("Connection error. Please log in again.", "error")
                    }
            }
            // If user == null, the app simply stays on the Login screen, which is exactly what we want!
        }
    }
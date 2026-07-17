package com.thesis.thesisapplication.activities

import android.os.Bundle
import android.os.Handler // ADDED
import android.os.Looper // ADDED
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.thesis.thesisapplication.R
import com.thesis.thesisapplication.helpers.showCustomNotification
import com.thesis.thesisapplication.helpers.showLoading
import com.thesis.thesisapplication.helpers.hideLoading

class AdminCreateUserActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var secondaryAuth: FirebaseAuth? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_create_user)

        val options = FirebaseApp.getInstance().options
        try {
            val secondaryApp = FirebaseApp.getInstance("SecondaryApp")
            secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
        } catch (e: IllegalStateException) {
            val secondaryApp = FirebaseApp.initializeApp(this, options, "SecondaryApp")
            secondaryAuth = FirebaseAuth.getInstance(secondaryApp!!)
        }

        val backBtn = findViewById<TextView>(R.id.btn_back_create)
        val nameInput = findViewById<EditText>(R.id.create_input_name)
        val emailInput = findViewById<EditText>(R.id.create_input_email)
        val passwordInput = findViewById<EditText>(R.id.create_input_password)
        val roleSpinner = findViewById<Spinner>(R.id.create_spinner_role)
        val createBtn = findViewById<MaterialButton>(R.id.btn_create_account)

        val roles = arrayOf("Student", "Admin")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        roleSpinner.adapter = spinnerAdapter

        backBtn.setOnClickListener { finish() }

        createBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val role = roleSpinner.selectedItem.toString()

            if (name.isEmpty() || email.isEmpty() || password.length < 6) {
                showCustomNotification("Please fill all fields (Password min 6 chars)", "warning")
                return@setOnClickListener
            }

            createNewUser(name, email, password, role)
        }
    }

    private fun createNewUser(name: String, email: String, password: String, role: String) {
        showLoading("Creating account...")

        secondaryAuth?.createUserWithEmailAndPassword(email, password)
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val newUserId = task.result?.user?.uid

                    if (newUserId != null) {
                        saveProfileToFirestore(newUserId, name, email, role)
                    } else {
                        hideLoading()
                        showCustomNotification("Error retrieving new User ID", "error")
                    }
                } else {
                    hideLoading()
                    val error = task.exception?.localizedMessage ?: "Failed to create Auth account"
                    showCustomNotification(error, "error")
                }
            }
    }

    private fun saveProfileToFirestore(uid: String, name: String, email: String, role: String) {
        val userProfile = hashMapOf(
            "uid" to uid,
            "fullName" to name,
            "email" to email,
            "role" to role,
            "profileImageUrl" to ""
        )

        db.collection("Users").document(uid).set(userProfile)
            .addOnSuccessListener {
                hideLoading()
                showCustomNotification("User created successfully!", "success")
                secondaryAuth?.signOut()

                // FIXED: Give the notification 1.2 seconds to display before destroying the screen!
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isDestroyed && !isFinishing) {
                        finish()
                    }
                }, 1200)
            }
            .addOnFailureListener { e ->
                hideLoading()
                showCustomNotification("Database Error: ${e.localizedMessage}", "error")
            }
    }
}
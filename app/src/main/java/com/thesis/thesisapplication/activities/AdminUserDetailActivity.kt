package com.thesis.thesisapplication.activities

import android.os.Bundle
import android.os.Handler // ADDED
import android.os.Looper // ADDED
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.thesis.thesisapplication.R
import com.thesis.thesisapplication.helpers.showCustomNotification
import com.thesis.thesisapplication.helpers.showLoading
import com.thesis.thesisapplication.helpers.hideLoading

class AdminUserDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_user_detail)

        val backBtn = findViewById<TextView>(R.id.btn_back)
        val nameText = findViewById<TextView>(R.id.detail_name)
        val emailText = findViewById<TextView>(R.id.detail_email)
        val avatarView = findViewById<ImageView>(R.id.detail_avatar)
        val roleSpinner = findViewById<Spinner>(R.id.spinner_role)
        val saveBtn = findViewById<MaterialButton>(R.id.btn_save_changes)
        val deleteBtn = findViewById<MaterialButton>(R.id.btn_delete_user)

        val roles = arrayOf("Student", "Admin")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        roleSpinner.adapter = spinnerAdapter

        userId = intent.getStringExtra("USER_ID")
        val currentName = intent.getStringExtra("USER_NAME") ?: "Unknown User"
        val currentEmail = intent.getStringExtra("USER_EMAIL") ?: "No Email"
        val currentRole = intent.getStringExtra("USER_ROLE") ?: "Student"
        val imageUrl = intent.getStringExtra("USER_IMAGE") ?: ""

        nameText.text = currentName
        emailText.text = currentEmail
        roleSpinner.setSelection(roles.indexOf(currentRole))

        if (imageUrl.isNotEmpty()) {
            avatarView.imageTintList = null
            Glide.with(this).load(imageUrl).centerCrop().into(avatarView)
        }

        backBtn.setOnClickListener { finish() }

        saveBtn.setOnClickListener {
            val newRole = roleSpinner.selectedItem.toString()
            updateUserRoleInFirestore(newRole)
        }

        deleteBtn.setOnClickListener {
            deleteUserFromFirestore()
        }
    }

    private fun updateUserRoleInFirestore(newRole: String) {
        if (userId == null) return

        showLoading("Updating role...")

        db.collection("Users").document(userId!!)
            .update("role", newRole)
            .addOnSuccessListener {
                hideLoading()
                showCustomNotification("Role updated successfully", "success")

                // FIXED: Safe delay
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isDestroyed && !isFinishing) finish()
                }, 1200)
            }
            .addOnFailureListener { e ->
                hideLoading()
                showCustomNotification("Failed to update: ${e.message}", "error")
            }
    }

    private fun deleteUserFromFirestore() {
        if (userId == null) return

        showLoading("Deleting user...")

        db.collection("Users").document(userId!!)
            .delete()
            .addOnSuccessListener {
                hideLoading()
                showCustomNotification("User deleted from database", "success")

                // FIXED: Safe delay
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isDestroyed && !isFinishing) finish()
                }, 1200)
            }
            .addOnFailureListener { e ->
                hideLoading()
                showCustomNotification("Failed to delete: ${e.message}", "error")
            }
    }
}
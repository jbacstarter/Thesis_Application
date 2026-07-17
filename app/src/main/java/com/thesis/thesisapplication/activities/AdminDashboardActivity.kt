package com.thesis.thesisapplication.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView // ADDED
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth // ADDED
import com.google.firebase.firestore.FirebaseFirestore
import com.thesis.thesisapplication.R
import com.thesis.thesisapplication.helpers.UserAdapter
import com.thesis.thesisapplication.models.User
import com.thesis.thesisapplication.helpers.showCustomNotification
import com.thesis.thesisapplication.helpers.showLoading
import com.thesis.thesisapplication.helpers.hideLoading

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var userAdapter: UserAdapter
    private var allUsersList = mutableListOf<User>()
    private var filteredList = mutableListOf<User>()

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_admin_dashboard)

        val rootView = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val searchBar = findViewById<EditText>(R.id.search_bar_input)
        val recyclerView = findViewById<RecyclerView>(R.id.users_recycler_view)
        val fabCreateUser = findViewById<FloatingActionButton>(R.id.fab_create_user)
        val btnLogout = findViewById<ImageView>(R.id.btn_logout) // NEW

        userAdapter = UserAdapter(filteredList)
        recyclerView.adapter = userAdapter

        // --- LOGOUT LOGIC ---
        btnLogout.setOnClickListener {
            // 1. Tell Firebase to log out
            FirebaseAuth.getInstance().signOut()

            // 2. Go back to Login Screen
            val intent = Intent(this, UserLoginActivity::class.java)
            // 3. Clear the app history so the physical back button doesn't bring them back here!
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        fabCreateUser.setOnClickListener {
            val intent = Intent(this, AdminCreateUserActivity::class.java)
            startActivity(intent)
        }

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterUsers(s.toString())
            }
        })
    }

    override fun onResume() {
        super.onResume()
        findViewById<EditText>(R.id.search_bar_input).text.clear()
        loadUsersFromFirestore()
    }

    private fun loadUsersFromFirestore() {
        showLoading("Fetching users...")

        db.collection("Users")
            .get()
            .addOnSuccessListener { result ->
                hideLoading()
                allUsersList.clear()

                for (document in result) {
                    val uid = document.id
                    val name = document.getString("fullName") ?: "Unknown"
                    val email = document.getString("email") ?: "No Email"
                    val role = document.getString("role") ?: "Student"
                    val image = document.getString("profileImageUrl") ?: ""

                    allUsersList.add(User(uid, name, email, role, image))
                }

                allUsersList.sortBy { it.fullName }
                filteredList.clear()
                filteredList.addAll(allUsersList)
                userAdapter.updateList(filteredList)
            }
            .addOnFailureListener { exception ->
                hideLoading()
                showCustomNotification("Error loading users: ${exception.message}", "error")
            }
    }

    private fun filterUsers(query: String) {
        val lowerCaseQuery = query.lowercase()
        val newList = if (lowerCaseQuery.isEmpty()) {
            allUsersList
        } else {
            allUsersList.filter {
                it.fullName.lowercase().contains(lowerCaseQuery) ||
                        it.email.lowercase().contains(lowerCaseQuery)
            }
        }
        userAdapter.updateList(newList)
    }
}
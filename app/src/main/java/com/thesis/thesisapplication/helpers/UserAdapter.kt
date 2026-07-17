package com.thesis.thesisapplication.helpers

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.thesis.thesisapplication.R
import com.thesis.thesisapplication.activities.AdminUserDetailActivity // We will build this next!
import com.thesis.thesisapplication.models.User

class UserAdapter(private var userList: List<User>) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textName: TextView = itemView.findViewById(R.id.user_name)
        val textEmail: TextView = itemView.findViewById(R.id.user_email)
        val textRole: TextView = itemView.findViewById(R.id.user_role)
        val avatarView: ImageView = itemView.findViewById(R.id.user_avatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_card, parent, false)
        return UserViewHolder(view)
    }

    override fun getItemCount(): Int {
        return userList.size
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val selectedUser = userList[position]

        holder.textName.text = selectedUser.fullName
        holder.textEmail.text = selectedUser.email
        holder.textRole.text = selectedUser.role.uppercase()

        if (selectedUser.profileImageUrl.isNotEmpty()) {
            holder.avatarView.imageTintList = null
            holder.avatarView.colorFilter = null
            Glide.with(holder.itemView.context)
                .load(selectedUser.profileImageUrl)
                .centerCrop()
                .into(holder.avatarView)
        } else {
            holder.avatarView.setImageResource(android.R.drawable.ic_menu_myplaces)
            holder.avatarView.setColorFilter(Color.parseColor("#CBD5E1"))
        }

        // --- NEW: THE CLICK LISTENER ---
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, AdminUserDetailActivity::class.java)
            // Pass the user data to the next screen
            intent.putExtra("USER_ID", selectedUser.uid)
            intent.putExtra("USER_NAME", selectedUser.fullName)
            intent.putExtra("USER_EMAIL", selectedUser.email)
            intent.putExtra("USER_ROLE", selectedUser.role)
            intent.putExtra("USER_IMAGE", selectedUser.profileImageUrl)
            context.startActivity(intent)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<User>) {
        userList = newList
        notifyDataSetChanged()
    }
}
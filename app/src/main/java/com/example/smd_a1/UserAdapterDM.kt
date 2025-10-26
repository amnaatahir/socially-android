package com.example.smd_a1

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smd_a1.R
import com.example.smd_a1.UserProfile



class UserAdapterDM(
    private val list: MutableList<UserProfile>,
    private val onClick: (UserProfile) -> Unit
) : RecyclerView.Adapter<UserAdapterDM.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProfile: ImageView = itemView.findViewById(R.id.ivProfile)
        val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        val tvSub: TextView = itemView.findViewById(R.id.tvSub)
        val onlineDot: View = itemView.findViewById(R.id.onlineDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_dm_user, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = list[position]
        holder.tvUsername.text = u.username
        holder.tvSub.text = "${u.firstName} ${u.lastName}" // placeholder

        // load profile pic (fallback to default)
        holder.ivProfile.setImageResource(R.drawable.oval)

        // online dot handling
        if (u.status.equals("online", true)) {
            holder.onlineDot.visibility = View.VISIBLE
            holder.onlineDot.setBackgroundColor(Color.parseColor("#4CAF50"))
        } else {
            holder.onlineDot.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(u) }
    }

    override fun getItemCount(): Int = list.size

    fun update(newList: List<UserProfile>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }
}
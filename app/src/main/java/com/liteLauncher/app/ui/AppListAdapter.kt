package com.liteLauncher.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.liteLauncher.app.R
import com.liteLauncher.app.data.AppInfo
import com.liteLauncher.app.data.AppRepository

class AppListAdapter(
    private val repository: AppRepository,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo, View) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private var fullList: List<AppInfo> = emptyList()
    private var shownList: List<AppInfo> = emptyList()

    fun submitList(apps: List<AppInfo>) {
        fullList = apps
        shownList = apps
        notifyDataSetChanged()
    }

    /** Filters the visible list without re-querying PackageManager */
    fun filter(query: String) {
        shownList = if (query.isBlank()) {
            fullList
        } else {
            fullList.filter { it.label.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val label: TextView = view.findViewById(R.id.appLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = shownList[position]
        holder.label.text = app.label
        holder.icon.setImageDrawable(repository.getIcon(app)) // served from LruCache after first load
        holder.itemView.setOnClickListener { onAppClick(app) }
        holder.itemView.setOnLongClickListener {
            onAppLongClick(app, holder.itemView)
            true
        }
    }

    override fun getItemCount(): Int = shownList.size
}

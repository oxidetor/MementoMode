package com.example.appblocker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.R
import com.example.appblocker.models.BlockableApp

class BlockableAppAdapter(
    private val onAppSelected: (BlockableApp, Boolean) -> Unit
) : RecyclerView.Adapter<BlockableAppAdapter.ViewHolder>() {

    private val apps = mutableListOf<BlockableApp>()

    fun updateApps(newApps: List<BlockableApp>) {
        apps.clear()
        apps.addAll(newApps)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blockable_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.bind(app)
    }

    override fun getItemCount(): Int = apps.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val appCheckbox: CheckBox = itemView.findViewById(R.id.appCheckbox)

        fun bind(app: BlockableApp) {
            appIcon.setImageDrawable(app.icon)
            appName.text = app.name
            appCheckbox.isChecked = app.isSelected

            appCheckbox.setOnCheckedChangeListener { _, isChecked ->
                app.isSelected = isChecked
                onAppSelected(app, isChecked)
            }

            itemView.setOnClickListener {
                appCheckbox.isChecked = !appCheckbox.isChecked
            }
        }
    }
} 
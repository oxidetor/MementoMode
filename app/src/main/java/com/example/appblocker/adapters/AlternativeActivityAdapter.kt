package com.example.appblocker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.R
import com.example.appblocker.models.AlternativeActivity

class AlternativeActivityAdapter(
    private val onActivitySelected: (AlternativeActivity, Boolean) -> Unit
) : RecyclerView.Adapter<AlternativeActivityAdapter.ViewHolder>() {

    private val activities = mutableListOf<AlternativeActivity>()

    fun updateActivities(newActivities: List<AlternativeActivity>) {
        activities.clear()
        activities.addAll(newActivities)
        notifyDataSetChanged()
    }

    fun getActivity(position: Int): AlternativeActivity {
        return activities[position]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alternative_activity, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val activity = activities[position]
        holder.bind(activity)
    }

    override fun getItemCount(): Int = activities.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkbox: CheckBox = itemView.findViewById(R.id.activityCheckbox)

        fun bind(activity: AlternativeActivity) {
            checkbox.text = activity.name
            checkbox.isChecked = activity.isSelected
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                activity.isSelected = isChecked
                onActivitySelected(activity, isChecked)
            }
        }
    }
} 
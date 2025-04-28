package com.example.appblocker.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.R

class AiCoachSetupAdapter : RecyclerView.Adapter<AiCoachSetupAdapter.ViewHolder>() {

    private val pages = listOf(
        R.layout.page_ai_coach_intro,
        R.layout.page_app_selection,
        R.layout.page_habits_goals,
        R.layout.page_coach_persona_selection,
        R.layout.page_name_input
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        Log.d("AiCoachSetupAdapter", "Creating view for layoutId: $viewType")
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Set a tag for each page to help identify it later
        val tag = "page_${position + 1}"
        holder.itemView.tag = tag
        Log.d("AiCoachSetupAdapter", "Binding view at position $position with tag $tag")
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemViewType(position: Int): Int = pages[position]

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
} 
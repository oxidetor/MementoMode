package com.example.appblocker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.R
import com.example.appblocker.calculator.CalculatorPage

/**
 * Adapter for the calculator ViewPager that follows a more SOLID approach.
 * Uses semantic names for pages instead of generic step numbers.
 */
class CalculatorPagerAdapter : RecyclerView.Adapter<CalculatorPagerAdapter.PageViewHolder>() {

    // Pages defined by their purpose rather than arbitrary step numbers
    // Removed habit selection page as it's now part of AI coach setup
    private val pages = listOf(
        CalculatorPage(R.layout.page_calculator_estimate_input, "estimate_input"),
        CalculatorPage(R.layout.page_calculator_lifetime_impact, "lifetime_impact"),
        CalculatorPage(R.layout.page_calculator_usage_breakdown, "usage_breakdown"),
        CalculatorPage(R.layout.page_calculator_goal_setting, "goal_setting")
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        // Set a tag for each page to help identify it later
        val pageIdentifier = pages[position].identifier
        holder.itemView.tag = pageIdentifier
        android.util.Log.d("CalculatorPagerAdapter", "Setting tag for position $position: $pageIdentifier")
    }

    override fun getItemCount(): Int = pages.size

    override fun getItemViewType(position: Int): Int = pages[position].layoutResId

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view)
} 
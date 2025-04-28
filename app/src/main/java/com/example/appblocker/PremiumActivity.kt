package com.example.appblocker

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PremiumActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)
        
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.premium_title)
        }
        
        // Set up close button
        findViewById<ImageButton>(R.id.closeButton).setOnClickListener {
            finish()
        }
        
        // Set up button actions
        findViewById<Button>(R.id.monthlyButton).setOnClickListener {
            showComingSoonMessage("monthly")
        }
        
        findViewById<Button>(R.id.yearlyButton).setOnClickListener {
            showComingSoonMessage("annual")
        }
        
        findViewById<Button>(R.id.foreverButton).setOnClickListener {
            showComingSoonMessage("lifetime")
        }
    }
    
    private fun showComingSoonMessage(plan: String) {
        val message = when (plan) {
            "monthly" -> getString(R.string.coming_soon_monthly)
            "annual" -> getString(R.string.coming_soon_annual)
            else -> getString(R.string.coming_soon_lifetime)
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 
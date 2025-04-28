package com.example.appblocker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView

class AppAccessRequestActivity : AppCompatActivity() {

    private lateinit var appNameText: MaterialTextView
    private lateinit var minutesSlider: Slider
    private lateinit var minutesText: MaterialTextView
    private lateinit var cancelButton: MaterialButton
    private lateinit var allowButton: MaterialButton

    private var packageName: String = ""
    private var appName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_access_request)

        // Get package name from intent
        packageName = intent.getStringExtra("package_name") ?: ""
        appName = intent.getStringExtra("app_name") ?: ""

        if (packageName.isEmpty() && appName.isEmpty()) {
            Toast.makeText(this, "Error: No app specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize views
        appNameText = findViewById(R.id.appNameText)
        minutesSlider = findViewById(R.id.minutesSlider)
        minutesText = findViewById(R.id.minutesText)
        cancelButton = findViewById(R.id.cancelButton)
        allowButton = findViewById(R.id.allowButton)

        // Set app name
        appNameText.text = getString(R.string.access_app_request, appName)

        // Update minutes text when slider changes
        minutesSlider.addOnChangeListener { _, value, _ ->
            val minutes = value.toInt()
            minutesText.text = resources.getQuantityString(
                R.plurals.minutes_selected, 
                minutes, 
                minutes
            )
        }

        // Set initial minutes text
        minutesText.text = resources.getQuantityString(
            R.plurals.minutes_selected, 
            minutesSlider.value.toInt(), 
            minutesSlider.value.toInt()
        )

        // Cancel button
        cancelButton.setOnClickListener {
            finish()
        }

        // Allow button
        allowButton.setOnClickListener {
            if (packageName == "ai_coach") {
                // Special case for AI coach
                Toast.makeText(this, "Opening AI Coach...", Toast.LENGTH_SHORT).show()
                // In a real app, we'd open the AI coach interface here
            } else {
                // Grant temporary access to app
                val minutes = minutesSlider.value.toInt()
                allowAppAccess(packageName, minutes)
            }
            finish()
        }
    }

    private fun allowAppAccess(packageName: String, minutes: Int) {
        // Tell the service to allow this app temporarily
        val intent = Intent(this, AppBlockerService::class.java)
        intent.action = "ACTION_ALLOW_TEMPORARILY"
        intent.putExtra("package_name", packageName)
        intent.putExtra("minutes", minutes)
        startService(intent)

        // Inform user
        Toast.makeText(
            this,
            getString(R.string.app_allowed_for_minutes, minutes),
            Toast.LENGTH_SHORT
        ).show()

        // Launch the app
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
    }
} 
package com.example.appblocker

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SelectAppsActivity : AppCompatActivity() {

    private lateinit var appAdapter: AppAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: TextInputEditText
    private lateinit var buttonSelectNone: MaterialButton
    private lateinit var buttonSave: MaterialButton
    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_apps)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        recyclerView = findViewById(R.id.recyclerViewApps)
        searchEditText = findViewById(R.id.editTextSearch)
        buttonSelectNone = findViewById(R.id.buttonSelectNone)
        buttonSave = findViewById(R.id.buttonSave)

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        
        // Load apps and set up adapter
        loadApps()
        
        // Setup search functionality
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.lowercase() ?: ""
                appAdapter.filter(query)
            }
        })

        // Setup Select None button
        buttonSelectNone.setOnClickListener {
            appAdapter.clearAllSelections()
        }
        
        // Setup Save button
        buttonSave.setOnClickListener {
            saveBlockedApps()
            finish()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Handle the back button
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun loadApps() {
        val mainActivity = MainActivity()
        val appsList = mainActivity.loadApps(this)
        
        // Get currently blocked apps
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        
        // Update selection state based on previously blocked apps
        appsList.forEach { app ->
            app.isSelected = blockedApps.contains(app.packageName)
        }
        
        // Create adapter with the updated app list
        appAdapter = AppAdapter(appsList)
        recyclerView.adapter = appAdapter
    }
    
    private fun saveBlockedApps() {
        val blockedApps = appAdapter.getBlockedApps().toSet()
        
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putStringSet("blocked_apps", blockedApps).apply()
        
        // Set result to OK to indicate selection was successful
        setResult(RESULT_OK)
    }
    
    override fun onBackPressed() {
        // First save the selections
        saveBlockedApps()
        super.onBackPressed()
    }
} 
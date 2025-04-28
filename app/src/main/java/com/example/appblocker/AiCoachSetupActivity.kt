package com.example.appblocker

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.appblocker.adapters.AiCoachSetupAdapter
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import android.widget.RadioGroup
import android.widget.RadioButton

class AiCoachSetupActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var nextButton: Button
    private lateinit var backButton: Button
    
    private val selectedApps = mutableSetOf<String>()
    private val selectedHabits = mutableSetOf<String>()
    private var selectedCoachPersona: String = "nova" // Default coach
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_coach_setup)
        
        supportActionBar?.hide()
        
        viewPager = findViewById(R.id.viewPager)
        nextButton = findViewById(R.id.nextButton)
        backButton = findViewById(R.id.backButton)
        
        // Initialize selectedCoachPersona from preferences if available
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        selectedCoachPersona = prefs.getString("coach_persona", "nova") ?: "nova"
        android.util.Log.d("AiCoachSetup", "Initial coach selection: $selectedCoachPersona")
        
        setupViewPager()
        setupButtons()
    }
    
    private fun setupViewPager() {
        viewPager.adapter = AiCoachSetupAdapter()
        viewPager.isUserInputEnabled = false
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateButtonVisibility(position)
                
                // Add a delay to ensure the view is properly laid out
                viewPager.post {
                    when (position) {
                        0 -> setupIntroduction()
                        1 -> setupAppSelection()
                        2 -> setupHabits()
                        3 -> setupCoachPersonaSelection()
                        4 -> setupNameInput()
                    }
                }
            }
        })
    }
    
    private fun updateButtonVisibility(position: Int) {
        backButton.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
        nextButton.text = if (position == 4) getString(R.string.get_started) else getString(R.string.next)
    }
    
    private fun setupButtons() {
        nextButton.setOnClickListener {
            if (viewPager.currentItem == 4) {
                // Save user name if provided
                saveUserName()
                
                // Save AI coach setup completion status and collected data
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                prefs.edit()
                    .putBoolean(PREF_AI_COACH_SETUP_COMPLETED, true)
                    .putStringSet("blocked_apps", selectedApps)
                    .putStringSet("selected_habits", selectedHabits)
                    .putString("coach_persona", selectedCoachPersona)
                    .apply()

                // Start MainActivity
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                viewPager.currentItem = viewPager.currentItem + 1
            }
        }

        backButton.setOnClickListener {
            if (viewPager.currentItem > 0) {
                viewPager.currentItem = viewPager.currentItem - 1
            }
        }

        updateButtonVisibility(0)
    }
    
    private fun setupIntroduction() {
        // Introduction page is simple, no special setup needed
    }
    
    private fun setupAppSelection() {
        // Launch SelectAppsActivity when this page is selected
        val intent = Intent(this, SelectAppsActivity::class.java)
        startActivityForResult(intent, REQUEST_SELECT_APPS)
        
        // We don't override the default nextButton behavior anymore
    }
    
    private fun setupHabits() {
        val view = viewPager.findViewWithTag<View>("page_3") ?: return
        
        // Setup habits chip group
        val habitsChipGroup = view.findViewById<ChipGroup>(R.id.habitsChipGroup) ?: return
        
        // Add predefined habit chips with emojis
        val habits = listOf(
            "🧘 Morning meditation",
            "💪 Daily exercise",
            "📚 Reading",
            "🎓 Learning a new skill",
            "👨‍👩‍👧‍👦 Family time",
            "🥗 Cooking healthy meals",
            "📝 Journaling",
            "🌳 Nature walks",
            "🎨 Creative arts",
            "🎵 Playing music",
            "🧩 Solving puzzles",
            "🌱 Gardening",
            "🧠 Mindfulness practice",
            "🚴 Cycling",
            "🏊 Swimming",
            "☕ Mindful tea/coffee",
            "🧹 Decluttering",
            "💤 Better sleep habits",
            "🤝 Volunteering",
            "🌎 Learning languages"
        )
        
        // Clear existing chips first to prevent duplicates if this page is revisited
        habitsChipGroup.removeAllViews()
        
        // Add habit chips
        for (habit in habits) {
            val chip = Chip(this)
            chip.text = habit
            chip.isCheckable = true
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedHabits.add(habit)
                } else {
                    selectedHabits.remove(habit)
                }
            }
            habitsChipGroup.addView(chip)
        }
    }
    
    private fun setupCardSelection(view: View, cardId: Int, personaId: String, radioId: Int) {
        val card = view.findViewById<MaterialCardView>(cardId)
        val radio = view.findViewById<RadioButton>(radioId)
        
        card?.setOnClickListener {
            // Set selected persona when card is clicked
            selectedCoachPersona = personaId 
            radio?.isChecked = true
        }
    }
    
    // Add a method to visually update all card radio buttons
    private fun updateVisualRadioButtons(view: View, selectedPersonaId: String) {
        // List of all visible radio buttons in cards
        val radioButtons = listOf(
            view.findViewById<RadioButton>(R.id.radioSergeant),
            view.findViewById<RadioButton>(R.id.radioWisdom),
            view.findViewById<RadioButton>(R.id.radioSpark),
            view.findViewById<RadioButton>(R.id.radioZen),
            view.findViewById<RadioButton>(R.id.radioNova)
        )
        
        // List of corresponding persona IDs in the same order
        val personaIds = listOf("sergeant", "wisdom", "spark", "zen", "nova")
        
        // Update all visible radio buttons
        for (i in radioButtons.indices) {
            radioButtons[i]?.isChecked = personaIds[i] == selectedPersonaId
        }
    }
    
    private fun setupCoachPersonaSelection() {
        // Log before trying to find the view
        android.util.Log.d("AiCoachSetup", "Setting up coach persona selection")
        
        // Access the view from the RecyclerView directly
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView
        if (recyclerView == null) {
            android.util.Log.e("AiCoachSetup", "Could not find RecyclerView in ViewPager2")
            return
        }
        
        // Get the current page view from RecyclerView
        val view = recyclerView.findViewHolderForAdapterPosition(3)?.itemView
        if (view == null) {
            android.util.Log.e("AiCoachSetup", "Could not find view for page 4")
            return
        }
        
        android.util.Log.d("AiCoachSetup", "Found view for page 4")
        
        // Get necessary views
        val selectedPersonaText = view.findViewById<TextView>(R.id.selectedPersonaText)
        val radioGroup = view.findViewById<RadioGroup>(R.id.coachRadioGroup)
        
        // Debug radioGroup
        if (radioGroup == null) {
            android.util.Log.e("AiCoachSetup", "RadioGroup is null")
            return
        }
        
        // Map visible radio buttons to their card equivalents
        val radioMapping = mapOf(
            "sergeant" to Pair(view.findViewById<RadioButton>(R.id.radioSergeantDirect), view.findViewById<RadioButton>(R.id.radioSergeant)),
            "wisdom" to Pair(view.findViewById<RadioButton>(R.id.radioWisdomDirect), view.findViewById<RadioButton>(R.id.radioWisdom)),
            "spark" to Pair(view.findViewById<RadioButton>(R.id.radioSparkDirect), view.findViewById<RadioButton>(R.id.radioSpark)),
            "zen" to Pair(view.findViewById<RadioButton>(R.id.radioZenDirect), view.findViewById<RadioButton>(R.id.radioZen)),
            "nova" to Pair(view.findViewById<RadioButton>(R.id.radioNovaDirect), view.findViewById<RadioButton>(R.id.radioNova))
        )
        
        // Check for null RadioButtons
        if (radioMapping.values.any { it.first == null || it.second == null }) {
            android.util.Log.e("AiCoachSetup", "One or more radio buttons are null")
            return
        }
        
        // Set initial selection
        radioMapping[selectedCoachPersona]?.first?.isChecked = true
        
        // Update all visible radio buttons to match current selection
        updateVisualRadioButtons(view, selectedCoachPersona)
        
        // Set up synchronization between visible and card RadioButtons
        for ((personaId, buttons) in radioMapping) {
            val directButton = buttons.first  // RadioButton in RadioGroup
            val cardButton = buttons.second   // RadioButton in CardView
            
            // When card button is clicked, update direct button
            cardButton?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    directButton?.isChecked = true
                    // Update selected persona
                    selectedCoachPersona = personaId
                    
                    // Update all visible radio buttons
                    updateVisualRadioButtons(view, personaId)
                }
            }
        }
        
        // Update status text
        updateSelectedPersonaText(selectedPersonaText)
        
        // Set click listeners for all cards
        setupCoachInfoButton(view, R.id.btnSergeantInfo, "sergeant", R.string.coach_sergeant_bio)
        setupCoachInfoButton(view, R.id.btnWisdomInfo, "wisdom", R.string.coach_wisdom_bio)
        setupCoachInfoButton(view, R.id.btnSparkInfo, "spark", R.string.coach_spark_bio)
        setupCoachInfoButton(view, R.id.btnZenInfo, "zen", R.string.coach_zen_bio)
        setupCoachInfoButton(view, R.id.btnNovaInfo, "nova", R.string.coach_nova_bio)
        
        // Set click listeners for all cards for easier tap targets
        setupCardSelection(view, R.id.cardSergeant, "sergeant", R.id.radioSergeantDirect)
        setupCardSelection(view, R.id.cardWisdom, "wisdom", R.id.radioWisdomDirect)
        setupCardSelection(view, R.id.cardSpark, "spark", R.id.radioSparkDirect)
        setupCardSelection(view, R.id.cardZen, "zen", R.id.radioZenDirect)
        setupCardSelection(view, R.id.cardNova, "nova", R.id.radioNovaDirect)
        
        // Setup radio group listener
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            // Map checked radio button to coach persona ID
            val selectedPersonaId = when (checkedId) {
                R.id.radioSergeantDirect -> "sergeant"
                R.id.radioWisdomDirect -> "wisdom"
                R.id.radioSparkDirect -> "spark"
                R.id.radioZenDirect -> "zen"
                R.id.radioNovaDirect -> "nova"
                else -> "nova" // Default
            }
            
            // Update selection
            selectedCoachPersona = selectedPersonaId
            
            // Update all visible radio buttons
            updateVisualRadioButtons(view, selectedPersonaId)
            
            // Save to preferences immediately
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString("coach_persona", selectedCoachPersona)
                .apply()
                
            // Update text
            updateSelectedPersonaText(selectedPersonaText)
            
            // Show toast
            android.widget.Toast.makeText(
                this, 
                getString(R.string.coach_selected, getCoachNameFromId(selectedPersonaId)),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        
        // Debug log
        android.util.Log.d("AiCoachSetup", "Coach selection loaded with: $selectedCoachPersona")
    }
    
    private fun setupCoachInfoButton(view: View, buttonId: Int, personaId: String, bioStringId: Int) {
        val button = view.findViewById<Button>(buttonId)
        button?.setOnClickListener {
            // Show coach bio dialog
            AlertDialog.Builder(this)
                .setTitle(getCoachNameFromId(personaId))
                .setMessage(getString(bioStringId))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
    
    private fun updateSelectedPersonaText(textView: TextView?) {
        textView?.text = if (selectedCoachPersona != "none") {
            getString(R.string.selected_coach, getCoachNameFromId(selectedCoachPersona))
        } else {
            getString(R.string.tap_to_select_coach)
        }
    }
    
    private fun getCoachNameFromId(personaId: String): String {
        return when (personaId) {
            "sergeant" -> getString(R.string.coach_sergeant_name)
            "wisdom" -> getString(R.string.coach_wisdom_name)
            "spark" -> getString(R.string.coach_spark_name)
            "zen" -> getString(R.string.coach_zen_name)
            "nova" -> getString(R.string.coach_nova_name)
            else -> ""
        }
    }
    
    private fun setupNameInput() {
        // Log before trying to find the view
        android.util.Log.d("AiCoachSetup", "Setting up name input")
        
        // Access the view from the RecyclerView directly
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView
        if (recyclerView == null) {
            android.util.Log.e("AiCoachSetup", "Could not find RecyclerView in ViewPager2")
            return
        }
        
        // Get the current page view from RecyclerView
        val view = recyclerView.findViewHolderForAdapterPosition(4)?.itemView
        if (view == null) {
            android.util.Log.e("AiCoachSetup", "Could not find view for page 5")
            return
        }
        
        // Find the name input EditText
        val nameInput = view.findViewById<TextInputEditText>(R.id.nameInput)
        
        // Pre-fill with existing name if available
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val existingName = prefs.getString("user_name", "")
        nameInput?.setText(existingName)
    }
    
    private fun saveUserName() {
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView ?: return
        val view = recyclerView.findViewHolderForAdapterPosition(4)?.itemView ?: return
        
        val nameInput = view.findViewById<TextInputEditText>(R.id.nameInput)
        val name = nameInput?.text?.toString()?.trim() ?: ""
        
        if (name.isNotEmpty()) {
            // Save the name to preferences
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString("user_name", name)
                .apply()
        }
    }
    
    companion object {
        const val PREF_AI_COACH_SETUP_COMPLETED = "ai_coach_setup_completed"
        private const val REQUEST_SELECT_APPS = 1001
    }

    // Override onActivityResult to handle the result from SelectAppsActivity
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SELECT_APPS && resultCode == RESULT_OK) {
            // Get the blocked apps from preferences directly
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
            selectedApps.clear()
            selectedApps.addAll(blockedApps)
            
            // Advance to the next page automatically
            viewPager.currentItem = viewPager.currentItem + 1
            
            // Reset the button behavior to ensure "Get Started" works
            setupButtons()
        }
    }
} 
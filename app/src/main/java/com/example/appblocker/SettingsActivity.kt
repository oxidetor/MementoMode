package com.example.appblocker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.appblocker.ai.AIServiceFactory
import com.example.appblocker.ai.OpenAIModelService
import com.example.appblocker.config.ApiConfig
import com.example.appblocker.databinding.ActivitySettingsBinding
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SettingsActivity"
        
        const val PREF_ACCESS_DURATION = "accessDuration"
        const val PREF_AI_STRICTNESS = "aiStrictness"
        const val PREF_AI_MODEL = "aiModel"
        const val PREF_DEBUG_MODE = "debugMode"
        const val PREF_API_KEY = "apiKey"
        const val PREF_CUSTOM_PROMPT = "customPrompt"
        const val PREF_USE_CUSTOM_API_KEY = "useCustomApiKey"
        const val PREF_ALWAYS_SHOW_CALCULATOR = "always_show_calculator"
        
        const val DEFAULT_ACCESS_DURATION = 5 // 5 minutes
    }

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var modelService: OpenAIModelService
    
    // Flag to track if we're currently fetching models
    private var isFetchingModels = false
    
    // Keep reference to current models list for the spinner
    private var currentModelsList: List<OpenAIModelService.ModelInfo> = emptyList()

    private lateinit var sliderDuration: Slider
    private lateinit var textViewDuration: TextView
    private lateinit var radioGroupStrictness: RadioGroup
    private lateinit var switchUseCustomApiKey: SwitchMaterial
    private lateinit var editTextApiKey: TextInputEditText
    private lateinit var textInputLayoutApiKey: TextInputLayout
    private lateinit var editTextCustomPrompt: TextInputEditText
    private lateinit var switchDebugMode: SwitchMaterial
    private lateinit var switchShowCalculator: SwitchMaterial
    private lateinit var spinnerModelSelection: Spinner
    private lateinit var textViewSelectedModel: TextView
    private lateinit var textViewAIDescription: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        sharedPreferences = getSharedPreferences(AppBlockerService.PREFS_NAME, Context.MODE_PRIVATE)
        modelService = OpenAIModelService(this)
        
        // Setup toolbar with back navigation
        val toolbar = binding.topAppBar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        // Initialize UI components using binding
        sliderDuration = binding.sliderDuration
        textViewDuration = binding.textViewDuration
        radioGroupStrictness = binding.radioGroupStrictness
        switchUseCustomApiKey = binding.switchUseCustomApiKey
        editTextApiKey = binding.editTextApiKey
        textInputLayoutApiKey = binding.textInputLayoutApiKey
        editTextCustomPrompt = binding.editTextCustomPrompt
        switchDebugMode = binding.switchDebugMode
        switchShowCalculator = binding.switchShowCalculator
        spinnerModelSelection = binding.spinnerModelSelection
        textViewSelectedModel = binding.textViewSelectedModel
        textViewAIDescription = binding.textViewAIDescription

        // Setup listeners
        setupDurationSlider()
        setupCustomPrompt()
        setupAIStrictness()
        setupDebugMode()
        setupAPIKeySection()
        setupModelsSpinner()
        setupAlwaysShowCalculatorToggle()
    }

    private fun setupDurationSlider() {
        val currentDuration = sharedPreferences.getInt(PREF_ACCESS_DURATION, DEFAULT_ACCESS_DURATION)
        sliderDuration.value = currentDuration.toFloat()
        updateDurationText(currentDuration)

        sliderDuration.addOnChangeListener { slider: Slider, value: Float, fromUser: Boolean ->
            if (fromUser) {
                val duration = value.toInt()
                updateDurationText(duration)
                sharedPreferences.edit().putInt(PREF_ACCESS_DURATION, duration).apply()
            }
        }
    }

    private fun setupCustomPrompt() {
        // Get saved custom prompt
        val savedPrompt = sharedPreferences.getString(PREF_CUSTOM_PROMPT, "") ?: ""
        editTextCustomPrompt.setText(savedPrompt)
        
        // Save custom prompt when it changes
        editTextCustomPrompt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val customPrompt = s.toString()
                sharedPreferences.edit().putString(PREF_CUSTOM_PROMPT, customPrompt).apply()
            }
        })
    }
    
    private fun setupAIStrictness() {
        // Get the current AI strictness setting
        val aiService = AIServiceFactory.getService(this)
        val currentStrictness = aiService.getStrictness()
        
        // Set the appropriate radio button
        val radioButtonId = when (currentStrictness) {
            AIStrictness.LENIENT -> R.id.radioLenient
            AIStrictness.MODERATE -> R.id.radioModerate
            AIStrictness.STRICT -> R.id.radioStrict
        }
        radioGroupStrictness.check(radioButtonId)
        
        // Set up listener for radio button changes
        radioGroupStrictness.setOnCheckedChangeListener { _, checkedId ->
            val strictness = when (checkedId) {
                R.id.radioLenient -> AIStrictness.LENIENT
                R.id.radioStrict -> AIStrictness.STRICT
                else -> AIStrictness.MODERATE
            }
            
            // Update the AI service strictness
            val aiService = AIServiceFactory.getService(this)
            aiService.setStrictness(strictness)
        }
    }

    private fun setupDebugMode() {
        // Get current debug mode setting
        val debugModeEnabled = sharedPreferences.getBoolean(PREF_DEBUG_MODE, false)
        switchDebugMode.isChecked = debugModeEnabled
        
        // Set up listener for switch changes
        switchDebugMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(PREF_DEBUG_MODE, isChecked).apply()
        }
    }

    private fun setupAPIKeySection() {
        val currentApiKey = sharedPreferences.getString(PREF_API_KEY, "") ?: ""
        val useCustomApiKey = sharedPreferences.getBoolean(PREF_USE_CUSTOM_API_KEY, false)
        
        // Check if a developer API key is available (assuming we don't have one here)
        val hasDevKey = false
        
        // Set up API key toggle if developer key is available
        if (hasDevKey) {
            switchUseCustomApiKey.visibility = View.VISIBLE
            switchUseCustomApiKey.isChecked = useCustomApiKey
            
            // Hide or show the API key field based on the toggle
            textInputLayoutApiKey.visibility = if (useCustomApiKey) View.VISIBLE else View.GONE
            
            // Update the description text
            if (useCustomApiKey) {
                textViewAIDescription.text = getString(R.string.real_ai_description)
            } else {
                textViewAIDescription.text = getString(R.string.real_ai_description) + "\n" +
                    getString(R.string.using_developer_key)
            }
            
            // Set up listener for the toggle
            switchUseCustomApiKey.setOnCheckedChangeListener { _, isChecked ->
                sharedPreferences.edit().putBoolean(PREF_USE_CUSTOM_API_KEY, isChecked).apply()
                textInputLayoutApiKey.visibility = if (isChecked) View.VISIBLE else View.GONE
                
                // Update the description text
                if (isChecked) {
                    textViewAIDescription.text = getString(R.string.real_ai_description)
                } else {
                    textViewAIDescription.text = getString(R.string.real_ai_description) + "\n" +
                        getString(R.string.using_developer_key)
                }
            }
        } else {
            // If no developer key, hide the toggle and always show the API key field
            switchUseCustomApiKey.visibility = View.GONE
            textInputLayoutApiKey.visibility = View.VISIBLE
            textViewAIDescription.text = getString(R.string.real_ai_description)
        }
        
        // Set the current API key in the field if we're using a custom key
        if (useCustomApiKey || !hasDevKey) {
            editTextApiKey.setText(currentApiKey)
        }
        
        // Always show model selection
        findViewById<View>(R.id.layoutModelSelection).visibility = View.VISIBLE
        
        // Save API key when it changes
        editTextApiKey.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                try {
                    val apiKey = s.toString()
                    sharedPreferences.edit().putString(PREF_API_KEY, apiKey).apply()
                    
                    // If we have a developer key but the user entered their own key,
                    // make sure the custom API key toggle is on
                    if (apiKey.isNotBlank()) {
                        if (!switchUseCustomApiKey.isChecked) {
                            switchUseCustomApiKey.isChecked = true
                            sharedPreferences.edit().putBoolean(PREF_USE_CUSTOM_API_KEY, true).apply()
                        }
                    }
                    
                    // Try to fetch models if the API key looks valid
                    if (apiKey.length >= 30) {
                        try {
                            fetchModelsFromApi()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error fetching models on API key change", e)
                            // Don't show a toast here to avoid spamming the user while typing
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in API key text watcher", e)
                }
            }
        })
        
        // Set up model selection
        setupModelsSpinner()
    }

    private fun updateDurationText(duration: Int) {
        val formattedDuration = String.format(Locale.getDefault(), getString(R.string.duration_format), duration)
        textViewDuration.text = formattedDuration
    }

    private fun setupModelsSpinner() {
        // Get available models from the model service
        currentModelsList = modelService.getRecommendedModels()
        val selectedModel = modelService.getSelectedModel()
        
        // Create adapter for spinner
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            currentModelsList.map { it.displayName }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        
        spinnerModelSelection.adapter = adapter
        
        // Set selected model in spinner
        val selectedIndex = currentModelsList.indexOfFirst { it.id == selectedModel }
        if (selectedIndex >= 0) {
            spinnerModelSelection.setSelection(selectedIndex)
        }
        
        // Update the selected model info text
        updateSelectedModelText(selectedModel)
        
        // Set up spinner selection listener
        spinnerModelSelection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                try {
                    if (position >= 0 && position < currentModelsList.size) {
                        val selectedModelInfo = currentModelsList[position]
                        modelService.saveSelectedModel(selectedModelInfo.id)
                        updateSelectedModelText(selectedModelInfo.id)
                    } else {
                        Log.e("SettingsActivity", "Invalid position in onItemSelected: $position, list size: ${currentModelsList.size}")
                    }
                } catch (e: Exception) {
                    Log.e("SettingsActivity", "Error in onItemSelected", e)
                    Toast.makeText(this@SettingsActivity, "Error selecting model", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }
    
    private fun updateSelectedModelText(modelId: String) {
        val modelDisplayName = modelService.getModelDisplayName(modelId)
        textViewSelectedModel.text = "Using: $modelDisplayName"
    }
    
    private fun hasValidApiKey(): Boolean {
        val useCustomApiKey = sharedPreferences.getBoolean(PREF_USE_CUSTOM_API_KEY, false)
        val userKey = sharedPreferences.getString(PREF_API_KEY, "") ?: ""
        
        return (useCustomApiKey && userKey.isNotBlank()) || (!useCustomApiKey && ApiConfig.hasDevKey())
    }
    
    private fun fetchModelsFromApi() {
        // Fetch the API key - either custom or default
        val apiKey: String
        val useCustomApiKey = sharedPreferences.getBoolean(PREF_USE_CUSTOM_API_KEY, false)
        apiKey = if (useCustomApiKey) {
            sharedPreferences.getString(PREF_API_KEY, "") ?: ""
        } else {
            // Use developer key if not using custom key
            sharedPreferences.getString(PREF_API_KEY, "") ?: ""
        }
        
        // Ensure we have an API key before fetching models
        if (apiKey.isBlank()) {
            Log.d(TAG, "Cannot fetch models: blank API key")
            return
        }
        
        // Show loading indicator
        val loadingToast = Toast.makeText(this@SettingsActivity, "Fetching available models...", Toast.LENGTH_SHORT)
        loadingToast.show()
        
        // Set fetching flag
        isFetchingModels = true
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Fetch models on IO dispatcher in a safe manner
                val result = try {
                    modelService.fetchAvailableModels()
                } catch (e: Exception) {
                    Log.e("SettingsActivity", "Error fetching models", e)
                    Result.failure<List<OpenAIModelService.ModelInfo>>(e)
                }
                
                // Ensure we're still in a valid state
                if (!isFinishing && !isDestroyed) {
                    loadingToast.cancel()
                    
                    result.fold(
                        onSuccess = { models ->
                            try {
                                // Update our reference to the current models list
                                currentModelsList = models
                                
                                // Create new adapter with the fetched models
                                val adapter = ArrayAdapter(
                                    this@SettingsActivity,
                                    android.R.layout.simple_spinner_item,
                                    models.map { it.displayName }
                                ).apply {
                                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                }
                                
                                spinnerModelSelection.adapter = adapter
                                
                                // Restore selection
                                val selectedModel = modelService.getSelectedModel()
                                val selectedIndex = models.indexOfFirst { it.id == selectedModel }
                                if (selectedIndex >= 0) {
                                    spinnerModelSelection.setSelection(selectedIndex)
                                } else if (models.isNotEmpty()) {
                                    // If the saved model isn't in the list, select first available
                                    spinnerModelSelection.setSelection(0)
                                    modelService.saveSelectedModel(models[0].id)
                                    updateSelectedModelText(models[0].id)
                                }
                                
                                Toast.makeText(this@SettingsActivity, "Found ${models.size} compatible models", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("SettingsActivity", "Error updating UI with models", e)
                                Toast.makeText(this@SettingsActivity, "Error updating model list: ${e.message}", Toast.LENGTH_SHORT).show()
                                
                                // Fallback to default models
                                fallbackToDefaultModels()
                            }
                        },
                        onFailure = { error ->
                            Log.e("SettingsActivity", "API failure", error)
                            Toast.makeText(this@SettingsActivity, "Error fetching models: ${error.message}", Toast.LENGTH_SHORT).show()
                            
                            // Fallback to default models
                            fallbackToDefaultModels()
                        }
                    )
                }
            } catch (e: Exception) {
                // Final safeguard against any uncaught errors
                Log.e("SettingsActivity", "Uncaught error in fetchModelsFromApi", e)
                if (!isFinishing && !isDestroyed) {
                    loadingToast.cancel()
                    Toast.makeText(this@SettingsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    
                    // Fallback to default models
                    fallbackToDefaultModels() 
                }
            } finally {
                // Always reset the fetching flag
                isFetchingModels = false
            }
        }
    }
    
    private fun fallbackToDefaultModels() {
        try {
            // Use the default recommended models when API call fails
            currentModelsList = modelService.getRecommendedModels()
            
            val adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                currentModelsList.map { it.displayName }
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            
            spinnerModelSelection.adapter = adapter
            
            // Restore selection from saved preferences
            val selectedModel = modelService.getSelectedModel()
            val selectedIndex = currentModelsList.indexOfFirst { it.id == selectedModel }
            if (selectedIndex >= 0) {
                spinnerModelSelection.setSelection(selectedIndex)
            }
            
            Log.d("SettingsActivity", "Using default models as fallback")
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error in fallback model loading", e)
        }
    }

    private fun setupAlwaysShowCalculatorToggle() {
        // Get current setting
        val alwaysShowCalculator = sharedPreferences.getBoolean(
            ScreentimeCalculatorActivity.PREF_ALWAYS_SHOW_CALCULATOR, false)
        
        switchShowCalculator.isChecked = alwaysShowCalculator
        
        // Set up listener for switch changes
        switchShowCalculator.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(
                ScreentimeCalculatorActivity.PREF_ALWAYS_SHOW_CALCULATOR, isChecked).apply()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
} 
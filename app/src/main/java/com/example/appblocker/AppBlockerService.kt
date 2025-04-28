package com.example.appblocker

import android.app.*
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import android.widget.Toast
import android.widget.ProgressBar
import android.widget.ImageButton
import android.widget.ImageView
import com.example.appblocker.ai.AIService
import com.example.appblocker.ai.AIServiceFactory
import com.example.appblocker.ai.RealOpenAIService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.appblocker.model.AIEvaluation
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.CountDownTimer
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import android.app.usage.UsageStats
import java.util.SortedMap
import java.util.TreeMap

class AppBlockerService : Service() {
    
    companion object {
        private const val TAG = "AppBlockerService"
        
        // Actions
        private const val ACTION_STOP = "com.example.appblocker.STOP"
        
        // Preferences
        private const val SERVICE_RUNNING_KEY = "service_running"
        const val PREFS_NAME = "AppBlockerPrefs"
        const val PREF_BLOCKED_APPS = "blocked_apps"
        const val PREF_CUSTOM_PROMPT = "customPrompt"
        const val PREF_API_KEY = "api_key"
        const val PREF_USE_CUSTOM_API_KEY = "useCustomApiKey"
        const val PREF_ACCESS_DURATION = "accessDuration"
        const val PREF_DEBUG_MODE = "debugMode"
        
        // Notification IDs
        private const val NOTIFICATION_ID = 1001
        private const val TEMP_ACCESS_NOTIFICATION_ID = 1002
        
        // Channel IDs
        private const val CHANNEL_ID = "app_blocker_channel"
        private const val TEMP_ACCESS_CHANNEL_ID = "app_blocker_temp_access_channel"
    }
    
    private lateinit var windowManager: WindowManager
    private var blockerView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val CHECK_INTERVAL = 500L // Check every 500ms
    private var blockedApps: Set<String> = emptySet()
    private var currentForegroundApp: String? = null
    private var isRunning = false
    private var isOverlayShowing = false
    private lateinit var temporaryAccessManager: TemporaryAccessManager
    private var aiService: AIService? = null
    private var chatAdapter = ChatAdapter()
    
    // Toast tracking to prevent multiple toasts
    private var lastToastTime = 0L
    private var lastToastMessage: String = ""
    private val TOAST_THROTTLE_MS = 3000L // Minimum time between similar toasts

    // Check current app every 500ms
    private val appCheckRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                checkCurrentApp()
                handler.postDelayed(this, 500)
            }
        }
    }
    
    // Update notification timer every second
    private val notificationUpdateRunnable = object : Runnable {
        override fun run() {
            val packageName = currentForegroundApp ?: return
            
            // Check if we still have temporary access
            if (temporaryAccessManager.hasTemporaryAccess(packageName)) {
                val remainingSeconds = temporaryAccessManager.getRemainingSeconds(packageName)
                
                // Show toast alerts at specific time intervals
                when (remainingSeconds) {
                    60 -> showRemainingTimeToast(packageName, "1 minute")
                    30 -> showRemainingTimeToast(packageName, "30 seconds")
                    10 -> showRemainingTimeToast(packageName, "10 seconds")
                }
                
                updateAccessNotification(packageName)
                // Schedule the next update
                handler.postDelayed(this, 1000) // Update every second
            } else {
                // If no more temporary access, remove the notification
                cancelAccessNotification()
                
                // Show blocker if this is a blocked app
                if (blockedApps.contains(packageName) && packageName != this@AppBlockerService.packageName) {
                    showBlockerOverlay()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        temporaryAccessManager = TemporaryAccessManager(this)
        
        // Load blocked apps
        loadBlockedApps()
        
        // Register the stop action receiver
        val filter = IntentFilter(ACTION_STOP)
        registerReceiver(stopReceiver, filter, RECEIVER_NOT_EXPORTED)
        
        // Create notification channels
        createNotificationChannel()
        createTempAccessNotificationChannel()
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createServiceNotification())
        
        // Mark service as running in shared preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putBoolean(SERVICE_RUNNING_KEY, true).apply()
        
        // Initialize the AI service
        aiService = AIServiceFactory.getService(this)
        
        // Start the monitoring handler
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            isRunning = true
            handler.post(appCheckRunnable)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        isRunning = false
        handler.removeCallbacks(appCheckRunnable)
        handler.removeCallbacks(notificationUpdateRunnable)
        hideBlockerIfShowing()
        cancelAccessNotification()
        setServiceRunning(false)
        super.onDestroy()
    }

    private fun setServiceRunning(running: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean(SERVICE_RUNNING_KEY, running)
            .apply()
    }

    private fun loadBlockedApps() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        blockedApps = prefs.getStringSet(PREF_BLOCKED_APPS, emptySet()) ?: emptySet()
        Log.d(TAG, "Loaded blocked apps: $blockedApps")
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP) {
                stopSelf()
            }
        }
    }

    private fun checkCurrentApp() {
        try {
            if (!AppUtils.hasUsageStatsPermission(this) || !AppUtils.hasOverlayPermission(this)) {
                Log.e(TAG, "Missing permissions, stopping service")
                stopSelf()
                return
            }

            val foregroundApp = AppUtils.getForegroundApp(this)
            Log.d(TAG, "Current foreground app: $foregroundApp")
            
            // Update current app reference if it has changed
            if (foregroundApp != null && foregroundApp != currentForegroundApp) {
                Log.d(TAG, "App changed from $currentForegroundApp to $foregroundApp")
                
                // Update current app
                currentForegroundApp = foregroundApp
                
                if (blockedApps.contains(foregroundApp) && foregroundApp != packageName) {
                    // Check for temporary access
                    if (temporaryAccessManager.hasTemporaryAccess(foregroundApp)) {
                        // Show notification for temporary access
                        updateAccessNotification(foregroundApp)
                        
                        // Start notification update timer
                        handler.removeCallbacks(notificationUpdateRunnable)
                        handler.post(notificationUpdateRunnable)
                    } else {
                        // Show blocker if no temporary access
                        Log.d(TAG, "Showing blocker for app: $foregroundApp")
                        showBlockerOverlay()
                    }
                } else {
                    hideBlockerIfShowing()
                    
                    // Cancel the temporary access notification if we're not in a blocked app
                    cancelAccessNotification()
                }
            } else if (foregroundApp != null) {
                // We're still in the same app, make sure UI elements are in the correct state
                if (blockedApps.contains(foregroundApp) && foregroundApp != packageName) {
                    if (temporaryAccessManager.hasTemporaryAccess(foregroundApp)) {
                        // Ensure notification is showing if we're in a blocked app with temporary access
                        updateAccessNotification(foregroundApp)
                        
                        // Make sure notification timer is running
                        handler.removeCallbacks(notificationUpdateRunnable)
                        handler.post(notificationUpdateRunnable)
                    } else if (blockerView == null) {
                        // Show blocker if no temporary access and not already showing
                        showBlockerOverlay()
                    }
                }
            }
        } catch (e: Exception) {
            // Log the error but make sure we still try to show the blocker
            Log.e(TAG, "Error in checkCurrentApp", e)
            
            // Try to get the current app even if there's an exception
            val foregroundApp = try {
                AppUtils.getForegroundApp(this)
            } catch (e2: Exception) {
                Log.e(TAG, "Error getting foreground app as fallback", e2)
                null
            }
            
            // If we have a foreground app and it's blocked, show the blocker
            if (foregroundApp != null && blockedApps.contains(foregroundApp) && foregroundApp != packageName) {
                try {
                    // Show blocker if it's not already showing
                    if (blockerView == null && !isOverlayShowing) {
                        Log.d(TAG, "Showing blocker for app as fallback: $foregroundApp")
                        showBlockerOverlay()
                    }
                } catch (e3: Exception) {
                    Log.e(TAG, "Error showing blocker overlay as fallback", e3)
                }
            }
        }
    }

    /**
     * Shows a toast message with the remaining time
     */
    private fun showRemainingTimeToast(packageName: String, timeText: String) {
        try {
            // Get app name for better display
            val appName = try {
                val pm = applicationContext.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                packageName
            }
            
            val message = getString(R.string.time_remaining_toast, timeText, appName)
            val currentTime = System.currentTimeMillis()
            
            // Check if we've shown this toast recently
            if (message != lastToastMessage || currentTime - lastToastTime > TOAST_THROTTLE_MS) {
                // Show toast on the main thread
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        applicationContext,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
                
                // Update tracking variables
                lastToastTime = currentTime
                lastToastMessage = message
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing toast", e)
        }
    }

    /**
     * Updates the notification to show the time remaining for temporary access
     */
    private fun updateAccessNotification(packageName: String) {
        if (!temporaryAccessManager.hasTemporaryAccess(packageName)) {
            cancelAccessNotification()
            return
        }
        
        val remainingSeconds = temporaryAccessManager.getRemainingSeconds(packageName)
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        
        // Get app name for better display
        val appName = try {
            val pm = applicationContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
        
        val channelId = TEMP_ACCESS_CHANNEL_ID
        
        // Create notification channel if needed
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Temporary App Access",
                NotificationManager.IMPORTANCE_LOW // Changed from HIGH to LOW to be less intrusive
            ).apply {
                description = "Shows remaining time for temporary app access"
                setShowBadge(true)
                // Disable sound and vibration to avoid annoying the user
                setSound(null, null)
                enableVibration(false)
                enableLights(true)
                lightColor = ContextCompat.getColor(this@AppBlockerService, R.color.colorAccent)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create an intent to open the app settings
        val intent = Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Format the time remaining text with locale
        val timeText = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        
        // Create notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Time Remaining on $appName : $timeText")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("You have $timeText remaining for temporary access to $appName"))
            .setSmallIcon(R.drawable.ic_timer)
            .setColor(ContextCompat.getColor(this, R.color.colorAccent))
            .setColorized(true)
            .setContentIntent(pendingIntent)
            .setOngoing(false) // Changed from true to false to make it dismissible
            .setPriority(NotificationCompat.PRIORITY_LOW) // Changed from HIGH to LOW to be less intrusive
            // Add a progress bar
            .setProgress(temporaryAccessManager.getRemainingSeconds(packageName), remainingSeconds, false)
            // Add category for better system handling
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            // Make it dismissible
            .setAutoCancel(true)
            // Ensure no sound for this notification
            .setSound(null)
            .setVibrate(null)
            .setDefaults(0) // Clear all defaults including sound, vibrate, and lights
            .build()
        
        // Show notification
        notificationManager.notify(TEMP_ACCESS_NOTIFICATION_ID, notification)
        
        // Log for debugging
        Log.d(TAG, "Updated access notification for $appName: $timeText remaining")
    }
    
    /**
     * Cancels the temporary access notification
     */
    private fun cancelAccessNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(TEMP_ACCESS_NOTIFICATION_ID)
        handler.removeCallbacks(notificationUpdateRunnable)
    }

    private fun showBlockerOverlay() {
        if (isOverlayShowing) return
        
        // Initialize the AI service when showing the overlay
        aiService = AIServiceFactory.getService(this)
        
        if (blockerView != null) {
            Log.d(TAG, "Blocker view already showing")
            return
        }
        
        try {
            // Create a themed context wrapper
            val themedContext = ContextThemeWrapper(this, R.style.Theme_MementoMode)
            val inflater = LayoutInflater.from(themedContext)
            blockerView = inflater.inflate(R.layout.layout_blocker_overlay, null)
            
            // Initialize chat components
            chatAdapter = ChatAdapter()
            val recyclerView = blockerView?.findViewById<RecyclerView>(R.id.recyclerViewChat)
            
            // Debug logging to check all key components
            Log.d(TAG, "RecyclerView found: ${recyclerView != null}")
            
            recyclerView?.apply {
                layoutManager = LinearLayoutManager(context).apply {
                    stackFromEnd = false
                    reverseLayout = false
                }
                adapter = chatAdapter
                
                // Set clip to padding false to allow overscroll effect
                clipToPadding = false
                
                // Ensure vertical scrollbar is visible
                isVerticalScrollBarEnabled = true
                scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
                
                // Add padding to ensure messages at the bottom are visible
                setPadding(paddingLeft, paddingTop, paddingRight, 16)
            }

            val buttonGoBack = blockerView?.findViewById<Button>(R.id.buttonGoBack)
            buttonGoBack?.setOnClickListener {
                // Return to home screen
                val homeIntent = Intent(Intent.ACTION_MAIN)
                homeIntent.addCategory(Intent.CATEGORY_HOME)
                homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(homeIntent)
                
                // Hide the overlay
                hideBlockerIfShowing()
            }

            val buttonRequestAccess = blockerView?.findViewById<Button>(R.id.buttonRequestAccess)
            buttonRequestAccess?.setOnClickListener {
                showChatInterface()
            }
            
            // Set up exit button for chat interface
            val buttonExitChat = blockerView?.findViewById<ImageButton>(R.id.buttonExitChat)
            buttonExitChat?.setOnClickListener {
                // Hide chat interface and show main blocker layout
                blockerView?.apply {
                    findViewById<View>(R.id.chatLayout)?.visibility = View.GONE
                    findViewById<View>(R.id.mainBlockerLayout)?.visibility = View.VISIBLE
                }
                
                // Clear any text in the input field
                blockerView?.findViewById<TextInputEditText>(R.id.editTextMessage)?.text?.clear()
                
                // Hide keyboard if it's showing
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(blockerView?.windowToken, 0)
                
                // Clear the conversation history when exiting chat
                aiService?.let { service ->
                    when (service) {
                        is RealOpenAIService -> {
                            service.clearConversationHistory()
                            Log.d(TAG, "OpenAI conversation thread cleared on chat exit")
                        }
                        else -> {
                            // Handle any other potential AI service implementations
                            Log.d(TAG, "Unknown AI service type, conversation history may not be cleared")
                        }
                    }
                }
                
                // Clear the chat adapter
                chatAdapter.clear()
                
                Log.d(TAG, "Exited chat interface")
            }

            val editText = blockerView?.findViewById<TextInputEditText>(R.id.editTextMessage)
            val buttonSend = blockerView?.findViewById<View>(R.id.buttonSend)
            val textInputLayout = blockerView?.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.textInputLayoutMessage)
            
            editText?.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    // Enable send button as long as there's some text
                    buttonSend?.isEnabled = !s.isNullOrBlank()
                    
                    // Update TextInputLayout colors based on text content
                    if (!s.isNullOrBlank()) {
                        // Success state - show accent color
                        textInputLayout?.boxStrokeColor = ContextCompat.getColor(this@AppBlockerService, R.color.colorAccent)
                        textInputLayout?.setCounterTextColor(ContextCompat.getColorStateList(this@AppBlockerService, R.color.colorAccent))
                    } else {
                        // Normal state - gray color
                        textInputLayout?.boxStrokeColor = ContextCompat.getColor(this@AppBlockerService, android.R.color.darker_gray)
                        textInputLayout?.setCounterTextColor(ContextCompat.getColorStateList(this@AppBlockerService, android.R.color.darker_gray))
                    }
                }
            })
            
            buttonSend?.isEnabled = false
            buttonSend?.setOnClickListener {
                handleSendClick()
            }
            
            // Set up the EditText to handle keyboard send action
            blockerView?.findViewById<TextInputEditText>(R.id.editTextMessage)?.apply {
                setOnEditorActionListener { _, actionId, event ->
                    if (actionId == EditorInfo.IME_ACTION_SEND ||
                        (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                    ) {
                        handleSendClick()
                        return@setOnEditorActionListener true
                    }
                    false
                }
                
                // Enable the send button when text is entered
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        buttonSend?.isEnabled = !s.isNullOrBlank()
                    }
                    
                    override fun afterTextChanged(s: Editable?) {}
                })
            }
            
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                // Set soft input mode to adjust pan to handle keyboard
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            }
            
            windowManager.addView(blockerView, layoutParams)
            isOverlayShowing = true
            Log.d(TAG, "Successfully added blocker view")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing blocker overlay", e)
            blockerView = null
        }
    }

    private fun showChatInterface() {
        blockerView?.apply {
            findViewById<View>(R.id.mainBlockerLayout)?.visibility = View.GONE
            findViewById<View>(R.id.chatLayout)?.visibility = View.VISIBLE
            
            // Make sure RecyclerView is properly configured
            val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewChat)
            recyclerView?.apply {
                // Ensure the layout manager is set
                if (layoutManager == null) {
                    layoutManager = LinearLayoutManager(context).apply {
                        // Set stackFromEnd to false to make messages appear from the top
                        stackFromEnd = false
                        reverseLayout = false
                    }
                    Log.d(TAG, "Setting new LinearLayoutManager on RecyclerView with stackFromEnd=false")
                } else if (layoutManager is LinearLayoutManager) {
                    (layoutManager as LinearLayoutManager).stackFromEnd = false
                    (layoutManager as LinearLayoutManager).reverseLayout = false
                    Log.d(TAG, "Updated existing LinearLayoutManager with stackFromEnd=false")
                }
                
                // Ensure the adapter is set
                if (adapter == null) {
                    adapter = chatAdapter
                    Log.d(TAG, "Setting ChatAdapter on RecyclerView")
                }
                
                // Set clip to padding false to allow overscroll effect
                clipToPadding = false
                
                // Make sure we can see the scrollbar
                isVerticalScrollBarEnabled = true
                scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
                
                // Add a debug message
                Log.d(TAG, "RecyclerView setup: width=${width}, height=${height}, visibility=${visibility}, itemCount=${chatAdapter.itemCount}")
            }
            
            // Initialize TextInputLayout with gray color
            findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.textInputLayoutMessage)?.apply {
                boxStrokeColor = ContextCompat.getColor(context, android.R.color.darker_gray)
                setCounterTextColor(ContextCompat.getColorStateList(context, android.R.color.darker_gray))
                counterMaxLength = 0 // Remove character counter
            }

            // Update TextInputLayout counter
            findViewById<TextInputEditText>(R.id.editTextMessage)?.apply {
                requestFocus()
                context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    ?.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }

            // Add initial message only if the chat is empty
            if (chatAdapter.itemCount == 0) {
                // Check if debug mode is enabled
                val isDebugMode = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(SettingsActivity.PREF_DEBUG_MODE, false)
                
                if (isDebugMode) {
                    chatAdapter.addMessage(
                        ChatMessage(
                            "DEBUG MODE ENABLED: Access will be automatically granted. Just click send.",
                            false
                        )
                    )
                    // Scroll after adding the message
                    scrollToLatestMessage()
                } else {
                    // Get app name and custom prompt for the greeting
                    val appName = currentForegroundApp?.let { packageName ->
                        try {
                            val packageManager = applicationContext.packageManager
                            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
                        } catch (e: Exception) {
                            packageName
                        }
                    } ?: "this app"
                    
                    val customPrompt = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(SettingsActivity.PREF_CUSTOM_PROMPT, "") ?: ""
                    
                    // Show a loading indicator while generating the greeting
                    findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.VISIBLE
                    
                    // Generate a personalized greeting asynchronously
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val currentUsageMinutes = getCurrentUsageMinutes()
                            val greeting = aiService?.generateGreeting(appName, currentUsageMinutes, customPrompt)
                                ?: "How can I help you with using this app today?"
                            
                            // Hide loading indicator
                            findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.GONE
                            
                            // Add the generated greeting to the chat
                            chatAdapter.addMessage(ChatMessage(greeting, false))
                            scrollToLatestMessage()
                            
                            // Ensure scroll indicator is properly checked
                            val chatRecyclerView = findViewById<RecyclerView>(R.id.recyclerViewChat)
                            chatRecyclerView?.postDelayed({
                                chatRecyclerView.let { rv -> 
                                    checkAndShowScrollIndicator(rv)
                                }
                            }, 500) // Allow time for layout to settle
                        } catch (e: Exception) {
                            Log.e(TAG, "Error generating greeting", e)
                            
                            // Hide loading indicator
                            findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.GONE
                            
                            // Fall back to default message if there's an error
                            chatAdapter.addMessage(
                                ChatMessage(
                                    "How can I help you with using this app today?",
                                    false
                                )
                            )
                            scrollToLatestMessage()
                            
                            // Also check scroll indicator for fallback
                            val chatRecyclerView = findViewById<RecyclerView>(R.id.recyclerViewChat)
                            chatRecyclerView?.postDelayed({
                                chatRecyclerView.let { rv -> 
                                    checkAndShowScrollIndicator(rv)
                                }
                            }, 500) // Allow time for layout to settle
                        }
                    }
                }
            }
        }
    }

    /**
     * Hides the blocker overlay if it's currently showing
     */
    private fun hideBlockerIfShowing() {
        if (isOverlayShowing && blockerView != null) {
            try {
                windowManager.removeView(blockerView)
                isOverlayShowing = false
                Log.d(TAG, "Blocker overlay removed")
                
                // Clear the conversation history when overlay is closed
                aiService?.let { service ->
                    when (service) {
                        is RealOpenAIService -> {
                            service.clearConversationHistory()
                            Log.d(TAG, "OpenAI conversation thread cleared")
                        }
                        else -> {
                            // Handle any other potential AI service implementations
                            Log.d(TAG, "Unknown AI service type, conversation history may not be cleared")
                        }
                    }
                }
                
                // Reset the AI service and blocker view
                aiService = null
                blockerView = null
            } catch (e: Exception) {
                Log.e(TAG, "Error removing blocker overlay", e)
            }
        }
    }

    private fun handleSendClick() {
        val message = blockerView?.findViewById<TextInputEditText>(R.id.editTextMessage)?.text.toString().trim()
        
        // Check if debug mode is enabled
        val isDebugMode = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(SettingsActivity.PREF_DEBUG_MODE, false)
        
        if (isDebugMode) {
            // In debug mode, bypass AI evaluation and immediately grant access
            val grantDuration = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(SettingsActivity.PREF_ACCESS_DURATION, SettingsActivity.DEFAULT_ACCESS_DURATION)
            
            // Add a debug message to the chat
            chatAdapter.addMessage(ChatMessage("Debug mode: Access automatically granted", false))
            scrollToLatestMessage()
            
            // Grant temporary access
            currentForegroundApp?.let { packageName ->
                temporaryAccessManager.grantTemporaryAccess(packageName, grantDuration)
                
                // Start notification update timer
                handler.removeCallbacks(notificationUpdateRunnable)
                handler.post(notificationUpdateRunnable)
                
                // Show a confirmation button
                showContinueButton(grantDuration)
            }
            return
        }
        
        // Remove strict character count validation
        if (message.isBlank()) {
            Toast.makeText(
                this,
                "Please enter a message.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Add user message to chat
        chatAdapter.addMessage(ChatMessage(message, true))
        scrollToLatestMessage()
        blockerView?.findViewById<TextInputEditText>(R.id.editTextMessage)?.text?.clear()
        
        // Show loading indicator
        blockerView?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.VISIBLE
        
        // Get current app name
        val appName = currentForegroundApp?.let { packageName ->
            try {
                val packageManager = applicationContext.packageManager
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                packageName
            }
        } ?: "this app"
        
        // Get custom prompt from settings
        val customPrompt = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(SettingsActivity.PREF_CUSTOM_PROMPT, "") ?: ""
        
        // Use AI service to evaluate the request with coroutines
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val currentUsageMinutes = getCurrentUsageMinutes()
                // Use the stored AI service instance instead of creating a new one
                val result = aiService?.evaluateRequest(appName, message, currentUsageMinutes, customPrompt)
                    ?: throw Exception("AI service not initialized")
                
                // Hide loading indicator
                blockerView?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.GONE
                
                // Add AI response to chat
                chatAdapter.addMessage(ChatMessage(result.message, false))
                scrollToLatestMessage()
                
                if (result.approved) {
                    // Use the timeAllowed from the AI evaluation
                    val grantDuration = if (result.timeAllowed > 0) result.timeAllowed else {
                        // Fallback to settings if AI didn't specify a time
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .getInt(SettingsActivity.PREF_ACCESS_DURATION, SettingsActivity.DEFAULT_ACCESS_DURATION)
                    }
                    
                    // Grant temporary access
                    currentForegroundApp?.let { packageName ->
                        temporaryAccessManager.grantTemporaryAccess(packageName, grantDuration)
                        
                        // Start notification update timer
                        handler.removeCallbacks(notificationUpdateRunnable)
                        handler.post(notificationUpdateRunnable)
                        
                        // Show a confirmation button instead of auto-hiding
                        showContinueButton(grantDuration)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating request", e)
                // Hide loading indicator
                blockerView?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.GONE
                // Show error message
                chatAdapter.addMessage(ChatMessage("Sorry, I couldn't process your request. Please try again.", false))
                scrollToLatestMessage()
            }
        }
    }

    private fun showContinueButton(grantDuration: Int) {
        // Hide the input field and send button
        blockerView?.findViewById<View>(R.id.textInputLayoutMessage)?.visibility = View.GONE
        blockerView?.findViewById<View>(R.id.buttonSend)?.visibility = View.GONE
        
        // First modify the RecyclerView constraints to make room for our new elements
        val recyclerView = blockerView?.findViewById<RecyclerView>(R.id.recyclerViewChat)
        
        // Create and add a continue button
        val continueButton = Button(ContextThemeWrapper(this, R.style.Theme_MementoMode))
        continueButton.text = getString(R.string.continue_to_app)
        continueButton.setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
        continueButton.setTextColor(Color.WHITE)
        continueButton.setPadding(32, 16, 32, 16)
        continueButton.id = View.generateViewId() // Generate ID first so we can reference it
        
        // Set click listener to hide the overlay
        continueButton.setOnClickListener {
            hideBlockerIfShowing()
        }
        
        // Add a message about the granted access duration
        val accessGrantedMessage = TextView(ContextThemeWrapper(this, R.style.Theme_MementoMode))
        accessGrantedMessage.text = getString(R.string.temporary_access_granted, grantDuration)
        accessGrantedMessage.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
        accessGrantedMessage.textSize = 16f
        accessGrantedMessage.gravity = Gravity.CENTER
        accessGrantedMessage.id = View.generateViewId() // Generate ID first so we can reference it
        
        // Add the views to the layout
        val chatLayout = blockerView?.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.chatLayout)
        
        // Remove any existing progress bar
        blockerView?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.GONE
        
        // First add button to get a valid ID reference
        val buttonParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT,
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
        buttonParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        buttonParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        buttonParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        buttonParams.bottomMargin = 32
        chatLayout?.addView(continueButton, buttonParams)
        
        // Then add message
        val messageParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
        messageParams.bottomToTop = continueButton.id
        messageParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        messageParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        messageParams.bottomMargin = 16
        messageParams.marginStart = 16
        messageParams.marginEnd = 16
        chatLayout?.addView(accessGrantedMessage, messageParams)
        
        // Now update RecyclerView constraints to stop at the message instead of the input field
        recyclerView?.let {
            val recyclerParams = it.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            recyclerParams?.let { params ->
                params.bottomToTop = accessGrantedMessage.id
                params.bottomMargin = 24  // Add extra space between messages and access granted text
                it.layoutParams = params
                // Request layout to update
                it.requestLayout()
                // Scroll to show latest messages with new constraints
                scrollToLatestMessage()
            }
        }
    }

    private fun createServiceNotification(): Notification {
        val channelId = CHANNEL_ID
        
        val channel = NotificationChannel(
            channelId,
            "Memento Mode Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when Memento Mode is running"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, AppBlockerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Memento Mode")
            .setContentText("Monitoring to block selected apps")
            .setSmallIcon(R.drawable.ic_app_blocker)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_app_blocker, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    // Update scrolling behavior for the new layout orientation
    private fun scrollToLatestMessage() {
        try {
            val recyclerView = blockerView?.findViewById<RecyclerView>(R.id.recyclerViewChat)
            if (recyclerView == null) {
                Log.e(TAG, "Cannot scroll: RecyclerView is null")
                return
            }
            
            // Debug logging
            Log.d(TAG, "Attempting to scroll to latest message, item count: ${chatAdapter.itemCount}")
            
            if (chatAdapter.itemCount > 0) {
                val lastItemPosition = chatAdapter.itemCount - 1
                Log.d(TAG, "Will scroll to position $lastItemPosition")
                
                // Force measure and layout if needed
                if (recyclerView.measuredHeight <= 0) {
                    Log.d(TAG, "RecyclerView not measured yet, requesting layout")
                    recyclerView.measure(
                        View.MeasureSpec.makeMeasureSpec(recyclerView.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    recyclerView.requestLayout()
                }
                
                // Use post to ensure this happens after layout
                recyclerView.post {
                    try {
                        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                        val lastVisiblePosition = layoutManager?.findLastVisibleItemPosition() ?: -1
                        val lastCompletelyVisiblePosition = layoutManager?.findLastCompletelyVisibleItemPosition() ?: -1
                        
                        if (lastVisiblePosition < lastItemPosition || lastCompletelyVisiblePosition < lastItemPosition) {
                            // Scroll to show the latest message, ensuring we see the top of the message
                            recyclerView.smoothScrollToPosition(lastItemPosition)
                            Log.d(TAG, "Scrolling to latest message at position $lastItemPosition")
                        } else {
                            Log.d(TAG, "Latest message already visible, not scrolling")
                        }
                        
                        // Check if we need to show the scroll indicator for messages below
                        recyclerView.postDelayed({
                            checkAndShowScrollIndicator(recyclerView)
                        }, 300) // Small delay to let layout settle
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during scrolling", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in scrollToLatestMessage", e)
        }
    }
    
    // Check if all messages are visible and show scroll indicator if needed
    private fun checkAndShowScrollIndicator(recyclerView: RecyclerView) {
        try {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            
            // Check if there are messages below the current view that aren't visible
            val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
            val lastMessagePosition = chatAdapter.itemCount - 1
            
            // If the last visible item isn't the last item in the list,
            // show the scroll indicator to let the user know there are more messages below
            val hasMessagesBelow = lastVisiblePosition < lastMessagePosition
            showScrollIndicator(hasMessagesBelow)
            
            // Set up scroll listener to update indicator based on scroll position
            setupScrollListener(recyclerView)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking message visibility", e)
        }
    }
    
    /**
     * Get the current screen time usage in minutes for blocked apps today
     * Calculates the total time spent on blocked applications since midnight
     */
    private fun getCurrentUsageMinutes(): Int {
        try {
            Log.d(TAG, "Starting getCurrentUsageMinutes")
            
            // Load blocked apps to ensure the set is current
            try {
                loadBlockedApps()
                Log.d(TAG, "Loaded blocked apps: $blockedApps")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading blocked apps", e)
                // Continue anyway to make the function more resilient
            }
            
            // Get the usage stats manager
            val usageStatsManager = try {
                getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            } catch (e: Exception) {
                Log.e(TAG, "Error getting UsageStatsManager", e)
                // Return a default value if we can't get the usage stats manager
                return getDefaultUsageMinutes()
            }
            
            // Calculate the start time (midnight of the current day)
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            
            // Current time as end time
            val endTime = System.currentTimeMillis()
            
            Log.d(TAG, "Querying usage stats from ${java.util.Date(startTime)} to ${java.util.Date(endTime)}")
            
            var blockedTimeMs = 0L
            var totalTimeMs = 0L
            
            // SIMPLIFIED APPROACH: Use UsageStats directly which is more stable
            try {
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )
                
                // Log total apps found
                Log.d(TAG, "Found ${stats.size} apps with usage stats")
                
                // Filter for apps and sum the time
                stats.forEach { stat ->
                    if (stat.totalTimeInForeground > 0) {
                        try {
                            // Skip our own app and system apps
                            if (!isSystemAppSafe(stat.packageName)) {
                                // Add to total time regardless of whether it's blocked
                                totalTimeMs += stat.totalTimeInForeground
                                
                                // Add to blocked time only if it's a blocked app
                                if (blockedApps.contains(stat.packageName)) {
                                    blockedTimeMs += stat.totalTimeInForeground
                                    Log.d(TAG, "Blocked app ${stat.packageName} used for ${stat.totalTimeInForeground / 60000} minutes today")
                                }
                            }
                        } catch (e: Exception) {
                            // Skip this app if there's an error
                            Log.e(TAG, "Error processing app ${stat.packageName}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying usage stats", e)
                // Continue to try the events approach
            }
            
            // If we couldn't get any data, try the events approach
            if (totalTimeMs == 0L) {
                Log.d(TAG, "No stats found, trying events approach")
                
                try {
                    // Get usage events
                    val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
                    val event = UsageEvents.Event()
                    
                    // Track foreground sessions for apps
                    val sessionStartTimes = HashMap<String, Long>()
                    
                    while (usageEvents.hasNextEvent()) {
                        try {
                            usageEvents.getNextEvent(event)
                            val packageName = event.packageName
                            
                            // Skip our own app and system apps
                            if (packageName == this.packageName || isSystemAppSafe(packageName)) {
                                continue
                            }
                            
                            when (event.eventType) {
                                UsageEvents.Event.ACTIVITY_RESUMED -> {
                                    // App came to foreground, record start time
                                    sessionStartTimes[packageName] = event.timeStamp
                                }
                                UsageEvents.Event.ACTIVITY_PAUSED -> {
                                    // App went to background, calculate session duration
                                    val sessionStartTime = sessionStartTimes[packageName]
                                    if (sessionStartTime != null) {
                                        val sessionDuration = event.timeStamp - sessionStartTime
                                        if (sessionDuration > 0) {
                                            // Add to total screen time
                                            totalTimeMs += sessionDuration
                                            
                                            // Add to blocked time if it's a blocked app
                                            if (blockedApps.contains(packageName)) {
                                                blockedTimeMs += sessionDuration
                                                Log.d(TAG, "Blocked app $packageName used for ${sessionDuration/60000} minutes today")
                                            }
                                        }
                                        // Clear start time after calculating duration
                                        sessionStartTimes.remove(packageName)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Skip this event if there's an error
                            Log.e(TAG, "Error processing usage event", e)
                            continue
                        }
                    }
                    
                    // Handle any ongoing sessions (apps still in foreground)
                    val currentTime = System.currentTimeMillis()
                    for ((packageName, startTime) in sessionStartTimes) {
                        try {
                            val sessionDuration = currentTime - startTime
                            if (sessionDuration > 0) {
                                // Add to total screen time
                                totalTimeMs += sessionDuration
                                
                                // Add to blocked time if it's a blocked app
                                if (blockedApps.contains(packageName)) {
                                    blockedTimeMs += sessionDuration
                                    Log.d(TAG, "Ongoing session for blocked app $packageName: ${sessionDuration/60000} minutes")
                                }
                            }
                        } catch (e: Exception) {
                            // Skip this session if there's an error
                            Log.e(TAG, "Error processing ongoing session", e)
                            continue
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using events approach", e)
                }
            }
            
            // Convert to minutes
            val blockedMinutes = (blockedTimeMs / 60000).toInt()
            val totalMinutes = (totalTimeMs / 60000).toInt()
            
            // Log the results
            Log.d(TAG, "Total blocked apps usage today: $blockedMinutes minutes")
            Log.d(TAG, "Total screen time today: $totalMinutes minutes")
            
            // Store the values in shared preferences safely
            try {
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                    putInt("current_usage_minutes", blockedMinutes)
                    putInt("total_usage_minutes", totalMinutes)
                    apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error storing usage in preferences", e)
            }
            
            // Ensure we return at least 1 minute if we detected any usage at all
            return if (blockedMinutes == 0 && blockedTimeMs > 0) 1 else blockedMinutes
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating screen time usage", e)
            return getDefaultUsageMinutes()
        }
    }
    
    /**
     * Get a default value for usage minutes from preferences or hardcoded value
     */
    private fun getDefaultUsageMinutes(): Int {
        return try {
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt("current_usage_minutes", 30) // Default to 30 minutes
        } catch (e: Exception) {
            Log.e(TAG, "Error getting default usage minutes", e)
            30 // Hardcoded fallback
        }
    }
    
    /**
     * Safely check if the given package is a system app
     * @return true if it's a system app, launcher, or our own app
     */
    private fun isSystemAppSafe(packageName: String): Boolean {
        try {
            // Skip our own app
            if (packageName == this.packageName) return true
            
            // Skip Android system UI, launcher, and other system apps
            if (packageName.startsWith("com.android.systemui") ||
                packageName.startsWith("com.android.launcher") ||
                packageName.startsWith("com.google.android")) {
                return true
            }
            
            try {
                val pm = applicationContext.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                return (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (e: Exception) {
                return false
            }
        } catch (e: Exception) {
            // If we get an error, assume it's not a system app
            Log.e(TAG, "Error in isSystemAppSafe for $packageName", e)
            return false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Memento Mode Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Memento Mode is running"
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createTempAccessNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TEMP_ACCESS_CHANNEL_ID,
                "Temporary App Access",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows remaining time for temporary app access"
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startMonitoring() {
        isRunning = true
        handler.post(appCheckRunnable)
    }

    // ScrollListener instance to avoid creating multiple listeners
    private var scrollListener: RecyclerView.OnScrollListener? = null
    
    // Set up scroll listener to hide/show indicator based on scroll position
    private fun setupScrollListener(recyclerView: RecyclerView) {
        // Remove any existing listener to avoid duplicates
        if (scrollListener != null) {
            recyclerView.removeOnScrollListener(scrollListener!!)
        }
        
        // Create new scroll listener
        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                
                val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
                
                // Check if we can scroll down further to see more messages
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                val lastMessagePosition = chatAdapter.itemCount - 1
                val hasMessagesBelow = lastVisiblePosition < lastMessagePosition
                
                // Update indicator visibility
                showScrollIndicator(hasMessagesBelow)
            }
        }
        
        // Add the listener
        recyclerView.addOnScrollListener(scrollListener!!)
    }
    
    // Show or hide the scroll indicator
    private fun showScrollIndicator(show: Boolean) {
        // Get or create the scroll indicator
        var scrollIndicator = blockerView?.findViewById<ImageView>(R.id.scrollIndicatorImage)
        
        // If indicator doesn't exist yet, create it
        if (scrollIndicator == null && show) {
            val chatLayout = blockerView?.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.chatLayout) ?: return
            
            // Create indicator with our custom design
            scrollIndicator = ImageView(ContextThemeWrapper(this, R.style.Theme_MementoMode)).apply {
                id = R.id.scrollIndicatorImage
                setImageResource(R.drawable.ic_scroll_indicator) // Use our custom scroll indicator
                background = ContextCompat.getDrawable(context, R.drawable.bg_scroll_indicator)
                alpha = 0.9f
                visibility = View.GONE // Start hidden
                contentDescription = "Scroll down for more"
                elevation = resources.getDimension(R.dimen.default_elevation)
                
                // Add padding inside the circle
                setPadding(8, 8, 8, 8)
            }
            
            // Position at the BOTTOM of the chat
            val params = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                48, // Width in dp
                48  // Height in dp
            ).apply {
                bottomToTop = R.id.textInputLayoutMessage // Position above the text input
                endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                marginEnd = 16
                bottomMargin = 16
            }
            
            chatLayout.addView(scrollIndicator, params)
            
            // Add click listener to scroll DOWN when indicator is tapped
            scrollIndicator.setOnClickListener {
                val recyclerView = blockerView?.findViewById<RecyclerView>(R.id.recyclerViewChat) ?: return@setOnClickListener
                // Scroll to the last item when clicking the down indicator
                recyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                
                // Add a scale animation when clicked
                scrollIndicator.animate()
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(100)
                    .withEndAction {
                        scrollIndicator.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
            }
        }
        
        // Update visibility with a smooth animation
        if (scrollIndicator != null) {
            if (show && scrollIndicator.visibility != View.VISIBLE) {
                scrollIndicator.alpha = 0f
                scrollIndicator.visibility = View.VISIBLE
                scrollIndicator.animate()
                    .alpha(0.9f)
                    .setDuration(200)
                    .start()
            } else if (!show && scrollIndicator.visibility == View.VISIBLE) {
                scrollIndicator.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        scrollIndicator.visibility = View.GONE
                    }
                    .start()
            }
        }
        
        Log.d(TAG, "Scroll indicator visibility updated: ${if (show) "visible" else "gone"}")
    }
} 
# App Blocker

An Android application that helps users manage their screen time by blocking access to selected apps, with AI-powered temporary access requests.

## Features

- Block selected applications
- Request temporary access by explaining why you need it
- AI evaluation of access requests (using built-in algorithm or OpenAI API)
- Customizable settings for access duration and strictness

## Developer Configuration

### Setting up the OpenAI Developer API Key

For development and testing purposes, you can add a developer API key to the app:

1. Open `app/src/main/java/com/example/appblocker/config/ApiConfig.kt`
2. Replace the empty string in `DEVELOPER_API_KEY` with your OpenAI API key:

```kotlin
object ApiConfig {
    // Add your OpenAI API key here for development/testing
    const val DEVELOPER_API_KEY = "your-api-key-here"
    
    fun hasDevKey(): Boolean = DEVELOPER_API_KEY.isNotBlank()
}
```

When a developer key is provided, the API key input field will be hidden in the Settings UI, and the app will automatically use the developer key for OpenAI API calls.

### Setting up the OpenAI Assistant ID

For the thread-based conversation feature to work properly, you need to create an OpenAI Assistant and configure its ID:

1. Go to https://platform.openai.com/assistants
2. Click "Create Assistant"
3. Set "Name" to "App Blocker Assistant"
4. Select a model (GPT-4 or GPT-3.5-turbo)
5. Set "Instructions" to:
   ```
   You are an AI assistant that helps users manage their app usage time. 
   Your task is to evaluate their request and decide if they should be granted temporary access.
   Respond with a JSON object containing:
   1. 'approved': true or false
   2. 'message': A brief explanation of your decision
   Only respond with valid JSON. Do not include any other text.
   ```
6. Under "Tools", add a Function:
   - Name: evaluate_access_request
   - Description: Evaluates if the user should be granted access to the app
   - Parameters (JSON Schema):
   ```json
   {
     "type": "object",
     "properties": {
       "approved": {
         "type": "boolean",
         "description": "Whether to approve access to the app"
       },
       "message": {
         "type": "string",
         "description": "Explanation for the decision"
       }
     },
     "required": ["approved", "message"]
   }
   ```
7. Copy the assistant ID (starts with "asst_") and paste it in `ApiConfig.kt`:
   ```kotlin
   const val ASSISTANT_ID = "your-assistant-id-here"
   ```

Note: If the assistant ID is not configured, the app will create a new assistant on the first run, but it's more efficient to create one in advance and configure its ID.

## User Guide

### Blocking Apps

1. Open the App Blocker app
2. Select the apps you want to block
3. Start the blocking service

### Requesting Temporary Access

1. When you attempt to open a blocked app, a blocking screen will appear
2. Tap "Request Access"
3. Explain why you need access to the app
4. The AI will evaluate your request and grant access if appropriate

### Settings

- **Temporary Access Duration**: How long access is granted when approved
- **Recommended Characters**: Target length for access request explanations
- **Debug Mode**: Bypasses AI evaluation (for testing)
- **AI Evaluation Strictness**: Controls how strictly requests are evaluated
- **AI Provider**: Choose between built-in AI or external OpenAI service
- **API Key**: Enter your OpenAI API key (if using external AI service)

## Notes

- The app requires Usage Stats and Draw Over Other Apps permissions
- OpenAI API calls require an internet connection and a valid API key

## Requirements

- Android 6.0 (Marshmallow) or higher
- Usage Data Access permission
- Draw Over Other Apps permission

## How to Use

1. Launch the App Blocker application
2. Grant the required permissions when prompted
   - Usage Data Access: Allows the app to detect when a blocked app is launched
   - Draw Over Other Apps: Allows the app to show a blocker screen over blocked apps
3. Select the apps you want to block from the list
4. Click "Save" to save your selection
5. Click "Start Blocker" to activate the blocking service
6. When you try to open a blocked app, a screen will appear preventing you from using it

## Permissions

This app requires the following permissions:

- **PACKAGE_USAGE_STATS**: To detect when a blocked app is being launched
- **SYSTEM_ALERT_WINDOW**: To display the blocker overlay on top of blocked apps
- **QUERY_ALL_PACKAGES**: To list all installed applications

## Building the Project

This is a standard Android Studio project. To build:

1. Clone the repository
2. Open the project in Android Studio
3. Build and run on your device

## Future Enhancements

- Custom blocking schedules
- Password protection
- Usage statistics
- Block app categories 
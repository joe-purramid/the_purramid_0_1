// ClockActivity.kt
package com.example.purramid.thepurramid.clock

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.purramid.thepurramid.R // Import R if needed for logging/errors
import java.util.UUID // Keep UUID if generating IDs here, though service might handle it

/**
 * This Activity now primarily acts as an entry point to launch the ClockOverlayService.
 * It determines the target clock ID (or requests a new one) and starts the service.
 */
class ClockActivity : AppCompatActivity() {

    companion object {
        // Key to potentially pass a specific clock ID to launch
        const val EXTRA_TARGET_CLOCK_ID = "target_clock_id"
        private const val TAG = "ClockActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No layout needed if the activity finishes immediately
        // setContentView(R.layout.activity_clock) // Remove this

        Log.d(TAG, "onCreate - Launching Clock Service")

        // Determine which clock instance to start/show
        // Option 1: Always start a new clock when launched from MainActivity/Widget
        // Option 2: Check intent for a specific ID (e.g., if launched from a notification for a specific clock)
        val targetClockId = intent.getIntExtra(EXTRA_TARGET_CLOCK_ID, -1) // Use -1 to indicate "new" or "default"

        val serviceIntent = Intent(this, ClockOverlayService::class.java)

        if (targetClockId != -1) {
            // If a specific clock ID is provided, ensure the service starts and knows about it
            Log.d(TAG, "Requesting service start for specific clockId: $targetClockId")
            serviceIntent.action = ClockOverlayService.ACTION_START_CLOCK // Or a specific "show" action
            serviceIntent.putExtra(ClockOverlayService.EXTRA_CLOCK_ID, targetClockId)
        } else {
            // If no specific ID, request the service to add a new clock
            Log.d(TAG, "Requesting service to add a new clock")
            serviceIntent.action = ClockOverlayService.ACTION_ADD_NEW_CLOCK
            // The service will assign the ID in this case
        }

        // Start the service
        ContextCompat.startForegroundService(this, serviceIntent)

        // Finish the activity immediately after starting the service
        finish()
    }

    // Remove lifecycle methods (onResume, onPause, onDestroy) and UI logic
    // related to the old ClockView and buttons.
}
```

**Step 5.2: Refactor `ClockSettingsActivity.kt`**

This activity will now communicate changes via Intents to the `ClockOverlayService`, which will then update the appropriate `ClockViewModel`. We'll remove direct SharedPreferences access for settings *changes* but might keep it for *initial UI population* for simplicity, assuming the Service doesn't easily provide the initial state back to this separate Activity.


```kotlin
// src/main/kotlin/com/example/purramid/thepurramid/clock/ClockSettingsActivity.kt
package com.example.purramid.thepurramid.clock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings // Keep for overlay permission check if needed here
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.example.purramid.thepurramid.R
import java.time.ZoneId // Import ZoneId

// Import Service constants
import com.example.purramid.thepurramid.clock.ClockOverlayService.Companion.ACTION_ADD_NEW_CLOCK
import com.example.purramid.thepurramid.clock.ClockOverlayService.Companion.ACTION_NEST_CLOCK
import com.example.purramid.thepurramid.clock.ClockOverlayService.Companion.ACTION_UPDATE_CLOCK_SETTING
import com.example.purramid.thepurramid.clock.ClockOverlayService.Companion.EXTRA_CLOCK_ID
import com.example.purramid.thepurramid.clock.ClockOverlayService.Companion.EXTRA_NEST_STATE
import com.example.purramid.thepurramid.clock.ClockOverlayService.Companion.EXTRA_SETTING_TYPE
import com.example.purramid.thepurramid.clock.ClockOverlayService.Companion.EXTRA_SETTING_VALUE


/**
 * Activity to configure settings for a specific clock instance.
 * Communicates changes to the ClockOverlayService via Intents.
 */
class ClockSettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_UTC = 21203 // Keep for TimeZone activity result
        private const val TAG = "ClockSettingsActivity"
        // Key for the intent extra holding the clock ID to configure
        const val EXTRA_CLOCK_ID_CONFIG = "clock_id_to_configure"
    }

    // Keep UI element references
    private lateinit var modeToggleButton: ToggleButton
    private lateinit var colorPalette: LinearLayout
    private lateinit var twentyFourHourToggleButton: ToggleButton
    private lateinit var setTimeZoneButton: Button
    private lateinit var secondsToggleButton: ToggleButton
    private lateinit var setAlarmButton: Button
    private lateinit var nestToggleButton: ToggleButton
    private lateinit var addAnotherClockButton: Button
    // private lateinit var enableOverlayButton: Button // Removed, permission handled elsewhere usually

    private var currentClockId: Int = -1 // ID of the clock being configured
    private var selectedColor: Int = Color.WHITE // Keep for UI state
    private var selectedColorView: View? = null // Keep for UI state

    // Keep SharedPreferences for *reading* initial state to populate UI easily
    // Changes will be sent via Intent, not saved directly here.
    private lateinit var sharedPreferences: SharedPreferences

    // Color definitions (keep for UI setup)
    private val colors = listOf(
        Color.WHITE, Color.BLACK, 0xFFDAA520.toInt(), 0xFF008080.toInt(), 0xFFADD8E6.toInt(), 0xFFEE82EE.toInt()
    )
    private val outlineColors = listOf(
        Color.BLACK, Color.WHITE, Color.BLACK, Color.BLACK, Color.BLACK, Color.WHITE
    )

    // Keep TimeZone result launcher
    private val timeZoneResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedTimeZoneId = result.data?.getStringExtra("selected_time_zone_id")
            selectedTimeZoneId?.let {
                Log.d(TAG, "Time zone selected: $it for clock $currentClockId")
                // Send update intent to service
                sendUpdateIntent("time_zone", it)
                // Optionally update SharedPreferences for next time settings opens? Or rely on service state?
                // sharedPreferences.edit().putString("clock_${currentClockId}_time_zone_id", it).apply()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clock_settings)

        // Get the clock ID from the intent that started this activity
        currentClockId = intent.getIntExtra(EXTRA_CLOCK_ID_CONFIG, -1)
        if (currentClockId == -1) {
            Log.e(TAG, "Error: Clock ID not provided in intent.")
            Toast.makeText(this, "Error: Clock ID missing.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Log.d(TAG, "Configuring settings for clock ID: $currentClockId")

        // Initialize SharedPreferences for reading initial values
        sharedPreferences = getSharedPreferences("clock_settings", Context.MODE_PRIVATE)

        bindViews()
        loadInitialUiState() // Load initial state from SharedPreferences (or ideally query Service/DB)
        setupListeners()
        updateAddAnotherButtonState() // Update based on potentially global state
    }

    private fun bindViews() {
        modeToggleButton = findViewById(R.id.modeToggleButton)
        colorPalette = findViewById(R.id.colorPalette)
        twentyFourHourToggleButton = findViewById(R.id.twentyFourHourToggleButton)
        setTimeZoneButton = findViewById(R.id.setTimeZoneButton)
        secondsToggleButton = findViewById(R.id.secondsToggleButton)
        setAlarmButton = findViewById(R.id.setAlarmButton)
        nestToggleButton = findViewById(R.id.nestToggleButton)
        addAnotherClockButton = findViewById(R.id.addAnotherClockButton)
        // enableOverlayButton = findViewById(R.id.enableOverlayButton) // Removed
    }

    /** Loads initial UI state from SharedPreferences.
     * Ideally, this would observe the ViewModel or query the Service/DB,
     * but using SharedPreferences is simpler for now if direct VM access is hard.
     */
    private fun loadInitialUiState() {
        // Use stored values to set the initial state of UI controls
        val savedMode = sharedPreferences.getString("clock_${currentClockId}_mode", "digital")
        modeToggleButton.isChecked = (savedMode == "analog")

        selectedColor = sharedPreferences.getInt("clock_${currentClockId}_color", Color.WHITE)
        setupColorPalette() // Setup palette after loading color

        val is24Hour = sharedPreferences.getBoolean("clock_${currentClockId}_24hour", false)
        twentyFourHourToggleButton.isChecked = is24Hour

        val displaySeconds = sharedPreferences.getBoolean("clock_${currentClockId}_display_seconds", true)
        secondsToggleButton.isChecked = displaySeconds

        val isNested = sharedPreferences.getBoolean("clock_${currentClockId}_nest", false)
        nestToggleButton.isChecked = isNested
    }

    private fun setupColorPalette() {
        colorPalette.removeAllViews() // Clear previous views if any
        colors.forEachIndexed { index, colorValue ->
            val colorView = View(this).apply {
                // Use fixed size for color circles (adjust dp as needed)
                val sizeInDp = 40
                val sizeInPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, sizeInDp.toFloat(), resources.displayMetrics
                ).toInt()
                layoutParams = LinearLayout.LayoutParams(sizeInPx, sizeInPx).apply {
                    setMargins(8, 8, 8, 8) // Add margins
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorValue)
                    setStroke(2, outlineColors[index]) // Use predefined outline
                }
                setOnClickListener {
                    // Update selectedColor for UI feedback
                    selectedColor = colorValue
                    updateColorSelection(this)
                    // Send update intent to service
                    sendUpdateIntent("color", colorValue)
                    // Save locally for next time settings opens (optional)
                    // sharedPreferences.edit().putInt("clock_${currentClockId}_color", colorValue).apply()
                }
            }
            colorPalette.addView(colorView)
            // Set initial selection highlight
            if (colorValue == selectedColor) {
                updateColorSelection(colorView)
            }
        }
    }

    private fun setupListeners() {
        modeToggleButton.setOnCheckedChangeListener { _, isChecked ->
            val newMode = if (isChecked) "analog" else "digital"
            sendUpdateIntent("mode", newMode)
            // Optionally save locally
            // sharedPreferences.edit().putString("clock_${currentClockId}_mode", newMode).apply()
        }

        twentyFourHourToggleButton.setOnCheckedChangeListener { _, isChecked ->
            sendUpdateIntent("24hour", isChecked)
            // Optionally save locally
            // sharedPreferences.edit().putBoolean("clock_${currentClockId}_24hour", isChecked).apply()
        }

        setTimeZoneButton.setOnClickListener {
            val intent = Intent(this, TimeZoneGlobeActivity::class.java)
            // Pass current zone if needed by globe activity
            // val currentZone = sharedPreferences.getString("clock_${currentClockId}_time_zone_id", ZoneId.systemDefault().id)
            // intent.putExtra("current_time_zone_id", currentZone)
            timeZoneResultLauncher.launch(intent)
        }

        secondsToggleButton.setOnCheckedChangeListener { _, isChecked ->
            sendUpdateIntent("seconds", isChecked)
            // Optionally save locally
            // sharedPreferences.edit().putBoolean("clock_${currentClockId}_display_seconds", isChecked).apply()
        }

        setAlarmButton.setOnClickListener {
            try {
                startActivity(Intent(AlarmClock.ACTION_SET_ALARM))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open alarm app", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error starting alarm intent", e)
            }
        }

        nestToggleButton.setOnCheckedChangeListener { _, isChecked ->
            // Send specific NEST action intent
            val serviceIntent = Intent(this@ClockSettingsActivity, ClockOverlayService::class.java).apply {
                action = ACTION_NEST_CLOCK
                putExtra(EXTRA_CLOCK_ID, currentClockId)
                putExtra(EXTRA_NEST_STATE, isChecked)
            }
            ContextCompat.startForegroundService(this@ClockSettingsActivity, serviceIntent)
            // Optionally save locally
            // sharedPreferences.edit().putBoolean("clock_${currentClockId}_nest", isChecked).apply()
            // Close settings immediately? Or allow further changes? Let's keep it open for now.
            // finish()
        }

        addAnotherClockButton.setOnClickListener {
            // Send ADD action intent to the service
            val serviceIntent = Intent(this, ClockOverlayService::class.java).apply {
                action = ACTION_ADD_NEW_CLOCK
                // Optionally pass settings from the *current* clock to clone
                // intent.putExtra("clone_from_clock_id", currentClockId) // Service needs to handle this
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            // Close settings after requesting add?
            finish()
        }

    }

    /** Sends an update intent to the ClockOverlayService. */
    private fun sendUpdateIntent(settingType: String, value: Any) {
        if (currentClockId == -1) return // Don't send if ID is invalid

        val serviceIntent = Intent(this, ClockOverlayService::class.java).apply {
            action = ACTION_UPDATE_CLOCK_SETTING
            putExtra(EXTRA_CLOCK_ID, currentClockId)
            putExtra(EXTRA_SETTING_TYPE, settingType)
            // Add value based on type
            when (value) {
                is String -> putExtra(EXTRA_SETTING_VALUE, value)
                is Int -> putExtra(EXTRA_SETTING_VALUE, value)
                is Boolean -> putExtra(EXTRA_SETTING_VALUE, value)
                // Add other types if needed
                else -> {
                    Log.w(TAG, "Unsupported value type for setting '$settingType': ${value::class.java.name}")
                    return // Don't send intent if type is wrong
                }
            }
        }
        Log.d(TAG, "Sending update intent: clockId=$currentClockId, type=$settingType, value=$value")
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun updateColorSelection(newSelection: View) {
        // Remove highlight from previous selection
        selectedColorView?.background?.apply {
            if (this is GradientDrawable) {
                // Find original outline color based on background color
                val bgColor = colors.find { it == this.color?.defaultColor }
                val outlineColor = if (bgColor != null) outlineColors[colors.indexOf(bgColor)] else Color.DKGRAY
                setStroke(2, outlineColor) // Reset to original stroke
            }
        }
        // Apply highlight to new selection
        newSelection.background?.apply {
            if (this is GradientDrawable) {
                // Use a distinct highlight color, e.g., a bright blue or yellow
                setStroke(6, Color.CYAN) // Increase stroke width and change color
            }
        }
        selectedColorView = newSelection
    }

    // Update button state based on global count (might need a better way to get this)
    private fun updateAddAnotherButtonState() {
        // Reading from sharedPrefs here is a temporary measure.
        // Ideally, the service/ViewModel would provide this count.
        val numberOfClocks = sharedPreferences.getInt("active_clock_count", 1) // Read count saved by service
        addAnotherClockButton.isEnabled = numberOfClocks < 4
        addAnotherClockButton.alpha = if (addAnotherClockButton.isEnabled) 1.0f else 0.5f
    }

    override fun onResume() {
        super.onResume()
        // Refresh button state in case count changed while settings were paused
        updateAddAnotherButtonState()
        // Re-load UI state in case underlying data changed?
        // loadInitialUiState() // Re-populating UI might discard unsaved changes if any
    }
}

// ClockSettingsActivity.kt
package com.example.purramid.thepurramid.clock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.AlarmClock
import android.util.Log
import android.util.TypedValue // Import TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.ToggleButton
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
        // Use the key defined in ClockViewModel for consistency
        const val EXTRA_CLOCK_ID_CONFIG = ClockViewModel.KEY_CLOCK_ID
    }

    // UI element references
    private lateinit var modeToggleButton: ToggleButton
    private lateinit var colorPalette: LinearLayout
    private lateinit var twentyFourHourToggleButton: ToggleButton
    private lateinit var setTimeZoneButton: Button
    private lateinit var secondsToggleButton: ToggleButton
    private lateinit var setAlarmButton: Button
    private lateinit var nestToggleButton: ToggleButton
    private lateinit var addAnotherClockButton: Button

    private var currentClockId: Int = -1 // ID of the clock being configured
    private var selectedColor: Int = Color.WHITE // Keep for UI state
    private var selectedColorView: View? = null // Keep for UI state

    // Keep SharedPreferences for *reading* initial state to populate UI easily
    private lateinit var sharedPreferences: SharedPreferences

    // Color definitions (keep for UI setup)
    private val colors = listOf(
        Color.WHITE, Color.BLACK, 0xFFDAA520.toInt(), 0xFF008080.toInt(), 0xFFADD8E6.toInt(), 0xFFEE82EE.toInt()
    )
    private val outlineColors = listOf(
        Color.BLACK, Color.WHITE, Color.BLACK, Color.BLACK, Color.BLACK, Color.WHITE
    )

    // TimeZone result launcher
    private val timeZoneResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedTimeZoneId = result.data?.getStringExtra("selected_time_zone_id")
            selectedTimeZoneId?.let {
                Log.d(TAG, "Time zone selected: $it for clock $currentClockId")
                // Send update intent to service
                sendUpdateIntent("time_zone", it)
                // Save locally ONLY to repopulate settings UI if reopened before service state fully updates it
                sharedPreferences.edit().putString("clock_${currentClockId}_time_zone_id", it).apply()
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
        // Using a specific name related to the clock feature
        sharedPreferences = getSharedPreferences("clock_settings_ui_prefs", Context.MODE_PRIVATE)

        bindViews()
        loadInitialUiState() // Load initial state from SharedPreferences
        setupListeners()
        updateAddAnotherButtonState() // Update based on potentially global state (read from prefs for now)
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
    }

    /** Loads initial UI state from SharedPreferences. */
    private fun loadInitialUiState() {
        // Use stored values to set the initial state of UI controls
        // These keys should match what the ViewModel/Service *might* save for UI hints,
        // or use defaults if no hints are saved.
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
        colorPalette.removeAllViews()
        colors.forEachIndexed { index, colorValue ->
            val colorView = View(this).apply {
                val sizeInDp = 40
                val sizeInPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, sizeInDp.toFloat(), resources.displayMetrics
                ).toInt()
                layoutParams = LinearLayout.LayoutParams(sizeInPx, sizeInPx).apply {
                    setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4)) // Adjusted margins
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorValue)
                    setStroke(dpToPx(1), outlineColors[index]) // Use dpToPx for stroke
                }
                setOnClickListener {
                    selectedColor = colorValue
                    updateColorSelection(this)
                    sendUpdateIntent("color", colorValue)
                    // Save locally for UI state persistence
                    sharedPreferences.edit().putInt("clock_${currentClockId}_color", colorValue).apply()
                }
            }
            colorPalette.addView(colorView)
            if (colorValue == selectedColor) {
                updateColorSelection(colorView) // Highlight initial selection
            }
        }
    }

    private fun setupListeners() {
        modeToggleButton.setOnCheckedChangeListener { _, isChecked ->
            val newMode = if (isChecked) "analog" else "digital"
            sendUpdateIntent("mode", newMode)
            // Save locally for UI state persistence
            sharedPreferences.edit().putString("clock_${currentClockId}_mode", newMode).apply()
        }

        twentyFourHourToggleButton.setOnCheckedChangeListener { _, isChecked ->
            sendUpdateIntent("24hour", isChecked)
            sharedPreferences.edit().putBoolean("clock_${currentClockId}_24hour", isChecked).apply()
        }

        setTimeZoneButton.setOnClickListener {
            val intent = Intent(this, TimeZoneGlobeActivity::class.java)
            // Pass current zone ID to the globe activity for initial selection
            val currentZoneId = sharedPreferences.getString("clock_${currentClockId}_time_zone_id", ZoneId.systemDefault().id)
            intent.putExtra("current_time_zone_id", currentZoneId)
            timeZoneResultLauncher.launch(intent)
        }

        secondsToggleButton.setOnCheckedChangeListener { _, isChecked ->
            sendUpdateIntent("seconds", isChecked)
            sharedPreferences.edit().putBoolean("clock_${currentClockId}_display_seconds", isChecked).apply()
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
            sharedPreferences.edit().putBoolean("clock_${currentClockId}_nest", isChecked).apply()
        }

        addAnotherClockButton.setOnClickListener {
            val serviceIntent = Intent(this, ClockOverlayService::class.java).apply {
                action = ACTION_ADD_NEW_CLOCK
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            finish() // Close settings after requesting add
        }
    }

    /** Sends an update intent to the ClockOverlayService. */
    private fun sendUpdateIntent(settingType: String, value: Any) {
        if (currentClockId == -1) return

        val serviceIntent = Intent(this, ClockOverlayService::class.java).apply {
            action = ACTION_UPDATE_CLOCK_SETTING
            putExtra(EXTRA_CLOCK_ID, currentClockId)
            putExtra(EXTRA_SETTING_TYPE, settingType)
            when (value) {
                is String -> putExtra(EXTRA_SETTING_VALUE, value)
                is Int -> putExtra(EXTRA_SETTING_VALUE, value)
                is Boolean -> putExtra(EXTRA_SETTING_VALUE, value)
                else -> {
                    Log.w(TAG, "Unsupported value type for setting '$settingType'")
                    return
                }
            }
        }
        Log.d(TAG, "Sending update intent: clockId=$currentClockId, type=$settingType, value=$value")
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun updateColorSelection(newSelection: View) {
        selectedColorView?.background?.apply {
            if (this is GradientDrawable) {
                val bgColor = colors.find { it == this.color?.defaultColor }
                val outlineColor = if (bgColor != null) outlineColors[colors.indexOf(bgColor)] else Color.DKGRAY
                setStroke(dpToPx(1), outlineColor) // Reset to original thin stroke
            }
        }
        newSelection.background?.apply {
            if (this is GradientDrawable) {
                setStroke(dpToPx(3), Color.CYAN) // Apply thicker highlight stroke
            }
        }
        selectedColorView = newSelection
    }

    // Update button state based on global count (still reads from prefs as placeholder)
    private fun updateAddAnotherButtonState() {
        val numberOfClocks = sharedPreferences.getInt("active_clock_count", 1)
        addAnotherClockButton.isEnabled = numberOfClocks < 4
        addAnotherClockButton.alpha = if (addAnotherClockButton.isEnabled) 1.0f else 0.5f
    }

    override fun onResume() {
        super.onResume()
        updateAddAnotherButtonState()
        // Consider reloading UI state if it might have changed externally
        // loadInitialUiState() // Be cautious about discarding user edits
    }

    // Helper for dp to px conversion
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}

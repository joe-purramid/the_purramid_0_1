// ClockSettingsActivity.kt
package com.example.thepurramid0_1

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
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

class ClockSettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_UTC = 21203
        const val ACTION_NEST_CLOCK = "com.example.thepurramid0_1.ACTION_NEST_CLOCK"
        const val EXTRA_CLOCK_ID = "clock_id"
        const val EXTRA_NEST_STATE = "nest_state"
        const val ACTION_ADD_NEW_CLOCK = "com.example.thepurramid0_1.ACTION_ADD_NEW_CLOCK"
        const val ACTION_UPDATE_CLOCK_SETTING = "com.example.thepurramid0_1.ACTION_UPDATE_CLOCK_SETTING"
        const val EXTRA_SETTING_TYPE = "setting_type"
        const val EXTRA_SETTING_VALUE = "setting_value"
    }

    private lateinit var overlayPermissionResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var modeToggleButton: ToggleButton
    private lateinit var colorPalette: LinearLayout
    private lateinit var twentyFourHourToggleButton: ToggleButton // New ToggleButton for 24-Hour Clock
    private lateinit var setTimeZoneButton: Button
    private lateinit var secondsToggleButton: ToggleButton
    private lateinit var setAlarmButton: Button
    private lateinit var nestToggleButton: ToggleButton
    private lateinit var addAnotherClockButton: Button
    private lateinit var enableOverlayButton: Button
    private var selectedColor: Int = Color.WHITE
    private var currentClockId: Int = -1 // To hold the ID of the clock being configured
    private var selectedColorView: View? = null

    private val colors = listOf(
        Color.WHITE,
        Color.BLACK,
        0xFFDAA520.toInt(), // Goldenrod
        0xFF008080.toInt(), // Teal
        0xFFADD8E6.toInt(), // Light Blue
        0xFFEE82EE.toInt()  // Violet
    )

    private val outlineColors = listOf(
        Color.BLACK,
        Color.WHITE,
        Color.BLACK,
        Color.BLACK,
        Color.BLACK,
        Color.WHITE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clock_settings)

        currentClockId = intent.getIntExtra("clock_id", -1)
        if (currentClockId == -1) {
            // Handle error: No clock ID provided
            Toast.makeText(this, "Error: Clock ID not provided.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        sharedPreferences = getSharedPreferences("clock_settings", Context.MODE_PRIVATE)
        modeToggleButton = findViewById(R.id.modeToggleButton)
        colorPalette = findViewById(R.id.colorPalette)
        twentyFourHourToggleButton = findViewById(R.id.twentyFourHourToggleButton) // Initialize the new toggle
        setTimeZoneButton = findViewById(R.id.setTimeZoneButton)
        secondsToggleButton = findViewById(R.id.secondsToggleButton)
        setAlarmButton = findViewById(R.id.setAlarmButton)
        nestToggleButton = findViewById(R.id.nestToggleButton)
        addAnotherClockButton = findViewById(R.id.addAnotherClockButton)
        enableOverlayButton = findViewById(R.id.enableOverlayButton)

        // Initialize ActivityResultLauncher for overlay permission
        overlayPermissionResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this@ClockSettingsActivity)) {
                    startClockOverlayService()
                } else {
                    Toast.makeText(this@ClockSettingsActivity, getString(R.string.overlay_permission_denied), Toast.LENGTH_SHORT).show()
                }
            } else {
                startClockOverlayService()
            }
        }

        // Load saved mode
        val savedMode = sharedPreferences.getString("clock_mode", "digital")
        modeToggleButton.isChecked = (savedMode == "analog")
        modeToggleButton.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putString("clock_mode", if (isChecked) "analog" else "digital").apply()
            sendUpdateIntent("mode", if (isChecked) "analog" else "digital")
        }

        // Load saved color
        selectedColor = sharedPreferences.getInt("app_color", Color.WHITE)
        colors.forEachIndexed { (colorNameResId, colorValue) ->
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 60).apply { weight = 1f; setMargins(8, 0, 8, 0) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorValue)
                    setStroke(2, outlineColors[index])
                }
                setOnClickListener {
                    sharedPreferences.edit().putInt("clock_${currentClockId}_color", colorValue).apply()
                    sendUpdateIntent("color", colorValue)
                    updateColorSelection(this)
                }
            colorPalette.addView(colorView)
        }
        setSelectedColorView(selectedColor) // Set initial color selection

        // Load saved 24-hour preference
        val is24Hour = sharedPreferences.getBoolean("clock_${currentClockId}_24hour", false)
        twentyFourHourToggleButton.isChecked = is24Hour
        twentyFourHourToggleButton.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("clock_${currentClockId}_24hour", isChecked).apply()
            sendUpdateIntent("24hour", isChecked)
        }

        // Load time zone preference
        setTimeZoneButton.setOnClickListener {
            val intent = Intent(this, TimeZoneGlobeActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_UTC)
        }

        // Load saved seconds preference
        val displaySeconds = sharedPreferences.getBoolean("display_seconds", false)
        secondsToggleButton.isChecked = displaySeconds
        secondsToggleButton.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("display_seconds", isChecked).apply()
            sendUpdateIntent("seconds", isChecked)
        }

        // Load saved nest preference for the current clock (if we decide to store it per clock)
        val isNested = sharedPreferences.getBoolean("clock_${currentClockId}_nest", false)
        nestToggleButton.isChecked = isNested
        nestToggleButton.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("clock_${currentClockId}_nest", isChecked).apply()

            // Send an intent to the service to toggle the nest mode for this specific clock
            val serviceIntent = Intent(this@ClockSettingsActivity, ClockOverlayService::class.java).apply {
                action = ACTION_NEST_CLOCK
                putExtra(EXTRA_CLOCK_ID, currentClockId)
                putExtra(EXTRA_NEST_STATE, isChecked)
            }
            ContextCompat.startForegroundService(this@ClockSettingsActivity, serviceIntent)
            finish() // Close settings immediately on Nest toggle
        }

        setAlarmButton.setOnClickListener {
            startActivity(Intent(AlarmClock.ACTION_SET_ALARM))
   }

        addAnotherClockButton.setOnClickListener {
            // Get current settings
            val currentMode = if (modeToggleButton.isChecked) "analog" else "digital"
            val currentColor = sharedPreferences.getInt("app_color", Color.WHITE)
            val is24HourForNew = twentyFourHourToggleButton.isChecked // Get current 24-hour setting
            val currentTimeZoneId = sharedPreferences.getString("time_zone_id", java.util.TimeZone.getDefault().id)
            val displaySeconds = secondsToggleButton.isChecked
            val isNested = nestToggleButton.isChecked

            // Send an intent to the service to create a new clock with these settings
            val serviceIntent = Intent(this, ClockOverlayService::class.java).apply {
                action = ACTION_ADD_NEW_CLOCK
                putExtra("cloned_mode", currentMode)
                putExtra("cloned_color", currentColor)
                putExtra("cloned_24hour", is24HourForNew) // Pass the 24-hour setting
                putExtra("cloned_time_zone_id", currentTimeZoneId)
                putExtra("cloned_display_seconds", displaySeconds)
                putExtra("cloned_nest_clock", isNested)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            finish() // Close the settings view after adding a new clock
        }

        enableOverlayButton.setOnClickListener {
            requestOverlayPermission()
        }

        // Initial check for the number of active clocks
        updateAddAnotherButtonState()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_UTC && resultCode == RESULT_OK) {
            val selectedTimeZoneId = data?.getStringExtra("selected_time_zone_id")
            selectedTimeZoneId?.let {
                sharedPreferences.edit().putString("time_zone_id", it).apply()
                sendUpdateIntent("time_zone", it)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check the number of active clocks and update the "Add Another" button state
        updateAddAnotherButtonState()
        // Load the current nest state for this clock when the settings activity resumes
        val isNested = sharedPreferences.getBoolean("clock_${currentClockId}_nest", false)
        nestToggleButton.isChecked = isNested
        setSelectedColorView(sharedPreferences.getInt("clock_${currentClockId}_color", Color.WHITE))
    }

    private fun updateAddAnotherButtonState() {
        val numberOfClocks = sharedPreferences.getInt("active_clock_count", 1) // Default to 1 if none tracked
        addAnotherClockButton.isEnabled = numberOfClocks < 4
        addAnotherClockButton.alpha = if (addAnotherClockButton.isEnabled) 1.0f else 0.5f
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.Builder()
                    .scheme("package")
                    .authority(packageName)
                    .build()
            }
            overlayPermissionResultLauncher.launch(intent)
        } else {
            startClockOverlayService()
        }
    }

    private fun startClockOverlayService() {
        // Start with a single default clock on initial service start
        val serviceIntent = Intent(this, ClockOverlayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        updateAddAnotherButtonState() // Update button state when service starts (initial clock)
    }

    private fun sendUpdateIntent(settingType: String, value: Any) {
        val serviceIntent = Intent(this, ClockOverlayService::class.java).apply {
            action = ACTION_UPDATE_CLOCK_SETTING
            putExtra(EXTRA_CLOCK_ID, currentClockId)
            putExtra(EXTRA_SETTING_TYPE, settingType)
            when (value) {
                is String -> putExtra(EXTRA_SETTING_VALUE, value)
                is Int -> putExtra(EXTRA_SETTING_VALUE, value)
                is Boolean -> putExtra(EXTRA_SETTING_VALUE, value)
            }
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun updateColorSelection(newSelection: View) {
        selectedColorView?.background?.apply {
            if (this is GradientDrawable) {
                setStroke(2, outlineColors[colorPalette.indexOfChild(selectedColorView!!)])
            }
        }
        newSelection.background?.apply {
            if (this is GradientDrawable) {
                setStroke(6, Color.TRANSPARENT) // Larger transparent stroke for selection circle
                val selectionDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(4, Color.BLACK) // Black outline for selection
                }
                val combinedDrawable = android.graphics.drawable.LayerDrawable(arrayOf(this, selectionDrawable))
                newSelection.background = combinedDrawable
            }
        }
        selectedColorView = newSelection
    }

    private fun setSelectedColorView(color: Int) {
        colorPalette.children.forEach { view ->
            val backgroundColor = (view.background as? GradientDrawable)?.color?.defaultColor
            if (backgroundColor == color) {
                updateColorSelection(view)
                return
            }
        }
        if (selectedColorView == null && colorPalette.childCount > 0) {
            updateColorSelection(colorPalette.getChildAt(0))
        }
    }

    private fun restartClockService(clockId: Int) {
        val selectedMode = sharedPreferences.getString("clock_${clockId}_mode", "digital") ?: "digital"
        val selectedColor = sharedPreferences.getInt("clock_${clockId}_color", Color.WHITE)
        val is24Hour = sharedPreferences.getBoolean("clock_${clockId}_24hour", false) // Get the 24-hour setting
        val selectedTimeZoneId = sharedPreferences.getString("clock_${clockId}_time_zone_id", java.util.TimeZone.getDefault().id)
        val displaySeconds = sharedPreferences.getBoolean("clock_${clockId}_display_seconds", false)
        val isNested = sharedPreferences.getBoolean("clock_${clockId}_nest", false)

        val serviceIntent = Intent(this, ClockOverlayService::class.java).apply {
            putExtra("clock_mode", selectedMode)
            putExtra("app_color", selectedColor)
            putExtra("is_24_hour", is24Hour) // Pass the 24-hour setting
            putExtra("time_zone_id", selectedTimeZoneId)
            putExtra("display_seconds", displaySeconds)
            putExtra("nest_clock", isNested)
            putExtra("clock_id_to_update", clockId) // Indicate which clock to update
            action = "com.example.thepurramid0_1.ACTION_UPDATE_CLOCK" // New action for updating
        }
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}
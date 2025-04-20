// ClockOverlayService.kt (Refactored - Step 2b: Handle Button Row)
package com.example.purramid.thepurramid // Use your package name (Alphabetized Imports)

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton // Added for new buttons
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.SVGImageView // If using SVG approach
import com.example.purramid.thepurramid.ClockView
import java.time.LocalTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.ConcurrentHashMap // Use ConcurrentHashMap for thread safety
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ClockOverlayService : Service(), ClockView.ClockInteractionListener {

    private lateinit var windowManager: WindowManager
    private lateinit var sharedPreferences: SharedPreferences

    // Used ConcurrentHashMap for thread safety
    private val activeClockViews = ConcurrentHashMap<Int, ViewGroup>() // Pair of ID and Root View ViewGroup
    private val clockViewInstances = ConcurrentHashMap<Int, ClockView>() // Map ID to ClockView instance
    private val clockLayoutParams = ConcurrentHashMap<Int, WindowManager.LayoutParams>() // Store LayoutParams per clock

    // --- State Management ---
    private data class ClockState(
        var currentTime: LocalTime = LocalTime.MIN, // Current time displayed
        var timeZoneId: ZoneId = ZoneId.systemDefault(),
        var isPaused: Boolean = false,
        var displaySeconds: Boolean = true,
        var is24Hour: Boolean = false,
        var clockColor: Int = Color.WHITE,
        var mode: String = "digital", // "digital" or "analog"
        var isNested: Boolean = false
    )
    private val clockStates = ConcurrentHashMap<Int, ClockState>() // Store state per clock ID
    private val handler = Handler(Looper.getMainLooper()) // Central ticker handler
    private var isTickerRunning = false
    // --- End State Management ---

    // Other properties
    private val clockCounter = AtomicInteger(0)
    private val nestedClockPositions = ConcurrentHashMap<Int, Int>() // ClockId to its nested column index
    private var lastTouchDownTime: Long = 0
    private var hasMovedBeyondSlopForTap: Boolean = false // Track if drag occurred during potential tap

    companion object {
        private const val TAG = "ClockOverlayService"
        private const val TICK_INTERVAL_MS = 1000L
        // Actions from SettingsActivity (ensure these match exactly)
        const val ACTION_NEST_CLOCK = "com.example.purramid.thepurramid.ACTION_NEST_CLOCK"
        const val EXTRA_CLOCK_ID = "clock_id"
        const val EXTRA_NEST_STATE = "nest_state"
        const val ACTION_ADD_NEW_CLOCK = "com.example.purramid.thepurramid.ACTION_ADD_NEW_CLOCK"
        const val ACTION_UPDATE_CLOCK_SETTING = "com.example.purramid.thepurramid.ACTION_UPDATE_CLOCK_SETTING"
        const val EXTRA_SETTING_TYPE = "setting_type"
        const val EXTRA_SETTING_VALUE = "setting_value"
        // Actions for Buttons
        const val ACTION_PAUSE_CLOCK = "com.example.purramid.thepurramid.ACTION_PAUSE_CLOCK"
        const val ACTION_PLAY_CLOCK = "com.example.purramid.thepurramid.ACTION_PLAY_CLOCK"
        const val ACTION_RESET_CLOCK = "com.example.purramid.thepurramid.ACTION_RESET_CLOCK"
        const val ACTION_OPEN_SETTINGS = "com.example.purramid.thepurramid.ACTION_OPEN_SETTINGS"
    }

    override fun onBind(intent: Intent?): IBinder? { return null }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sharedPreferences = getSharedPreferences("clock_settings", Context.MODE_PRIVATE)
        startInForeground()
        loadActiveClocksFromPrefs()
        if (clockStates.isEmpty()) {
            createNewClock(null)
        } else {
            startTickerIfNecessary()
        }
        updateActiveClockCountInPrefs()
    }

    // --- Central Ticker Logic ---
    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (clockStates.isEmpty()) {
                stopTicker()
                return
            }
            clockStates.forEach { (id, state) ->
                if (!state.isPaused) {
                    try {
                        val newTime = state.currentTime.plusSeconds(1)
                        state.currentTime = newTime

                        // Find the view and update its display
                        clockViewInstances[id]?.updateDisplayTime(newTime)
                    } catch (e: Exception) { Log.e(TAG, "Error updating time for clock $id", e) }
                }
                // Else: If paused, do nothing, time remains as it was (potentially manually set)
            }
            // Schedule the next tick
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }
    private fun startTickerIfNecessary() {
        if (!isTickerRunning && !clockStates.isEmpty()) {
            Log.d(TAG, "Starting ticker.")
            isTickerRunning = true
            handler.post(tickerRunnable)
        }
    }
    private fun stopTicker() {
         if (isTickerRunning) {
            Log.d(TAG, "Stopping ticker.")
            isTickerRunning = false
            handler.removeCallbacks(tickerRunnable)
        }
    }
    // --- End Ticker Logic ---

    private fun startInForeground() {
        val notificationId = 1
        val channelId = "clock_overlay_channel"
        val channelName = "Clock Overlay Service"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Floating clock(s) active")
            .setSmallIcon(R.drawable.ic_clock)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(notificationId, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received action: ${intent?.action}")
        when (intent?.action) {
            ACTION_ADD_NEW_CLOCK -> handleAddNewClock(intent)
            ACTION_UPDATE_CLOCK_SETTING -> handleUpdateClockSetting(intent)
            ACTION_NEST_CLOCK -> handleNestClock(intent)
            // Actions from Buttons
            ACTION_PAUSE_CLOCK -> handlePauseClock(intent)
            ACTION_PLAY_CLOCK -> handlePlayClock(intent)
            ACTION_RESET_CLOCK -> handleResetClock(intent)
            ACTION_OPEN_SETTINGS -> handleOpenSettings(intent)
            else -> { startTickerIfNecessary(); Log.w(TAG, "Unhandled or null action received.") }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called.")
        stopTicker()
        activeClockViews.keys.toList().forEach { id -> removeClockInternal(id, activeClockViews[id], false) }
        activeClockViews.clear()
        clockViewInstances.clear()
        clockLayoutParams.clear()
        clockStates.clear()
        nestedClockPositions.clear()
    }

    // --- Action Handling Methods ---
    private fun handleAddNewClock(intent: Intent?) {
         if (clockStates.size >= 4) { Toast.makeText(this, "Maximum of 4 clocks reached.", Toast.LENGTH_SHORT).show(); return }
         createNewClock(intent)
         updateActiveClockCountInPrefs()
    }
    private fun handleUpdateClockSetting(intent: Intent?) {
        val clockId = intent?.getIntExtra(EXTRA_CLOCK_ID, -1) ?: -1
        val settingType = intent?.getStringExtra(EXTRA_SETTING_TYPE)
        val state = clockStates[clockId]

        when (settingType) {
            "mode" -> {
                val newMode = intent.getStringExtra(EXTRA_SETTING_VALUE) ?: state.mode
                if (newMode != state.mode) {
                    Log.d(TAG, "Mode changed for clock $clockId to $newMode. Re-inflating view.")

                    // --- Apply Rule: Reset time before changing mode ---
                    try {
                        state.currentTime = LocalTime.now(state.timeZoneId)
                        state.isPaused = false // Ensure not paused after mode change
                    } catch (e: Exception) { Log.e(TAG, "Error getting time on mode change reset", e) }
                    // --- End Reset ---

                    state.mode = newMode // Update state

                    // Get references needed to remove the old view and add the new one
                    val currentRootView = activeClockViews[clockId]
                    val currentParams = clockLayoutParams[clockId]
                    if (currentRootView == null || currentParams == null) {
                        Log.e(TAG, "Cannot re-inflate, missing current view or params for clock $clockId")
                        return // Exit if something is wrong
                    }
                }

                // --- Re-inflation Logic ---
                // 1. Remove the old view
                try {
                    windowManager.removeView(currentRootView)
                    Log.d(TAG, "Removed old view for clock $clockId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing old view during mode change for $clockId", e)
                }
                activeClockViews.remove(clockId)
                clockViewInstances.remove(clockId)

                // 2. Inflate and set up the new view using the helper
                val newViewPair = inflateAndSetupClockView(clockId, state, currentParams)

                if (newViewPair != null) {
                    val (newRootView, newClockView) = newViewPair

                // 3. Add the new view
                try {
                    windowManager.addView(newRootView, currentParams)
                    Log.d(TAG, "Added new view for clock $clockId")
                    // 4. Update maps with new references
                    activeClockViews[clockId] = newRootView
                    clockViewInstances[clockId] = newClockView
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding new view during mode change for $clockId", e)
                    removeClockInternal(clockId, null, true) // Attempt cleanup
                    }
                } else {
                    Log.e(TAG, "Failed to inflate/setup new view for clock $clockId during mode change.")
                    removeClockInternal(clockId, null, true) // Attempt cleanup
                }
                // --- End Re-inflation Logic ---
            }

            "color" -> {
                val view = clockViewInstances[clockId] ?: return
                val color = intent.getIntExtra(EXTRA_SETTING_VALUE, state.clockColor)
                state.clockColor = color
                view.setClockColor(color)
            }
            "24hour" -> {
                val view = clockViewInstances[clockId] ?: return
                val is24Hour = intent.getBooleanExtra(EXTRA_SETTING_VALUE, state.is24Hour)
                state.is24Hour = is24Hour
                view.setIs24HourFormat(is24Hour)
            }
            "time_zone" -> {
                 val view = clockViewInstances[clockId] ?: return
                 val zoneIdString = intent.getStringExtra(EXTRA_SETTING_VALUE) ?: state.timeZoneId.id
                 try {
                     val zoneId = ZoneId.of(zoneIdString)
                     state.timeZoneId = zoneId
                     view.setClockTimeZone(zoneId)
                     if (!state.isPaused) {
                         state.currentTime = LocalTime.now(zoneId)
                         view.updateDisplayTime(state.currentTime)
                     }
                 } catch (e: Exception) { Log.e(TAG, "Invalid Zone ID: $zoneIdString", e) }
            }
            "seconds" -> {
                val view = clockViewInstances[clockId] ?: return
                val displaySeconds = intent.getBooleanExtra(EXTRA_SETTING_VALUE, state.displaySeconds)
                state.displaySeconds = displaySeconds
                view.setDisplaySeconds(displaySeconds)
            }
            else -> Log.w(TAG, "Unknown setting type: $settingType")
        }
        saveSpecificClockSetting(clockId, settingType, intent)
    }

    private fun handleNestClock(intent: Intent?) {
        val clockId = intent?.getIntExtra(EXTRA_CLOCK_ID, -1) ?: -1
        val shouldBeNested = intent?.getBooleanExtra(EXTRA_NEST_STATE, false) ?: false
        val state = clockStates[clockId]
        val rootView = activeClockViews[clockId]

        if (clockId != -1 && state != null && rootView != null) {
            Log.d(TAG,"Setting nest state for clock $clockId to $shouldBeNested")
            if (state.isNested != shouldBeNested) {
                state.isNested = shouldBeNested
                applyNestModeVisuals(clockId, rootView, shouldBeNested)
                repositionNestedClocks()
                sharedPreferences.edit().putBoolean("clock_${clockId}_nest", shouldBeNested).apply()
            }
        } else { Log.e(TAG, "Invalid data for ACTION_NEST_CLOCK") }
    }

    private fun handlePauseClock(intent: Intent?) {
        val clockId = intent?.getIntExtra(EXTRA_CLOCK_ID, -1) ?: -1
        clockStates[clockId]?.let {
            if (!it.isPaused) {
                it.isPaused = true
                Log.d(TAG, "Paused clock $clockId")
                updatePlayPauseButton(clockId)
            }
        }
    }

    private fun handlePlayClock(intent: Intent?) {
        val clockId = intent?.getIntExtra(EXTRA_CLOCK_ID, -1) ?: -1
        clockStates[clockId]?.let {
            if (it.isPaused) {
                it.isPaused = false
                Log.d(TAG, "Playing clock $clockId")
                updatePlayPauseButton(clockId)
            }
        }
    }

    private fun handleResetClock(intent: Intent?) {
        val clockId = intent?.getIntExtra(EXTRA_CLOCK_ID, -1) ?: -1
        clockStates[clockId]?.let { state ->
            Log.d(TAG, "Resetting clock $clockId")
            val wasPaused = state.isPaused
            state.isPaused = false
            try {
                val now = LocalTime.now(state.timeZoneId)
                state.currentTime = now
                clockViewInstances[clockId]?.updateDisplayTime(now)
            } catch (e: Exception) { Log.e(TAG, "Error getting time on reset", e) }
            if (wasPaused) { updatePlayPauseButton(clockId) }
        }
    }

    private fun handleOpenSettings(intent: Intent?) {
        val clockId = intent?.getIntExtra(EXTRA_CLOCK_ID, -1) ?: -1
        if (clockId != -1) {
            Log.d(TAG, "Opening settings for clock $clockId")
            // Optional: Could add highlight/dimming here if desired before opening settings
            // highlightClock(clockId)
            // dimOtherClocks(clockId)

            // Create intent to launch ClockSettingsActivity
            val settingsIntent = Intent(this, ClockSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) // Ensure a fresh instance
                putExtra(ClockSettingsActivity.EXTRA_CLOCK_ID, clockId) // Pass the specific clock ID
            }
            try {
                startActivity(settingsIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting ClockSettingsActivity", e)
                Toast.makeText(this, "Could not open settings.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "Invalid clockId received for ACTION_OPEN_SETTINGS")
        }
    }

    // --- Clock Creation / Deletion / Loading ---
    private fun createNewClock(creationIntent: Intent?) {
        val clockId = clockCounter.incrementAndGet()
        // Determine initial settings
        val initialState = ClockState(/* ... load from intent or prefs ... */)
        val params = createLayoutParams(/* ... load from prefs or defaults ... */)

        // --- Use Helper Function ---
        val newViewPair = inflateAndSetupClockView(clockId, initialState, params)

        if (newViewPair != null) {
            val (newRootView, newClockView) = newViewPair

            // Store state and view references
            clockStates[clockId] = initialState
            activeClockViews[clockId] = rootView
            clockViewInstances[clockId] = clockView
            clockLayoutParams[clockId] = params

            // Add to Window Manager
            try { windowManager.addView(rootView, params); Log.d(TAG,"Added clock $clockId view.") }
            catch (e: Exception) { /* ... handle error, cleanup ... */ return }

            // Apply nesting visuals if needed
            if (initialState.isNested) { /* ... */ }

            // Save state to prefs, start ticker
            saveNewClockToPrefs(clockId, initialState, params)
            startTickerIfNecessary()
        } else {
            Log.e(TAG, "Failed to inflate/setup view for new clock $clockId")
        }
        updateActiveClockCountInPrefs() // Update count regardless of success? Maybe only on success.
    }

    private fun removeClock(clockIdToRemove: Int) {
         val rootView = activeClockViews[clockIdToRemove]
         removeClockInternal(clockIdToRemove, rootView, true)
    }
    private fun removeClockInternal(clockIdToRemove: Int, rootViewToRemove: View?, saveState: Boolean) {
        Log.d(TAG, "Removing clock $clockIdToRemove")
        if (rootViewToRemove != null) { /* ... try removeView ... */ }
        // Remove from maps
        activeClockViews.remove(clockIdToRemove); clockViewInstances.remove(clockIdToRemove);
        clockLayoutParams.remove(clockIdToRemove); clockStates.remove(clockIdToRemove);
        nestedClockPositions.remove(clockIdToRemove)
        if (saveState) { /* ... remove from SharedPreferences ... */ }
        repositionNestedClocks()
        if (clockStates.isEmpty()) { stopTicker() }
    }

    private fun loadActiveClocksFromPrefs() {
         val activeIds = sharedPreferences.getStringSet("active_clock_ids", emptySet()) ?: emptySet()
         Log.d(TAG, "Loading active clock IDs from prefs: $activeIds")
         activeIds.forEach { idStr ->
             val id = idStr.toIntOrNull()
             if (id != null) {
                 clockCounter.updateAndGet { currentMax -> max(currentMax, id) } // Ensure counter is ahead
                 val state = ClockState(/*... load from prefs ...*/)
                 val params = createLayoutParams(/*... load from prefs ...*/)

                 // --- Use Helper Function ---
                 val loadedViewPair = inflateAndSetupClockView(id, state, params)

                 if (loadedViewPair != null) {                                 
                    val (rootView, clockView) = loadedViewPair
                    // Store state and references
                    clockStates[id] = state
                    activeClockViews[id] = rootView
                    clockViewInstances[id] = clockView
                    clockLayoutParams[id] = params


                     // Add to window ...
                     try { windowManager.addView(rootView, params) } catch (e: Exception) { /*...*/ continue }
         
                 // Setup interactions
                 setupActionButtons(id, rootView) // *** ADDED ***
                 updatePlayPauseButton(id)     // *** ADDED ***
                 setupWindowTouchListener(id, rootView)

                    // Apply nesting ...
                    if (state.isNested) { applyNestModeVisuals(id, rootView, true) }
                 } else {
                      Log.e(TAG, "Failed to inflate/setup view for loaded clock $id")
                 }
             }
         }
         repositionNestedClocks()
     }

     // --- Persistence Functions ---
     private fun saveNewClockToPrefs(clockId: Int, state: ClockState, params: WindowManager.LayoutParams) { 
         val currentIds = sharedPreferences.getStringSet("active_clock_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentIds.add(clockId.toString())

        sharedPreferences.edit()
            .putStringSet("active_clock_ids", currentIds)
            .putString("clock_${clockId}_mode", state.mode)
            .putInt("clock_${clockId}_color", state.clockColor)
            .putString("clock_${clockId}_time_zone_id", state.timeZoneId.id)
            .putBoolean("clock_${clockId}_display_seconds", state.displaySeconds)
            .putBoolean("clock_${clockId}_24hour", state.is24Hour)
            .putBoolean("clock_${clockId}_nest", state.isNested)
            .putInt("clock_${clockId}_x", params.x)
            .putInt("clock_${clockId}_y", params.y)
            .putInt("clock_${clockId}_width", params.width)
            .putInt("clock_${clockId}_height", params.height)
            .apply()

        // updateActiveClockCountInPrefs() 
        // Called separately after this function executes
     }
     
     private fun saveSpecificClockSetting(clockId: Int, settingType: String, intent: Intent?) { 
        val editor = sharedPreferences.edit()
        when (settingType) {
            "mode" -> editor.putString("clock_${clockId}_mode", intent?.getStringExtra(EXTRA_SETTING_VALUE))
            "color" -> editor.putInt("clock_${clockId}_color", intent?.getIntExtra(EXTRA_SETTING_VALUE, Color.WHITE) ?: Color.WHITE)
            "24hour" -> editor.putBoolean("clock_${clockId}_24hour", intent?.getBooleanExtra(EXTRA_SETTING_VALUE, false) ?: false)
            "time_zone" -> editor.putString("clock_${clockId}_time_zone_id", intent?.getStringExtra(EXTRA_SETTING_VALUE))
            "seconds" -> editor.putBoolean("clock_${clockId}_display_seconds", intent?.getBooleanExtra(EXTRA_SETTING_VALUE, true) ?: true)
            // Nest state is saved separately in handleNestClock
        }
        editor.apply()
     }

     private fun saveClockPosition(clockId: Int, x: Int, y: Int) { 
        sharedPreferences.edit()
            .putInt("clock_${clockId}_x", x)
            .putInt("clock_${clockId}_y", y)
            .apply()
        // Update internal state (params are stored in clockLayoutParams map)
        clockLayoutParams[clockId]?.x = x
        clockLayoutParams[clockId]?.y = y
     }

     private fun saveClockDimensions(clockId: Int, width: Int, height: Int) { 
        sharedPreferences.edit()
            .putInt("clock_${clockId}_width", width)
            .putInt("clock_${clockId}_height", height)
            .apply()
        // Update internal state
        clockLayoutParams[clockId]?.width = width
        clockLayoutParams[clockId]?.height = height
     }

     private fun updateActiveClockCountInPrefs() { 
        val count = clockStates.size
         sharedPreferences.edit().putInt("active_clock_count", count).apply()
         Log.d(TAG, "Active clock count updated to: $count")
     }


    // --- NEW: Helper Function for View Inflation and Setup ---
    private fun inflateAndSetupClockView(clockId: Int, state: ClockState, params: WindowManager.LayoutParams): Pair<ViewGroup, ClockView>? {
        val inflater = LayoutInflater.from(this)
        val rootView: ViewGroup
        val clockView: ClockView

        try {
            // Inflate layout based on mode
            if (state.mode == "analog") {
                rootView = inflater.inflate(R.layout.view_floating_clock_analog, null) as ViewGroup
                val frameLayout = rootView.findViewById<FrameLayout>(R.id.analogClockViewContainer)
                    ?: throw IllegalStateException("Analog layout missing FrameLayout container (analogClockViewContainer)")
                // Create ClockView programmatically and add it
                clockView = ClockView(this, null)
                frameLayout.addView(clockView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                // Find SVGs and set them on the view
                val clockFace = rootView.findViewById<SVGImageView>(R.id.clockFaceImageView)
                val hourHand = rootView.findViewById<SVGImageView>(R.id.hourHandImageView)
                val minuteHand = rootView.findViewById<SVGImageView>(R.id.minuteHandImageView)
                val secondHand = rootView.findViewById<SVGImageView>(R.id.secondHandImageView)
                if (clockFace == null || hourHand == null || minuteHand == null || secondHand == null) {
                     Log.w(TAG, "Analog layout missing required SVGImageView elements for clock $clockId")
                     // Continue without SVGs? Or fail? Let's log and continue for now.
                     clockView.setAnalogImageViews(null, null, null, null) // Indicate missing views
                } else {
                    clockView.setAnalogImageViews(clockFace, hourHand, minuteHand, secondHand)
                }
            } else { // Digital
                rootView = inflater.inflate(R.layout.view_floating_clock_digital, null) as ViewGroup
                clockView = rootView.findViewById(R.id.digitalClockView)
                    ?: throw IllegalStateException("Digital layout missing ClockView element (digitalClockView)")
            }

            // Configure the ClockView instance
            clockView.setClockId(clockId)
            clockView.setClockMode(state.mode == "analog")
            clockView.setClockColor(state.clockColor)
            clockView.setIs24HourFormat(state.is24Hour)
            clockView.setClockTimeZone(state.timeZoneId)
            clockView.setDisplaySeconds(state.displaySeconds)
            // Update its display immediately (use current state time, not system time)
            clockView.updateDisplayTime(state.currentTime)

            clockView.interactionListener = this // Set the listener

            // Setup interactions
            setupActionButtons(clockId, rootView)
            updatePlayPauseButton(clockId) // Set initial button state
            setupWindowTouchListener(clockId, rootView) // Window drag/resize

            return Pair(rootView, clockView)

        } catch (e: Exception) {
            Log.e(TAG, "Error inflating or setting up view for clock $clockId (mode: ${state.mode})", e)
            return null // Return null on failure
        }
    }

    // --- Implementation for ClockInteractionListener Methods ---

    override fun onTimeManuallySet(clockId: Int, newTime: LocalTime) {
        clockStates[clockId]?.let { state ->
            if (!state.isPaused) { // Only allow setting time if not paused? Or allow always? Let's allow always.
                Log.d(TAG, "Manual time set for clock $clockId to $newTime")
                state.currentTime = newTime // Update the authoritative time state
                // Optionally save this manually set time? For now, it persists until reset/mode/zone change.
                // We could add a flag 'isManuallySet' to ClockState if needed.
            } else {
                Log.d(TAG, "Manual time set ignored for clock $clockId because it is paused.")
            }
        }
    }

    override fun onDragStateChanged(clockId: Int, isDragging: Boolean) {
        clockStates[clockId]?.let { state ->
            if (isDragging) {
                if (!state.isPaused) {
                    Log.d(TAG, "Pausing clock $clockId due to hand drag.")
                    state.isPaused = true
                    updatePlayPauseButton(clockId)
                }
            } else { // Drag finished
                if (state.isPaused) {
                    // Should we automatically resume after drag? Let's assume yes for now.
                    Log.d(TAG, "Resuming clock $clockId after hand drag.")
                    state.isPaused = false
                    updatePlayPauseButton(clockId)
                    // Ticker will automatically resume incrementing from the manually set time
                }
            }
        }
    }

    // --- Interaction Handling ---
    private fun setupWindowTouchListener(clockId: Int, rootView: View) {
        rootView.setOnTouchListener(object : View.OnTouchListener {
            // Variables for drag/resize logic
            var initialX: Int = 0
            var initialY: Int = 0
            var initialTouchX: Float = 0f
            var initialTouchY: Float = 0f
            var initialWidth: Int = 0
            var initialHeight: Int = 0
            var initialDistance: Float = 0f
            var pointerId1: Int = -1
            var pointerId2: Int = -1
            var mode: Int = 0 // 0=None, 1=Drag, 2=Resize
            val DRAG = 1
            val RESIZE = 2
            // Get touch slop instance specific to the context for accuracy
            val touchSlop = android.view.ViewConfiguration.get(applicationContext).scaledTouchSlop.toFloat()
            var hasMovedBeyondSlopForTapDetect = false // Flag to distinguish tap from drag/resize

            // Cache screen dimensions for efficiency during drag
            var screenWidth = 0
            var screenHeight = 0

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                val params = clockLayoutParams[clockId] ?: return false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // Record initial state
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX // Use raw coordinates for screen position
                        initialTouchY = event.rawY
                        pointerId1 = event.getPointerId(0)
                        mode = DRAG // Assume dragging initially
                        hasMovedBeyondSlopForTapDetect = false // Reset flag
                        lastTouchDownTime = SystemClock.elapsedRealtime() // For tap detection

                        // Get screen dimensions on touch down
                        val displayMetrics = resources.displayMetrics
                        screenWidth = displayMetrics.widthPixels
                        screenHeight = displayMetrics.heightPixels
                        // Note: For perfect boundary checks, might need to account for status/nav bars.
                        // Using full screenHeight is simpler for now.

                        return true // Consume event to detect move/up actions
                    }
                    
                    MotionEvent.ACTION_POINTER_DOWN -> { /* ... set mode=RESIZE, set hasMoved flag ... */ return true }
                    MotionEvent.ACTION_MOVE -> {
                        // ... (check for slop movement, set hasMoved flag) ...
                        if (!hasMovedBeyondSlopForTapDetect) return true
                        if (mode == DRAG) { /* ... Drag logic ... */ }
                        else if (mode == RESIZE) { /* ... Resize logic ... */ }
                        return true
                    }
                    
                    MotionEvent.ACTION_POINTER_DOWN -> {
                         // Switch to resize mode if a second finger touches down
                         if (event.pointerCount == 2) {
                             pointerId2 = event.getPointerId(1)
                             initialDistance = distance(event, 0, 1) // Calculate initial distance between fingers
                             initialWidth = params.width // Store initial size for scaling
                             initialHeight = params.height
                             if (initialDistance > 10f) { // Threshold to avoid noise
                                 mode = RESIZE
                                 hasMovedBeyondSlopForTapDetect = true // Starting resize means it's not a tap
                             }
                         }
                         return true // Consume pointer down events
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // Calculate movement delta
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY

                        // Check if movement has exceeded the touch slop threshold
                        if (!hasMovedBeyondSlopForTapDetect) {
                            if (sqrt(dx * dx + dy * dy) > touchSlop) {
                                hasMovedBeyondSlopForTapDetect = true // Flag that significant movement occurred
                            }
                        }

                        // Only perform drag/resize if movement is significant (beyond slop)
                        if (!hasMovedBeyondSlopForTapDetect) return true

                        if (mode == DRAG && event.pointerCount == 1) {
                            // Calculate potential new coordinates
                            var newX = initialX + dx.toInt()
                            var newY = initialY + dy.toInt()

                            // --- Boundary Check Logic ---
                            val viewWidth = view.width
                            val viewHeight = view.height
                            newX = max(0, min(newX, screenWidth - viewWidth))
                            newY = max(0, min(newY, screenHeight - viewHeight))
                            // --- End Boundary Check ---

                            params.x = newX
                            params.y = newY
                            try { windowManager.updateViewLayout(view, params) }
                            catch (e: Exception) { Log.e(TAG,"UpdateViewLayout (Drag) Error", e) }
                            return true // Consume move event during drag

                        } else if (mode == RESIZE && event.pointerCount >= 2) {
                            val index1 = event.findPointerIndex(pointerId1)
                            val index2 = event.findPointerIndex(pointerId2)
                            if (index1 != -1 && index2 != -1) {
                                 val newDistance = distance(event, index1, index2)
                                 if (initialDistance > 10f) { // Avoid division by zero/small values
                                     val scaleFactor = newDistance / initialDistance
                                     var newWidth = (initialWidth * scaleFactor).toInt()
                                     var newHeight = (initialHeight * scaleFactor).toInt()

                                     // Min/Max size constraints
                                     val minSize = dpToPx(50)
                                     val maxSize = dpToPx(500) // Example max size

                                     // Boundary check for resize (prevent resizing past screen edge)
                                     newWidth = max(minSize, min(newWidth, min(maxSize, screenWidth - params.x)))
                                     newHeight = max(minSize, min(newHeight, min(maxSize, screenHeight - params.y)))

                                     params.width = newWidth
                                     params.height = newHeight
                                     try { windowManager.updateViewLayout(view, params) }
                                     catch (e: Exception) { Log.e(TAG,"UpdateViewLayout (Resize) Error", e) }
                                 }
                            }
                            return true // Consume move event during resize
                        }
                    }
                    
                    MotionEvent.ACTION_POINTER_UP -> {
                        // Handle one finger lifting during a resize gesture
                        if (mode == RESIZE) {
                            // Optional: Could switch back to DRAG mode if one finger remains,
                            // but simply ending the resize and saving state is simpler.
                            saveClockDimensions(clockId, params.width, params.height)
                            mode = 0 // Reset mode
                        }
                        return true // Consume pointer up events
                    }
                    
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                         // Finger lifted or gesture cancelled
                         if (mode == DRAG && hasMovedBeyondSlopForTapDetect) {
                             // Save final position after dragging
                             saveClockPosition(clockId, params.x, params.y)
                         } else if (mode == RESIZE && hasMovedBeyondSlopForTapDetect) {
                             // Final dimensions should have been saved on ACTION_POINTER_UP
                             // If ACTION_UP happens with 2 fingers (less common), save here too.
                             saveClockDimensions(clockId, params.width, params.height)
                         } else if (!hasMovedBeyondSlopForTapDetect) {
                             // Movement didn't exceed slop, treat as a tap on the background
                             Log.d(TAG, "Tap detected on clock $clockId background, ignored.")
                             // We don't trigger the old tooltip/menu here anymore
                         }
                         // Reset state variables
                         mode = 0
                         pointerId1 = -1
                         pointerId2 = -1
                         hasMovedBeyondSlopForTapDetect = false
                         return true // Event handled
                    }
                }
                // If none of the above conditions handled the event
                return false
            }

             // Helper for distance calculation between two pointers
             private fun distance(event: MotionEvent, pointerIndex1: Int, pointerIndex2: Int): Float {
                 val x = event.getX(pointerIndex1) - event.getX(pointerIndex2)
                 val y = event.getY(pointerIndex1) - event.getY(pointerIndex2)
                 return sqrt(x * x + y * y)
             }

             // Helper to convert DP to PX (needed for min/max size)
             private fun dpToPx(dp: Int): Int {
                 return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
             }
        })
    }
    
    // --- Setup Listeners for Action Buttons ---
    private fun setupActionButtons(clockId: Int, rootView: ViewGroup) {
        val buttonPlayPause = rootView.findViewById<ImageButton>(R.id.buttonPlayPause)
        val buttonReset = rootView.findViewById<ImageButton>(R.id.buttonReset)
        val buttonSettings = rootView.findViewById<ImageButton>(R.id.buttonSettings)

        buttonPlayPause?.setOnClickListener {
            val state = clockStates[clockId] ?: return@setOnClickListener
            val action = if (state.isPaused) ACTION_PLAY_CLOCK else ACTION_PAUSE_CLOCK
            val intent = Intent(this, ClockOverlayService::class.java).apply {
                this.action = action
                putExtra(EXTRA_CLOCK_ID, clockId)
            }
            startService(intent)
        }

        buttonReset?.setOnClickListener {
            val intent = Intent(this, ClockOverlayService::class.java).apply {
                this.action = ACTION_RESET_CLOCK
                putExtra(EXTRA_CLOCK_ID, clockId)
            }
            startService(intent)
        }

        buttonSettings?.setOnClickListener {
             val intent = Intent(this, ClockOverlayService::class.java).apply {
                 this.action = ACTION_OPEN_SETTINGS
                 putExtra(EXTRA_CLOCK_ID, clockId)
             }
             startService(intent)
        }
    }

    // --- NEW: Update Play/Pause Button Visual State ---
    private fun updatePlayPauseButton(clockId: Int) {
         activeClockViews[clockId]?.let { rootView ->
             val button = rootView.findViewById<ImageButton>(R.id.buttonPlayPause)
             val state = clockStates[clockId]
             if (button != null && state != null) {
                 if (state.isPaused) {
                     button.setImageResource(R.drawable.ic_play)
                     button.contentDescription = getString(R.string.play)
                 } else {
                     button.setImageResource(R.drawable.ic_pause)
                     button.contentDescription = getString(R.string.pause)
                 }
             }
         }
    }

    // --- Nesting Logic, Utility, UI Feedback (Remain the same) ---
    private fun applyNestModeVisuals(clockId: Int, clockRootView: ViewGroup, isNested: Boolean) {
        // Combines logic from applyNestMode and applyNestingToClockOnCreation
        val configState = clockStates[clockId] ?: return // Use config from state map
        val params = clockLayoutParams[clockId] ?: return

        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density

        // Define nested state appearance
        val nestedScale = 0.3f
        val nestedWidthDp = 60f
        val nestedWidthPx = (nestedWidthDp * density).toInt()
        val nestedHeightPx = nestedWidthPx // Assume roughly square when nested

        // Define un-nested state appearance (use saved dimensions or WRAP_CONTENT)
        val unNestedWidth = sharedPreferences.getInt("clock_${clockId}_width", WindowManager.LayoutParams.WRAP_CONTENT)
        val unNestedHeight = sharedPreferences.getInt("clock_${clockId}_height", WindowManager.LayoutParams.WRAP_CONTENT)
        val unNestedX = sharedPreferences.getInt("clock_${clockId}_x", 50) // Restore saved X
        val unNestedY = sharedPreferences.getInt("clock_${clockId}_y", 50) // Restore saved Y

        // Find standard action buttons
        val buttonPlayPause = clockRootView.findViewById<View>(R.id.buttonPlayPause)
        val buttonReset = clockRootView.findViewById<View>(R.id.buttonReset)
        val buttonSettings = clockRootView.findViewById<View>(R.id.buttonSettings)

        if (isNested) {
            // --- Apply Nested State ---

            // Hide standard action buttons
            buttonPlayPause?.visibility = View.GONE
            buttonReset?.visibility = View.GONE
            buttonSettings?.visibility = View.GONE            
            
            // Scale down the view
            clockRootView.scaleX = nestedScale
            clockRootView.scaleY = nestedScale

            // Set fixed small size (position is handled by repositionNestedClocks)
            params.width = nestedWidthPx
            params.height = nestedHeightPx

            // Add to nested positions map if not already there
            if (!nestedClockPositions.containsKey(clockId)) {
                nestedClockPositions[clockId] = nestedClockPositions.size // Assign next available index
            }

            // --- Add Exit Nest Button ---
            addExitNestButton(clockId, clockRootView)

        } else {           
            // --- Apply Un-nested State ---
            // Restore standard action buttons
            buttonPlayPause?.visibility = View.VISIBLE
            buttonReset?.visibility = View.VISIBLE
            buttonSettings?.visibility = View.VISIBLE

            // Ensure Play/Pause button state is correct
            updatePlayPauseButton(clockId)

            // Restore scale
            clockRootView.scaleX = 1.0f
            clockRootView.scaleY = 1.0f

            // Restore size and position
            params.width = unNestedWidth
            params.height = unNestedHeight
            params.x = unNestedX
            params.y = unNestedY
            params.gravity = Gravity.TOP or Gravity.START // Ensure gravity is reset

            // Remove from nested positions map
            nesedClockPositions.remove(clockId)

            // --- Remove Exit Nest Button ---
            val exitButton = clockRootView.findViewWithTag<View>("exit_nest_button_$clockId")
            exitButton?.let {
                clockRootView.removeView(it)
                Log.d(TAG, "Removed Exit Nest button for clock $clockId")
            }
        }

        // Update the view layout
        try {
            windowManager.updateViewLayout(clockRootView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating layout for nest mode change", e)
        }
    }
    
    // --- Helper: Adds the Exit Nest button dynamically ---
    private fun addExitNestButton(clockId: Int, clockRootView: ViewGroup) {
        // Check if button already exists (shouldn't happen if logic is correct, but safe check)
        if (clockRootView.findViewWithTag<View>("exit_nest_button_$clockId") != null) {
            Log.w(TAG, "Exit Nest button already exists for clock $clockId")
            return
        }

        val buttonSizePx = (25f * resources.displayMetrics.density).toInt() // Adjust size as needed
        val buttonMarginPx = (4f * resources.displayMetrics.density).toInt()

        val exitNestButton = TextView(this).apply {
            text = "X" // Simple text "X" - replace with ImageButton if you have an icon
            tag = "exit_nest_button_$clockId" // Set tag to find it later
            setTextColor(Color.parseColor("#FF6666")) // Reddish color for close
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f) // Adjust size
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(180, 60, 60, 60)) // Semi-transparent dark background
                setSize(buttonSizePx, buttonSizePx)
            }
            // Ensure it's clickable
            isClickable = true
            isFocusable = true

            setOnClickListener {
                Log.d(TAG, "Exit Nest button clicked for clock $clockId")
                // Send intent back to service to un-nest this clock
                val unNestIntent = Intent(this@ClockOverlayService, ClockOverlayService::class.java).apply {
                    action = ACTION_NEST_CLOCK
                    putExtra(EXTRA_CLOCK_ID, clockId)
                    putExtra(EXTRA_NEST_STATE, false) // Explicitly set nest state to false
                }
                ContextCompat.startForegroundService(this@ClockOverlayService, unNestIntent)
            }
        }

        // Add the button to the root view
        // Using LinearLayout requires careful placement, FrameLayout is easier for overlays.
        // Assuming root is LinearLayout for now, adding with specific layout params.
        // This might need adjustment depending on the exact structure of your XML root.
        val buttonParams = LinearLayout.LayoutParams(buttonSizePx, buttonSizePx).apply {
            // Try placing it at the top-end within the LinearLayout
            // This positioning might be imperfect in LinearLayout; FrameLayout is better.
            gravity = Gravity.TOP or Gravity.END
            setMargins(buttonMarginPx, buttonMarginPx, buttonMarginPx, buttonMarginPx)
        }

        try {
            clockRootView.addView(exitNestButton, buttonParams)
            Log.d(TAG, "Added Exit Nest button for clock $clockId")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding Exit Nest button", e)
            // This might fail if clockRootView isn't a ViewGroup or has restrictions
        }
    }

    private fun repositionNestedClocks() {
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val screenWidth = displayMetrics.widthPixels
        val nestedMarginPx = (10f * density).toInt() // Margin from edge
        val nestedSpacingPx = (8f * density).toInt() // Spacing between clocks
        val nestedWidthDp = 60f // Base width before scaling
        val scale = 0.3f
        val scaledWidthPx = (nestedWidthDp * density * scale).toInt() // Effective width after scaling
        val scaledHeightPx = scaledWidthPx // Assume square after scaling

        // Get the list of currently nested clocks, sorted by their assigned index
        val currentlyNested = nestedClockPositions.entries
            .sortedBy { it.value } // Sort by the stored index (0, 1, 2...)
            .mapNotNull { entry ->
                activeClockViews[entry.key]?.let { view ->
                    // Triple contains: clockId, the rootView, the assigned index
                    Triple(entry.key, view, entry.value)
                }
            }

        // Recalculate positions based on the sorted list
        currentlyNested.forEach { (id, rootView, index) ->
            val params = clockLayoutParams[id] ?: return@forEach
            val nestedY = nestedMarginPx + (index * (scaledHeightPx + nestedSpacingPx))

            params.gravity = Gravity.TOP or Gravity.END // Align to top-right
            params.x = nestedMarginPx // Use margin for X (distance from right edge)
            params.y = nestedY

            // Also ensure the size is correct for nested state
            params.width = scaledWidthPx
            params.height = scaledHeightPx

            try {
                 windowManager.updateViewLayout(rootView, params)
            } catch (e: Exception) {
                 Log.e(TAG, "Error repositioning nested clock $id", e)
             }
        }
         Log.d(TAG, "Repositioned ${currentlyNested.size} nested clocks.")
    }
    
    private fun createLayoutParams(x: Int, y: Int, width: Int, height: Int): WindowManager.LayoutParams { 
        return WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            // Flag explanation:
            // FLAG_NOT_FOCUSABLE: Prevents the window from taking focus (e.g., keyboard)
            // FLAG_NOT_TOUCH_MODAL: Allows touches outside the window to go to views behind it. Essential for overlay.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT // Allow transparency
        ).apply {
            gravity = Gravity.TOP or Gravity.START // Use absolute positioning from top-left
            this.x = x // Set initial X position
            this.y = y // Set initial Y position
        }
    }
    
    private fun highlightClock(clockId: Int) { 
        activeClockViews[clockId]?.let { view ->
            // Apply a temporary highlight effect
            // Example: Using foreground requires API 23+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val highlightDrawable = ContextCompat.getDrawable(this, R.drawable.clock_highlight_border)
                view.foreground = highlightDrawable
            }
            // Consider a fallback for older APIs if needed
        }
    }
    
    private fun resetClockHighlight() { 
        activeClockViews.values.forEach { view ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                view.foreground = null
            }
            // Reset fallback if used
        }
    }
    
    private fun dimOtherClocks(activeClockId: Int) { 
        activeClockViews.forEach { (id, view) ->
            if (id != activeClockId) {
                view.alpha = 0.6f // Dim inactive clocks (adjust value as needed)
            } else {
                view.alpha = 1.0f // Ensure active clock is not dimmed
            }
        }
    }
    
    private fun resetClockDimming() { 
        activeClockViews.values.forEach { view ->
            view.alpha = 1.0f
        }
    }

}
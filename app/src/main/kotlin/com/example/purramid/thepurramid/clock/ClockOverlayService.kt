// ClockOverlayService.kt
package com.example.purramid.thepurramid.clock

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
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Observer
import com.caverock.androidsvg.SVGImageView
import com.example.purramid.thepurramid.MainActivity
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.ClockDao
import com.example.purramid.thepurramid.data.db.ClockStateEntity
import com.example.purramid.thepurramid.clock.viewmodel.ClockState
import com.example.purramid.thepurramid.clock.viewmodel.ClockViewModel
import com.example.purramid.thepurramid.clock.viewmodel.ClockViewModelFactory
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.ConcurrentHashMap // Use ConcurrentHashMap for thread safety
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
iimport kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@AndroidEntryPoint
class ClockOverlayService : Service(), ClockView.ClockInteractionListener {

    // --- Injected Dependencies ---
    @Inject
    lateinit var windowManager: WindowManager
    @Inject
    lateinit var clockDao: ClockDao

    // Inject Hilt's default ViewModel Factory which includes SavedStateHandle support
    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    // --- ViewModel Management ---
    private val viewModelStore = ViewModelStore()
    override fun getViewModelStore(): ViewModelStore = viewModelStore

    // Map to hold ViewModel instances, keyed by clockId
    private val clockViewModels = ConcurrentHashMap<Int, ClockViewModel>()

    // Map to hold observer jobs, keyed by clockId, to manage observation lifecycle
    private val stateObserverJobs = ConcurrentHashMap<Int, Job>()

    // --- View Management ---
    private val activeClockViews =
        ConcurrentHashMap<Int, ViewGroup>() // Pair of ID and Root View ViewGroup
    private val clockViewInstances =
        ConcurrentHashMap<Int, ClockView>() // Map ID to ClockView instance
    private val clockLayoutParams =
        ConcurrentHashMap<Int, WindowManager.LayoutParams>() // Store LayoutParams per clock

    // --- Other State ---
    private val clockIdCounter = AtomicInteger(0) // Counter for new clock IDs
    private val nestedClockPositions =
        ConcurrentHashMap<Int, Int>() // clockId -> nested column index
    private var isForeground = false // Track foreground state

    companion object {
        private const val TAG = "ClockOverlayService"
        private const val TICK_INTERVAL_MS = 1000L

        // Actions from SettingsActivity (ensure these match exactly)
        const val ACTION_NEST_CLOCK = "com.example.purramid.thepurramid.ACTION_NEST_CLOCK"
        const val EXTRA_CLOCK_ID = "clock_id"
        const val EXTRA_NEST_STATE = "nest_state"
        const val ACTION_ADD_NEW_CLOCK = "com.example.purramid.thepurramid.ACTION_ADD_NEW_CLOCK"
        const val ACTION_UPDATE_CLOCK_SETTING =
            "com.example.purramid.thepurramid.ACTION_UPDATE_CLOCK_SETTING"
        const val EXTRA_SETTING_TYPE = "setting_type"
        const val EXTRA_SETTING_VALUE = "setting_value"

        // Actions for Buttons
        const val ACTION_PAUSE_CLOCK = "com.example.purramid.thepurramid.ACTION_PAUSE_CLOCK"
        const val ACTION_PLAY_CLOCK = "com.example.purramid.thepurramid.ACTION_PLAY_CLOCK"
        const val ACTION_RESET_CLOCK = "com.example.purramid.thepurramid.ACTION_RESET_CLOCK"
        const val ACTION_OPEN_SETTINGS = "com.example.purramid.thepurramid.ACTION_OPEN_SETTINGS"

        // Notification constants
        private const val NOTIFICATION_ID = 2 // Unique ID for this service
        private const val CHANNEL_ID = "ClockOverlayServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
        loadAndRestoreClockStates()
    }


    private fun startInForeground() {
        val notificationId = 1
        val channelId = "clock_overlay_channel"
        val channelName = "Clock Overlay Service"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Floating clock(s) active")
            .setSmallIcon(R.drawable.ic_clock)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(notificationId, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand received action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_CLOCK -> {
                startForegroundServiceIfNeeded()
                // Loading happens in onCreate, ensure at least one clock exists
                if (clockViewModels.isEmpty()) {
                    handleAddNewClock(null) // Add a default clock if none were loaded
                }
            }

            ACTION_ADD_NEW_CLOCK -> {
                startForegroundServiceIfNeeded() // Ensure service is running
                handleAddNewClock(intent)
            }

            ACTION_UPDATE_CLOCK_SETTING -> {
                handleUpdateClockSetting(intent)
            }

            ACTION_NEST_CLOCK -> {
                handleNestClock(intent)
            }
            // Actions from Buttons
            ACTION_PAUSE_CLOCK -> handlePauseClock(intent)
            ACTION_PLAY_CLOCK -> handlePlayClock(intent)
            ACTION_RESET_CLOCK -> handleResetClock(intent)
            ACTION_OPEN_SETTINGS -> handleOpenSettings(intent)
            else -> {
                startTickerIfNecessary(); Log.w(TAG, "Unhandled or null action received.")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called.")
        // Clean up all clocks, observers, and ViewModels
        clockViewModels.keys.forEach { id ->
            removeClockInternal(
                id,
                saveState = false
            )
        } // Remove without saving final state
        clockViewModels.clear()
        stateObserverJobs.values.forEach { it.cancel() }
        stateObserverJobs.clear()
        viewModelStore.clear() // Clear the ViewModelStore
        super.onDestroy()
    }

    // --- State Loading and Management ---

    private fun loadAndRestoreClockStates() {
        lifecycleScope.launch(Dispatchers.IO) {
            val savedStates = clockDao.getAllStates()
            Log.d(TAG, "Found ${savedStates.size} saved clock states.")
            if (savedStates.isNotEmpty()) {
                var maxId = 0
                savedStates.forEach { entity ->
                    maxId = max(maxId, entity.clockId)
                    // Create/Get ViewModel and add view on Main thread
                    withContext(Dispatchers.Main) {
                        createOrUpdateClockInstance(entity.clockId, entity)
                    }
                }
                clockIdCounter.set(maxId + 1) // Set counter beyond highest loaded ID
            } else {
                // If no saved states, handle adding the first default clock via onStartCommand
                Log.d(TAG, "No saved states found.")
            }
            // Ensure service is in foreground if clocks were loaded
            withContext(Dispatchers.Main) {
                if (clockViewModels.isNotEmpty()) {
                    startForegroundServiceIfNeeded()
                }
            }
        }
    }

    /**
     * Gets or creates a ClockViewModel for a given ID and optionally initializes its state.
     * Manages the observation of the ViewModel's state.
     */
    private fun createOrUpdateClockInstance(
        clockId: Int,
        initialStateEntity: ClockStateEntity? = null
    ) {
        Log.d(TAG, "Creating/Updating instance for clockId: $clockId")

        // Check if ViewModel already exists
        var viewModel = clockViewModels[clockId]
        if (viewModel == null) {
            // Create ViewModel using the Factory and SavedStateHandle (Hilt handles this)
            // We need to provide the clockId to the SavedStateHandle *before* creation.
            // This is tricky with Service scope. A custom factory or assisted inject might be needed.
            // --- Simplification: Assume ViewModel loads its ID from SavedStateHandle set by Activity/Intent ---
            // --- Or, pass ID during ViewModel creation if using custom factory ---
            val defaultArgs = Bundle().apply { putInt(ClockViewModel.KEY_CLOCK_ID, clockId) }
            val savedStateViewModelFactory =
                androidx.lifecycle.SavedStateViewModelFactory(application, this, defaultArgs)

            viewModel =
                ViewModelProvider(this, savedStateViewModelFactory).get(ClockViewModel::class.java)

            // If loading initial state, update the ViewModel (it might load itself too, check VM logic)
            // This might be redundant if VM loads itself in init based on SavedStateHandle
            // initialStateEntity?.let { viewModel.initializeStateFromEntity(it) } // Add method to VM if needed

            clockViewModels[clockId] = viewModel
            Log.d(TAG, "Created new ViewModel for clockId: $clockId")
        } else {
            Log.d(TAG, "Reusing existing ViewModel for clockId: $clockId")
        }

        // --- Setup View and Observation ---
        val currentParams = clockLayoutParams[clockId] ?: createDefaultLayoutParams()
        val rootView = activeClockViews[clockId]
        val clockView = clockViewInstances[clockId]

        if (rootView == null || clockView == null) {
            // Inflate and set up the view if it doesn't exist
            val newViewPair = inflateAndSetupClockView(clockId, currentParams, viewModel)
            if (newViewPair != null) {
                val (newRootView, newClockViewInstance) = newViewPair
                activeClockViews[clockId] = newRootView
                clockViewInstances[clockId] = newClockViewInstance
                clockLayoutParams[clockId] = currentParams // Store params

                // Add view to WindowManager
                try {
                    // Load position/size from initial state before adding
                    val initialPosState = initialStateEntity ?: viewModel.uiState.value
                    currentParams.x = initialPosState.windowX
                    currentParams.y = initialPosState.windowY
                    currentParams.width =
                        if (initialPosState.windowWidth > 0) initialPosState.windowWidth else WindowManager.LayoutParams.WRAP_CONTENT
                    currentParams.height =
                        if (initialPosState.windowHeight > 0) initialPosState.windowHeight else WindowManager.LayoutParams.WRAP_CONTENT
                    currentParams.gravity = Gravity.TOP or Gravity.START // Use absolute positioning

                    windowManager.addView(newRootView, currentParams)
                    Log.d(TAG, "Added clock view for ID: $clockId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding clock view for ID: $clockId", e)
                    // Clean up if addView fails
                    clockViewModels.remove(clockId)
                    activeClockViews.remove(clockId)
                    clockViewInstances.remove(clockId)
                    clockLayoutParams.remove(clockId)
                    stateObserverJobs[clockId]?.cancel()
                    stateObserverJobs.remove(clockId)
                    return // Exit if view cannot be added
                }
            } else {
                Log.e(TAG, "Failed to inflate/setup view for clock $clockId")
                clockViewModels.remove(clockId) // Clean up VM if view fails
                return
            }
        } else {
            // View already exists, ensure params are stored
            if (!clockLayoutParams.containsKey(clockId)) {
                clockLayoutParams[clockId] = currentParams
            }
        }

        // --- Start Observing StateFlow ---
        stateObserverJobs[clockId]?.cancel() // Cancel previous observer job for this ID
        stateObserverJobs[clockId] = lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // Update View and LayoutParams on the main thread
                withContext(Dispatchers.Main) {
                    updateClockViewAndLayout(state)
                }
            }
        }
        Log.d(TAG, "Started observing ViewModel for clockId: $clockId")
    }

    /** Inflates the correct layout and sets up the ClockView instance */
    private fun inflateAndSetupClockView(
        clockId: Int,
        params: WindowManager.LayoutParams,
        viewModel: ClockViewModel // Pass the specific VM instance
    ): Pair<ViewGroup, ClockView>? {
        val inflater = LayoutInflater.from(this)
        val currentMode = viewModel.uiState.value.mode // Get mode from VM state
        val rootView: ViewGroup
        val clockView: ClockView

        try {
            if (currentMode == "analog") {
                // ... (inflate view_floating_clock_analog, find SVGs, setup clockView) ...
                rootView = inflater.inflate(R.layout.view_floating_clock_analog, null) as ViewGroup
                val frameLayout = rootView.findViewById<FrameLayout>(R.id.analogClockViewContainer)
                    ?: throw IllegalStateException("Analog layout missing FrameLayout container")

                clockView = ClockView(this, null).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                frameLayout.addView(clockView)

                val clockFace =
                    rootView.findViewById<com.caverock.androidsvg.SVGImageView>(R.id.clockFaceImageView)
                val hourHand =
                    rootView.findViewById<com.caverock.androidsvg.SVGImageView>(R.id.hourHandImageView)
                val minuteHand =
                    rootView.findViewById<com.caverock.androidsvg.SVGImageView>(R.id.minuteHandImageView)
                val secondHand =
                    rootView.findViewById<com.caverock.androidsvg.SVGImageView>(R.id.secondHandImageView)

                if (clockFace != null && hourHand != null && minuteHand != null && secondHand != null) {
                    clockView.setAnalogImageViews(clockFace, hourHand, minuteHand, secondHand)
                } else {
                    Log.w(
                        TAG,
                        "Analog layout missing required SVGImageView elements for clock $clockId"
                    )
                    clockView.setAnalogImageViews(null, null, null, null)
                }

            } else { // Digital
                // ... (inflate view_floating_clock_digital, find clockView) ...
                rootView = inflater.inflate(R.layout.view_floating_clock_digital, null) as ViewGroup
                clockView = rootView.findViewById(R.id.digitalClockView)
                    ?: throw IllegalStateException("Digital layout missing ClockView element")
            }

            // Common setup for ClockView
            clockView.setClockId(clockId)
            clockView.interactionListener = this // Service implements the listener

            // Initial state application will happen via the observer
            // clockView.updateState(viewModel.uiState.value) // Apply initial state

            // Setup action buttons (Play/Pause, Reset, Settings)
            setupActionButtons(clockId, rootView, viewModel)

            return Pair(rootView, clockView)

        } catch (e: Exception) {
            Log.e(TAG, "Error inflating/setting up view for clock $clockId (mode: $currentMode)", e)
            return null
        }
    }

    /** Updates the specific ClockView and its WindowManager LayoutParams based on state */
    private fun updateClockViewAndLayout(state: ClockState) {
        val clockId = state.clockId
        val rootView = activeClockViews[clockId]
        val clockView = clockViewInstances[clockId]
        val params = clockLayoutParams[clockId]

        if (clockView == null || params == null || rootView == null) {
            Log.w(TAG, "Attempting to update non-existent view/params for clock $clockId")
            return
        }

        // Check if mode changed - requires re-inflation
        if (clockView.isAnalog != (state.mode == "analog")) {
            Log.d(TAG, "Mode changed for clock $clockId. Re-inflating.")
            removeClockInternal(clockId, saveState = false) // Remove existing view/VM observer
            createOrUpdateClockInstance(clockId) // Recreate with new mode (VM loads state)
            return // Exit update as view is being replaced
        }

        // Update ClockView properties
        clockView.setClockColor(state.clockColor)
        clockView.setIs24HourFormat(state.is24Hour)
        clockView.setClockTimeZone(state.timeZoneId)
        clockView.setDisplaySeconds(state.displaySeconds)
        clockView.updateDisplayTime(state.currentTime) // Most frequent update

        // Update WindowManager LayoutParams if needed
        var layoutChanged = false
        if (params.x != state.windowX || params.y != state.windowY) {
            params.x = state.windowX
            params.y = state.windowY
            layoutChanged = true
        }
        val newWidth =
            if (state.windowWidth > 0) state.windowWidth else WindowManager.LayoutParams.WRAP_CONTENT
        val newHeight =
            if (state.windowHeight > 0) state.windowHeight else WindowManager.LayoutParams.WRAP_CONTENT
        if (params.width != newWidth || params.height != newHeight) {
            params.width = newWidth
            params.height = newHeight
            layoutChanged = true
        }

        // Apply nesting visuals (positioning handled separately)
        applyNestModeVisuals(clockId, rootView, state.isNested)

        // Update layout in WindowManager only if changes occurred
        if (layoutChanged && rootView.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(rootView, params)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating WindowManager layout for clock $clockId", e)
            }
        }

        // Update Play/Pause button state based on VM's isPaused
        updatePlayPauseButton(clockId, rootView, state.isPaused)
    }

    // --- Action Handling Methods ---
    private fun handleAddNewClock(intent: Intent?) {
        if (clockViewModels.size >= 4) {
            Toast.makeText(this, "Maximum of 4 clocks reached.", Toast.LENGTH_SHORT).show()
            return
        }
        val newClockId = clockIdCounter.incrementAndGet()
        Log.d(TAG, "Handling Add New Clock request. New ID: $newClockId")

        // Clone settings from intent if provided
        val clonedSettings = loadClonedSettings(intent)

        // Create/Get ViewModel and View for the new clock
        createOrUpdateClockInstance(newClockId) // This will create VM and save default state
    }

    private fun handleUpdateClockSetting(intent: Intent?) {
        val clockId = intent?.getIntExtra(EXTRA_CLOCK_ID, -1) ?: -1
        val settingType = intent?.getStringExtra(EXTRA_SETTING_TYPE)
        val state = clockViewModels[clockId]

        if (viewModel == null || settingType == null) {
            Log.e(
                TAG,
                "Cannot update setting, invalid clockId ($clockId) or settingType ($settingType)"
            )
            return
        }

        Log.d(TAG, "Updating setting '$settingType' for clock $clockId")

        // Delegate update to the specific ViewModel instance
        when (settingType) {
            "mode" -> viewModel.updateMode(intent.getStringExtra(EXTRA_SETTING_VALUE) ?: "digital")
            "color" -> viewModel.updateColor(intent.getIntExtra(EXTRA_SETTING_VALUE, Color.WHITE))
            "24hour" -> viewModel.updateIs24Hour(intent.getBooleanExtra(EXTRA_SETTING_VALUE, false))
            "time_zone" -> {
                val zoneIdString = intent.getStringExtra(EXTRA_SETTING_VALUE)
                try {
                    zoneIdString?.let { viewModel.updateTimeZone(ZoneId.of(it)) }
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid Zone ID received: $zoneIdString", e)
                }
            }

            "seconds" -> viewModel.updateDisplaySeconds(
                intent.getBooleanExtra(
                    EXTRA_SETTING_VALUE,
                    true
                )
            )
            // Nest state handled by ACTION_NEST_CLOCK
            else -> Log.w(TAG, "Unknown setting type received: $settingType")
        }
    }

    private fun handleNestClock(intent: Intent?) {
        val clockId = intent?.getIntExtra(EXTRA_CLOCK_ID, -1) ?: -1
        val shouldBeNested = intent?.getBooleanExtra(EXTRA_NEST_STATE, false) ?: false
        val state = clockStates[clockId]
        val rootView = activeClockViews[clockId]

        if (viewModel != null && rootView != null) {
            Log.d(TAG, "Setting nest state for clock $clockId to $shouldBeNested")
            viewModel.updateIsNested(shouldBeNested) // Update VM state (triggers save & observer)
            // Visuals and repositioning are handled by the state observer calling updateClockViewAndLayout
            repositionNestedClocks() // Reposition all nested clocks
        } else {
            Log.e(TAG, "Invalid data for ACTION_NEST_CLOCK: clockId=$clockId")
        }
    }

    private fun removeClockInternal(clockIdToRemove: Int, saveState: Boolean = true) {
        Log.d(TAG, "Removing clock internal: $clockIdToRemove")
        val rootView = activeClockViews.remove(clockIdToRemove)
        clockViewInstances.remove(clockIdToRemove)
        clockLayoutParams.remove(clockIdToRemove)
        stateObserverJobs[clockIdToRemove]?.cancel() // Cancel observer
        stateObserverJobs.remove(clockIdToRemove)
        val viewModel = clockViewModels.remove(clockIdToRemove)

        // Remove view from window manager
        rootView?.let {
            if (it.isAttachedToWindow) {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing view for clock $clockIdToRemove", e)
                }
            }
        }

        // Delete state from DB if requested (usually true unless stopping service)
        if (saveState) { // 'saveState' here means 'persist the deletion'
            viewModel?.deleteState() // Tell VM to delete its persisted state
        }

        // Clean up nesting position
        nestedClockPositions.remove(clockIdToRemove)
        repositionNestedClocks()

        // Stop service if last clock is removed
        if (clockViewModels.isEmpty()) {
            stopService()
        }
    }

    private fun stopService() {
        Log.d(TAG, "Stopping service as no clocks are left.")
        stopForeground(true)
        stopSelf()
        isForeground = false
    }

    // --- Implementation for ClockInteractionListener Methods ---

    override fun onTimeManuallySet(clockId: Int, newTime: LocalTime) {
        clockViewModels[clockId]?.setManuallySetTime(newTime)
    }

    override fun onDragStateChanged(clockId: Int, isDragging: Boolean) {
        // We might not need this callback anymore, or use it for visual feedback.
        Log.d(TAG, "Clock $clockId drag state changed: $isDragging")
    }

    // --- Helper Functions ---

    private fun createDefaultLayoutParams(): WindowManager.LayoutParams {
        // Define default size, can be adjusted based on mode later
        val defaultWidth = WindowManager.LayoutParams.WRAP_CONTENT
        val defaultHeight = WindowManager.LayoutParams.WRAP_CONTENT
        return WindowManager.LayoutParams(
            defaultWidth, defaultHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER // Start new clocks centered
        }
    }

    private fun setupActionButtons(clockId: Int, rootView: ViewGroup, viewModel: ClockViewModel) {
        val btnPlayPause = rootView.findViewById<View>(R.id.buttonPlayPause)
        val btnReset = rootView.findViewById<View>(R.id.buttonReset)
        val btnSettings = rootView.findViewById<View>(R.id.buttonSettings)

        btnPlayPause?.setOnClickListener {
            val currentState = viewModel.uiState.value
            viewModel.setPaused(!currentState.isPaused) // Toggle pause state
        }
        btnReset?.setOnClickListener {
            viewModel.resetTime()
        }
        btnSettings?.setOnClickListener {
            // Launch ClockSettingsActivity for this specific clockId
            val settingsIntent = Intent(this, ClockSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ClockViewModel.KEY_CLOCK_ID, clockId) // Pass ID to settings
            }
            startActivity(settingsIntent)
        }
    }

    private fun updatePlayPauseButton(clockId: Int, rootView: ViewGroup, isPaused: Boolean) {
        val button = rootView.findViewById<android.widget.ImageButton>(R.id.buttonPlayPause)
        button?.apply {
            setImageResource(if (isPaused) R.drawable.ic_play else R.drawable.ic_pause)
            contentDescription = getString(if (isPaused) R.string.play else R.string.pause)
        }
    }

    private fun applyNestModeVisuals(clockId: Int, clockRootView: ViewGroup, isNested: Boolean) {
        // TODO: Implement visual changes for nesting (scaling, hiding buttons)
        // Similar logic to TrafficLightService's implementation
        Log.d(TAG, "Applying nest visuals for $clockId: $isNested (TODO)")
    }

    private fun repositionNestedClocks() {
        // TODO: Implement repositioning logic based on nestedClockPositions map
        // Similar logic to TrafficLightService's implementation
        Log.d(TAG, "Repositioning nested clocks (TODO)")
    }

    // --- Foreground Service Methods ---
    private fun startForegroundServiceIfNeeded() {
        if (!isForeground) {
            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntentFlags =
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Floating clock(s) active")
                .setSmallIcon(R.drawable.ic_clock)
                .setContentIntent(pendingIntent)
                .build()

            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
            Log.d(TAG, "Service started in foreground.")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Clock Overlay Service Channel", // Channel name
                NotificationManager.IMPORTANCE_LOW // Low importance for overlays
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    // --- Not Used ---
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent) // Needed for LifecycleService
        return null
    }
}
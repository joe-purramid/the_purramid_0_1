// TrafficLightService.kt
package com.example.purramid.thepurramid.traffic_light

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.example.purramid.thepurramid.MainActivity
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Define Actions
const val ACTION_START_TRAFFIC_LIGHT = "com.example.purramid.START_TRAFFIC_LIGHT"
const val ACTION_STOP_TRAFFIC_LIGHT = "com.example.purramid.STOP_TRAFFIC_LIGHT"
// Add other actions as needed (e.g., specific updates)

@AndroidEntryPoint
class TrafficLightService : LifecycleService(), ViewModelStoreOwner { // Inherit from LifecycleService and implement ViewModelStoreOwner

    @Inject lateinit var windowManager: WindowManager
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory // Inject factory

    private lateinit var viewModel: TrafficLightViewModel
    private val viewModelStore = ViewModelStore() // Required for ViewModelStoreOwner

    private var overlayView: TrafficLightOverlayView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var isViewAdded = false
    private var initialParamsSet = false // Flag to set initial params only once
    private var stateObserverJob: Job? = null // Keep track of the observer job

    companion object {
        private const val TAG = "TrafficLightService"
        private const val NOTIFICATION_ID = 4 // Unique ID for this service's notification
        private const val CHANNEL_ID = "TrafficLightServiceChannel"
    }

    // Implement ViewModelStoreOwner
    override fun getViewModelStore(): ViewModelStore = viewModelStore

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        // Initialize ViewModel using the service as the owner
        // Note: AndroidViewModelFactory might be needed if ViewModel has Application context
        viewModel = ViewModelProvider(this, viewModelFactory)[TrafficLightViewModel::class.java]


        createNotificationChannel()
        setupDefaultLayoutParams()

        // Launch coroutine to load initial state and set params
        lifecycleScope.launch {
            // Get the *first* non-default state emitted after loading
            val initialState = viewModel.uiState.first { it.instanceId != 0 }
            Log.d(TAG, "Initial state loaded: Pos=(${initialState.windowX}, ${initialState.windowY}), Size=(${initialState.windowWidth}x${initialState.windowHeight})")
            applyStateToLayoutParams(initialState)
            initialParamsSet = true
            // If view is already added (e.g., service restart), update its layout
            if (isViewAdded && overlayView != null) {
                try {
                    windowManager.updateViewLayout(overlayView, layoutParams)
                } catch (e: Exception) { Log.e(TAG, "Error updating layout after initial load", e) }
            }
            // Start observing subsequent state changes
            observeViewModelState()
        }
    }

    private fun setupLayoutParams() {
        val defaultWidth = resources.getDimensionPixelSize(R.dimen.traffic_light_default_width)
        val defaultHeight = resources.getDimensionPixelSize(R.dimen.traffic_light_default_height)

        layoutParams = LayoutParams(
            defaultWidth,
            defaultHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) LayoutParams.TYPE_APPLICATION_OVERLAY else LayoutParams.TYPE_SYSTEM_ALERT,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER // Default to center
            x = 0 // Will be updated from loaded state
            y = 0 // Will be updated from loaded state
        }
    }

    // Apply loaded/saved state to LayoutParams
    private fun applyStateToLayoutParams(state: TrafficLightState) {
        layoutParams.x = state.windowX
        layoutParams.y = state.windowY
        // Use default/wrap_content if saved size is invalid (-1)
        layoutParams.width = if (state.windowWidth > 0) state.windowWidth else resources.getDimensionPixelSize(R.dimen.traffic_light_default_width)
        layoutParams.height = if (state.windowHeight > 0) state.windowHeight else resources.getDimensionPixelSize(R.dimen.traffic_light_default_height)
        // Ensure gravity is correct for absolute positioning
        layoutParams.gravity = Gravity.TOP or Gravity.START
    }

    // Start observing state changes *after* initial load
    private fun observeViewModelState() {
        stateObserverJob?.cancel() // Cancel previous job if any
        stateObserverJob = lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state.instanceId == 0) return@collectLatest // Ignore default initial state

                // Apply state to the view
                overlayView?.updateState(state)

                // Apply state to layout params *if* they differ and initial load is done
                if (initialParamsSet) {
                    var needsLayoutUpdate = false
                    if (layoutParams.x != state.windowX || layoutParams.y != state.windowY) {
                        layoutParams.x = state.windowX
                        layoutParams.y = state.windowY
                        layoutParams.gravity = Gravity.TOP or Gravity.START // Ensure correct gravity
                        needsLayoutUpdate = true
                    }
                    val newWidth = if (state.windowWidth > 0) state.windowWidth else layoutParams.width // Keep current if state has no size
                    val newHeight = if (state.windowHeight > 0) state.windowHeight else layoutParams.height

                    if (layoutParams.width != newWidth || layoutParams.height != newHeight) {
                        layoutParams.width = newWidth
                        layoutParams.height = newHeight
                        needsLayoutUpdate = true
                    }

                    if (needsLayoutUpdate && isViewAdded && overlayView != null) {
                        try {
                            windowManager.updateViewLayout(overlayView, layoutParams)
                        } catch (e: Exception) { Log.e(TAG, "Error updating layout from state observer", e) }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId) // Important for LifecycleService
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_TRAFFIC_LIGHT -> {
                // Ensure the ViewModel instance ID is correctly set if passed via intent
                // Hilt handles SavedStateHandle injection, so ViewModel init should get it.
                val idFromIntent = intent.getIntExtra(TrafficLightActivity.EXTRA_INSTANCE_ID, -1)
                if (idFromIntent != -1 && viewModel.uiState.value.instanceId != idFromIntent) {
                    Log.w(TAG, "Instance ID mismatch? Intent: $idFromIntent, VM: ${viewModel.uiState.value.instanceId}. Reloading VM state.")
                    // This scenario needs careful handling. Maybe re-create VM or force load?
                    // For now, rely on init loading the correct ID from SavedStateHandle.
                }
                startForegroundService()
                addOverlayViewIfNeeded()
            }
            ACTION_STOP_TRAFFIC_LIGHT -> {
                stopService()
            }
            // Handle other actions if needed
        }
        return START_STICKY
    }

    private fun addOverlayViewIfNeeded() {
        if (overlayView == null) {
            overlayView = TrafficLightOverlayView(this).apply {
                // Set listener to handle interactions (taps) from the view
                interactionListener = object : TrafficLightOverlayView.InteractionListener {
                    override fun onLightTapped(color: LightColor) {
                        viewModel.handleLightTap(color)
                    }
                    override fun onCloseRequested() {
                        stopService()
                    }
                    override fun onSettingsRequested() {
                        viewModel.setSettingsOpen(true)
                        val intent = Intent(this@TrafficLightService, TrafficLightActivity::class.java).apply {
                            action = TrafficLightActivity.ACTION_SHOW_SETTINGS
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            // Pass the correct instance ID back to the activity
                            putExtra(TrafficLightActivity.EXTRA_INSTANCE_ID, viewModel.uiState.value.instanceId)
                        }
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error starting TrafficLightActivity for settings", e)
                            // TODO Decide on error messaging standard (toast? snackbar? nothing?)
                            // Handler(Looper.getMainLooper()).post { Toast.makeText(applicationContext, "Cannot open settings", Toast.LENGTH_SHORT).show() }
                        }
                    }

                    // Handle window movement requests from the view's touch handler
                     override fun onMove(deltaX: Int, deltaY: Int) {
                         if (!isViewAdded) return
                         layoutParams.x += rawDeltaX.toInt()
                         layoutParams.y += rawDeltaY.toInt()
                         try {
                             windowManager.updateViewLayout(this@apply, layoutParams)
                         } catch (e: Exception) {
                             Log.e(TAG, "Error updating layout on move", e)
                         }
                     }
                     override fun onMoveFinished() {
                         // Save final position
                         viewModel.saveWindowPosition(layoutParams.x, layoutParams.y)
                     }
                     // Add onResize if implementing resizing
                }
            }
        }

        if (!isViewAdded && overlayView != null && initialParamsSet) {
            try {
                windowManager.addView(overlayView, layoutParams)
                isViewAdded = true
                Log.d(TAG, "Traffic Light overlay view added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding overlay view", e)
                overlayView = null // Reset if failed
            }
        } else if (!initialParamsSet) {
            Log.d(TAG, "Overlay view creation deferred until initial state is loaded.")
        }
    }


    private fun removeOverlayView() {
        overlayView?.let {
            if (isViewAdded) {
                try {
                    windowManager.removeView(it)
                    isViewAdded = false
                    Log.d(TAG, "Traffic Light overlay view removed.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing overlay view", e)
                }
            }
        }
        overlayView = null
    }

    private fun stopService() {
        Log.d(TAG, "Stopping service")
        removeOverlayView()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stateObserverJob?.cancel() // Cancel the state observer
        removeOverlayView()
        viewModelStore.clear() // Clear the ViewModelStore
        super.onDestroy()
    }

    // Not used for unbound service
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent) // Important for LifecycleService
        return null
    }


    // --- Foreground Service Setup ---
    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Traffic Light Active")
            .setContentText("Traffic Light overlay is running.")
            .setSmallIcon(R.drawable.ic_traffic_light)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Traffic Light Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
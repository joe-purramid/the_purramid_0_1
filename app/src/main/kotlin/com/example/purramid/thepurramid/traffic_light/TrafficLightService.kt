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
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.example.purramid.thepurramid.MainActivity
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

// Define Actions
const val ACTION_START_TRAFFIC_LIGHT = "com.example.purramid.START_TRAFFIC_LIGHT"
const val ACTION_STOP_TRAFFIC_LIGHT = "com.example.purramid.STOP_TRAFFIC_LIGHT"
// Add other actions as needed (e.g., specific updates)

@AndroidEntryPoint
class TrafficLightService : LifecycleService(), ViewModelStoreOwner { // Inherit from LifecycleService and implement ViewModelStoreOwner

    @Inject lateinit var windowManager: WindowManager
    // @Inject lateinit var viewModelFactory: ViewModelProvider.Factory // Needed if not using Hilt directly

    private lateinit var viewModel: TrafficLightViewModel
    private val viewModelStore = ViewModelStore() // Required for ViewModelStoreOwner

    private var overlayView: TrafficLightOverlayView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var isViewAdded = false

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

        // --- ViewModel Initialization ---
        // Use Hilt's default factory provided via Activity/Fragment scope or manual provider
        // Since Service doesn't have direct Hilt ViewModel support, we get it manually
        // This requires setting up Hilt correctly to provide ViewModels outside standard scopes or using Activity context (less ideal)
        // A simpler way for now might be to tie VM lifecycle to service lifecycle manually.
        // Let's use the standard Hilt injection via activityViewModels() in the Activity/Fragment
        // and communicate state updates TO the service/view via other means (e.g., observing from service)
        // --- OR --- Implement ViewModelStoreOwner as done above.

        // Initialize ViewModel using the service as the owner
        viewModel = ViewModelProvider(this).get(TrafficLightViewModel::class.java)


        createNotificationChannel()
        setupLayoutParams()

        // Observe ViewModel state to update the overlay view
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // Ensure view exists and state is valid before updating
                overlayView?.updateState(state)
                 // Update window size/position if needed based on state (e.g., orientation change might affect desired size)
                 if (isViewAdded) {
                    // Example: Adjust layout params based on orientation if needed
                    // layoutParams.width = if (state.orientation == Orientation.VERTICAL) dpToPx(80) else dpToPx(220)
                    // layoutParams.height = if (state.orientation == Orientation.VERTICAL) dpToPx(220) else dpToPx(80)
                    // windowManager.updateViewLayout(overlayView, layoutParams)
                 }
            }
        }
    }

    private fun setupLayoutParams() {
        // Initial size and position (can be loaded from ViewModel/DB later)
        val initialWidth = resources.getDimensionPixelSize(R.dimen.traffic_light_default_width)
        val initialHeight = resources.getDimensionPixelSize(R.dimen.traffic_light_default_height)

        layoutParams = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER // Start centered or load last position
            // Load x, y from ViewModel/DB later
            // x = savedX
            // y = savedY
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId) // Important for LifecycleService
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_TRAFFIC_LIGHT -> {
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
                        // Need context to show fragment - best launched from Activity
                        // Send broadcast or use other mechanism to request Activity to show settings
                        val intent = Intent(this@TrafficLightService, TrafficLightActivity::class.java).apply {
                            action = "SHOW_SETTINGS" // Define a custom action
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            // Pass instance ID if needed
                        }
                        startActivity(intent) // Starting activity from service
                    }

                    // Handle window movement requests from the view's touch handler
                     override fun onMove(deltaX: Int, deltaY: Int) {
                         if (!isViewAdded) return
                         layoutParams.x += deltaX
                         layoutParams.y += deltaY
                         try {
                             windowManager.updateViewLayout(this@apply, layoutParams)
                         } catch (e: Exception) {
                             Log.e(TAG, "Error updating layout on move", e)
                         }
                     }
                     override fun onMoveFinished() {
                         // TODO: Persist new position via ViewModel/DB
                         // viewModel.savePosition(layoutParams.x, layoutParams.y)
                     }
                     // Add onResize if implementing resizing
                }
            }
        }

        if (!isViewAdded && overlayView != null) {
            try {
                windowManager.addView(overlayView, layoutParams)
                isViewAdded = true
                Log.d(TAG, "Traffic Light overlay view added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding overlay view", e)
                overlayView = null // Reset if failed
            }
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
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Traffic Light Active")
            .setContentText("Traffic Light overlay is running.")
            .setSmallIcon(R.drawable.ic_traffic_light) // Use traffic light icon
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
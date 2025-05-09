// SpotlightService.kt
package com.example.purramid.thepurramid.spotlight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.example.purramid.thepurramid.MainActivity
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.spotlight.viewmodel.SpotlightViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Define actions for controlling the service via Intents
const val ACTION_START_SPOTLIGHT = "com.example.purramid.START_SPOTLIGHT"
const val ACTION_STOP_SPOTLIGHT = "com.example.purramid.STOP_SPOTLIGHT"

@AndroidEntryPoint
class SpotlightService : LifecycleService(), ViewModelStoreOwner {

    // Inject dependencies
    @Inject lateinit var windowManager: WindowManager
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory // Inject Hilt Factory

    private val spotlightViews = ConcurrentHashMap<Int, Pair<SpotlightView, WindowManager.LayoutParams>>()
    private var nextSpotlightId = 1 // Simple ID generator
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main) // Scope for DB operations

    companion object {
        private const val TAG = "SpotlightService"
        private const val NOTIFICATION_ID = 3 // Choose a unique ID
        private const val CHANNEL_ID = "SpotlightServiceChannel"
        private const val PASS_THROUGH_DELAY_MS = 50L // Short delay to remove pass-through flag
    }

    // Implement ViewModelStoreOwner
    override fun getViewModelStore(): ViewModelStore = viewModelStore

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        // Initialize ViewModel using the service as the owner
        viewModel = ViewModelProvider(this, viewModelFactory)[SpotlightViewModel::class.java]

        createNotificationChannel()
        observeViewModelState() // Start observing ViewModel right away
        // ViewModel init triggers loading from DB
    }

    // Observe ViewModel State
    private fun observeViewModelState() {
        stateObserverJob?.cancel() // Cancel previous job if any
        stateObserverJob = lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state.isLoading) {
                    Log.d(TAG, "Spotlight state is loading...")
                    return@collectLatest
                }
                if (state.error != null) {
                    Log.e(TAG, "Error in Spotlight state: ${state.error}")
                    // Optional: Show Toast or handle error appropriately
                    // android.widget.Toast.makeText(this@SpotlightService, state.error, android.widget.Toast.LENGTH_SHORT).show()
                    // Potentially stop service if loading failed critically?
                    return@collectLatest
                }

                Log.d(TAG, "Received UI State Update: ${state.spotlights.size} spotlights, Shape: ${state.globalShape}")

                // Update or add/remove the single SpotlightView
                if (state.spotlights.isNotEmpty()) {
                    addOrUpdateOverlayView(state)
                } else {
                    // No spotlights left, remove the view
                    Log.d(TAG, "No spotlights in state, removing overlay view.")
                    removeOverlayView()
                    // Let the foreground service state manage stopping
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_SPOTLIGHT -> {
                startForegroundServiceIfNeeded() // Ensure service is foreground
                // ViewModel init handles loading/adding initial spotlight
                }
            }
            ACTION_STOP_SPOTLIGHT -> {
                stopService()
            }
        }
        return START_STICKY
    }

private var isForeground = false // Track foreground state

private fun startForegroundServiceIfNeeded() {
    if (isForeground) return

    val notificationIntent = Intent(this, MainActivity::class.java)
    val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

    val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Spotlight Active")
        .setContentText("Spotlight overlay is running.")
        .setSmallIcon(R.drawable.ic_spotlight)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    try {
        startForeground(NOTIFICATION_ID, notification)
        isForeground = true
        Log.d(TAG, "Service started in foreground.")
    } catch (e: Exception) {
        Log.e(TAG, "Error starting foreground service", e)
        // Handle specific exceptions like ForegroundServiceStartNotAllowedException if needed
    }
}

private fun stopService() {
    Log.d(TAG, "Stopping service requested")
    // Ask ViewModel to delete all spotlights? Or just remove view and stop?
    // Let's just remove the view and stop, keeping DB state.
    lifecycleScope.launch { // Use lifecycleScope for safety
        removeOverlayView()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isForeground = false
        Log.d(TAG, "Service stopped.")
    }
}

private fun createDefaultLayoutParams(): WindowManager.LayoutParams {
    return WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        },
        // Start NOT_TOUCHABLE to allow interaction with underlying apps initially?
        // Let's start NOT_FOCUSABLE | NOT_TOUCH_MODAL as before. Pass-through handles taps.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 0
    }
}

private fun addOrUpdateOverlayView(state: SpotlightUiState) {
    // Ensure execution on the main thread for UI operations
    if (Looper.myLooper() != Looper.getMainLooper()) {
        Log.w(TAG, "addOrUpdateOverlayView called from non-UI thread. Posting to handler.")
        handler.post { addOrUpdateOverlayView(state) }
        return
    }

    if (spotlightView == null) {
        layoutParams = createDefaultLayoutParams()
        spotlightView = SpotlightView(this, null).apply {
            interactionListener = createInteractionListener(this, layoutParams!!)
            setLayerType(View.LAYER_TYPE_HARDWARE, null) // Important for CLEAR mode
        }
        Log.d(TAG, "Created SpotlightView instance.")
    }

    // Update the view with the current state from ViewModel
    spotlightView?.updateSpotlights(state.spotlights, state.globalShape)

    if (!isViewAdded && spotlightView != null && layoutParams != null) {
        try {
            windowManager.addView(spotlightView, layoutParams)
            isViewAdded = true
            Log.d(TAG, "Added SpotlightView to WindowManager.")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding spotlight view", e)
            spotlightView = null
            layoutParams = null
            isViewAdded = false
        }
    }
}

private fun removeOverlayView() {
    // Ensure execution on the main thread
    if (Looper.myLooper() != Looper.getMainLooper()) {
        handler.post { removeOverlayView() }
        return
    }

    spotlightView?.let { view ->
        if (isViewAdded && view.isAttachedToWindow) {
            try {
                windowManager.removeView(view)
                Log.d(TAG, "Removed spotlight view.")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing spotlight view", e)
            }
        }
    }
    spotlightView = null
    layoutParams = null
    isViewAdded = false
}

// Listener implementation now forwards calls to ViewModel
private fun createInteractionListener(
    view: SpotlightView,
    params: WindowManager.LayoutParams
): SpotlightView.SpotlightInteractionListener {
    return object : SpotlightView.SpotlightInteractionListener {
        override fun requestWindowMove(deltaX: Float, deltaY: Float) {
            if (isViewAdded) {
                params.x += deltaX.toInt()
                params.y += deltaY.toInt()
                try { windowManager.updateViewLayout(view, params) }
                catch (e: Exception) { Log.e(TAG, "Error updating layout on move", e) }
            }
        }
        override fun requestWindowMoveFinished() {
            // No state to save for window position in this design
            Log.d(TAG, "Window move finished")
        }
        override fun requestUpdateSpotlightState(updatedSpotlight: SpotlightView.Spotlight) {
            Log.d(TAG,"Listener: Request update for Spotlight ID ${updatedSpotlight.id}")
            viewModel.updateSpotlightState(updatedSpotlight)
        }
        override fun requestTapPassThrough() {
            enableTapPassThrough(view, params)
        }
        override fun requestClose(spotlightId: Int) {
            Log.d(TAG,"Listener: Request close for Spotlight ID $spotlightId")
            viewModel.deleteSpotlight(spotlightId)
            // Service stop logic is handled by the ViewModel observer
        }
        override fun requestShapeChange() {
            Log.d(TAG,"Listener: Request shape change")
            viewModel.cycleGlobalShape() // Ask ViewModel to cycle
        }
        override fun requestAddNew() {
            Log.d(TAG,"Listener: Request add new")
            val displayMetrics = resources.displayMetrics
            viewModel.addSpotlight(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }
}

// --- Tap Pass-Through Logic (no changes needed) ---
private fun enableTapPassThrough(view: SpotlightView, params: WindowManager.LayoutParams) {
    if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0) {
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
            handler.postDelayed({ removeTapPassThroughFlag(view, params) }, PASS_THROUGH_DELAY_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating layout for pass-through", e)
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
    }
}

private fun removeTapPassThroughFlag(view: SpotlightView, params: WindowManager.LayoutParams) {
    if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        try {
            if (view.isAttachedToWindow) {
                windowManager.updateViewLayout(view, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating layout to remove pass-through", e)
        }
    }
}
// --- End Tap Pass-Through Logic ---

override fun onDestroy() {
    Log.d(TAG, "onDestroy")
    stateObserverJob?.cancel() // Cancel observer job
    removeOverlayView() // Ensure view is removed
    viewModelStore.clear() // Clear ViewModels
    super.onDestroy()
}

override fun onBind(intent: Intent?): IBinder? {
    super.onBind(intent)
    return null
}

private fun createNotificationChannel() {
    // ... (implementation remains the same) ...
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Spotlight Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}
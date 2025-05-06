// SpotlightService.kt
package com.example.purramid.thepurramid.spotlight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.purramid.thepurramid.MainActivity // Or appropriate entry point
import com.example.purramid.thepurramid.R
import java.util.concurrent.ConcurrentHashMap

// Define actions for controlling the service via Intents
const val ACTION_START_SPOTLIGHT = "com.example.purramid.START_SPOTLIGHT"
const val ACTION_STOP_SPOTLIGHT = "com.example.purramid.STOP_SPOTLIGHT"
const val ACTION_ADD_SPOTLIGHT = "com.example.purramid.ADD_SPOTLIGHT"
const val ACTION_REMOVE_SPOTLIGHT = "com.example.purramid.REMOVE_SPOTLIGHT" // Added
const val ACTION_CHANGE_SHAPE = "com.example.purramid.CHANGE_SHAPE"     // Added
const val EXTRA_SPOTLIGHT_ID = "spotlight_id"

class SpotlightService : Service() {

    private lateinit var windowManager: WindowManager
    private val spotlightViews = ConcurrentHashMap<Int, Pair<SpotlightView, WindowManager.LayoutParams>>()
    private var nextSpotlightId = 1 // Simple ID generator
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "SpotlightService"
        private const val NOTIFICATION_ID = 3 // Choose a unique ID
        private const val CHANNEL_ID = "SpotlightServiceChannel"
        private const val PASS_THROUGH_DELAY_MS = 50L // Short delay to remove pass-through flag
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_SPOTLIGHT -> {
                startForegroundService()
                // Add initial spotlight if none exist
                if (spotlightViews.isEmpty()) {
                    addSpotlightView()
                }
            }
            ACTION_STOP_SPOTLIGHT -> {
                stopService()
            }
            ACTION_ADD_SPOTLIGHT -> {
                addSpotlightView()
            }
            ACTION_REMOVE_SPOTLIGHT -> {
                 val idToRemove = intent.getIntExtra(EXTRA_SPOTLIGHT_ID, -1)
                 if (idToRemove != -1) {
                     removeSpotlightView(idToRemove)
                 }
            }
            ACTION_CHANGE_SHAPE -> {
                 // Cycle through shapes for all views, or implement per-spotlight later
                 changeGlobalShape()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java) // Intent to open on notification tap
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Spotlight Active")
            .setContentText("Spotlight overlay is running.")
            .setSmallIcon(R.drawable.ic_spotlight) // Use your spotlight icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopService() {
        Log.d(TAG, "Stopping service")
        spotlightViews.keys.forEach { id -> removeSpotlightView(id, updateManager = false) } // Remove views without map concurrent modification
        spotlightViews.clear()
        stopForeground(true)
        stopSelf()
    }


    private fun createLayoutParams(width: Int, height: Int, x: Int = 0, y: Int = 0): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                // Older versions might need TYPE_PHONE or other permissions
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT // Requires SYSTEM_ALERT_WINDOW
            },
            // Start with NOT_FOCUSABLE and NOT_TOUCH_MODAL. Add/remove NOT_TOUCHABLE dynamically.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun addSpotlightView() {
        if (spotlightViews.size >= 4) { // Limit spotlights
            Log.w(TAG, "Maximum number of spotlights reached.")
            // Optionally show a Toast message
            // Toast.makeText(this, "Maximum spotlights reached", Toast.LENGTH_SHORT).show()
             return
        }

        val spotlightId = nextSpotlightId++
        val context = this // Service context

        // Create initial params (cover screen initially, adjust as needed)
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Position new spotlights slightly offset or centered
        val initialX = (spotlightViews.size % 2) * 50 // Example positioning
        val initialY = (spotlightViews.size / 2) * 50 // Example positioning

        val params = createLayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, initialX, initialY)

        val spotlightView = SpotlightView(context, null).apply {
            // Set initial spotlight properties (e.g., centered in its overlay)
            // Note: SpotlightView's onSizeChanged won't work the same way here.
            // We need to manually calculate initial center/radius.
            val initialRadius = 150f // Example radius
            spotlights.clear() // Clear default spotlight
            spotlights.add(SpotlightView.Spotlight(
                 (screenWidth / 2f), // Centered initially
                 (screenHeight / 2f),// Centered initially
                 initialRadius,
                 shape = SpotlightView.Spotlight.Shape.CIRCLE // Default shape
            ))
             // Set the listener to communicate back to the service
             interactionListener = object : SpotlightView.SpotlightInteractionListener {
                override fun requestUpdateLayout(viewParams: WindowManager.LayoutParams) {
                    try {
                        windowManager.updateViewLayout(this@apply, viewParams)
                    } catch (e: Exception) { Log.e(TAG, "Error updating layout", e) }
                }
                 override fun requestTapPassThrough() {
                    enableTapPassThrough(this@apply, params)
                }
                override fun requestClose() {
                   removeSpotlightView(spotlightId)
                }
                 override fun requestShapeChange() {
                     // Trigger global shape change, or implement per-spotlight
                     changeGlobalShape()
                 }
                 override fun requestAddNew() {
                     addSpotlightView()
                 }
            }
            tag = spotlightId // Store ID for later retrieval if needed
        }


        try {
            windowManager.addView(spotlightView, params)
            spotlightViews[spotlightId] = Pair(spotlightView, params)
            Log.d(TAG, "Added spotlight view with ID: $spotlightId")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding spotlight view", e)
            nextSpotlightId-- // Decrement ID if add failed
        }
    }

    // Modify removeSpotlightView to accept an optional flag
    private fun removeSpotlightView(id: Int, updateManager: Boolean = true) {
        spotlightViews[id]?.let { (view, _) ->
            try {
                windowManager.removeView(view)
                Log.d(TAG, "Removed spotlight view with ID: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing spotlight view $id", e)
            } finally {
                if (updateManager) { // Avoid concurrent modification if called during iteration
                   spotlightViews.remove(id)
                }
            }
        }
        // If removing the last spotlight, stop the service? Optional.
         if (updateManager && spotlightViews.isEmpty()) {
              // stopService() // Uncomment if service should stop when last spotlight closed
         }
    }

    private fun changeGlobalShape() {
        // Simple cycling logic for demonstration
        val currentGlobalShape = spotlightViews.values.firstOrNull()?.first?.currentShape ?: SpotlightView.Spotlight.Shape.CIRCLE
        val nextShape = when (currentGlobalShape) {
            SpotlightView.Spotlight.Shape.CIRCLE -> SpotlightView.Spotlight.Shape.SQUARE
            SpotlightView.Spotlight.Shape.SQUARE -> SpotlightView.Spotlight.Shape.CIRCLE
             // Add OVAL/RECTANGLE if implemented
             else -> SpotlightView.Spotlight.Shape.CIRCLE
        }
        // Apply to all existing views
        spotlightViews.values.forEach { (view, _) ->
            view.setGlobalShape(nextShape) // Add this method to SpotlightView
        }
    }

    // --- Tap Pass-Through Logic ---
    private fun enableTapPassThrough(view: SpotlightView, params: WindowManager.LayoutParams) {
        if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0) {
             params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
             try {
                 windowManager.updateViewLayout(view, params)
                 // Schedule removal of the flag
                 handler.postDelayed({ removeTapPassThroughFlag(view, params) }, PASS_THROUGH_DELAY_MS)
             } catch (e: Exception) {
                 Log.e(TAG, "Error updating layout for pass-through", e)
                 // Attempt to revert flag immediately if update failed
                 params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
             }
        }
    }

    private fun removeTapPassThroughFlag(view: SpotlightView, params: WindowManager.LayoutParams) {
         if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
             try {
                 // Check if view is still attached before updating
                  if (view.isAttachedToWindow) {
                      windowManager.updateViewLayout(view, params)
                  }
             } catch (e: Exception) {
                 Log.e(TAG, "Error updating layout to remove pass-through", e)
                 // Flag might remain incorrectly set if update fails repeatedly
             }
         }
    }
    // --- End Tap Pass-Through Logic ---

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        // Ensure all views are removed if service is destroyed unexpectedly
        stopService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spotlight Service Channel",
                NotificationManager.IMPORTANCE_LOW // Low importance is often suitable for overlays
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
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
import com.example.purramid.thepurramid.data.db.SpotlightDao
import com.example.purramid.thepurramid.MainActivity // Or appropriate entry point
import com.example.purramid.thepurramid.R
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Define actions for controlling the service via Intents
const val ACTION_START_SPOTLIGHT = "com.example.purramid.START_SPOTLIGHT"
const val ACTION_STOP_SPOTLIGHT = "com.example.purramid.STOP_SPOTLIGHT"
const val ACTION_ADD_SPOTLIGHT = "com.example.purramid.ADD_SPOTLIGHT"
const val ACTION_REMOVE_SPOTLIGHT = "com.example.purramid.REMOVE_SPOTLIGHT" // Added
const val ACTION_CHANGE_SHAPE = "com.example.purramid.CHANGE_SHAPE"     // Added
const val EXTRA_SPOTLIGHT_ID = "spotlight_id"

@AndroidEntryPoint
class SpotlightService : Service() {

    // Inject dependencies
    @Inject lateinit var windowManager: WindowManager
    @Inject lateinit var spotlightDao: SpotlightDao // Inject DAO

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

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
        loadSpotlightsFromDb()
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

    // --- Database Operations ---
    private fun loadSpotlightsFromDb() {
        serviceScope.launch(Dispatchers.IO) {
            val loadedEntities = spotlightDao.getAllSpotlights()
            withContext(Dispatchers.Main) {
                spotlightViews.clear() // Clear existing views before loading
                if (loadedEntities.isNotEmpty()) {
                    Log.d(TAG, "Loading ${loadedEntities.size} spotlights from DB")
                    loadedEntities.forEach { entity ->
                        // Update nextSpotlightId to avoid collisions
                        nextSpotlightId = maxOf(nextSpotlightId, entity.id + 1)
                        addSpotlightViewFromEntity(entity)
                    }
                } else {
                    Log.d(TAG, "No spotlights found in DB, adding initial one.")
                    // Add initial spotlight if DB is empty *after* checking
                    if (spotlightViews.isEmpty()){ // Double check after async load
                        addSpotlightView()
                    }
                }
            }
        }
    }

    private fun saveSpotlightToDb(spotlight: SpotlightView.Spotlight, id: Int) {
        val entity = SpotlightStateEntity(
            id = id,
            centerX = spotlight.centerX,
            centerY = spotlight.centerY,
            radius = spotlight.radius,
            shape = spotlight.shape.name, // Convert enum to string
            width = spotlight.width,
            height = spotlight.height,
            size = spotlight.size
        )
        serviceScope.launch(Dispatchers.IO) {
            try {
                spotlightDao.insertOrUpdate(entity)
                Log.d(TAG, "Saved spotlight $id state to DB.")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving spotlight $id to DB", e)
            }
        }
    }

    private fun deleteSpotlightFromDb(id: Int) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                spotlightDao.deleteSpotlightById(id) // Use a delete by ID method
                Log.d(TAG, "Deleted spotlight $id from DB.")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting spotlight $id from DB", e)
            }
        }
    }

    // Method to add view based on loaded entity data
    private fun addSpotlightViewFromEntity(entity: SpotlightStateEntity) {
        val context = this
        val params = createLayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

        val spotlightView = SpotlightView(context, null).apply {
            val spotlightData = SpotlightView.Spotlight(
                centerX = entity.centerX,
                centerY = entity.centerY,
                radius = entity.radius,
                shape = try { SpotlightView.Spotlight.Shape.valueOf(entity.shape) } catch (e: IllegalArgumentException) { SpotlightView.Spotlight.Shape.CIRCLE }, // Default if string is invalid
                width = entity.width,
                height = entity.height,
                size = entity.size
            )
            this.spotlights = mutableListOf(spotlightData) // Set the loaded spotlight data
            this.currentShape = spotlightData.shape // Sync global shape with loaded

            interactionListener = createInteractionListener(entity.id, this, params) // Use helper
            tag = entity.id
        }

        try {
            windowManager.addView(spotlightView, params)
            spotlightViews[entity.id] = Pair(spotlightView, params)
            Log.d(TAG, "Restored spotlight view with ID: ${entity.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding restored spotlight view ${entity.id}", e)
        }
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
        if (spotlightViews.size >= 4) {
            Log.w(TAG, "Maximum number of spotlights reached.")
            return
        }

        val spotlightId = nextSpotlightId++
        val context = this
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val initialX = (spotlightViews.size % 2) * 50
        val initialY = (spotlightViews.size / 2) * 50

        val params = createLayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, initialX, initialY)

        // Determine initial shape from the first existing spotlight or default to CIRCLE
        val initialShape = spotlightViews.values.firstOrNull()?.first?.currentShape ?: SpotlightView.Spotlight.Shape.CIRCLE
        val initialRadius = 150f
        val initialSize = initialRadius * 2f
        val initialWidth = if(initialShape == SpotlightView.Spotlight.Shape.OVAL || initialShape == SpotlightView.Spotlight.Shape.RECTANGLE) initialRadius * 2 * 1.5f else initialSize
        val initialHeight = if(initialShape == SpotlightView.Spotlight.Shape.OVAL || initialShape == SpotlightView.Spotlight.Shape.RECTANGLE) initialRadius * 2 / 1.5f else initialSize


        val spotlightData = SpotlightView.Spotlight(
            centerX = (screenWidth / 2f), // Initial center
            centerY = (screenHeight / 2f),// Initial center
            radius = initialRadius,
            shape = initialShape, // Use determined shape
            width = initialWidth,
            height = initialHeight,
            size = if(initialShape == SpotlightView.Spotlight.Shape.SQUARE || initialShape == SpotlightView.Spotlight.Shape.RECTANGLE) maxOf(initialWidth, initialHeight) else initialSize
        )

        val spotlightView = SpotlightView(context, null).apply {
            this.spotlights = mutableListOf(spotlightData) // Set initial data
            this.currentShape = initialShape // Sync view's global shape notion
            interactionListener = createInteractionListener(spotlightId, this, params) // Use helper
            tag = spotlightId
        }

        // Save the new spotlight state to DB *before* adding the view
        saveSpotlightToDb(spotlightData, spotlightId)

        try {
            windowManager.addView(spotlightView, params)
            spotlightViews[spotlightId] = Pair(spotlightView, params)
            Log.d(TAG, "Added spotlight view with ID: $spotlightId")
            updateCanAddStateForAllViews() // Update add button state on other views
        } catch (e: Exception) {
            Log.e(TAG, "Error adding spotlight view", e)
            nextSpotlightId-- // Decrement ID
            // Attempt to delete the entry we just tried to save
            deleteSpotlightFromDb(spotlightId)
        }
    }

    // Helper to create interaction listener
    private fun createInteractionListener(
        id: Int,
        view: SpotlightView,
        params: WindowManager.LayoutParams
    ): SpotlightView.SpotlightInteractionListener {
        return object : SpotlightView.SpotlightInteractionListener {
            override fun requestUpdateLayout(viewParams: WindowManager.LayoutParams) {
                try {
                    // Check if view is still attached before updating
                    if (view.isAttachedToWindow) {
                        windowManager.updateViewLayout(view, viewParams)
                        // Save position changes (debounce this?)
                        // saveSpotlightPosition(id, viewParams.x, viewParams.y) // Need separate pos save later
                    }
                } catch (e: Exception) { Log.e(TAG, "Error updating layout for view $id", e) }
            }
            override fun requestTapPassThrough() {
                enableTapPassThrough(view, params)
            }
            override fun requestClose() {
                removeSpotlightView(id)
            }
            override fun requestShapeChange() {
                changeGlobalShape()
            }
            override fun requestAddNew() {
                addSpotlightView()
            }
        }
    }

    private fun removeSpotlightView(id: Int, updateManager: Boolean = true) {
        spotlightViews[id]?.let { (view, _) ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeView(view)
                }
                Log.d(TAG, "Removed spotlight view with ID: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing spotlight view $id", e)
            } finally {
                deleteSpotlightFromDb(id) // Delete from DB
                if (updateManager) {
                    spotlightViews.remove(id)
                    updateCanAddStateForAllViews() // Update other views' button state
                }
            }
        }
        // Service stop logic...
        if (updateManager && spotlightViews.isEmpty()) {
            // stopService() // Optional: Stop if last spotlight is removed
        }
    }

    private fun updateCanAddStateForAllViews() {
        val canAdd = spotlightViews.size < 4
        spotlightViews.values.forEach { (view, _) ->
            view.updateCanAddSpotlights(canAdd)
        }
    }

    private fun changeGlobalShape() {
        val currentGlobalShape = spotlightViews.values.firstOrNull()?.first?.currentShape ?: SpotlightView.Spotlight.Shape.CIRCLE
        val nextShape = when (currentGlobalShape) {
            SpotlightView.Spotlight.Shape.CIRCLE -> SpotlightView.Spotlight.Shape.SQUARE
            SpotlightView.Spotlight.Shape.SQUARE -> SpotlightView.Spotlight.Shape.OVAL
            SpotlightView.Spotlight.Shape.OVAL -> SpotlightView.Spotlight.Shape.RECTANGLE
            SpotlightView.Spotlight.Shape.RECTANGLE -> SpotlightView.Spotlight.Shape.CIRCLE
        }
        spotlightViews.forEach { (id, pair) ->
            val view = pair.first
            view.setGlobalShape(nextShape) // Updates internal shape and dimensions
            // Save the updated state (shape and dimensions) to DB
            view.spotlights.firstOrNull()?.let { updatedData ->
                saveSpotlightToDb(updatedData, id)
            }
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
        serviceScope.coroutineContext.cancelChildren()
        spotlightViews.keys.forEach { id -> removeSpotlightView(id, updateManager = false) }
        spotlightViews.clear()
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
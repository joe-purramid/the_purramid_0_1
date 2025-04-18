// ClockOverlayService.kt
package com.example.thepurramid0_1

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ClockOverlayService : Service() {

    private lateinit var clockView: TextView
    private lateinit var windowManager: WindowManager
    private val activeClocks = mutableListOf<Pair<Int, ViewGroup>>() // Pair of ID and Root View
    private val handler = Handler(Looper.getMainLooper())
    private val clockLayouts = mutableMapOf<Int, WindowManager.LayoutParams>()
    private val clockViews = mutableMapOf<Int, ClockView>()
    private val clockSettings = mutableMapOf<Int, ClockConfig>() // Store settings for each clock
    private val clockCounter = AtomicInteger(0)
    private val activeClockIds = mutableSetOf<Int>()
    // private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private lateinit var sharedPreferences: SharedPreferences
    private val nestedClockPositions = mutableMapOf<Int, Int>() // ClockId to its nested column index

    data class ClockConfig(
        val mode: String,
        val color: Int,
        val timeZoneId: String,
        val displaySeconds: Boolean,
        val isNested: Boolean,
        var layoutParams: WindowManager.LayoutParams
    )

    companion object {
        private const val TAG = "ClockOverlayService"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNewClockInstance(
        id: Int,
        mode: String,
        color: Int,
        timeZoneId: String,
        displaySeconds: Boolean,
        isNested: Boolean,
        initialLayoutParams: WindowManager.LayoutParams
    ) {
        android.util.Log.d(TAG, "createNewClockInstance called for ID: $id")

        // Keep your logic for inflating the layout:
        val inflater = LayoutInflater.from(this)
        val newClockRootView = if (mode == "analog") {
            inflater.inflate(R.layout.view_floating_clock_analog, null) as ViewGroup
        } else {
            inflater.inflate(R.layout.view_floating_clock_digital, null) as ViewGroup
        }

        val clockView = newClockRootView.findViewById<ClockView>(if (mode == "analog") R.id.analogClockViewContainer else R.id.digitalClockView)
        clockView?.setClockId(id)
        clockViews[id] = clockView
        // clockView?.updateSettings() // Apply initial settings
        clockView?.updateSettings(ClockConfig(mode, color, timeZoneId, displaySeconds, isNested, initialLayoutParams)) // Apply loaded settings

        if (mode == "analog") {
            val clockFace = newClockRootView.findViewById<com.caverock.androidsvg.SVGImageView>(R.id.clockFaceImageView)
            val hourHand = newClockRootView.findViewById<com.caverock.androidsvg.SVGImageView>(R.id.hourHandImageView)
            val minuteHand = newClockRootView.findViewById<com.caverock.androidsvg.SVGImageView>(R.id.minuteHandImageView)
            val secondHand = newClockRootView.findViewById<com.caverock.androidsvg.SVGImageView>(R.id.secondHandImageView)
            clockView?.setAnalogImageViews(clockFace, hourHand, minuteHand, secondHand)
        }

        clockLayouts[id] = initialLayoutParams
        activeClocks.add(Pair(id, newClockRootView))
        windowManager.addView(newClockRootView, initialLayoutParams)

        setupTouchListener(id, newClockRootView)
        setupLongClickListener(id, newClockRootView)

        // Create and add the close button
        val closeButtonSizePx = (25f * resources.displayMetrics.density).toInt()
        val closeButtonMarginPx = (5f * resources.displayMetrics.density).toInt()
        val closeButton = TextView(this).apply {
            text = "\u274C" // Close symbol
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.DKGRAY) // Dark gray for the close button
                setSize(closeButtonSizePx, closeButtonSizePx)
            }
            setOnClickListener {
                removeClock(clockId, newClockRootView)
            }
        }

        val closeButtonLayoutParams = FrameLayout.LayoutParams(closeButtonSizePx, closeButtonSizePx).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(closeButtonMarginPx, closeButtonMarginPx, closeButtonMarginPx, closeButtonMarginPx)
        }

        if (newClockRootView is FrameLayout) {
            newClockRootView.addView(closeButton, closeButtonLayoutParams)
        } else if (newClockRootView is LinearLayout) {
            val frameLayoutWrapper = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val existingLayoutParams = newClockRootView.layoutParams
            newClockRootView.layoutParams = LinearLayout.LayoutParams(existingLayoutParams.width, existingLayoutParams.height)
            frameLayoutWrapper.addView(newClockRootView)
            frameLayoutWrapper.addView(closeButton, closeButtonLayoutParams)
            // Replace the LinearLayout with the FrameLayout wrapper in activeClocks and WindowManager
            val index = activeClocks.indexOfFirst { it.first == clockId }
            if (index != -1) {
                activeClocks[index] = Pair(clockId, frameLayoutWrapper)
            }
            windowManager.removeView(newClockRootView)
            windowManager.addView(frameLayoutWrapper, initialLayoutParams)
        } else {
            Toast.makeText(this, "Unsupported layout for Close button.", Toast.LENGTH_SHORT).show()
        }

        updateActiveClockCount()

        // Save the new clock ID and settings
        val currentIds = sharedPreferences.getStringSet("active_clock_ids", mutableSetOf()) ?: mutableSetOf()
        currentIds.add(clockId.toString())
        sharedPreferences.edit().putStringSet("active_clock_ids", currentIds).apply()
        saveClockSettings(clockId, mode, color, timeZoneId, displaySeconds, isNested, initialLayoutParams.x, initialLayoutParams.y, initialLayoutParams.width, initialLayoutParams.height)
    }

    private fun createNewClock(
        mode: String,
        color: Int,
        timeZoneId: String,
        displaySeconds: Boolean,
        isNested: Boolean,
    ) {
        if (activeClocks.size >= 4) return
        val clockId = clockCounter.incrementAndGet()

        // Initial position at center with offset
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val offsetX = 20 * activeClocks.size // Simple offset based on the number of clocks
        val offsetY = 20 * activeClocks.size

        val initialLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth / 2) - (WindowManager.LayoutParams.WRAP_CONTENT / 2) + offsetX
            y = (screenHeight / 2) - (WindowManager.LayoutParams.WRAP_CONTENT / 2) + offsetY
        }

        val clockConfig = ClockConfig(mode, color, timeZoneId, displaySeconds, isNested, initialLayoutParams)
        clockSettings[clockId] = clockConfig

        // Call createNewClockInstance to handle view creation and setup
        createNewClockInstance(
            clockId,
            mode,
            color,
            timeZoneId,
            displaySeconds,
            isNested,
            initialLayoutParams
        )

        saveActiveClocks()
        saveClockSettings(clockId, mode, color, timeZoneId, displaySeconds, isNested, initialLayoutParams.x, initialLayoutParams.y, initialLayoutParams.width, initialLayoutParams.height)
        updateActiveClockCount()
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sharedPreferences = getSharedPreferences("clock_settings", Context.MODE_PRIVATE)

        // Load existing clocks on service start
        loadActiveClocks()

        // Load initial clock if none exist
        if (sharedPreferences.getStringSet("active_clock_ids", emptySet())?.isEmpty() == true) {
            val defaultLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100 // Example initial X position (adjust as needed)
                y = 100 // Example initial Y position (adjust as needed)
            }
            createNewClock(
                sharedPreferences.getString("clock_mode", "digital") ?: "digital",
                sharedPreferences.getInt("clock_color", Color.BLACK),
                sharedPreferences.getString("time_zone_id", TimeZone.getDefault().id) ?: TimeZone.getDefault().id,
                sharedPreferences.getBoolean("display_seconds", false),
                sharedPreferences.getBoolean("nest_clock", false),
                defaultLayoutParams // Passing the default layout parameters
            )
        // Keep track of clock count
        updateActiveClockCount()
    }

        // Create a notification for foreground service
        val notificationId = 1
        val channelId = "clock_overlay_channel"
        val channelName = "Clock Overlay Service"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_clock) // Replace with your icon
            .setContentTitle("Floating Clock")
            .setContentText("Clock overlay is running")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notification = notificationBuilder.build()

        // Start the service in the foreground
        startForeground(notificationId, notification)

        updateActiveClockCount()
    }

    private fun addClock(clockId: Int, rootView: ViewGroup, initialX: Int, initialY: Int) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        val inflater = LayoutInflater.from(this)
        val rootView: ViewGroup
        val clockView: ClockView

        if (clockSettings.containsKey(clockId) && clockSettings[clockId]?.mode == "analog") {
            // Inflate the layout for the analog clock
            val analogRootView = inflater.inflate(R.layout.view_floating_clock_analog, null) as LinearLayout
            val analogFrameLayout = analogRootView.getChildAt(0) as FrameLayout

            clockView = ClockView(this, null) // Instantiate ClockView

            val analogClockFace = analogFrameLayout.findViewById<ImageView>(R.id.clockFaceImageView)
            val analogHourHand = analogFrameLayout.findViewById<ImageView>(R.id.hourHandImageView)
            val analogMinuteHand = analogFrameLayout.findViewById<ImageView>(R.id.minuteHandImageView)
            val analogSecondHand = analogFrameLayout.findViewById<ImageView>(R.id.secondHandImageView)
            clockView?.setAnalogImageViews(analogClockFace, analogHourHand, analogMinuteHand, analogSecondHand)
            rootView = analogRootView
        } else {
            // Inflate the layout for the digital clock
            rootView = inflater.inflate(R.layout.view_floating_clock, null) as FrameLayout // Or LinearLayout, depending on your close button logic
            clockView = rootView.findViewById(R.id.digitalClockView) // Assuming you have a DigitalClockView (or ClockView handling digital) in this layout
            // If your ClockView handles both, you might need to set a mode here
        }

        clockView.setClockId(clockId)
        clockViews[clockId] = clockView
        clockView.setWindowManagerAndLayoutParams(windowManager, params)

        activeClocks[clockId] = rootView

        setupTouchListener(clockId, rootView)
        windowManager.addView(rootView, params)
        activeClockIds.add(clockId)
        saveActiveClocks()
    }

    private fun updateActiveClockCount() {
        val currentCount = activeClocks.size
        android.util.Log.d(TAG, "Number of active clocks: $currentCount")
    }

    private fun loadActiveClocks() {
        val activeClockIds = sharedPreferences.getStringSet("active_clock_ids", emptySet()) ?: emptySet()
        for (idStr in activeClockIds) {
            val id = idStr.toIntOrNull() ?: continue
            val mode = sharedPreferences.getString("clock_${id}_mode", "digital") ?: "digital"
            val color = sharedPreferences.getInt("clock_${id}_color", Color.WHITE)
            val timeZoneId = sharedPreferences.getString("clock_${id}_set_time_zone", TimeZone.getDefault().id) ?: TimeZone.getDefault().id
            val displaySeconds = sharedPreferences.getBoolean("clock_${id}_display_seconds", false)
            val isNested = sharedPreferences.getBoolean("clock_${id}_nest", false)
            val savedX = sharedPreferences.getInt("clock_${id}_x", getInitialLayoutParams(activeClocks.size).x)
            val savedY = sharedPreferences.getInt("clock_${id}_y", getInitialLayoutParams(activeClocks.size).y)
            val savedWidth = sharedPreferences.getInt("clock_${id}_width", WindowManager.LayoutParams.WRAP_CONTENT)
            val savedHeight = sharedPreferences.getInt("clock_${id}_height", WindowManager.LayoutParams.WRAP_CONTENT)

            val initialLayoutParams = WindowManager.LayoutParams(
                savedWidth,
                savedHeight,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSPARENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = savedX
                y = savedY
            }
            
            clockSettings[id] = ClockConfig(mode, color, timeZoneId, displaySeconds, isNested, initialLayoutParams)
            createNewClockInstance(id, mode, color, timeZoneId, displaySeconds, isNested, initialLayoutParams)
            if (isNested) {
                // Apply nest mode to all initially nested clocks
                applyNestMode(id, activeClocks.find { it.first == id }?.second)
            }
        }
        // After loading all clocks, ensure the nested positions are consistent
        repositionNestedClocks()
        updateActiveClockCount()
    }
                
    private fun getInitialLayoutParams(index: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (20 * index).toInt()
            y = (20 * index).toInt()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "com.example.com.example.thepurramid0_1.ADD_NEW_CLOCK" -> {
            if (activeClocks.size < 4) {
                val mode = intent.getStringExtra("clock_mode") ?: "digital"
                val color = intent.getIntExtra("clock_color", Color.WHITE)
                val timeZoneId = intent.getStringExtra("time_zone_id") ?: TimeZone.getDefault().id
                val displaySeconds = intent.getBooleanExtra("display_seconds", false)
                val isNested = intent.getBooleanExtra("nest_clock", false)
                createNewClock(mode, color, timeZoneId, displaySeconds, isNested)
            } else {
                Toast.makeText(this, "Maximum of 4 clocks reached.", Toast.LENGTH_SHORT).show()
            }
        } 

        ClockSettingsActivity.ACTION_NEST_CLOCK -> {
            val clockIdToNest = intent.getIntExtra(ClockSettingsActivity.EXTRA_CLOCK_ID, -1)
            val nestState = intent.getBooleanExtra(ClockSettingsActivity.EXTRA_NEST_STATE, false)
            if (clockIdToNest != -1) {
                val clockPair = activeClocks.find { it.first == clockIdToNest }
                clockPair?.second?.let { rootView ->
                    val config = clockSettings[clockIdToNest]
                    if (config != null) {
                        config.isNested = nestState
                        applyNestMode(clockIdToNest, rootView)
                        // Save the nest state
                        sharedPreferences.edit().putBoolean("clock_${clockIdToNest}_nest", nestState).apply()
                    }
                }

        ClockSettingsActivity.ACTION_NEST_CLOCK -> {
            val clockIdToNest = intent.getIntExtra(ClockSettingsActivity.EXTRA_CLOCK_ID, -1)
            val nestState = intent.getBooleanExtra(ClockSettingsActivity.EXTRA_NEST_STATE, false)
            if (clockIdToNest != -1) {
                val clockPair = activeClocks.find { it.first == clockIdToNest }
                clockPair?.second?.let { rootView ->
                    val config = clockSettings[clockIdToNest]
                    if (config != null) {
                        config.isNested = nestState
                        applyNestMode(clockIdToNest, rootView)
                        // Save the nest state
                        sharedPreferences.edit().putBoolean("clock_${clockIdToNest}_nest", nestState).apply()
                    }
                }
            }
        }
        else {
            // Handle other actions if needed
            }
        }
        updateActiveClockCount()
        return START_STICKY
    }

    private fun addCloseButton(clockId: Int, rootView: ViewGroup) {
        val closeButtonSizePx = (25f * resources.displayMetrics.density).toInt()
        val closeButtonMarginPx = (5f * resources.displayMetrics.density).toInt()
        val closeButton = TextView(this).apply {
            text = "\u274C" // Close symbol
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.DKGRAY) // Dark gray for the close button
                setSize(closeButtonSizePx, closeButtonSizePx)
            }
            setOnClickListener {
                removeClock(clockId, rootView)
            }
        }

        val closeButtonLayoutParams = FrameLayout.LayoutParams(closeButtonSizePx, closeButtonSizePx).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(closeButtonMarginPx, closeButtonMarginPx, closeButtonMarginPx, closeButtonMarginPx)
        }

        if (rootView is FrameLayout) {
            rootView.addView(closeButton, closeButtonLayoutParams)
        } else if (rootView is LinearLayout) {
            val frameLayoutWrapper = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val existingLayoutParams = rootView.layoutParams
            rootView.layoutParams = LinearLayout.LayoutParams(existingLayoutParams.width, existingLayoutParams.height)
            frameLayoutWrapper.addView(rootView)
            frameLayoutWrapper.addView(closeButton, closeButtonLayoutParams)
            // Update activeClocks to hold the FrameLayout wrapper
            val index = activeClocks.indexOfFirst { it.first == clockId }
            if (index != -1) {
                activeClocks[index] = Pair(clockId, frameLayoutWrapper)
            }
            windowManager.removeView(rootView)
            windowManager.addView(frameLayoutWrapper, clockLayouts[clockId]) // Use the stored layout params
        } else {
            Toast.makeText(this, "Unsupported layout for Close button.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
    super.onDestroy()
    if (::windowManager.isInitialized && ::clockView.isInitialized && clockView.isAttachedToWindow) {
        windowManager.removeView(clockView)
    }
    handler.removeCallbacksAndMessages(null)
    }

    private fun saveActiveClocks() {
        val currentIds = activeClocks.map { it.first.toString() }.toSet()
        sharedPreferences.edit().putStringSet("active_clock_ids", currentIds).apply()
        android.util.Log.d(TAG, "saveActiveClocks called. Active IDs: $currentIds")
    }

    private fun removeClock(clockIdToRemove: Int, rootViewToRemove: View) {
        try {
            windowManager.removeView(rootViewToRemove)
            activeClocks.removeAll { it.first == clockIdToRemove }
            clockSettings.remove(clockIdToRemove)

            val currentIds = sharedPreferences.getStringSet("active_clock_ids", emptySet())?.toMutableSet()
            currentIds?.remove(clockIdToRemove.toString())
            sharedPreferences.edit().putStringSet("active_clock_ids", currentIds).apply()
            sharedPreferences.edit().remove("clock_${clockIdToRemove}_mode").apply()
            sharedPreferences.edit().remove("clock_${clockIdToRemove}_color").apply()
            sharedPreferences.edit().remove("clock_${clockIdToRemove}_time_zone_id").apply()
            sharedPreferences.edit().remove("clock_${clockIdToRemove}_display_seconds").apply()
            sharedPreferences.edit().remove("clock_${clockIdToRemove}_nest").apply()
            sharedPreferences.edit().remove("clock_${clockIdToRemove}_x").apply()
            sharedPreferences.edit().remove("clock_${clockIdToRemove}_y").apply()
            sharedPreferences.edit().remove("clock_${clockIdToRemove}_width").apply()
            sharedPreferences.edit().remove("clock_${clockIdToRemove}_height").apply()

            nestedClockPositions.remove(clockIdToRemove)
            repositionNestedClocks()
            updateActiveClockCount()

        } catch (e: IllegalArgumentException) {
            // View might have already been removed
            Log.e(TAG, "Error removing clock view: ${e.message}")
        }
    }
    
    newClockRootView.setOnLongClickListener { view ->
        showClockActionsTooltip(clockId, view)
        true
    }

    // Implement hold-and-drag and resize functionality
    newClockRootView.setOnTouchListener(object : View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        var initialWidth = 0
        var initialHeight = 0
        var initialDistance = 0f

        var pointerId1 = -1
        var pointerId2 = -1

        var isDragging = false
        var isResizing = false
        var movedDistance = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = true
                    isResizing = false
                    pointerId1 = event.getPointerId(0)
                    pointerId2 = -1
                    movedDistance = 0f
                    return false // Allow long click
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2 && isDragging) {
                        isDragging = false
                        isResizing = true
                        initialWidth = view.width
                        initialHeight = view.height
                        pointerId2 = event.getPointerId(1)
                        initialDistance = MotionEvent.distance(event, 0, 1)
                        movedDistance = 0f
                        return true
                    } else if (event.pointerCount == 2 && !isDragging && !isResizing) {
                        isResizing = true
                        initialWidth = view.width
                        initialHeight = view.height
                        pointerId1 = event.getPointerId(0)
                        pointerId2 = event.getPointerId(1)
                        initialDistance = MotionEvent.distance(event, 0, 1)
                        movedDistance = 0f
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val currentTouchX = event.rawX
                    val currentTouchY = event.rawY
                    val deltaMoveX = currentTouchX - initialTouchX
                    val deltaMoveY = currentTouchY - initialTouchY
                    movedDistance = sqrt((deltaMoveX * deltaMoveX + deltaMoveY * deltaMoveY).toDouble()).toFloat()

                    val touchSlop = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        8f, // Minimum distance threshold in dp
                        resources.displayMetrics
                    )

                    if (isDragging && event.pointerCount == 1 && movedDistance > touchSlop) {
                        var newX = initialX + deltaMoveX.toInt()
                        var newY = initialY + deltaMoveY.toInt()

                        val screenWidth = resources.displayMetrics.widthPixels
                        val screenHeight = resources.displayMetrics.heightPixels
                        val clockWidth = view.width
                        val clockHeight = view.height

                        newX = max(0, min(newX, screenWidth - clockWidth))
                        newY = max(0, min(newY, screenHeight - clockHeight))

                        layoutParams.x = newX
                        layoutParams.y = newY
                        windowManager.updateViewLayout(view, layoutParams)
                        return true
                    } else if (isResizing && event.pointerCount >= 2 && movedDistance > touchSlop) {
                        val index1 = event.findPointerIndex(pointerId1)
                        val index2 = event.findPointerIndex(pointerId2)

                        if (index1 != -1 && index2 != -1) {
                            val newDistance = MotionEvent.distance(event, index1, index2)
                            val scaleFactor = newDistance / initialDistance

                            val newWidth = (initialWidth * scaleFactor).toInt()
                            val newHeight = (initialHeight * scaleFactor).toInt()

                            val minSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50f, resources.displayMetrics).toInt()
                            val maxSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 500f, resources.displayMetrics).toInt()

                            layoutParams.width = max(minSize, min(newWidth, maxSize))
                            layoutParams.height = max(minSize, min(newHeight, maxSize))
                            windowManager.updateViewLayout(view, layoutParams)
                            return true
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    if (isResizing && event.pointerCount < 2) {
                        isResizing = false
                        isDragging = false
                        // Store the new dimensions
                        clockSettings[clockId]?.layoutParams?.width = layoutParams.width
                        clockSettings[clockId]?.layoutParams?.height = layoutParams.height
                        saveClockDimensions(clockId, layoutParams.width, layoutParams.height)
                        return true
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        isDragging = false
                        // Save the new position
                        clockSettings[clockId]?.layoutParams?.x = layoutParams.x
                        clockSettings[clockId]?.layoutParams?.y = layoutParams.y
                        return false
                    }
                }
            }
            return false
        }

        private fun MotionEvent.distance(index1: Int, index2: Int): Float {
            val x = getX(index1) - getX(index2)
            val y = getY(index1) - getY(index2)
            return sqrt((x * x + y * y).toDouble()).toFloat()
        }
    })
}

    val initialLayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSPARENT
    )
    initialLayoutParams.gravity = Gravity.TOP or Gravity.START
    initialLayoutParams.x = (20 * activeClocks.size).toInt() // Offset new clocks slightly
    initialLayoutParams.y = (20 * activeClocks.size).toInt()

    clockSettings[clockId] = ClockConfig(mode, color, timeZoneId, displaySeconds, isNested, initialLayoutParams)
    activeClocks.add(Pair(clockId, newClockRootView))
    windowManager.addView(newClockRootView, initialLayoutParams)

    applyNestMode(clockId, newClockRootView, isNested)
    updateActiveClockCount()

    // Set OnLongClickListener for the root view
    newClockRootView.setOnLongClickListener { view ->
        showClockActionsTooltip(clockId, view)
        true // Consume the long click event
    }
}

private var currentTooltipWindow: PopupWindow? = null

private fun showClockActionsTooltip(clockId: Int, anchorView: View) {
    val inflater = LayoutInflater.from(this)
    val tooltipView = inflater.inflate(R.layout.tooltip_clock_actions, null)

    val pausePlayTextView = tooltipView.findViewById<TextView>(R.id.tooltipPausePlay)
    val resetTextView = tooltipView.findViewById<TextView>(R.id.tooltipReset)
    val settingsTextView = tooltipView.findViewById<TextView>(R.id.tooltipSettings)

    val clockPair = activeClocks.find { it.first == clockId }
    val clockRootView = clockPair?.second
    val clockView = clockRootView?.findViewById<ClockView>(if (clockSettings[clockId]?.mode == "analog") R.id.analogClockView else R.id.digitalClockView)

    // Set initial pause/play text and icon
    if (clockView?.isPaused == true) {
        pausePlayTextView.text = getString(R.string.tooltip_play)
        pausePlayTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play, null, null, null)
    } else {
        pausePlayTextView.text = getString(R.string.tooltip_pause)
        pausePlayTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause, null, null, null)
    }

    pausePlayTextView.setOnClickListener {
        clockView?.let {
            if (it.isPaused) {
                it.playTime()
                pausePlayTextView.text = getString(R.string.pause)
                pausePlayTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause, null, null, null)
            } else {
                it.pauseTime()
                pausePlayTextView.text = getString(R.string.play)
                pausePlayTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play, null, null, null)
            }
        }
        resetClockHighlight()
        resetClockDimming()
        currentTooltipWindow?.dismiss()
    }

    resetTextView.setOnClickListener {
        clockView?.resetTime()
        resetClockHighlight()
        resetClockDimming()
        currentTooltipWindow?.dismiss()
    }

    settingsTextView.setOnClickListener {
        // Highlight the current clock
        highlightClock(clockId)
        // Dim other clocks
        dimOtherClocks(clockId)
        clockView?.launchSettings()
        currentTooltipWindow?.dismiss()
    }

    currentTooltipWindow?.dismiss() // Dismiss any existing tooltip
    val popupWindow = PopupWindow(
        tooltipView,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        true // Focusable so touches outside dismiss it
    )

    // Set background for better visibility
    popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.tooltip_background))

    // Show the tooltip near the long-press location
    val location = IntArray(2)
    anchorView.getLocationOnScreen(location)
    popupWindow.showAtLocation(
        anchorView,
        Gravity.NO_GRAVITY,
        location[0] + anchorView.width / 2 - popupWindow.width / 2,
        location[1] - popupWindow.height - 20
    )

    currentTooltipWindow = popupWindow
}

private fun highlightClock(clockId: Int) {
    for ((id, view) in activeClocks) {
        if (id == clockId) {
            // Apply a highlight effect (e.g., a border)
            view.background = ContextCompat.getDrawable(this, clock_highlight_border)
        }
    }
}

private fun resetClockHighlight() {
    for ((_, view) in activeClocks) {
        view.background = null // Remove the highlight
    }
}

private fun dimOtherClocks(activeClockId: Int) {
    for ((id, view) in activeClocks) {
        if (id != activeClockId) {
            view.alpha = 0.5f // Adjust the dimming level as needed
        }
    }
}

private fun resetClockDimming() {
    for ((_, view) in activeClocks) {
        view.alpha = 1.0f
    }
}

    private fun setupLongClickListener(clockId: Int, rootView: View) {
        rootView.setOnLongClickListener { view ->
            showClockActionsTooltip(clockId, view)
            true // Consume the long click event
        }
    }

    private fun setupTouchListener(clockId: Int, rootView: View) {
        val touchListener = object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = clockLayouts[clockId]?.x ?: 0
                        initialY = clockLayouts[clockId]?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        val params = clockLayouts[clockId]
                        params?.x = initialX + deltaX.toInt()
                        params?.y = initialY + deltaY.toInt()
                        windowManager.updateViewLayout(rootView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Save the new position when the touch ends
                        val finalX = clockLayouts[clockId]?.x ?: 0
                        val finalY = clockLayouts[clockId]?.y ?: 0
                        saveClockPosition(clockId, finalX, finalY)
                        return true
                    }
                    else -> return false
                }
            }
        }
        rootView.setOnTouchListener(touchListener)
    }

    private fun saveClockPosition(clockId: Int, x: Int, y: Int) {
        sharedPreferences.edit()
            .putInt("clock_${clockId}_x", x)
            .putInt("clock_${clockId}_y", y)
            .apply()
    }

    private fun setupButtonListeners(clockId: Int, rootView: ViewGroup) {
        val pausePlayButton = rootView.findViewById<Button>(R.id.pausePlayButton)
        val resetButton = rootView.findViewById<Button>(R.id.resetButton)
        val settingsButton = rootView.findViewById<Button>(R.id.settingsButton)
        val clockView = rootView.findViewById<ClockView>(if (clockSettings[clockId]?.mode == "analog") R.id.analogClockView else R.id.digitalClockView)

        pausePlayButton?.setOnClickListener {
            clockView?.let {
                if (it.isPaused) {
                    it.playTime()
                    pausePlayButton.text = getString(R.string.pause)
                } else {
                    it.pauseTime()
                    pausePlayButton.text = getString(R.string.play)
                }
            }
        }

        resetButton?.setOnClickListener {
            clockView?.resetTime()
            pausePlayButton?.text = getString(R.string.pause) // Reset also implies playing
        }

        settingsButton?.setOnClickListener {
            clockView?.launchSettings()
        }
    }

    private fun applyNestMode(nestedId: Int, nestedView: ViewGroup?) {
        val config = clockSettings[clockId] ?: return

        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val nestedMarginDp = 10f
        val nestedMarginPx = (nestedMarginDp * density).toInt()
        val nestedWidthDp = 60f // Adjust as needed
        val nestedWidthPx = (nestedWidthDp * density).toInt()
        val scale = 0.3f

       // Hide control buttons
        rootView.findViewById<View>(R.id.pausePlayButton)?.visibility = View.GONE
        rootView.findViewById<View>(R.id.resetButton)?.visibility = View.GONE
        rootView.findViewById<View>(R.id.settingsButton)?.visibility = View.GONE

        // Scale down the entire root view
        rootView.scaleX = scale
        rootView.scaleY = scale

        // Determine the current number of nested clocks to calculate the column index
        val numberOfNested = clockSettings.count { it.value.isNested }
        nestedClockPositions.putIfAbsent(clockId, numberOfNested - 1) // Assign index

        // Calculate the vertical position based on the number of nested clocks
        val nestedIndex = nestedClockPositions.getOrPut(clockId) {
            nestedClockPositions.size
        }
        val scaledHeight = (nestedWidthPx * scale).toInt() // Assuming roughly square
        val nestedY = nestedMarginPx + (nestedIndex * (nestedWidthPx * scale * 1.2f)) // Adjust spacing

        config.layoutParams.width = (nestedWidthPx * scale).toInt()
        config.layoutParams.height = (nestedWidthPx * scale).toInt() // Assume a roughly square clock for nesting
        config.layoutParams.gravity = Gravity.TOP or Gravity.END
        config.layoutParams.x = screenWidth - (nestedWidthPx * scale).toInt() - nestedMarginPx
        config.layoutParams.y = nestedY.toInt()
        windowManager.updateViewLayout(rootView, config.layoutParams)

        // Add an "Exit Nest" button
val exitNestButton = TextView(this).apply {
            text = "\u274C" // Using the Cross Mark symbol
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, exitButtonTextSizeSp)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.RED) // You can choose a different color
                setSize(buttonSizePx, buttonSizePx)
            }
            setOnClickListener {
                config.isNested = false
                // Restore visibility of control buttons
                rootView.findViewById<View>(R.id.pausePlayButton)?.visibility = View.VISIBLE
                rootView.findViewById<View>(R.id.resetButton)?.visibility = View.VISIBLE
                rootView.findViewById<View>(R.id.settingsButton)?.visibility = View.VISIBLE
                // Reset scale
                rootView.scaleX = 1.0f
                rootView.scaleY = 1.0f
                // Restore previous or default size
                config.layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                config.layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                // Restore a default position (top-right)
                config.layoutParams.gravity = Gravity.TOP or Gravity.END
                val defaultNestMarginPx = (20 * density).toInt()
                config.layoutParams.x = screenWidth - config.layoutParams.width - defaultNestMarginPx
                config.layoutParams.y = defaultNestMarginPx
                windowManager.updateViewLayout(rootView, config.layoutParams)
                // Remove this clock's nested position
                nestedClockPositions.remove(clockId)
                // Re-layout other nested clocks
                repositionNestedClocks()
                // Remove the exit button
                if (rootView is ViewGroup) {
                    rootView.removeView(this)
                }
            }
        }

      // Add the button to the root view
        if (rootView is FrameLayout) {
            val buttonParams = FrameLayout.LayoutParams(buttonSizePx, buttonSizePx).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, 0, buttonMarginEndPx, buttonMarginBottomPx)
            }
            rootView.addView(exitNestButton, buttonParams)
        } else if (rootView is LinearLayout) {
            val buttonParams = LinearLayout.LayoutParams(buttonSizePx, buttonSizePx).apply {
                gravity = Gravity.END
                setMargins(0, 0, buttonMarginEndPx, buttonMarginBottomPx)
            }
            rootView.addView(exitNestButton, buttonParams)
        } else {
            Toast.makeText(this, "Unsupported layout for Exit Nest button.", Toast.LENGTH_SHORT).show()
        }

        // Ensure the clock is draggable even in nested mode
        setupTouchListener(clockId, rootView)
    }

private fun repositionNestedClocks() {
    val displayMetrics = resources.displayMetrics
    val density = displayMetrics.density
    val screenWidth = displayMetrics.widthPixels
    val nestedMarginPx = (10f * density).toInt()
    val nestedWidthPx = (60f * density).toInt()
    val scale = 0.3f
    val scaledHeight = (nestedWidthPx * scale).toInt()

    // Get the list of currently nested clocks, sorted by their nested position
    val currentlyNested = activeClocks
        .filter { clockPair -> clockSettings[clockPair.first]?.isNested == true }
        .sortedBy { nestedClockPositions[it.first] ?: Int.MAX_VALUE }

    // Clear and re-populate the nested positions based on the current order
    nestedClockPositions.clear()
    currentlyNested.forEachIndexed { index, (id, rootView) ->
        nestedClockPositions[id] = index
        val config = clockSettings[id] ?: return@forEachIndexed
        val nestedY = nestedMarginPx + (index * (scaledHeight * 1.1f))

        config.layoutParams.gravity = Gravity.TOP or Gravity.END
        config.layoutParams.x = screenWidth - (nestedWidthPx * scale).toInt() - nestedMarginPx
        config.layoutParams.y = nestedY
        windowManager.updateViewLayout(rootView, config.layoutParams)
    }
}
}
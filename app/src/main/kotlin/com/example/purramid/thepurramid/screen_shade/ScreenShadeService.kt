// ScreenShadeService.kt
package com.example.purramid.thepurramid.screen_shade

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.purramid.thepurramid.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ScreenShadeService : Service() {

    // Inject dependencies
    @Inject lateinit var windowManager: WindowManager
    @Inject lateinit var sharedPreferences: SharedPreferences // Assuming you have a Hilt module providing SharedPreferences("screen_shade_state", Context.MODE_PRIVATE)

    private val activeMasks = mutableListOf<MaskView>()
    private var floatingMenuView: LinearLayout? = null
    private var lastTouchedMask: MaskView? = null
    private var addButtonView: ImageView? = null
    private var isAllLocked = false
    private var imageChooserPendingIntent: PendingIntent? = null

    companion object {
        private const val CHANNEL_ID = "ScreenShadeServiceChannel"
        const val ACTION_START = "com.example.thepurramid0_1.ACTION_START_SHADE_SERVICE"
        const val ACTION_STOP = "com.example.thepurramid0_1.ACTION_STOP_SHADE_SERVICE"
        const val ACTION_ADD_MASK = "com.example.thepurramid0_1.ACTION_ADD_MASK"
        const val ACTION_LOCK_MASK = "com.example.thepurramid0_1.ACTION_LOCK_MASK"
        const val ACTION_LOCK_ALL = "com.example.thepurramid0_1.ACTION_LOCK_ALL"
        private const val BILLBOARD_REQUEST_CODE = 020219
        const val ACTION_LAUNCH_IMAGE_CHOOSER = "com.example.thepurramid0_1.ACTION_LAUNCH_IMAGE_CHOOSER"
        const val EXTRA_PENDING_INTENT = "com.example.thepurramid0_1.EXTRA_PENDING_INTENT"
        const val ACTION_IMAGE_SELECTED = "com.example.thepurramid0_1.ACTION_IMAGE_SELECTED"
        const val EXTRA_IMAGE_URI = "com.example.thepurramid0_1.EXTRA_IMAGE_URI"
        private var billboardTargetMask: MaskView? = null
        private const val MAX_MASKS = 4
    }

    init {
        setBackgroundColor(Color.BLACK)
        setOnTouchListener(onTouchListener)
        yellowBorder = GradientDrawable().apply {
            setStroke(dpToPx(3), Color.YELLOW)
            setColor(Color.TRANSPARENT)
        }
        // Initialize the ImageView for the billboard
        billboardImageView = ImageView(serviceContext).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER // To preserve aspect ratio
            visibility = View.GONE
        }
        addView(billboardImageView)
        setupCloseButton() // Initialize the close button
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupFloatingMenu()
        createImageChooserPendingIntent()
        restoreMaskState() // Restore saved mask state
        if (activeMasks.isEmpty()) {
            addMask(isFullScreen = true) // Add an initial full-screen mask if no saved state
        }
    }

    private fun createImageChooserPendingIntent() {
        val imageIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "image/bmp", "image/gif", "image/webp"))
        }
        imageChooserPendingIntent = PendingIntent.getActivity(
            this,
            BILLBOARD_REQUEST_CODE,
            imageIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundService()
                if (activeMasks.isEmpty()) {
                    addMask() // Add an initial full-screen mask
                }
            }
            ACTION_STOP -> {
                removeAllMasks()
                removeFloatingMenu()
                stopForeground(true)
                stopSelf()
            }
            ACTION_ADD_MASK -> {
                if (activeMasks.size < MAX_MASKS) {
                    addMask()
                }
            }
            ACTION_LOCK_MASK -> {
                lastTouchedMask?.apply {
                    isLocked = !isLocked
                    updateBorderVisibility()
                }
            }
            ACTION_LOCK_ALL -> {
                isAllLocked = !isAllLocked
                activeMasks.forEach { it.isLocked = isAllLocked }
                activeMasks.forEach { it.updateBorderVisibility() }
            }
            ACTION_BILLBOARD -> {
                lastTouchedMask?.let {
                    Log.d("ScreenShadeService", "Requesting image selection for mask: $it, PendingIntent: $imageChooserPendingIntent")
                    val broadcastIntent = Intent(ACTION_LAUNCH_IMAGE_CHOOSER)
                    broadcastIntent.putExtra(EXTRA_PENDING_INTENT, imageChooserPendingIntent)
                    sendBroadcast(broadcastIntent)
                    billboardTargetMask = it
                }
            }
        }
        return START_STICKY
    }

    // Method to handle the received image URI
    fun handleImageSelected(uri: android.net.Uri?) {
        billboardTargetMask?.setBillboardImage(uri)
        billboardTargetMask = null // Clear the target mask
    }
    
    override fun onDestroy() {
        super.onDestroy()
        saveMaskState() // Save the current mask state
        removeAllMasks()
        removeFloatingMenu()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun saveMaskState() {
        val editor = sharedPreferences.edit()
        editor.putInt("mask_count", activeMasks.size)
        activeMasks.forEachIndexed { index, mask ->
            editor.putInt("mask_${index}_x", mask.params?.x ?: 0)
            editor.putInt("mask_${index}_y", mask.params?.y ?: 0)
            editor.putInt("mask_${index}_width", mask.width)
            editor.putInt("mask_${index}_height", mask.height)
            editor.putBoolean("mask_${index}_locked", mask.isLocked)
            mask.currentImageUri?.let { uri ->
                editor.putString("mask_${index}_billboard_uri", uri.toString())
            } ?: editor.remove("mask_${index}_billboard_uri")
        }
        editor.apply()
    }

    private fun restoreMaskState() {
        val maskCount = sharedPreferences.getInt("mask_count", 0)
        for (i in 0 until maskCount) {
            val x = sharedPreferences.getInt("mask_${i}_x", 0)
            val y = sharedPreferences.getInt("mask_${i}_y", 0)
            val width = sharedPreferences.getInt("mask_${i}_width", resources.displayMetrics.widthPixels)
            val height = sharedPreferences.getInt("mask_${i}_height", resources.displayMetrics.heightPixels)
            val locked = sharedPreferences.getBoolean("mask_${i}_locked", false)
            val billboardUriString = sharedPreferences.getString("mask_${i}_billboard_uri", null)
            val billboardUri = billboardUriString?.let { android.net.Uri.parse(it) }

            val newMask = MaskView(this).apply {
                isLocked = locked
                setBillboardImage(billboardUri)
            }
            activeMasks.add(newMask)
            newMask.show(x, y, width, height)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Screen Mask Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, ScreenShadeActivity::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Mask Active")
            .setContentText("Tap to interact with masks.")
            .setSmallIcon(R.drawable.ic_shade) // Use your shade icon
            // .setContentIntent(pendingIntent) // You can add a PendingIntent to open the settings
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(1, notification)
    }

    private fun setupFloatingMenu() {
        floatingMenuView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#80000000")) // Semi-transparent dark background
            // Add buttons with icons and click listeners
            addButton(R.drawable.ic_add_circle, R.string.add_another_screen) {
                if (activeMasks.size < MAX_MASKS) {
                    addMask()
                }
            }
            addButton(R.drawable.ic_lock, R.string.lock_unlock) {
                lastTouchedMask?.apply {
                    isLocked = !isLocked
                    updateBorderVisibility()
                }
            }
            addButton(R.drawable.ic_lock_all, R.string.lock_unlock_all) {
                isAllLocked = !isAllLocked
                activeMasks.forEach { it.isLocked = isAllLocked }
                activeMasks.forEach { it.updateBorderVisibility() }
            }
            addButton(R.drawable.ic_billboard, R.string.billboard) {
                lastTouchedMask?.let {
                    // TODO: Launch billboard activity, pass a reference or ID of the mask
                    Log.d("ScreenShadeService", "Billboard button clicked for mask: $it")
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // So it doesn't take focus
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = resources.displayMetrics.heightPixels / 10 // Position near the top
            }
            try { windowManager.addView(this, params) } catch (e: Exception) {
                Log.e("ScreenShadeService", "Error setting up floating menu view", e)
            }
        }
    }

    private fun removeFloatingMenu() {
        floatingMenuView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {
                Log.e("ScreenShadeService", "Error removing floating menu view", e)
            }
            floatingMenuView = null
        }
    }

    private fun LinearLayout.addButton(iconResId: Int, onClick: () -> Unit) {
        val button = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, iconResId))
            setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
            setOnClickListener { onClick() }
            setOnLongClickListener {
                Toast.makeText(context, getString(nameResId), Toast.LENGTH_SHORT).show()
                true // Consume the long-press event
                }
        }
        val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(button, layoutParams)
        return button // Return the ImageView so we can hold a reference if needed
    }

    private fun addMask(creatorMask: MaskView? = null) {
        if (activeMasks.size >= MAX_MASKS) {
            return
        }

        val newMask = MaskView(this)
        activeMasks.add(newMask)

        val initialWidth = creatorMask?.width ?: resources.displayMetrics.widthPixels
        val initialHeight = creatorMask?.height ?: resources.displayMetrics.heightPixels

        val initialX = if (creatorMask != null) {
            resources.displayMetrics.widthPixels / 2 - initialWidth / 2
        } else 0
        val initialY = if (creatorMask != null) {
            resources.displayMetrics.heightPixels / 2 - initialHeight / 2
        } else 0

        val offset = activeMasks.size * 20 // Simple offset for new masks
        newMask.show(initialX + offset, initialY + offset, initialWidth, initialHeight)

        if (activeMasks.size == MAX_MASKS) {
            updateAddButtonState() // Disable the "add new mask" button
            // Log.d("ScreenShadeService", "Max masks reached")
        }
    }

    private fun removeMask(maskToRemove: MaskView) {
        if (activeMasks.contains(maskToRemove)) {
            activeMasks.remove(maskToRemove)
            maskToRemove.hide()
            updateAddButtonState() // Update the "add" button state
        }
    }

    private fun removeAllMasks() {
        activeMasks.forEach { it.hide() }
        activeMasks.clear()
    }

    private fun updateAddButtonState() {
        addButtonView?.isEnabled = activeMasks.size < MAX_MASKS
        addButtonView?.alpha = if (addButtonView?.isEnabled == true) 1.0f else 0.5f
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // Inner class to represent a single screen mask
    inner class MaskView(val serviceContext: Context) : View(serviceContext) {
        private var params: WindowManager.LayoutParams? = null
        private var currentX = 0
        private var currentY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        var isLocked = false
        private val resizeHandleSize = dpToPx(20)
        private var currentlyResizing = false
        private var resizeDirection: ResizeDirection? = null
        private var yellowBorder: GradientDrawable? = null
        private var borderFadeAnimator: android.animation.ObjectAnimator? = null
        private var billboardImageView: ImageView? = null
        private var currentImageUri: android.net.Uri? = null
        private val paddingFraction = 0.05f
        private var closeButton: ImageView? = null
        
        init {
            setBackgroundColor(Color.BLACK)
            setOnTouchListener(onTouchListener)

            // Initialize the yellow border
            yellowBorder = GradientDrawable().apply {
                setStroke(dpToPx(3), Color.YELLOW)
                setColor(Color.TRANSPARENT)
            }

            // Initialize the ImageView for the billboard
            billboardImageView = ImageView(serviceContext).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER // To preserve aspect ratio
                visibility = View.GONE
            }
            addView(billboardImageView)

            // Initialize the close button
            setupCloseButton()
        }

        enum class ResizeDirection {
            LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE
        }

        override fun onDraw(canvas: android.graphics.Canvas?) {
            super.onDraw(canvas)
            if (isLocked) {
                background = yellowBorder
            } else {
                background = GradientDrawable().apply { setColor(Color.BLACK) }
            }
        }

        fun updateBorderVisibility() {
            if (isLocked) {
                // Start fade-in then fade-out animation
                yellowBorder?.alpha = 255
                borderFadeAnimator?.cancel()
                borderFadeAnimator = android.animation.ObjectAnimator.ofInt(yellowBorder, "alpha", 255, 0).apply {
                    duration = 1000 // Fade out over 1 second (0.5s delay + 1s fade)
                    startDelay = 500
                    addUpdateListener { invalidate() }
                    start()
                }
                invalidate() // Redraw to show initial border
            } else {
                borderFadeAnimator?.cancel()
                background = GradientDrawable().apply { setColor(Color.BLACK) }
                invalidate()
            }
        }

    private fun setupCloseButton() {
        closeButton = ImageView(serviceContext).apply {
            setImageDrawable(ContextCompat.getDrawable(serviceContext, R.drawable.ic_close)) // Use your close icon
            setPadding(dpToPx(5), dpToPx(5), dpToPx(5), dpToPx(5))
            visibility = View.VISIBLE // Initially visible
            setOnClickListener {
                if (isLocked) {
                    Toast.makeText(serviceContext, "Unlock this screen shade to close.", Toast.LENGTH_SHORT).show()
                } else {
                    (serviceContext as? ScreenShadeService)?.removeMaskView(this@MaskView)
                }
            }
        }
        val closeButtonSize = dpToPx(24)
        val layoutParams = LayoutParams(closeButtonSize, closeButtonSize).apply {
            gravity = Gravity.TOP or Gravity.END // Position in the top-right
            marginEnd = dpToPx(8) // Inset from the right
            topMargin = dpToPx(8) // Inset from the top
        }
        addView(closeButton, layoutParams)
    }

        private val onTouchListener = OnTouchListener { v, event ->
            lastTouchedMask = this // Update the last touched mask

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    currentX = params?.x ?: 0
                    currentY = params?.y ?: 0
                    currentlyResizing = false
                    resizeDirection = getResizeDirection(event.x.toInt(), event.y.toInt())

                    if (resizeDirection != ResizeDirection.MOVE) {
                        currentlyResizing = true
                    }

                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isLocked) return@OnTouchListener true // Don't move or resize if locked

                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    if (currentlyResizing && resizeDirection != null) {
                        updateSizeAndPosition(deltaX, deltaY, resizeDirection!!)
                    } else if (resizeDirection == ResizeDirection.MOVE) {
                        params?.x = currentX + deltaX.toInt()
                        params?.y = currentY + deltaY.toInt()
                        updateViewLayout()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    currentlyResizing = false
                    resizeDirection = null
                    return true
                }
                else -> false
            }
        }

        private fun getResizeDirection(x: Int, y: Int): ResizeDirection {
            val left = 0
            val top = 0
            val right = width
            val bottom = height

            val touchSlop = dpToPx(10) // Sensitivity for touch

            return when {
                x < left + touchSlop && y < top + touchSlop -> ResizeDirection.TOP_LEFT
                x > right - touchSlop && y < top + touchSlop -> ResizeDirection.TOP_RIGHT
                x < left + touchSlop && y > bottom - touchSlop -> ResizeDirection.BOTTOM_LEFT
                x > right - touchSlop && y > bottom - touchSlop -> ResizeDirection.BOTTOM_RIGHT
                x < left + touchSlop -> ResizeDirection.LEFT
                x > right - touchSlop -> ResizeDirection.RIGHT
                y < top + touchSlop -> ResizeDirection.TOP
                y > bottom - touchSlop -> ResizeDirection.BOTTOM
                else -> ResizeDirection.MOVE
            }
        }

        private fun updateSizeAndPosition(deltaX: Float, deltaY: Float, direction: ResizeDirection) {
            params?.apply {
                when (direction) {
                    ResizeDirection.LEFT -> {
                        width -= deltaX.toInt()
                        x += deltaX.toInt()
                    }
                    ResizeDirection.TOP -> {
                        height -= deltaY.toInt()
                        y += deltaY.toInt()
                    }
                    ResizeDirection.RIGHT -> {
                        width += deltaX.toInt()
                    }
                    ResizeDirection.BOTTOM -> {
                        height += deltaY.toInt()
                    }
                    ResizeDirection.TOP_LEFT -> {
                        width -= deltaX.toInt()
                        x += deltaX.toInt()
                        height -= deltaY.toInt()
                        y += deltaY.toInt()
                    }
                    ResizeDirection.TOP_RIGHT -> {
                        width += deltaX.toInt()
                        height -= deltaY.toInt()
                        y += deltaY.toInt()
                    }
                    ResizeDirection.BOTTOM_LEFT -> {
                        width -= deltaX.toInt()
                        x += deltaX.toInt()
                        height += deltaY.toInt()
                    }
                    ResizeDirection.BOTTOM_RIGHT -> {
                        width += deltaX.toInt()
                        height += deltaY.toInt()
                    }
                    else -> return // Should not happen
                }
                // Ensure minimum size
                if (width < dpToPx(50)) width = dpToPx(50)
                if (height < dpToPx(50)) height = dpToPx(50)
            }
            updateViewLayout()
        }

        fun show(initialX: Int, initialY: Int, initialWidth: Int, initialHeight: Int) {
            params = WindowManager.LayoutParams(
                initialWidth,
                initialHeight,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = initialX
                y = initialY
            }
            try { windowManager.addView(this, params) } catch (e: Exception) {
                Log.e("ScreenShadeService", "Error showing view", e)
            }
        }

        fun hide() {
            try {
                // Check if view is attached before trying to remove
                if (this.isAttachedToWindow) {
                    windowManager.removeView(this)
                } else {
                    Log.w("MaskView", "Attempted to remove view that was not attached.")
                }
            } catch (e: Exception) { // Catch specific exceptions like IllegalArgumentException if preferred
                // Log the error with a tag and the exception details
                Log.e("MaskView", "Error removing mask view", e)
            }
        }

        private fun updateViewLayout() {
            try {
                params?.let { windowManager.updateViewLayout(this, it) }
            } catch (e: Exception) {
                Log.e("MaskView", "Error updating view layout", e)
            }
        }
        fun setBillboardImage(uri: android.net.Uri?) {
            currentImageUri = uri
            if (uri != null) {
                billboardImageView?.visibility = View.VISIBLE
                // Load the image using a library that handles various formats including GIF
                Glide.with(serviceContext)
                    .load(uri)
                    .into(billboardImageView!!)
                applyImagePadding()
            } else {
                billboardImageView?.visibility = View.GONE
            }
        }

        private fun applyImagePadding() {
            billboardImageView?.layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            ).apply {
                val padding = (width * paddingFraction).toInt()
                setMargins(padding, padding, padding, padding)
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (currentImageUri != null && billboardImageView?.visibility == View.VISIBLE) {
                applyImagePadding()
            }
        }
    }
}
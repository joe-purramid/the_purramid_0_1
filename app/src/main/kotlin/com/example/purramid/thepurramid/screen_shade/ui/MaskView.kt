// MaskView.kt
package com.example.purramid.thepurramid.screen_shade.ui

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector // For pinch-to-resize
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast // For placeholder messages
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.screen_shade.ScreenShadeState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val instanceId: Int // Each MaskView needs to know its ID
) : FrameLayout(context, attrs, defStyleAttr) {

    interface InteractionListener {
        fun onMaskMoved(instanceId: Int, x: Int, y: Int)
        fun onMaskResized(instanceId: Int, width: Int, height: Int)
        fun onLockToggled(instanceId: Int)
        fun onCloseRequested(instanceId: Int)
        fun onBillboardTapped(instanceId: Int) // To request image change
        fun onColorChangeRequested(instanceId: Int) // Placeholder for color picker
        fun onControlsToggled(instanceId: Int) // To toggle controls visibility
    }

    var interactionListener: InteractionListener? = null
    private lateinit var currentState: ScreenShadeState

    private var billboardImageView: ImageView
    private var closeButton: ImageView
    // Add other control buttons if they are part of the mask itself (e.g., lock, color)

    private var yellowBorder: GradientDrawable
    private var borderFadeAnimator: ObjectAnimator? = null

    // Touch handling for move and resize
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var initialMaskX: Int = 0
    private var initialMaskY: Int = 0
    private var initialMaskWidth: Int = 0
    private var initialMaskHeight: Int = 0
    private var isMoving = false
    private var isResizing = false // More sophisticated resize later
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private enum class ResizeDirection { NONE, LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private var currentResizeDirection = ResizeDirection.NONE
    private val resizeHandleSize = dpToPx(24) // Increased for easier touch

    // Scale Gesture Detector for pinch-to-resize
    private var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1f


    init {
        // Set initial background (will be updated by state)
        setBackgroundColor(Color.BLACK) // Or any other opaque default you prefer

        // Billboard ImageView
        billboardImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE // Initially hidden
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                val padding = (width * 0.05f).toInt() // Example padding
                setMargins(padding, padding, padding, padding)
            }
            setOnClickListener {
                if (!currentState.isLocked) { // Only allow changing image if not locked
                    interactionListener?.onBillboardTapped(instanceId)
                }
            }
        }
        addView(billboardImageView)

        // Close Button
        closeButton = ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_close))
            val buttonSize = dpToPx(32)
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            layoutParams = LayoutParams(buttonSize, buttonSize).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            setOnClickListener {
                if (!currentState.isLocked) {
                    interactionListener?.onCloseRequested(instanceId)
                } else {
                    // Consider Snackbar if Activity context is available, or log for now
                    Toast.makeText(context, "Unlock mask to close", Toast.LENGTH_SHORT).show()
                }
            }
        }
        addView(closeButton)

        // Yellow border for locked state
        yellowBorder = GradientDrawable().apply {
            setStroke(dpToPx(3), Color.YELLOW)
            setColor(Color.TRANSPARENT) // Transparent fill
        }

        // Initialize with a default state (will be overwritten)
        this.currentState = ScreenShadeState(instanceId = instanceId)

        // Setup scale gesture detector
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())

        // Set up touch listener on the MaskView itself for moving and resizing
        setupMaskTouchListener()
    }

    fun updateState(newState: ScreenShadeState) {
        this.currentState = newState

        // Update border visibility based on lock state
        if (newState.isLocked) {
            // Start fade-in then fade-out animation
            foreground = yellowBorder // Set border as foreground to overlay content
            yellowBorder.alpha = 255
            borderFadeAnimator?.cancel()
            borderFadeAnimator = ObjectAnimator.ofInt(yellowBorder, "alpha", 255, 0).apply {
                duration = 1000 // Fade out over 1 second
                startDelay = 500 // Wait 0.5s before starting fade
                addUpdateListener { invalidate() } // Invalidate to redraw border alpha
                start()
            }
        } else {
            borderFadeAnimator?.cancel()
            foreground = null // Remove border
        }
        invalidate() // Redraw view
        // Show/hide controls based on state
        closeButton.visibility = if (newState.isControlsVisible) View.VISIBLE else View.GONE
        // Add other control button visibility updates here
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun setupMaskTouchListener() {
        this.setOnTouchListener { _, event ->
            if (currentState.isLocked) return@setOnTouchListener true // Consume touch if locked

            scaleGestureDetector.onTouchEvent(event) // Pass to scale detector first

            val action = event.actionMasked
            val currentX = event.x
            val currentY = event.y

            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX // Use raw for window-level coordinates
                    initialTouchY = event.rawY
                    initialMaskX = currentState.x // Current position from state
                    initialMaskY = currentState.y
                    initialMaskWidth = currentState.width
                    initialMaskHeight = currentState.height

                    isMoving = false
                    isResizing = false
                    currentResizeDirection = getResizeDirection(currentX, currentY)

                    if (currentResizeDirection != ResizeDirection.NONE && currentResizeDirection != ResizeDirection.MOVE) {
                        isResizing = true
                        // Store initial dimensions for relative resizing
                    }
                    // Consume event if we might resize or move
                    return@setOnTouchListener currentResizeDirection != ResizeDirection.NONE
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isResizing && currentResizeDirection != ResizeDirection.NONE && currentResizeDirection != ResizeDirection.MOVE) {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        handleResize(deltaX, deltaY)
                        return@setOnTouchListener true
                    } else if (!isResizing && (currentResizeDirection == ResizeDirection.MOVE || // Explicit move intent
                               (abs(event.rawX - initialTouchX) > touchSlop || abs(event.rawY - initialTouchY) > touchSlop))) { // Slop exceeded
                        if (!isMoving) { // Start moving
                            isMoving = true
                            // Recalculate initialTouch based on current event to avoid jump if slop was just exceeded
                            initialTouchX = event.rawX - (currentState.x - initialMaskX)
                            initialTouchY = event.rawY - (currentState.y - initialMaskY)
                        }
                        val newX = initialMaskX + (event.rawX - initialTouchX).toInt()
                        val newY = initialMaskY + (event.rawY - initialTouchY).toInt()
                        // For visual feedback during move:
                        // Directly tell service to update WindowManager params
                        interactionListener?.onMaskMoved(instanceId, newX, newY)
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isResizing) {
                        // Report final size
                        interactionListener?.onMaskResized(instanceId, currentState.width, currentState.height)
                    } else if (isMoving) {
                        // Report final position
                        val finalX = initialMaskX + (event.rawX - initialTouchX).toInt()
                        val finalY = initialMaskY + (event.rawY - initialTouchY).toInt()
                        interactionListener?.onMaskMoved
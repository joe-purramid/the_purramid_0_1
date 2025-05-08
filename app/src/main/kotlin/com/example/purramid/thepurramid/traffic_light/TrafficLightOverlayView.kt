// TrafficLightOverlayView.kt
package com.example.purramid.thepurramid.traffic_light

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.animation.LinearInterpolator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.TrafficLightOverlayViewBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.LightColor
import com.example.purramid.thepurramid.traffic_light.viewmodel.Orientation
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightState
import kotlin.math.abs
import kotlin.math.max // Import max
import kotlin.math.min // Import min
import kotlin.math.sqrt

class TrafficLightOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) { // Use FrameLayout or ConstraintLayout as base

    private val binding: TrafficLightOverlayViewBinding
    var interactionListener: InteractionListener? = null

    // --- Touch Handling Variables ---
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isMoving = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val viewBoundsRect = Rect()
    private var scaleFactor = 1f
    private var isResizing = false
    private var minSizePx = resources.getDimensionPixelSize(R.dimen.traffic_light_min_size)
    private var maxSizePx = resources.displayMetrics.widthPixels

    // --- Blinking Variables ---
    private var blinkingAnimator: ObjectAnimator? = null
    private var currentlyBlinkingView: View? = null
    private val blinkDuration = 750L // milliseconds for one blink cycle (on -> off -> on)

    interface InteractionListener {
        fun onLightTapped(color: LightColor)
        fun onCloseRequested()
        fun onSettingsRequested()
        fun onMove(rawDeltaX: Float, rawDeltaY: Float)
        fun onMoveFinished()
        fun onResize(newWidth: Int, newHeight: Int)
        fun onResizeFinished(finalWidth: Int, finalHeight: Int)
    }

    init {
        binding = TrafficLightOverlayViewBinding.inflate(LayoutInflater.from(context), this, true)
        setupInternalListeners()
        setupScaleDetector()
        setupTouchListener() // Setup touch listener for the overlay view itself
        if (minSizePx <= 0) minSizePx = 100 // Fallback min size
    }

    private fun setupInternalListeners() {
        // --- Vertical Lights ---
        binding.lightRedVerticalOverlay.setOnClickListener { interactionListener?.onLightTapped(LightColor.RED) }
        binding.lightYellowVerticalOverlay.setOnClickListener { interactionListener?.onLightTapped(LightColor.YELLOW) }
        binding.lightGreenVerticalOverlay.setOnClickListener { interactionListener?.onLightTapped(LightColor.GREEN) }

        // --- Horizontal Lights ---
        binding.lightRedHorizontalOverlay.setOnClickListener { interactionListener?.onLightTapped(LightColor.RED) }
        binding.lightYellowHorizontalOverlay.setOnClickListener { interactionListener?.onLightTapped(LightColor.YELLOW) }
        binding.lightGreenHorizontalOverlay.setOnClickListener { interactionListener?.onLightTapped(LightColor.GREEN) }

        // --- Buttons ---
        binding.overlayButtonClose.setOnClickListener { interactionListener?.onCloseRequested() }
        binding.overlayButtonSettings.setOnClickListener { interactionListener?.onSettingsRequested() }
    }

    fun updateState(state: TrafficLightState) {
        val isVertical = state.orientation == Orientation.VERTICAL
        binding.trafficLightVerticalContainerOverlay.isVisible = isVertical
        binding.trafficLightHorizontalContainerOverlay.isVisible = !isVertical

        val activeColor = state.activeLight
        val blinkEnabled = state.isBlinkingEnabled

        // Get the views for the current orientation
        val redView = if (isVertical) binding.lightRedVerticalOverlay else binding.lightRedHorizontalOverlay
        val yellowView = if (isVertical) binding.lightYellowVerticalOverlay else binding.lightYellowHorizontalOverlay
        val greenView = if (isVertical) binding.lightGreenVerticalOverlay else binding.lightGreenHorizontalOverlay

        // Update activation state
        redView.isActivated = activeColor == LightColor.RED
        yellowView.isActivated = activeColor == LightColor.YELLOW
        greenView.isActivated = activeColor == LightColor.GREEN

        // Determine which view should blink (if any)
        val viewToBlink: View? = when (activeColor) {
            LightColor.RED -> redView
            LightColor.YELLOW -> yellowView
            LightColor.GREEN -> greenView
            null -> null
        }

        // Stop previous blinking if the blinking view changes or blinking is disabled
        if (currentlyBlinkingView != null && (currentlyBlinkingView != viewToBlink || !blinkEnabled)) {
            stopBlinking(currentlyBlinkingView!!)
        }

        // Stop blinking on newly inactive views and reset alpha
        if (activeColor != LightColor.RED) stopBlinking(redView); redView.alpha = 1f
        if (activeColor != LightColor.YELLOW) stopBlinking(yellowView); yellowView.alpha = 1f
        if (activeColor != LightColor.GREEN) stopBlinking(greenView); greenView.alpha = 1f

        // Start new blinking if needed
        if (blinkEnabled && viewToBlink != null && currentlyBlinkingView != viewToBlink) {
            startBlinking(viewToBlink)
        } else if (!blinkEnabled && viewToBlink != null) {
            // Ensure alpha is reset if blinking is turned off while light is active
            viewToBlink.alpha = 1f
        }
    }

    // --- Setup Scale Detector ---
    private fun setupScaleDetector() {
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    }

    // --- Blinking Logic ---
    private fun startBlinking(view: View) {
        stopBlinking(view) // Ensure any previous animation on this view is stopped

        // Simple blink: Fade out and back in repeatedly
        blinkingAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.3f, 1f).apply {
            duration = blinkDuration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART // Restart animation from beginning
            interpolator = LinearInterpolator() // Constant speed fade
        }
        currentlyBlinkingView = view
        blinkingAnimator?.start()
        Log.d("OverlayView", "Started blinking for: ${view.id}")
    }

    private fun stopBlinking(view: View) {
        if (currentlyBlinkingView == view && blinkingAnimator != null) {
            blinkingAnimator?.cancel()
            blinkingAnimator = null
            view.alpha = 1f // Reset alpha to fully visible
            currentlyBlinkingView = null
            Log.d("OverlayView", "Stopped blinking for: ${view.id}")
        } else if (view.alpha != 1f) {
            // Ensure alpha is reset even if it wasn't the 'currentlyBlinkingView' tracked
            view.animate().cancel() // Cancel potential other animations
            view.alpha = 1f
        }
    }

    // --- Touch Handling for Movement ---
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        this.setOnTouchListener { _, event ->
            // Check if touch is on an interactive element first
            if (isTouchOnInteractiveElement(event)) {
                // If ACTION_DOWN is on button/light, let it proceed but don't start move/scale
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    isMoving = false
                    isResizing = false // Reset flags
                }
                return@setOnTouchListener false // Let the child handle its click
            }

            // Let ScaleGestureDetector inspect the event first
            val scaleConsumed = scaleGestureDetector.onTouchEvent(event)

            var moveConsumed = false
            // If not resizing, handle potential move gesture
            if (!isResizing) {
                moveConsumed = handleMoveGesture(event)
            }

            // Reset isResizing flag on ACTION_UP/CANCEL, managed by ScaleListener's onScaleEnd
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (isMoving) {
                    interactionListener?.onMoveFinished()
                    isMoving = false
                }
                // isResizing is reset in ScaleListener.onScaleEnd
            }

            // Consume the event if it was handled by scale or move, or if it's ACTION_DOWN
            scaleConsumed || moveConsumed || event.actionMasked == MotionEvent.ACTION_DOWN
        }
    }

    private fun handleMoveGesture(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Record initial touch point for potential move
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isMoving = false
                return true // Indicate interest in subsequent events
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) {
                    // If more pointers go down during a potential move, stop the move check
                    isMoving = false
                    return false
                }

                val currentX = event.rawX
                val currentY = event.rawY
                val deltaX = currentX - initialTouchX
                val deltaY = currentY - initialTouchY

                if (!isMoving) {
                    if (sqrt(deltaX * deltaX + deltaY * deltaY) > touchSlop) {
                        isMoving = true
                    }
                }

                if (isMoving) {
                    interactionListener?.onMove(deltaX, deltaY)
                    // Update initial touch points for the next delta
                    initialTouchX = currentX
                    initialTouchY = currentY
                    return true // Consumed event as move
                }
            }
            // UP/CANCEL handled in the main onTouchListener
        }
        return false // Event not consumed by move logic here
    }

    // Helper to check if touch is within the bounds of any interactive child
    private fun isTouchOnInteractiveElement(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()

        // Check buttons first as they are smaller targets
        if (isTouchInsideTarget(x, y, binding.overlayButtonClose)) return true
        if (isTouchInsideTarget(x, y, binding.overlayButtonSettings)) return true

        // Check currently visible light container
        if (binding.trafficLightVerticalContainerOverlay.isVisible) {
            if (isTouchInsideTarget(x, y, binding.lightRedVerticalOverlay)) return true
            if (isTouchInsideTarget(x, y, binding.lightYellowVerticalOverlay)) return true
            if (isTouchInsideTarget(x, y, binding.lightGreenVerticalOverlay)) return true
        } else if (binding.trafficLightHorizontalContainerOverlay.isVisible) {
            if (isTouchInsideTarget(x, y, binding.lightRedHorizontalOverlay)) return true
            if (isTouchInsideTarget(x, y, binding.lightYellowHorizontalOverlay)) return true
            if (isTouchInsideTarget(x, y, binding.lightGreenHorizontalOverlay)) return true
        }
        return false
    }

    private fun isTouchInsideTarget(x: Int, y: Int, target: View): Boolean {
        target.getHitRect(viewBoundsRect) // Get bounds relative to this view's parent
        return target.isVisible && viewBoundsRect.contains(x, y)
    }

    // --- Scale Gesture Listener Implementation ---
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var currentWidth: Int = 0
        private var currentHeight: Int = 0

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            // Ignore scale if moving
            if (isMoving) return false

            isResizing = true
            currentWidth = width // Get current view width (which should match layoutParams.width)
            currentHeight = height // Get current view height
            scaleFactor = 1.0f // Reset scale factor
            Log.d("OverlayView", "onScaleBegin - Start Size: ${currentWidth}x$currentHeight")
            return true // We want to handle scaling
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!isResizing) return false

            scaleFactor *= detector.scaleFactor // Accumulate scale factor is WRONG, use detector's factor directly

            // Calculate new size based on the detector's scale factor relative to the *start* of the gesture
            // A simpler approach is to scale the *current* size by the *incremental* factor
            var newWidth = (currentWidth * detector.scaleFactor).toInt()
            var newHeight = (currentHeight * detector.scaleFactor).toInt()


            // Apply constraints
            newWidth = max(minSizePx, newWidth) // Apply min size
            newHeight = max(minSizePx, newHeight)
            // Apply max size if needed:
            // newWidth = min(newWidth, maxSizePx)
            // newHeight = min(newHeight, maxSizePx)


            // Notify the listener (Service) to update layout params
            interactionListener?.onResize(newWidth, newHeight)
            // Update current size for next incremental calculation
            currentWidth = newWidth
            currentHeight = newHeight

            Log.d("OverlayView", "onScale - Factor: ${detector.scaleFactor}, New Size: ${newWidth}x$newHeight")

            return true // Scale event handled
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            Log.d("OverlayView", "onScaleEnd - Final Size: ${currentWidth}x$currentHeight")
            isResizing = false
            interactionListener?.onResizeFinished(currentWidth, currentHeight)
            // Reset temporary variables if necessary
            scaleFactor = 1.0f
        }
    }

    // --- Cleanup ---
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Stop animator when view is detached
        blinkingAnimator?.cancel()
        blinkingAnimator = null
        currentlyBlinkingView = null
    }
}
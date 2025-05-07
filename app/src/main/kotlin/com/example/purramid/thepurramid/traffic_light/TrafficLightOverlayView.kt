// TrafficLightOverlayView.kt
package com.example.purramid.thepurramid.traffic_light

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
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

    // Rect for hit testing buttons
    private val viewBoundsRect = Rect()

    interface InteractionListener {
        fun onLightTapped(color: LightColor)
        fun onCloseRequested()
        fun onSettingsRequested()
        fun onMove(rawDeltaX: Float, rawDeltaY: Float)
        fun onMoveFinished()
        // Add onResize methods if needed
    }

    init {
        binding = TrafficLightOverlayViewBinding.inflate(LayoutInflater.from(context), this, true)
        setupInternalListeners()
        setupTouchListener() // Setup touch listener for the overlay view itself
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
        binding.trafficLightVerticalContainerOverlay.isVisible = state.orientation == Orientation.VERTICAL
        binding.trafficLightHorizontalContainerOverlay.isVisible = state.orientation == Orientation.HORIZONTAL

        // Activate correct lights based on orientation
        if (state.orientation == Orientation.VERTICAL) {
            binding.lightRedVerticalOverlay.isActivated = state.activeLight == LightColor.RED
            binding.lightYellowVerticalOverlay.isActivated = state.activeLight == LightColor.YELLOW
            binding.lightGreenVerticalOverlay.isActivated = state.activeLight == LightColor.GREEN
        } else {
            binding.lightRedHorizontalOverlay.isActivated = state.activeLight == LightColor.RED
            binding.lightYellowHorizontalOverlay.isActivated = state.activeLight == LightColor.YELLOW
            binding.lightGreenHorizontalOverlay.isActivated = state.activeLight == LightColor.GREEN
        }

        // TODO: Handle blinking if state.isBlinkingEnabled and state.activeLight != null
        // This would involve starting/stopping an animation (e.g., alpha fade loop) on the active light view.
    }

    // --- Touch Handling for Movement ---
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        this.setOnTouchListener { _, event ->
            // Allow touches on buttons to pass through
            if (isTouchOnButton(event)) {
                 return@setOnTouchListener false // Don't consume if touch is on a button
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    // Get initial window position (needs to be passed from Service or stored)
                    // For simplicity, calculate deltas and notify the service
                    isMoving = false
                    true // Consume ACTION_DOWN to receive MOVE/UP
                }
                MotionEvent.ACTION_MOVE -> {
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
                        // Notify listener (Service) about the move delta
                        interactionListener?.onMove(deltaX.toInt(), deltaY.toInt())
                        // Update initial touch points for next delta calculation
                        initialTouchX = currentX
                        initialTouchY = currentY
                    }
                    true // Consume MOVE event
                }
                MotionEvent.ACTION_UP -> {
                    if (isMoving) {
                        interactionListener?.onMoveFinished()
                    }
                    isMoving = false
                    true // Consume UP event
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isMoving) {
                        interactionListener?.onMoveFinished() // Treat cancel like UP for saving position
                    }
                    isMoving = false
                    true // Consume CANCEL event
                }
                else -> false
            }
        }
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

    private fun isTouchOnButton(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()

        // Check bounds of close button
        if (binding.overlayButtonClose.isVisible &&
            x >= binding.overlayButtonClose.left && x <= binding.overlayButtonClose.right &&
            y >= binding.overlayButtonClose.top && y <= binding.overlayButtonClose.bottom) {
            return true
        }
         // Check bounds of settings button
         if (binding.overlayButtonSettings.isVisible &&
             x >= binding.overlayButtonSettings.left && x <= binding.overlayButtonSettings.right &&
             y >= binding.overlayButtonSettings.top && y <= binding.overlayButtonSettings.bottom) {
             return true
         }

        // Check bounds of lights only if they are clickable in the current mode (simplification: check all)
        if (isTouchInsideTarget(x, y, binding.lightRedVerticalOverlay)) return true
        if (isTouchInsideTarget(x, y, binding.lightYellowVerticalOverlay)) return true
        if (isTouchInsideTarget(x, y, binding.lightGreenVerticalOverlay)) return true
        if (isTouchInsideTarget(x, y, binding.lightRedHorizontalOverlay)) return true
        if (isTouchInsideTarget(x, y, binding.lightYellowHorizontalOverlay)) return true
        if (isTouchInsideTarget(x, y, binding.lightGreenHorizontalOverlay)) return true

        return false
    }

    private fun isTouchInsideTarget(x: Int, y: Int, target: View): Boolean {
        return target.isVisible &&
               x >= target.left && x <= target.right &&
               y >= target.top && y <= target.bottom
    }
}
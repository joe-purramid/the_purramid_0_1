// TrafficLightActivity.kt
package com.example.purramid.thepurramid.traffic_light

import android.annotation.SuppressLint
import android.graphics.Color // For highlight example
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.ActivityTrafficLightBinding
// Make sure these viewmodel imports are correct for your file structure
import com.example.purramid.thepurramid.traffic_light.LightColor // Assuming LightColor is in the same package
import com.example.purramid.thepurramid.traffic_light.Orientation // Assuming Orientation is in the same package
import com.example.purramid.thepurramid.traffic_light.TrafficLightState // Assuming TrafficLightState is in the same package
import com.example.purramid.thepurramid.traffic_light.TrafficLightViewModel // Assuming TrafficLightViewModel is in the same package

import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt // Import for sqrt

@AndroidEntryPoint
class TrafficLightActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrafficLightBinding
    private val viewModel: TrafficLightViewModel by viewModels()

    private var instanceId: Int = 0

    // --- Window Movement & Resizing Variables ---
    private lateinit var windowManagerService: WindowManager // Renamed to avoid conflict
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isMoving = false
    private var isResizing = false
    private val touchSlop = 20 // Pixel buffer
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1f


    companion object {
        private val M_INSTANCE_ID_COUNTER = AtomicInteger(0)
        const val EXTRA_INSTANCE_ID = "com.example.purramid.traffic_light.INSTANCE_ID"

        fun getNextInstanceId(): Int {
            return M_INSTANCE_ID_COUNTER.getAndIncrement()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrafficLightBinding.inflate(layoutInflater)
        setContentView(binding.root)

        instanceId = intent.getIntExtra(EXTRA_INSTANCE_ID, getNextInstanceId())
        viewModel.initializeInstance(instanceId)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        windowManagerService = getSystemService(WINDOW_SERVICE) as WindowManager
        layoutParams = window.attributes as WindowManager.LayoutParams

        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())

        setupButtonClickListeners()
        observeViewModel()
        setupTouchListener()
    }

    private fun setupButtonClickListeners() {
        binding.buttonClose.setOnClickListener { finish() }
        binding.buttonSettings.setOnClickListener {
            viewModel.setSettingsOpen(true)
            TrafficLightSettingsFragment.newInstance().show(
                supportFragmentManager, TrafficLightSettingsFragment.TAG
            )
        }

        binding.lightRedVertical.setOnClickListener { viewModel.handleLightTap(LightColor.RED) }
        binding.lightYellowVertical.setOnClickListener { viewModel.handleLightTap(LightColor.YELLOW) }
        binding.lightGreenVertical.setOnClickListener { viewModel.handleLightTap(LightColor.GREEN) }

        binding.lightRedHorizontal.setOnClickListener { viewModel.handleLightTap(LightColor.RED) }
        binding.lightYellowHorizontal.setOnClickListener { viewModel.handleLightTap(LightColor.YELLOW) }
        binding.lightGreenHorizontal.setOnClickListener { viewModel.handleLightTap(LightColor.GREEN) }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.instanceId == instanceId) {
                        updateUi(state)
                        updateHighlight(state.isSettingsOpen)
                    }
                }
            }
        }
    }

    private fun updateHighlight(isSettingsCurrentlyOpen: Boolean) {
        if (isSettingsCurrentlyOpen) {
            binding.rootLayout.setBackgroundColor(Color.argb(50, 255, 255, 0))
        } else {
            binding.rootLayout.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun updateUi(state: TrafficLightState) {
        binding.trafficLightVerticalContainer.isVisible = state.orientation == Orientation.VERTICAL
        binding.trafficLightHorizontalContainer.isVisible = state.orientation == Orientation.HORIZONTAL

        val (redView, yellowView, greenView) = if (state.orientation == Orientation.VERTICAL) {
            Triple(binding.lightRedVertical, binding.lightYellowVertical, binding.lightGreenVertical)
        } else {
            Triple(binding.lightRedHorizontal, binding.lightYellowHorizontal, binding.lightGreenHorizontal)
        }

        redView.isActivated = state.activeLight == LightColor.RED
        yellowView.isActivated = state.activeLight == LightColor.YELLOW
        greenView.isActivated = state.activeLight == LightColor.GREEN

        val activeDesc = when (state.activeLight) {
            LightColor.RED -> getString(R.string.traffic_light_red_active_desc)
            LightColor.YELLOW -> getString(R.string.traffic_light_yellow_active_desc)
            LightColor.GREEN -> getString(R.string.traffic_light_green_active_desc)
            null -> getString(R.string.traffic_light_no_light_active_desc)
        }
        if (state.orientation == Orientation.VERTICAL) {
            binding.trafficLightVerticalShell.contentDescription = activeDesc
        } else {
            binding.trafficLightHorizontalShell.contentDescription = activeDesc
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        binding.rootLayout.setOnTouchListener { _, event ->
            val scaleConsumed = scaleGestureDetector.onTouchEvent(event)

            if (!isResizing) { // isResizing is set by ScaleListener
                handleMoveGesture(event)
            }
            scaleConsumed || isMoving || event.actionMasked == MotionEvent.ACTION_DOWN
        }
    }

    private fun handleMoveGesture(event: MotionEvent) {
        // This function is generally called when !isResizing
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Only consider starting a move if it's the primary pointer
                if (event.pointerId == 0) { // Check if it's the first pointer
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false // Not moving yet, just pressed down
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // Process move only if a single pointer is involved and not resizing
                if (event.pointerCount == 1 && !isResizing) {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    if (!isMoving) { // If not moving, check if slop is overcome
                        if (sqrt(deltaX * deltaX + deltaY * deltaY) > touchSlop) {
                            isMoving = true // Slop overcome, now officially moving
                        }
                    }

                    if (isMoving) { // If moving state is active
                        layoutParams.x = initialX + deltaX.toInt()
                        layoutParams.y = initialY + deltaY.toInt()
                        try {
                            windowManagerService.updateViewLayout(binding.root, layoutParams)
                        } catch (e: IllegalArgumentException) {
                            Log.e("TrafficLightActivity", "Error updating view layout on move", e)
                        }
                    }
                } else if (isMoving && event.pointerCount > 1) {
                    // If a move was in progress and more pointers are added,
                    // but it didn't become a scale gesture (isResizing is false),
                    // the drag effectively stops because the condition (event.pointerCount == 1) is no longer met.
                    // This satisfies "second finger ignored" as the drag halts.
                    isMoving = false // Stop the one-finger drag
                }
            }
            MotionEvent.ACTION_UP -> {
                // Last pointer is up
                if (isMoving) {
                    // Optional: Persist position via ViewModel
                }
                isMoving = false
                // isResizing should be false here, managed by ScaleListener.onScaleEnd
            }
            MotionEvent.ACTION_CANCEL -> {
                isMoving = false
                isResizing = false // Ensure this is reset on cancel as well
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.actionIndex == 0 && isMoving) { // If primary pointer that was moving is lifted
                    isMoving = false
                }
                // If a scale gesture was active and now only one pointer remains
                if (isResizing && event.pointerCount < 2) {
                    isResizing = false // Scale ended
                    isMoving = false // Don't immediately start moving
                }
            }
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var startWidth = 0
        private var startHeight = 0

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (isMoving && detector.eventTime - detector.previousEventTime < 100) { // Heuristic: quick succession of events
                // If a move was just happening, a very quick second finger might be part of a pinch.
                // Allow scaling to take over.
                isMoving = false // Stop the move to allow scaling
            }
            // Ignore resize if already moving with one finger, UNLESS it's a clear pinch start
            // The above 'isMoving = false' handles the transition.
            // If still isMoving here, means it wasn't a quick transition.
            if (isMoving) return false


            isResizing = true
            startWidth = layoutParams.width
            startHeight = layoutParams.height
            scaleFactor = 1f
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!isResizing) return false // Should not happen if onScaleBegin was true

            scaleFactor = detector.scaleFactor // Use incremental scale factor

            // Apply scale to current dimensions, not initial ones, for smoother interactive scaling
            var newWidth = (layoutParams.width * scaleFactor).toInt()
            var newHeight = (layoutParams.height * scaleFactor).toInt()


            val minSizePx = resources.getDimensionPixelSize(R.dimen.traffic_light_min_width) // Assuming min_width can serve as general min_size
            // No explicit max size from requirements, but good to have a practical limit
            val practicalMaxSizePx = 현실적으로_화면_크기의_일정_비율 // (e.g., screenWidth * 0.9f) - replace with actual calculation

            newWidth = max(minSizePx, newWidth)
            newHeight = max(minSizePx, newHeight)
            // newWidth = min(newWidth, practicalMaxSizePx) // If you add a max size
            // newHeight = min(newHeight, practicalMaxSizePx) // If you add a max size


            layoutParams.width = newWidth
            layoutParams.height = newHeight

            try {
                windowManagerService.updateViewLayout(binding.root, layoutParams)
            } catch (e: IllegalArgumentException) {
                Log.e("TrafficLightActivity", "Error updating view layout during scale", e)
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isResizing = false
            // Optional: Persist size via ViewModel
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup if needed
    }
}
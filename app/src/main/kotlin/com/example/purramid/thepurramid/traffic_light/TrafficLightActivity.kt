// TrafficLightActivity.kt
package com.example.purramid.thepurramid.traffic_light

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.purramid.thepurramid.R
// Corrected import for the renamed layout file
import com.example.purramid.thepurramid.databinding.ActivityTrafficLightBinding // <-- CHANGE HERE
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@AndroidEntryPoint
class TrafficLightActivity : AppCompatActivity() {

    // Use the correct binding class name
    private lateinit var binding: ActivityTrafficLightBinding // <-- CHANGE HERE
    private val viewModel: TrafficLightViewModel by viewModels()

    // --- Window Movement & Resizing Variables ---
    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isMoving = false
    private var isResizing = false
    private val touchSlop = 20 // Pixel buffer for shake tolerance / distinguishing tap/drag
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate using the correct binding class
        binding = ActivityTrafficLightBinding.inflate(layoutInflater) // <-- CHANGE HERE
        setContentView(binding.root)

        // Important: Ensure the window can be moved/resized
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, // Allow drawing outside screen bounds initially if needed
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) // No dimming
        // Apply theme/style for transparency if needed via AndroidManifest or theme

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        layoutParams = window.attributes as WindowManager.LayoutParams

        // Initialize scale detector for resizing
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())


        setupButtonClickListeners()
        observeViewModel()
        setupTouchListener() // Setup touch listener AFTER initializing window manager etc.
    }

    private fun setupButtonClickListeners() {
        binding.buttonClose.setOnClickListener { finish() }
        binding.buttonSettings.setOnClickListener {
            // Show the settings dialog fragment
            TrafficLightSettingsFragment.newInstance().show(
                supportFragmentManager, TrafficLightSettingsFragment.TAG
            )

            // TODO: Implement highlight logic for the window that opened settings
            // This might involve the ViewModel triggering a state change
            // observed by the Activity, or direct communication if using a Service.
        }

        // Setup listeners for light taps
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
                    updateUi(state)
                }
            }
        }
    }

    private fun updateUi(state: TrafficLightState) {
        // Update Orientation
        binding.trafficLightVerticalContainer.isVisible = state.orientation == Orientation.VERTICAL
        binding.trafficLightHorizontalContainer.isVisible = state.orientation == Orientation.HORIZONTAL

        // Update Active Light (selectors handle visual change)
        val (redView, yellowView, greenView) = if (state.orientation == Orientation.VERTICAL) {
            Triple(binding.lightRedVertical, binding.lightYellowVertical, binding.lightGreenVertical)
        } else {
            Triple(binding.lightRedHorizontal, binding.lightYellowHorizontal, binding.lightGreenHorizontal)
        }

        redView.isActivated = state.activeLight == LightColor.RED
        yellowView.isActivated = state.activeLight == LightColor.YELLOW
        greenView.isActivated = state.activeLight == LightColor.GREEN

        // Update Content Descriptions based on active light
        val activeDesc = when(state.activeLight) {
            LightColor.RED -> getString(R.string.traffic_light_red_active_desc)
            LightColor.YELLOW -> getString(R.string.traffic_light_yellow_active_desc)
            LightColor.GREEN -> getString(R.string.traffic_light_green_active_desc)
            null -> getString(R.string.traffic_light_no_light_active_desc)
        }
        // Update content description of the appropriate container or shell image
        if(state.orientation == Orientation.VERTICAL) {
            binding.trafficLightVerticalShell.contentDescription = activeDesc
        } else {
            binding.trafficLightHorizontalShell.contentDescription = activeDesc
        }

        // TODO: Update blinking state visuals if needed
        // TODO: Update Timer UI elements
        // TODO: Update Message UI elements
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        binding.rootLayout.setOnTouchListener { _, event ->
            // Pass touch events to ScaleGestureDetector first
            val scaleConsumed = scaleGestureDetector.onTouchEvent(event)

            // Only handle move if not resizing
            if (!isResizing) {
                handleMoveGesture(event)
            }

            // Consume event if scale or move handled it
            scaleConsumed || isMoving || event.action == MotionEvent.ACTION_DOWN // Consume ACTION_DOWN to enable move detection
        }
    }


    private fun handleMoveGesture(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Only track primary pointer for moving
                if (event.pointerCount == 1) {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false // Reset moving flag
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // Ignore move if more than one pointer OR if resizing is active
                if (event.pointerCount == 1 && !isResizing) {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    // Only start moving if displacement exceeds slop
                    if (isMoving || abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) {
                        isMoving = true // Set flag once movement threshold passed
                        layoutParams.x = initialX + deltaX.toInt()
                        layoutParams.y = initialY + deltaY.toInt()
                        try {
                            windowManager.updateViewLayout(binding.root, layoutParams)
                        } catch (e: IllegalArgumentException) {
                            // Handle edge case where view might be detached
                            Log.e("TrafficLightActivity", "Error updating view layout", e)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Reset flags only if we weren't resizing
                if (!isResizing) {
                    isMoving = false
                    // Optional: Persist position via ViewModel
                    // if (abs(layoutParams.x - initialX) > someThreshold || abs(layoutParams.y - initialY) > someThreshold ) {
                    //      viewModel.updateWindowPosition(layoutParams.x, layoutParams.y)
                    // }
                }
            }
            // Prevent moving if a second finger is added AFTER starting a move
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isMoving) {
                    // Cancel current move if a second finger is added
                    isMoving = false
                    // Reset to initial position? Or keep current? Requirements say ignore move if resizing starts.
                    // layoutParams.x = initialX
                    // layoutParams.y = initialY
                    // windowManager.updateViewLayout(binding.root, layoutParams)
                }
            }
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var startWidth = 0
        private var startHeight = 0

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            // Ignore resize if already moving with one finger
            if (isMoving) return false

            isResizing = true
            startWidth = layoutParams.width
            startHeight = layoutParams.height
            // If using view scaling instead of window resizing:
            // startWidth = binding.root.width
            // startHeight = binding.root.height
            scaleFactor = 1f // Reset scale factor at the beginning of a gesture
            return true // We want to handle the scaling gesture
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor

            // Prevent excessive shrinking or growing if desired
            scaleFactor = max(0.5f, min(scaleFactor, 3.0f)) // Example limits

            // --- Window Resizing Approach ---
            if (startWidth > 0 && startHeight > 0) { // Ensure initial dimensions are valid
                layoutParams.width = (startWidth * scaleFactor).toInt()
                layoutParams.height = (startHeight * scaleFactor).toInt()

                // Apply minimum size constraints (e.g., based on resources)
                val minWidth = resources.getDimensionPixelSize(R.dimen.traffic_light_min_width)
                val minHeight = resources.getDimensionPixelSize(R.dimen.traffic_light_min_height)
                layoutParams.width = max(minWidth, layoutParams.width)
                layoutParams.height = max(minHeight, layoutParams.height)

                try {
                    windowManager.updateViewLayout(binding.root, layoutParams)
                } catch (e: IllegalArgumentException) {
                    Log.e("TrafficLightActivity", "Error updating view layout during scale", e)
                }
            }

            // --- Alternative: View Scaling Approach (Comment out Window Resizing if using this) ---
            // binding.root.scaleX = scaleFactor
            // binding.root.scaleY = scaleFactor
            // // Need to ensure layout handles scaling appropriately, may need adjustments

            return true // The scale event was handled
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isResizing = false
            // Optional: Persist size via ViewModel (Window Resizing)
            // viewModel.updateWindowSize(layoutParams.width, layoutParams.height)

            // Optional: Persist scale via ViewModel (View Scaling)
            // viewModel.updateWindowScale(scaleFactor)
        }
    }

    // Optional: Override onStop or onDestroy to save state if needed
    // override fun onStop() {
    //     super.onStop()
    //     // Persist state if the app is stopped but not destroyed
    //     viewModel.saveState()
    // }
}
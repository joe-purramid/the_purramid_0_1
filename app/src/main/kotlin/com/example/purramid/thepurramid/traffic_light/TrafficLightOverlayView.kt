// TrafficLightOverlayView.kt
package com.example.purramid.thepurramid.traffic_light

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.TrafficLightOverlayViewBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.LightColor
import com.example.purramid.thepurramid.traffic_light.viewmodel.Orientation
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class TrafficLightOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val instanceId: Int
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val BASE_MESSAGE_TEXT_SIZE_SP = 24f
        private const val MIN_ACCESSIBLE_TEXT_SIZE_SP = 14f
        private const val TAG = "TrafficLightOverlay"
        private const val LAUNCH_ANIMATION_DELAY = 300L // 0.3 seconds

        // Traffic light colors as per specification
        private const val COLOR_RED_DEFAULT = 0xFFB81B0E.toInt()
        private const val COLOR_RED_ACTIVE = 0xFFFF0000.toInt()
        private const val COLOR_YELLOW_DEFAULT = 0xFFB8BB0E.toInt()
        private const val COLOR_YELLOW_ACTIVE = 0xFFFFFF00.toInt()
        private const val COLOR_GREEN_DEFAULT = 0xFF549C30.toInt()
        private const val COLOR_GREEN_ACTIVE = 0xFF00FF00.toInt()
    }

    private val binding: TrafficLightOverlayViewBinding
    var interactionListener: InteractionListener? = null

    // Touch Handling Variables
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var initialViewX: Int = 0
    private var initialViewY: Int = 0
    private var isMoving = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val viewBoundsRect = Rect()

    // Resize/Scale Handling Variables
    private var scaleGestureDetector: ScaleGestureDetector
    private var initialViewWidth: Int = 0
    private var initialViewHeight: Int = 0
    private var scaleFactor = 1f
    private var isResizingWithScale = false
    private val minSizePx = resources.getDimensionPixelSize(R.dimen.traffic_light_min_size)

    // Blinking Variables
    private var blinkingAnimator: ObjectAnimator? = null
    private var currentlyBlinkingView: View? = null
    private val blinkDuration = 750L

    // Launch animation
    private var launchAnimator: AnimatorSet? = null
    private var isLaunchAnimationComplete = false

    // Danger alert
    private var dangerMessageViews: Map<LightColor, TextView>? = null
    private var dangerBlinkingAnimator: ObjectAnimator? = null

    // State reference for certain operations
    private val _uiState: StateFlow<TrafficLightState>
        get() = (context as? TrafficLightService)?.let { service ->
            service.activeTrafficLightViewModels[instanceId]?.uiState
        } ?: MutableStateFlow(TrafficLightState())

    interface InteractionListener {
        fun onLightTapped(instanceId: Int, color: LightColor)
        fun onCloseRequested(instanceId: Int)
        fun onSettingsRequested(instanceId: Int)
        fun onMove(instanceId: Int, rawDeltaX: Float, rawDeltaY: Float)
        fun onMoveFinished(instanceId: Int)
        fun onResize(instanceId: Int, newWidth: Int, newHeight: Int)
        fun onResizeFinished(instanceId: Int, finalWidth: Int, finalHeight: Int)
    }

    init {
        binding = TrafficLightOverlayViewBinding.inflate(LayoutInflater.from(context), this, true)
        setupInternalListeners()
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        setupOverlayTouchListener()
        setupDangerMessageViews()
        initializeLightColors()
    }

    private fun initializeLightColors() {
        // Set all lights to use the base drawable
        binding.lightRedVerticalOverlay.setImageResource(R.drawable.ic_circle_base)
        binding.lightYellowVerticalOverlay.setImageResource(R.drawable.ic_circle_base)
        binding.lightGreenVerticalOverlay.setImageResource(R.drawable.ic_circle_base)
        binding.lightRedHorizontalOverlay.setImageResource(R.drawable.ic_circle_base)
        binding.lightYellowHorizontalOverlay.setImageResource(R.drawable.ic_circle_base)
        binding.lightGreenHorizontalOverlay.setImageResource(R.drawable.ic_circle_base)

        // Set default colors
        updateLightColor(binding.lightRedVerticalOverlay, LightColor.RED, false)
        updateLightColor(binding.lightYellowVerticalOverlay, LightColor.YELLOW, false)
        updateLightColor(binding.lightGreenVerticalOverlay, LightColor.GREEN, false)
        updateLightColor(binding.lightRedHorizontalOverlay, LightColor.RED, false)
        updateLightColor(binding.lightYellowHorizontalOverlay, LightColor.YELLOW, false)
        updateLightColor(binding.lightGreenHorizontalOverlay, LightColor.GREEN, false)
    }

    private fun updateLightColor(lightView: ImageView, color: LightColor, isActive: Boolean) {
        val tintColor = when (color) {
            LightColor.RED -> if (isActive) COLOR_RED_ACTIVE else COLOR_RED_DEFAULT
            LightColor.YELLOW -> if (isActive) COLOR_YELLOW_ACTIVE else COLOR_YELLOW_DEFAULT
            LightColor.GREEN -> if (isActive) COLOR_GREEN_ACTIVE else COLOR_GREEN_DEFAULT
        }
        lightView.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
    }

    private fun setupInternalListeners() {
        // Vertical Lights
        binding.lightRedVerticalOverlay.setOnClickListener { interactionListener?.onLightTapped(instanceId, LightColor.RED) }
        binding.lightYellowVerticalOverlay.setOnClickListener { interactionListener?.onLightTapped(instanceId, LightColor.YELLOW) }
        binding.lightGreenVerticalOverlay.setOnClickListener { interactionListener?.onLightTapped(instanceId, LightColor.GREEN) }

        // Horizontal Lights
        binding.lightRedHorizontalOverlay.setOnClickListener { interactionListener?.onLightTapped(instanceId, LightColor.RED) }
        binding.lightYellowHorizontalOverlay.setOnClickListener { interactionListener?.onLightTapped(instanceId, LightColor.YELLOW) }
        binding.lightGreenHorizontalOverlay.setOnClickListener { interactionListener?.onLightTapped(instanceId, LightColor.GREEN) }

        // Buttons
        binding.overlayButtonClose.setOnClickListener { interactionListener?.onCloseRequested(instanceId) }
        binding.overlayButtonSettings.setOnClickListener { interactionListener?.onSettingsRequested(instanceId) }
    }

    fun updateState(state: TrafficLightState) {
        val isVertical = state.orientation == Orientation.VERTICAL
        binding.trafficLightVerticalContainerOverlay.isVisible = isVertical
        binding.trafficLightHorizontalContainerOverlay.isVisible = !isVertical

        if (state.isDangerousAlertActive) {
            showDangerAlert(isVertical)
        } else {
            hideDangerAlert()
            updateNormalLightState(state, isVertical)
        }

        // Update message display
        updateMessageDisplay(state)
    }

    private fun updateNormalLightState(state: TrafficLightState, isVertical: Boolean) {
        val activeColor = state.activeLight
        val blinkEnabled = state.isBlinkingEnabled

        val redView = if (isVertical) binding.lightRedVerticalOverlay else binding.lightRedHorizontalOverlay
        val yellowView = if (isVertical) binding.lightYellowVerticalOverlay else binding.lightYellowHorizontalOverlay
        val greenView = if (isVertical) binding.lightGreenVerticalOverlay else binding.lightGreenHorizontalOverlay

        // Update colors instead of just activated state
        updateLightColor(redView, LightColor.RED, activeColor == LightColor.RED)
        updateLightColor(yellowView, LightColor.YELLOW, activeColor == LightColor.YELLOW)
        updateLightColor(greenView, LightColor.GREEN, activeColor == LightColor.GREEN)

        val viewToBlink: View? = when (activeColor) {
            LightColor.RED -> redView
            LightColor.YELLOW -> yellowView
            LightColor.GREEN -> greenView
            null -> null
        }

        // Stop previous blinking if conditions change
        if (currentlyBlinkingView != null && (currentlyBlinkingView != viewToBlink || !blinkEnabled || activeColor == null)) {
            stopBlinking(currentlyBlinkingView!!)
        }

        // Ensure non-active lights are not blinking
        if (activeColor != LightColor.RED) { stopBlinking(redView) }
        if (activeColor != LightColor.YELLOW) { stopBlinking(yellowView) }
        if (activeColor != LightColor.GREEN) { stopBlinking(greenView) }

        if (blinkEnabled && viewToBlink != null) {
            if (currentlyBlinkingView != viewToBlink) {
                startBlinking(viewToBlink)
            }
        } else if (!blinkEnabled && viewToBlink != null) {
            stopBlinking(viewToBlink)
        }
    }

    fun startLaunchAnimation(onComplete: () -> Unit = {}) {
        if (isLaunchAnimationComplete) return

        val state = _uiState.value
        val isVertical = state.orientation == Orientation.VERTICAL
        val blinkingEnabled = state.isBlinkingEnabled

        if (!blinkingEnabled) {
            isLaunchAnimationComplete = true
            onComplete()
            return
        }

        val redView = if (isVertical) binding.lightRedVerticalOverlay else binding.lightRedHorizontalOverlay
        val yellowView = if (isVertical) binding.lightYellowVerticalOverlay else binding.lightYellowHorizontalOverlay
        val greenView = if (isVertical) binding.lightGreenVerticalOverlay else binding.lightGreenHorizontalOverlay

        val animatorList = mutableListOf<Animator>()

        // First sequence: Red -> Yellow -> Green
        animatorList.add(createActivationAnimator(redView, LightColor.RED, 0L))
        animatorList.add(createActivationAnimator(yellowView, LightColor.YELLOW, LAUNCH_ANIMATION_DELAY))
        animatorList.add(createActivationAnimator(greenView, LightColor.GREEN, LAUNCH_ANIMATION_DELAY * 2))

        // Second sequence: Red -> Yellow -> Green (repeat)
        animatorList.add(createActivationAnimator(redView, LightColor.RED, LAUNCH_ANIMATION_DELAY * 3))
        animatorList.add(createActivationAnimator(yellowView, LightColor.YELLOW, LAUNCH_ANIMATION_DELAY * 4))
        animatorList.add(createActivationAnimator(greenView, LightColor.GREEN, LAUNCH_ANIMATION_DELAY * 5))

        // Final sequence: All lights flash together 3 times
        for (i in 0..2) {
            val baseDelay = LAUNCH_ANIMATION_DELAY * 6 + (i * LAUNCH_ANIMATION_DELAY * 2)
            animatorList.add(createSimultaneousFlashAnimator(
                listOf(redView, yellowView, greenView),
                listOf(LightColor.RED, LightColor.YELLOW, LightColor.GREEN),
                baseDelay
            ))
        }

        launchAnimator = AnimatorSet().apply {
            playTogether(animatorList)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Reset all lights to default colors
                    updateLightColor(redView, LightColor.RED, false)
                    updateLightColor(yellowView, LightColor.YELLOW, false)
                    updateLightColor(greenView, LightColor.GREEN, false)

                    isLaunchAnimationComplete = true
                    onComplete()
                }
            })
            start()
        }
    }

    private fun createActivationAnimator(view: View, color: LightColor, startDelay: Long): Animator {
        return ObjectAnimator.ofFloat(view, "alpha", 1f, 1f).apply {
            duration = LAUNCH_ANIMATION_DELAY
            this.startDelay = startDelay
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    if (view is ImageView) {
                        updateLightColor(view, color, true)
                    }
                }
                override fun onAnimationEnd(animation: Animator) {
                    if (view is ImageView) {
                        updateLightColor(view, color, false)
                    }
                }
            })
        }
    }

    private fun createSimultaneousFlashAnimator(views: List<View>, colors: List<LightColor>, startDelay: Long): Animator {
        return ObjectAnimator.ofFloat(views[0], "alpha", 1f, 1f).apply {
            duration = LAUNCH_ANIMATION_DELAY
            this.startDelay = startDelay
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    views.forEachIndexed { index, view ->
                        if (view is ImageView && index < colors.size) {
                            updateLightColor(view, colors[index], true)
                        }
                    }
                }
                override fun onAnimationEnd(animation: Animator) {
                    views.forEachIndexed { index, view ->
                        if (view is ImageView && index < colors.size) {
                            updateLightColor(view, colors[index], false)
                        }
                    }
                }
            })
        }
    }

    private fun startBlinking(view: View) {
        if (view !is ImageView) return

        val color = when (view.id) {
            R.id.light_red_vertical_overlay, R.id.light_red_horizontal_overlay -> LightColor.RED
            R.id.light_yellow_vertical_overlay, R.id.light_yellow_horizontal_overlay -> LightColor.YELLOW
            R.id.light_green_vertical_overlay, R.id.light_green_horizontal_overlay -> LightColor.GREEN
            else -> return
        }

        stopBlinking(view)

        blinkingAnimator = ObjectAnimator.ofFloat(0f, 1f).apply {
            duration = blinkDuration
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                updateLightColor(view, color, progress < 0.5f)
            }
            start()
        }
        currentlyBlinkingView = view
    }

    private fun stopBlinking(view: View) {
        if (currentlyBlinkingView == view && blinkingAnimator?.isRunning == true) {
            blinkingAnimator?.cancel()
        }

        if (view is ImageView) {
            val color = when (view.id) {
                R.id.light_red_vertical_overlay, R.id.light_red_horizontal_overlay -> LightColor.RED
                R.id.light_yellow_vertical_overlay, R.id.light_yellow_horizontal_overlay -> LightColor.YELLOW
                R.id.light_green_vertical_overlay, R.id.light_green_horizontal_overlay -> LightColor.GREEN
                else -> return
            }

            val isActive = _uiState.value.activeLight == color
            updateLightColor(view, color, isActive)
        }

        if (currentlyBlinkingView == view) {
            currentlyBlinkingView = null
            blinkingAnimator = null
        }
    }

    fun setHighlighted(highlighted: Boolean) {
        if (highlighted) {
            binding.overlayRootLayout.setBackgroundResource(R.drawable.highlight_border)
        } else {
            binding.overlayRootLayout.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun getScaledTextSize(baseSizeSp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            baseSizeSp,
            resources.displayMetrics
        )
    }

    private fun applyAccessibleTextSize(textView: TextView) {
        textView.textSize = getScaledTextSize(BASE_MESSAGE_TEXT_SIZE_SP)
    }

    private fun setupDangerMessageViews() {
        // These will be dynamically created when danger mode activates
    }

    private fun showDangerAlert(isVertical: Boolean) {
        currentlyBlinkingView?.let { stopBlinking(it) }

        if (dangerMessageViews == null) {
            createDangerMessageViews(isVertical)
        }

        dangerMessageViews?.forEach { (_, textView) ->
            textView.isVisible = true
        }

        startDangerBlink(isVertical)
    }

    private fun createDangerMessageViews(isVertical: Boolean) {
        val container = if (isVertical)
            binding.trafficLightVerticalContainerOverlay
        else
            binding.trafficLightHorizontalContainerOverlay

        val messages = mapOf(
            LightColor.RED to "Warning",
            LightColor.YELLOW to "Notify your instructor immediately.",
            LightColor.GREEN to "Double-tap green to dismiss this alert."
        )

        val messageViews = mutableMapOf<LightColor, TextView>()

        messages.forEach { (color, message) ->
            val textView = TextView(context).apply {
                text = message
                textSize = getScaledTextSize(BASE_MESSAGE_TEXT_SIZE_SP)
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.WHITE)
                setPadding(16, 8, 16, 8)
                elevation = 4f
                isVisible = false
            }

            container.addView(textView)

            val layoutParams = textView.layoutParams as ConstraintLayout.LayoutParams
            when (color) {
                LightColor.RED -> {
                    if (isVertical) {
                        layoutParams.topToTop = R.id.light_red_vertical_overlay
                        layoutParams.bottomToBottom = R.id.light_red_vertical_overlay
                        layoutParams.startToEnd = R.id.traffic_light_vertical_shell_overlay
                    } else {
                        layoutParams.topToBottom = R.id.light_red_horizontal_overlay
                        layoutParams.startToStart = R.id.light_red_horizontal_overlay
                        layoutParams.endToEnd = R.id.light_red_horizontal_overlay
                    }
                }
                LightColor.YELLOW -> {
                    if (isVertical) {
                        layoutParams.topToTop = R.id.light_yellow_vertical_overlay
                        layoutParams.bottomToBottom = R.id.light_yellow_vertical_overlay
                        layoutParams.startToEnd = R.id.traffic_light_vertical_shell_overlay
                    } else {
                        layoutParams.topToBottom = R.id.light_yellow_horizontal_overlay
                        layoutParams.startToStart = R.id.light_yellow_horizontal_overlay
                        layoutParams.endToEnd = R.id.light_yellow_horizontal_overlay
                    }
                }
                LightColor.GREEN -> {
                    if (isVertical) {
                        layoutParams.topToTop = R.id.light_green_vertical_overlay
                        layoutParams.bottomToBottom = R.id.light_green_vertical_overlay
                        layoutParams.startToEnd = R.id.traffic_light_vertical_shell_overlay
                    } else {
                        layoutParams.topToBottom = R.id.light_green_horizontal_overlay
                        layoutParams.startToStart = R.id.light_green_horizontal_overlay
                        layoutParams.endToEnd = R.id.light_green_horizontal_overlay
                    }
                }
            }
            layoutParams.marginStart = 16
            textView.layoutParams = layoutParams

            messageViews[color] = textView
        }

        dangerMessageViews = messageViews
    }

    private fun startDangerBlink(isVertical: Boolean) {
        val redView = if (isVertical) binding.lightRedVerticalOverlay else binding.lightRedHorizontalOverlay
        val yellowView = if (isVertical) binding.lightYellowVerticalOverlay else binding.lightYellowHorizontalOverlay

        dangerBlinkingAnimator = ObjectAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                if (progress < 0.5f) {
                    updateLightColor(redView, LightColor.RED, true)
                    updateLightColor(yellowView, LightColor.YELLOW, false)
                } else {
                    updateLightColor(redView, LightColor.RED, false)
                    updateLightColor(yellowView, LightColor.YELLOW, true)
                }
            }
            start()
        }
    }

    private fun hideDangerAlert() {
        dangerBlinkingAnimator?.cancel()
        dangerBlinkingAnimator = null

        dangerMessageViews?.forEach { (_, textView) ->
            textView.isVisible = false
        }

        val isVertical = binding.trafficLightVerticalContainerOverlay.isVisible
        val redView = if (isVertical) binding.lightRedVerticalOverlay else binding.lightRedHorizontalOverlay
        val yellowView = if (isVertical) binding.lightYellowVerticalOverlay else binding.lightYellowHorizontalOverlay

        updateLightColor(redView, LightColor.RED, false)
        updateLightColor(yellowView, LightColor.YELLOW, false)
    }

    private fun updateMessageDisplay(state: TrafficLightState) {
        val activeLight = state.activeLight
        val messages = state.messages

        hideAllMessages()

        activeLight?.let { color ->
            val message = messages.getMessageForColor(color)
            if (!message.isEmpty()) {
                showMessage(color, message, state.orientation == Orientation.VERTICAL)
            }
        }
    }

    private fun hideAllMessages() {
        // Implementation depends on how messages are displayed
    }

    private fun showMessage(color: LightColor, message: MessageData, isVertical: Boolean) {
        // This would create/update TextView for the message
        // Position it according to spec 12.3.3
        // Implementation depends on how you want to handle dynamic view creation
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayTouchListener() {
        this.setOnTouchListener { _, event ->
            if (isTouchOnInteractiveElement(event) && event.actionMasked == MotionEvent.ACTION_DOWN) {
                isMoving = false
                isResizingWithScale = false
                return@setOnTouchListener false
            }

            val scaleConsumed = scaleGestureDetector.onTouchEvent(event)
            var moveConsumed = false

            if (!isResizingWithScale) {
                moveConsumed = handleMoveGesture(event)
            }

            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (isMoving) {
                    interactionListener?.onMoveFinished(instanceId)
                    isMoving = false
                }
                if (isResizingWithScale) {
                    interactionListener?.onResizeFinished(instanceId, width, height)
                    isResizingWithScale = false
                }
            }

            scaleConsumed || moveConsumed || event.actionMasked == MotionEvent.ACTION_DOWN
        }
    }

    private fun handleMoveGesture(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isMoving = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) {
                    isMoving = false
                    return false
                }
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY

                if (!isMoving) {
                    if (sqrt(deltaX * deltaX + deltaY * deltaY) > touchSlop) {
                        isMoving = true
                    }
                }

                if (isMoving) {
                    interactionListener?.onMove(instanceId, deltaX, deltaY)
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }
            }
        }
        return false
    }

    private fun isTouchOnInteractiveElement(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()

        val interactiveViews = listOfNotNull(
            binding.overlayButtonClose, binding.overlayButtonSettings,
            binding.lightRedVerticalOverlay, binding.lightYellowVerticalOverlay, binding.lightGreenVerticalOverlay,
            binding.lightRedHorizontalOverlay, binding.lightYellowHorizontalOverlay, binding.lightGreenHorizontalOverlay
        )

        for (view in interactiveViews) {
            if (view.isVisible) {
                view.getHitRect(viewBoundsRect)
                if (viewBoundsRect.contains(x, y)) {
                    return true
                }
            }
        }
        return false
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (isMoving) return false

            isResizingWithScale = true
            initialViewWidth = width
            initialViewHeight = height
            scaleFactor = 1.0f
            Log.d(TAG, "onScaleBegin - Start Size: ${initialViewWidth}x$initialViewHeight")
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!isResizingWithScale) return false
            scaleFactor *= detector.scaleFactor

            var newWidth = (initialViewWidth * scaleFactor).toInt()
            var newHeight = (initialViewHeight * scaleFactor).toInt()

            newWidth = max(minSizePx, newWidth)
            newHeight = max(minSizePx, newHeight)

            interactionListener?.onResize(instanceId, newWidth, newHeight)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (isResizingWithScale) {
                var finalWidth = (initialViewWidth * scaleFactor).toInt()
                var finalHeight = (initialViewHeight * scaleFactor).toInt()
                finalWidth = max(minSizePx, finalWidth)
                finalHeight = max(minSizePx, finalHeight)

                interactionListener?.onResizeFinished(instanceId, finalWidth, finalHeight)
            }
            isResizingWithScale = false
            scaleFactor = 1.0f
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        launchAnimator?.cancel()
        blinkingAnimator?.cancel()
        dangerBlinkingAnimator?.cancel()
        launchAnimator = null
        blinkingAnimator = null
        dangerBlinkingAnimator = null
        currentlyBlinkingView = null
    }
}
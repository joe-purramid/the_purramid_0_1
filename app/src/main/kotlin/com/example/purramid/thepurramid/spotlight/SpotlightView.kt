// SpotlightView.kt
package com.example.purramid.thepurramid.spotlight

import android.content.Context
import android.graphics.*
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.example.purramid.thepurramid.R
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

class SpotlightView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // --- Listener Interface ---
    interface SpotlightInteractionListener {
        fun requestUpdateLayout(params: WindowManager.LayoutParams)
        fun requestTapPassThrough()
        fun requestClose()
        fun requestShapeChange() // Request global shape change
        fun requestAddNew()      // Request adding a new spotlight
    }
    var interactionListener: SpotlightInteractionListener? = null

    private val shadeColor = Color.argb(128, 0, 0, 0) // Charcoal at 50% opacity
    private val shadePaint = Paint().apply { color = shadeColor }
    private val spotlightPaint = Paint().apply { // Paint for the "hole"
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true // Make edges smoother
    }

    var spotlights = mutableListOf<Spotlight>()
        set(value) { field = value; invalidate() }
    var currentShape = Spotlight.Shape.CIRCLE // Keep track of the current global shape
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
    private var isDragging = false
    private var isResizing = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var currentDraggingSpotlight: Spotlight? = null
    private var pointerId1 = MotionEvent.INVALID_POINTER_ID
    private var pointerId2 = MotionEvent.INVALID_POINTER_ID
    private var initialDistance = 0f
    private var initialAngle = 0f
    private var initialSpotlightRadius = 0f // Added
    private var initialSpotlightSize = 0f // Added
    private var initialSpotlightWidth = 0f // Added
    private var initialSpotlightHeight = 0f // Added
    private var resizingSpotlight: Spotlight? = null
    private var resizingSpotlightInitialShape: Spotlight.Shape? = null
    private var downX = 0f
    private var downY = 0f
    private var downTime: Long = 0

    private val ovalRect = RectF() // Preallocate RectF for oval drawing

    // --- UI Control Elements ---
    private val controlButtonSize = dpToPx(48)
    private val controlButtonPadding = dpToPx(12)
    private val controlMargin = dpToPx(16)

    // Button Rectangles for touch detection
    private var addRect = Rect()
    private var closeRect = Rect()
    private var shapeRect = Rect()

    // Button Drawables
    private var addDrawable: Drawable? = null
    private var closeDrawable: Drawable? = null
    private var shapeDrawableCircle: Drawable? = null
    private var shapeDrawableSquare: Drawable? = null

    var showControls = true // Flag to control visibility
    private var canAddMoreSpotlights = true // Track if max is reached


    init {
        // IMPORTANT: For CLEAR mode to work reliably, the view needs its own layer.
        // LAYER_TYPE_HARDWARE often works best. Set this in the Service when adding the view
        // or here if appropriate.
        // setLayerType(LAYER_TYPE_HARDWARE, null) // Requires API 11+

        loadControlDrawables()
    }

    private fun loadControlDrawables() {
        addDrawable = ContextCompat.getDrawable(context, R.drawable.ic_add_circle)
        closeDrawable = ContextCompat.getDrawable(context, R.drawable.ic_close) // Assuming you have ic_close
        shapeDrawableCircle = ContextCompat.getDrawable(context, R.drawable.ic_circle)
        shapeDrawableSquare = ContextCompat.getDrawable(context, R.drawable.ic_square)
    }

    fun updateCanAddSpotlights(canAdd: Boolean) {
        canAddMoreSpotlights = canAdd
        invalidate() // Redraw to potentially disable button visual
    }

    // Called by the service to apply global shape change
    fun setGlobalShape(newShape: Spotlight.Shape) {
        currentShape = newShape
        // Apply shape change to existing spotlights
        spotlights.forEach { spotlight ->
            // Maintain size/proportions as best possible when switching shape
            val avgDim = (spotlight.width + spotlight.height) / 2f
            spotlight.shape = newShape
            when (newShape) {
                Spotlight.Shape.CIRCLE -> {
                    spotlight.radius = avgDim / 2f
                    spotlight.width = spotlight.radius * 2
                    spotlight.height = spotlight.radius * 2
                    spotlight.size = spotlight.radius * 2
                }
                Spotlight.Shape.SQUARE -> {
                    spotlight.size = avgDim
                    spotlight.width = spotlight.size
                    spotlight.height = spotlight.size
                    spotlight.radius = spotlight.size / 2f // Approximate
                }
                Spotlight.Shape.OVAL -> { // Reset to default aspect ratio or keep? Let's reset.
                    spotlight.radius = avgDim / 2f // Base radius
                    spotlight.width = spotlight.radius * 2 * 1.5f // Example aspect ratio
                    spotlight.height = spotlight.radius * 2 / 1.5f
                    spotlight.size = maxOf(spotlight.width, spotlight.height)
                }
                Spotlight.Shape.RECTANGLE -> {
                    spotlight.radius = avgDim / 2f // Base radius
                    spotlight.width = spotlight.radius * 2 * 1.5f // Example aspect ratio
                    spotlight.height = spotlight.radius * 2 / 1.5f
                    spotlight.size = maxOf(spotlight.width, spotlight.height)
                }
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw the shade overlay
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shadePaint)

        // Draw the spotlights (by clearing the area)
        spotlights.forEach { spotlight ->
            when (spotlight.shape) {
                Spotlight.Shape.CIRCLE -> canvas.drawCircle(spotlight.centerX, spotlight.centerY, spotlight.radius, spotlightPaint)
                Spotlight.Shape.OVAL -> {
                    ovalRect.set(
                        spotlight.centerX - spotlight.width / 2f,
                        spotlight.centerY - spotlight.height / 2f,
                        spotlight.centerX + spotlight.width / 2f,
                        spotlight.centerY + spotlight.height / 2f
                    )
                    canvas.drawOval(ovalRect, spotlightPaint)
                }
                Spotlight.Shape.SQUARE -> canvas.drawRect(
                    spotlight.centerX - spotlight.size / 2f,
                    spotlight.centerY - spotlight.size / 2f,
                    spotlight.centerX + spotlight.size / 2f,
                    spotlight.centerY + spotlight.size / 2f,
                    spotlightPaint
                )
                Spotlight.Shape.RECTANGLE -> canvas.drawRect(
                    spotlight.centerX - spotlight.width / 2f,
                    spotlight.centerY - spotlight.height / 2f,
                    spotlight.centerX + spotlight.width / 2f,
                    spotlight.centerY + spotlight.height / 2f,
                    spotlightPaint
                )
            }
        }
        // Draw UI Controls (if shown)
        if (showControls) {
            drawControls(canvas)
        }
    }

    private fun drawControls(canvas: Canvas) {
        val bottomY = height - controlMargin - controlButtonSize / 2f // Y center for buttons
        val totalWidthNeeded = controlButtonSize * 3 + controlMargin * 2
        val startX = (width - totalWidthNeeded) / 2f

        // Calculate bounds for hit detection
        var currentX = startX

        // Add Button
        addRect.set(
            currentX.toInt(),
            (bottomY - controlButtonSize / 2f).toInt(),
            (currentX + controlButtonSize).toInt(),
            (bottomY + controlButtonSize / 2f).toInt()
        )
        addDrawable?.bounds = addRect
        addDrawable?.alpha = if (canAddMoreSpotlights) 255 else 128 // Dim if disabled
        addDrawable?.draw(canvas)
        currentX += controlButtonSize + controlMargin

        // Close Button
        closeRect.set(
            currentX.toInt(),
            (bottomY - controlButtonSize / 2f).toInt(),
            (currentX + controlButtonSize).toInt(),
            (bottomY + controlButtonSize / 2f).toInt()
        )
        closeDrawable?.bounds = closeRect
        closeDrawable?.draw(canvas)
        currentX += controlButtonSize + controlMargin

        // Shape Button
        shapeRect.set(
            currentX.toInt(),
            (bottomY - controlButtonSize / 2f).toInt(),
            (currentX + controlButtonSize).toInt(),
            (bottomY + controlButtonSize / 2f).toInt()
        )
        val shapeDrawable = if (currentShape == Spotlight.Shape.CIRCLE || currentShape == Spotlight.Shape.OVAL) {
            shapeDrawableCircle
        } else {
            shapeDrawableSquare
        }
        shapeDrawable?.bounds = shapeRect
        shapeDrawable?.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                downTime = System.currentTimeMillis()
                activePointerId = event.getPointerId(0)
                initialTouchX = event.x // Local coords
                initialTouchY = event.y
                initialRawX = event.rawX // Screen coords
                initialRawY = event.rawY

                // Check if touch is on a UI control first
                if (showControls && handleControlTouch(downX, downY)) {
                    return true // Consumed by control button
                }

                currentDraggingSpotlight = findTouchedSpotlight(initialTouchX, initialTouchY)
                isDragging = false
                isResizing = false
                resizingSpotlight = null
                pointerId1 = MotionEvent.INVALID_POINTER_ID
                pointerId2 = MotionEvent.INVALID_POINTER_ID

                return true // Claim event even if not on spotlight initially
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2 && !isResizing && currentDraggingSpotlight == null) {
                    // Check if starting point is inside a spotlight for resize target
                    val index1 = event.findPointerIndex(event.getPointerId(0))
                    val index2 = event.findPointerIndex(event.getPointerId(1))
                    if (index1 != -1 && index2 != -1) {
                        val x1 = event.getX(index1); val y1 = event.getY(index1)
                        val x2 = event.getX(index2); val y2 = event.getY(index2)
                        val midX = (x1 + x2) / 2f
                        val midY = (y1 + y2) / 2f
                        resizingSpotlight = findTouchedSpotlight(midX, midY)

                        if (resizingSpotlight != null) {
                            isResizing = true
                            isDragging = false
                            pointerId1 = event.getPointerId(0)
                            pointerId2 = event.getPointerId(1)
                            initialDistance = hypot(x2 - x1, y2 - y1)
                            initialAngle = atan2(y2 - y1, x2 - x1)
                            // Capture initial dimensions
                            resizingSpotlight?.let {
                                initialSpotlightRadius = it.radius
                                initialSpotlightSize = it.size
                                initialSpotlightWidth = it.width
                                initialSpotlightHeight = it.height
                                resizingSpotlightInitialShape = it.shape
                            }
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2 && !isResizing && currentDraggingSpotlight == null) {
                        // Check if starting point is inside a spotlight for resize target
                        val index1 = event.findPointerIndex(event.getPointerId(0))
                        val index2 = event.findPointerIndex(event.getPointerId(1))
                        if (index1 != -1 && index2 != -1) {
                            val x1 = event.getX(index1); val y1 = event.getY(index1)
                            val x2 = event.getX(index2); val y2 = event.getY(index2)
                            val midX = (x1 + x2) / 2f
                            val midY = (y1 + y2) / 2f
                            resizingSpotlight = findTouchedSpotlight(midX, midY)

                            if (resizingSpotlight != null) {
                                isResizing = true
                                isDragging = false
                                pointerId1 = event.getPointerId(0)
                                pointerId2 = event.getPointerId(1)
                                initialDistance = hypot(x2 - x1, y2 - y1)
                                initialAngle = atan2(y2 - y1, x2 - x1)
                                // Capture initial dimensions
                                resizingSpotlight?.let {
                                    initialSpotlightRadius = it.radius
                                    initialSpotlightSize = it.size
                                    initialSpotlightWidth = it.width
                                    initialSpotlightHeight = it.height
                                    resizingSpotlightInitialShape = it.shape
                                }
                            }
                        }
                    }
                    return true
                }
                // --- Handle Window Move (Touch on Shade) ---
                else if (!isResizing && currentDraggingSpotlight == null && activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    // This logic needs to move to the Service, as the View itself doesn't control WindowManager params directly.
                    // The View should detect the drag on the shade and notify the Service.
                    // For now, we just detect if dragging started on the shade.
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val currentRawX = event.getRawX() // Use RawX for screen coords
                        val currentRawY = event.getRawY()
                        val deltaRawX = currentRawX - initialRawX
                        val deltaRawY = currentRawY - initialRawY

                        if (!isDragging && hypot(deltaRawX, deltaRawY) > touchSlop) {
                            isDragging = true // Flag that dragging (of the window) started
                            // Tell the service to start moving the window? Or pass delta?
                        }
                        if (isDragging) {
                            // **This part MUST happen in the Service**
                            // params.x = initialWindowX + deltaRawX.toInt()
                            // params.y = initialWindowY + deltaRawY.toInt()
                            // interactionListener?.requestUpdateLayout(params)
                            // Log.d("SpotlightView", "Dragging shade: dx=$deltaRawX, dy=$deltaRawY")
                        }
                    }
                    return true
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)

                if (isResizing && (pointerId == pointerId1 || pointerId == pointerId2)) {
                    // One of the resizing fingers lifted, stop resizing.
                    isResizing = false
                    resizingSpotlight = null
                    // Reset pointer IDs
                    pointerId1 = MotionEvent.INVALID_POINTER_ID
                    pointerId2 = MotionEvent.INVALID_POINTER_ID
                }
                return true // Consume event
            }

            MotionEvent.ACTION_UP -> {
                val upTime = System.currentTimeMillis()
                val wasDragging = isDragging // Capture state before reset
                val wasResizing = isResizing // Capture state before reset

                // Reset all interaction states
                isDragging = false
                isResizing = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
                pointerId1 = MotionEvent.INVALID_POINTER_ID
                pointerId2 = MotionEvent.INVALID_POINTER_ID
                currentDraggingSpotlight = null
                resizingSpotlight = null

                // Check if this sequence qualifies as a tap
                if (!wasDragging && !wasResizing) {
                    val deltaTime = upTime - downTime
                    val deltaX = (event.x - downX).absoluteValue
                    val deltaY = (event.y - downY).absoluteValue

                    if (deltaTime < tapTimeout && deltaX < touchSlop && deltaY < touchSlop) {
                        // Check if tap was on a control button first
                        if (showControls && handleControlTouch(downX, downY, true)) {
                            return true // Tap handled by controls
                        }

                        // It's a tap. Check if inside a spotlight.
                        val tappedSpotlight = findTouchedSpotlight(downX, downY)
                        if (tappedSpotlight != null) {
                            // --- TAP INSIDE SPOTLIGHT ---
                            performClick() // Accessibility
                            interactionListener?.requestTapPassThrough() // Ask Service to allow pass-through
                            return true // Consume the UP event here, pass-through handled by service flag
                        } else {
                            // --- TAP ON SHADED AREA ---
                            // Toggle controls on shade tap?
                            // showControls = !showControls
                            // invalidate()
                            performClick() // Accessibility
                            return true // Consume tap on shade
                        }
                    }
                }
                // If it was a drag or resize, consume the event
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                // Reset everything on cancel
                isDragging = false
                isResizing = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
                pointerId1 = MotionEvent.INVALID_POINTER_ID
                pointerId2 = MotionEvent.INVALID_POINTER_ID
                currentDraggingSpotlight = null
                resizingSpotlight = null
                return true
            }
        }
        // Default case if no action matches
        return super.onTouchEvent(event)
    }

    // Helper to check and handle taps on control buttons
    private fun handleControlTouch(x: Float, y: Float, isTap: Boolean = false): Boolean {
        if (!showControls) return false

        if (addRect.contains(x.toInt(), y.toInt())) {
            if (isTap && canAddMoreSpotlights) interactionListener?.requestAddNew()
            return true // Consumed touch on add button area
        }
        if (closeRect.contains(x.toInt(), y.toInt())) {
            if (isTap) interactionListener?.requestClose()
            return true // Consumed touch on close button area
        }
        if (shapeRect.contains(x.toInt(), y.toInt())) {
            if (isTap) interactionListener?.requestShapeChange()
            return true // Consumed touch on shape button area
        }
        return false // Touch was not on a control button
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true // Indicate that the click was handled
    }

    private fun findTouchedSpotlight(x: Float, y: Float): Spotlight? {
        for (spotlight in spotlights.reversed()) {
            when (spotlight.shape) {
                Spotlight.Shape.CIRCLE -> {
                    if (hypot(x - spotlight.centerX, y - spotlight.centerY) < spotlight.radius) {
                        return spotlight
                    }
                }
                Spotlight.Shape.OVAL -> {
                    // Check if point is inside ellipse equation: ((x-h)^2 / a^2) + ((y-k)^2 / b^2) <= 1
                    val a = spotlight.width / 2f
                    val b = spotlight.height / 2f
                    if (a > 0 && b > 0) { // Avoid division by zero
                        val term1 = ((x - spotlight.centerX) / a).let { it * it }
                        val term2 = ((y - spotlight.centerY) / b).let { it * it }
                        if (term1 + term2 <= 1) {
                            return spotlight
                        }
                    }
                }
                Spotlight.Shape.SQUARE -> {
                    val halfSize = spotlight.size / 2f
                    if (x >= spotlight.centerX - halfSize && x <= spotlight.centerX + halfSize &&
                        y >= spotlight.centerY - halfSize && y <= spotlight.centerY + halfSize) {
                        return spotlight
                    }
                }
                Spotlight.Shape.RECTANGLE -> {
                    val halfWidth = spotlight.width / 2f
                    val halfHeight = spotlight.height / 2f
                    if (x >= spotlight.centerX - halfWidth && x <= spotlight.centerX + halfWidth &&
                        y >= spotlight.centerY - halfHeight && y <= spotlight.centerY + halfHeight) {
                        return spotlight
                    }
                }
            }
        }
        return null // No spotlight touched
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }

    // Data class to represent a spotlight
    data class Spotlight(
        var centerX: Float,
        var centerY: Float,
        var radius: Float,
        var size: Float = radius * 2, // For squares/rectangles
        var width: Float = radius * 2, // For ovals/rectangles
        var height: Float = radius * 2, // For ovals/rectangles
        var shape: Shape
    ) {
        enum class Shape {
            CIRCLE, OVAL, SQUARE, RECTANGLE
        }
    }
}

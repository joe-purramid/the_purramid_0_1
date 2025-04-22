// SpotlightView.kt
package com.example.purramid.thepurramid.spotlight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.hypot

class SpotlightView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val shadeColor = Color.argb(128, 0, 0, 0) // Charcoal at 50% opacity
    private val shadePaint = Paint().apply { color = shadeColor }
    private val spotlightPaint = Paint() // Paint for the "hole"

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
    private var resizingSpotlightInitialShape: Spotlight.Shape? = null // Added
    private var downX = 0f
    private var downY = 0f
    private var downTime: Long = 0

    private val ovalRect = RectF() // Preallocate RectF for oval drawing

    init {
        // Initialize with one centered circular spotlight
        // Center will be set in onSizeChanged
        spotlights.add(Spotlight(0f, 0f, 100f, shape = Spotlight.Shape.CIRCLE))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Update the initial spotlight's center now that we have the actual dimensions
        if (spotlights.size == 1) {
            spotlights[0].centerX = w / 2f
            spotlights[0].centerY = h / 2f
        }
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
                    ovalRect.left = spotlight.centerX - spotlight.width / 2f
                    ovalRect.top = spotlight.centerY - spotlight.height / 2f
                    ovalRect.right = spotlight.centerX + spotlight.width / 2f
                    ovalRect.bottom = spotlight.centerY + spotlight.height / 2f
                    canvas.drawOval(ovalRect, spotlightPaint)
                }
                Spotlight.Shape.SQUARE -> canvas.drawRect(
                    spotlight.centerX - spotlight.size / 2f,
                    spotlight.centerY - spotlight.size / 2f,
                    spotlight.centerX + spotlight.size / 2f,
                    spotlight.centerY + spotlight.size / 2f,
                    spotlightPaint
                )
                Spotlight.Shape.RECTANGLE -> {
                    ovalRect.left = spotlight.centerX - spotlight.width / 2f
                    ovalRect.top = spotlight.centerY - spotlight.height / 2f
                    ovalRect.right = spotlight.centerX + spotlight.width / 2f
                    ovalRect.bottom = spotlight.centerY + spotlight.height / 2f
                    canvas.drawRect(ovalRect, spotlightPaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
                // Single touch started
                activePointerId = event.getPointerId(0)
                initialTouchX = event.getX(0)
                initialTouchY = event.getY(0)
                currentDraggingSpotlight = findTouchedSpotlight(initialTouchX, initialTouchY)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger touched down
                if (currentDraggingSpotlight == null && event.pointerCount == 2) {
                    isResizing = true
                    pointerId1 = event.getPointerId(0)
                    pointerId2 = event.getPointerId(1)
                    val index1 = event.findPointerIndex(pointerId1)
                    val index2 = event.findPointerIndex(pointerId2)

                    if (index1 != -1 && index2 != -1) {
                        val x1 = event.getX(index1)
                        val y1 = event.getY(index1)
                        val x2 = event.getX(index2)
                        val y2 = event.getY(index2)

                        initialDistance = hypot(x2 - x1, y2 - y1)
                        initialAngle = atan2(y2 - y1, x2 - x1)

                        // Find the touched spotlight (if any) based on the midpoint of the touch
                        val midX = (x1 + x2) / 2f
                        val midY = (y1 + y2) / 2f
                        resizingSpotlight = findTouchedSpotlight(midX, midY)

                        resizingSpotlight?.let {
                            initialSpotlightRadius = it.radius // Set initial radius
                            initialSpotlightSize = it.size // Set initial size
                            initialSpotlightWidth = it.width // Set initial width
                            initialSpotlightHeight = it.height // Set initial height
                            resizingSpotlightInitialShape = it.shape // Set initial shape
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentDraggingSpotlight != null && activePointerId != MotionEvent.INVALID_POINTER_ID && !isResizing) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val newX = event.getX(pointerIndex)
                        val newY = event.getY(pointerIndex)
                        val dx = newX - initialTouchX
                        val dy = newY - initialTouchY

                        // Check if the movement exceeds the touch slop
                        if (!isDragging && hypot(dx, dy) > touchSlop) {
                            isDragging = true
                        }

                        if (isDragging) {
                            currentDraggingSpotlight?.centerX = initialTouchX + dx
                            currentDraggingSpotlight?.centerY = initialTouchY + dy
                            invalidate()
                        }
                    }
                } else if (isResizing && resizingSpotlight != null && pointerId1 != MotionEvent.INVALID_POINTER_ID && pointerId2 != MotionEvent.INVALID_POINTER_ID) {
                    val index1 = event.findPointerIndex(pointerId1)
                    val index2 = event.findPointerIndex(pointerId2)

                    if (index1 != -1 && index2 != -1) {
                        val x1 = event.getX(index1)
                        val y1 = event.getY(index1)
                        val x2 = event.getX(index2)
                        val y2 = event.getY(index2)

                        val currentDistance = hypot(x2 - x1, y2 - y1)
                        val scaleFactor = currentDistance / initialDistance

                        resizingSpotlight?.let {
                            when (currentShape) { // Use the global currentShape for resizing logic
                                Spotlight.Shape.CIRCLE, Spotlight.Shape.OVAL -> {
                                    it.radius = initialSpotlightRadius * scaleFactor
                                    it.width = initialSpotlightWidth * scaleFactor
                                    it.height = initialSpotlightHeight * scaleFactor
                                    // More refined oval shaping based on finger angle could be added here
                                }
                                Spotlight.Shape.SQUARE, Spotlight.Shape.RECTANGLE -> {
                                    it.size = initialSpotlightSize * scaleFactor
                                    it.width = initialSpotlightWidth * scaleFactor
                                    it.height = initialSpotlightHeight * scaleFactor
                                    // More refined rectangle shaping based on finger movement could be added here
                                }
                            }
                            invalidate()
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (isResizing && (pointerId == pointerId1 || pointerId == pointerId2)) {
                    // One of the resizing fingers lifted
                    pointerId1 = MotionEvent.INVALID_POINTER_ID
                    pointerId2 = MotionEvent.INVALID_POINTER_ID
                    resizingSpotlight = null
                    isResizing = false
                }
            }
            MotionEvent.ACTION_UP -> {
                val wasDragging = isDragging // Store the dragging state before resetting

                // Reset dragging state (this should always happen on ACTION_UP)
                activePointerId = MotionEvent.INVALID_POINTER_ID
                currentDraggingSpotlight = null
                isDragging = false

                // Check for a click (tap) only if not dragging or resizing
                if (!wasDragging && !isResizing) {
                    // Check for a click (tap)
                    val upTime = System.currentTimeMillis()
                    val deltaTime = upTime - downTime
                    val deltaX = (event.x - downX).absoluteValue
                    val deltaY = (event.y - downY).absoluteValue

                    if (deltaTime < tapTimeout && deltaX < touchSlop && deltaY < touchSlop) {
                        performClick() // Call performClick when a click is detected
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                currentDraggingSpotlight = null
                isDragging = false
                pointerId1 = MotionEvent.INVALID_POINTER_ID
                pointerId2 = MotionEvent.INVALID_POINTER_ID
                resizingSpotlight = null
                isResizing = false
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true // Indicate that the click was handled
    }

    private fun findTouchedSpotlight(x: Float, y: Float): Spotlight? {
        for (spotlight in spotlights.reversed()) {
            when (spotlight.shape) {
                Spotlight.Shape.CIRCLE, Spotlight.Shape.OVAL -> {
                    val dx = x - spotlight.centerX
                    val dy = y - spotlight.centerY
                    if (hypot(dx, dy) < spotlight.radius.coerceAtLeast(spotlight.width / 2f).coerceAtLeast(spotlight.height / 2f)) {
                        return spotlight
                    }
                }
                Spotlight.Shape.SQUARE, Spotlight.Shape.RECTANGLE -> {
                    val halfWidth = spotlight.width / 2f
                    val halfHeight = spotlight.height / 2f
                    if (x >= spotlight.centerX - halfWidth &&
                        x <= spotlight.centerX + halfWidth &&
                        y >= spotlight.centerY - halfHeight &&
                        y <= spotlight.centerY + halfHeight) {
                        return spotlight
                    }
                }
            }
        }
        return null
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

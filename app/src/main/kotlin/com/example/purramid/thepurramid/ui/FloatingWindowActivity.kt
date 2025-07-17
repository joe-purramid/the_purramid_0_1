package com.example.purramid.thepurramid.ui

import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import com.example.purramid.thepurramid.instance.InstanceManager
import com.example.purramid.thepurramid.util.dpToPx
import javax.inject.Inject
import kotlin.math.abs
import androidx.core.graphics.drawable.toDrawable

abstract class FloatingWindowActivity : AppCompatActivity() {

    protected var instanceId: Int = 0
    private var isDragging = false
    private var isResizing = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val movementThreshold = 10f // Will be converted to pixels
    private var movementThresholdPx = 0f

    @Inject lateinit var instanceManager: InstanceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Convert threshold to pixels
        movementThresholdPx = dpToPx(movementThreshold.toInt()).toFloat()

        // Configure window appearance
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        // Set up window positioning
        setupWindowPosition()

        // Make window draggable and resizable
        setupWindowInteraction()
    }

    private fun setupWindowPosition() {
        val initialX = intent.getIntExtra("WINDOW_X", -1)
        val initialY = intent.getIntExtra("WINDOW_Y", -1)
        val initialWidth = intent.getIntExtra("WINDOW_WIDTH", -1)
        val initialHeight = intent.getIntExtra("WINDOW_HEIGHT", -1)

        window.attributes = window.attributes.apply {
            if (initialX != -1 && initialY != -1) {
                x = initialX
                y = initialY
                gravity = Gravity.TOP or Gravity.START
            }
            if (initialWidth > 0) width = initialWidth
            if (initialHeight > 0) height = initialHeight
        }
    }

    private fun setupWindowInteraction() {
        // Set up touch handling on the root view
        // This will be called after setContentView in the subclass
        window.decorView.post {
            findViewById<View>(android.R.id.content)?.setOnTouchListener { _, event ->
                handleWindowTouch(event)
            }
        }
    }

    private fun handleWindowTouch(event: MotionEvent): Boolean {
        // Don't handle if the touch is on an interactive element
        if (isTouchOnInteractiveElement(event)) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.rawX
                lastTouchY = event.rawY

                val touchPoint = Point(event.x.toInt(), event.y.toInt())
                when {
                    isInResizeZone(touchPoint) -> {
                        isResizing = true
                        isDragging = false
                    }
                    isInDragZone(touchPoint) -> {
                        isDragging = true
                        isResizing = false
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - lastTouchX
                val deltaY = event.rawY - lastTouchY

                // Apply movement threshold
                if (abs(deltaX) < movementThresholdPx &&
                    abs(deltaY) < movementThresholdPx) {
                    return true
                }

                when {
                    isDragging -> moveWindow(deltaX.toInt(), deltaY.toInt())
                    isResizing -> resizeWindow(deltaX.toInt(), deltaY.toInt())
                }

                lastTouchX = event.rawX
                lastTouchY = event.rawY
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging || isResizing) {
                    saveWindowState()
                }
                isDragging = false
                isResizing = false
                return true
            }
        }
        return false
    }

    private fun moveWindow(deltaX: Int, deltaY: Int) {
        val params = window.attributes
        params.x += deltaX
        params.y += deltaY

        // Constrain to screen bounds per Universal Requirements 12.2.1
        val decorView = window.decorView
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        params.x = params.x.coerceIn(0, screenWidth - decorView.width)
        params.y = params.y.coerceIn(0, screenHeight - decorView.height)

        window.attributes = params
    }

    private fun resizeWindow(deltaX: Int, deltaY: Int) {
        val params = window.attributes
        val newWidth = (params.width + deltaX).coerceAtLeast(getMinWidth())
        val newHeight = (params.height + deltaY).coerceAtLeast(getMinHeight())

        // Apply equal scaling per Universal Requirements 12.1.1
        val scaleFactor = minOf(
            newWidth.toFloat() / params.width,
            newHeight.toFloat() / params.height
        )

        params.width = (params.width * scaleFactor).toInt()
        params.height = (params.height * scaleFactor).toInt()

        window.attributes = params
    }

    abstract fun getMinWidth(): Int
    abstract fun getMinHeight(): Int
    abstract fun isTouchOnInteractiveElement(event: MotionEvent): Boolean
    abstract fun isInResizeZone(point: Point): Boolean
    abstract fun isInDragZone(point: Point): Boolean
    abstract fun saveWindowState()

    fun getCurrentWindowBounds(): Rect {
        val location = IntArray(2)
        window.decorView.getLocationOnScreen(location)
        return Rect(
            location[0],
            location[1],
            location[0] + window.decorView.width,
            location[1] + window.decorView.height
        )
    }
}
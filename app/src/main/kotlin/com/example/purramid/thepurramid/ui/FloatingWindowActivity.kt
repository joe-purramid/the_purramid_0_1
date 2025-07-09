package com.example.purramid.thepurramid.ui

import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.purramid.thepurramid.instance.InstanceManager
import com.example.purramid.thepurramid.util.dpToPx
import javax.inject.Inject
import kotlin.math.abs

abstract class FloatingWindowActivity : AppCompatActivity() {

    protected var instanceId: Int = 0
    private var isDragging = false
    private var isResizing = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val movementThreshold = 10f // dp

    @Inject lateinit var instanceManager: InstanceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure window for floating behavior
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Set window flags for pass-through
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )

        // Apply saved window position/size if available
        setupWindowPosition()
    }

    private fun setupWindowPosition() {
        val prefs = getSharedPreferences(getWindowPrefsName(), MODE_PRIVATE)
        val savedX = prefs.getInt("window_${instanceId}_x", -1)
        val savedY = prefs.getInt("window_${instanceId}_y", -1)
        val savedWidth = prefs.getInt("window_${instanceId}_width", -1)
        val savedHeight = prefs.getInt("window_${instanceId}_height", -1)

        if (savedX != -1 && savedY != -1) {
            window.attributes = window.attributes.apply {
                x = savedX
                y = savedY
                gravity = Gravity.TOP or Gravity.START
                if (savedWidth > 0) width = savedWidth
                if (savedHeight > 0) height = savedHeight
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Don't handle if touch is on interactive element
        if (isTouchOnInteractiveElement(event)) {
            return super.onTouchEvent(event)
        }

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
                    else -> {
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
                if (abs(deltaX) < dpToPx(movementThreshold.toInt()) &&
                    abs(deltaY) < dpToPx(movementThreshold.toInt())) {
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

            MotionEvent.ACTION_OUTSIDE -> {
                // Pass through to underlying content
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    private fun moveWindow(deltaX: Int, deltaY: Int) {
        val params = window.attributes
        params.x += deltaX
        params.y += deltaY

        // Constrain to screen bounds
        val displayMetrics = resources.displayMetrics
        params.x = params.x.coerceIn(0, displayMetrics.widthPixels - window.decorView.width)
        params.y = params.y.coerceIn(0, displayMetrics.heightPixels - window.decorView.height)

        window.attributes = params
    }

    private fun resizeWindow(deltaX: Int, deltaY: Int) {
        val params = window.attributes
        params.width = (params.width + deltaX).coerceAtLeast(getMinWidth())
        params.height = (params.height + deltaY).coerceAtLeast(getMinHeight())
        window.attributes = params
    }

    private fun saveWindowState() {
        val prefs = getSharedPreferences(getWindowPrefsName(), MODE_PRIVATE)
        val params = window.attributes
        prefs.edit().apply {
            putInt("window_${instanceId}_x", params.x)
            putInt("window_${instanceId}_y", params.y)
            putInt("window_${instanceId}_width", params.width)
            putInt("window_${instanceId}_height", params.height)
            apply()
        }
    }

    private fun isInResizeZone(point: Point): Boolean {
        val decorView = window.decorView
        val edgeThreshold = dpToPx(20)
        return point.x > decorView.width - edgeThreshold ||
                point.y > decorView.height - edgeThreshold
    }

    protected fun getCurrentWindowBounds(): Rect {
        val location = IntArray(2)
        window.decorView.getLocationOnScreen(location)
        return Rect(
            location[0],
            location[1],
            location[0] + window.decorView.width,
            location[1] + window.decorView.height
        )
    }

    abstract fun getMinWidth(): Int
    abstract fun getMinHeight(): Int
    abstract fun isTouchOnInteractiveElement(event: MotionEvent): Boolean
    abstract fun getWindowPrefsName(): String
}
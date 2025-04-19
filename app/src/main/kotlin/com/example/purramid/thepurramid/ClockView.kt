// ClockView.kt (Refactored - Step 2: Time State/Ticking Logic Removed)
package com.example.purramid.thepurramid // Use your package name

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.drawable.DrawableCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.caverock.androidsvg.SVGImageView
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
// import java.util.TimeZone // REMOVED unless used elsewhere
import kotlin.math.*

class ClockView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // --- Internal State (Configurable via setters) ---
    private var isAnalog: Boolean = false
    private var clockColor: Int = Color.WHITE
    private var is24Hour: Boolean = false
    private var timeZoneId: ZoneId = ZoneId.systemDefault() // Still needed for formatting
    private var displaySeconds: Boolean = true

    // --- State for Display ---
    private var displayedTime: LocalTime = LocalTime.MIN // Holds the last time received

    // --- Views for Analog Mode (External - TODO: Review this approach later) ---
    private var clockFaceImageView: SVGImageView? = null
    private var hourHandImageView: SVGImageView? = null
    private var minuteHandImageView: SVGImageView? = null
    private var secondHandImageView: SVGImageView? = null

    // --- Time Formatter (Configured by setters) ---
    private lateinit var timeFormatter: DateTimeFormatter

    // --- Drawing Tools ---
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 48f, resources.displayMetrics)
    }
    private val bounds = Rect() // Needed for digital text bounds

    // --- Touch Handling State (Keep for now, review/move later) ---
    private var touchStartX: Float = 0f
    private var touchStartY: Float = 0f
    private var lastTouchAngle: Float = 0f
    private var currentlyMovingHand: Hand? = null
    private var initialHandAngle: Float = 0f
    private var totalSecondRotation: Int = 0
    private var totalMinuteRotation: Int = 0

    enum class Hand {
        HOUR, MINUTE, SECOND
    }

    // --- Initialization ---
    init {
        // Initialize defaults needed before setters are called
        updateTimeFormat() // Initialize timeFormatter based on default state
        setPaintColors() // Set initial paint colors based on default state

        // Set touch listener (logic to be reviewed/moved later)
        setOnTouchListener(handTouchListener)
    }

    // --- Public Method for Updating Display ---
    // Called externally by the managing service.
    fun updateDisplayTime(timeToDisplay: LocalTime) {
        this.displayedTime = timeToDisplay
        if (isAnalog) {
            updateAnalogHands() // Update SVG rotations
        } else {
            invalidate() // Triggers onDraw for digital
        }
    }


    // --- Public Configuration Methods ---

    fun setClockMode(isAnalogMode: Boolean) {
        if (isAnalog != isAnalogMode) {
            isAnalog = isAnalogMode
            updateTimeFormat()
            updateAnalogViewVisibility() // Crucial for switching modes
            invalidate()
        }
    }

    fun setClockColor(color: Int) {
        if (clockColor != color) {
            clockColor = color
            setPaintColors()
            invalidate()
        }
    }

    fun setIs24HourFormat(is24: Boolean) {
        if (is24Hour != is24) {
            is24Hour = is24
            updateTimeFormat()
            updateNumberVisibility()
            invalidate()
        }
    }

    fun setClockTimeZone(zoneId: ZoneId) {
        if (this.timeZoneId != zoneId) {
            this.timeZoneId = zoneId
            updateTimeFormat() // No need to update time immediately here; Service will send next update
            invalidate()
        }
    }

    fun setDisplaySeconds(display: Boolean) {
        if (displaySeconds != display) {
            displaySeconds = display
            updateTimeFormat()
            if (isAnalog) {
                secondHandImageView?.visibility = if (display) View.VISIBLE else View.GONE
            }
            invalidate()
        }
    }

    // --- Internal Helper Methods (Remain mostly the same) ---

    private fun setPaintColors() { // Remains the same
        val handColor = getHandColorForBackground(clockColor)
        textPaint.color = handColor

        // Update external SVG colors if they are being used
        if (clockFaceImageView != null) {
            updateAnalogColors()
        }
        secondHandImageView?.visibility = if (isAnalog && displaySeconds) View.VISIBLE else View.GONE
    }

    private fun getHandColorForBackground(backgroundColor: Int): Int { // Remains the same
        val luminance = Color.luminance(backgroundColor)
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }

    private fun updateTimeFormat() { // Remains the same
        val locale = Locale.getDefault()
        val basePattern = if (is24Hour) "HH:mm" else "hh:mm"
        val patternWithSeconds = if (is24Hour) "HH:mm:ss" else "hh:mm:ss"
        timeFormatter = DateTimeFormatter
            .ofPattern(if (displaySeconds) patternWithSeconds else basePattern, locale)
            .withZone(timeZoneId)
    }


    // --- Drawing Logic (Using displayedTime) ---

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isAnalog) {
           // Only draw if digital mode is active
            drawDigitalClock(canvas)
        }
        // If analog, do nothing here; drawing is handled by external SVGImageViews
    }

    private fun drawDigitalClock(canvas: Canvas) {
        // --- Uses this.displayedTime instead of internal currentTime ---
        val formattedTime = displayedTime.format(timeFormatter) // Format the received time

        canvas.drawColor(clockColor)
        textPaint.getTextBounds(formattedTime, 0, formattedTime.length, bounds)
        val x = width / 2f
        val y = height / 2f - bounds.exactCenterY()
        canvas.drawText(formattedTime, x, y, textPaint)

        // Draw AM/PM if needed (uses displayedTime)
        if (!is24Hour) {
            val amPmFormatter = DateTimeFormatter.ofPattern("a", Locale.getDefault())
            val amPmString = displayedTime.format(amPmFormatter)
            val amPmPaint = Paint(textPaint).apply { textSize *= 0.4f }
            val amPmTextWidth = amPmPaint.measureText(amPmString)
            // Position AM/PM to the right...
            val amPmX = x + (bounds.width() / 2f) + amPmTextWidth / 2f + dpToPx(4) // Added padding
            val amPmY = y
            canvas.drawText(amPmString, amPmX, amPmY, amPmPaint)
        }
    }

    // --- External SVG Handling  ---

    fun setAnalogImageViews( // Remains the same
        clockFace: SVGImageView?, hourHand: SVGImageView?, minuteHand: SVGImageView?, secondHand: SVGImageView?
    ) {
        this.clockFaceImageView = clockFace
        this.hourHandImageView = hourHand
        this.minuteHandImageView = minuteHand
        this.secondHandImageView = secondHand
        updateAnalogViewVisibility()
        updateNumberVisibility()
        updateAnalogColors()
        updateAnalogHands() // Uses displayedTime now
    }

    private fun updateAnalogViewVisibility() { 
        val visibility = if (isAnalog) View.VISIBLE else View.GONE
        clockFaceImageView?.visibility = visibility
        hourHandImageView?.visibility = visibility
        minuteHandImageView?.visibility = visibility
        secondHandImageView?.visibility = if (isAnalog && displaySeconds) View.VISIBLE else View.GONE
    }

    private fun updateAnalogColors() { // Remains the same (needs review later)
        clockFaceImageView?.let { /* ... tinting logic ... */ }
        Log.w("ClockView", "SVG Color Tinting needs review/implementation")
    }

    private fun updateAnalogHands() {
        // --- Updates external SVG rotations ---
        if (isAnalog && clockFaceImageView != null) { // Check if in analog mode and SVGs exist
            val hour = displayedTime.hour
            val minute = displayedTime.minute
            val second = displayedTime.second

            val hourAngle = (hour % 12 + minute / 60f) * 30f
            val minuteAngle = (minute + second / 60f) * 6f
            val secondAngle = second * 6f

            hourHandImageView?.rotation = hourAngle
            minuteHandImageView?.rotation = minuteAngle
            secondHandImageView?.rotation = secondAngle
        }
    }

    private fun updateNumberVisibility() { 
        clockFaceImageView?.svg?.let { svg ->
              try {
                  val twelveHourGroup = svg.getElementById("twelveHourNumbers")
                  val twentyFourHourGroup = svg.getElementById("twentyFourHourNumbers")
                  twelveHourGroup?.setAttribute("visibility", if (is24Hour) "hidden" else "visible")
                  twentyFourHourGroup?.setAttribute("visibility", if (is24Hour) "visible" else "hidden")
                  clockFaceImageView?.invalidate()
                  Log.d("ClockView", "Updated SVG number visibility for 24h=$is24Hour")
              } catch (e: Exception) { Log.e("ClockView", "Error accessing SVG elements", e) }
          }
    }

    // --- Touch Handling Logic (Keep for now, review/move later) ---
    // NOTE: Calls to pauseTime()/playTime() inside WILL cause errors now.
    // We will fix this when refactoring touch handling. Add TODOs.
    private val handTouchListener = OnTouchListener { v, event ->
        if (!isAnalog) { return@OnTouchListener false }
        val centerX = width / 2f
        val centerY = height / 2f
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchAngle = calculateAngle(centerX, centerY, x, y)
                // ... (Hit detection logic remains, find currentlyMovingHand) ...
                if (currentlyMovingHand != null) {
                    // TODO: Refactor hand dragging - cannot call pauseTime() anymore
                    // pauseTime() // Error - method removed
                    Log.w("ClockView", "TODO: Implement hand drag start without pauseTime()")
                    lastTouchAngle = touchAngle
                    return@OnTouchListener true
                } else {
                    // Assume window drag start (logic to move to Service)
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    return@OnTouchListener true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentlyMovingHand != null) {
                    // Hand Dragging Logic (placeholder rotation remains)
                    val currentTouchAngle = calculateAngle(centerX, centerY, x, y)
                    var deltaAngle = angleDifference(currentTouchAngle, lastTouchAngle)
                    // TODO: Calculate time change based on deltaAngle and maybe call a listener?
                    when (currentlyMovingHand) { /* ... rotate views ... */ }
                    lastTouchAngle = currentTouchAngle
                    return@OnTouchListener true
                } else {
                    // Window Dragging Logic (to move to Service)
                    // ... (log or no-op for now) ...
                    return@OnTouchListener true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentlyMovingHand != null) {
                    // End of hand drag
                    // TODO: Refactor hand drag end - cannot call playTime() anymore
                    // playTime() // Error - method removed
                    Log.w("ClockView", "TODO: Implement hand drag end")
                    currentlyMovingHand = null
                    return@OnTouchListener true
                } else {
                    // End of window drag (logic belongs in Service)
                    return@OnTouchListener true
                }
            }
        }
        return@OnTouchListener false
    }

    // --- Touch Helper Functions ---
    private fun calculateAngle(centerX: Float, centerY: Float, x: Float, y: Float): Float { /* ... */ }
    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float { /* ... */ }
    private fun angleDifference(angle1: Float, angle2: Float): Float { /* ... */ }


    // --- Lifecycle ---
    override fun onDetachedFromWindow() { // Cleaned up
        super.onDetachedFromWindow()
    }

    // Utility function for digital AM/PM text positioning (if needed)
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }

}
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
import kotlin.math.*

class ClockView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // --- Internal State (Configurable via setters) ---
    private var clockId: Int = -1
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
    private var lastTouchAngle: Float = 0f
    private var currentlyMovingHand: Hand? = null
    private var initialHandAngle: Float = 0f
    var interactionListener: ClockInteractionListener? = null

    enum class Hand {
        HOUR, MINUTE, SECOND
    }

    interface ClockInteractionListener {
        fun onTimeManuallySet(clockId: Int, newTime: LocalTime)
        fun onDragStateChanged(clockId: Int, isDragging: Boolean)
    }

    fun setClockId(id: Int) {
        this.clockId = id
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
        if (!isAnalog) {
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

    // Calculate LocalTime based on current rotation angles of the hands
    private fun calculateTimeFromAngles(): LocalTime {
        // Ensure angles are positive within 0-360 range
        val rawHourAngle = (hourHandImageView?.rotation ?: 0f).mod(360f)
        val rawMinuteAngle = (minuteHandImageView?.rotation ?: 0f).mod(360f)
        val rawSecondAngle = (secondHandImageView?.rotation ?: 0f).mod(360f)

        // --- Convert angles to time components ---
        // Note: This is a simplified conversion. A full conversion might need more precision
        // or consider how minute/second changes affect hour/minute angles slightly.

        // Seconds: 6 degrees per second
        val seconds = round(rawSecondAngle / 6f).toInt().mod(60)

        // Minutes: 6 degrees per minute (influenced slightly by seconds, ignore for simplicity here)
        val minutes = round(rawMinuteAngle / 6f).toInt().mod(60)

        // Hours: 30 degrees per hour (influenced by minutes)
        // Calculate the base hour from the angle, considering the minute influence
        // Each minute moves the hour hand 0.5 degrees (30 degrees / 60 minutes)
        val hourContributionFromMinutes = minutes * 0.5f
        val adjustedHourAngle = rawHourAngle - hourContributionFromMinutes
        var hours = round(adjustedHourAngle / 30f).toInt() // Hour value (0-11)

        // Handle potential wrap-around and convert to 1-12 for calculation ease
        hours = if (hours <= 0) hours + 12 else hours

        // TODO: Determine AM/PM if needed, or handle 24h conversion based on state?
        // This calculation primarily gives 1-12 hour format. Service might need to adjust based on context.
        // For now, just create LocalTime, assuming AM/PM is handled elsewhere or based on rough hour estimate.
        // A more robust solution might track total rotation or use the last known displayedTime as a base.

        // Let's try to construct a 24-hour time if possible based on the last displayed time
        // (This adds complexity back, maybe the Service should handle this?)
        // Simplified: Assume the resulting time is within the same 12-hour block as displayedTime or default to 24h format
        var hour24 = hours
        if (!is24Hour) { // If we are displaying 12h format, infer AM/PM based on last time
            if (displayedTime.hour >= 12 && hour24 < 12) hour24 += 12 // If last time was PM and new hour is 1-11, make it PM
            else if (displayedTime.hour < 12 && hour24 == 12) hour24 = 0 // Handle 12 AM case
        } else {
            // If in 24h mode, need a better way to know if we crossed midnight/noon
            // Simplification: Use the 1-12 result, assume Service knows context.
            // Let's stick to 0-23 based on angle, roughly. Need refinement.
            hour24 = round(adjustedHourAngle / 30f).toInt().mod(24) // This is likely incorrect way to get 24h
        }

        // Create LocalTime (handle potential calculation errors leading to invalid values)
        return try {
            // Use the simplified 1-12 hour conversion for now, service can refine
            val finalHour = round(rawHourAngle / 30f).toInt().mod(12).let { if (it == 0) 12 else it }
            // Basic AM/PM guess (needs improvement)
            val finalHour24 = if (displayedTime.hour >= 12 && finalHour < 12 ) finalHour + 12 else if (displayedTime.hour < 12 && finalHour == 12) 0 else finalHour
            // Ensure values are within range
            LocalTime.of(finalHour24.coerceIn(0, 23), minutes, seconds)
        } catch (e: Exception) {
            Log.e("ClockView", "Error calculating time from angles, returning previous time", e)
            displayedTime // Return last known time on error
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
        // Only handle touch if in analog mode with SVGs available
        if (!isAnalog || clockFaceImageView == null || hourHandImageView == null || minuteHandImageView == null || clockId == -1) {
            return@OnTouchListener false
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val x = event.x
        val y = event.y
        val radius = min(centerX, centerY) * 0.8f // Approximate radius

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchAngle = calculateAngle(centerX, centerY, x, y)
                val dist = distance(centerX, centerY, x, y)

                // Hit detection (Get current angles)
                val hourAngle = hourHandImageView?.rotation ?: 0f
                val minuteAngle = minuteHandImageView?.rotation ?: 0f
                val secondAngle = secondHandImageView?.rotation ?: 0f
                val angleThreshold = 15f // Tolerance

                currentlyMovingHand = null // Reset
                // Check second hand first if displayed
                if (displaySeconds && secondHandImageView?.isVisible == true &&
                    abs(angleDifference(touchAngle, secondAngle)) < angleThreshold && dist > radius * 0.4f) {
                    currentlyMovingHand = Hand.SECOND
                } else if (abs(angleDifference(touchAngle, minuteAngle)) < angleThreshold && dist > radius * 0.3f) {
                    currentlyMovingHand = Hand.MINUTE
                } else if (abs(angleDifference(touchAngle, hourAngle)) < angleThreshold && dist > radius * 0.2f) {
                    currentlyMovingHand = Hand.HOUR
                }
                // --- End Hit Detection ---

                if (currentlyMovingHand != null) {
                    Log.d("ClockView", "Hand drag started: $currentlyMovingHand")
                    lastTouchAngle = touchAngle
                    initialHandAngle = when(currentlyMovingHand) { // Store initial angle if needed later
                        Hand.HOUR -> hourAngle
                        Hand.MINUTE -> minuteAngle
                        Hand.SECOND -> secondAngle
                    }
                    interactionListener?.onDragStateChanged(clockId, true) // Notify listener: drag started
                    return@OnTouchListener true
                } else {
                    return@OnTouchListener false // Allow window drag
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentlyMovingHand != null) {
                    val currentTouchAngle = calculateAngle(centerX, centerY, x, y)
                    val deltaAngle = angleDifference(currentTouchAngle, lastTouchAngle)

                    // Apply visual rotation directly for immediate feedback
                    when (currentlyMovingHand) {
                        Hand.HOUR -> hourHandImageView?.rotation = (hourHandImageView?.rotation ?: 0f) + deltaAngle
                        Hand.MINUTE -> minuteHandImageView?.rotation = (minuteHandImageView?.rotation ?: 0f) + deltaAngle
                        Hand.SECOND -> secondHandImageView?.rotation = (secondHandImageView?.rotation ?: 0f) + deltaAngle
                    }
                    lastTouchAngle = currentTouchAngle
                    return@OnTouchListener true // Consume event: dragging hand
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentlyMovingHand != null) {
                    Log.d("ClockView", "Hand drag ended: $currentlyMovingHand")
                    // Calculate the final time based on the final angles
                    val finalTime = calculateTimeFromAngles()
                    // Notify the listener (Service) about the manually set time
                    interactionListener?.onTimeManuallySet(clockId, finalTime)
                    interactionListener?.onDragStateChanged(clockId, false) // Notify listener: drag ended
                    currentlyMovingHand = null // Reset dragging state
                    return@OnTouchListener true // Consume event: end hand drag
                }
            }
        }
        // If no conditions met or not consumed above
        return@OnTouchListener false
    }

    // --- Time Calculation Helper function ---
    // Calculates the LocalTime based on the current rotation angles of the hand ImageViews.
     // Provides a basic 12-hour interpretation; Service needs to handle 24h context.
    private fun calculateTimeFromAngles(): LocalTime {
        // Ensure angles are positive within 0-360 range
        val rawHourAngle = (hourHandImageView?.rotation ?: 0f).mod(360f)
        val rawMinuteAngle = (minuteHandImageView?.rotation ?: 0f).mod(360f)
        val rawSecondAngle = (secondHandImageView?.rotation ?: 0f).mod(360f)

        // Convert angles to time components (simplified 12-hour based calculation)
        val seconds = round(rawSecondAngle / 6f).toInt().mod(60)
        val minutes = round(rawMinuteAngle / 6f).toInt().mod(60)

        // Hour calculation needs care due to minute influence and 12 vs 24 ambiguity
        // Get hour component purely from hour hand angle (0-11.99~)
        val hourComponent = rawHourAngle / 30f
        // Let's round for now, Service needs to determine AM/PM or true 24h context.
        var hour12 = round(hourComponent).toInt().mod(12)
        if (hour12 == 0) hour12 = 12 // Display 12 instead of 0

        // Construct a time. Defaulting to current time's AM/PM for hour resolution.
        // Service MUST refine this based on context.
        var hour24 = hour12
        if (displayedTime.hour >= 12 && hour12 != 12) { // If currently PM, assume new time is PM unless it's 12
            hour24 += 12
        } else if (displayedTime.hour < 12 && hour12 == 12) { // Handle 12 AM case
            hour24 = 0
        }
        hour24 = hour24.coerceIn(0, 23) // Ensure valid range

        return try {
            LocalTime.of(hour24, minutes, seconds)
        } catch (e: Exception) {
            Log.e("ClockView", "Error creating LocalTime from angles, returning previous", e)
            displayedTime // Fallback to last known time
        }
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
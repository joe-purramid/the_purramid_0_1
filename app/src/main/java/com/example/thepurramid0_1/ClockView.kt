// ClockView.kt
package com.example.thepurramid0_1

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGImageView
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

class ClockView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var currentTime: LocalTime = LocalTime.now(ZoneId.systemDefault())
    private var isPaused: Boolean = false
    private var pauseTimeOffset: Long = 0L // Offset from system time when paused
    private var isAnalog: Boolean = false // Default to digital
    private var clockColor: Int = Color.WHITE //  Default clock face color
    private var is24Hour: Boolean = false // New setting for 24-hour format
    private var timeZoneId: String = ZoneId.systemDefault().id
    private var displaySeconds: Boolean = true // Default to show seconds
    private var clockId: Int = -1

    private var clockFaceImageView: SVGImageView? = null
    private var hourHandImageView: SVGImageView? = null
    private var minuteHandImageView: SVGImageView? = null
    private var secondHandImageView: SVGImageView? = null

    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timeFormatter: DateTimeFormatter
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 48f, resources.displayMetrics)
    }
    private val analogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val secondHandAnalogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val bounds = Rect()
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!isPaused) {
                val zoneId = ZoneId.of(timeZoneId)
                currentTime = LocalTime.now(zoneId)
            }
            if (isAnalog) {
                updateAnalogHands()
            } else {
                invalidate() // Request a redraw for digital
            }
            handler.postDelayed(this, 1000) // Update every second
        }
    }

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

    init {
        sharedPreferences = context.getSharedPreferences("clock_settings", Context.MODE_PRIVATE)
        loadInitialClockSettings()
        handler.post(tickRunnable)
        setOnTouchListener(clockTouchListener) // Set listener for clock face movement
    }

   fun setWindowManagerAndLayoutParams(wm: WindowManager, lp: WindowManager.LayoutParams) {
        windowManager = wm
        layoutParams = lp
    }

    private val clockTouchListener = OnTouchListener { v, event ->
        val centerX = width / 2f
        val centerY = height / 2f
        val x = event.x
        val y = event.y
        val distanceToCenter = sqrt((x - centerX).pow(2) + (y - centerY).pow(2))
        val clockRadius = min(centerX, centerY) * 0.8f // Approximate clock face radius

        // Only allow clock movement if the touch is not on a hand (approximate check)
        val isTouchingHand = currentlyMovingHand != null ||
                (hourHandImageView?.isVisible == true && isPointOnHand(x, y, centerX, centerY, hourHandImageView?.rotation ?: 0f, 0.5f * clockRadius)) ||
                (minuteHandImageView?.isVisible == true && isPointOnHand(x, y, centerX, centerY, minuteHandImageView?.rotation ?: 0f, 0.7f * clockRadius)) ||
                (secondHandImageView?.isVisible == true && displaySeconds && isPointOnHand(x, y, centerX, centerY, secondHandImageView?.rotation ?: 0f, 0.85f * clockRadius))

        if (!isAnalog || isTouchingHand) {
            return@OnTouchListener handTouchListener.onTouch(v, event) // Delegate to hand touch listener
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                // Move the clock overlay
                layoutParams?.let { lp ->
                    lp.x += dx.toInt()
                    lp.y += dy.toInt()
                    windowManager?.updateViewLayout(this@ClockView, lp)
                }
                touchStartX = event.x
                touchStartY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Handle end of drag if needed
            }
        }
        true
    }

    private val handTouchListener = OnTouchListener { v, event ->
        if (!isAnalog || clockFaceImageView == null || hourHandImageView == null || minuteHandImageView == null) {
            return@OnTouchListener false
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val hourAngle = hourHandImageView?.rotation ?: 0f
                val minuteAngle = minuteHandImageView?.rotation ?: 0f
                val secondAngle = secondHandImageView?.rotation ?: 0f

                if (isPointOnHand(x, y, centerX, centerY, hourAngle, min(centerX, centerY) * 0.5f)) {
                    currentlyMovingHand = Hand.HOUR
                } else if (isPointOnHand(x, y, centerX, centerY, minuteAngle, min(centerX, centerY) * 0.7f)) {
                    currentlyMovingHand = Hand.MINUTE
                } else if (displaySeconds && isPointOnHand(x, y, centerX, centerY, secondAngle, min(centerX, centerY) * 0.85f)) {
                    currentlyMovingHand = Hand.SECOND
                }

                if (currentlyMovingHand != null) {
                    pauseTime()
                    lastTouchAngle = calculateAngle(centerX, centerY, x, y)
                    initialHandAngle = when (currentlyMovingHand) {
                        Hand.HOUR -> hourAngle
                        Hand.MINUTE -> minuteAngle
                        Hand.SECOND -> secondAngle
                        null -> 0f
                    }
                    return@OnTouchListener true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentlyMovingHand != null) {
                    val currentTouchAngle = calculateAngle(centerX, centerY, x, y)
                    val deltaAngle = currentTouchAngle - lastTouchAngle

                    // Prevent immediate movement due to slight shaking
                    val angleDifference = abs(deltaAngle)
                    val sensitivityThreshold = 5f // Adjust as needed
                    if (angleDifference > sensitivityThreshold) {
                        when (currentlyMovingHand) {
                            Hand.HOUR -> {
                                hourHandImageView?.rotation = (hourHandImageView?.rotation ?: 0f) + deltaAngle
                            }
                            Hand.MINUTE -> {
                                minuteHandImageView?.rotation = (minuteHandImageView?.rotation ?: 0f) + deltaAngle
                                // Handle minute affecting hour on full rotation (forward)
                                val currentMinuteRotation = (minuteHandImageView?.rotation ?: 0f) / 360f
                                val deltaMinuteRotation = currentMinuteRotation.toInt() - totalMinuteRotation
                                if (deltaMinuteRotation > 0) {
                                    hourHandImageView?.rotation = (hourHandImageView?.rotation ?: 0f) + (30f * deltaMinuteRotation)
                                    totalMinuteRotation += deltaMinuteRotation
                                }
                                // Handle minute affecting hour on full rotation (backward)
                                val backwardDeltaMinuteRotation = totalMinuteRotation - currentMinuteRotation.toInt()
                                if (backwardDeltaMinuteRotation > 0) {
                                    hourHandImageView?.rotation = (hourHandImageView?.rotation ?: 0f) - (30f * backwardDeltaMinuteRotation)
                                    totalMinuteRotation -= backwardDeltaMinuteRotation
                                }
                            }
                            Hand.SECOND -> {
                                secondHandImageView?.rotation = (secondHandImageView?.rotation ?: 0f) + deltaAngle
                                // Handle second affecting minute on full rotation (forward)
                                val currentSecondRotation = (secondHandImageView?.rotation ?: 0f) / 360f
                                val deltaSecondRotation = currentSecondRotation.toInt() - totalSecondRotation
                                if (deltaSecondRotation > 0) {
                                    minuteHandImageView?.rotation = (minuteHandImageView?.rotation ?: 0f) + (6f * deltaSecondRotation)
                                    totalSecondRotation += deltaSecondRotation
                                }
                                // Handle second affecting minute on full rotation (backward)
                                val backwardDeltaSecondRotation = totalSecondRotation - currentSecondRotation.toInt()
                                if (backwardDeltaSecondRotation > 0) {
                                    minuteHandImageView?.rotation = (minuteHandImageView?.rotation ?: 0f) - (6f * backwardDeltaSecondRotation)
                                    totalSecondRotation -= backwardDeltaSecondRotation
                                }
                            }
                            null -> {}
                        }
                        lastTouchAngle = currentTouchAngle
                    }
                    return@OnTouchListener true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentlyMovingHand != null) {
                    // Snap the hand to the angle of the finger when released
                    val finalTouchAngle = calculateAngle(centerX, centerY, x, y)
                    when (currentlyMovingHand) {
                        Hand.HOUR -> hourHandImageView?.rotation = finalTouchAngle
                        Hand.MINUTE -> minuteHandImageView?.rotation = finalTouchAngle
                        Hand.SECOND -> secondHandImageView?.rotation = finalTouchAngle
                        null -> {}
                    }
                    currentlyMovingHand = null
                    totalSecondRotation = (secondHandImageView?.rotation ?: 0f) / 360f.toInt()
                    totalMinuteRotation = (minuteHandImageView?.rotation ?: 0f) / 360f.toInt()
                    return@OnTouchListener true
                }
            }
        }
        return@OnTouchListener false
    }

    private fun calculateAngle(centerX: Float, centerY: Float, x: Float, y: Float): Float {
        val angle = Math.toDegrees(atan2(y - centerY, x - centerX).toDouble()).toFloat()
        return (angle + 90 + 360) % 360 // Adjust to clock-wise 12 o'clock start
    }

    private fun isPointOnHand(x: Float, y: Float, centerX: Float, centerY: Float, handRotation: Float, handLength: Float): Boolean {
        val handAngleRad = Math.toRadians((handRotation - 90).toDouble()) // Convert to standard angle
        val handEndX = centerX + cos(handAngleRad) * handLength
        val handEndY = centerY + sin(handAngleRad) * handLength

        // Approximate the hand as a line segment with some thickness
        val distance = pointToLineDistance(x, y, centerX, centerY, handEndX, handEndY)
        return distance < TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics) // Adjust thickness
    }

    private fun pointToLineDistance(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val segmentLengthSq = ((x2 - x1).pow(2) + (y2 - y1).pow(2))
        if (segmentLengthSq == 0f) return sqrt((px - x1).pow(2) + (py - y1).pow(2))
        val t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / segmentLengthSq
        val clampedT = max(0f, min(1f, t))
        val closestX = x1 + clampedT * (x2 - x1)
        val closestY = y1 + clampedT * (y2 - y1)
        return sqrt((px - closestX).pow(2) + (py - closestY).pow(2))
    }

    private fun loadInitialClockSettings() {
        val mode = sharedPreferences.getString("clock_${clockId}_mode", "digital") ?: "digital"
        isAnalog = mode == "analog"
        clockColor = sharedPreferences.getInt("clock_${clockId}_color", Color.WHITE)
        is24Hour = sharedPreferences.getBoolean("clock_${clockId}_24hour", false) // Load 24-hour setting
        timeZoneId = sharedPreferences.getString("clock_${clockId}_time_zone_id", ZoneId.systemDefault().id) ?: ZoneId.systemDefault().id
        displaySeconds = sharedPreferences.getBoolean("clock_${clockId}_display_seconds", true) // Default to true
        updateTimeFormat() // Initialize timeFormat based on settings
        setPaintColors()
    }

    private fun setPaintColors() {
        val handColor = getHandColorForBackground(clockColor)
        textPaint.color = handColor
        analogPaint.color = handColor
        secondHandAnalogPaint.color = handColor
        // For analog using ImageViews, colors are set in updateAnalogColors
        if (clockFaceImageView != null) {
            updateAnalogColors()
            secondHandImageView?.isVisible = displaySeconds
        }
    }

    private fun getHandColorForBackground(backgroundColor: Int): Int {
        return when (backgroundColor) {
            Color.WHITE -> Color.BLACK
            Color.BLACK -> Color.WHITE
            0xFFDAA520.toInt() -> Color.BLACK // Goldenrod
            0xFF03DAC5.toInt() -> Color.BLACK // Teal
            0xFFADD8E6.toInt() -> Color.BLACK // Light Blue
            0xFFEE82EE.toInt() -> Color.WHITE // Violet
            else -> Color.BLACK // Default
        }
    }

    private fun updateTimeFormat() {
        timeFormatter = if (isAnalog) {
            DateTimeFormatter.ofPattern(if (displaySeconds) if (is24Hour) "HH:mm:ss" else "hh:mm:ss" else if (is24Hour) "HH:mm" else "hh:mm", Locale.getDefault())
    } else {
            if (displaySeconds) DateTimeFormatter.ofLocalizedTime(if (is24Hour) java.time.format.FormatStyle.MEDIUM else java.time.format.FormatStyle.MEDIUM).withLocale(Locale.getDefault())
            else DateTimeFormatter.ofLocalizedTime(if (is24Hour) java.time.format.FormatStyle.SHORT else java.time.format.FormatStyle.SHORT).withLocale(Locale.getDefault())
        }.withZone(ZoneId.of(timeZoneId))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isAnalog && clockFaceImageView == null) {
            drawAnalogClock(canvas)
        } else if (!isAnalog) {
            drawDigitalClock(canvas)
        }
        // If analog with ImageViews, drawing is handled by them
    }

    private fun drawAnalogClock(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(centerX, centerY) * 0.8f
        val now = LocalTime.now(ZoneId.of(timeZoneId))
        val hour = now.hour % 12 // 12-hour format for analog display
        val minute = now.minute
        val second = now.second

        // Draw clock face
        analogPaint.style = Paint.Style.STROKE
        analogPaint.strokeWidth = 5f
        canvas.drawCircle(centerX, centerY, radius, analogPaint)

        // Draw hour ticks
        for (i in 0..11) {
            val angle = Math.toRadians((i * 30 - 90).toDouble()).toFloat()
            val startX = centerX + radius * 0.8f * cos(angle)
            val startY = centerY + radius * 0.8f * sin(angle)
            val endX = centerX + radius * 0.9f * cos(angle)
            val endY = centerY + radius * 0.9f * sin(angle)
            canvas.drawLine(startX, startY, endX, endY, analogPaint.apply { strokeWidth = 3f })

            // Draw 24-hour inner ticks and numbers
            if (is24Hour) {
                val innerRadius = radius * 0.6f
                if (i % 3 == 0 && i != 0) { // Draw numbers at 3, 6, 9, 12 for inner ring
                    val hour24 = i + 12
                    val angle24 = Math.toRadians((i * 30 - 90).toDouble()).toFloat()
                    val textX = centerX + innerRadius * 0.7f * cos(angle24)
                    val textY = centerY + innerRadius * 0.7f * sin(angle24) + bounds.height() / 4f // Adjust for text height
                    textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
                    canvas.drawText(hour24.toString(), textX, textY, textPaint)
                    textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 48f, resources.displayMetrics) // Reset text size
                }
            }
        }

        // Draw minute ticks
        for (i in 0..59) {
            if (i % 5 != 0) {
                val angle = Math.toRadians((i * 6 - 90).toDouble()).toFloat()
                val startX = centerX + radius * 0.85f * cos(angle)
                val startY = centerY + radius * 0.85f * sin(angle)
                val endX = centerX + radius * 0.88f * cos(angle)
                val endY = centerY + radius * 0.88f * sin(angle)
                canvas.drawLine(startX, startY, endX, endY, analogPaint.apply { strokeWidth = 1f })
            }
        }

        // Draw hands
        val hourAngle = (hour + minute / 60f) * 30f - 90f
        val minuteAngle = (minute + second / 60f) * 6f - 90f
        val secondAngle = second * 6f - 90f

        val hourHandLength = radius * 0.5f
        canvas.drawLine(centerX, centerY, centerX + cos(Math.toRadians(hourAngle)) * hourHandLength, centerY + sin(Math.toRadians(hourAngle)) * hourHandLength, analogPaint.apply { strokeWidth = 8f })

        val minuteHandLength = radius * 0.7f
        canvas.drawLine(centerX, centerY, centerX + cos(Math.toRadians(minuteAngle)) * minuteHandLength, centerY + sin(Math.toRadians(minuteAngle)) * minuteHandLength, analogPaint.apply { strokeWidth = 6f })

        if (displaySeconds) {
            val secondHandLength = radius * 0.85f
            canvas.drawLine(centerX, centerY, centerX + cos(Math.toRadians(secondAngle)) * secondHandLength, centerY + sin(Math.toRadians(secondAngle)) * secondHandLength, secondHandAnalogPaint)
        }
    }

    private fun drawDigitalClock(canvas: Canvas) {
        val formattedTime = currentTime.format(timeFormatter)
        val amPmFormatter = DateTimeFormatter.ofPattern("a", Locale.getDefault())
        val amPmString = currentTime.format(amPmFormatter).uppercase(Locale.getDefault()) // Ensure consistent case for comparison
        val amText = context.getString(R.string.am_designator).uppercase(Locale.getDefault())
        val pmText = context.getString(R.string.pm_designator).uppercase(Locale.getDefault())

        textPaint.getTextBounds(formattedTime, 0, formattedTime.length, bounds)
        val x = width / 2f
        val y = height / 2f + bounds.height() / 2f
        canvas.drawText(formattedTime, x, y, textPaint)

        val amPmPaint = Paint(textPaint) // Use the same paint for consistent styling
        val amPmTextSize = textPaint.textSize * 0.4f // Adjust size as needed
    amPmPaint.textSize = amPmTextSize

        val amPmBounds = Rect()
    amPmPaint.getTextBounds(amText, 0, amText.length, amPmBounds)
        val amPmWidth = amPmPaint.measureText(amText)

        val offsetX = bounds.width() / 2f + amPmWidth / 2f + 8f // Position to the right with some padding
        val amY = timeY - amPmBounds.height() / 2f
        val pmY = timeY + amPmBounds.height()

        if (amPmString == pmText) { // Compare with the translated PM string
        canvas.drawText(pmText, timeX + offsetX, pmY, amPmPaint)
        amPmPaint.alpha = 100 // Make AM slightly faded if it's PM
        canvas.drawText(amText, timeX + offsetX, amY, amPmPaint)
        } else if (amPmString == amText) { // Compare with the translated AM string
        canvas.drawText(amText, timeX + offsetX, amY, amPmPaint)
        amPmPaint.alpha = 100 // Make PM slightly faded if it's AM
        canvas.drawText(pmText, timeX + offsetX, pmY, amPmPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(tickRunnable)
    }

    fun setAnalogImageViews(
        clockFace: SVGImageView?, 
        hourHand: SVGImageView?, 
        minuteHand: SVGImageView?, 
        secondHand: SVGImageView?
    ) {
        this.clockFaceImageView = clockFace
        this.hourHandImageView = hourHand
        this.minuteHandImageView = minuteHand
        this.secondHandImageView = secondHand
        this.secondHandImageView?.isVisible = displaySeconds

        clockFace?.let {
            try {
                val svg = SVG.getFromResource(resources, R.raw.clock_face)
                it.setSVG(svg)
                updateNumberVisibility()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        updateAnalogColors() // Apply initial colors
        updateAnalogHands()
    }

    private fun updateAnalogColors() {
        val handColor = getHandColorForBackground(clockColor)
        val faceFillColor = clockColor

        tintDrawable(clockFaceImageView?.drawable, faceFillColor, android.graphics.PorterDuff.Mode.SRC_IN)
        tintDrawableStroke(clockFaceImageView?.drawable, handColor)
        tintDrawableStroke(hourHandImageView?.drawable, handColor)
        tintDrawableStroke(minuteHandImageView?.drawable, handColor)
        tintDrawableStroke(secondHandImageView?.drawable, handColor)
    }

    private fun tintDrawable(drawable: android.graphics.drawable.Drawable?, color: Int, mode: android.graphics.PorterDuff.Mode) {
        drawable?.mutate()
        DrawableCompat.setTint(drawable!!, color)
        DrawableCompat.setTintMode(drawable, mode)
    }

    private fun tintDrawableStroke(drawable: android.graphics.drawable.Drawable?, color: Int) {
        if (drawable is VectorDrawableCompat) {
            for (i in 0 until drawable.vectorDrawable.groupCount) {
                val group = drawable.vectorDrawable.getGroup(i)
                for (j in 0 until group.pathCount) {
                    val path = group.getPath(j)
                    path.strokeColor = color
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && drawable is android.graphics.drawable.VectorDrawable) {
            fun traverseVectorDrawable(vd: android.graphics.drawable.VectorDrawable, color: Int) {
                for (i in 0 until vd.groupCount) {
                    val group = vd.getGroup(i)
                    for (j in 0 until group.pathCount) {
                        val path = vd.getPath(j)
                        path.strokeColor = color
                    }
                }
            }
            traverseVectorDrawable(drawable, color)
        }
        drawable?.invalidateSelf()
    }

    private fun updateAnalogHands() {
        val now = LocalTime.now(ZoneId.of(timeZoneId))
        val hours = now.hour % 12
        val minutes = now.minute
        val seconds = now.second

        val hourAngle = (hours + minutes / 60f) * 30f
        val minuteAngle = (minutes + seconds / 60f) * 6f
        val secondAngle = seconds * 6f

        hourHandImageView?.rotation = hourAngle
        minuteHandImageView?.rotation = minuteAngle
        secondHandImageView?.rotation = secondAngle
    }

    fun pauseTime() {
        if (!isPaused) {
            isPaused = true
            pauseTimeOffset = System.currentTimeMillis() - LocalTime.now(ZoneId.of(timeZoneId)).toNanoOfDay() / 1_000_000L
        }
    }

    fun playTime() {
        if (isPaused) {
            isPaused = false
        }
    }

    fun resetTime() {
        isPaused = false
        pauseTimeOffset = 0L
        timeZoneId = sharedPreferences.getString("clock_time_zone_id", ZoneId.systemDefault().id) ?: ZoneId.systemDefault().id
        updateTimeFormat()
        val zoneId = ZoneId.of(timeZoneId)
        currentTime = LocalTime.now(zoneId)
        if (isAnalog) {
            updateAnalogHands()
        } else {
            invalidate()
        }
    }

    fun setClockId(id: Int) {
        clockId = id
        sharedPreferences = context.getSharedPreferences("clock_settings", Context.MODE_PRIVATE)
        loadInitialClockSettings()
    }

    override fun updateSettings() {
        loadInitialClockSettings()
        updateNumberVisibility() // Update visibility when settings change
        invalidate()
    }

    fun launchSettings() {
        if (clockId != -1) {
            val intent = Intent(context, ClockSettingsActivity::class.java)
            intent.putExtra("clock_id", clockId)
            context.startActivity(intent)
        }
    }

    private fun updateNumberVisibility() {
        clockFaceImageView?.svg?.let { svg ->
            val twelveHourGroup = svg.getElementById("twelveHourNumbers")
            val twentyFourHourGroup = svg.getElementById("twentyFourHourNumbers")

            twelveHourGroup?.setGraphical(is24Hour.not()) // Show 12-hour numbers if not 24-hour mode
            twentyFourHourGroup?.setGraphical(is24Hour)    // Show 24-hour numbers if in 24-hour mode
        }
    }
}

// Extension function to convert TimeZone to ZoneId
fun TimeZone.toZoneId(): ZoneId = ZoneId.of(this.id)

// Extension function (if needed elsewhere) to convert ZoneId to TimeZone
fun ZoneId.toTimeZone(): TimeZone = TimeZone.getTimeZone(this.id)
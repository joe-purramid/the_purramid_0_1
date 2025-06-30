// TimelineView.kt
package com.example.purramid.thepurramid.traffic_light

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.purramid.thepurramid.traffic_light.viewmodel.TimedSequence
import com.example.purramid.thepurramid.traffic_light.viewmodel.LightColor

class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val circlePaint = Paint().apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 14f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }

    private var sequence: TimedSequence? = null
    private var currentStepIndex: Int = 0
    private var elapsedStepSeconds: Int = 0
    private var showTimeRemaining: Boolean = false
    private var animatedPosition: Float = 0f

    fun setSequence(sequence: TimedSequence?, currentStep: Int, elapsedSeconds: Int, showTime: Boolean) {
        this.sequence = sequence
        this.currentStepIndex = currentStep
        this.elapsedStepSeconds = elapsedSeconds
        this.showTimeRemaining = showTime
        calculateAnimatedPosition()
        invalidate()
    }

    private fun calculateAnimatedPosition() {
        val seq = sequence ?: return
        if (currentStepIndex >= seq.steps.size) return

        var totalElapsed = 0
        for (i in 0 until currentStepIndex) {
            totalElapsed += seq.steps[i].durationSeconds
        }
        totalElapsed += elapsedStepSeconds

        val totalDuration = seq.getTotalDurationSeconds()
        animatedPosition = if (totalDuration > 0) {
            totalElapsed.toFloat() / totalDuration.toFloat()
        } else 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val seq = sequence ?: return
        if (seq.steps.isEmpty()) return

        val centerX = width / 2f
        val topY = 40f
        val bottomY = height - 40f
        val lineLength = bottomY - topY

        // Draw main timeline
        canvas.drawLine(centerX, topY, centerX, bottomY, linePaint)

        // Draw circles for each step
        val totalDuration = seq.getTotalDurationSeconds()
        if (totalDuration == 0) return

        var accumulatedTime = 0
        val circleRadius = 16f

        // Start circle
        drawCircle(canvas, centerX, topY, circleRadius, seq.steps.firstOrNull()?.color)

        // Step circles
        seq.steps.forEachIndexed { index, step ->
            if (index > 0) {
                val y = topY + (accumulatedTime.toFloat() / totalDuration.toFloat() * lineLength)
                drawCircle(canvas, centerX, y, circleRadius, step.color)
            }
            accumulatedTime += step.durationSeconds
        }

        // End circle (white)
        circlePaint.color = Color.WHITE
        circlePaint.strokeColor = Color.BLACK
        canvas.drawCircle(centerX, bottomY, circleRadius, circlePaint)

        // Draw animated position indicator
        if (currentStepIndex < seq.steps.size) {
            val currentY = topY + (animatedPosition * lineLength)
            circlePaint.color = getColorForLight(seq.steps[currentStepIndex].color)
            circlePaint.strokeColor = Color.BLACK
            canvas.drawCircle(centerX, currentY, circleRadius * 0.8f, circlePaint)

            // Draw time remaining if enabled
            if (showTimeRemaining) {
                val currentStep = seq.steps[currentStepIndex]
                val remainingSeconds = currentStep.durationSeconds - elapsedStepSeconds
                val timeText = formatTime(remainingSeconds)
                canvas.drawText(timeText, centerX + circleRadius + 20f, currentY + 5f, textPaint)
            }
        }
    }

    private fun drawCircle(canvas: Canvas, x: Float, y: Float, radius: Float, color: LightColor?) {
        circlePaint.color = getColorForLight(color)
        circlePaint.strokeColor = Color.BLACK
        canvas.drawCircle(x, y, radius, circlePaint)
    }

    private fun getColorForLight(color: LightColor?): Int {
        return when (color) {
            LightColor.RED -> 0xFFFF0000.toInt()
            LightColor.YELLOW -> 0xFFFFFF00.toInt()
            LightColor.GREEN -> 0xFF00FF00.toInt()
            null -> Color.GRAY
        }
    }

    private fun formatTime(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%d:%02d", minutes, seconds)
        }
    }
}
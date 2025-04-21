// SpinDialView.kt
package com.example.purramid.thepurramid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import androidx.core.content.ContextCompat
import java.util.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SpinDialView : View {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private val backgroundPaint = Paint().apply {
        color = Color.WHITE // Default background color
        style = Paint.Style.FILL
    }

    private val wedgePaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 24f // Default text size
        textAlign = Paint.Align.CENTER
    }

    private val arrowPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private var lists: List<SpinList> = emptyList()
    var currentList: SpinList? = null
    var settings: SpinSettings = SpinSettings()

    private var dialRadius = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var rotation = 0f // Current rotation of the dial

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Calculate the center and radius of the dial
        centerX = w / 2f
        centerY = h / 2f
        dialRadius = min(w, h) * 0.4f // Adjust radius as needed
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawCircle(centerX, centerY, dialRadius, backgroundPaint)

        // Draw wedges
        drawWedges(canvas)

        // Draw selection arrow
        drawSelectionArrow(canvas)
    }

    private fun drawWedges(canvas: Canvas) {
        val numWedges = settings.numWedges
        if (numWedges < 2) return // Avoid division by zero
        val wedgeAngle = 360f / numWedges
        var startAngle = 0f

        currentList?.items?.forEachIndexed { index, item ->
            wedgePaint.color = item.backgroundColor ?: getAutoAssignedColor(index) // Use custom color or auto-assigned

            val sweepAngle = wedgeAngle
            val oval = RectF(centerX - dialRadius, centerY - dialRadius, centerX + dialRadius, centerY + dialRadius)
            canvas.drawArc(oval, startAngle + rotation, sweepAngle, true, wedgePaint)

            drawItemContent(canvas, item, startAngle + rotation + sweepAngle / 2f, sweepAngle)

            startAngle += wedgeAngle
        }
    }

    private fun drawItemContent(canvas: Canvas, item: SpinItem, middleAngle: Float, sweepAngle: Float) {
        val itemX = centerX + (dialRadius * 0.7 * cos(Math.toRadians(middleAngle.toDouble()))).toFloat()
        val itemY = centerY + (dialRadius * 0.7 * sin(Math.toRadians(middleAngle.toDouble()))).toFloat()

        when (item.type) {
            SpinItemType.TEXT -> {
                textPaint.textSize = calculateTextSize(item.content, sweepAngle)
                canvas.drawText(item.content, itemX, itemY + textPaint.textSize / 2, textPaint)
            }
            SpinItemType.IMAGE -> {
                // TODO: Draw Image
            }
            SpinItemType.EMOJI -> {
                // TODO: Draw Emoji
            }
        }
    }

    private fun calculateTextSize(text: String, sweepAngle: Float): Float {
        // TODO: Calculate appropriate text size based on wedge size
        return 24f
    }

    private fun drawSelectionArrow(canvas: Canvas) {
        val arrowSize = 40f
        val arrowX = width - paddingRight - arrowSize // Right side
        val arrowY = height / 2f
        val path = android.graphics.Path().apply {
            moveTo(arrowX, arrowY - arrowSize / 2)
            lineTo(arrowX, arrowY + arrowSize / 2)
            lineTo(arrowX + arrowSize, arrowY)
            close()
        }
        canvas.drawPath(path, arrowPaint)
    }

    private fun getAutoAssignedColor(index: Int): Int {
        // TODO: Implement WCAG 2.2 AA color contrast
        return when (index % 6) {
            0 -> Color.RED
            1 -> Color.BLUE
            2 -> Color.GREEN
            3 -> Color.YELLOW
            4 -> Color.CYAN
            5 -> Color.MAGENTA
            else -> Color.GRAY
        }
    }

    fun spin(onAnimationEnd: () -> Unit) {
        val degreesPerItem = 360f / settings.numWedges
        val targetRotation = rotation + (360 * 3) - (degreesPerItem * (Random().nextInt(settings.numWedges)))
        val animation = RotateAnimation(
            rotation,
            targetRotation,
            Animation.RELATIVE_TO_SELF,
            0.5f,
            Animation.RELATIVE_TO_SELF,
            0.5f
        ).apply {
            duration = 2000 // 2 seconds
            interpolator = LinearInterpolator()
            fillAfter = true
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    rotation = targetRotation % 360
                    invalidate()
                    onAnimationEnd()
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
        }
        startAnimation(animation)
    }

    fun setData(lists: List<SpinList>, settings: SpinSettings) {
        this.lists = lists
        this.settings = settings
        this.currentList = lists.firstOrNull { it.id == settings.currentListId } ?: lists.firstOrNull()
        invalidate() // Redraw the view
    }

    private fun calculateTextSize(text: String, sweepAngle: Float): Float {
        val availableWidth = (dialRadius * 0.8 * sin(Math.toRadians(sweepAngle / 2.0))).toFloat() * 2 // Approximate width of the wedge
        val availableHeight = dialRadius * 0.5f // Approximate height (adjust as needed)

        textPaint.textSize = 24f // Start with a default size
        var textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)

        while (textBounds.width() > availableWidth || textBounds.height() > availableHeight) {
            textPaint.textSize -= 1f // Reduce text size until it fits
            if (textPaint.textSize <= 0f) {
                textPaint.textSize = 8f // Minimum size
                break
            }
            textPaint.getTextBounds(text, 0, text.length, textBounds)
        }
        return textPaint.textSize
    }
}
// SpinDialView.kt
package com.example.purramid.thepurramid // Use your actual package name

import android.content.Context
import android.graphics.* // Import Rect needed for text bounds
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator // Smoother start/end
import android.view.animation.RotateAnimation
// import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import com.bumptech.glide.Glide // Import Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.purramid.thepurramid.data.db.SpinItemEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import kotlin.math.* // Import ceil explicitly if needed, or just use extension functions
import kotlin.random.Random // Use Kotlin's Random

class SpinDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paints
    private val wedgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f // Thinner lines
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 40f // Start with a slightly larger default
        textAlign = Paint.Align.CENTER
        // Consider adding typeface if desired: typeface = Typeface.DEFAULT_BOLD
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    /** Factor determining distance of item content from center (0.0 center, 1.0 edge) */
    private val radiusFactor = 0.65f

    // Data
    private var items: List<SpinItemEntity> = emptyList()
    private var settings: SpinSettingsEntity? = null

    // Dimensions
    private var dialRadius = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var textBounds = Rect() // Reusable Rect for text measurements

    // State
    private var currentRotation = 0f // Current visual rotation (degrees)
    private var isLtr = resources.configuration.layoutDirection == LAYOUT_DIRECTION_LTR

    // Image Cache (Using Glide now, but keep reference for potential direct bitmap handling)
    // private val imageCache = mutableMapOf<String, Bitmap?>() // Cache loaded images via Glide

    // --- Public Methods ---

    /**
     * Sets the data for the dial (items and settings).
     * @param newItems List of items to display in the wedges.
     * @param newSettings Settings associated with this randomizer instance.
     */
    fun setData(newItems: List<SpinItemEntity>, newSettings: SpinSettingsEntity?) {
        this.items = newItems
        this.settings = newSettings
        // Clear image cache if items change significantly? Glide handles its own cache.
        requestLayout() // Recalculate size if needed (though unlikely)
        invalidate()    // Trigger redraw
    }

    /**
     * Starts the spin animation.
     * @param onResult Callback invoked when the animation finishes, providing the selected item.
     */
    fun spin(onResult: (SpinItemEntity?) -> Unit) {
        if (items.isEmpty()) {
            onResult(null) // Cannot spin an empty list
            return
        }

        val numWedges = items.size.coerceAtLeast(2) // Use actual item count
        val degreesPerWedge = 360f / numWedges

        // Calculate target rotation:
        // Base rotation + Multiple full spins + Random offset to land on a wedge
        val randomSpins = 3 + Random.nextInt(3) // 3 to 5 full spins
        val randomWedgeIndex = Random.nextInt(numWedges)
        // Calculate angle to center the target wedge on the selection arrow
        // Selection arrow points at 0 degrees (right) for LTR, 180 degrees (left) for RTL
        val selectionAngle = if (isLtr) 0f else 180f
        // Angle of the *middle* of the target wedge
        val targetWedgeMiddleAngle = (randomWedgeIndex * degreesPerWedge) + (degreesPerWedge / 2f)
        // Rotation needed to align targetWedgeMiddleAngle with selectionAngle
        val finalAngleOffset = selectionAngle - targetWedgeMiddleAngle

        val targetRotation = currentRotation + (360f * randomSpins) + finalAngleOffset

        val rotateAnimation = RotateAnimation(
            currentRotation, // Start from current visual rotation
            targetRotation,  // End at calculated target rotation
            Animation.RELATIVE_TO_SELF, 0.5f, // Pivot point X (center)
            Animation.RELATIVE_TO_SELF, 0.5f  // Pivot point Y (center)
        ).apply {
            duration = (settings?.let { calculateSpinDuration(it) } ?: 2000L) // Use calculated duration or default
            interpolator = DecelerateInterpolator() // Start fast, slow down
            fillAfter = true // Keep the end state after animation

            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    // Normalize rotation to 0-360 range
                    currentRotation = targetRotation % 360f
                    if (currentRotation < 0) currentRotation += 360f

                    // Determine winning item based on final angle
                    val winningItem = determineWinningItem(currentRotation)
                    invalidate() // Redraw in final position
                    onResult(winningItem) // Call the callback with the result
                }
            })
        }
        startAnimation(rotateAnimation)
    }

    // --- Lifecycle and Drawing ---

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        // Leave some padding, consider arrow size
        dialRadius = min(w, h) * 0.45f - 30f // Adjust radius as needed
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background circle (optional, could be transparent)
        // canvas.drawCircle(centerX, centerY, dialRadius, backgroundPaint)

        // Draw wedges and content
        drawWedgesAndContent(canvas)

        // Draw selection arrow
        drawSelectionArrow(canvas)
    }

    private fun drawWedgesAndContent(canvas: Canvas) {
        val numWedges = items.size
        if (numWedges < 2) {
            // Handle drawing for 0 or 1 item (e.g., full circle or nothing)
            if (numWedges == 1) {
                wedgePaint.color = items[0].backgroundColor ?: getAutoAssignedColor(0)
                canvas.drawCircle(centerX, centerY, dialRadius, wedgePaint)
                drawItemContent(canvas, items[0], 0f, 360f) // Draw item centered
            }
            return // Avoid division by zero for wedgeAngle
        }

        val wedgeAngle = 360f / numWedges
        val oval = RectF(centerX - dialRadius, centerY - dialRadius, centerX + dialRadius, centerY + dialRadius)

        // Save canvas state before applying rotation for wedges
        canvas.withSave {
            // Rotate canvas around center based on currentRotation
            rotate(currentRotation, centerX, centerY)

            // Draw wedges and items
            var startAngle = 0f
            items.forEachIndexed { index, item ->
                wedgePaint.color = item.backgroundColor ?: getAutoAssignedColor(index)
                val sweepAngle = wedgeAngle

                // Draw wedge arc
                canvas.drawArc(oval, startAngle, sweepAngle, true, wedgePaint)
                // Draw wedge outline
                canvas.drawArc(oval, startAngle, sweepAngle, true, linePaint)

                // Draw separator line (from center to edge)
                val lineX = centerX + dialRadius * cos(Math.toRadians(startAngle.toDouble())).toFloat()
                val lineY = centerY + dialRadius * sin(Math.toRadians(startAngle.toDouble())).toFloat()
                canvas.drawLine(centerX, centerY, lineX, lineY, linePaint)

                // Draw content centered within the wedge, rotating the canvas FOR the item
                val middleAngle = startAngle + sweepAngle / 2f
                drawItemContent(canvas, item, middleAngle, sweepAngle)

                startAngle += wedgeAngle
            }
            // Draw outer circle outline last
            canvas.drawCircle(centerX, centerY, dialRadius, linePaint)
        } // Canvas rotation is restored here
    }

    private fun drawItemContent(canvas: Canvas, item: SpinItemEntity, middleAngleDegrees: Float, sweepAngleDegrees: Float) {
        // Calculate position along the angle bisector (radius factor determines distance from center)
        val radiusFactor = 0.65f // Adjust to position content within wedge (0.0 near center, 1.0 near edge)
        val itemCenterX = centerX + (dialRadius * radiusFactor * cos(Math.toRadians(middleAngleDegrees.toDouble()))).toFloat()
        val itemCenterY = centerY + (dialRadius * radiusFactor * sin(Math.toRadians(middleAngleDegrees.toDouble()))).toFloat()

        // Calculate rotation needed to align content horizontally/readably
        // The canvas is already rotated by currentRotation. We apply rotation relative to wedge center.
        // We want the item's baseline to be roughly perpendicular to the wedge's angle bisector.
        // Angle for horizontal text is 0. Add 90 degrees to make it perpendicular to radius.
        val itemRotation = middleAngleDegrees - 90f // Adjust alignment as needed

        canvas.withSave {
            rotate(itemRotation, itemCenterX, itemCenterY)

            when (item.itemType) {
                SpinItemType.TEXT -> {
                    textPaint.textSize = calculateTextSize(item.content, sweepAngleDegrees)
                    // Adjust Y position based on text alignment (center vertically)
                    textPaint.getTextBounds(item.content, 0, item.content.length, textBounds)
                    val textY = itemCenterY + textBounds.height() / 2f - textBounds.bottom // Center vertically
                    canvas.drawText(item.content, itemCenterX, textY, textPaint)
                }
                SpinItemType.IMAGE -> {
                    // Using Glide is strongly recommended for efficient loading and caching
                    // TODO: Replace BitmapFactory with Glide or Coil
                    try {
                        Glide.with(context)
                            .asBitmap()
                            .load(item.content) // Assuming item.content is a path or URL
                            .into(object : CustomTarget<Bitmap>() {
                                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                    // Need to redraw *after* bitmap is loaded. This is tricky in onDraw.
                                    // Ideally, load bitmaps outside onDraw and trigger invalidate().
                                    // For simplicity *now*, we might need a placeholder or redraw logic.
                                    // Let's just draw if already cached by Glide? (Needs better design)

                                    // --- TEMPORARY DRAW LOGIC (Needs Improvement) ---
                                    // This direct drawing in callback is problematic.
                                    val scaledBitmap = scaleBitmapToFit(resource, sweepAngleDegrees)
                                    val imageRect = RectF(
                                        itemCenterX - scaledBitmap.width / 2f,
                                        itemCenterY - scaledBitmap.height / 2f,
                                        itemCenterX + scaledBitmap.width / 2f,
                                        itemCenterY + scaledBitmap.height / 2f
                                    )
                                    // Need access to the canvas *at the right time*
                                    // This needs a pattern where bitmaps are loaded async and then invalidate() is called.
                                    // For now, we might just *attempt* to draw, knowing it might not show reliably first time.
                                    // A better way: store loaded bitmaps in a map keyed by item.id, trigger invalidate on load.
                                    canvas.drawBitmap(scaledBitmap, null, imageRect, null) // Problematic placement
                                    // invalidate() // Calling invalidate inside onDraw callback leads to loop!
                                }
                                override fun onLoadCleared(placeholder: Drawable?) { /* Handle placeholder */ }
                                override fun onLoadFailed(errorDrawable: Drawable?) {
                                    // Draw placeholder or error text
                                    textPaint.textSize = 12f
                                    canvas.drawText("[Image Error]", itemCenterX, itemCenterY, textPaint)
                                }
                            })

                    } catch (e: Exception) {
                        // Log error, draw placeholder
                        textPaint.textSize = 12f
                        canvas.drawText("[Image Load Error]", itemCenterX, itemCenterY, textPaint)
                    }
                }
                SpinItemType.EMOJI -> {
                    // TODO: Implement proper EmojiCompat drawing if needed for complex emojis/sequences
                    // Basic drawing using drawText:
                    textPaint.textSize = calculateTextSize(item.emojiList.joinToString(""), sweepAngleDegrees) // Adjust size for emoji
                    textPaint.getTextBounds(item.emojiList.joinToString(""), 0, item.emojiList.joinToString("").length, textBounds)
                    val emojiY = itemCenterY + textBounds.height() / 2f - textBounds.bottom
                    canvas.drawText(item.emojiList.joinToString(" "), itemCenterX, emojiY, textPaint) // Join with space
                }
            }
        } // Canvas rotation is restored here
    }

    private fun drawSelectionArrow(canvas: Canvas) {
        val arrowBaseWidth = 40f
        val arrowHeight = 30f
        val arrowTipOffset = 10f // Distance from dial edge

        val arrowPointX: Float
        val path = Path()

        if (isLtr) {
            // Points right, arrow tip at (centerX + dialRadius + offset)
            arrowPointX = centerX + dialRadius + arrowTipOffset
            path.moveTo(arrowPointX, centerY) // Tip
            path.lineTo(arrowPointX - arrowHeight, centerY - arrowBaseWidth / 2f) // Top base corner
            path.lineTo(arrowPointX - arrowHeight, centerY + arrowBaseWidth / 2f) // Bottom base corner
        } else {
            // Points left, arrow tip at (centerX - dialRadius - offset)
            arrowPointX = centerX - dialRadius - arrowTipOffset
            path.moveTo(arrowPointX, centerY) // Tip
            path.lineTo(arrowPointX + arrowHeight, centerY - arrowBaseWidth / 2f) // Top base corner
            path.lineTo(arrowPointX + arrowHeight, centerY + arrowBaseWidth / 2f) // Bottom base corner
        }
        path.close()
        canvas.drawPath(path, arrowPaint)
    }

    // --- Helper Methods ---

    private fun calculateSpinDuration(settings: SpinSettingsEntity): Long {
        return if (settings.isSequenceEnabled) {
            // Sequence mode: 1 second per 10 items (rounded up)
            val numWedges = items.size.coerceAtLeast(1)
            val durationSeconds = ceil(numWedges / 10.0).toLong()
            durationSeconds * 1000L
        } else {
            // Normal spin: 2 seconds
            2000L
        }
    }

    /** Determines the winning item based on the final rotation angle. */
    private fun determineWinningItem(finalRotation: Float): SpinItemEntity? {
        if (items.isEmpty()) return null

        val numWedges = items.size.coerceAtLeast(1)
        val wedgeAngle = 360f / numWedges
        // Angle where the selection arrow points (relative to the unrotated dial)
        val selectionAngle = if (isLtr) 0f else 180f

        // Calculate the effective angle of the dial's 0-degree mark relative to the selection arrow
        // We need to account for the rotation
        val dialZeroAngleRelativeToSelection = (selectionAngle - finalRotation).normalizeAngle()

        // Find which wedge contains this angle
        val winningIndex = floor(dialZeroAngleRelativeToSelection / wedgeAngle).toInt()

        // Ensure index is within bounds (due to floating point nuances)
        val validIndex = winningIndex.coerceIn(0, items.size - 1)

        return items.getOrNull(validIndex)
    }

    /** Normalizes an angle to be within the 0-360 degree range. */
    private fun Float.normalizeAngle(): Float {
        var angle = this % 360f
        if (angle < 0) {
            angle += 360f
        }
        return angle
    }


    private fun getAutoAssignedColor(index: Int): Int {
        // TODO: Implement proper WCAG 2.2 AA color contrast calculation between adjacent wedges.
        // This placeholder just cycles through a predefined list.
        val colors = listOf(
            Color.parseColor("#E6194B"), // Red
            Color.parseColor("#3CB44B"), // Green
            Color.parseColor("#FFE119"), // Yellow
            Color.parseColor("#4363D8"), // Blue
            Color.parseColor("#F58231"), // Orange
            Color.parseColor("#911EB4"), // Purple
            Color.parseColor("#46F0F0"), // Cyan
            Color.parseColor("#F032E6"), // Magenta
            Color.parseColor("#BCF60C"), // Lime
            Color.parseColor("#FABEBE"), // Pink
            Color.parseColor("#008080"), // Teal
            Color.parseColor("#E6BEFF"), // Lavender
            Color.parseColor("#9A6324"), // Brown
            Color.parseColor("#FFFAC8"), // Beige
            Color.parseColor("#800000"), // Maroon
            Color.parseColor("#AAFFC3"), // Mint
            Color.parseColor("#808000"), // Olive
            Color.parseColor("#FFD8B1"), // Apricot
            Color.parseColor("#000075"), // Navy
            Color.parseColor("#A9A9A9")  // Grey (Use DKGRAY/LTGRAY cautiously for contrast)
        )
        return colors[index % colors.size]
    }

    private fun calculateTextSize(text: String, sweepAngleDegrees: Float): Float {
        // Estimate usable width/height within the wedge sector more accurately
        // Consider arc geometry - width is wider near edge, narrower near center
        val usableRadiusFraction = 0.8f // How far out text can go
        val textRadius = dialRadius * radiusFactor * usableRadiusFraction // Position where text width is measured

        // Max width based on chord length at textRadius distance for the sweepAngle
        val maxWidth = 2 * textRadius * sin(Math.toRadians(sweepAngleDegrees / 2.0)).toFloat() * 0.9f // Use 90% of chord width

        // Max height is roughly proportional to radius (simpler estimate)
        val maxHeight = dialRadius * 0.25f // Allow text to be ~25% of radius height? Adjust.

        // Iterate down from a starting size
        var currentSize = 60f // Start larger for potentially bigger wedges
        textPaint.textSize = currentSize

        while (currentSize > 8f) { // Minimum text size 8sp (or density-independent equivalent)
            textPaint.getTextBounds(text, 0, text.length, textBounds)
            if (textBounds.width() <= maxWidth && textBounds.height() <= maxHeight) {
                return currentSize // Found a size that fits
            }
            currentSize -= 1f // Decrease size and try again
            textPaint.textSize = currentSize
        }
        return 8f // Return minimum size if it never fits
    }

    private fun scaleBitmapToFit(bitmap: Bitmap, sweepAngleDegrees: Float): Bitmap {
        // Similar calculation as calculateTextSize for max dimensions
        val usableRadiusFraction = 0.7f // Images slightly smaller than text maybe
        val imageRadius = dialRadius * radiusFactor * usableRadiusFraction
        val maxDim = 2 * imageRadius * sin(Math.toRadians(sweepAngleDegrees / 2.0)).toFloat() * 0.85f // Use 85% of chord width

        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        if (width <= 0 || height <= 0) return bitmap // Avoid division by zero

        val scale = min(maxDim / width, maxDim / height)

        // Only scale down, don't scale up small images excessively
        if (scale >= 1.0f && max(width, height) < dialRadius * 0.1f) { // Don't scale up tiny images
            return bitmap
        }

        val finalScale = min(scale, 1.5f) // Limit up-scaling factor if needed

        val newWidth = (width * finalScale).coerceAtLeast(1f).toInt()
        val newHeight = (height * finalScale).coerceAtLeast(1f).toInt()

        // Use filtering for better quality scaling
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
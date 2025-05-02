// SpinDialView.kt
package com.example.purramid.thepurramid.randomizers.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.core.graphics.ColorUtils
import android.graphics.drawable.Drawable
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.purramid.thepurramid.data.db.SpinItemEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.randomizers.SpinItemType
import com.example.purramid.thepurramid.randomizers.SpinList
import com.example.purramid.thepurramid.randomizers.SpinSettings
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SpinDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

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

    private val textBounds = Rect()

    private var lists: List<SpinList> = emptyList()
    var currentList: SpinList? = null
    var settings: SpinSettings = SpinSettings()

		// Cache for loaded images <ItemID, Bitmap?> (null if loading/failed) 
		private val imageBitmapCache = ConcurrentHashMap<UUID, Bitmap?>() 
		// Rect for text bounds measurement 
		private val textBounds = Rect()

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

    private fun drawItemContent(canvas: Canvas, item: SpinItemEntity, middleAngleDegrees: Float, sweepAngleDegrees: Float) {
        val radiusFactor = 0.65f
        val itemCenterX = centerX + (dialRadius * radiusFactor * cos(Math.toRadians(middleAngleDegrees.toDouble()))).toFloat()
        val itemCenterY = centerY + (dialRadius * radiusFactor * sin(Math.toRadians(middleAngleDegrees.toDouble()))).toFloat()
        val itemRotation = middleAngleDegrees - 90f

        // --- Determine background color and check text contrast ---
        val wedgeBackgroundColor = item.backgroundColor ?: getAutoAssignedColor(items.indexOf(item)) // Get the background color
        val defaultTextColor = Color.BLACK
        val alternateTextColor = Color.WHITE
			// WCAG 2.2 AA contrast standards
			val contrastThreshold = 4.5
        val contrastWithBlack = ColorUtils.calculateContrast(defaultTextColor, wedgeBackgroundColor)
        val contrastWithWhite = ColorUtils.calculateContrast(alternateTextColor, wedgeBackgroundColor)

        // Set text color to black or white depending on which has better contrast >= 4.5
        // Default to black if neither meets the threshold but black has higher contrast.
        textPaint.color = if (contrastWithBlack >= contrastThreshold) {
            defaultTextColor
        } else if (contrastWithWhite >= contrastThreshold) {
            alternateTextColor
        } else {
            // Neither meets 4.5:1, pick the better of the two (or could default based on luminance)
            if (contrastWithBlack > contrastWithWhite) defaultTextColor else alternateTextColor
        }

       canvas.save() //Save canvas state before rotation
			canvas.withRotation(itemRotation, itemCenterX, itemCenterY) { // Rotate canvas for easier drawing
            
			when (item.itemType) {
       		SpinItemType.TEXT -> {
	          textPaint.textSize = calculateTextSize(item.content, sweepAngleDegrees)
					textPaint.getTextBounds(item.content, 0, item.content.length, textBounds)
					val textY = itemCenterY + textBounds.height() / 2f - textBounds.bottom 
					canvas.drawText(item.content, itemCenterX, textY, textPaint)
       		}
	      	SpinItemType.IMAGE -> {
          	val cachedBitmap = imageBitmapCache[item.id]
                    
					if (cachedBitmap != null) {
						// Image loaded successfully, draw it
						val scaledBitmap = scaleBitmapToFit(cachedBitmap, sweepAngleDegrees)
						// Draw bitmap centered at itemCenterX, itemCenterY
						canvas.drawBitmap( 
							scaledBitmap, 
							itemCenterX - scaledBitmap.width / 2f, 
							itemCenterY - scaledBitmap.height / 2f, 
							null // Use default paint 
						)
						// Recycle the scaled bitmap if it's different from the cached one to save memory
						if (scaledBitmap != cachedBitmap) { 
							// scaledBitmap.recycle() 
						}

                        val imageRect = RectF(
                            itemCenterX - scaledBitmap.width / 2f,
                            itemCenterY - scaledBitmap.height / 2f,
                            itemCenterX + scaledBitmap.width / 2f,
                            itemCenterY + scaledBitmap.height / 2f
                        )
                        canvas.drawBitmap(scaledBitmap, null, imageRect, null)
                    } else {
                        // Image is loading or failed to load
                        // Draw placeholder text or drawable
                        // Optional: check if key exists in cache to differentiate loading vs failed
                        textPaint.textSize = calculateTextSize("[Image]", sweepAngleDegrees) // Smaller size for placeholder
                        textPaint.getTextBounds("[Image]", 0, "[Image]".length, textBounds)
                        val textY = itemCenterY + textBounds.height() / 2f - textBounds.bottom
                        canvas.drawText("[Image]", itemCenterX, textY, textPaint)
                    }
                }
               
				SpinItemType.EMOJI -> 
					val emojiString = item.emojiList.joinToString(" ") // Join with space
					textPaint.textSize = calculateTextSize(emojiString, sweepAngleDegrees) // Adjust size 
					textPaint.getTextBounds(emojiString, 0, emojiString.length, textBounds
					val emojiY = itemCenterY + textBounds.height() / 2f - textBounds.bottom
					canvas.drawText(emojiString, itemCenterX, emojiY, textPaint)
                }
            }
        } // Canvas rotation restored
    }

    private fun calculateTextSize(text: String, sweepAngleDegrees: Float): Float { 
			if (text.isBlank() || sweepAngleDegrees <= 0) { 
				return 10f // Return a small default if no text or angle 
	 		} 

			// --- Define Constraints --- 
			val radiusFactor = 0.65f // Where text is drawn radially 
			val maxTextWidthPaddingFactor = 0.9f // Use 90% of calculated width for padding 
			val maxTextHeightFactor = 0.18f // Use ~18% of dial radius for max height 

			// Max height constraint (simple approach based on radius) 
			val maxTextHeight = dialRadius * maxTextHeightFactor 

			// Max width constraint (approximate chord length at radiusFactor) 
			val textRadius = dialRadius * radiusFactor 
			val sweepAngleRadians = Math.toRadians(sweepAngleDegrees.toDouble()) 
			// Chord length = 2 * R * sin(angle / 2) 
			val maxTextWidth = (2.0 * textRadius * sin(sweepAngleRadians / 2.0)).toFloat() * maxTextWidthPaddingFactor 

			// --- Iterative Sizing --- 
			val maxTryTextSize = dialRadius / 4f // Start with a large potential size 
			val minTextSizeSp = 8f // Minimum text size in scaled pixels (sp) 
			val minTextSizePx = minTextSizeSp * context.resources.displayMetrics.scaledDensity 

			var currentTextSize = maxTryTextSize 

			while (currentTextSize > minTextSizePx) { 
				textPaint.textSize = currentTextSize 
				textPaint.getTextBounds(text, 0, text.length, textBounds) 

			// Check if text fits within calculated bounds 
			if (textBounds.width() <= maxTextWidth && textBounds.height() <= maxTextHeight) { 
				return currentTextSize // Found a suitable size 
			} 

			// Reduce text size and try again (e.g., decrease by 1sp equivalent) 
			currentTextSize -= 1f * context.resources.displayMetrics.scaledDensity 
			// Ensure we don't go below minimum 
			currentTextSize = maxOf(currentTextSize, minTextSizePx) 
		} 

		// If loop finishes, it means even the minimum size didn't fit (or barely fits) return minTextSizePx 
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
        // TODO: Consider algorithmic color generation and contrast checking against previous wedge
        // 1. Define the palette
        val safePalette = listOf(
            Color.parseColor("#003f5c"), // Dark Blue
            Color.parseColor("#ff6361"), // Red/Orange
            Color.parseColor("#58508d"), // Purple
            Color.parseColor("#ffa600"), // Orange/Yellow
            Color.parseColor("#bc5090"), // Pink/Magenta
            Color.parseColor("#00796b"), // Teal
            Color.parseColor("#d45087"), // Another Pink/Purple
            Color.parseColor("#2f4b7c"), // Medium Blue
            Color.parseColor("#ffcc66"), // Light Yellow
            Color.parseColor("#a05195"), // Another Purple
            Color.parseColor("#665191"), // Indigo
            Color.parseColor("#ff7c43")  // Orange
            // Add more carefully selected contrasting colors if needed
        )

        // 2. Return a color from the palette using the index
        if (safePalette.isEmpty()) {
            return Color.GRAY // Fallback if palette is somehow empty
        }
        return safePalette[index % safePalette.size] // Use modulo for safe cycling
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

    // --- Updated setData ---
    fun setData(newItems: List<SpinItemEntity>, newSettings: SpinSettingsEntity?) {
        val oldItems = this.items
        this.items = newItems
        this.settings = newSettings

        // Clear cache for items that are no longer present
        val newItemIds = newItems.map { it.id }.toSet()
        imageBitmapCache.keys.retainAll { it in newItemIds }

        // Preload images for new items
        newItems.forEach { newItem ->
            if (newItem.itemType == SpinItemType.IMAGE && !imageBitmapCache.containsKey(newItem.id)) {
                loadItemImage(newItem)
            }
        }

        requestLayout()
        invalidate()
    }

    // --- Image Loading Function ---
    private fun loadItemImage(item: SpinItemEntity) {
        if (item.itemType != SpinItemType.IMAGE || item.content.isBlank()) return

        // Set null initially to indicate loading (or use a specific loading state)
        imageBitmapCache[item.id] = null

        Glide.with(context)
            .asBitmap()
            .load(item.content) // Assuming item.content is path/URI
            // Optional: Add error/placeholder handling in Glide
            // .placeholder(placeholderDrawable)
            // .error(placeholderDrawable)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    // Cache the loaded bitmap
                    imageBitmapCache[item.id] = resource
                    // Request redraw ONLY if the view is still attached
                    if (isAttachedToWindow) {
                        invalidate()
                    }
                }
                override fun onLoadCleared(placeholder: Drawable?) {
                    // Handle placeholder state if needed
                    imageBitmapCache[item.id] = null // Clear if load is cancelled
                    if (isAttachedToWindow) invalidate()
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    // Mark as failed (null bitmap) or use an error bitmap
                    imageBitmapCache[item.id] = null // Indicate load failed
                    if (isAttachedToWindow) invalidate()
                }
            })
    }

    private fun calculateTextSize(text: String, sweepAngle: Float): Float {
        val availableWidth = (dialRadius * 0.8 * sin(Math.toRadians(sweepAngle / 2.0))).toFloat() * 2
        val baseTextSize = dialRadius / 5 // A reasonable starting point based on dial size
        return baseTextSize
    }
}
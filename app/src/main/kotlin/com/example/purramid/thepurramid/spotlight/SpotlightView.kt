// SpotlightView.kt
package com.example.purramid.thepurramid.spotlight

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.spotlight.SpotlightOpening
import com.example.purramid.thepurramid.util.dpToPx
import kotlin.math.absoluteValue
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.maxOf

/**
 * Custom view that renders a semi-opaque overlay with multiple spotlight openings (holes).
 * This is a single view that manages all openings for one service instance.
 */
class SpotlightView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // --- Listener Interface ---
    interface SpotlightInteractionListener {
        fun onOpeningMoved(openingId: Int, newX: Float, newY: Float)
        fun onOpeningResized(opening: SpotlightOpening)
        fun onOpeningShapeToggled(openingId: Int)
        fun onOpeningLockToggled(openingId: Int)
        fun onAllLocksToggled()
        fun onOpeningDeleted(openingId: Int)
        fun onAddNewOpeningRequested()
        fun onControlsToggled()
        fun onSettingsRequested()
    }

    var interactionListener: SpotlightInteractionListener? = null

    // --- Colors per Specification ---
    private val maskColor = Color.parseColor("#36454F") // Charcoal
    private val maskPaint = Paint().apply {
        color = maskColor
        alpha = 128 // 0.5 opacity = 128/255
    }

    private val spotlightPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }

    private val lockBorderPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = context.dpToPx(3).toFloat()
        isAntiAlias = true
    }

    // --- State Management ---
    private var currentState: SpotlightUiState = SpotlightUiState()
    private var openings: List<SpotlightOpening> = emptyList()
    private var showControls = true
    private var showSettingsMenu = false
    private var selectedOpeningId: Int? = null

    // --- Touch Handling ---
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
    private var downTime: Long = 0
    private var downX = 0f
    private var downY = 0f

    // Interaction states
    private var activeOpening: SpotlightOpening? = null
    private var isDraggingOpening = false
    private var isResizingOpening = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var openingStartX = 0f
    private var openingStartY = 0f

    // Visual feedback
    private var visualFeedbackOpening: SpotlightOpening? = null

    // Control button definitions
    private val controlButtonSize = context.dpToPx(48)
    private val controlMargin = context.dpToPx(16)
    private val controlIconSize = context.dpToPx(24)

    // Drawables for controls
    private var moveDrawable: Drawable? = null
    private var resizeDrawable: Drawable? = null
    private var settingsDrawable: Drawable? = null
    private var closeDrawable: Drawable? = null
    private var shapeDrawable: Drawable? = null
    private var lockDrawable: Drawable? = null
    private var lockOpenDrawable: Drawable? = null
    private var lockAllDrawable: Drawable? = null
    private var lockAllOpenDrawable: Drawable? = null
    private var addDrawable: Drawable? = null

    // For drawing
    private val ovalRect = RectF()
    private val path = Path()

    // Minimum size for openings
    private val minDimensionPx = context.dpToPx(50).toFloat()

    init {
        loadDrawables()
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun loadDrawables() {
        moveDrawable = ContextCompat.getDrawable(context, R.drawable.ic_move)?.mutate()
        resizeDrawable = ContextCompat.getDrawable(context, R.drawable.ic_resize_right_handle)?.mutate()
        settingsDrawable = ContextCompat.getDrawable(context, R.drawable.ic_settings)?.mutate()
        closeDrawable = ContextCompat.getDrawable(context, R.drawable.ic_close)?.mutate()
        shapeDrawable = ContextCompat.getDrawable(context, R.drawable.ic_spotlight_shape)?.mutate()
        lockDrawable = ContextCompat.getDrawable(context, R.drawable.ic_lock)?.mutate()
        lockOpenDrawable = ContextCompat.getDrawable(context, R.drawable.ic_lock_open)?.mutate()
        lockAllDrawable = ContextCompat.getDrawable(context, R.drawable.ic_lock_all)?.mutate()
        lockAllOpenDrawable = ContextCompat.getDrawable(context, R.drawable.ic_lock_all_open)?.mutate()
        addDrawable = ContextCompat.getDrawable(context, R.drawable.ic_add_circle)?.mutate()
    }

    fun updateState(state: SpotlightUiState) {
        currentState = state
        openings = state.openings
        showControls = state.showControls
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw semi-opaque overlay
        canvas.drawColor(maskColor)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)

        // Create path for all openings
        path.reset()

        // Use visual feedback opening if actively interacting
        val drawList = if (visualFeedbackOpening != null) {
            openings.map { opening ->
                if (opening.openingId == visualFeedbackOpening?.openingId) {
                    visualFeedbackOpening!!
                } else {
                    opening
                }
            }
        } else {
            openings
        }

        // Add all openings to path
        drawList.forEach { opening ->
            when (opening.shape) {
                SpotlightOpening.Shape.CIRCLE -> {
                    path.addCircle(opening.centerX, opening.centerY, opening.radius, Path.Direction.CW)
                }
                SpotlightOpening.Shape.OVAL -> {
                    ovalRect.set(
                        opening.centerX - opening.width / 2f,
                        opening.centerY - opening.height / 2f,
                        opening.centerX + opening.width / 2f,
                        opening.centerY + opening.height / 2f
                    )
                    path.addOval(ovalRect, Path.Direction.CW)
                }
                SpotlightOpening.Shape.SQUARE -> {
                    val halfSize = opening.size / 2f
                    path.addRect(
                        opening.centerX - halfSize,
                        opening.centerY - halfSize,
                        opening.centerX + halfSize,
                        opening.centerY + halfSize,
                        Path.Direction.CW
                    )
                }
                SpotlightOpening.Shape.RECTANGLE -> {
                    path.addRect(
                        opening.centerX - opening.width / 2f,
                        opening.centerY - opening.height / 2f,
                        opening.centerX + opening.width / 2f,
                        opening.centerY + opening.height / 2f,
                        Path.Direction.CW
                    )
                }
            }

            // Draw lock border if locked
            if (opening.isLocked) {
                drawLockBorder(canvas, opening)
            }
        }

        // Clear all openings at once
        canvas.drawPath(path, spotlightPaint)

        // Draw controls for each opening if enabled
        if (showControls) {
            openings.forEach { opening ->
                drawControlsForOpening(canvas, opening)
            }
        }
    }

    private fun drawLockBorder(canvas: Canvas, opening: SpotlightOpening) {
        when (opening.shape) {
            SpotlightOpening.Shape.CIRCLE -> {
                canvas.drawCircle(
                    opening.centerX,
                    opening.centerY,
                    opening.radius + lockBorderPaint.strokeWidth / 2,
                    lockBorderPaint
                )
            }
            SpotlightOpening.Shape.OVAL -> {
                ovalRect.set(
                    opening.centerX - opening.width / 2f - lockBorderPaint.strokeWidth / 2,
                    opening.centerY - opening.height / 2f - lockBorderPaint.strokeWidth / 2,
                    opening.centerX + opening.width / 2f + lockBorderPaint.strokeWidth / 2,
                    opening.centerY + opening.height / 2f + lockBorderPaint.strokeWidth / 2
                )
                canvas.drawOval(ovalRect, lockBorderPaint)
            }
            SpotlightOpening.Shape.SQUARE -> {
                val halfSize = opening.size / 2f + lockBorderPaint.strokeWidth / 2
                canvas.drawRect(
                    opening.centerX - halfSize,
                    opening.centerY - halfSize,
                    opening.centerX + halfSize,
                    opening.centerY + halfSize,
                    lockBorderPaint
                )
            }
            SpotlightOpening.Shape.RECTANGLE -> {
                canvas.drawRect(
                    opening.centerX - opening.width / 2f - lockBorderPaint.strokeWidth / 2,
                    opening.centerY - opening.height / 2f - lockBorderPaint.strokeWidth / 2,
                    opening.centerX + opening.width / 2f + lockBorderPaint.strokeWidth / 2,
                    opening.centerY + opening.height / 2f + lockBorderPaint.strokeWidth / 2,
                    lockBorderPaint
                )
            }
        }
    }

    private fun drawControlsForOpening(canvas: Canvas, opening: SpotlightOpening) {
        val bounds = getOpeningBounds(opening)

        // Position controls per specification
        // Move handle - top left
        val moveX = bounds.left - controlButtonSize / 2
        val moveY = bounds.top - controlButtonSize / 2
        drawControl(canvas, moveDrawable, moveX, moveY, opening.isLocked)

        // Close button - top right
        val closeX = bounds.right - controlButtonSize / 2
        val closeY = bounds.top - controlButtonSize / 2
        drawControl(canvas, closeDrawable, closeX, closeY, opening.isLocked)

        // Settings button - bottom left
        val settingsX = bounds.left - controlButtonSize / 2
        val settingsY = bounds.bottom - controlButtonSize / 2
        drawControl(canvas, settingsDrawable, settingsX, settingsY, false)

        // Resize handle - bottom right
        val resizeX = bounds.right - controlButtonSize / 2
        val resizeY = bounds.bottom - controlButtonSize / 2
        if (opening.isLocked) {
            // Draw inactive resize handle when locked
            val inactiveResize = ContextCompat.getDrawable(context, R.drawable.ic_resize_right_handle)?.mutate()
            inactiveResize?.alpha = 128
            drawControl(canvas, inactiveResize, resizeX, resizeY, true)
        } else {
            drawControl(canvas, resizeDrawable, resizeX, resizeY, false)
        }

        // Only draw settings menu if this is the selected opening
        if (selectedOpeningId == opening.openingId && showSettingsMenu) {
            drawSettingsMenu(canvas, settingsX, settingsY, opening)
        }
    }

    private fun drawControl(canvas: Canvas, drawable: Drawable?, centerX: Float, centerY: Float, disabled: Boolean = false) {
        drawable?.let {
            val halfSize = controlIconSize / 2
            it.setBounds(
                (centerX - halfSize).toInt(),
                (centerY - halfSize).toInt(),
                (centerX + halfSize).toInt(),
                (centerY + halfSize).toInt()
            )
            if (disabled) {
                it.alpha = 128
            } else {
                it.alpha = 255
            }
            it.draw(canvas)
        }
    }

    private fun drawSettingsMenu(canvas: Canvas, anchorX: Float, anchorY: Float, opening: SpotlightOpening) {
        // Settings menu extends upward from the settings button
        val menuWidth = controlButtonSize
        val menuItemHeight = controlButtonSize
        val menuItemCount = 4 // Shape, Lock, Lock All, Add Another
        val menuHeight = menuItemHeight * menuItemCount

        // Background for menu (semi-transparent)
        val menuPaint = Paint().apply {
            color = Color.parseColor("#36454F")
            alpha = 200
        }

        val menuLeft = anchorX - menuWidth / 2
        val menuTop = anchorY - menuHeight - controlButtonSize / 2
        val menuRight = menuLeft + menuWidth
        val menuBottom = anchorY - controlButtonSize / 2

        // Draw menu background
        canvas.drawRect(menuLeft, menuTop, menuRight, menuBottom, menuPaint)

        // Draw menu items from top to bottom
        var currentY = menuTop + menuItemHeight / 2

        // 1. Shape button
        drawControl(canvas, shapeDrawable, anchorX, currentY, opening.isLocked)
        currentY += menuItemHeight

        // 2. Lock button
        val lockIcon = if (opening.isLocked) lockDrawable else lockOpenDrawable
        drawControl(canvas, lockIcon, anchorX, currentY, false)
        currentY += menuItemHeight

        // 3. Lock All button
        val lockAllIcon = if (currentState.areAllLocked) lockAllDrawable else lockAllOpenDrawable
        drawControl(canvas, lockAllIcon, anchorX, currentY, false)
        currentY += menuItemHeight

        // 4. Add Another button
        drawControl(canvas, addDrawable, anchorX, currentY, !currentState.canAddMore)
    }

    private fun getOpeningBounds(opening: SpotlightOpening): RectF {
        return when (opening.shape) {
            SpotlightOpening.Shape.CIRCLE -> {
                RectF(
                    opening.centerX - opening.radius,
                    opening.centerY - opening.radius,
                    opening.centerX + opening.radius,
                    opening.centerY + opening.radius
                )
            }
            SpotlightOpening.Shape.OVAL -> {
                RectF(
                    opening.centerX - opening.width / 2f,
                    opening.centerY - opening.height / 2f,
                    opening.centerX + opening.width / 2f,
                    opening.centerY + opening.height / 2f
                )
            }
            SpotlightOpening.Shape.SQUARE -> {
                val halfSize = opening.size / 2f
                RectF(
                    opening.centerX - halfSize,
                    opening.centerY - halfSize,
                    opening.centerX + halfSize,
                    opening.centerY + halfSize
                )
            }
            SpotlightOpening.Shape.RECTANGLE -> {
                RectF(
                    opening.centerX - opening.width / 2f,
                    opening.centerY - opening.height / 2f,
                    opening.centerX + opening.width / 2f,
                    opening.centerY + opening.height / 2f
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()

                // Check what was touched
                val touchedOpening = findOpeningAt(downX, downY)
                val touchedControl = if (showControls) findControlAt(downX, downY) else null

                if (touchedControl != null) {
                    handleControlTouch(touchedControl)
                    return true
                }

                if (touchedOpening != null) {
                    activeOpening = touchedOpening
                    dragStartX = downX
                    dragStartY = downY
                    openingStartX = touchedOpening.centerX
                    openingStartY = touchedOpening.centerY
                    return true
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                activeOpening?.let { opening ->
                    if (!opening.isLocked) {
                        val deltaX = event.x - dragStartX
                        val deltaY = event.y - dragStartY

                        if (!isDraggingOpening && hypot(deltaX, deltaY) > touchSlop) {
                            isDraggingOpening = true
                            visualFeedbackOpening = opening.copy()
                        }

                        if (isDraggingOpening) {
                            visualFeedbackOpening?.let {
                                it.centerX = openingStartX + deltaX
                                it.centerY = openingStartY + deltaY

                                // Constrain to screen bounds
                                val bounds = getOpeningBounds(it)
                                if (bounds.left < 0) it.centerX -= bounds.left
                                if (bounds.top < 0) it.centerY -= bounds.top
                                if (bounds.right > width) it.centerX -= (bounds.right - width)
                                if (bounds.bottom > height) it.centerY -= (bounds.bottom - height)
                            }
                            invalidate()
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val upTime = System.currentTimeMillis()
                val deltaTime = upTime - downTime
                val deltaX = (event.x - downX).absoluteValue
                val deltaY = (event.y - downY).absoluteValue

                // Handle drag completion
                if (isDraggingOpening) {
                    visualFeedbackOpening?.let {
                        interactionListener?.onOpeningMoved(it.openingId, it.centerX, it.centerY)
                    }
                    isDraggingOpening = false
                    visualFeedbackOpening = null
                    activeOpening = null
                    invalidate()
                    return true
                }

                // Handle tap
                if (deltaTime < tapTimeout && deltaX < touchSlop && deltaY < touchSlop) {
                    val tappedOpening = findOpeningAt(downX, downY)
                    if (tappedOpening == null && !showControls) {
                        // Tapped on mask area - toggle controls
                        interactionListener?.onControlsToggled()
                    } else if (showSettingsMenu) {
                        // Close settings menu if tapping outside
                        showSettingsMenu = false
                        selectedOpeningId = null
                        invalidate()
                    }
                }

                activeOpening = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDraggingOpening = false
                isResizingOpening = false
                visualFeedbackOpening = null
                activeOpening = null
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun findOpeningAt(x: Float, y: Float): SpotlightOpening? {
        // Check in reverse order so top-most is found first
        return openings.reversed().firstOrNull { opening ->
            isPointInOpening(x, y, opening)
        }
    }

    private fun isPointInOpening(x: Float, y: Float, opening: SpotlightOpening): Boolean {
        return when (opening.shape) {
            SpotlightOpening.Shape.CIRCLE -> {
                hypot(x - opening.centerX, y - opening.centerY) <= opening.radius
            }
            SpotlightOpening.Shape.OVAL -> {
                val a = opening.width / 2f
                val b = opening.height / 2f
                if (a > 0 && b > 0) {
                    val dx = (x - opening.centerX) / a
                    val dy = (y - opening.centerY) / b
                    (dx * dx + dy * dy) <= 1
                } else false
            }
            SpotlightOpening.Shape.SQUARE -> {
                val halfSize = opening.size / 2f
                x >= opening.centerX - halfSize && x <= opening.centerX + halfSize &&
                        y >= opening.centerY - halfSize && y <= opening.centerY + halfSize
            }
            SpotlightOpening.Shape.RECTANGLE -> {
                val halfWidth = opening.width / 2f
                val halfHeight = opening.height / 2f
                x >= opening.centerX - halfWidth && x <= opening.centerX + halfWidth &&
                        y >= opening.centerY - halfHeight && y <= opening.centerY + halfHeight
            }
        }
    }

    private data class ControlInfo(
        val opening: SpotlightOpening,
        val type: ControlType,
        val bounds: RectF
    )

    private enum class ControlType {
        MOVE, RESIZE, SETTINGS, CLOSE, SHAPE, LOCK, LOCK_ALL, ADD
    }

    private fun findControlAt(x: Float, y: Float): ControlInfo? {
        val touchRadius = controlButtonSize / 2f

        // First check if settings menu is open
        if (showSettingsMenu && selectedOpeningId != null) {
            val selectedOpening = openings.find { it.openingId == selectedOpeningId }
            selectedOpening?.let { opening ->
                val bounds = getOpeningBounds(opening)
                val settingsX = bounds.left - controlButtonSize / 2
                val settingsY = bounds.bottom - controlButtonSize / 2

                // Check settings menu items
                val menuLeft = settingsX - touchRadius
                val menuRight = settingsX + touchRadius
                var menuY = settingsY - controlButtonSize * 4.5f // Start from top of menu

                // Shape button
                if (x >= menuLeft && x <= menuRight && y >= menuY && y <= menuY + controlButtonSize) {
                    return ControlInfo(opening, ControlType.SHAPE, RectF(menuLeft, menuY, menuRight, menuY + controlButtonSize))
                }
                menuY += controlButtonSize

                // Lock button
                if (x >= menuLeft && x <= menuRight && y >= menuY && y <= menuY + controlButtonSize) {
                    return ControlInfo(opening, ControlType.LOCK, RectF(menuLeft, menuY, menuRight, menuY + controlButtonSize))
                }
                menuY += controlButtonSize

                // Lock All button
                if (x >= menuLeft && x <= menuRight && y >= menuY && y <= menuY + controlButtonSize) {
                    return ControlInfo(opening, ControlType.LOCK_ALL, RectF(menuLeft, menuY, menuRight, menuY + controlButtonSize))
                }
                menuY += controlButtonSize

                // Add Another button
                if (x >= menuLeft && x <= menuRight && y >= menuY && y <= menuY + controlButtonSize) {
                    return ControlInfo(opening, ControlType.ADD, RectF(menuLeft, menuY, menuRight, menuY + controlButtonSize))
                }
            }
        }

        // Then check regular controls
        openings.forEach { opening ->
            val bounds = getOpeningBounds(opening)

            // Check each control position
            val controls = listOf(
                // Move - top left
                ControlInfo(
                    opening,
                    ControlType.MOVE,
                    RectF(
                        bounds.left - touchRadius,
                        bounds.top - touchRadius,
                        bounds.left + touchRadius,
                        bounds.top + touchRadius
                    )
                ),
                // Close - top right
                ControlInfo(
                    opening,
                    ControlType.CLOSE,
                    RectF(
                        bounds.right - touchRadius,
                        bounds.top - touchRadius,
                        bounds.right + touchRadius,
                        bounds.top + touchRadius
                    )
                ),
                // Settings - bottom left
                ControlInfo(
                    opening,
                    ControlType.SETTINGS,
                    RectF(
                        bounds.left - touchRadius,
                        bounds.bottom - touchRadius,
                        bounds.left + touchRadius,
                        bounds.bottom + touchRadius
                    )
                ),
                // Resize - bottom right
                ControlInfo(
                    opening,
                    ControlType.RESIZE,
                    RectF(
                        bounds.right - touchRadius,
                        bounds.bottom - touchRadius,
                        bounds.right + touchRadius,
                        bounds.bottom + touchRadius
                    )
                )
            )

            controls.forEach { control ->
                if (control.bounds.contains(x, y)) {
                    return control
                }
            }
        }

        return null
    }

    private fun handleControlTouch(control: ControlInfo) {
        when (control.type) {
            ControlType.MOVE -> {
                if (!control.opening.isLocked) {
                    activeOpening = control.opening
                    isDraggingOpening = true
                    visualFeedbackOpening = control.opening.copy()
                }
            }
            ControlType.RESIZE -> {
                if (!control.opening.isLocked) {
                    // TODO: Implement resize functionality
                }
            }
            ControlType.SETTINGS -> {
                // Toggle settings menu for this opening
                if (selectedOpeningId == control.opening.openingId && showSettingsMenu) {
                    // Close menu if clicking on same settings button
                    showSettingsMenu = false
                    selectedOpeningId = null
                } else {
                    // Open menu for this opening
                    showSettingsMenu = true
                    selectedOpeningId = control.opening.openingId
                }
                invalidate()
            }
            ControlType.CLOSE -> {
                if (!control.opening.isLocked) {
                    interactionListener?.onOpeningDeleted(control.opening.openingId)
                }
            }
            ControlType.SHAPE -> {
                if (!control.opening.isLocked) {
                    interactionListener?.onOpeningShapeToggled(control.opening.openingId)
                    // Close settings menu after action
                    showSettingsMenu = false
                    selectedOpeningId = null
                    invalidate()
                }
            }
            ControlType.LOCK -> {
                interactionListener?.onOpeningLockToggled(control.opening.openingId)
                // Close settings menu after action
                showSettingsMenu = false
                selectedOpeningId = null
                invalidate()
            }
            ControlType.LOCK_ALL -> {
                interactionListener?.onAllLocksToggled()
                // Close settings menu after action
                showSettingsMenu = false
                selectedOpeningId = null
                invalidate()
            }
            ControlType.ADD -> {
                if (currentState.canAddMore) {
                    interactionListener?.onAddNewOpeningRequested()
                    // Close settings menu after action
                    showSettingsMenu = false
                    selectedOpeningId = null
                    invalidate()
                }
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
// FloatingWindowActivity.kt
abstract class FloatingWindowActivity : AppCompatActivity() {
    
    protected var instanceId: Int = 0
    private var isDragging = false
    private var isResizing = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val movementThreshold = 10.dp // Convert to pixels in init
    
    @Inject lateinit var instanceManager: InstanceManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configure window appearance
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
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
        findViewById<View>(android.R.id.content).setOnTouchListener { _, event ->
            handleWindowTouch(event)
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
                if (Math.abs(deltaX) < movementThreshold && 
                    Math.abs(deltaY) < movementThreshold) {
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
        window.attributes = params
    }
    
    private fun resizeWindow(deltaX: Int, deltaY: Int) {
        val params = window.attributes
        params.width = Math.max(params.width + deltaX, getMinWidth())
        params.height = Math.max(params.height + deltaY, getMinHeight())
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
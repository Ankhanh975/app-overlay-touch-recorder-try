package com.appoverlaytouchrecorder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

// Data classes for touch events
data class TouchEvent(
    val action: String,
    val x: Float,
    val y: Float,
    val timestamp: Long,
    val pressure: Float = 0f,
    val size: Float = 0f
)

data class SwipeEvent(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val duration: Long,
    val velocity: Float,
    val direction: String,
    val timestamp: Long
)

/**
 * Configuration classes for the generalized overlay system
 * 
 * This system allows creating arbitrary UI overlays with different configurations
 * and element types, making it easy to extend for future requirements.
 */

/**
 * Configuration for overlay window properties
 */
data class OverlayConfig(
    val width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
    val height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
    val gravity: Int = Gravity.TOP or Gravity.END,
    val x: Int = 20,
    val y: Int = 100,
    val flags: Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    val pixelFormat: Int = PixelFormat.TRANSLUCENT
)

/**
 * Configuration for individual UI elements within an overlay
 */
data class UIElementConfig(
    val type: UIElementType,
    val text: String? = null,
    val textSize: Float = 16f,
    val textColor: Int = Color.WHITE,
    val backgroundColor: Int = Color.argb(180, 0, 0, 0),
    val padding: Int = 24,
    val gravity: Int = Gravity.CENTER,
    val onClick: (() -> Unit)? = null,
    val isVisible: Boolean = true
)

/**
 * Supported UI element types for overlays
 */
enum class UIElementType {
    TEXT_VIEW,
    BUTTON,
    LIST_VIEW,
    LINEAR_LAYOUT,
    FRAME_LAYOUT
}

class FpsOverlayService : AccessibilityService() {
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var fpsTextView: TextView? = null
    private var touchLogListView: ListView? = null
    private var touchEventAdapter: TouchEventAdapter? = null
    private var logContainer: LinearLayout? = null
    private var isOverlayVisible = false
    private var isLogVisible = true
    private var fpsCounter = 0
    private var frameCount = 0
    private var lastFpsUpdate = 0L
    private var fpsUpdateJob: Job? = null
    
    // Touch recording variables
    private val touchEvents = mutableListOf<TouchEvent>()
    private val swipeEvents = mutableListOf<SwipeEvent>()
    private var touchStartTime = 0L
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isTrackingSwipe = false
    private val maxLogEntries = 100
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    // UI Element Builders
    private fun createTextView(config: UIElementConfig): TextView {
        return TextView(this).apply {
            text = config.text ?: ""
            textSize = config.textSize
            setTextColor(config.textColor)
            setTypeface(null, Typeface.BOLD)
            setPadding(config.padding, config.padding / 2, config.padding, config.padding / 2)
            setBackgroundColor(config.backgroundColor)
            gravity = config.gravity
            visibility = if (config.isVisible) View.VISIBLE else View.GONE
        }
    }
    
    private fun createButton(config: UIElementConfig): Button {
        return Button(this).apply {
            text = config.text ?: ""
            textSize = config.textSize
            setPadding(config.padding, config.padding / 2, config.padding, config.padding / 2)
            visibility = if (config.isVisible) View.VISIBLE else View.GONE
            config.onClick?.let { onClickListener ->
                setOnClickListener { onClickListener() }
            }
        }
    }
    
    private fun createLinearLayout(
        orientation: Int = LinearLayout.VERTICAL,
        backgroundColor: Int = Color.TRANSPARENT,
        gravity: Int = Gravity.CENTER
    ): LinearLayout {
        return LinearLayout(this).apply {
            this.orientation = orientation
            setBackgroundColor(backgroundColor)
            this.gravity = gravity
        }
    }
    
    private fun createListView(adapter: BaseAdapter? = null): ListView {
        return ListView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            divider = null
            isVerticalScrollBarEnabled = true
            adapter?.let { this.adapter = it }
        }
    }
    
    // Custom adapter for touch events
    inner class TouchEventAdapter(
        private val context: Context,
        private val touchEvents: MutableList<TouchEvent>,
        private val swipeEvents: MutableList<SwipeEvent>
    ) : BaseAdapter() {
        
        private val allEvents = mutableListOf<Any>()
        
        init {
            updateEvents()
        }
        
        fun updateEvents() {
            allEvents.clear()
            allEvents.addAll(touchEvents)
            allEvents.addAll(swipeEvents)
            allEvents.sortByDescending { 
                when (it) {
                    is TouchEvent -> it.timestamp
                    is SwipeEvent -> it.timestamp
                    else -> 0L
                }
            }
            notifyDataSetChanged()
        }
        
        override fun getCount(): Int = allEvents.size
        
        override fun getItem(position: Int): Any = allEvents[position]
        
        override fun getItemId(position: Int): Long = position.toLong()
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false)
            val text1 = view.findViewById<TextView>(android.R.id.text1)
            val text2 = view.findViewById<TextView>(android.R.id.text2)
            
            val event = allEvents[position]
            val isNewest = position == 0 // First item is newest
            
            when (event) {
                is TouchEvent -> {
                    val actionText = if (isNewest) "🆕 ${event.action}" else event.action
                    text1.text = "$actionText at (${event.x.toInt()}, ${event.y.toInt()})"
                    text2.text = dateFormat.format(Date(event.timestamp))
                    text1.setTextColor(if (isNewest) Color.GREEN else Color.WHITE)
                    text2.setTextColor(Color.LTGRAY)
                }
                is SwipeEvent -> {
                    val actionText = if (isNewest) "🆕 SWIPE ${event.direction}" else "SWIPE ${event.direction}"
                    text1.text = "$actionText (${event.velocity.toInt()}px/s)"
                    text2.text = "${dateFormat.format(Date(event.timestamp))} - ${event.duration}ms"
                    text1.setTextColor(if (isNewest) Color.GREEN else Color.CYAN)
                    text2.setTextColor(Color.LTGRAY)
                }
            }
            
            view.setBackgroundColor(Color.argb(100, 0, 0, 0))
            return view
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        showOverlay()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideFpsOverlay()
    }
    
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // Check if we should show/hide overlay based on orientation or fullscreen mode
        updateOverlayVisibility()
        
        // Capture touch events
        event?.let { accessibilityEvent ->
            when (accessibilityEvent.eventType) {
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                    recordTouchEvent("CLICK", accessibilityEvent)
                }
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                    recordTouchEvent("LONG_CLICK", accessibilityEvent)
                }
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    recordSwipeEvent("SCROLL", accessibilityEvent)
                }
                android.view.accessibility.AccessibilityEvent.TYPE_GESTURE_DETECTION_START -> {
                    recordTouchEvent("GESTURE_START", accessibilityEvent)
                }
                android.view.accessibility.AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> {
                    recordTouchEvent("GESTURE_END", accessibilityEvent)
                }
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                    recordTouchEvent("FOCUS", accessibilityEvent)
                }
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                    recordTouchEvent("SELECT", accessibilityEvent)
                }
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    recordTouchEvent("TEXT_CHANGE", accessibilityEvent)
                }
            }
        }
    }
    
    override fun onInterrupt() {
        hideFpsOverlay()
    }
    
    private fun updateOverlayVisibility() {
        val shouldShow = shouldShowOverlay()
        if (shouldShow && !isOverlayVisible) {
            showOverlay()
        } else if (!shouldShow && isOverlayVisible) {
            hideOverlay()
        }
    }
    
    private fun shouldShowOverlay(): Boolean {
        val display = windowManager?.defaultDisplay
        val rotation = display?.rotation
        
        // Show in landscape mode
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        
        // Check if any app is in fullscreen mode (simplified check)
        val isFullscreen = isInFullscreenMode()
        
        return isLandscape || isFullscreen
    }
    
    private fun isInFullscreenMode(): Boolean {
        // This is a simplified check - in a real implementation, you'd need more sophisticated detection
        return false // For now, we'll rely on orientation detection
    }
    
    private fun recordTouchEvent(action: String, event: android.view.accessibility.AccessibilityEvent) {
        val bounds = android.graphics.Rect()
        event.source?.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            val touchEvent = TouchEvent(
                action = action,
                x = bounds.centerX().toFloat(),
                y = bounds.centerY().toFloat(),
                timestamp = System.currentTimeMillis()
            )
            
            synchronized(touchEvents) {
                touchEvents.add(touchEvent)
                if (touchEvents.size > maxLogEntries) {
                    touchEvents.removeAt(0)
                }
            }
            
            touchEventAdapter?.updateEvents()
        }
    }
    
    private fun recordSwipeEvent(action: String, event: android.view.accessibility.AccessibilityEvent) {
        val bounds = android.graphics.Rect()
        event.source?.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            val currentTime = System.currentTimeMillis()
            
            if (!isTrackingSwipe) {
                // Start tracking swipe
                touchStartTime = currentTime
                touchStartX = bounds.centerX().toFloat()
                touchStartY = bounds.centerY().toFloat()
                isTrackingSwipe = true
            } else {
                // End tracking swipe
                val endX = bounds.centerX().toFloat()
                val endY = bounds.centerY().toFloat()
                val duration = currentTime - touchStartTime
                
                val deltaX = endX - touchStartX
                val deltaY = endY - touchStartY
                val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
                val velocity = if (duration > 0) distance / duration * 1000 else 0f
                
                val direction = when {
                    kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) -> if (deltaX > 0) "RIGHT" else "LEFT"
                    else -> if (deltaY > 0) "DOWN" else "UP"
                }
                
                val swipeEvent = SwipeEvent(
                    startX = touchStartX,
                    startY = touchStartY,
                    endX = endX,
                    endY = endY,
                    duration = duration,
                    velocity = velocity,
                    direction = direction,
                    timestamp = currentTime
                )
                
                synchronized(swipeEvents) {
                    swipeEvents.add(swipeEvent)
                    if (swipeEvents.size > maxLogEntries) {
                        swipeEvents.removeAt(0)
                    }
                }
                
                isTrackingSwipe = false
                touchEventAdapter?.updateEvents()
            }
        }
    }
    
    /**
     * Generalized overlay creation method
     * 
     * Creates a system overlay with the specified configuration and content view.
     * This is the core method that all other overlay creation methods use.
     * 
     * @param config The overlay configuration (position, size, flags, etc.)
     * @param contentView The view to display in the overlay
     * @return true if overlay was created successfully, false otherwise
     */
    private fun createOverlay(config: OverlayConfig, contentView: View): Boolean {
        if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                isOverlayVisible || !Settings.canDrawOverlays(this)
            } else {
                TODO("VERSION.SDK_INT < M")
            }
        ) {
            return false
        }
        
        val layoutParams = WindowManager.LayoutParams(
            config.width,
            config.height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            config.flags,
            config.pixelFormat
        )
        
        layoutParams.gravity = config.gravity
        layoutParams.x = config.x
        layoutParams.y = config.y
        
        overlayView = contentView
        
        // Make the overlay draggable by adding touch listener
        overlayView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Store initial touch position
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Update overlay position
                    layoutParams.x = (event.rawX - contentView.width / 2).toInt()
                    layoutParams.y = (event.rawY - contentView.height / 2).toInt()
                    windowManager?.updateViewLayout(overlayView, layoutParams)
                    true
                }
                else -> false
            }
        }
        
        windowManager?.addView(overlayView, layoutParams)
        isOverlayVisible = true
        
        return true
    }
    
    /**
     * Generic overlay management methods
     * 
     * These methods provide a flexible API for creating different types of overlays
     * with various configurations and UI elements.
     */
    
    /**
     * Shows the main FPS and touch recording overlay
     */
    private fun showOverlay() {
        val display = windowManager?.defaultDisplay
        val size = android.graphics.Point()
        display?.getSize(size)
        
        val overlayConfig = OverlayConfig(
            width = (size.x * 0.4f).toInt(), // 40% of screen width
            height = (size.y * 0.6f).toInt(), // 60% of screen height
            gravity = Gravity.TOP or Gravity.END,
            x = 20,
            y = 100
        )
        
        val contentView = createFpsOverlayContent()
        
        if (createOverlay(overlayConfig, contentView)) {
        startFpsCounter()
        }
    }
    
    // Legacy method for backward compatibility
    private fun showFpsOverlay() {
        showOverlay()
    }
    
    // Method to create custom overlays with different configurations
    private fun showCustomOverlay(
        config: OverlayConfig,
        contentBuilder: () -> View
    ): Boolean {
        val contentView = contentBuilder()
        return createOverlay(config, contentView)
    }
    
    // Example method for creating a simple text overlay
    private fun showTextOverlay(
        text: String,
        position: Pair<Int, Int> = Pair(20, 100),
        size: Pair<Int, Int> = Pair(300, 100)
    ): Boolean {
        val config = OverlayConfig(
            width = size.first,
            height = size.second,
            gravity = Gravity.TOP or Gravity.END,
            x = position.first,
            y = position.second
        )
        
        val contentView = createTextView(
            UIElementConfig(
                type = UIElementType.TEXT_VIEW,
                text = text,
                textSize = 16f,
                textColor = Color.WHITE,
                backgroundColor = Color.argb(180, 0, 0, 0),
                padding = 16,
                gravity = Gravity.CENTER
            )
        )
        
        return createOverlay(config, contentView)
    }
    
    // Method to create a button overlay
    private fun showButtonOverlay(
        text: String,
        onClick: () -> Unit,
        position: Pair<Int, Int> = Pair(20, 200),
        size: Pair<Int, Int> = Pair(200, 80)
    ): Boolean {
        val config = OverlayConfig(
            width = size.first,
            height = size.second,
            gravity = Gravity.TOP or Gravity.END,
            x = position.first,
            y = position.second
        )
        
        val contentView = createButton(
            UIElementConfig(
                type = UIElementType.BUTTON,
                text = text,
                textSize = 14f,
                padding = 16,
                onClick = onClick
            )
        )
        
        return createOverlay(config, contentView)
    }
    
    // Method to create a custom overlay with multiple elements
    private fun showCustomMultiElementOverlay(
        elements: List<UIElementConfig>,
        layout: Int = LinearLayout.VERTICAL,
        position: Pair<Int, Int> = Pair(20, 300),
        size: Pair<Int, Int> = Pair(400, 300)
    ): Boolean {
        val config = OverlayConfig(
            width = size.first,
            height = size.second,
            gravity = Gravity.TOP or Gravity.END,
            x = position.first,
            y = position.second
        )
        
        val container = createLinearLayout(
            orientation = layout,
            backgroundColor = Color.argb(200, 0, 0, 0)
        )
        
        elements.forEach { elementConfig ->
            val view = when (elementConfig.type) {
                UIElementType.TEXT_VIEW -> createTextView(elementConfig)
                UIElementType.BUTTON -> createButton(elementConfig)
                UIElementType.LIST_VIEW -> createListView()
                else -> createTextView(elementConfig)
            }
            container.addView(view)
        }
        
        return createOverlay(config, container)
    }
    
    private fun createFpsOverlayContent(): View {
        // Create main container
        val mainContainer = createLinearLayout(
            orientation = LinearLayout.VERTICAL,
            backgroundColor = Color.TRANSPARENT
        )
        
        // FPS display at top
        fpsTextView = createTextView(
            UIElementConfig(
                type = UIElementType.TEXT_VIEW,
                text = "FPS: 0",
                textSize = 16f,
                textColor = Color.WHITE,
                backgroundColor = Color.argb(180, 0, 0, 0),
                padding = 24,
                gravity = Gravity.CENTER
            )
        )
        
        // Control buttons container
        val controlContainer = createLinearLayout(
            orientation = LinearLayout.HORIZONTAL,
            backgroundColor = Color.argb(150, 0, 0, 0),
            gravity = Gravity.CENTER
        )
        
        val toggleLogButton = createButton(
            UIElementConfig(
                type = UIElementType.BUTTON,
                text = if (isLogVisible) "Hide Log" else "Show Log",
                textSize = 12f,
                padding = 16,
                onClick = null // Will be set after button creation
            )
        )
        
        // Set the click listener after button creation
        toggleLogButton.setOnClickListener {
            isLogVisible = !isLogVisible
            logContainer?.visibility = if (isLogVisible) View.VISIBLE else View.GONE
            toggleLogButton.text = if (isLogVisible) "Hide Log" else "Show Log"
        }
        
        val clearButton = createButton(
            UIElementConfig(
                type = UIElementType.BUTTON,
                text = "Clear Log",
                textSize = 12f,
                padding = 16,
                onClick = {
                    synchronized(touchEvents) { touchEvents.clear() }
                    synchronized(swipeEvents) { swipeEvents.clear() }
                    touchEventAdapter?.updateEvents()
                }
            )
        )
        
        controlContainer.addView(toggleLogButton)
        controlContainer.addView(clearButton)
        
        // Touch log container
        logContainer = createLinearLayout(
            orientation = LinearLayout.VERTICAL,
            backgroundColor = Color.argb(200, 0, 0, 0)
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        
        // Log header
        val logHeader = createTextView(
            UIElementConfig(
                type = UIElementType.TEXT_VIEW,
                text = "Touch Events Log (Newest First)",
                textSize = 14f,
                textColor = Color.YELLOW,
                backgroundColor = Color.argb(150, 0, 0, 0),
                padding = 16,
                gravity = Gravity.CENTER
            )
        )
        
        // Touch log list
        touchLogListView = createListView()
        
        // Initialize adapter
        touchEventAdapter = TouchEventAdapter(this, touchEvents, swipeEvents)
        touchLogListView?.adapter = touchEventAdapter
        
        // Add views to containers
        logContainer?.addView(logHeader)
        logContainer?.addView(touchLogListView)
        
        mainContainer.addView(fpsTextView)
        mainContainer.addView(controlContainer)
        mainContainer.addView(logContainer)
        
        return mainContainer
    }
    
    // Generic overlay hiding method
    private fun hideOverlay() {
        fpsUpdateJob?.cancel()
        fpsUpdateJob = null
        
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
        fpsTextView = null
        touchLogListView = null
        touchEventAdapter = null
        logContainer = null
        isOverlayVisible = false
    }
    
    // Legacy method for backward compatibility
    private fun hideFpsOverlay() {
        hideOverlay()
    }
    
    private fun startFpsCounter() {
        fpsUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isOverlayVisible) {
                val currentTime = System.currentTimeMillis()
                frameCount++
                
                if (currentTime - lastFpsUpdate >= 1000) { // Update FPS every second
                    fpsCounter = frameCount
                    frameCount = 0
                    lastFpsUpdate = currentTime
                    
                    // Update UI on main thread
                    fpsTextView?.text = "FPS: $fpsCounter"
                }
                
                delay(16) // ~60 FPS update rate
            }
        }
    }
}

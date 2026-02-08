package com.trainingvalidator.poc.ui.components

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.trainingvalidator.poc.R

/**
 * GlassmorphicMessageView - A modern glassmorphic message pill component
 * 
 * FIXED: Proper layering - blur background BEHIND content, not on content itself.
 * 
 * Structure:
 * - Layer 0: Blur background (semi-transparent with blur effect)
 * - Layer 1: Content (icon + text) - NOT blurred
 * 
 * Features:
 * - Frosted glass background with blur effect
 * - Smooth fade-in/fade-out animations
 * - Support for different message types (TIP, WARNING, ERROR, MOTIVATION)
 * - Auto-dismiss functionality
 * - Queue support for multiple messages
 */
class GlassmorphicMessageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        // Message types
        const val TYPE_TIP = 0
        const val TYPE_WARNING = 1
        const val TYPE_ERROR = 2
        const val TYPE_MOTIVATION = 3
        const val TYPE_INFO = 4
        
        // Timing
        private const val ANIMATION_DURATION = 300L
        private const val DEFAULT_DISPLAY_DURATION = 3000L
        private const val ERROR_DISPLAY_DURATION = 4000L
        
        // Colors
        private val COLOR_TIP_ACCENT = Color.parseColor("#2196F3")        // Blue
        private val COLOR_WARNING_ACCENT = Color.parseColor("#FFC107")    // Amber
        private val COLOR_ERROR_ACCENT = Color.parseColor("#FF5252")      // Red
        private val COLOR_MOTIVATION_ACCENT = Color.parseColor("#00E676") // Green
        private val COLOR_INFO_ACCENT = Color.parseColor("#9E9E9E")       // Gray
        
        // Glass background
        private val COLOR_GLASS_BACKGROUND = Color.parseColor("#40000000") // 25% Black (more visible)
        private val COLOR_GLASS_BORDER = Color.parseColor("#33FFFFFF")     // 20% White
    }
    
    // Views - LAYERED PROPERLY
    private val blurBackgroundView: BlurBackgroundView
    private val contentLayout: LinearLayout
    private val iconView: ImageView
    private val messageText: TextView
    
    // State
    private var isShowing = false
    private var dismissRunnable: Runnable? = null
    private val messageQueue = mutableListOf<QueuedMessage>()
    
    // Configuration
    private val cornerRadiusPx = 24f.dpToPx()
    private var currentAccentColor = COLOR_INFO_ACCENT
    
    data class QueuedMessage(
        val text: String,
        val type: Int,
        val durationMs: Long
    )
    
    init {
        // Layer 0: Blur background (custom view that draws blurred background)
        // Wrap content - we will position/size it to match contentLayout in onLayout()
        blurBackgroundView = BlurBackgroundView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        
        // Layer 1: Content layout (NOT blurred)
        contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dpToPx().toInt(), 12.dpToPx().toInt(), 20.dpToPx().toInt(), 12.dpToPx().toInt())
            // Transparent background - the blur view behind handles the glass effect
            setBackgroundColor(Color.TRANSPARENT)
        }
        
        // Create icon view
        iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(24.dpToPx().toInt(), 24.dpToPx().toInt())
        }
        
        // Create message text
        messageText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 12.dpToPx().toInt()
            }
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 2
            setShadowLayer(3f, 1f, 1f, Color.parseColor("#80000000"))
        }
        
        // Assemble content
        contentLayout.addView(iconView)
        contentLayout.addView(messageText)
        
        // Add layers in order (blur behind, content on top)
        addView(blurBackgroundView)
        addView(contentLayout)
        
        // Initially hidden
        alpha = 0f
        visibility = View.GONE
    }
    
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Size and position blur background to match content, without changing layoutParams
        val contentLeft = contentLayout.left
        val contentTop = contentLayout.top
        val contentRight = contentLayout.right
        val contentBottom = contentLayout.bottom
        blurBackgroundView.layout(contentLeft, contentTop, contentRight, contentBottom)
    }
    
    /**
     * Show a message with specified type
     */
    fun showMessage(text: String, type: Int, durationMs: Long = DEFAULT_DISPLAY_DURATION) {
        if (isShowing) {
            // Queue the message
            messageQueue.add(QueuedMessage(text, type, durationMs))
            return
        }
        
        displayMessage(text, type, durationMs)
    }
    
    fun showTip(text: String) = showMessage(text, TYPE_TIP)
    fun showWarning(text: String) = showMessage(text, TYPE_WARNING)
    fun showError(text: String) = showMessage(text, TYPE_ERROR, ERROR_DISPLAY_DURATION)
    fun showMotivation(text: String) = showMessage(text, TYPE_MOTIVATION)
    
    /**
     * Display the message with animation
     */
    private fun displayMessage(text: String, type: Int, durationMs: Long) {
        isShowing = true
        
        // Update content
        messageText.text = text
        updateStyleForType(type)
        
        // Cancel any pending dismiss
        dismissRunnable?.let { removeCallbacks(it) }
        
        // Show with animation
        visibility = View.VISIBLE
        animateIn {
            // Schedule auto-dismiss (using local val for null-safety)
            val runnable = Runnable { hide() }
            dismissRunnable = runnable
            postDelayed(runnable, durationMs)
        }
    }
    
    /**
     * Update styling based on message type
     */
    private fun updateStyleForType(type: Int) {
        val (iconRes, accentColor) = when (type) {
            TYPE_TIP -> R.drawable.ic_tip to COLOR_TIP_ACCENT
            TYPE_WARNING -> R.drawable.ic_warning to COLOR_WARNING_ACCENT
            TYPE_ERROR -> R.drawable.ic_error to COLOR_ERROR_ACCENT
            TYPE_MOTIVATION -> R.drawable.ic_motivation to COLOR_MOTIVATION_ACCENT
            else -> R.drawable.ic_info to COLOR_INFO_ACCENT
        }
        
        currentAccentColor = accentColor
        
        // Update icon
        try {
            iconView.setImageResource(iconRes)
            iconView.setColorFilter(accentColor)
            iconView.visibility = View.VISIBLE
        } catch (e: Exception) {
            iconView.visibility = View.GONE
        }
        
        // Update blur background accent
        blurBackgroundView.updateAccentColor(accentColor)
    }
    
    /**
     * Animate the view in
     */
    private fun animateIn(onComplete: () -> Unit) {
        translationY = -20.dpToPx()
        
        val fadeIn = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f)
        val slideIn = ObjectAnimator.ofFloat(this, "translationY", -20.dpToPx(), 0f)
        
        AnimatorSet().apply {
            playTogether(fadeIn, slideIn)
            duration = ANIMATION_DURATION
            interpolator = OvershootInterpolator(0.8f)
            start()
        }
        
        postDelayed({ onComplete() }, ANIMATION_DURATION)
    }
    
    /**
     * Hide the message with animation
     */
    fun hide() {
        if (!isShowing) return
        
        animateOut {
            visibility = View.GONE
            isShowing = false
            
            // Show next queued message
            if (messageQueue.isNotEmpty()) {
                val next = messageQueue.removeAt(0)
                displayMessage(next.text, next.type, next.durationMs)
            }
        }
    }
    
    private fun animateOut(onComplete: () -> Unit) {
        val fadeOut = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f)
        val slideOut = ObjectAnimator.ofFloat(this, "translationY", 0f, -20.dpToPx())
        
        AnimatorSet().apply {
            playTogether(fadeOut, slideOut)
            duration = 200
            interpolator = DecelerateInterpolator()
            start()
        }
        
        postDelayed({ onComplete() }, 200)
    }
    
    fun clearAll() {
        dismissRunnable?.let { removeCallbacks(it) }
        messageQueue.clear()
        
        if (isShowing) {
            visibility = View.GONE
            alpha = 0f
            isShowing = false
        }
    }
    
    // Extension functions
    private fun Float.dpToPx(): Float = this * context.resources.displayMetrics.density
    private fun Int.dpToPx(): Float = this.toFloat().dpToPx()
    
    /**
     * Custom view for blur background - draws the glass effect
     * This view is BEHIND the content, so blur doesn't affect text
     */
    private inner class BlurBackgroundView(context: Context) : View(context) {
        
        private var viewWidth = 0f
        private var viewHeight = 0f
        private var accentColor = COLOR_INFO_ACCENT
        
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = COLOR_GLASS_BACKGROUND
        }
        
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f.dpToPx()
            color = COLOR_GLASS_BORDER
        }
        
        private val accentBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f.dpToPx()
        }
        
        private val rect = RectF()
        
        init {
            // Enable software layer for blur effect on older devices
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            
            // Apply blur to background paint for frosted effect
            backgroundPaint.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        }
        
        /**
         * Called automatically when this view's size changes (via MATCH_PARENT).
         * Replaces the old updateBounds() call from onLayout() - no requestLayout() loop.
         */
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            viewWidth = w.toFloat()
            viewHeight = h.toFloat()
        }
        
        fun updateAccentColor(color: Int) {
            accentColor = color
            accentBorderPaint.color = color
            invalidate()
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            if (viewWidth <= 0 || viewHeight <= 0) return
            
            rect.set(2f, 2f, viewWidth - 2f, viewHeight - 2f)
            
            // Draw frosted glass background
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, backgroundPaint)
            
            // Draw subtle white border
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, borderPaint)
            
            // Draw accent color border
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, accentBorderPaint)
        }
        
        private fun Float.dpToPx(): Float = this * context.resources.displayMetrics.density
    }
}

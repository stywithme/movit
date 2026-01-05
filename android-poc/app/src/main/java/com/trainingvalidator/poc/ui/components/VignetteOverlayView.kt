package com.trainingvalidator.poc.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * VignetteOverlayView - Ambient peripheral alert system
 * 
 * Creates subtle vignette effects at screen edges to alert the user
 * using peripheral vision without blocking the main content.
 * 
 * Features:
 * - Warning mode: Soft amber glow at edges
 * - Error mode: Red glow with pulse animation
 * - Smooth transitions between states
 * - Non-intrusive design
 */
class VignetteOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // Alert modes
        const val MODE_NONE = 0
        const val MODE_WARNING = 1
        const val MODE_ERROR = 2
        
        // Colors
        private val COLOR_WARNING = Color.parseColor("#FFC107")  // Amber
        private val COLOR_ERROR = Color.parseColor("#FF5252")    // Red
        
        // Animation
        private const val PULSE_DURATION = 1500L
        private const val FADE_DURATION = 300L
        
        // Vignette intensity
        private const val MAX_ALPHA = 0.35f
        private const val MIN_PULSE_ALPHA = 0.15f
    }
    
    // State
    private var currentMode = MODE_NONE
    private var currentAlpha = 0f
    private var targetAlpha = 0f
    private var vignetteColor = COLOR_WARNING
    
    // Paint
    private val vignettePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    // Animators
    private var pulseAnimator: ValueAnimator? = null
    private var fadeAnimator: ValueAnimator? = null
    
    // Gradient shader (created in onSizeChanged)
    private var vignetteGradient: RadialGradient? = null
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGradient()
    }
    
    /**
     * Update the radial gradient for vignette effect
     */
    private fun updateGradient() {
        if (width <= 0 || height <= 0) return
        
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = maxOf(width, height) * 0.8f
        
        vignetteGradient = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                adjustAlpha(vignetteColor, currentAlpha * 0.5f),
                adjustAlpha(vignetteColor, currentAlpha)
            ),
            floatArrayOf(0f, 0.5f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        
        vignettePaint.shader = vignetteGradient
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (currentMode == MODE_NONE || currentAlpha <= 0.01f) return
        
        // Draw vignette overlay
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
    }
    
    /**
     * Show warning alert (amber glow)
     */
    fun showWarning() {
        if (currentMode == MODE_WARNING) return
        
        stopAnimations()
        currentMode = MODE_WARNING
        vignetteColor = COLOR_WARNING
        
        fadeIn(MAX_ALPHA * 0.7f)
    }
    
    /**
     * Show error alert (red pulsing glow)
     */
    fun showError() {
        if (currentMode == MODE_ERROR) return
        
        stopAnimations()
        currentMode = MODE_ERROR
        vignetteColor = COLOR_ERROR
        
        fadeIn(MAX_ALPHA)
        startPulse()
    }
    
    /**
     * Clear all alerts
     */
    fun clear() {
        if (currentMode == MODE_NONE) return
        
        stopAnimations()
        fadeOut {
            currentMode = MODE_NONE
        }
    }
    
    /**
     * Fade in the vignette
     */
    private fun fadeIn(toAlpha: Float) {
        targetAlpha = toAlpha
        
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(currentAlpha, toAlpha).apply {
            duration = FADE_DURATION
            addUpdateListener { animator ->
                currentAlpha = animator.animatedValue as Float
                updateGradient()
                invalidate()
            }
            start()
        }
    }
    
    /**
     * Fade out the vignette
     */
    private fun fadeOut(onComplete: () -> Unit) {
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(currentAlpha, 0f).apply {
            duration = FADE_DURATION
            addUpdateListener { animator ->
                currentAlpha = animator.animatedValue as Float
                updateGradient()
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onComplete()
                }
            })
            start()
        }
    }
    
    /**
     * Start pulse animation for error mode
     */
    private fun startPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(MAX_ALPHA, MIN_PULSE_ALPHA, MAX_ALPHA).apply {
            duration = PULSE_DURATION
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                currentAlpha = animator.animatedValue as Float
                updateGradient()
                invalidate()
            }
            start()
        }
    }
    
    /**
     * Stop all animations
     */
    private fun stopAnimations() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        fadeAnimator?.cancel()
        fadeAnimator = null
    }
    
    /**
     * Adjust color alpha
     */
    private fun adjustAlpha(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimations()
    }
}

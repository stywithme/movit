package com.trainingvalidator.poc.ui.components

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.core.view.ViewCompat

/**
 * AnimationUtils - Smart animation utilities for the training UI
 * 
 * Provides functional animations that serve UX purposes:
 * - Slot Machine Counter: Smooth number transitions
 * - Pulse Effects: Visual feedback for events
 * - Fade Transitions: Smooth state changes
 * - Bounce Effects: Emphasis animations
 */
object AnimationUtils {
    
    // ===================== Slot Machine Counter =====================
    
    /**
     * Animate a number change with slot machine effect
     * The old number slides up and fades out, new number slides in from below
     */
    fun animateCounterChange(
        textView: TextView,
        newValue: String,
        direction: SlideDirection = SlideDirection.UP,
        durationMs: Long = 250
    ) {
        val slideDistance = 50f * (if (direction == SlideDirection.UP) -1 else 1)
        
        // Animate out current value
        textView.animate()
            .translationY(slideDistance)
            .alpha(0f)
            .setDuration(durationMs / 2)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // Update text
                textView.text = newValue
                
                // Reset position for slide in
                textView.translationY = -slideDistance
                
                // Animate in new value
                textView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(durationMs / 2)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }
    
    enum class SlideDirection {
        UP, DOWN
    }
    
    // ===================== Pulse Effects =====================
    
    /**
     * Pulse scale animation for emphasis
     */
    fun pulseScale(view: View, scale: Float = 1.2f, durationMs: Long = 150) {
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(durationMs / 2)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(durationMs / 2)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }
    
    /**
     * Color pulse animation for feedback
     */
    fun pulseColor(textView: TextView, color: Int, originalColor: Int, durationMs: Long = 500) {
        textView.setTextColor(color)
        textView.postDelayed({
            textView.setTextColor(originalColor)
        }, durationMs)
    }
    
    /**
     * Combined pulse (scale + color) for rep completed
     */
    fun repCompletedPulse(
        textView: TextView,
        isCorrect: Boolean,
        correctColor: Int,
        incorrectColor: Int,
        originalColor: Int
    ) {
        val pulseColor = if (isCorrect) correctColor else incorrectColor
        
        // Scale pulse
        pulseScale(textView, 1.15f, 200)
        
        // Color pulse
        pulseColor(textView, pulseColor, originalColor, 500)
    }
    
    // ===================== Countdown Animations =====================
    
    /**
     * Countdown number animation with scale down effect
     */
    fun animateCountdown(textView: TextView, number: String, onComplete: () -> Unit = {}) {
        // Start large
        textView.scaleX = 1.5f
        textView.scaleY = 1.5f
        textView.alpha = 0f
        textView.text = number
        
        val duration = 800L
        
        // Animate: fade in + scale down
        textView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(2f))
            .withEndAction(onComplete)
            .start()
    }
    
    /**
     * "Go!" animation with scale up and bounce
     * @param goLabel Localized overlay text (e.g. from system message training_go_overlay)
     */
    fun animateGoText(textView: TextView, goLabel: String = "GO!", onComplete: () -> Unit = {}) {
        textView.scaleX = 0.5f
        textView.scaleY = 0.5f
        textView.alpha = 0f
        textView.text = goLabel
        
        textView.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(2f))
            .withEndAction {
                // Fade out
                textView.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setStartDelay(200)
                    .withEndAction(onComplete)
                    .start()
            }
            .start()
    }
    
    // ===================== Phase Transitions =====================
    
    /**
     * Crossfade text transition for phase changes
     */
    fun crossfadeText(textView: TextView, newText: String, durationMs: Long = 200) {
        textView.animate()
            .alpha(0f)
            .setDuration(durationMs / 2)
            .withEndAction {
                textView.text = newText
                textView.animate()
                    .alpha(1f)
                    .setDuration(durationMs / 2)
                    .start()
            }
            .start()
    }
    
    // ===================== Panel Transitions =====================
    
    /**
     * Slide and fade in a panel
     */
    fun slideInPanel(view: View, fromDirection: Direction = Direction.BOTTOM, durationMs: Long = 300) {
        val distance = 100f
        
        when (fromDirection) {
            Direction.BOTTOM -> view.translationY = distance
            Direction.TOP -> view.translationY = -distance
            Direction.LEFT -> view.translationX = -distance
            Direction.RIGHT -> view.translationX = distance
        }
        
        view.alpha = 0f
        view.visibility = View.VISIBLE
        
        view.animate()
            .translationX(0f)
            .translationY(0f)
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    /**
     * Slide and fade out a panel
     */
    fun slideOutPanel(view: View, toDirection: Direction = Direction.BOTTOM, durationMs: Long = 200, onComplete: () -> Unit = {}) {
        val distance = 100f
        
        val (targetX, targetY) = when (toDirection) {
            Direction.BOTTOM -> 0f to distance
            Direction.TOP -> 0f to -distance
            Direction.LEFT -> -distance to 0f
            Direction.RIGHT -> distance to 0f
        }
        
        view.animate()
            .translationX(targetX)
            .translationY(targetY)
            .alpha(0f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.translationX = 0f
                view.translationY = 0f
                onComplete()
            }
            .start()
    }
    
    enum class Direction {
        TOP, BOTTOM, LEFT, RIGHT
    }
    
    // ===================== Bounce Effect =====================
    
    /**
     * Bounce in animation for emphasis
     */
    fun bounceIn(view: View, durationMs: Long = 400) {
        view.scaleX = 0f
        view.scaleY = 0f
        view.alpha = 0f
        view.visibility = View.VISIBLE
        
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()
    }
    
    // ===================== Shake Effect =====================
    
    /**
     * Shake animation for error feedback
     */
    fun shake(view: View, intensity: Float = 10f, durationMs: Long = 400) {
        val shakeAnimator = ObjectAnimator.ofFloat(
            view,
            View.TRANSLATION_X,
            0f, intensity, -intensity, intensity, -intensity, intensity / 2, -intensity / 2, 0f
        ).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        shakeAnimator.start()
    }
    
    // ===================== Skeleton Glow Effect =====================
    
    /**
     * Create a pulsing glow effect value animator
     * Returns a ValueAnimator that provides alpha values between 0.3 and 1.0
     */
    fun createGlowAnimator(durationMs: Long = 1500): ValueAnimator {
        return ValueAnimator.ofFloat(0.5f, 1f, 0.5f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
    }
    
    // ===================== Progress Animations =====================
    
    /**
     * Animate progress bar smoothly
     */
    fun animateProgress(view: View, toProgress: Float, durationMs: Long = 300) {
        val animator = ObjectAnimator.ofFloat(view, "progress", toProgress)
        animator.duration = durationMs
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }
}

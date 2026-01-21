package com.trainingvalidator.poc.training.report

import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * StateDisplayConfig - User-friendly display names and icons for JointStates
 * 
 * Converts internal state names (PERFECT, NORMAL, etc.) to user-friendly
 * localized names that are easier to understand.
 * 
 * Internal → Display:
 * - PERFECT  → "مثالي!" / "Excellent!"
 * - NORMAL   → "جيد" / "Good"
 * - PAD      → "مقبول" / "Acceptable"
 * - WARNING  → "يحتاج تحسين" / "Needs Work"
 * - DANGER   → "خطر - انتبه!" / "Danger - Caution!"
 */
object StateDisplayConfig {
    
    /**
     * Display information for a state
     */
    data class DisplayInfo(
        val nameAr: String,
        val nameEn: String,
        val icon: String,
        val colorRes: Int,
        val priority: DisplayPriority
    ) {
        /**
         * Get localized name
         */
        fun getName(isArabic: Boolean): String = if (isArabic) nameAr else nameEn
        
        /**
         * Get as LocalizedText
         */
        fun toLocalizedText(): LocalizedText = LocalizedText(ar = nameAr, en = nameEn)
    }
    
    /**
     * Display priority - determines how to present the state to user
     */
    enum class DisplayPriority {
        CELEBRATE,    // PERFECT - show with celebration 🎉
        POSITIVE,     // NORMAL - positive feedback
        NEUTRAL,      // PAD - neutral, can improve
        ATTENTION,    // WARNING - needs attention
        CRITICAL      // DANGER - critical alert 🚨
    }
    
    /**
     * Pre-defined display configurations for each state
     */
    private val DISPLAY_MAP = mapOf(
        JointState.PERFECT to DisplayInfo(
            nameAr = "مثالي!",
            nameEn = "Excellent!",
            icon = "⭐",
            colorRes = R.color.state_perfect,
            priority = DisplayPriority.CELEBRATE
        ),
        JointState.NORMAL to DisplayInfo(
            nameAr = "جيد",
            nameEn = "Good",
            icon = "✓",
            colorRes = R.color.state_normal,
            priority = DisplayPriority.POSITIVE
        ),
        JointState.PAD to DisplayInfo(
            nameAr = "مقبول",
            nameEn = "Acceptable",
            icon = "~",
            colorRes = R.color.state_pad,
            priority = DisplayPriority.NEUTRAL
        ),
        JointState.WARNING to DisplayInfo(
            nameAr = "يحتاج تحسين",
            nameEn = "Needs Work",
            icon = "⚠️",
            colorRes = R.color.state_warning,
            priority = DisplayPriority.ATTENTION
        ),
        JointState.DANGER to DisplayInfo(
            nameAr = "خطر - انتبه!",
            nameEn = "Danger - Caution!",
            icon = "🚨",
            colorRes = R.color.state_danger,
            priority = DisplayPriority.CRITICAL
        ),
        JointState.TRANSITION to DisplayInfo(
            nameAr = "انتقال",
            nameEn = "Moving",
            icon = "→",
            colorRes = R.color.state_transition,
            priority = DisplayPriority.NEUTRAL
        )
    )
    
    // ==================== Public API ====================
    
    /**
     * Get display info for a state
     */
    fun getDisplayInfo(state: JointState): DisplayInfo {
        return DISPLAY_MAP[state] ?: DISPLAY_MAP[JointState.NORMAL]!!
    }
    
    /**
     * Get user-friendly name for a state
     */
    fun getDisplayName(state: JointState, isArabic: Boolean = false): String {
        return getDisplayInfo(state).getName(isArabic)
    }
    
    /**
     * Get icon for a state
     */
    fun getIcon(state: JointState): String {
        return getDisplayInfo(state).icon
    }
    
    /**
     * Get color resource for a state
     */
    fun getColorRes(state: JointState): Int {
        return getDisplayInfo(state).colorRes
    }
    
    /**
     * Get display priority for a state
     */
    fun getPriority(state: JointState): DisplayPriority {
        return getDisplayInfo(state).priority
    }
    
    /**
     * Check if state should be celebrated (PERFECT)
     */
    fun shouldCelebrate(state: JointState): Boolean {
        return getPriority(state) == DisplayPriority.CELEBRATE
    }
    
    /**
     * Check if state is critical (DANGER)
     */
    fun isCritical(state: JointState): Boolean {
        return getPriority(state) == DisplayPriority.CRITICAL
    }
    
    /**
     * Check if state needs attention (WARNING or DANGER)
     */
    fun needsAttention(state: JointState): Boolean {
        val priority = getPriority(state)
        return priority == DisplayPriority.ATTENTION || priority == DisplayPriority.CRITICAL
    }
    
    /**
     * Check if state is counted (PERFECT, NORMAL, PAD)
     */
    fun isCounted(state: JointState): Boolean {
        return state == JointState.PERFECT || 
               state == JointState.NORMAL || 
               state == JointState.PAD
    }
}

/**
 * Joint name localization helper
 */
object JointNameHelper {
    
    private val JOINT_NAMES = mapOf(
        "left_knee" to LocalizedText(ar = "الركبة اليسرى", en = "Left Knee"),
        "right_knee" to LocalizedText(ar = "الركبة اليمنى", en = "Right Knee"),
        "left_hip" to LocalizedText(ar = "الورك الأيسر", en = "Left Hip"),
        "right_hip" to LocalizedText(ar = "الورك الأيمن", en = "Right Hip"),
        "left_elbow" to LocalizedText(ar = "المرفق الأيسر", en = "Left Elbow"),
        "right_elbow" to LocalizedText(ar = "المرفق الأيمن", en = "Right Elbow"),
        "left_shoulder" to LocalizedText(ar = "الكتف الأيسر", en = "Left Shoulder"),
        "right_shoulder" to LocalizedText(ar = "الكتف الأيمن", en = "Right Shoulder"),
        "left_ankle" to LocalizedText(ar = "الكاحل الأيسر", en = "Left Ankle"),
        "right_ankle" to LocalizedText(ar = "الكاحل الأيمن", en = "Right Ankle"),
        "left_wrist" to LocalizedText(ar = "المعصم الأيسر", en = "Left Wrist"),
        "right_wrist" to LocalizedText(ar = "المعصم الأيمن", en = "Right Wrist"),
        "spine" to LocalizedText(ar = "العمود الفقري", en = "Spine"),
        "neck" to LocalizedText(ar = "الرقبة", en = "Neck")
    )
    
    // Simplified names (without left/right)
    private val SIMPLE_JOINT_NAMES = mapOf(
        "knee" to LocalizedText(ar = "الركبة", en = "Knee"),
        "hip" to LocalizedText(ar = "الورك", en = "Hip"),
        "elbow" to LocalizedText(ar = "المرفق", en = "Elbow"),
        "shoulder" to LocalizedText(ar = "الكتف", en = "Shoulder"),
        "ankle" to LocalizedText(ar = "الكاحل", en = "Ankle"),
        "wrist" to LocalizedText(ar = "المعصم", en = "Wrist"),
        "spine" to LocalizedText(ar = "العمود الفقري", en = "Spine"),
        "neck" to LocalizedText(ar = "الرقبة", en = "Neck")
    )
    
    /**
     * Get localized joint name
     * @param jointCode Joint code (e.g., "left_knee")
     * @param simplified If true, returns "Knee" instead of "Left Knee"
     */
    fun getJointName(jointCode: String, simplified: Boolean = false): LocalizedText {
        // Try exact match first
        JOINT_NAMES[jointCode]?.let { return it }
        
        // Try simplified match
        if (simplified) {
            for ((key, name) in SIMPLE_JOINT_NAMES) {
                if (jointCode.contains(key)) {
                    return name
                }
            }
        } else {
            // Build name from code
            for ((key, name) in SIMPLE_JOINT_NAMES) {
                if (jointCode.contains(key)) {
                    val side = when {
                        jointCode.startsWith("left") -> LocalizedText(ar = "الأيسر", en = "Left")
                        jointCode.startsWith("right") -> LocalizedText(ar = "الأيمن", en = "Right")
                        else -> null
                    }
                    return if (side != null) {
                        LocalizedText(
                            ar = "${name.ar} ${side.ar}",
                            en = "${side.en} ${name.en}"
                        )
                    } else {
                        name
                    }
                }
            }
        }
        
        // Fallback: format the code
        return LocalizedText(
            ar = jointCode.replace("_", " "),
            en = jointCode.replace("_", " ").split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        )
    }
    
    /**
     * Get simplified joint name (without left/right)
     */
    fun getSimpleJointName(jointCode: String): LocalizedText {
        return getJointName(jointCode, simplified = true)
    }
}

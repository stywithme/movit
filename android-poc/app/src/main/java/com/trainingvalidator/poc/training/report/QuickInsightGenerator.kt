package com.trainingvalidator.poc.training.report

import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * QuickInsightGenerator - Generates the main insight message for the report
 * 
 * Analyzes the training session and generates a single, clear message
 * that tells the user the most important thing they need to know.
 * 
 * Priority order:
 * 1. DANGER_WARNING - If there are any dangerous positions
 * 2. FOCUS_POINT - If there's a clear area needing improvement
 * 3. CELEBRATION - If the performance was excellent
 */
object QuickInsightGenerator {
    
    /**
     * Generate the QuickInsight for a report
     */
    fun generate(report: PostTrainingReport): QuickInsight {
        return when {
            // Priority 0: No reps completed - special handling
            report.summary.totalReps == 0 -> generateNoRepsInsight(report)
            
            // Priority 1: Danger alerts
            report.hasDangerAlerts() -> generateDangerInsight(report)
            
            // Priority 2: Check for clear improvement areas
            hasSignificantImprovementArea(report) -> generateFocusInsight(report)
            
            // Priority 3: Celebration for good performance
            report.summary.averageScore >= 80 -> generateCelebrationInsight(report)
            
            // Default: Focus on the main area to improve
            else -> generateDefaultFocusInsight(report)
        }
    }
    
    /**
     * Generate insight when no reps were completed
     * This helps users understand why and how to improve
     */
    private fun generateNoRepsInsight(report: PostTrainingReport): QuickInsight {
        val durationSeconds = (report.summary.durationMs / 1000).toInt()
        val isHoldExercise = report.exerciseConfig?.isHoldExercise() == true
        
        return when {
            // Very short session - probably technical issue or user stopped early
            durationSeconds < 5 -> QuickInsight(
                type = InsightType.FOCUS_POINT,
                title = LocalizedText(
                    ar = "جلسة قصيرة جداً",
                    en = "Very Short Session"
                ),
                subtitle = LocalizedText(
                    ar = "لم يتم اكتشاف أي حركة مكتملة",
                    en = "No complete movement was detected"
                ),
                actionable = LocalizedText(
                    ar = "حاول مرة أخرى وتأكد من ظهور جسمك بالكامل في الكاميرا",
                    en = "Try again and make sure your full body is visible in the camera"
                ),
                icon = "📹"
            )
            
            // Hold exercise - user didn't reach hold position
            isHoldExercise -> QuickInsight(
                type = InsightType.FOCUS_POINT,
                title = LocalizedText(
                    ar = "لم يتم الوصول لوضع الثبات",
                    en = "Hold Position Not Reached"
                ),
                subtitle = LocalizedText(
                    ar = "تأكد من الوصول للوضع الصحيح قبل البدء",
                    en = "Make sure to reach the correct position before starting"
                ),
                actionable = LocalizedText(
                    ar = "اتبع التعليمات على الشاشة للوصول للوضع المطلوب",
                    en = "Follow the on-screen instructions to reach the required position"
                ),
                icon = "🎯"
            )
            
            // Rep-based exercise - user didn't complete the full range of motion
            else -> QuickInsight(
                type = InsightType.FOCUS_POINT,
                title = LocalizedText(
                    ar = "لم تكتمل أي عدة",
                    en = "No Reps Completed"
                ),
                subtitle = LocalizedText(
                    ar = "لم تصل الحركة للمدى الكامل المطلوب",
                    en = "Movement didn't reach the full required range"
                ),
                actionable = LocalizedText(
                    ar = "حاول النزول أعمق والعودة للوضع الأول بشكل كامل",
                    en = "Try going deeper and fully returning to the starting position"
                ),
                icon = "💪"
            )
        }
    }
    
    /**
     * Generate danger warning insight
     */
    private fun generateDangerInsight(report: PostTrainingReport): QuickInsight {
        val dangerAlerts = report.dangerAlerts
        val primaryAlert = dangerAlerts.first()
        val dangerCount = dangerAlerts.size
        
        return QuickInsight(
            type = InsightType.DANGER_WARNING,
            title = LocalizedText(
                ar = "انتبه: ${primaryAlert.jointName.ar}",
                en = "Warning: ${primaryAlert.jointName.en}"
            ),
            subtitle = if (dangerCount == 1) {
                LocalizedText(
                    ar = "في العدة #${primaryAlert.repNumber}، وصلت لوضع غير آمن",
                    en = "In rep #${primaryAlert.repNumber}, you reached an unsafe position"
                )
            } else {
                LocalizedText(
                    ar = "في $dangerCount عدات، وصلت لوضع غير آمن",
                    en = "In $dangerCount reps, you reached an unsafe position"
                )
            },
            actionable = LocalizedText(
                ar = "راجع الصور أدناه لتفهم المشكلة",
                en = "Review the images below to understand the issue"
            ),
            icon = "⚠️"
        )
    }
    
    /**
     * Check if there's a significant area needing improvement
     */
    private fun hasSignificantImprovementArea(report: PostTrainingReport): Boolean {
        // Has warning-level errors affecting multiple reps
        val warningErrors = report.errorAnalysis.filter { 
            it.state == JointState.WARNING && it.count >= 3 
        }
        if (warningErrors.isNotEmpty()) return true
        
        // Score is between 60-80% (room for improvement)
        if (report.summary.averageScore in 60f..80f) return true
        
        // Fatigue detected in the session
        val repCount = report.repTimeline.size
        val fatiguePoint = detectFatiguePoint(report)
        if (fatiguePoint != null && fatiguePoint < repCount * 0.7) return true
        
        return false
    }
    
    /**
     * Generate focus point insight
     */
    private fun generateFocusInsight(report: PostTrainingReport): QuickInsight {
        // Find the most significant issue
        val primaryIssue = findPrimaryIssue(report)
        
        return QuickInsight(
            type = InsightType.FOCUS_POINT,
            title = LocalizedText(
                ar = "ركز على: ${primaryIssue.focusArea.ar}",
                en = "Focus on: ${primaryIssue.focusArea.en}"
            ),
            subtitle = primaryIssue.details,
            actionable = primaryIssue.tip,
            icon = "🎯"
        )
    }
    
    /**
     * Generate celebration insight for excellent performance
     */
    private fun generateCelebrationInsight(report: PostTrainingReport): QuickInsight {
        val score = report.summary.averageScore
        val perfectCount = report.summary.stateBreakdown.perfectCount
        val totalReps = report.summary.totalReps
        
        val subtitle = when {
            perfectCount == totalReps -> LocalizedText(
                ar = "كل العدات كانت مثالية!",
                en = "All reps were perfect!"
            )
            perfectCount > totalReps / 2 -> LocalizedText(
                ar = "حافظت على شكل ممتاز في $perfectCount من $totalReps عدة",
                en = "You maintained excellent form in $perfectCount of $totalReps reps"
            )
            else -> LocalizedText(
                ar = "أداء رائع بنسبة ${score.toInt()}%",
                en = "Great performance with ${score.toInt()}% accuracy"
            )
        }
        
        val actionable = when {
            report.summary.weightKg != null -> LocalizedText(
                ar = "الجلسة القادمة: جرب زيادة الوزن",
                en = "Next session: Try increasing the weight"
            )
            score >= 95 -> LocalizedText(
                ar = "الجلسة القادمة: أضف المزيد من العدات",
                en = "Next session: Add more reps"
            )
            else -> LocalizedText(
                ar = "استمر على هذا المستوى!",
                en = "Keep up this level!"
            )
        }
        
        return QuickInsight(
            type = InsightType.CELEBRATION,
            title = LocalizedText(
                ar = "أداء رائع!",
                en = "Outstanding Performance!"
            ),
            subtitle = subtitle,
            actionable = actionable,
            icon = "🏆"
        )
    }
    
    /**
     * Generate default focus insight for moderate performance
     */
    private fun generateDefaultFocusInsight(report: PostTrainingReport): QuickInsight {
        val primaryIssue = findPrimaryIssue(report)
        
        return QuickInsight(
            type = InsightType.FOCUS_POINT,
            title = LocalizedText(
                ar = "ركز على: ${primaryIssue.focusArea.ar}",
                en = "Focus on: ${primaryIssue.focusArea.en}"
            ),
            subtitle = primaryIssue.details,
            actionable = primaryIssue.tip,
            icon = "💪"
        )
    }
    
    /**
     * Find the primary issue to focus on
     */
    private fun findPrimaryIssue(report: PostTrainingReport): FocusIssue {
        // Check for common issues in order of importance
        
        // 1. Check for warning errors
        val warningErrors = report.errorAnalysis
            .filter { it.state == JointState.WARNING }
            .sortedByDescending { it.count }
        
        if (warningErrors.isNotEmpty()) {
            val error = warningErrors.first()
            return FocusIssue(
                focusArea = error.jointName,
                details = LocalizedText(
                    ar = "لاحظنا مشكلة في ${error.count} عدات",
                    en = "We noticed an issue in ${error.count} reps"
                ),
                tip = error.tip
            )
        }
        
        // 2. Check for stability issues (if there's consistency data)
        report.consistency?.let { consistency ->
            val variation = consistency.variationMs
            if (variation > 1500) { // More than 1.5 seconds variation
                return FocusIssue(
                    focusArea = LocalizedText(ar = "ثبات الإيقاع", en = "Tempo Consistency"),
                    details = LocalizedText(
                        ar = "هناك تفاوت كبير في سرعة العدات",
                        en = "There's significant variation in rep speed"
                    ),
                    tip = LocalizedText(
                        ar = "حاول الحفاظ على إيقاع ثابت",
                        en = "Try to maintain a consistent tempo"
                    )
                )
            }
        }
        
        // 3. Check for fatigue
        val fatiguePoint = detectFatiguePoint(report)
        if (fatiguePoint != null) {
            return FocusIssue(
                focusArea = LocalizedText(ar = "إدارة التعب", en = "Fatigue Management"),
                details = LocalizedText(
                    ar = "بدأ الأداء يتراجع من العدة #$fatiguePoint",
                    en = "Performance started declining from rep #$fatiguePoint"
                ),
                tip = LocalizedText(
                    ar = "جرب التوقف عند ${fatiguePoint - 1} عدة للحفاظ على الجودة",
                    en = "Try stopping at ${fatiguePoint - 1} reps to maintain quality"
                )
            )
        }
        
        // 4. Default: General improvement
        return FocusIssue(
            focusArea = LocalizedText(ar = "الشكل العام", en = "Overall Form"),
            details = LocalizedText(
                ar = "استمر بالتدريب لتحسين أدائك",
                en = "Keep practicing to improve your performance"
            ),
            tip = LocalizedText(
                ar = "ركز على التحكم والبطء في الحركة",
                en = "Focus on control and slow movement"
            )
        )
    }
    
    /**
     * Get the fatigue point from pre-calculated value
     * 
     * Uses fatigueIndex from PerformanceSummary (Single Source of Truth).
     * Calculated once in ReportGenerator using MetricsCalculator.
     */
    private fun detectFatiguePoint(report: PostTrainingReport): Int? {
        return report.summary.fatigueIndex
    }
    
    /**
     * Data class for focus issue
     */
    private data class FocusIssue(
        val focusArea: LocalizedText,
        val details: LocalizedText,
        val tip: LocalizedText
    )
}

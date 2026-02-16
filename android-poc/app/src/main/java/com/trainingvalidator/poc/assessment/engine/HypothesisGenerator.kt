package com.trainingvalidator.poc.assessment.engine

import com.trainingvalidator.poc.assessment.models.*
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.report.PostTrainingReport

/**
 * HypothesisGenerator - Generates hypothesis cards from observed compensations.
 * 
 * IMPORTANT: These are HYPOTHESES, not diagnoses.
 * Each observation includes possible causes with confirmation status.
 * Cross-referencing between exercises can auto-confirm/rule-out causes.
 */
object HypothesisGenerator {
    
    fun generate(
        reports: List<PostTrainingReport>,
        regions: List<AssessmentRegion>,
        reportConfidences: List<Pair<PostTrainingReport, ConfidenceLevel>>
    ): List<HypothesisCard> {
        val hypotheses = mutableListOf<HypothesisCard>()
        
        // Check each exercise for compensation patterns
        for ((report, confidence) in reportConfidences) {
            if (confidence == ConfidenceLevel.LOW) continue
            
            val exerciseId = report.exerciseId.lowercase()
            
            // Overhead Squat compensations
            if (exerciseId.contains("overhead_squat")) {
                hypotheses.addAll(analyzeOverheadSquat(report, regions))
            }
            
            // Lunge compensations
            if (exerciseId.contains("lunge")) {
                hypotheses.addAll(analyzeLunge(report, regions))
            }
            
            // Shoulder mobility compensations
            if (exerciseId.contains("shoulder")) {
                hypotheses.addAll(analyzeShoulderMobility(report, regions))
            }
        }
        
        // Cross-reference: auto-confirm hypotheses across exercises
        crossReference(hypotheses, regions)
        
        return hypotheses
    }
    
    private fun analyzeOverheadSquat(
        report: PostTrainingReport,
        regions: List<AssessmentRegion>
    ): List<HypothesisCard> {
        val cards = mutableListOf<HypothesisCard>()
        val summary = report.summary
        
        // Check for trunk lean (from position checks)
        if (summary.positionWarningReps > 0 || summary.positionErrorReps > 0) {
            val hipRegion = regions.find { it.region == BodyRegion.HIP }
            val ankleConfirmed = regions.find { it.region == BodyRegion.ANKLE }
                ?.let { it.romPercentage < 60f } ?: false
            
            cards.add(HypothesisCard(
                observation = LocalizedText(
                    ar = "ميل الجذع للأمام أثناء القرفصاء",
                    en = "Forward trunk lean during squat"
                ),
                possibleCauses = listOf(
                    PossibleCause(
                        cause = LocalizedText(ar = "محدودية مرونة الكاحل", en = "Limited ankle mobility"),
                        status = if (ankleConfirmed) CauseStatus.CONFIRMED else CauseStatus.POSSIBLE,
                        evidence = if (ankleConfirmed) "Ankle ROM < 60% of norm" else null
                    ),
                    PossibleCause(
                        cause = LocalizedText(ar = "ضعف عضلات الجذع", en = "Weak core muscles"),
                        status = CauseStatus.POSSIBLE
                    ),
                    PossibleCause(
                        cause = LocalizedText(ar = "شد في قابضات الورك", en = "Tight hip flexors"),
                        status = CauseStatus.POSSIBLE
                    )
                ),
                recommendations = listOf(
                    HypothesisRecommendation(
                        type = RecommendationType.MOBILIZE,
                        priority = RecommendationPriority.HIGH,
                        description = LocalizedText(
                            ar = "تمارين مرونة الكاحل",
                            en = "Ankle mobility exercises"
                        )
                    ),
                    HypothesisRecommendation(
                        type = RecommendationType.STRENGTHEN,
                        priority = RecommendationPriority.MEDIUM,
                        description = LocalizedText(
                            ar = "تقوية عضلات الجذع",
                            en = "Core strengthening exercises"
                        )
                    )
                ),
                confidence = ConfidenceLevel.HIGH
            ))
        }
        
        // Check for arms dropping
        val shoulderRegion = regions.find { it.region == BodyRegion.SHOULDER }
        if (shoulderRegion != null && shoulderRegion.romPercentage < 70f) {
            cards.add(HypothesisCard(
                observation = LocalizedText(
                    ar = "سقوط الذراعين للأمام أثناء القرفصاء",
                    en = "Arms falling forward during squat"
                ),
                possibleCauses = listOf(
                    PossibleCause(
                        cause = LocalizedText(ar = "محدودية مرونة الكتف", en = "Limited shoulder mobility"),
                        status = CauseStatus.CONFIRMED,
                        evidence = "Shoulder ROM ${shoulderRegion.getFormattedRomPercentage()} of norm"
                    ),
                    PossibleCause(
                        cause = LocalizedText(ar = "شد في عضلات الصدر", en = "Tight chest muscles"),
                        status = CauseStatus.POSSIBLE
                    )
                ),
                recommendations = listOf(
                    HypothesisRecommendation(
                        type = RecommendationType.STRETCH,
                        priority = RecommendationPriority.HIGH,
                        description = LocalizedText(
                            ar = "إطالة عضلات الكتف والصدر",
                            en = "Shoulder and chest stretching"
                        )
                    )
                ),
                confidence = ConfidenceLevel.HIGH
            ))
        }
        
        return cards
    }
    
    private fun analyzeLunge(
        report: PostTrainingReport,
        regions: List<AssessmentRegion>
    ): List<HypothesisCard> {
        val cards = mutableListOf<HypothesisCard>()
        val summary = report.summary
        
        // Check asymmetry
        val sym = summary.avgSymmetry
        if (sym != null && sym < 80f) {
            cards.add(HypothesisCard(
                observation = LocalizedText(
                    ar = "عدم تماثل ملحوظ بين الساقين",
                    en = "Notable asymmetry between legs"
                ),
                possibleCauses = listOf(
                    PossibleCause(
                        cause = LocalizedText(ar = "ضعف في الجانب الأضعف", en = "Weakness on the weaker side"),
                        status = CauseStatus.CONFIRMED,
                        evidence = "LSI: ${sym.toInt()}%"
                    ),
                    PossibleCause(
                        cause = LocalizedText(ar = "إصابة سابقة", en = "Previous injury"),
                        status = CauseStatus.POSSIBLE
                    )
                ),
                recommendations = listOf(
                    HypothesisRecommendation(
                        type = RecommendationType.STRENGTHEN,
                        priority = RecommendationPriority.HIGH,
                        description = LocalizedText(
                            ar = "تمارين أحادية الجانب — جرعة أكبر للجانب الأضعف",
                            en = "Single-leg exercises — more volume on weaker side"
                        )
                    )
                ),
                confidence = ConfidenceLevel.HIGH
            ))
        }
        
        // Check balance issues
        if (summary.avgStability != null && summary.avgStability < 60f) {
            cards.add(HypothesisCard(
                observation = LocalizedText(
                    ar = "ضعف في التوازن أثناء الطعن",
                    en = "Balance weakness during lunge"
                ),
                possibleCauses = listOf(
                    PossibleCause(
                        cause = LocalizedText(ar = "ضعف عضلات الأرداف", en = "Weak gluteal muscles"),
                        status = CauseStatus.POSSIBLE
                    ),
                    PossibleCause(
                        cause = LocalizedText(ar = "ضعف ثبات الكاحل", en = "Ankle instability"),
                        status = CauseStatus.POSSIBLE
                    )
                ),
                recommendations = listOf(
                    HypothesisRecommendation(
                        type = RecommendationType.STRENGTHEN,
                        priority = RecommendationPriority.HIGH,
                        description = LocalizedText(
                            ar = "تمارين توازن وثبات",
                            en = "Balance and stability exercises"
                        )
                    )
                ),
                confidence = ConfidenceLevel.MEDIUM
            ))
        }
        
        return cards
    }
    
    private fun analyzeShoulderMobility(
        report: PostTrainingReport,
        regions: List<AssessmentRegion>
    ): List<HypothesisCard> {
        val cards = mutableListOf<HypothesisCard>()
        val summary = report.summary
        
        // Check asymmetry
        val sym = summary.avgSymmetry
        if (sym != null && sym < 85f) {
            cards.add(HypothesisCard(
                observation = LocalizedText(
                    ar = "فرق ملحوظ في مرونة الكتفين",
                    en = "Notable difference in shoulder mobility"
                ),
                possibleCauses = listOf(
                    PossibleCause(
                        cause = LocalizedText(ar = "عدم تماثل عضلي", en = "Muscular imbalance"),
                        status = CauseStatus.CONFIRMED,
                        evidence = "Shoulder LSI: ${sym.toInt()}%"
                    ),
                    PossibleCause(
                        cause = LocalizedText(ar = "إصابة سابقة في أحد الكتفين", en = "Previous shoulder injury"),
                        status = CauseStatus.POSSIBLE
                    )
                ),
                recommendations = listOf(
                    HypothesisRecommendation(
                        type = RecommendationType.MOBILIZE,
                        priority = RecommendationPriority.HIGH,
                        description = LocalizedText(
                            ar = "تمارين مرونة الكتف — تركيز على الجانب الأضعف",
                            en = "Shoulder mobility — focus on weaker side"
                        )
                    )
                ),
                confidence = ConfidenceLevel.HIGH
            ))
        }
        
        return cards
    }
    
    /**
     * Cross-reference hypotheses across exercises to auto-confirm/rule-out causes.
     */
    private fun crossReference(
        hypotheses: MutableList<HypothesisCard>,
        regions: List<AssessmentRegion>
    ) {
        // Example: If ankle ROM is confirmed low from squat, 
        // and trunk lean was observed, confirm ankle as cause
        val ankleRegion = regions.find { it.region == BodyRegion.ANKLE }
        if (ankleRegion != null && ankleRegion.romPercentage < 60f) {
            for (i in hypotheses.indices) {
                val card = hypotheses[i]
                val updatedCauses = card.possibleCauses.map { cause ->
                    if (cause.cause.en.contains("ankle", ignoreCase = true) && 
                        cause.status == CauseStatus.POSSIBLE) {
                        cause.copy(
                            status = CauseStatus.CONFIRMED,
                            evidence = "Ankle ROM: ${ankleRegion.getFormattedRomPercentage()}"
                        )
                    } else cause
                }
                hypotheses[i] = card.copy(possibleCauses = updatedCauses)
            }
        }
    }
}

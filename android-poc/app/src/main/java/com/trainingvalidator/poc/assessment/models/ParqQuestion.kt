package com.trainingvalidator.poc.assessment.models

import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * PAR-Q+ Pre-Screening Questions.
 * 
 * Based on the internationally recognized Physical Activity
 * Readiness Questionnaire (PAR-Q+).
 */
data class ParqQuestion(
    val id: String,
    val question: LocalizedText,
    val answer: Boolean = false // true = "yes" (flag)
)

object ParqQuestions {
    fun getQuestions(): List<ParqQuestion> = listOf(
        ParqQuestion(
            id = "heart_condition",
            question = LocalizedText(
                ar = "هل أخبرك طبيب بأن لديك مشكلة في القلب؟",
                en = "Has a doctor told you that you have a heart condition?"
            )
        ),
        ParqQuestion(
            id = "chest_pain",
            question = LocalizedText(
                ar = "هل تشعر بألم في الصدر أثناء النشاط البدني؟",
                en = "Do you feel chest pain during physical activity?"
            )
        ),
        ParqQuestion(
            id = "dizziness",
            question = LocalizedText(
                ar = "هل فقدت التوازن بسبب دوخة أو فقدت الوعي مؤخراً؟",
                en = "Have you lost balance due to dizziness or lost consciousness recently?"
            )
        ),
        ParqQuestion(
            id = "bone_joint",
            question = LocalizedText(
                ar = "هل لديك مشكلة في العظام أو المفاصل قد تتأثر بالتمارين؟",
                en = "Do you have a bone or joint problem that could be worsened by exercise?"
            )
        ),
        ParqQuestion(
            id = "medication",
            question = LocalizedText(
                ar = "هل يصف لك طبيب أدوية لضغط الدم أو القلب حالياً؟",
                en = "Are you currently prescribed medication for blood pressure or heart condition?"
            )
        ),
        ParqQuestion(
            id = "other_reason",
            question = LocalizedText(
                ar = "هل هناك أي سبب آخر يمنعك من ممارسة النشاط البدني؟",
                en = "Is there any other reason you should not participate in physical activity?"
            )
        ),
        ParqQuestion(
            id = "pregnancy",
            question = LocalizedText(
                ar = "هل أنتِ حامل أو ولدتِ خلال الأشهر الـ 6 الأخيرة؟",
                en = "Are you pregnant or have you given birth in the last 6 months?"
            )
        )
    )

    fun hasFlags(answers: List<ParqQuestion>): Boolean =
        answers.any { it.answer }

    fun getFlaggedIds(answers: List<ParqQuestion>): List<String> =
        answers.filter { it.answer }.map { it.id }
}

package com.trainingvalidator.poc.assessment.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.trainingvalidator.poc.ui.utils.feedbackLanguageCode
import com.trainingvalidator.poc.assessment.models.ParqQuestion
import com.trainingvalidator.poc.assessment.models.ParqQuestions
import com.trainingvalidator.poc.assessment.models.AssessmentType
import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * PreScreeningActivity - PAR-Q+ physical activity readiness questionnaire.
 * 
 * 7 yes/no questions. If all "no" → proceed to assessment.
 * If any "yes" → warning message + option to proceed with consent.
 */
class PreScreeningActivity : AppCompatActivity() {
    
    private val questions = ParqQuestions.getQuestions().toMutableList()
    private val switchMap = mutableMapOf<String, Switch>()
    private lateinit var continueButton: Button
    private var assessmentType: AssessmentType = AssessmentType.INITIAL
    
    /** Matches Profile / app locale (same as training feedback). */
    private val language: String get() = feedbackLanguageCode()
    
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#121212")

        assessmentType = AssessmentType.valueOf(
            intent.getStringExtra(EXTRA_ASSESSMENT_TYPE) ?: AssessmentType.INITIAL.name
        )
        
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(0, 0, 0, 0)
        }
        
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(32))
        }
        
        // Header
        content.addView(TextView(this).apply {
            text = if (language == "ar") "فحص ما قبل الاختبار" else "Pre-Assessment Screening"
            setTextColor(Color.WHITE)
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
        })
        
        content.addView(TextView(this).apply {
            text = if (language == "ar") 
                "للتأكد من سلامتك، يرجى الإجابة على الأسئلة التالية."
            else 
                "To ensure your safety, please answer the following questions."
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 14f
            setPadding(0, dp(8), 0, dp(24))
        })
        
        // Disclaimer
        content.addView(createDisclaimerCard())
        
        // Questions
        for ((index, question) in questions.withIndex()) {
            content.addView(createQuestionCard(index, question))
        }
        
        // Continue button
        continueButton = Button(this).apply {
            text = if (language == "ar") "ابدأ الاختبار" else "Start Assessment"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(24)
            layoutParams = lp
            
            setOnClickListener { onContinueClicked() }
        }
        content.addView(continueButton)
        
        // Back button
        content.addView(TextView(this).apply {
            text = if (language == "ar") "رجوع" else "Go Back"
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(16))
            setOnClickListener { finish() }
        })
        
        root.addView(content)
        setContentView(root)
    }
    
    private fun createDisclaimerCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A237E"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(20)
            layoutParams = lp
            
            addView(TextView(context).apply {
                text = if (language == "ar") 
                    "⚕️ تنبيه: هذا ليس فحص طبي. هو أداة wellness لتقييم الحركة فقط."
                else 
                    "⚕️ Note: This is not a medical exam. It's a wellness tool for movement assessment only."
                setTextColor(Color.parseColor("#B3C5FF"))
                textSize = 12f
            })
        }
    }
    
    private fun createQuestionCard(index: Int, question: ParqQuestion): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            layoutParams = lp
            
            // Question text
            addView(TextView(context).apply {
                text = "${index + 1}. ${question.question.get(language)}"
                setTextColor(Color.parseColor("#E0E0E0"))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            
            // Yes/No switch
            val switch = Switch(context).apply {
                isChecked = false
                setOnCheckedChangeListener { _, isChecked ->
                    questions[index] = questions[index].copy(answer = isChecked)
                    updateButtonState()
                }
            }
            switchMap[question.id] = switch
            addView(switch)
        }
    }
    
    private fun updateButtonState() {
        val hasFlags = ParqQuestions.hasFlags(questions)
        if (hasFlags) {
            continueButton.setBackgroundColor(Color.parseColor("#FF9800"))
            continueButton.text = if (language == "ar") "متابعة مع تحذير" else "Continue with Warning"
        } else {
            continueButton.setBackgroundColor(Color.parseColor("#4CAF50"))
            continueButton.text = if (language == "ar") "ابدأ الاختبار" else "Start Assessment"
        }
    }
    
    private fun onContinueClicked() {
        val hasFlags = ParqQuestions.hasFlags(questions)
        
        if (hasFlags) {
            AlertDialog.Builder(this)
                .setTitle(if (language == "ar") "تحذير" else "Warning")
                .setMessage(
                    if (language == "ar") 
                        "بناءً على إجاباتك، ننصحك باستشارة طبيبك قبل البدء في أي نشاط بدني. هل تريد المتابعة على مسؤوليتك؟"
                    else 
                        "Based on your answers, we recommend consulting your doctor before starting physical activity. Do you wish to continue at your own responsibility?"
                )
                .setPositiveButton(if (language == "ar") "متابعة" else "Continue") { _, _ ->
                    launchAssessment()
                }
                .setNegativeButton(if (language == "ar") "رجوع" else "Go Back", null)
                .show()
        } else {
            launchAssessment()
        }
    }
    
    private fun launchAssessment() {
        val intent = AssessmentSessionActivity.createIntent(
            this,
            parqPassed = !ParqQuestions.hasFlags(questions),
            parqFlags = ParqQuestions.getFlaggedIds(questions),
            assessmentType = assessmentType,
        )
        startActivity(intent)
        finish()
    }
    
    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    
    companion object {
        private const val EXTRA_ASSESSMENT_TYPE = "assessment_type"

        fun createIntent(
            context: Context,
            assessmentType: AssessmentType = AssessmentType.INITIAL,
        ): Intent =
            Intent(context, PreScreeningActivity::class.java).apply {
                putExtra(EXTRA_ASSESSMENT_TYPE, assessmentType.name)
            }
    }
}

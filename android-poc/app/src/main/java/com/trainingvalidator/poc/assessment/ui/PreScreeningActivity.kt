package com.trainingvalidator.poc.assessment.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.assessment.models.ParqQuestion
import com.trainingvalidator.poc.assessment.models.ParqQuestions
import com.trainingvalidator.poc.ui.utils.currentLanguage

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

    private val language: String get() = currentLanguage
    
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#121212")
        
        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(0, 0, 0, 0)
        }
        
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(32))
        }
        
        content.addView(TextView(this).apply {
            text = getString(R.string.screening_title)
            setTextColor(Color.WHITE)
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
        })

        content.addView(TextView(this).apply {
            text = getString(R.string.screening_subtitle)
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
            text = getString(R.string.screening_start)
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
            text = getString(R.string.go_back)
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
                text = getString(R.string.screening_disclaimer)
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
            continueButton.text = getString(R.string.screening_continue_warning)
        } else {
            continueButton.setBackgroundColor(Color.parseColor("#4CAF50"))
            continueButton.text = getString(R.string.screening_start)
        }
    }
    
    private fun onContinueClicked() {
        val hasFlags = ParqQuestions.hasFlags(questions)
        
        if (hasFlags) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.screening_warning_title))
                .setMessage(getString(R.string.screening_warning_message))
                .setPositiveButton(getString(R.string.screening_continue)) { _, _ ->
                    launchAssessment()
                }
                .setNegativeButton(getString(R.string.go_back), null)
                .show()
        } else {
            launchAssessment()
        }
    }
    
    private fun launchAssessment() {
        val intent = AssessmentSessionActivity.createIntent(
            this,
            parqPassed = !ParqQuestions.hasFlags(questions),
            parqFlags = ParqQuestions.getFlaggedIds(questions)
        )
        startActivity(intent)
        finish()
    }
    
    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    
    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, PreScreeningActivity::class.java)
    }
}

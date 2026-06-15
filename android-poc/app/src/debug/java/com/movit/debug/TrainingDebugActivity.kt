package com.movit.debug

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.movit.designsystem.MovitTheme
import com.movit.feature.trainingdebug.TrainingDebugRoute

class TrainingDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val exerciseSlug = intent.getStringExtra(EXTRA_EXERCISE_SLUG)
        setContent {
            MovitTheme {
                TrainingDebugRoute(
                    exerciseSlug = exerciseSlug,
                    onBack = { finish() },
                    onCopy = { text ->
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("training_debug", text),
                        )
                        Toast.makeText(this, "Debug info copied", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_EXERCISE_SLUG = "extra_exercise_slug"
    }
}

package com.trainingvalidator.poc.training.feedback

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.*

/**
 * Selects the optimal TTS voice for a given language, preferring male
 * voices and higher-quality neural engines when available.
 *
 * Selection strategy (Arabic):
 *  1. Enumerate all voices from the active TTS engine.
 *  2. Filter to Arabic locale variants (ar, ar-SA, ar-XA, ...).
 *  3. Score each voice by quality tier, male preference, and network requirement.
 *  4. Pick the highest-scoring voice that does NOT require network.
 *     If none exists offline, pick the best online voice as a last resort.
 *
 * For English the same pipeline runs with Locale.US preference.
 */
object TtsVoiceSelector {

    private const val TAG = "TtsVoiceSelector"

    private const val PREFERRED_ENGINE = "com.google.android.tts"

    fun getPreferredEngine(): String = PREFERRED_ENGINE

    /**
     * Apply the best available voice to [tts] for [language] ("ar" or "en").
     * Call **after** TTS init succeeds.
     *
     * Guarantee: always calls setLanguage() or setVoice() — TTS will produce
     * sound even on devices with no ideal voice.
     *
     * @return true if a voice was explicitly set; false if we fell back to setLanguage().
     */
    fun applyBestVoice(tts: TextToSpeech, language: String): Boolean {
        val targetLocales = buildTargetLocales(language)
        val allVoices: Set<Voice>? = try { tts.voices } catch (_: Exception) { null }

        if (allVoices.isNullOrEmpty()) {
            Log.w(TAG, "No voices enumerable; falling back to setLanguage()")
            tts.setLanguage(targetLocales.first())
            return false
        }

        // 1st: offline voices matching language
        val offlineCandidates = allVoices.filter { voice ->
            !voice.isNetworkConnectionRequired &&
            targetLocales.any { it.language == voice.locale.language }
        }
        if (offlineCandidates.isNotEmpty()) {
            return pickAndApply(tts, offlineCandidates, language, targetLocales)
        }

        // 2nd: any voice matching language (including online)
        val allLangCandidates = allVoices.filter { voice ->
            targetLocales.any { it.language == voice.locale.language }
        }
        if (allLangCandidates.isNotEmpty()) {
            Log.w(TAG, "No offline voices for $language; using online voice")
            return pickAndApply(tts, allLangCandidates, language, targetLocales)
        }

        // 3rd: no matching voices at all — setLanguage() lets the engine decide
        Log.w(TAG, "No $language voices found; setLanguage() fallback")
        tts.setLanguage(targetLocales.first())
        return false
    }

    private fun pickAndApply(
        tts: TextToSpeech,
        candidates: List<Voice>,
        language: String,
        targetLocales: List<Locale>
    ): Boolean {
        if (candidates.isEmpty()) {
            Log.w(TAG, "No voices at all for $language; setLanguage() fallback")
            tts.setLanguage(targetLocales.first())
            return false
        }

        val scored = candidates.map { it to scoreVoice(it, language) }
        val best = scored.maxByOrNull { it.second }!!.first

        tts.voice = best
        Log.d(TAG, "Selected voice: name=${best.name}, locale=${best.locale}, " +
                "quality=${best.quality}, network=${best.isNetworkConnectionRequired}, " +
                "features=${best.features}")
        return true
    }

    /**
     * Score a voice. Higher is better.
     *
     * Weights:
     *  - Quality tier    (0-400) — VERY_HIGH > HIGH > NORMAL > LOW
     *  - Male preference (0-200) — heuristic based on voice name/features
     *  - Locale match    (0-100) — exact region match (ar-XA, ar-SA) > generic ar
     *  - Not network     (0-50)  — offline preferred
     */
    private fun scoreVoice(voice: Voice, language: String): Int {
        var score = 0

        // Quality tier
        score += when (voice.quality) {
            Voice.QUALITY_VERY_HIGH -> 400
            Voice.QUALITY_HIGH      -> 300
            Voice.QUALITY_NORMAL    -> 200
            Voice.QUALITY_LOW       -> 100
            else                    -> 150
        }

        // Male preference via heuristics (Android API does not expose gender directly)
        if (isMaleHeuristic(voice)) score += 200

        // Locale precision
        val country = voice.locale.country.lowercase()
        if (language == "ar") {
            // Prefer XA (Google's pan-Arabic) and SA (Saudi)
            if (country == "xa" || country == "sa") score += 100
            else if (country.isNotEmpty()) score += 50
        } else {
            if (voice.locale == Locale.US) score += 100
            else if (voice.locale.language == "en") score += 50
        }

        // Offline bonus
        if (!voice.isNetworkConnectionRequired) score += 50

        return score
    }

    /**
     * Heuristic to guess male gender from voice metadata.
     *
     * Google TTS naming conventions observed (not guaranteed across all engines):
     *  - Standard/WaveNet: "-B", "-C" are typically male for Arabic
     *  - Chirp3-HD: certain star names map to male
     *  - Some engines put "male" in features set
     *
     * Falls back to false (unknown) — never penalises, just doesn't get bonus.
     */
    private fun isMaleHeuristic(voice: Voice): Boolean {
        val name = voice.name.lowercase()
        val features = voice.features.map { it.lowercase() }.toSet()

        if ("male" in features) return true
        if ("female" in features) return false

        // Google Standard/WaveNet Arabic: B and C are male
        if (name.contains("ar-xa-standard-b") || name.contains("ar-xa-standard-c")) return true
        if (name.contains("ar-xa-wavenet-b") || name.contains("ar-xa-wavenet-c")) return true

        // Google Chirp3-HD known male star names (from official docs)
        val maleStars = setOf(
            "achird", "algenib", "algieba", "alnilam", "charon",
            "enceladus", "fenrir", "iapetus", "orus", "puck",
            "rasalgethi", "sadachbia", "sadaltager", "schedar",
            "umbriel", "zubenelgenubi"
        )
        for (star in maleStars) {
            if (name.contains(star)) return true
        }

        // English male heuristic
        if (name.contains("en-us-standard-b") || name.contains("en-us-standard-d")) return true
        if (name.contains("en-us-wavenet-b") || name.contains("en-us-wavenet-d")) return true

        return false
    }

    private fun buildTargetLocales(language: String): List<Locale> {
        return if (language == "ar") {
            listOf(
                Locale.forLanguageTag("ar-XA"),
                Locale.forLanguageTag("ar-SA"),
                Locale.forLanguageTag("ar")
            )
        } else {
            listOf(Locale.US, Locale.UK, Locale.ENGLISH)
        }
    }
}

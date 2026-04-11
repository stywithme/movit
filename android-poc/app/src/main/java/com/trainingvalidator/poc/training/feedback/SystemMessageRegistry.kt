package com.trainingvalidator.poc.training.feedback

import com.trainingvalidator.poc.network.SystemMessageTemplate
import com.trainingvalidator.poc.training.models.LocalizedText
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry of system messages synced from the backend.
 * Falls back to hardcoded defaults in call sites when a key is missing or text is blank.
 * Audio URLs follow the same rules as exercise messages: cache file preferred, else TTS.
 */
object SystemMessageRegistry {

    private val byCode = ConcurrentHashMap<String, LocalizedText>()

    /** Replace all entries (typically after a successful sync). */
    fun replaceAll(templates: List<SystemMessageTemplate>) {
        byCode.clear()
        for (t in templates) {
            byCode[t.code] = t.content
        }
    }

    /**
     * Resolved [LocalizedText] for [key], merging server text/audio with app defaults.
     */
    fun get(key: String, defaultAr: String, defaultEn: String): LocalizedText {
        val remote = byCode[key] ?: return LocalizedText(ar = defaultAr, en = defaultEn)
        return LocalizedText(
            ar = remote.ar.ifBlank { defaultAr },
            en = remote.en.ifBlank { defaultEn },
            audioAr = remote.audioAr,
            audioEn = remote.audioEn
        )
    }

    /**
     * Replace `{name}` placeholders in both languages (e.g. `{n}`, `{seconds}`, `{joints}`).
     */
    fun substitute(lt: LocalizedText, vars: Map<String, String>): LocalizedText {
        fun rep(s: String): String {
            var out = s
            for ((k, v) in vars) {
                out = out.replace("{$k}", v)
            }
            return out
        }
        return lt.copy(ar = rep(lt.ar), en = rep(lt.en))
    }
}

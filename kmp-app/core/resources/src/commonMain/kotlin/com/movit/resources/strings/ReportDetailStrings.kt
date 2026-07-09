package com.movit.resources.strings

import com.movit.resources.localizedString

data class ReportDetailStrings(
    val language: String,
    val sessionOverview: String,
    val fatigueLabel: String,
    val fatigueLow: String,
    val fatigueElevated: String,
    val fatigueModerate: String,
    val fatigueNotEnough: String,
    val formSteady: String,
) {
    suspend fun avgForm(score: Int): String =
        localizedString(language, "report_detail_avg_form", score)

    suspend fun bestSet(setNumber: Int): String =
        localizedString(language, "report_detail_best_set", setNumber)

    suspend fun worstSet(setNumber: Int): String =
        localizedString(language, "report_detail_worst_set", setNumber)

    suspend fun bestRep(repNumber: Int): String =
        localizedString(language, "report_detail_best_rep", repNumber)

    suspend fun worstRep(repNumber: Int): String =
        localizedString(language, "report_detail_worst_rep", repNumber)

    suspend fun bestRepInThisSet(repNumber: Int): String =
        localizedString(language, "report_detail_best_rep_in_set", repNumber)

    suspend fun worstRepInThisSet(repNumber: Int): String =
        localizedString(language, "report_detail_worst_rep_in_set", repNumber)

    suspend fun holdAchievement(percent: Int): String =
        localizedString(language, "report_detail_hold_achievement", percent)

    suspend fun setShort(index: Int): String =
        localizedString(language, "report_detail_set_short", index)

    suspend fun formDropped(delta: Int, lastSet: Int): String =
        localizedString(language, "report_detail_form_dropped", delta, lastSet)

    suspend fun frameEvidenceDanger(repNumber: Int): String =
        localizedString(language, "report_detail_frame_danger", repNumber)

    suspend fun frameEvidenceBest(repNumber: Int): String =
        localizedString(language, "report_detail_frame_best", repNumber)

    suspend fun frameEvidenceFormIssue(repNumber: Int): String =
        localizedString(language, "report_detail_frame_form_issue", repNumber)

    suspend fun frameEvidencePeak(repNumber: Int): String =
        localizedString(language, "report_detail_frame_peak", repNumber)

    suspend fun frameEvidenceHoldSample(): String =
        localizedString(language, "report_detail_frame_hold_sample")

    companion object {
        suspend fun load(language: String): ReportDetailStrings = ReportDetailStrings(
            language = language,
            sessionOverview = localizedString(language, "report_detail_session_overview"),
            fatigueLabel = localizedString(language, "reports_fatigue_label"),
            fatigueLow = localizedString(language, "report_detail_fatigue_low"),
            fatigueElevated = localizedString(language, "reports_fatigue_elevated"),
            fatigueModerate = localizedString(language, "reports_fatigue_moderate"),
            fatigueNotEnough = localizedString(language, "report_detail_fatigue_not_enough"),
            formSteady = localizedString(language, "report_detail_form_steady"),
        )
    }
}

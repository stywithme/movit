package com.movit.core.data.local

data class SessionJournalRow(
    val sessionId: String,
    val exerciseId: String,
    val payloadJson: String,
    val status: String,
    val updatedAtEpochMs: Long,
)

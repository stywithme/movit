package com.movit.core.data.sync

import com.movit.core.data.outbox.parseHttpStatusFromError
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

internal enum class SyncFailureKind {
    Network,
    Decode,
    Http,
    Unknown,
}

internal enum class OutboxFailureKind {
    Network,
    Unexpected,
}

internal fun classifySyncFailure(error: Throwable): SyncFailureKind {
    if (error is CancellationException) throw error
    return classifyThrowable(error)
}

internal fun classifyOutboxFailure(error: Throwable): OutboxFailureKind {
    if (error is CancellationException) throw error
    return when (classifyThrowable(error)) {
        SyncFailureKind.Decode,
        SyncFailureKind.Unknown,
        -> OutboxFailureKind.Unexpected
        SyncFailureKind.Network,
        SyncFailureKind.Http,
        -> OutboxFailureKind.Network
    }
}

private fun classifyThrowable(error: Throwable): SyncFailureKind {
    var current: Throwable? = error
    while (current != null) {
        if (current is SerializationException) return SyncFailureKind.Decode
        val status = parseHttpStatusFromError(current.message)
        when {
            status != null && status >= 500 -> return SyncFailureKind.Network
            status != null -> return SyncFailureKind.Http
            isNetworkThrowable(current) -> return SyncFailureKind.Network
        }
        current = current.cause
    }
    return SyncFailureKind.Unknown
}

private fun isNetworkThrowable(error: Throwable): Boolean {
    val name = error::class.simpleName.orEmpty()
    return name.contains("Timeout", ignoreCase = true) ||
        name.contains("Socket", ignoreCase = true) ||
        name.contains("Connect", ignoreCase = true) ||
        name.contains("Network", ignoreCase = true) ||
        name.contains("IOException", ignoreCase = true)
}

package com.movit.shared

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : AppResult<Nothing>
}

fun <T> AppResult<T>.getOrNull(): T? = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> null
}

fun <T> AppResult<T>.getOrElse(default: () -> T): T = when (this) {
    is AppResult.Success -> value
    is AppResult.Failure -> default()
}

package com.campusbite.ui

/**
 * Explicit three-state UI contract shared by every networked screen.
 *
 * Loading — a request is in flight; show an indeterminate ProgressBar / placeholder.
 * Success — data is available; populate the RecyclerView via ViewBinding.
 * Error   — the request failed (timeout, airplane mode, HTTP 4xx/5xx); show a
 *           friendly banner and keep any cached content on screen instead of crashing.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

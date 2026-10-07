package com.fetchreel.app.data

sealed interface PlaylistDownloadEvent {
    data class ItemProgress(
        val currentIndex: Int,
        val totalCount: Int,
        val currentTitle: String,
        val itemProgress: Float,
        val overallProgress: Float,
        val speed: String,
        val eta: String
    ) : PlaylistDownloadEvent

    data class Completed(
        val playlistTitle: String,
        val successfulCount: Int,
        val failedCount: Int,
        val isAudio: Boolean
    ) : PlaylistDownloadEvent

    data class Failed(val error: String) : PlaylistDownloadEvent
}

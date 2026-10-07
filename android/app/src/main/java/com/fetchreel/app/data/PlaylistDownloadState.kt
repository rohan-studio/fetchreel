package com.fetchreel.app.data

sealed interface PlaylistDownloadState {
    object Idle : PlaylistDownloadState
    object Probing : PlaylistDownloadState
    data class Probed(
        val playlist: PlaylistInfo,
        val selectedItemIds: Set<String>,
        val selectedOption: QualityOption
    ) : PlaylistDownloadState
    data class Downloading(
        val playlist: PlaylistInfo,
        val currentIndex: Int,
        val totalCount: Int,
        val currentItemTitle: String,
        val itemProgress: Float,
        val overallProgress: Float,
        val speedText: String,
        val etaText: String
    ) : PlaylistDownloadState
    data class Completed(
        val playlistTitle: String,
        val downloadedCount: Int,
        val failedCount: Int,
        val isAudio: Boolean
    ) : PlaylistDownloadState
    data class Error(val message: String) : PlaylistDownloadState
}

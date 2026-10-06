package com.fetchreel.app.data

sealed interface DownloadState {
    object Idle : DownloadState
    object Probing : DownloadState
    data class Probed(val info: VideoInfo, val selectedOption: QualityOption) : DownloadState
    data class Downloading(
        val info: VideoInfo,
        val progress: Float,
        val speedText: String,
        val etaText: String
    ) : DownloadState
    data class Completed(val title: String, val savedPath: String, val isAudio: Boolean) : DownloadState
    data class Error(val message: String) : DownloadState
}

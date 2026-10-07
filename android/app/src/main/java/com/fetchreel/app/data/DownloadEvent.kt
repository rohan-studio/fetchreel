package com.fetchreel.app.data

data class ActiveDownload(
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val option: QualityOption,
    val progress: Float = 0f,
    val speed: String = "",
    val eta: String = "",
    val statusText: String = "Starting download..."
)

sealed interface DownloadEvent {
    data class Progress(
        val progress: Float,
        val speed: String,
        val eta: String,
        val statusText: String = ""
    ) : DownloadEvent
    data class Completed(val title: String, val path: String, val isAudio: Boolean) : DownloadEvent
    data class Failed(val error: String) : DownloadEvent
}

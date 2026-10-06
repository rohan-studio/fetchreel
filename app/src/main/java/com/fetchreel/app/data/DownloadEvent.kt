package com.fetchreel.app.data

sealed interface DownloadEvent {
    data class Progress(val progress: Float, val speed: String, val eta: String) : DownloadEvent
    data class Completed(val title: String, val path: String, val isAudio: Boolean) : DownloadEvent
    data class Failed(val error: String) : DownloadEvent
}

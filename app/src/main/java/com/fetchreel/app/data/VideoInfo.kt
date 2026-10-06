package com.fetchreel.app.data

data class VideoInfo(
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val uploader: String? = null,
    val duration: Long? = null,
    val options: List<QualityOption> = emptyList()
) {
    fun formattedDuration(): String {
        if (duration == null || duration <= 0) return ""
        val minutes = duration / 60
        val seconds = duration % 60
        val hours = minutes / 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes % 60, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}

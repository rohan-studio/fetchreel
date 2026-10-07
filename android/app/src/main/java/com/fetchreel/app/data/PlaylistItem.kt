package com.fetchreel.app.data

data class PlaylistItem(
    val id: String,
    val title: String,
    val url: String,
    val duration: Long? = null,
    val thumbnail: String? = null,
    val uploader: String? = null,
    val index: Int = 0
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

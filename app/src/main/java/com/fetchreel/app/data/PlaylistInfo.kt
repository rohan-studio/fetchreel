package com.fetchreel.app.data

data class PlaylistInfo(
    val url: String,
    val title: String,
    val author: String? = null,
    val thumbnail: String? = null,
    val items: List<PlaylistItem> = emptyList()
) {
    companion object {
        val defaultQualityOptions = listOf(
            QualityOption(
                label = "Best Available (MP4)",
                formatSpec = "bestvideo+bestaudio/best",
                isAudio = false
            ),
            QualityOption(
                label = "1080p (MP4)",
                formatSpec = "bestvideo[height<=1080]+bestaudio/best[height<=1080]",
                height = 1080,
                isAudio = false
            ),
            QualityOption(
                label = "720p (HD MP4)",
                formatSpec = "bestvideo[height<=720]+bestaudio/best[height<=720]",
                height = 720,
                isAudio = false
            ),
            QualityOption(
                label = "480p (SD MP4)",
                formatSpec = "bestvideo[height<=480]+bestaudio/best[height<=480]",
                height = 480,
                isAudio = false
            ),
            QualityOption(
                label = "Audio Only (MP3)",
                formatSpec = "bestaudio/best",
                isAudio = true
            )
        )

        fun isPlaylistUrl(url: String): Boolean {
            val lower = url.lowercase().trim()
            return lower.contains("list=") ||
                    lower.contains("/playlist") ||
                    lower.contains("/sets/") ||
                    lower.contains("/album/")
        }
    }
}

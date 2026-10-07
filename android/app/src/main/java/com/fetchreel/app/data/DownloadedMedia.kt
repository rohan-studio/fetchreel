package com.fetchreel.app.data

import android.net.Uri

data class DownloadedMedia(
    val id: Long,
    val name: String,
    val uri: Uri,
    val sizeBytes: Long,
    val dateAdded: Long,
    val isAudio: Boolean
) {
    fun formattedSize(): String {
        val mb = sizeBytes / (1024.0 * 1024.0)
        return if (mb >= 1000) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }
}

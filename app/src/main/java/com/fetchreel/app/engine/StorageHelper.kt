package com.fetchreel.app.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.fetchreel.app.data.DownloadedMedia
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object StorageHelper {

    private const val SUBFOLDER = "Fetchreel"

    fun getAppTempFolder(context: Context): File {
        val folder = File(context.cacheDir, "fetchreel_downloads")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    /**
     * Moves a completed downloaded file from app temp cache to public Download/Fetchreel folder
     */
    fun saveToPublicStorage(
        context: Context,
        tempFile: File,
        desiredFileName: String,
        isAudio: Boolean
    ): String {
        val cleanName = desiredFileName.replace(Regex("[^a-zA-Z0-9.\\-_ ]"), "").trim()
            .ifEmpty { if (isAudio) "audio.mp3" else "video.mp4" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val mimeType = if (isAudio) "audio/mpeg" else "video/mp4"
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER"

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(contentUri, values)
                ?: throw IllegalStateException("Could not create MediaStore entry")

            try {
                resolver.openOutputStream(uri)?.use { outStream ->
                    FileInputStream(tempFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return "$relativePath/$cleanName"
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            } finally {
                tempFile.delete()
            }
        } else {
            // Android 9 and older
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, SUBFOLDER)
            if (!targetDir.exists()) targetDir.mkdirs()

            var targetFile = File(targetDir, cleanName)
            var counter = 1
            while (targetFile.exists()) {
                val nameWithoutExt = cleanName.substringBeforeLast(".")
                val ext = cleanName.substringAfterLast(".", "")
                targetFile = File(targetDir, "$nameWithoutExt ($counter).$ext")
                counter++
            }

            FileInputStream(tempFile).use { inStream ->
                FileOutputStream(targetFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
            tempFile.delete()

            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                null,
                null
            )
            return targetFile.absolutePath
        }
    }

    /**
     * Queries recent media saved in Fetchreel folder
     */
    fun queryRecentDownloads(context: Context): List<DownloadedMedia> {
        val list = mutableListOf<DownloadedMedia>()
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.MIME_TYPE
            )
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%$SUBFOLDER%")
            val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

            // Query Downloads
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

                while (cursor.moveToNext() && list.size < 20) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Media"
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol)
                    val mime = cursor.getString(mimeCol) ?: ""
                    val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                    val isAudio = mime.startsWith("audio") || name.endsWith(".mp3")
                    list.add(DownloadedMedia(id, name, uri, size, date, isAudio))
                }
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, SUBFOLDER)
            if (targetDir.exists() && targetDir.isDirectory) {
                targetDir.listFiles()?.sortedByDescending { it.lastModified() }?.take(20)?.forEach { file ->
                    val uri = Uri.fromFile(file)
                    val isAudio = file.name.endsWith(".mp3", ignoreCase = true)
                    list.add(
                        DownloadedMedia(
                            id = file.hashCode().toLong(),
                            name = file.name,
                            uri = uri,
                            sizeBytes = file.length(),
                            dateAdded = file.lastModified() / 1000,
                            isAudio = isAudio
                        )
                    )
                }
            }
        }
        return list
    }

    /**
     * Deletes a downloaded media item from public storage / MediaStore
     */
    fun deleteMediaFile(context: Context, item: DownloadedMedia): Boolean {
        return try {
            val resolver = context.contentResolver
            val rows = resolver.delete(item.uri, null, null)
            if (rows > 0) return true

            // Direct file deletion fallback
            if (item.uri.scheme == "file") {
                val f = File(item.uri.path ?: "")
                if (f.exists()) return f.delete()
            }

            // Also check directory path directly
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetFile = File(File(downloadsDir, SUBFOLDER), item.name)
            if (targetFile.exists()) {
                return targetFile.delete()
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}


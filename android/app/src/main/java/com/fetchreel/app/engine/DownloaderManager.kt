package com.fetchreel.app.engine

import android.content.Context
import android.util.Log
import com.fetchreel.app.data.PlaylistItem
import com.fetchreel.app.data.PlaylistInfo
import com.fetchreel.app.data.QualityOption
import com.fetchreel.app.data.VideoInfo
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object DownloaderManager {

    private const val TAG = "DownloaderManager"
    private val gson = Gson()
    private val initMutex = Mutex()

    @Volatile
    private var isInitialized = false

    /**
     * Ensures native YoutubeDL and FFmpeg runtimes are properly initialized and unpacked.
     * Suspends callers until initialization completes safely.
     */
    suspend fun ensureInitialized(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext Result.success(Unit)
        initMutex.withLock {
            if (isInitialized) return@withLock Result.success(Unit)
            try {
                val appContext = context.applicationContext
                Log.d(TAG, "Initializing YoutubeDL and FFmpeg...")
                YoutubeDL.getInstance().init(appContext)
                FFmpeg.getInstance().init(appContext)
                isInitialized = true
                val ver = YoutubeDL.getInstance().version(appContext)
                Log.d(TAG, "YoutubeDL and FFmpeg ready (engine version: $ver)")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize YoutubeDL / FFmpeg", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Probes media metadata and available resolutions on-device without downloading.
     * Retries with fallback player clients if YouTube bot challenges occur.
     */
    suspend fun probeUrl(context: Context, url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val initRes = ensureInitialized(context)
            if (initRes.isFailure) {
                return@withContext Result.failure(
                    initRes.exceptionOrNull() ?: IllegalStateException("Engine initialization failed")
                )
            }
            Log.d(TAG, "Starting probe for URL: $url")
            val isYouTube = url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)
            val isInstagram = url.contains("instagram.com", ignoreCase = true)

            // Prioritize Android/iOS mobile clients to bypass heavy web JS/QuickJS de-obfuscation challenges
            val clientFallbacks = if (isYouTube) {
                listOf("android,ios", "ios", "android", "tv,tv_embedded", null)
            } else {
                listOf(null)
            }

            var lastError: Exception? = null
            for (client in clientFallbacks) {
                try {
                    val request = YoutubeDLRequest(url).apply {
                        addOption("--dump-json")
                        addOption("--no-playlist")
                        addOption("--skip-download")
                        addOption("--no-warnings")
                        addOption("--no-call-home")
                        addOption("--no-check-certificates")
                        addOption("--prefer-free-formats")
                        addOption("--socket-timeout", 15)
                        addOption("--retries", 2)
                        addOption(
                            "--user-agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        )
                        if (client != null) {
                            addOption("--extractor-args", "youtube:player_client=$client;skip=translated_subs,comments")
                        } else if (isYouTube) {
                            addOption("--extractor-args", "youtube:player_client=android,ios;skip=translated_subs,comments")
                        }
                        if (isInstagram) {
                            addOption("--add-header", "Accept-Language: en-US,en;q=0.9")
                        }
                    }

                    val response = YoutubeDL.getInstance().execute(request)
                    val jsonStr = response.out.trim().ifEmpty { throw IllegalStateException("Empty response from yt-dlp") }
                    val json = gson.fromJson(jsonStr, JsonObject::class.java)

                    val title = json.get("title")?.asString ?: "Untitled"
                    val thumbnail = json.get("thumbnail")?.asString
                    val uploader = json.get("uploader")?.asString
                        ?: json.get("channel")?.asString
                        ?: json.get("extractor")?.asString
                    val duration = json.get("duration")?.asLong

                    val formatsArray = json.getAsJsonArray("formats")
                    val qualityOptions = mutableListOf<QualityOption>()

                    // Standard industry resolution tiers (handles 16:9, widescreen letterboxed 1012p, and 9:16 vertical Reels)
                    val tierDefs = listOf(
                        Triple("4K (2160p Ultra HD)", 2160) { s: Int, l: Int -> s >= 2000 || l >= 3500 },
                        Triple("2K (1440p Quad HD)", 1440) { s: Int, l: Int -> s >= 1350 || l >= 2400 },
                        Triple("1080p (Full HD)", 1080) { s: Int, l: Int -> s >= 900 || l >= 1700 },
                        Triple("720p (HD)", 720) { s: Int, l: Int -> s >= 650 || l >= 1150 },
                        Triple("480p (SD)", 480) { s: Int, l: Int -> s >= 420 || l >= 800 },
                        Triple("360p", 360) { s: Int, l: Int -> s >= 300 || l >= 550 },
                        Triple("240p", 240) { s: Int, l: Int -> s < 300 && l < 550 }
                    )

                    val tierFormats = mutableMapOf<String, MutableList<JsonObject>>()

                    if (formatsArray != null) {
                        for (elem in formatsArray) {
                            if (!elem.isJsonObject) continue
                            val fObj = elem.asJsonObject
                            val height = fObj.get("height")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                            val width = fObj.get("width")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                            val vcodec = fObj.get("vcodec")?.takeIf { !it.isJsonNull }?.asString ?: "none"

                            if ((height > 0 || width > 0) && vcodec != "none") {
                                val shortDim = if (width > 0 && height > 0) minOf(width, height) else height
                                val longDim = if (width > 0 && height > 0) maxOf(width, height) else height

                                for (tdef in tierDefs) {
                                    if (tdef.third(shortDim, longDim)) {
                                        tierFormats.getOrPut(tdef.first) { mutableListOf() }.add(fObj)
                                        break
                                    }
                                }
                            }
                        }
                    }

                    qualityOptions.add(
                        QualityOption(
                            label = "Best available (Highest Quality • Original)",
                            formatSpec = "bestvideo+bestaudio/bestvideo*+bestaudio/best",
                            height = null,
                            isAudio = false
                        )
                    )

                    for (tdef in tierDefs) {
                        val tname = tdef.first
                        val fmts = tierFormats[tname]
                        if (!fmts.isNullOrEmpty()) {
                            val bestFmt = fmts.maxByOrNull { f ->
                                val tbr = f.get("tbr")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0
                                val vbr = f.get("vbr")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0
                                maxOf(tbr, vbr)
                            }
                            val fid = bestFmt?.get("format_id")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            val th = tdef.second
                            val fmtSpec = if (fid.isNotEmpty()) {
                                "$fid+bestaudio/bestvideo[height<=$th]+bestaudio/bestvideo+bestaudio/best"
                            } else {
                                "bestvideo[height<=$th]+bestaudio/bestvideo+bestaudio/best"
                            }

                            qualityOptions.add(
                                QualityOption(
                                    label = tname,
                                    formatSpec = fmtSpec,
                                    height = th,
                                    isAudio = false
                                )
                            )
                        }
                    }

                    qualityOptions.add(
                        QualityOption(
                            label = "Audio only (MP3)",
                            formatSpec = "bestaudio/best",
                            height = null,
                            isAudio = true
                        )
                    )

                    val videoInfo = VideoInfo(
                        url = url,
                        title = title,
                        thumbnail = thumbnail,
                        uploader = uploader,
                        duration = duration,
                        options = qualityOptions
                    )

                    Log.d(TAG, "Successfully probed URL: $url -> title: $title")
                    return@withContext Result.success(videoInfo)
                } catch (e: Exception) {
                    lastError = e
                    val errMsg = e.message?.lowercase() ?: ""
                    val isBotCheck = errMsg.contains("not a bot") ||
                            errMsg.contains("sign in to confirm") ||
                            errMsg.contains("bot")
                    if (!isBotCheck || client == clientFallbacks.last()) {
                        break
                    }
                    Log.w(TAG, "Bot check encountered for $url, retrying with client: $client")
                }
            }

            val finalException = lastError ?: IllegalStateException("Failed to probe URL")
            Log.e(TAG, "Error probing URL: $url", finalException)
            Result.failure(cleanException(finalException))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error probing URL: $url", e)
            Result.failure(cleanException(e))
        }
    }

    /**
     * Probes playlist metadata and extracts list of individual video items without downloading them.
     */
    suspend fun probePlaylist(context: Context, url: String): Result<PlaylistInfo> = withContext(Dispatchers.IO) {
        try {
            val initRes = ensureInitialized(context)
            if (initRes.isFailure) {
                return@withContext Result.failure(
                    initRes.exceptionOrNull() ?: IllegalStateException("Engine initialization failed")
                )
            }
            Log.d(TAG, "Starting playlist probe for URL: $url")
            val isYouTube = url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)

            val clientFallbacks = if (isYouTube) {
                listOf("android,ios", "ios", "android", "tv,tv_embedded", null)
            } else {
                listOf(null)
            }

            var lastError: Exception? = null
            for (client in clientFallbacks) {
                try {
                    val request = YoutubeDLRequest(url).apply {
                        addOption("--flat-playlist")
                        addOption("--dump-single-json")
                        addOption("--yes-playlist")
                        addOption("--skip-download")
                        addOption("--no-warnings")
                        addOption("--no-call-home")
                        addOption("--no-check-certificates")
                        addOption("--socket-timeout", 20)
                        addOption("--retries", 2)
                        addOption(
                            "--user-agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        )
                        if (client != null) {
                            addOption("--extractor-args", "youtube:player_client=$client;skip=translated_subs,comments")
                        } else if (isYouTube) {
                            addOption("--extractor-args", "youtube:player_client=android,ios;skip=translated_subs,comments")
                        }
                    }

                    val response = YoutubeDL.getInstance().execute(request)
                    val jsonStr = response.out.trim().ifEmpty { throw IllegalStateException("Empty response from yt-dlp") }
                    val json = gson.fromJson(jsonStr, JsonObject::class.java)

                    val playlistTitle = json.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "Untitled Playlist"
                    val uploader = json.get("uploader")?.takeIf { !it.isJsonNull }?.asString
                        ?: json.get("channel")?.takeIf { !it.isJsonNull }?.asString
                        ?: json.get("uploader_id")?.takeIf { !it.isJsonNull }?.asString
                    val playlistThumbnail = json.get("thumbnail")?.takeIf { !it.isJsonNull }?.asString

                    val items = mutableListOf<PlaylistItem>()

                    val entries = json.getAsJsonArray("entries")
                    if (entries != null && entries.size() > 0) {
                        var idx = 0
                        for (entryElem in entries) {
                            if (!entryElem.isJsonObject) continue
                            val entry = entryElem.asJsonObject
                            val id = entry.get("id")?.takeIf { !it.isJsonNull }?.asString ?: ""
                            val title = entry.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "Video #${idx + 1}"
                            if (title.contains("[Private video]") || title.contains("[Deleted video]")) {
                                continue
                            }

                            var itemUrl = entry.get("url")?.takeIf { !it.isJsonNull }?.asString
                            if (itemUrl.isNullOrEmpty() || !itemUrl.startsWith("http")) {
                                itemUrl = if (id.isNotEmpty()) "https://www.youtube.com/watch?v=$id" else url
                            }

                            val duration = try {
                                entry.get("duration")?.takeIf { !it.isJsonNull }?.asLong
                            } catch (_: Exception) {
                                try {
                                    entry.get("duration")?.takeIf { !it.isJsonNull }?.asDouble?.toLong()
                                } catch (_: Exception) {
                                    null
                                }
                            }

                            var thumb: String? = entry.get("thumbnail")?.takeIf { !it.isJsonNull }?.asString
                            if (thumb.isNullOrEmpty()) {
                                val thumbsArr = entry.getAsJsonArray("thumbnails")
                                if (thumbsArr != null && thumbsArr.size() > 0) {
                                    val lastThumb = thumbsArr.get(thumbsArr.size() - 1)
                                    if (lastThumb.isJsonObject) {
                                        thumb = lastThumb.asJsonObject.get("url")?.takeIf { !it.isJsonNull }?.asString
                                    }
                                }
                            }
                            if (thumb.isNullOrEmpty() && id.isNotEmpty()) {
                                thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                            }

                            val itemUploader = entry.get("uploader")?.takeIf { !it.isJsonNull }?.asString ?: uploader

                            items.add(
                                PlaylistItem(
                                    id = if (id.isNotEmpty()) id else UUID.randomUUID().toString(),
                                    title = title,
                                    url = itemUrl,
                                    duration = duration,
                                    thumbnail = thumb,
                                    uploader = itemUploader,
                                    index = idx
                                )
                            )
                            idx++
                        }
                    } else {
                        // In case a single video was provided to the playlist tab
                        val id = json.get("id")?.takeIf { !it.isJsonNull }?.asString ?: UUID.randomUUID().toString()
                        val duration = try {
                            json.get("duration")?.takeIf { !it.isJsonNull }?.asLong
                        } catch (_: Exception) {
                            null
                        }
                        items.add(
                            PlaylistItem(
                                id = id,
                                title = playlistTitle,
                                url = url,
                                duration = duration,
                                thumbnail = playlistThumbnail,
                                uploader = uploader,
                                index = 0
                            )
                        )
                    }

                    if (items.isEmpty()) {
                        throw IllegalStateException("No downloadable videos found in playlist")
                    }

                    val playlistInfo = PlaylistInfo(
                        url = url,
                        title = playlistTitle,
                        author = uploader,
                        thumbnail = playlistThumbnail ?: items.firstOrNull()?.thumbnail,
                        items = items
                    )

                    Log.d(TAG, "Successfully probed playlist: $playlistTitle (${items.size} videos)")
                    return@withContext Result.success(playlistInfo)
                } catch (e: Exception) {
                    lastError = e
                    val errMsg = e.message?.lowercase() ?: ""
                    val isBotCheck = errMsg.contains("not a bot") ||
                            errMsg.contains("sign in to confirm") ||
                            errMsg.contains("bot")
                    if (!isBotCheck || client == clientFallbacks.last()) {
                        break
                    }
                    Log.w(TAG, "Bot check encountered for playlist $url, retrying with client: $client")
                }
            }

            val finalException = lastError ?: IllegalStateException("Failed to probe playlist")
            Log.e(TAG, "Error probing playlist: $url", finalException)
            Result.failure(cleanException(finalException))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error probing playlist: $url", e)
            Result.failure(cleanException(e))
        }
    }

    /**
     * Downloads and processes video or audio directly on the device
     */
    suspend fun downloadMedia(
        context: Context,
        url: String,
        option: QualityOption,
        tempDir: File,
        onProgress: (progress: Float, speedText: String, etaText: String, statusText: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val initRes = ensureInitialized(context)
            if (initRes.isFailure) {
                return@withContext Result.failure(
                    initRes.exceptionOrNull() ?: IllegalStateException("Engine initialization failed")
                )
            }

            val isYouTube = url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)
            val isInstagram = url.contains("instagram.com", ignoreCase = true)
            val jobId = UUID.randomUUID().toString()
            // Embed clean title in temporary filename to support Instant/Quick download mode
            val outputTemplate = File(tempDir, "${jobId}___%(title).100B.%(ext)s").absolutePath

            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("-o", outputTemplate)
                addOption("--no-warnings")
                addOption("--no-call-home")
                addOption("--no-check-certificates")
                addOption("--geo-bypass")
                addOption("--buffer-size", "64K")
                addOption("--concurrent-fragments", 5)
                addOption("--retries", 3)
                addOption("--socket-timeout", 20)
                addOption(
                    "--user-agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )

                if (isYouTube) {
                    addOption("--extractor-args", "youtube:player_client=web,tv;skip=translated_subs,comments")
                }
                if (isInstagram) {
                    addOption("--add-header", "Accept-Language: en-US,en;q=0.9")
                }

                if (option.isAudio) {
                    addOption("-f", "bestaudio/best")
                    addOption("--extract-audio")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "192K")
                } else {
                    val rawSpec = option.formatSpec
                    val safeFormatSpec = if (rawSpec.contains("/")) {
                        rawSpec
                    } else if (option.height != null) {
                        val h = option.height
                        "bestvideo[height<=$h]+bestaudio/best[height<=$h]/bestvideo[height<=$h]/best[height<=$h]/best"
                    } else {
                        "bestvideo+bestaudio/best"
                    }
                    addOption("-f", safeFormatSpec)
                    addOption("--merge-output-format", "mp4")
                }
            }

            var isMerging = false
            var isAudioStream = false

            YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                val speed = extractSpeedFromLog(line)
                val etaText = if (etaInSeconds > 0) "${etaInSeconds}s" else ""
                val currentLine = line ?: ""

                val statusText = when {
                    currentLine.contains("[Merger]", ignoreCase = true) || currentLine.contains("Merging formats", ignoreCase = true) -> {
                        isMerging = true
                        "Merging video & audio with FFmpeg..."
                    }
                    currentLine.contains("[ExtractAudio]", ignoreCase = true) || currentLine.contains("Destination: .*\\.mp3".toRegex()) -> {
                        isMerging = true
                        "Converting audio to MP3..."
                    }
                    currentLine.contains("[download] Destination", ignoreCase = true) -> {
                        if (currentLine.contains(".m4a") || currentLine.contains(".webm") || currentLine.contains(".mp3")) {
                            isAudioStream = true
                            "Downloading audio stream..."
                        } else {
                            "Downloading video stream..."
                        }
                    }
                    isMerging -> "Finalizing media file..."
                    isAudioStream -> "Downloading audio stream..."
                    else -> "Downloading..."
                }

                val effectiveProgress = if (isMerging) 98f else progress
                onProgress(effectiveProgress, speed, etaText, statusText)
            }

            // Find output file produced in tempDir matching jobId
            val matchingFiles = tempDir.listFiles { _, name -> name.startsWith(jobId) }
            val downloadedFile = matchingFiles?.filter { !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                ?.maxByOrNull { it.lastModified() }
                ?: throw IllegalStateException("Download finished but output file was not found")

            Result.success(downloadedFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error during download", e)
            Result.failure(cleanException(e))
        }
    }

    /**
     * Updates yt-dlp over-the-air from GitHub without updating the APK
     */
    suspend fun updateEngine(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized(context)
            val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)
            val currentVer = YoutubeDL.getInstance().version(context.applicationContext)
            Result.success(status?.name ?: "Version: $currentVer")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update yt-dlp", e)
            Result.failure(e)
        }
    }

    private fun extractSpeedFromLog(line: String?): String {
        if (line == null) return ""
        val speedRegex = Regex("""(\d+(\.\d+)?\s*(KiB|MiB|GiB|B)/s)""")
        return speedRegex.find(line)?.value ?: ""
    }

    private fun cleanException(e: Exception): Exception {
        val rawMsg = e.message ?: return e
        val errorLines = rawMsg.lines().filter { it.contains("ERROR:", ignoreCase = true) }
        val cleanMsg = if (errorLines.isNotEmpty()) {
            errorLines.joinToString("\n") { it.trim() }
        } else {
            rawMsg.lines().lastOrNull { it.isNotBlank() } ?: rawMsg
        }
        return Exception(cleanMsg, e)
    }
}

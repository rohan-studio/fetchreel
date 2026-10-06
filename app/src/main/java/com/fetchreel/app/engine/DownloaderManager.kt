package com.fetchreel.app.engine

import android.content.Context
import android.util.Log
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

            // Alternate player client profiles for YouTube (mirrors Fetchreel app.py fallback logic)
            val clientFallbacks = if (isYouTube) {
                listOf(null, "android,web", "tv", "tv_embedded", "ios")
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
                        addOption("--socket-timeout", 25)
                        addOption("--retries", 3)
                        addOption("--no-check-certificates")
                        addOption(
                            "--user-agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        )
                        if (client != null) {
                            addOption("--extractor-args", "youtube:player_client=$client")
                        }
                        if (isInstagram) {
                            addOption("--add-header", "Accept-Language: en-US,en;q=0.9")
                        }
                    }

                    val response = YoutubeDL.getInstance().execute(request)
                    val jsonStr = response.out?.trim() ?: throw IllegalStateException("Empty response from yt-dlp")
                    val json = gson.fromJson(jsonStr, JsonObject::class.java)

                    val title = json.get("title")?.asString ?: "Untitled"
                    val thumbnail = json.get("thumbnail")?.asString
                    val uploader = json.get("uploader")?.asString
                        ?: json.get("channel")?.asString
                        ?: json.get("extractor")?.asString
                    val duration = json.get("duration")?.asLong

                    val formatsArray = json.getAsJsonArray("formats")
                    val seenHeights = mutableSetOf<Int>()
                    val qualityOptions = mutableListOf<QualityOption>()

                    if (formatsArray != null) {
                        for (elem in formatsArray) {
                            if (!elem.isJsonObject) continue
                            val fObj = elem.asJsonObject
                            val height = fObj.get("height")?.takeIf { !it.isJsonNull }?.asInt ?: continue
                            val vcodec = fObj.get("vcodec")?.takeIf { !it.isJsonNull }?.asString ?: "none"

                            if (height > 0 && vcodec != "none" && !seenHeights.contains(height)) {
                                seenHeights.add(height)
                                qualityOptions.add(
                                    QualityOption(
                                        label = "${height}p",
                                        formatSpec = "bestvideo[height<=$height]+bestaudio/best[height<=$height]",
                                        height = height,
                                        isAudio = false
                                    )
                                )
                            }
                        }
                    }

                    qualityOptions.sortByDescending { it.height ?: 0 }
                    qualityOptions.add(
                        0,
                        QualityOption(
                            label = "Best available",
                            formatSpec = "bestvideo+bestaudio/best",
                            height = null,
                            isAudio = false
                        )
                    )
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
     * Downloads and processes video or audio directly on the device
     */
    suspend fun downloadMedia(
        context: Context,
        url: String,
        option: QualityOption,
        tempDir: File,
        onProgress: (progress: Float, speedText: String, etaText: String) -> Unit
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
            val outputTemplate = File(tempDir, "$jobId.%(ext)s").absolutePath

            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("-o", outputTemplate)
                addOption("--concurrent-fragments", 4)
                addOption("--retries", 3)
                addOption("--socket-timeout", 30)
                addOption("--no-check-certificates")
                addOption(
                    "--user-agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )

                if (isYouTube) {
                    addOption("--extractor-args", "youtube:player_client=android,web")
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
                    addOption("-f", option.formatSpec)
                    addOption("--merge-output-format", "mp4")
                }
            }

            YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                val speed = extractSpeedFromLog(line)
                val etaText = if (etaInSeconds > 0) "${etaInSeconds}s" else ""
                onProgress(progress, speed, etaText)
            }

            // Find output file produced in tempDir matching jobId
            val matchingFiles = tempDir.listFiles { _, name -> name.startsWith(jobId) }
            val downloadedFile = matchingFiles?.maxByOrNull { it.lastModified() }
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

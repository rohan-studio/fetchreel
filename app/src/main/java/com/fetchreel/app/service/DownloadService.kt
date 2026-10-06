package com.fetchreel.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fetchreel.app.FetchreelApplication
import com.fetchreel.app.MainActivity
import com.fetchreel.app.R
import com.fetchreel.app.data.DownloadEvent
import com.fetchreel.app.data.PlaylistItem
import com.fetchreel.app.data.PlaylistDownloadEvent
import com.fetchreel.app.data.QualityOption
import com.fetchreel.app.engine.DownloaderManager
import com.fetchreel.app.engine.StorageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DownloadService : Service() {

    data class PlaylistDownloadRequest(
        val playlistTitle: String,
        val items: List<PlaylistItem>,
        val option: QualityOption
    )

    companion object {
        private const val TAG = "DownloadService"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_ID_PLAYLIST = 2001

        const val ACTION_START_DOWNLOAD = "ACTION_START_DOWNLOAD"
        const val ACTION_START_PLAYLIST_DOWNLOAD = "ACTION_START_PLAYLIST_DOWNLOAD"
        const val EXTRA_URL = "EXTRA_URL"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        const val EXTRA_FORMAT_SPEC = "EXTRA_FORMAT_SPEC"
        const val EXTRA_IS_AUDIO = "EXTRA_IS_AUDIO"
        const val EXTRA_LABEL = "EXTRA_LABEL"

        private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 64)
        val events = _events.asSharedFlow()

        private val _playlistEvents = MutableSharedFlow<PlaylistDownloadEvent>(extraBufferCapacity = 64)
        val playlistEvents = _playlistEvents.asSharedFlow()

        @Volatile
        private var pendingPlaylistRequest: PlaylistDownloadRequest? = null

        fun startDownload(
            context: Context,
            url: String,
            title: String,
            option: QualityOption
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FORMAT_SPEC, option.formatSpec)
                putExtra(EXTRA_IS_AUDIO, option.isAudio)
                putExtra(EXTRA_LABEL, option.label)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startPlaylistDownload(
            context: Context,
            playlistTitle: String,
            items: List<PlaylistItem>,
            option: QualityOption
        ) {
            pendingPlaylistRequest = PlaylistDownloadRequest(
                playlistTitle = playlistTitle,
                items = items,
                option = option
            )
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_PLAYLIST_DOWNLOAD
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Media"
                val formatSpec = intent.getStringExtra(EXTRA_FORMAT_SPEC) ?: "bestvideo+bestaudio/best"
                val isAudio = intent.getBooleanExtra(EXTRA_IS_AUDIO, false)
                val label = intent.getStringExtra(EXTRA_LABEL) ?: ""

                val option = QualityOption(
                    label = label,
                    formatSpec = formatSpec,
                    isAudio = isAudio
                )

                startForegroundWithNotification(title)
                executeDownload(url, title, option)
            }
            ACTION_START_PLAYLIST_DOWNLOAD -> {
                val req = pendingPlaylistRequest ?: return START_NOT_STICKY
                startForegroundForPlaylist(req.playlistTitle, req.items.size)
                executePlaylistDownload(req)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification(title: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, FetchreelApplication.CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText("Starting download...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startForegroundForPlaylist(playlistTitle: String, totalCount: Int) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, FetchreelApplication.CHANNEL_ID)
            .setContentTitle("Playlist: $playlistTitle")
            .setContentText("Preparing to download $totalCount videos...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun executeDownload(url: String, title: String, option: QualityOption) {
        serviceScope.launch(Dispatchers.IO) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Fetchreel::DownloadWakeLock"
            )
            try {
                wakeLock?.acquire(45 * 60 * 1000L) // 45-minute safety timeout to prevent CPU sleep
                val tempDir = StorageHelper.getAppTempFolder(this@DownloadService)
                var lastUpdate = 0L

                val result = DownloaderManager.downloadMedia(
                    context = this@DownloadService,
                    url = url,
                    option = option,
                    tempDir = tempDir
                ) { progress, speed, eta ->
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 500) {
                        lastUpdate = now
                        updateNotification(title, progress.toInt(), speed, eta)
                        _events.tryEmit(DownloadEvent.Progress(progress, speed, eta))
                    }
                }

                result.fold(
                    onSuccess = { tempFile ->
                        try {
                            val extension = if (option.isAudio) ".mp3" else ".mp4"
                            val fileName = "$title$extension"
                            val savedPath = StorageHelper.saveToPublicStorage(
                                context = this@DownloadService,
                                tempFile = tempFile,
                                desiredFileName = fileName,
                                isAudio = option.isAudio
                            )
                            showCompletedNotification(title, savedPath)
                            _events.tryEmit(DownloadEvent.Completed(title, savedPath, option.isAudio))
                        } catch (e: Exception) {
                            showErrorNotification(title, e.localizedMessage ?: "Failed to save file")
                            _events.tryEmit(DownloadEvent.Failed(e.localizedMessage ?: "Failed to save file"))
                        } finally {
                            stopForeground(STOP_FOREGROUND_DETACH)
                            stopSelf()
                        }
                    },
                    onFailure = { error ->
                        showErrorNotification(title, error.localizedMessage ?: "Download failed")
                        _events.tryEmit(DownloadEvent.Failed(error.localizedMessage ?: "Download failed"))
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                )
            } finally {
                if (wakeLock?.isHeld == true) {
                    try {
                        wakeLock.release()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun executePlaylistDownload(request: PlaylistDownloadRequest) {
        serviceScope.launch(Dispatchers.IO) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Fetchreel::PlaylistDownloadWakeLock"
            )
            val items = request.items
            val totalCount = items.size
            var successCount = 0
            var failedCount = 0
            val tempDir = StorageHelper.getAppTempFolder(this@DownloadService)

            try {
                wakeLock?.acquire(120 * 60 * 1000L) // 2-hour safety timeout for large playlist batches

                for (i in items.indices) {
                    if (!isActive) break
                    val item = items[i]
                    val itemIndex = i + 1

                    updatePlaylistNotification(
                        playlistTitle = request.playlistTitle,
                        currentIndex = itemIndex,
                        totalCount = totalCount,
                        itemTitle = item.title,
                        progress = 0,
                        speed = "",
                        eta = ""
                    )

                    val overallBase = (i.toFloat() / totalCount.toFloat()) * 100f
                    _playlistEvents.tryEmit(
                        PlaylistDownloadEvent.ItemProgress(
                            currentIndex = itemIndex,
                            totalCount = totalCount,
                            currentTitle = item.title,
                            itemProgress = 0f,
                            overallProgress = overallBase,
                            speed = "",
                            eta = ""
                        )
                    )

                    var lastUpdate = 0L
                    val result = DownloaderManager.downloadMedia(
                        context = this@DownloadService,
                        url = item.url,
                        option = request.option,
                        tempDir = tempDir
                    ) { progress, speed, eta ->
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 500) {
                            lastUpdate = now
                            val overall = ((i.toFloat() + (progress / 100f)) / totalCount.toFloat()) * 100f
                            updatePlaylistNotification(
                                playlistTitle = request.playlistTitle,
                                currentIndex = itemIndex,
                                totalCount = totalCount,
                                itemTitle = item.title,
                                progress = progress.toInt(),
                                speed = speed,
                                eta = eta
                            )
                            _playlistEvents.tryEmit(
                                PlaylistDownloadEvent.ItemProgress(
                                    currentIndex = itemIndex,
                                    totalCount = totalCount,
                                    currentTitle = item.title,
                                    itemProgress = progress,
                                    overallProgress = overall,
                                    speed = speed,
                                    eta = eta
                                )
                            )
                        }
                    }

                    result.fold(
                        onSuccess = { tempFile ->
                            try {
                                val extension = if (request.option.isAudio) ".mp3" else ".mp4"
                                val prefix = if (totalCount > 1) String.format("%02d - ", itemIndex) else ""
                                val fileName = "$prefix${item.title}$extension"
                                StorageHelper.saveToPublicStorage(
                                    context = this@DownloadService,
                                    tempFile = tempFile,
                                    desiredFileName = fileName,
                                    isAudio = request.option.isAudio
                                )
                                successCount++
                            } catch (e: Exception) {
                                Log.e(TAG, "Error saving playlist item: ${item.title}", e)
                                failedCount++
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Error downloading playlist item: ${item.title}", error)
                            failedCount++
                        }
                    )
                }

                showPlaylistCompletedNotification(request.playlistTitle, successCount, failedCount)
                _playlistEvents.tryEmit(
                    PlaylistDownloadEvent.Completed(
                        playlistTitle = request.playlistTitle,
                        successfulCount = successCount,
                        failedCount = failedCount,
                        isAudio = request.option.isAudio
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in playlist download", e)
                _playlistEvents.tryEmit(PlaylistDownloadEvent.Failed(e.localizedMessage ?: "Playlist download failed"))
            } finally {
                if (wakeLock?.isHeld == true) {
                    try {
                        wakeLock.release()
                    } catch (_: Exception) {}
                }
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun updateNotification(title: String, progress: Int, speed: String, eta: String) {
        val text = buildString {
            if (speed.isNotEmpty()) append(speed)
            if (eta.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append("ETA: ").append(eta)
            }
            if (isEmpty()) append("$progress%")
        }

        val notification = NotificationCompat.Builder(this, FetchreelApplication.CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun updatePlaylistNotification(
        playlistTitle: String,
        currentIndex: Int,
        totalCount: Int,
        itemTitle: String,
        progress: Int,
        speed: String,
        eta: String
    ) {
        val sub = buildString {
            append("[$currentIndex/$totalCount] ")
            append(itemTitle)
            if (speed.isNotEmpty()) append(" • ").append(speed)
            if (eta.isNotEmpty()) append(" • ETA: ").append(eta)
        }

        val notification = NotificationCompat.Builder(this, FetchreelApplication.CHANNEL_ID)
            .setContentTitle("Playlist: $playlistTitle ($currentIndex/$totalCount)")
            .setContentText(sub)
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompletedNotification(title: String, path: String) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, FetchreelApplication.CHANNEL_ID)
            .setContentTitle("Download Complete!")
            .setContentText("$title saved to Downloads/Fetchreel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showPlaylistCompletedNotification(playlistTitle: String, successCount: Int, failedCount: Int) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = if (failedCount == 0) {
            "All $successCount items saved to Downloads/Fetchreel"
        } else {
            "$successCount downloaded ($failedCount failed). Saved to Downloads/Fetchreel"
        }

        val notification = NotificationCompat.Builder(this, FetchreelApplication.CHANNEL_ID)
            .setContentTitle("Playlist Finished: $playlistTitle")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showErrorNotification(title: String, error: String) {
        val notification = NotificationCompat.Builder(this, FetchreelApplication.CHANNEL_ID)
            .setContentTitle("Download Failed: $title")
            .setContentText(error)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 2, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

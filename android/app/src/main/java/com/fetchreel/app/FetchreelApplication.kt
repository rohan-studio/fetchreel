package com.fetchreel.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.fetchreel.app.engine.DownloaderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FetchreelApplication : Application() {

    companion object {
        const val CHANNEL_ID = "fetchreel_downloads_channel"
        private const val TAG = "FetchreelApp"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initDownloadEngines()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun initDownloadEngines() {
        CoroutineScope(Dispatchers.IO).launch {
            DownloaderManager.ensureInitialized(this@FetchreelApplication)
        }
    }
}

package com.fetchreel.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fetchreel.app.data.DownloadEvent
import com.fetchreel.app.data.DownloadState
import com.fetchreel.app.data.PlaylistDownloadEvent
import com.fetchreel.app.data.PlaylistDownloadState
import com.fetchreel.app.data.PlaylistInfo
import com.fetchreel.app.data.QualityOption
import com.fetchreel.app.engine.DownloaderManager
import com.fetchreel.app.engine.StorageHelper
import com.fetchreel.app.service.DownloadService
import com.fetchreel.app.ui.components.*
import com.fetchreel.app.ui.theme.*
import kotlinx.coroutines.launch

enum class MainSection {
    SINGLE, PLAYLIST
}

@Composable
fun MainScreen(
    initialSharedUrl: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentSection by rememberSaveable { mutableStateOf(MainSection.SINGLE) }

    // Single link state
    var url by remember { mutableStateOf(initialSharedUrl ?: "") }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    val activeDownload by DownloadService.activeDownload.collectAsState()

    // Playlist state
    var playlistUrl by remember { mutableStateOf("") }
    var playlistDownloadState by remember { mutableStateOf<PlaylistDownloadState>(PlaylistDownloadState.Idle) }

    // General state
    var recentDownloads by remember { mutableStateOf(StorageHelper.queryRecentDownloads(context)) }
    var isUpdatingEngine by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var lastCapturedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var autoCaptureEnabled by rememberSaveable { mutableStateOf(true) }

    fun refreshRecentDownloads() {
        recentDownloads = StorageHelper.queryRecentDownloads(context)
    }

    // Probing for single media
    fun startProbing(targetUrl: String) {
        if (targetUrl.isBlank()) return
        scope.launch {
            downloadState = DownloadState.Probing
            val result = DownloaderManager.probeUrl(context, targetUrl.trim())
            result.fold(
                onSuccess = { info ->
                    val defaultOption = info.options.firstOrNull() ?: QualityOption(
                        label = "Best",
                        formatSpec = "bestvideo+bestaudio/best"
                    )
                    downloadState = DownloadState.Probed(info, defaultOption)
                },
                onFailure = { error ->
                    val errorMsg = error.localizedMessage ?: "Failed to read media link"
                    downloadState = DownloadState.Error(errorMsg)
                    snackbarHostState.showSnackbar(errorMsg)
                }
            )
        }
    }

    // Probing for playlist
    fun startPlaylistProbing(targetUrl: String) {
        if (targetUrl.isBlank()) return
        scope.launch {
            playlistDownloadState = PlaylistDownloadState.Probing
            val result = DownloaderManager.probePlaylist(context, targetUrl.trim())
            result.fold(
                onSuccess = { plInfo ->
                    val allIds = plInfo.items.map { it.id }.toSet()
                    val defaultOption = PlaylistInfo.defaultQualityOptions.first()
                    playlistDownloadState = PlaylistDownloadState.Probed(
                        playlist = plInfo,
                        selectedItemIds = allIds,
                        selectedOption = defaultOption
                    )
                },
                onFailure = { error ->
                    val errorMsg = error.localizedMessage ?: "Failed to read playlist link"
                    playlistDownloadState = PlaylistDownloadState.Error(errorMsg)
                    snackbarHostState.showSnackbar(errorMsg)
                }
            )
        }
    }

    fun checkClipboardForLink() {
        if (!autoCaptureEnabled) return
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val clipText = clipData.getItemAt(0)?.coerceToText(context)?.toString()?.trim() ?: ""
                    val urlMatch = Regex("""https?://[^\s]+""").find(clipText)?.value
                    if (!urlMatch.isNullOrBlank() && urlMatch != lastCapturedUrl) {
                        lastCapturedUrl = urlMatch
                        if (currentSection == MainSection.PLAYLIST) {
                            if (urlMatch != playlistUrl) {
                                playlistUrl = urlMatch
                                if (playlistDownloadState !is PlaylistDownloadState.Downloading && playlistDownloadState !is PlaylistDownloadState.Probing) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "📋 Playlist link captured from clipboard!",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                    startPlaylistProbing(urlMatch)
                                }
                            }
                        } else {
                            if (urlMatch != url) {
                                url = urlMatch
                                if (PlaylistInfo.isPlaylistUrl(urlMatch)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "⚠️ Playlist link detected in clipboard",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                } else {
                                    if (downloadState !is DownloadState.Downloading && downloadState !is DownloadState.Probing) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = "📋 Link auto-captured from clipboard!",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                        startProbing(urlMatch)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // Auto-capture link & refresh files on resume
    DisposableEffect(lifecycleOwner, autoCaptureEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkClipboardForLink()
                refreshRecentDownloads()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-probe if opened with a shared URL from another app
    LaunchedEffect(initialSharedUrl) {
        if (!initialSharedUrl.isNullOrBlank()) {
            if (PlaylistInfo.isPlaylistUrl(initialSharedUrl)) {
                currentSection = MainSection.PLAYLIST
                playlistUrl = initialSharedUrl
                startPlaylistProbing(initialSharedUrl)
            } else {
                currentSection = MainSection.SINGLE
                url = initialSharedUrl
                startProbing(initialSharedUrl)
            }
        }
    }

    // Listen to background download service events for single media
    LaunchedEffect(Unit) {
        DownloadService.events.collect { event ->
            when (event) {
                is DownloadEvent.Progress -> {
                    val current = downloadState
                    if (current is DownloadState.Probed) {
                        downloadState = DownloadState.Downloading(
                            info = current.info,
                            progress = event.progress,
                            speedText = event.speed,
                            etaText = event.eta
                        )
                    } else if (current is DownloadState.Downloading) {
                        downloadState = current.copy(
                            progress = event.progress,
                            speedText = event.speed,
                            etaText = event.eta
                        )
                    }
                }
                is DownloadEvent.Completed -> {
                    downloadState = DownloadState.Completed(
                        title = event.title,
                        savedPath = event.path,
                        isAudio = event.isAudio
                    )
                    refreshRecentDownloads()
                    Toast.makeText(context, "Saved to Downloads/Fetchreel!", Toast.LENGTH_LONG).show()
                }
                is DownloadEvent.Failed -> {
                    downloadState = DownloadState.Error(event.error)
                    snackbarHostState.showSnackbar("Download error: ${event.error}")
                }
            }
        }
    }

    // Listen to background download service events for playlist batch downloads
    LaunchedEffect(Unit) {
        DownloadService.playlistEvents.collect { event ->
            when (event) {
                is PlaylistDownloadEvent.ItemProgress -> {
                    val current = playlistDownloadState
                    val plInfo = when (current) {
                        is PlaylistDownloadState.Probed -> current.playlist
                        is PlaylistDownloadState.Downloading -> current.playlist
                        else -> null
                    }
                    if (plInfo != null) {
                        playlistDownloadState = PlaylistDownloadState.Downloading(
                            playlist = plInfo,
                            currentIndex = event.currentIndex,
                            totalCount = event.totalCount,
                            currentItemTitle = event.currentTitle,
                            itemProgress = event.itemProgress,
                            overallProgress = event.overallProgress,
                            speedText = event.speed,
                            etaText = event.eta
                        )
                    }
                }
                is PlaylistDownloadEvent.Completed -> {
                    playlistDownloadState = PlaylistDownloadState.Completed(
                        playlistTitle = event.playlistTitle,
                        downloadedCount = event.successfulCount,
                        failedCount = event.failedCount,
                        isAudio = event.isAudio
                    )
                    refreshRecentDownloads()
                    Toast.makeText(context, "Playlist finished: ${event.successfulCount} saved!", Toast.LENGTH_LONG).show()
                }
                is PlaylistDownloadEvent.Failed -> {
                    playlistDownloadState = PlaylistDownloadState.Error(event.error)
                    snackbarHostState.showSnackbar("Playlist error: ${event.error}")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            QrCodeFab(onClick = { showQrDialog = true })
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppHeader(
                isUpdatingEngine = isUpdatingEngine,
                onShowQr = { showQrDialog = true },
                onUpdateEngine = {
                    scope.launch {
                        isUpdatingEngine = true
                        val res = DownloaderManager.updateEngine(context)
                        isUpdatingEngine = false
                        res.fold(
                            onSuccess = { msg ->
                                snackbarHostState.showSnackbar("Engine updated: $msg")
                            },
                            onFailure = { err ->
                                snackbarHostState.showSnackbar("Update check: ${err.localizedMessage}")
                            }
                        )
                    }
                }
            )

            // Section Switcher (Single Video vs Playlist)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isSingleSelected = currentSection == MainSection.SINGLE
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSingleSelected) AccentIndigo else Color.Transparent)
                        .clickable { currentSection = MainSection.SINGLE }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = if (isSingleSelected) TextPrimary else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Single Video",
                            color = if (isSingleSelected) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSingleSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                val isPlaylistSelected = currentSection == MainSection.PLAYLIST
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isPlaylistSelected) AccentIndigo else Color.Transparent)
                        .clickable { currentSection = MainSection.PLAYLIST }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = if (isPlaylistSelected) TextPrimary else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Playlist",
                            color = if (isPlaylistSelected) TextPrimary else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isPlaylistSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Clipboard auto-capture toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = null,
                        tint = if (autoCaptureEnabled) AccentCyan else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Auto-capture copied links",
                        color = if (autoCaptureEnabled) TextPrimary else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = autoCaptureEnabled,
                    onCheckedChange = { autoCaptureEnabled = it },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentCyan,
                        checkedTrackColor = AccentCyan.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkCard
                    )
                )
            }

            // Active Download Card (prominently displayed when single download is active in background)
            activeDownload?.let { active ->
                DownloadProgressCard(download = active)
            }

            // ==================== SECTION 1: SINGLE MEDIA ====================
            if (currentSection == MainSection.SINGLE) {
                UrlInputField(
                    url = url,
                    onUrlChange = { url = it },
                    isProbing = downloadState is DownloadState.Probing,
                    onFetchClicked = { startProbing(url) }
                )

                // Warning Banner if playlist link is pasted in Single Video section
                if (PlaylistInfo.isPlaylistUrl(url)) {
                    PlaylistWarningBanner(
                        onSwitchToPlaylist = {
                            playlistUrl = url
                            currentSection = MainSection.PLAYLIST
                            startPlaylistProbing(url)
                        }
                    )
                }

                when (val state = downloadState) {
                    is DownloadState.Probing -> {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = AccentIndigoLight,
                                    strokeWidth = 2.5.dp
                                )
                                Column {
                                    Text(
                                        text = "Analyzing link...",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Extracting video formats and resolutions",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    is DownloadState.Probed -> {
                        if (activeDownload == null) {
                            VideoPreviewCard(info = state.info)
                            QualitySelector(
                                options = state.info.options,
                                selectedOption = state.selectedOption,
                                onOptionSelected = { option ->
                                    downloadState = state.copy(selectedOption = option)
                                },
                                onDownloadClicked = {
                                    DownloadService.startDownload(
                                        context = context,
                                        url = state.info.url,
                                        title = state.info.title,
                                        option = state.selectedOption,
                                        thumbnail = state.info.thumbnail
                                    )
                                }
                            )
                        }
                    }
                    is DownloadState.Downloading -> {
                        if (activeDownload == null) {
                            VideoPreviewCard(info = state.info)
                            DownloadProgressCard(
                                info = state.info,
                                progress = state.progress,
                                speedText = state.speedText,
                                etaText = state.etaText
                            )
                        }
                    }
                    is DownloadState.Completed -> {
                        if (activeDownload == null) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "✓ Download Complete!",
                                        color = SuccessGreen,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "File saved to ${state.savedPath}",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    is DownloadState.Error -> {
                        if (activeDownload == null) {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Error",
                                        color = ErrorRed,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = state.message,
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    else -> { /* Idle */ }
                }
            }

            // ==================== SECTION 2: PLAYLIST ====================
            if (currentSection == MainSection.PLAYLIST) {
                UrlInputField(
                    url = playlistUrl,
                    onUrlChange = { playlistUrl = it },
                    isProbing = playlistDownloadState is PlaylistDownloadState.Probing,
                    onFetchClicked = { startPlaylistProbing(playlistUrl) }
                )

                when (val state = playlistDownloadState) {
                    is PlaylistDownloadState.Probing -> {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = AccentCyan,
                                    strokeWidth = 2.5.dp
                                )
                                Column {
                                    Text(
                                        text = "Fetching Playlist...",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Extracting video list and entries",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    is PlaylistDownloadState.Probed -> {
                        PlaylistCard(
                            playlist = state.playlist,
                            selectedItemIds = state.selectedItemIds,
                            selectedOption = state.selectedOption,
                            onToggleItem = { itemId ->
                                val updated = state.selectedItemIds.toMutableSet()
                                if (updated.contains(itemId)) {
                                    updated.remove(itemId)
                                } else {
                                    updated.add(itemId)
                                }
                                playlistDownloadState = state.copy(selectedItemIds = updated)
                            },
                            onSelectAll = {
                                val allIds = state.playlist.items.map { it.id }.toSet()
                                playlistDownloadState = state.copy(selectedItemIds = allIds)
                            },
                            onDeselectAll = {
                                playlistDownloadState = state.copy(selectedItemIds = emptySet())
                            },
                            onOptionSelected = { opt ->
                                playlistDownloadState = state.copy(selectedOption = opt)
                            },
                            onDownloadClicked = {
                                val selectedItems = state.playlist.items.filter { state.selectedItemIds.contains(it.id) }
                                if (selectedItems.isNotEmpty()) {
                                    DownloadService.startPlaylistDownload(
                                        context = context,
                                        playlistTitle = state.playlist.title,
                                        items = selectedItems,
                                        option = state.selectedOption
                                    )
                                }
                            }
                        )
                    }
                    is PlaylistDownloadState.Downloading -> {
                        PlaylistProgressCard(
                            playlist = state.playlist,
                            currentIndex = state.currentIndex,
                            totalCount = state.totalCount,
                            currentItemTitle = state.currentItemTitle,
                            itemProgress = state.itemProgress,
                            overallProgress = state.overallProgress,
                            speedText = state.speedText,
                            etaText = state.etaText
                        )
                    }
                    is PlaylistDownloadState.Completed -> {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "✓ Playlist Download Complete!",
                                    color = SuccessGreen,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val summary = if (state.failedCount == 0) {
                                    "All ${state.downloadedCount} videos saved to Downloads/Fetchreel"
                                } else {
                                    "${state.downloadedCount} saved (${state.failedCount} failed) from '${state.playlistTitle}'"
                                }
                                Text(
                                    text = summary,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    is PlaylistDownloadState.Error -> {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Error Fetching Playlist",
                                    color = ErrorRed,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.message,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    else -> { /* Idle */ }
                }
            }

            // Recent downloads list shown at the bottom of both sections
            RecentDownloadsList(
                items = recentDownloads,
                onDeleteItem = { mediaItem ->
                    val success = StorageHelper.deleteMediaFile(context, mediaItem)
                    refreshRecentDownloads()
                    scope.launch {
                        if (success) {
                            snackbarHostState.showSnackbar("🗑️ Deleted ${mediaItem.name}")
                        } else {
                            snackbarHostState.showSnackbar("Could not delete file")
                        }
                    }
                }
            )

            WatermarkFooter()
        }

        if (showQrDialog) {
            QrCodeDownloadDialog(onDismiss = { showQrDialog = false })
        }
    }
}

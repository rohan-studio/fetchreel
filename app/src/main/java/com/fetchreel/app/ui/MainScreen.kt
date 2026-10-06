package com.fetchreel.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fetchreel.app.data.DownloadEvent
import com.fetchreel.app.data.DownloadState
import com.fetchreel.app.data.QualityOption
import com.fetchreel.app.data.VideoInfo
import com.fetchreel.app.engine.DownloaderManager
import com.fetchreel.app.engine.StorageHelper
import com.fetchreel.app.service.DownloadService
import com.fetchreel.app.ui.components.*
import com.fetchreel.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    initialSharedUrl: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var url by remember { mutableStateOf(initialSharedUrl ?: "") }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var recentDownloads by remember { mutableStateOf(StorageHelper.queryRecentDownloads(context)) }
    var isUpdatingEngine by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var lastCapturedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var autoCaptureEnabled by rememberSaveable { mutableStateOf(true) }

    fun refreshRecentDownloads() {
        recentDownloads = StorageHelper.queryRecentDownloads(context)
    }

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

    fun checkClipboardForLink() {
        if (!autoCaptureEnabled) return
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val clipText = clipData.getItemAt(0)?.coerceToText(context)?.toString()?.trim() ?: ""
                    val urlMatch = Regex("""https?://[^\s]+""").find(clipText)?.value
                    if (!urlMatch.isNullOrBlank() && urlMatch != lastCapturedUrl && urlMatch != url) {
                        lastCapturedUrl = urlMatch
                        url = urlMatch
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
            url = initialSharedUrl
            startProbing(initialSharedUrl)
        }
    }

    // Listen to background download service events
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
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

            UrlInputField(
                url = url,
                onUrlChange = { url = it },
                isProbing = downloadState is DownloadState.Probing,
                onFetchClicked = { startProbing(url) }
            )

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
                                option = state.selectedOption
                            )
                        }
                    )
                }
                is DownloadState.Downloading -> {
                    VideoPreviewCard(info = state.info)
                    DownloadProgressCard(
                        info = state.info,
                        progress = state.progress,
                        speedText = state.speedText,
                        etaText = state.etaText
                    )
                }
                is DownloadState.Completed -> {
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
                is DownloadState.Error -> {
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
                else -> { /* Idle */ }
            }

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

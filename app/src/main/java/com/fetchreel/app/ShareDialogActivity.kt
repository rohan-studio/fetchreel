package com.fetchreel.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fetchreel.app.data.QualityOption
import com.fetchreel.app.data.VideoInfo
import com.fetchreel.app.engine.DownloaderManager
import com.fetchreel.app.service.DownloadService
import com.fetchreel.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Snaptube-style direct overlay popup activity.
 * Appears as a sleek translucent bottom sheet over Instagram, YouTube, Facebook, TikTok, etc.
 * Allows one-tap MP3 / 4K / 1080p / 720p download and dismisses back to the host app instantly.
 */
class ShareDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.dataString
        val sharedUrl = extractUrl(rawText)

        setContent {
            FetchreelTheme {
                SharePopupScreen(
                    sharedUrl = sharedUrl,
                    onDismiss = { finish() },
                    onOpenInApp = { url ->
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, url)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(mainIntent)
                        finish()
                    },
                    onStartDownload = { info, option ->
                        DownloadService.startDownload(
                            context = this,
                            url = info.url,
                            title = info.title,
                            option = option,
                            thumbnail = info.thumbnail
                        )
                        Toast.makeText(
                            this,
                            "🚀 Starting download: ${option.label}",
                            Toast.LENGTH_SHORT
                        ).show()
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        }
                        startActivity(mainIntent)
                        finish()
                    }
                )
            }
        }
    }

    private fun extractUrl(text: String?): String? {
        if (text == null) return null
        val regex = Regex("""https?://[^\s]+""")
        return regex.find(text)?.value
    }
}

private sealed interface PopupProbeState {
    data object Loading : PopupProbeState
    data class Success(val info: VideoInfo) : PopupProbeState
    data class Error(val message: String) : PopupProbeState
}

@Composable
private fun SharePopupScreen(
    sharedUrl: String?,
    onDismiss: () -> Unit,
    onOpenInApp: (String) -> Unit,
    onStartDownload: (VideoInfo, QualityOption) -> Unit
) {
    val context = LocalContext.current
    var probeState by remember { mutableStateOf<PopupProbeState>(PopupProbeState.Loading) }

    BackHandler { onDismiss() }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl.isNullOrBlank()) {
            probeState = PopupProbeState.Error("No valid video or media link found in shared text.")
            return@LaunchedEffect
        }
        probeState = PopupProbeState.Loading
        val result = DownloaderManager.probeUrl(context, sharedUrl)
        result.fold(
            onSuccess = { info -> probeState = PopupProbeState.Success(info) },
            onFailure = { err ->
                probeState = PopupProbeState.Error(err.localizedMessage ?: "Failed to extract formats")
            }
        )
    }

    // Outer translucent backdrop (tapping outside dismisses back to host app)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Bottom popup card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* consume clicks */ }
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top drag handle indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextMuted.copy(alpha = 0.5f))
                )

                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(AccentIndigo, AccentCyan))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Fetchreel Quick Download",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Direct download popup",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!sharedUrl.isNullOrBlank()) {
                            IconButton(onClick = { onOpenInApp(sharedUrl) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open Full App",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                when (val state = probeState) {
                    is PopupProbeState.Loading -> {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = AccentCyan,
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = "Analyzing media link...",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Extracting MP3 audio, 4K, 1080p, and 720p video formats",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    is PopupProbeState.Error -> {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = ErrorRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "Could not fetch media",
                                        color = ErrorRed,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = state.message,
                                    color = TextPrimary,
                                    fontSize = 12.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (!sharedUrl.isNullOrBlank()) {
                                        TextButton(onClick = { onOpenInApp(sharedUrl) }) {
                                            Text("Open In Full App", color = AccentIndigoLight)
                                        }
                                    }
                                    TextButton(onClick = onDismiss) {
                                        Text("Close", color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }

                    is PopupProbeState.Success -> {
                        val info = state.info
                        val audioOptions = info.options.filter { it.isAudio }
                        val videoOptions = info.options.filter { !it.isAudio }

                        // Mini Video Preview Card
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!info.thumbnail.isNullOrBlank()) {
                                    AsyncImage(
                                        model = info.thumbnail,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .width(96.dp)
                                            .height(58.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = info.title,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!info.uploader.isNullOrBlank()) {
                                            Text(
                                                text = info.uploader,
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (info.formattedDuration().isNotEmpty()) {
                                            Text(
                                                text = "• ${info.formattedDuration()}",
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Section 1: AUDIO (MP3)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    tint = AccentIndigoLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "AUDIO (MP3)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentIndigoLight,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            val defaultAudio = audioOptions.firstOrNull() ?: QualityOption(
                                label = "Audio only (MP3)",
                                formatSpec = "bestaudio/best",
                                isAudio = true
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onStartDownload(info, defaultAudio) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkCard),
                                border = BorderStroke(1.dp, AccentIndigo.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(AccentIndigo.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Audiotrack,
                                                contentDescription = null,
                                                tint = AccentIndigoLight,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "MP3 High Quality Audio",
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Extract soundtrack only",
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = { onStartDownload(info, defaultAudio) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("MP3", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Section 2: VIDEO QUALITIES (4K, 1080p, 720p, etc.)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "VIDEO QUALITIES",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            // Render Video Quality Tiles
                            val effectiveVideoOptions = if (videoOptions.isEmpty()) {
                                listOf(
                                    QualityOption(
                                        label = "Best available",
                                        formatSpec = "bestvideo+bestaudio/best",
                                        isAudio = false
                                    )
                                )
                            } else {
                                videoOptions
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                effectiveVideoOptions.forEach { opt ->
                                    val badgeText = when {
                                        (opt.height ?: 0) >= 2160 -> "4K UHD"
                                        (opt.height ?: 0) >= 1440 -> "2K QHD"
                                        (opt.height ?: 0) >= 1080 -> "1080p FHD"
                                        (opt.height ?: 0) >= 720 -> "720p HD"
                                        (opt.height ?: 0) >= 480 -> "480p SD"
                                        (opt.height ?: 0) >= 360 -> "360p"
                                        opt.label.contains("Best", ignoreCase = true) -> "Best"
                                        else -> opt.label
                                    }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onStartDownload(info, opt) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                                        border = BorderStroke(1.dp, DarkCardBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(AccentCyan.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Videocam,
                                                        contentDescription = null,
                                                        tint = AccentCyan,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                                Text(
                                                    text = opt.label,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Quality badge tag
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(DarkSurface)
                                                        .border(1.dp, DarkCardBorder, RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = badgeText,
                                                        color = if ((opt.height ?: 0) >= 1080) AccentCyan else TextSecondary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { onStartDownload(info, opt) },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Download,
                                                        contentDescription = "Download",
                                                        tint = AccentCyan,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Disclaimer & Watermark Footer
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ROHAN STUDIO • Dev: Rohan Arya",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "⚠️ For Educational & Research Purposes Only",
                        color = TextMuted.copy(alpha = 0.7f),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

package com.fetchreel.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fetchreel.app.data.ActiveDownload
import com.fetchreel.app.data.VideoInfo
import com.fetchreel.app.ui.theme.*

@Composable
fun DownloadProgressCard(
    download: ActiveDownload
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (download.progress / 100f).coerceIn(0f, 1f),
        label = "DownloadProgress"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AccentIndigo.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Status spinner & Quality badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AccentCyan
                    )
                    Text(
                        text = "Downloading...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentIndigo.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = download.option.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan
                    )
                }
            }

            // Thumbnail + Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkCard),
                    contentAlignment = Alignment.Center
                ) {
                    if (!download.thumbnail.isNullOrEmpty()) {
                        AsyncImage(
                            model = download.thumbnail,
                            contentDescription = download.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (download.option.isAudio) Icons.Default.Audiotrack else Icons.Default.Videocam,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = download.statusText.ifEmpty { "Downloading stream..." },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentIndigoLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Progress Bar and Percent
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = AccentIndigo,
                    trackColor = DarkCard
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = download.speed.ifEmpty { "Processing stream..." },
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    Text(
                        text = "${download.progress.toInt()}%" + if (download.eta.isNotEmpty()) " (ETA: ${download.eta})" else "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }
            }

            Text(
                text = "Runs in background notification • Safe to switch apps",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
fun DownloadProgressCard(
    info: VideoInfo,
    progress: Float,
    speedText: String,
    etaText: String
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (progress / 100f).coerceIn(0f, 1f),
        label = "DownloadProgress"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AccentIndigo.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Downloading...",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "${progress.toInt()}%",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = AccentIndigo,
                trackColor = DarkCard
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = speedText.ifEmpty { "Processing stream..." },
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                if (etaText.isNotEmpty()) {
                    Text(
                        text = "ETA: $etaText",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Text(
                text = "Runs in background notification • Safe to switch apps",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

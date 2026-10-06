package com.fetchreel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fetchreel.app.ui.theme.*

@Composable
fun AppHeader(
    isUpdatingEngine: Boolean,
    onUpdateEngine: () -> Unit,
    onShowQr: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AccentIndigo, AccentCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Fetchreel Logo",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = "Fetchreel",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "100% On-Device • No Server",
                    fontSize = 11.sp,
                    color = AccentCyan
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onShowQr,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkCard)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = "Download APK QR Code",
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onUpdateEngine,
                enabled = !isUpdatingEngine,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkCard)
            ) {
                if (isUpdatingEngine) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AccentIndigoLight,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Update yt-dlp Engine",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

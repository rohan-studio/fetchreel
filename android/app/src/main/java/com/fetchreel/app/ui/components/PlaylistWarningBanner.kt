package com.fetchreel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fetchreel.app.ui.theme.*

@Composable
fun PlaylistWarningBanner(
    onSwitchToPlaylist: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.12f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WarningAmber.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Warning",
                    tint = WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Playlist Link Detected",
                    color = WarningAmber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "This single link section only downloads the first video. To download the whole playlist or pick specific videos, use the Playlist section.",
                color = TextPrimary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Button(
                onClick = onSwitchToPlaylist,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Switch to Playlist Section",
                    color = DarkBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = DarkBackground,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

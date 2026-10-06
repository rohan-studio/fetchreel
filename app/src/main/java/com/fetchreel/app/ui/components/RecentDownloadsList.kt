package com.fetchreel.app.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fetchreel.app.data.DownloadedMedia
import com.fetchreel.app.ui.theme.*

@Composable
fun RecentDownloadsList(
    items: List<DownloadedMedia>,
    onDeleteItem: (DownloadedMedia) -> Unit = {}
) {
    val context = LocalContext.current
    var itemPendingDelete by remember { mutableStateOf<DownloadedMedia?>(null) }

    if (items.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Downloaded Media (in /Download/Fetchreel)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )

        items.forEach { item ->
            RecentItemRow(
                item = item,
                context = context,
                onDelete = { itemPendingDelete = item }
            )
        }
    }

    if (itemPendingDelete != null) {
        val target = itemPendingDelete!!
        AlertDialog(
            onDismissRequest = { itemPendingDelete = null },
            title = {
                Text(
                    text = "Delete Download?",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${target.name}\"? This will permanently remove the file from your device.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = itemPendingDelete
                        itemPendingDelete = null
                        if (toDelete != null) {
                            onDeleteItem(toDelete)
                        }
                    }
                ) {
                    Text("Delete", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemPendingDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun RecentItemRow(
    item: DownloadedMedia,
    context: Context,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (item.isAudio) AccentIndigo.copy(alpha = 0.2f) else AccentCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isAudio) Icons.Default.Audiotrack else Icons.Default.Videocam,
                    contentDescription = null,
                    tint = if (item.isAudio) AccentIndigoLight else AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.formattedSize(),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            // Play / View action
            IconButton(
                onClick = {
                    openMediaFile(context, item)
                },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(DarkCard)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Share action
            IconButton(
                onClick = {
                    shareMediaFile(context, item)
                },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(DarkCard)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Delete action
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(DarkCard)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = ErrorRed,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun openMediaFile(context: Context, item: DownloadedMedia) {
    try {
        val mimeType = if (item.isAudio) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (_: Exception) { }
}

private fun shareMediaFile(context: Context, item: DownloadedMedia) {
    try {
        val mimeType = if (item.isAudio) "audio/*" else "video/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share media"))
    } catch (_: Exception) { }
}

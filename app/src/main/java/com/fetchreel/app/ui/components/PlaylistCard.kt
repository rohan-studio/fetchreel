package com.fetchreel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fetchreel.app.data.PlaylistInfo
import com.fetchreel.app.data.PlaylistItem
import com.fetchreel.app.data.QualityOption
import com.fetchreel.app.ui.theme.*

@Composable
fun PlaylistCard(
    playlist: PlaylistInfo,
    selectedItemIds: Set<String>,
    selectedOption: QualityOption,
    onToggleItem: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onOptionSelected: (QualityOption) -> Unit,
    onDownloadClicked: () -> Unit
) {
    val allSelected = selectedItemIds.size == playlist.items.size && playlist.items.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Playlist Overview Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header row: Thumbnail + Playlist info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkCard)
                    ) {
                        if (!playlist.thumbnail.isNullOrEmpty()) {
                            AsyncImage(
                                model = playlist.thumbnail,
                                contentDescription = playlist.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier
                                    .size(36.dp)
                                    .align(Alignment.Center)
                            )
                        }

                        // Badge overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(Color.Black.copy(alpha = 0.8f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${playlist.items.size} vids",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = playlist.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!playlist.author.isNullOrEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = playlist.author,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Text(
                            text = "${playlist.items.size} videos available",
                            fontSize = 11.sp,
                            color = AccentIndigoLight
                        )
                    }
                }
            }
        }

        // Format & Quality Selection
        Text(
            text = "Select Download Quality",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(PlaylistInfo.defaultQualityOptions) { option ->
                val isSelected = option.label == selectedOption.label
                val bgColor = if (isSelected) AccentIndigo else DarkSurface
                val borderColor = if (isSelected) AccentIndigoLight else DarkCardBorder
                val textColor = if (isSelected) TextPrimary else TextSecondary

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable { onOptionSelected(option) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (option.isAudio) Icons.Default.Headphones else Icons.Default.Videocam,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = option.label,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Selection Controller Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selectedItemIds.size} of ${playlist.items.size} selected",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selectedItemIds.isNotEmpty()) AccentCyan else TextMuted
            )

            TextButton(
                onClick = { if (allSelected) onDeselectAll() else onSelectAll() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (allSelected) "Deselect All" else "Select All",
                    fontSize = 12.sp,
                    color = AccentIndigoLight,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // List of Playlist Items
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                playlist.items.forEachIndexed { idx, item ->
                    val isChecked = selectedItemIds.contains(item.id)
                    PlaylistItemRow(
                        item = item,
                        index = idx + 1,
                        isSelected = isChecked,
                        onToggle = { onToggleItem(item.id) }
                    )
                }
            }
        }

        // Download Action Button
        Button(
            onClick = onDownloadClicked,
            enabled = selectedItemIds.isNotEmpty(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SuccessGreen,
                disabledContainerColor = DarkCard
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = if (selectedItemIds.isNotEmpty()) TextPrimary else TextMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedItemIds.isNotEmpty()) {
                    "Download ${selectedItemIds.size} Videos (${selectedOption.label})"
                } else {
                    "Select at least 1 video"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (selectedItemIds.isNotEmpty()) TextPrimary else TextMuted
            )
        }
    }
}

@Composable
private fun PlaylistItemRow(
    item: PlaylistItem,
    index: Int,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) DarkCard.copy(alpha = 0.5f) else Color.Transparent)
            .clickable { onToggle() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = AccentIndigo,
                uncheckedColor = TextMuted,
                checkmarkColor = TextPrimary
            ),
            modifier = Modifier.size(20.dp)
        )

        // Number index
        Text(
            text = "$index",
            fontSize = 11.sp,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(22.dp)
        )

        // Small thumbnail
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DarkCard)
        ) {
            if (!item.thumbnail.isNullOrEmpty()) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (item.formattedDuration().isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = item.formattedDuration(),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Title and duration
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) TextPrimary else TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.uploader.isNullOrEmpty()) {
                Text(
                    text = item.uploader,
                    fontSize = 10.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

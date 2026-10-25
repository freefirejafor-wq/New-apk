package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.SportsChannel
import com.example.ui.theme.*

@Composable
fun SportsChannelsScreen(
    channels: List<SportsChannel>,
    selectedCategory: String = "All",
    onCategorySelect: (String) -> Unit = {},
    onChannelClick: (SportsChannel) -> Unit,
    onFavoriteToggle: (String) -> Unit,
    searchQuery: String = ""
) {
    val distinctCategories = remember(channels) {
        listOf("All") + channels.map { it.category }.filter { it.isNotBlank() }.distinct()
    }

    val filteredChannels = remember(channels, searchQuery, selectedCategory) {
        channels.filter { ch ->
            val matchesCat = selectedCategory.equals("All", ignoreCase = true) ||
                ch.category.equals(selectedCategory, ignoreCase = true) ||
                (selectedCategory.equals("SPORTS", ignoreCase = true) && ch.category.contains("Sport", ignoreCase = true))
            val matchesQuery = if (searchQuery.isBlank()) true else {
                ch.name.contains(searchQuery, ignoreCase = true) ||
                    ch.category.contains(searchQuery, ignoreCase = true) ||
                    ch.logoText.contains(searchQuery, ignoreCase = true)
            }
            matchesCat && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
            .padding(horizontal = 12.dp)
    ) {
        if (distinctCategories.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                distinctCategories.forEach { cat ->
                    val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategorySelect(cat) },
                        label = {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                            selectedLabelColor = CyanAccent,
                            containerColor = NavySurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) CyanAccent else NavyBorder
                        )
                    )
                }
            }
        }

        if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No channels found in \"$selectedCategory\"",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onCategorySelect("All") },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                    ) {
                        Text("Show All Channels", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filteredChannels, key = { it.id }) { channel ->
                    SportsChannelGridItem(
                        channel = channel,
                        onClick = { onChannelClick(channel) },
                        onFavoriteToggle = { onFavoriteToggle(channel.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun SportsChannelGridItem(
    channel: SportsChannel,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(1.dp, NavyBorder, RoundedCornerShape(12.dp))
            .testTag("channel_item_${channel.name.lowercase().replace(" ", "_")}"),
        colors = CardDefaults.cardColors(containerColor = NavySurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circular channel logo badge
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.5.dp, NavyBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val logoUrl = channel.logoUrl.trim()
                if (logoUrl.startsWith("http://", ignoreCase = true) || logoUrl.startsWith("https://", ignoreCase = true)) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .clip(CircleShape)
                    )
                } else {
                    val text = if (channel.logoText.isNotBlank()) channel.logoText else channel.name.take(4)
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Channel Name
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Streams count badge & Favorite star
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${channel.streams.size} servers",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = CyanAccent,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (channel.isFavorite) GoldYellow else TextTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

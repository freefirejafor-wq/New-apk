package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveMatch
import com.example.data.model.SportsChannel
import com.example.ui.components.MatchCard
import com.example.ui.theme.*

@Composable
fun FavouritesScreen(
    favoriteMatches: List<LiveMatch>,
    favoriteChannels: List<SportsChannel>,
    onMatchClick: (LiveMatch) -> Unit,
    onChannelClick: (SportsChannel) -> Unit,
    onMatchFavoriteToggle: (String) -> Unit,
    onChannelFavoriteToggle: (String) -> Unit,
    onStreamSelectClick: (LiveMatch) -> Unit
) {
    val isEmpty = favoriteMatches.isEmpty() && favoriteChannels.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
            .padding(horizontal = 14.dp)
    ) {
        if (isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Favourites Found",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 18.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap the star icon on any live match or sports channel to pin it here for quick access.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (favoriteMatches.isNotEmpty()) {
                    item {
                        Text(
                            text = "Pinned Matches (${favoriteMatches.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(favoriteMatches, key = { "match_${it.id}" }) { match ->
                        MatchCard(
                            match = match,
                            onClick = { onMatchClick(match) },
                            onFavoriteToggle = { onMatchFavoriteToggle(match.id) },
                            onStreamSelectClick = { onStreamSelectClick(match) }
                        )
                    }
                }

                if (favoriteChannels.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Pinned Channels (${favoriteChannels.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(favoriteChannels, key = { "ch_${it.id}" }) { channel ->
                        SportsChannelGridItem(
                            channel = channel,
                            onClick = { onChannelClick(channel) },
                            onFavoriteToggle = { onChannelFavoriteToggle(channel.id) }
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
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
    onStreamSelectClick: (LiveMatch) -> Unit,
    searchQuery: String = ""
) {
    val filteredMatches = remember(favoriteMatches, searchQuery) {
        if (searchQuery.isBlank()) favoriteMatches
        else favoriteMatches.filter {
            it.team1Name.contains(searchQuery, ignoreCase = true) ||
                it.team2Name.contains(searchQuery, ignoreCase = true) ||
                it.tournament.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredChannels = remember(favoriteChannels, searchQuery) {
        if (searchQuery.isBlank()) favoriteChannels
        else favoriteChannels.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    val liveFavMatches = remember(filteredMatches) {
        filteredMatches.filter { it.status == MatchStatus.LIVE }
    }
    val upcomingFavMatches = remember(filteredMatches) {
        filteredMatches.filter { it.status == MatchStatus.UPCOMING }
    }
    val finishedFavMatches = remember(filteredMatches) {
        filteredMatches.filter { it.status == MatchStatus.FINISHED }
    }

    val isEmpty = filteredMatches.isEmpty() && filteredChannels.isEmpty()

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
                if (liveFavMatches.isNotEmpty()) {
                    item(key = "fav_live_header") {
                        Text(
                            text = "LIVE PINNED MATCHES (${liveFavMatches.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = LiveRed,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(liveFavMatches, key = { "fav_live_${it.id}" }) { match ->
                        MatchCard(
                            match = match,
                            onClick = { onMatchClick(match) },
                            onFavoriteToggle = { onMatchFavoriteToggle(match.id) },
                            onStreamSelectClick = { onStreamSelectClick(match) }
                        )
                    }
                }

                if (upcomingFavMatches.isNotEmpty()) {
                    item(key = "fav_upcoming_header") {
                        Text(
                            text = "UPCOMING PINNED MATCHES (${upcomingFavMatches.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = SkyBlue,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(upcomingFavMatches, key = { "fav_up_${it.id}" }) { match ->
                        MatchCard(
                            match = match,
                            onClick = { onMatchClick(match) },
                            onFavoriteToggle = { onMatchFavoriteToggle(match.id) },
                            onStreamSelectClick = { onStreamSelectClick(match) }
                        )
                    }
                }

                if (finishedFavMatches.isNotEmpty()) {
                    item(key = "fav_fin_header") {
                        Text(
                            text = "FINISHED PINNED MATCHES (${finishedFavMatches.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(finishedFavMatches, key = { "fav_fin_${it.id}" }) { match ->
                        MatchCard(
                            match = match,
                            onClick = { onMatchClick(match) },
                            onFavoriteToggle = { onMatchFavoriteToggle(match.id) },
                            onStreamSelectClick = { onStreamSelectClick(match) }
                        )
                    }
                }

                if (filteredChannels.isNotEmpty()) {
                    item(key = "fav_channels_header") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "PINNED CHANNELS (${filteredChannels.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = CyanAccent,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(filteredChannels, key = { "ch_${it.id}" }) { channel ->
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

package com.demonlab.lune.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.demonlab.lune.tools.PlaybackManager
import java.util.Calendar
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.demonlab.lune.R
import com.demonlab.lune.data.Playlist
import com.demonlab.lune.tools.Song

@Composable
fun ResumeScreen(
    viewModel: com.demonlab.lune.ui.viewmodels.MusicViewModel,
    allSongs: List<Song>,
    allPlaylists: List<Playlist>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onSongClick: (Song, List<Song>) -> Unit,
    onPlaylistClick: (Playlist) -> Unit
) {
    val scrollState = rememberScrollState()

    // 1. Recommendations (Shuffle top 5 artists, limit 10)
    val recommendations = remember(allSongs) {
        val topArtists = allSongs.groupingBy { it.artist }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }

        allSongs.filter { topArtists.contains(it.artist) }
            .distinctBy { it.id }
            .shuffled()
            .take(10)
    }

    // 2. Playlists
    val topPlaylists = remember(allPlaylists) {
        // Since we don't have play counts for playlists, we just show the first 10
        allPlaylists.take(10)
    }

    // 3. Recently Added (Sort by dateAdded descending)
    val recentlyAdded = remember(allSongs) {
        allSongs.sortedByDescending { it.dateAdded }.take(10)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        
        val actualCount = 3
        val infiniteCount = actualCount * 1000
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = actualCount * 500,
            pageCount = { infiniteCount }
        )
        
        // Stats Calculation
        // String fallbacks
        val sUnknownArtist = stringResource(R.string.unknown_artist)
        val sUnknownSong = stringResource(R.string.unknown_song)

        // Stats Calculation (Real Habits vs Library Counts)
        val fallbackTopArtistName = remember(allSongs) {
            allSongs.filter { it.artist.isNotBlank() && it.artist != "<unknown>" }
                .groupingBy { it.artist }
                .eachCount()
                .maxByOrNull { it.value }?.key
        }

        val fallbackTopPlaylist = remember(allPlaylists, viewModel.playlistMappings) {
            allPlaylists.maxByOrNull { playlist ->
                viewModel.getSongsForPlaylistSync(playlist.id).size
            }
        }

        // Top 3 Songs (New)
        val top3Songs = remember(viewModel.topSongStats, allSongs) {
            viewModel.topSongStats.mapNotNull { stat ->
                val idStr = stat.id.replace("SONG_", "")
                val id = idStr.toLongOrNull()
                allSongs.find { it.id == id }
            }.take(3)
        }

        val topSong = top3Songs.firstOrNull()

        val topArtistStat = viewModel.topArtistStats.firstOrNull()
        val realTopArtistName = remember(topArtistStat, fallbackTopArtistName) {
            topArtistStat?.id?.replace("ARTIST_", "") ?: (fallbackTopArtistName ?: sUnknownArtist)
        }

        val topPlaylistStat = viewModel.topPlaylistStats.firstOrNull()
        val realTopPlaylist = remember(topPlaylistStat, allPlaylists, fallbackTopPlaylist) {
            if (topPlaylistStat != null) {
                val idStr = topPlaylistStat.id.replace("PLAYLIST_", "")
                val id = idStr.toLongOrNull()
                allPlaylists.find { it.id == id } ?: fallbackTopPlaylist
            } else fallbackTopPlaylist
        }
    
        val topArtistSongs = remember(realTopArtistName, allSongs) {
            allSongs.filter { it.artist == realTopArtistName }.take(3)
        }


        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp) // Padding added
                .height(260.dp) // Increased height to prevent clipping
        ) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 2,
                userScrollEnabled = true
            ) { page ->
                val actualPage = page % actualCount
                
                // Calculate offset relative to the current page
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                val absOffset = kotlin.math.abs(pageOffset)
                
                // Solid cards as requested
                val scale = 1f - (absOffset * 0.05f).coerceIn(0f, 0.15f)
                val alpha = 1f // Solid
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f - absOffset) // Current card on top
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            alpha = alpha
                        )
                ) {
                    when (actualPage) {
                        0 -> WelcomeCard(
                            playbackManager = PlaybackManager.getInstance(androidx.compose.ui.platform.LocalContext.current),
                            totalSongs = allSongs.size,
                            mostPlayedSong = topSong?.title ?: sUnknownSong,
                            playlistsCount = allPlaylists.size
                        )
                        1 -> StatsPlaylistCard(
                            viewModel = viewModel,
                            playlist = realTopPlaylist
                        )
                        2 -> StatsSongsCard(
                            songs = top3Songs
                        )
                    }
                }
            }

            // Pager Indicator (Right Side)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(actualCount) { index ->
                    val isSelected = pagerState.currentPage % actualCount == index
                    val height by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isSelected) 18.dp else 6.dp,
                        label = "dotHeight"
                    )
                    val color = if (isSelected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(height)
                            .clip(RoundedCornerShape(100))
                            .background(color)
                    )
                }
            }
        }

        // Recommendations Section
        if (recommendations.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.resume_recommendations))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(recommendations, key = { it.id }) { song ->
                    HorizontalSongCard(
                        song = song,
                        onClick = { onSongClick(song, recommendations) }
                    )
                }
            }
        }

        // Top Playlists Section
        if (topPlaylists.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.resume_top_playlists))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(topPlaylists, key = { it.id }) { playlist ->
                    HorizontalPlaylistCard(
                        viewModel = viewModel,
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) }
                    )
                }
            }
        }

        // Recently Added Section
        if (recentlyAdded.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.resume_recently_added))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(recentlyAdded, key = { it.id }) { song ->
                    HorizontalSongCard(
                        song = song,
                        onClick = { onSongClick(song, recentlyAdded) }
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun HorizontalSongCard(song: Song, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                AsyncImage(
                    model = song.coverUrl ?: song.albumArtUri,
                    contentDescription = "Cover",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = R.drawable.ic_launcher_background)
                )
                // Small play overlay indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist.ifBlank { stringResource(R.string.unknown_artist) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HorizontalPlaylistCard(
    viewModel: com.demonlab.lune.ui.viewmodels.MusicViewModel,
    playlist: Playlist, 
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                com.demonlab.lune.ui.activities.PlaylistPreviewCovers(
                    playlistId = playlist.id,
                    viewModel = viewModel,
                    size = 64.dp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = playlist.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun WelcomeCard(
    playbackManager: PlaybackManager,
    totalSongs: Int,
    mostPlayedSong: String,
    playlistsCount: Int
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..5 -> context.getString(R.string.welcome_early_morning)
            in 6..11 -> context.getString(R.string.welcome_morning)
            in 12..13 -> context.getString(R.string.welcome_noon)
            in 14..18 -> context.getString(R.string.welcome_afternoon)
            in 19..22 -> context.getString(R.string.welcome_evening)
            else -> context.getString(R.string.welcome_night)
        }
    }

    val dailyTimeMs = playbackManager.dailyListeningTime
    val hours = (dailyTimeMs / (1000 * 60 * 60)).toInt()
    val minutes = ((dailyTimeMs / (1000 * 60)) % 60).toInt()
    val seconds = ((dailyTimeMs / 1000) % 60).toInt()

    val timeString = when {
        hours > 0 -> context.getString(R.string.stats_hours_unit, hours)
        minutes > 0 -> context.getString(R.string.stats_minutes_unit, minutes)
        else -> context.getString(R.string.stats_seconds_unit, seconds)
    }

    val (timeIcon, iconColor, gradientColors) = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..5 -> Triple(
                Icons.Default.NightsStay, 
                Color(0xFF9575CD),
                listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
            )
            in 6..11 -> Triple(
                Icons.Default.WbSunny, 
                Color(0xFFFFB300),
                listOf(Color(0xFF29B6F6), Color(0xFF81D4FA))
            )
            in 12..13 -> Triple(
                Icons.Default.LightMode, 
                Color(0xFFFFD600),
                listOf(Color(0xFF4FC3F7), Color(0xFFE1F5FE))
            )
            in 14..18 -> Triple(
                Icons.Default.WbSunny, 
                Color(0xFFFB8C00),
                listOf(Color(0xFFFBC02D), Color(0xFFF57C00))
            )
            in 19..22 -> Triple(
                Icons.Default.WbTwilight, 
                Color(0xFFFF8A65),
                listOf(Color(0xFF3949AB), Color(0xFF1A237E))
            )
            else -> Triple(
                Icons.Default.NightsStay, 
                Color(0xFF5C6BC0),
                listOf(Color(0xFF121212), Color(0xFF283593))
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = gradientColors
                    )
                )
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = timeIcon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = iconColor
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Stats Column
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    StatItem(
                        icon = painterResource(id = R.drawable.ic_logo_diamonds),
                        label = stringResource(R.string.stats_listening_today),
                        value = timeString,
                        color = Color.White,
                        contentColor = Color.White
                    )
                    StatItem(
                        icon = Icons.Default.PlayArrow,
                        label = stringResource(R.string.total_songs),
                        value = totalSongs.toString(),
                        color = Color.White,
                        contentColor = Color.White
                    )
                    StatItem(
                        icon = Icons.Default.MusicNote,
                        label = stringResource(R.string.most_played_desc),
                        value = mostPlayedSong,
                        color = Color.White,
                        contentColor = Color.White
                    )
                    StatItem(
                        icon = Icons.Default.Folder,
                        label = stringResource(R.string.playlists),
                        value = playlistsCount.toString(),
                        color = Color.White,
                        contentColor = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun StatItem(
    icon: Any,
    label: String,
    value: String,
    color: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            when (icon) {
                is androidx.compose.ui.graphics.vector.ImageVector -> Icon(icon, null, modifier = Modifier.size(16.dp), color)
                is androidx.compose.ui.graphics.painter.Painter -> Icon(icon, null, modifier = Modifier.size(16.dp), color)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = contentColor)
        }
    }
}

@Composable
fun StatsPlaylistCard(
    viewModel: com.demonlab.lune.ui.viewmodels.MusicViewModel,
    playlist: Playlist?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (playlist == null) return

    val playlistSongs = viewModel.getSongsForPlaylistSync(playlist.id)
    val totalDuration = playlistSongs.sumOf { it.duration }
    val hours = (totalDuration / (1000 * 60 * 60)).toInt()
    val minutes = ((totalDuration / (1000 * 60)) % 60).toInt()
    
    val timeString = if (hours > 0) {
        context.getString(R.string.stats_hours_unit, hours) + " " + context.getString(R.string.stats_minutes_unit, minutes)
    } else {
        context.getString(R.string.stats_minutes_unit, minutes)
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Text(
                        text = stringResource(R.string.stats_top_playlist),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_logo_diamonds), null, modifier = Modifier.size(14.dp), MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("$timeString ${stringResource(R.string.stats_music_unit)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp), MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${playlistSongs.size} ${if (playlistSongs.size == 1) stringResource(R.string.song_singular) else stringResource(R.string.song_plural)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.stats_playlist_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
                Box(modifier = Modifier.size(90.dp).clip(RoundedCornerShape(16.dp))) {
                    com.demonlab.lune.ui.activities.PlaylistPreviewCovers(
                        playlistId = playlist.id,
                        viewModel = viewModel,
                        size = 90.dp
                    )
                }
            }
        }
    }
}

@Composable
fun StatsSongsCard(
    songs: List<Song>
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.stats_top_songs),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = stringResource(R.string.stats_songs_message),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }

                if (songs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.stats_loading_hits),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        songs.forEachIndexed { index, song ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.3f),
                                    modifier = Modifier.width(28.dp)
                                )
                                AsyncImage(
                                    model = song.coverUrl ?: song.albumArtUri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(R.drawable.ic_logo_diamonds)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

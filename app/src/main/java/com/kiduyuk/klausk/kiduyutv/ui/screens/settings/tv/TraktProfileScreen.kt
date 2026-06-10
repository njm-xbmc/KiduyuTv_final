package com.kiduyuk.klausk.kiduyutv.ui.screens.settings.tv

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiduyuk.klausk.kiduyutv.data.api.ApiClient
import com.kiduyuk.klausk.kiduyutv.data.model.Movie
import com.kiduyuk.klausk.kiduyutv.data.model.TvShow
import com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktUser
import com.kiduyuk.klausk.kiduyutv.data.remote.TraktApiClient
import com.kiduyuk.klausk.kiduyutv.data.repository.TraktRepository
import com.kiduyuk.klausk.kiduyutv.ui.components.MovieCard
import com.kiduyuk.klausk.kiduyutv.ui.components.TvShowCard
import com.kiduyuk.klausk.kiduyutv.ui.theme.*
import com.kiduyuk.klausk.kiduyutv.util.TraktAuthManager
import com.kiduyuk.klausk.kiduyutv.viewmodel.MyListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Trakt Profile Screen - displays user profile information from Trakt.tv
 * Now with tabs for Collection, Watchlist and Recommendations.
 */
@Composable
fun TraktProfileScreen(
    onBackClick: () -> Unit,
    onMovieClick: (Int) -> Unit = {},
    onTvShowClick: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Profile", "Collection", "Watchlist", "Recommended")

    var isLoading by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf<TraktUser?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val traktRepository = remember {
        TraktRepository(TraktApiClient.apiService, TraktAuthManager)
    }

    // Fetch profile on load
    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        traktRepository.getUserSettings().collect { result ->
            result.fold(
                onSuccess = { settings ->
                    profile = settings.user
                    isLoading = false
                },
                onFailure = { e ->
                    error = e.message ?: "Failed to load profile"
                    isLoading = false
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(color = CardDark, shape = RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .background(
                        color = PrimaryRed.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Trakt.tv",
                    color = PrimaryRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Content Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .padding(32.dp)
        ) {
            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = PrimaryRed,
                edgePadding = 0.dp,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = PrimaryRed
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 16.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == index) PrimaryRed else TextSecondary
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> {
                        when {
                            isLoading -> LoadingIndicator("Loading profile...")
                            error != null -> ErrorMessage(error!!, onBackClick)
                            profile != null -> TraktProfileContent(profile = profile!!)
                        }
                    }
                    1 -> TraktMediaTabContent(
                        traktRepository = traktRepository,
                        tabType = "collection",
                        onMovieClick = onMovieClick,
                        onTvShowClick = onTvShowClick
                    )
                    2 -> TraktMediaTabContent(
                        traktRepository = traktRepository,
                        tabType = "watchlist",
                        onMovieClick = onMovieClick,
                        onTvShowClick = onTvShowClick
                    )
                    3 -> TraktMediaTabContent(
                        traktRepository = traktRepository,
                        tabType = "recommendations",
                        onMovieClick = onMovieClick,
                        onTvShowClick = onTvShowClick
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = PrimaryRed,
                strokeWidth = 3.dp
            )
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun ErrorMessage(message: String, onBackClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = message,
                color = Color(0xFFFF6B6B),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onBackClick) {
                Text("Go Back")
            }
        }
    }
}

@Composable
private fun TraktMediaTabContent(
    traktRepository: TraktRepository,
    tabType: String,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit
) {
    val tmdbApiService = remember { ApiClient.tmdbApiService }
    val posterCache = remember { mutableStateMapOf<String, String?>() }
    var mediaItems by remember { mutableStateOf<List<MyListItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tabType) {
        isLoading = true
        error = null
        try {
            val results = mutableListOf<MyListItem>()
            val processedIds = mutableSetOf<String>()

            // Fetch both movies and shows
            val types = listOf("movies", "shows")
            
            for (type in types) {
                val flow = when (tabType) {
                    "collection" -> traktRepository.getCollection(type)
                    "watchlist" -> traktRepository.getWatchlist(type)
                    else -> traktRepository.getRecommendations(type)
                }

                flow.collect { result ->
                    result.onSuccess { items ->
                        items.forEach { item ->
                            val movie = (item as? com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktCollectionItem)?.movie
                                ?: (item as? com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktWatchlistItem)?.movie
                                ?: (item as? com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktRecommendation)?.movie
                            
                            val show = (item as? com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktCollectionItem)?.show
                                ?: (item as? com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktWatchlistItem)?.show
                                ?: (item as? com.kiduyuk.klausk.kiduyutv.data.model.trakt.TraktRecommendation)?.show

                            if (movie != null) {
                                movie.ids.tmdb?.let { tmdbId ->
                                    val key = "movie-$tmdbId"
                                    if (processedIds.add(key)) {
                                        results.add(MyListItem(
                                            id = tmdbId,
                                            title = movie.title,
                                            posterPath = null,
                                            type = "movie",
                                            voteAverage = movie.rating ?: 0.0
                                        ))
                                    }
                                }
                            } else if (show != null) {
                                show.ids.tmdb?.let { tmdbId ->
                                    val key = "tv-$tmdbId"
                                    if (processedIds.add(key)) {
                                        results.add(MyListItem(
                                            id = tmdbId,
                                            title = show.title,
                                            posterPath = null,
                                            type = "tv",
                                            voteAverage = show.rating ?: 0.0
                                        ))
                                    }
                                }
                            }
                        }
                    }.onFailure { e ->
                        Log.e("TraktProfileScreen", "Failed to fetch $tabType $type: ${e.message}")
                    }
                }
            }

            mediaItems = results.toList()
            
            // Background loading for posters
            withContext(Dispatchers.IO) {
                mediaItems.forEachIndexed { index, item ->
                    val key = "${item.type}-${item.id}"
                    val path = posterCache[key] ?: try {
                        if (item.type == "movie") {
                            tmdbApiService.getMovieDetail(item.id).posterPath
                        } else {
                            tmdbApiService.getTvShowDetail(item.id).posterPath
                        }
                    } catch (e: Exception) { null }
                    
                    if (path != null) {
                        posterCache[key] = path
                        withContext(Dispatchers.Main) {
                            mediaItems = mediaItems.toMutableList().apply {
                                this[index] = this[index].copy(posterPath = path)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    if (isLoading && mediaItems.isEmpty()) {
        LoadingIndicator("Loading $tabType...")
    } else if (error != null && mediaItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Error: $error", color = Color.Red)
        }
    } else if (mediaItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "No items found in your $tabType.", color = TextSecondary)
        }
    } else {
        TraktMediaGrid(
            items = mediaItems,
            onMovieClick = onMovieClick,
            onTvShowClick = onTvShowClick
        )
    }
}

@Composable
private fun TraktMediaGrid(
    items: List<MyListItem>,
    onMovieClick: (Int) -> Unit,
    onTvShowClick: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val spacing = 16.dp
    val columns = 6
    val calculatedCardWidth = (screenWidth - 100.dp - (spacing * (columns - 1))) / columns
    val calculatedCardHeight = calculatedCardWidth * 1.5f

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        items(items) { item ->
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            if (item.type == "movie") {
                MovieCard(
                    movie = Movie(
                        id = item.id,
                        title = item.title,
                        overview = "",
                        posterPath = item.posterPath,
                        backdropPath = null,
                        voteAverage = item.voteAverage,
                        releaseDate = null,
                        genreIds = null,
                        popularity = 0.0
                    ),
                    isSelected = isFocused,
                    onClick = { onMovieClick(item.id) },
                    modifier = Modifier
                        .width(calculatedCardWidth)
                        .height(calculatedCardHeight)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onMovieClick(item.id) }
                )
            } else {
                TvShowCard(
                    tvShow = TvShow(
                        id = item.id,
                        name = item.title,
                        overview = "",
                        posterPath = item.posterPath,
                        backdropPath = null,
                        voteAverage = item.voteAverage,
                        firstAirDate = null,
                        genreIds = null,
                        popularity = 0.0
                    ),
                    isSelected = isFocused,
                    onClick = { onTvShowClick(item.id) },
                    modifier = Modifier
                        .width(calculatedCardWidth)
                        .height(calculatedCardHeight)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onTvShowClick(item.id) }
                )
            }
        }
    }
}

@Composable
private fun TraktProfileContent(
    profile: TraktUser
) {
    val username = profile.username
    val avatarUrl = remember(username) {
        "https://avatar-redcircle.trakt.tv/$username.png"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Avatar
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(CardDark)
                .border(3.dp, PrimaryRed, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Profile Avatar",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Username
        Text(
            text = profile.username,
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        // Display Name
        if (!profile.name.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = profile.name,
                color = TextSecondary,
                fontSize = 18.sp
            )
        }

        // VIP Badge
        if (profile.vip) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFFFD700).copy(alpha = 0.15f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "VIP Member",
                    color = Color(0xFFFFD700),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Info Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // About
            if (!profile.about.isNullOrBlank()) {
                ProfileInfoCard(
                    title = "About",
                    content = profile.about
                )
            }

            // Location
            if (!profile.location.isNullOrBlank()) {
                ProfileInfoRow(
                    icon = null,
                    label = "Location",
                    value = profile.location
                )
            }

            // Joined Date
            if (!profile.joinedAt.isNullOrBlank()) {
                ProfileInfoRow(
                    icon = Icons.Default.CheckCircle,
                    label = "Member Since",
                    value = formatTraktDate(profile.joinedAt)
                )
            }

            // Gender
            if (!profile.gender.isNullOrBlank()) {
                ProfileInfoRow(
                    icon = null,
                    label = "Gender",
                    value = profile.gender.replaceFirstChar { it.uppercase() }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ProfileInfoCard(
    title: String,
    content: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .padding(20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = content,
                color = TextPrimary,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTraktDate(dateString: String): String {
    return try {
        // Trakt date format: 2010-09-01T12:34:56.000Z
        val parts = dateString.split("T")
        if (parts.isNotEmpty()) {
            parts[0] // Return just the date part
        } else {
            dateString
        }
    } catch (e: Exception) {
        dateString
    }
}

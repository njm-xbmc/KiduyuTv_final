package com.kiduyuk.klausk.kiduyutv.ui.screens.home.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.ui.components.LottieLoadingView
import com.kiduyuk.klausk.kiduyutv.ui.components.TopBar
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.CardDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.DarkRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.PrimaryRed
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextPrimary
import com.kiduyuk.klausk.kiduyutv.ui.theme.TextSecondary
import com.kiduyuk.klausk.kiduyutv.viewmodel.CategoryItem
import com.kiduyuk.klausk.kiduyutv.viewmodel.LiveTvViewModel

/**
 * Composable function for the Live TV screen.
 * Displays categories and channels based on the IPTV playlist.
 *
 * @param onChannelPlay Callback when a channel is selected for playback
 * @param onNavigate Lambda to handle navigation between top-level screens
 * @param onSearchClick Lambda to navigate to the search screen
 * @param onSettingsClick Lambda to navigate to the settings screen
 * @param viewModel The [LiveTvViewModel] instance
 */
@Composable
fun LiveTvScreen(
    onChannelPlay: (IptvChannel) -> Unit,
    onNavigate: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNotificationClick: (id: Int, type: String) -> Unit = { _, _ -> },
    viewModel: LiveTvViewModel = remember { LiveTvViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Initialize ViewModel with context for caching
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        viewModel.loadPlaylist()
    }
    
    // Handle channel selection for playback
    LaunchedEffect(uiState.selectedChannel) {
        uiState.selectedChannel?.let { channel ->
            onChannelPlay(channel)
            viewModel.clearSelectedChannel()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Top Bar
            TopBar(
                selectedRoute = "live_tv",
                onNavItemClick = onNavigate,
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onNotificationClick = onNotificationClick
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when {
                // Loading state
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LottieLoadingView(size = 300.dp)
                    }
                }
                
                // Error state
                uiState.error != null -> {
                    ErrorContent(
                        errorMessage = uiState.error!!,
                        onRetry = { viewModel.loadPlaylist(forceRefresh = true) }
                    )
                }
                
                // Categories view
                uiState.selectedCategory == null -> {
                    CategoriesContent(
                        categories = uiState.categories,
                        onCategoryClick = { category ->
                            viewModel.selectCategory(category.name)
                        }
                    )
                }
                
                // Channels view
                else -> {
                    ChannelsContent(
                        categoryName = uiState.selectedCategory!!,
                        channels = uiState.channels,
                        onChannelClick = { channel ->
                            viewModel.selectChannel(channel)
                        },
                        onBackClick = { viewModel.clearCategorySelection() }
                    )
                }
            }
        }
    }
}

/**
 * Content displaying all available categories.
 */
@Composable
private fun CategoriesContent(
    categories: List<CategoryItem>,
    onCategoryClick: (CategoryItem) -> Unit
) {
    // Focus requester for D-pad navigation
    val firstFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    
    // Request focus on first item when categories load
    LaunchedEffect(categories) {
        if (categories.isNotEmpty()) {
            firstFocusRequester.requestFocus()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Live TV Categories",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No categories available",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                itemsIndexed(categories) { index, category ->
                    val modifier = if (index == 0) {
                        Modifier.focusRequester(firstFocusRequester)
                    } else {
                        Modifier
                    }
                    CategoryCard(
                        category = category,
                        modifier = modifier,
                        onClick = { onCategoryClick(category) }
                    )
                }
            }
        }
    }
}

/**
 * Card component for displaying a category.
 */
@Composable
private fun CategoryCard(
    category: CategoryItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) DarkRed else CardDark)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = category.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${category.channelCount} channels",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Content displaying channels within a selected category.
 */
@Composable
private fun ChannelsContent(
    categoryName: String,
    channels: List<IptvChannel>,
    onChannelClick: (IptvChannel) -> Unit,
    onBackClick: () -> Unit
) {
    // Focus requesters for D-pad navigation
    val backFocusRequester = remember { FocusRequester() }
    val firstChannelFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    
    // Request focus on back button when screen opens
    LaunchedEffect(categoryName) {
        backFocusRequester.requestFocus()
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Back button and category title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            val backInteractionSource = remember { MutableInteractionSource() }
            val isBackFocused by backInteractionSource.collectIsFocusedAsState()
            
            Box(
                modifier = Modifier
                    .focusRequester(backFocusRequester)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBackFocused) PrimaryRed else DarkRed)
                    .clickable(
                        interactionSource = backInteractionSource,
                        indication = null,
                        onClick = onBackClick
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "\u2190 Back",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = categoryName,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        if (channels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No channels in this category",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            // Request focus on first channel when grid is ready
            LaunchedEffect(channels) {
                if (channels.isNotEmpty()) {
                    firstChannelFocusRequester.requestFocus()
                }
            }
            
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                itemsIndexed(channels) { index, channel ->
                    val modifier = if (index == 0) {
                        Modifier.focusRequester(firstChannelFocusRequester)
                    } else {
                        Modifier
                    }
                    ChannelCard(
                        channel = channel,
                        modifier = modifier,
                        onClick = { onChannelClick(channel) }
                    )
                }
            }
        }
    }
}

/**
 * Card component for displaying a channel.
 */
@Composable
private fun ChannelCard(
    channel: IptvChannel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) DarkRed else CardDark)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Channel logo
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(channel.logo)
                        .crossfade(true)
                        .build(),
                    contentDescription = channel.name,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Error content with retry button.
 */
@Composable
private fun ErrorContent(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Error loading playlist",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = TextSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            val interactionSource = remember { MutableInteractionSource() }
            val isRetryFocused by interactionSource.collectIsFocusedAsState()
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isRetryFocused) PrimaryRed else DarkRed)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onRetry
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Retry",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
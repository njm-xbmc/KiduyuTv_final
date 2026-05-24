package com.kiduyuk.klausk.kiduyutv.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.IptvChannel
import com.kiduyuk.klausk.kiduyutv.data.model.IptvPlaylist
import com.kiduyuk.klausk.kiduyutv.data.repository.IptvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for the Live TV screen.
 *
 * @param isLoading Loading state for initial playlist fetch
 * @param categories List of available categories
 * @param selectedCategory Currently selected category
 * @param channels Channels in the selected category
 * @param selectedChannel Currently selected channel for playback
 * @param error Error message if playlist fetch failed
 */
data class LiveTvUiState(
    val isLoading: Boolean = true,
    val categories: List<CategoryItem> = emptyList(),
    val selectedCategory: String? = null,
    val channels: List<IptvChannel> = emptyList(),
    val selectedChannel: IptvChannel? = null,
    val error: String? = null
)

/**
 * Represents a category item for display in the UI.
 *
 * @param name Category name
 * @param channelCount Number of channels in this category
 */
data class CategoryItem(
    val name: String,
    val channelCount: Int
)

/**
 * ViewModel for the Live TV screen.
 * Manages playlist fetching, category selection, and channel browsing.
 */
class LiveTvViewModel : ViewModel() {
    
    private val repository = IptvRepository.getInstance()
    
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()
    
    private var cachedPlaylist: IptvPlaylist? = null
    private var appContext: Context? = null
    
    /**
     * Initializes the ViewModel with application context for caching.
     * Call this in the Composable with rememberUpdatedState or via LaunchedEffect.
     *
     * @param context Application context
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
    
    /**
     * Loads the IPTV playlist from the remote server or cache.
     *
     * @param forceRefresh If true, bypasses cache and fetches from network
     */
    fun loadPlaylist(forceRefresh: Boolean = false) {
        val context = appContext ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            repository.fetchPlaylist(context, forceRefresh).fold(
                onSuccess = { playlist ->
                    cachedPlaylist = playlist
                    val categoryItems = playlist.categories.keys.map { name ->
                        CategoryItem(
                            name = name,
                            channelCount = playlist.categories[name]?.size ?: 0
                        )
                    }.sortedBy { it.name }
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        categories = categoryItems,
                        error = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load playlist"
                    )
                }
            )
        }
    }
    
    /**
     * Selects a category and loads its channels.
     *
     * @param categoryName The name of the category to select
     */
    fun selectCategory(categoryName: String) {
        cachedPlaylist?.let { playlist ->
            val channels = playlist.categories[categoryName] ?: emptyList()
            _uiState.value = _uiState.value.copy(
                selectedCategory = categoryName,
                channels = channels,
                selectedChannel = null
            )
        }
    }
    
    /**
     * Clears the category selection and returns to categories view.
     */
    fun clearCategorySelection() {
        _uiState.value = _uiState.value.copy(
            selectedCategory = null,
            channels = emptyList(),
            selectedChannel = null
        )
    }
    
    /**
     * Selects a channel for playback.
     *
     * @param channel The channel to play
     */
    fun selectChannel(channel: IptvChannel) {
        _uiState.value = _uiState.value.copy(selectedChannel = channel)
    }
    
    /**
     * Clears the selected channel.
     */
    fun clearSelectedChannel() {
        _uiState.value = _uiState.value.copy(selectedChannel = null)
    }
    
    /**
     * Gets all channels (for "All Channels" view).
     *
     * @return List of all channels in the playlist
     */
    fun getAllChannels(): List<IptvChannel> {
        return cachedPlaylist?.allChannels ?: emptyList()
    }
    
    /**
     * Clears the memory cache.
     */
    fun clearMemoryCache() {
        repository.clearMemoryCache()
    }
    
    /**
     * Clears both memory and disk cache.
     */
    fun clearCache() {
        appContext?.let {
            repository.clearCache(it)
        }
    }
}
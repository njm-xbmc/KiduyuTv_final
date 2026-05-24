package com.kiduyuk.klausk.kiduyutv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a TV channel category from the IPTV playlist.
 *
 * @param name The name of the category (e.g., "Sports", "Movies", "News")
 * @param channels The list of channels belonging to this category
 */
data class IptvCategory(
    val name: String,
    val channels: List<IptvChannel> = emptyList()
)

/**
 * Represents a single TV channel from the IPTV playlist.
 *
 * @param name The name of the channel
 * @param logo The URL of the channel's logo image
 * @param url The streaming URL for the channel
 * @param group The category/group the channel belongs to
 * @param tvgId The TVG ID for EPG integration
 * @param tvgName The TVG name for EPG integration
 */
data class IptvChannel(
    val name: String,
    val logo: String?,
    val url: String,
    val group: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null
)

/**
 * Represents the parsed IPTV playlist data.
 *
 * @param categories Map of category names to their channels
 * @param allChannels Flat list of all channels (useful for "All" category)
 */
data class IptvPlaylist(
    val categories: Map<String, List<IptvChannel>>,
    val allChannels: List<IptvChannel>
)
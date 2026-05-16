package com.kiduyuk.klausk.kiduyutv.data.remote

import com.kiduyuk.klausk.kiduyutv.data.model.trakt.*
import retrofit2.Response
import retrofit2.http.*

/**
 * TraktApiService - Retrofit service interface for Trakt API
 */
interface TraktApiService {

    // ── User ────────────────────────────────────────────────────────────────
    
    /**
     * Get user profile
     */
    @GET("users/me")
    suspend fun getUserProfile(): Response<TraktUser>
    
    /**
     * Get watched movies
     */
    @GET("users/me/watched/movies")
    suspend fun getWatchedMovies(): Response<List<TraktWatchedMovie>>
    
    /**
     * Get watched shows (includes all seasons/episodes)
     */
    @GET("users/me/watched/shows")
    suspend fun getWatchedShows(): Response<List<TraktWatchedShow>>
    
    // ── Sync ────────────────────────────────────────────────────────────────
    
    /**
     * Get playback progress for movies and shows
     */
    @GET("sync/playback")
    suspend fun getPlaybackProgress(
        @Query("type") type: String
    ): Response<List<TraktPlaybackProgress>>
    
    /**
     * Get watchlist
     */
    @GET("users/me/watchlist")
    suspend fun getWatchlist(
        @Query("type") type: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): Response<List<TraktWatchlistItem>>
    
    /**
     * Add item to watchlist
     */
    @POST("sync/watchlist")
    suspend fun addToWatchlist(
        @Body items: TraktSyncItems
    ): Response<TraktSyncResponse>
    
    /**
     * Remove item from watchlist
     */
    @DELETE("sync/watchlist")
    suspend fun removeFromWatchlist(
        @Body items: TraktSyncItems
    ): Response<TraktSyncResponse>
    
    // ── Scrobble ────────────────────────────────────────────────────────────
    
    /**
     * Start scrobbling (start watching)
     */
    @POST("scrobble/start")
    suspend fun scrobbleStart(
        @Body scrobble: TraktScrobbleRequest
    ): Response<TraktScrobbleResponse>
    
    /**
     * Pause scrobbling (update progress)
     */
    @POST("scrobble/pause")
    suspend fun scrobblePause(
        @Body scrobble: TraktScrobbleRequest
    ): Response<TraktScrobbleResponse>
    
    /**
     * Stop scrobbling (mark as watched)
     */
    @POST("scrobble/stop")
    suspend fun scrobbleStop(
        @Body scrobble: TraktScrobbleRequest
    ): Response<TraktScrobbleResponse>
    
    // ── History ──────────────────────────────────────────────────────────────
    
    /**
     * Add to watch history
     */
    @POST("sync/history")
    suspend fun addToHistory(
        @Body items: TraktSyncItems
    ): Response<TraktSyncResponse>
    
    /**
     * Get watch history
     */
    @GET("users/me/history")
    suspend fun getWatchHistory(
        @Query("type") type: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): Response<List<TraktHistoryItem>>
}
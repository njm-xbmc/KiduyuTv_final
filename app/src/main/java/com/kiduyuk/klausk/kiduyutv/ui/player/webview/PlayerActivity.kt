import android.view.ViewGroup
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.model.StreamProviderManager
import com.kiduyuk.klausk.kiduyutv.data.model.WatchHistoryItem
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.util.FirebaseManager
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


    // 15-second progress update handler
    private val progressUpdateHandler = Handler(Looper.getMainLooper())
    private val progressUpdateRunnable = Runnable { updateWatchProgress() }
        updateWatchProgress()
    }
    private val repository = TmdbRepository()

     */
    private fun isFireTVDevice(context: Context): Boolean {
        val isFireTvHardware = context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
        val isFireTvModel = Build.MODEL != null && Build.MODEL.startsWith("AFT", ignoreCase = true)
        return isFireTvHardware || isFireTvModel
    }

    companion object {
        private const val TAG = "VideasyPlayer"
        private const val PROGRESS_INTERVAL_MS = 15_000L
    }

    // ── Cursor hide timer ──────────────────────────────────────────────────────
        }
    }

    /**
     * Check if the device is an Amazon Fire TV or Fire Stick.
     */
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fix: Missing Translucent Window Format
        // Fire TV's window manager often requires the Activity's window to be explicitly set 
        // to a translucent format to correctly composite the video surface with the WebView UI.
        window.setFormat(PixelFormat.TRANSLUCENT)

            )

            // Fix: Solid Background Color Overlapping Video
            // On many Fire OS versions, the video is rendered on a SurfaceView that sits behind
            // the WebView's main drawing layer. Setting a solid black background hides the video.
            setBackgroundColor(0x00000000) // Set to transparent
            
            // Fix: Amazon Chromium WebView vs. System WebView
            // Enable debugging for Amazon Chromium WebView optimizations
            if (isFireTV) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true

                // Fix: Media Playback User Gesture Restriction
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                // Viewport scaling
                loadWithOverviewMode = true
                useWideViewPort = true
                
                // FIX: Changed zoom constraints to allow video players to properly resize video layouts.
                // We disable visual buttons (displayZoomControls) so it stays clean.
                builtInZoomControls = false  // True on phones/tablets, False on TV (removes visual artifacts)
                displayZoomControls = false       // Keeps UI completely clean of ugly +/- buttons
                setSupportZoom(true)         // Allows standard devices to stretch cinematic views if needed

                // FIX: Multi-window support must be TRUE for standard HTML5 video elements
                
                // FIX: Multi-window support must be TRUE for standard HTML5 video elements 
                // to scale up and trigger full-screen player states natively.
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true // Allows player scripts to execute properly
                
                // Security layer bypass for http:// streaming streams running on https:// pages
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                cacheMode = WebSettings.LOAD_DEFAULT // Utilizes the browser cache for buffering

                userAgentString = if (isFireTV) {
                    "Mozilla/5.0 (Linux; Android 9; AFTMM Build/PS7233) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                } else {
                    Log.i(TAG, "[WebChrome] onShowCustomView called")
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    Log.d(TAG, "[WebChrome] Load progress: $newProgress%")
                }
            }

            val iframeHtml = intent.getStringExtra("IFRAME_HTML")
            if (iframeHtml != null) {
            }
        }

        // iframe HTML. Updates in-memory position (movies) and position + season/episode (TV).
        // The 15-second timer handles the actual DB + Firebase write — bridge only updates state.
        webView.addJavascriptInterface(
            PlayerBridge { _, positionSec, season, episode ->
                runOnUiThread {
                    // Update current position from player (don't save yet - timer handles DB save)
                    currentPlaybackPosition = (positionSec * 1000).toLong()

                    if (currentIsTv) {
                            if (season != currentSeason || episode != currentEpisode) {
                                currentEpisode = episode
                            }
                    // Update season/episode if provided (TV shows)
                    if (season != null && episode != null && currentIsTv) {
                        if (season != currentSeason || episode != currentEpisode) {
                            Log.i(TAG, "[Episode] Changed S${currentSeason}E${currentEpisode} -> S${season}E${episode}")
                            currentSeason = season
                            currentEpisode = episode
                        }
                    }
                }
            },
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        persistWatchProgress()     // Flush current state immediately — don't wait for next tick
        stopProgressUpdateTimer()
        webView.onPause()
        webView.pauseTimers()
        stopProgressUpdateTimer()
    }

    override fun onDestroy() {
        cursorHideHandler.postDelayed(cursorHideRunnable, 5000)
    }

    // ── Watch history ──────────────────────────────────────────────────────────

    private fun checkAndAddToWatchHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                isMediaInWatchHistory = repository.isInWatchHistory(
                    this@PlayerActivity,
                    currentTmdbId,
                    currentIsTv
                )

                if (!isMediaInWatchHistory) {
                    val watchHistoryItem = WatchHistoryItem(
                        id = currentTmdbId,
                        title = currentTitle,
                    )

                    repository.saveToWatchHistory(this@PlayerActivity, watchHistoryItem)
                    Log.d(TAG, "[WatchHistory] saveToWatchHistory called successfully")

                    FirebaseManager.syncWatchHistory(
                    com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.syncWatchHistory(
                        tmdbId = currentTmdbId,
                        isTv = currentIsTv,
                        seasonNumber = if (currentIsTv) currentSeason else null,
                        voteAverage = currentVoteAverage,
                        releaseDate = currentReleaseDate
                    )
                    Log.d(TAG, "[WatchHistory] Media already in watch history, skipping insert")
                }
            } catch (e: Exception) {
            }
        }
    }

    // ── Progress timer ─────────────────────────────────────────────────────────

    private fun startProgressUpdateTimer() {
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
        progressUpdateHandler.postDelayed(progressUpdateRunnable, PROGRESS_INTERVAL_MS)
    }
        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
    }

     */
    private fun updateWatchProgress() {
     *
     * Movies  → saves playback position only.
    private fun persistWatchProgress() {
        if (currentTmdbId == -1) return

        val backdropPath = currentBackdropPath
        val voteAverage  = currentVoteAverage
        val releaseDate  = currentReleaseDate
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.updatePlaybackPosition(
                    mediaId = currentTmdbId,
                    mediaType = if (currentIsTv) "tv" else "movie",
                    position = currentPlaybackPosition

                if (currentIsTv) {
                    repository.updateEpisodeInfo(
                        position  = position
                        mediaId = currentTmdbId,
                        mediaType = "tv",
                        seasonNumber = currentSeason,
                        episodeNumber = currentEpisode
                    )
                }

                    overview         = overview,
                    backdropPath     = backdropPath,
                    voteAverage      = voteAverage,
                com.kiduyuk.klausk.kiduyutv.util.FirebaseManager.syncWatchHistory(
                    tmdbId = currentTmdbId,
                    isTv = currentIsTv,
                    seasonNumber = if (currentIsTv) currentSeason else null,
                    episodeNumber = if (currentIsTv) currentEpisode else null,
                    playbackPosition = currentPlaybackPosition,
                    duration = 0L,
                    title = currentTitle,
                    overview = currentOverview,
                    posterPath = currentPosterPath,
                    backdropPath = currentBackdropPath,
                    voteAverage = currentVoteAverage,
                    releaseDate = currentReleaseDate
                )
            } catch (e: Exception) {
                Log.e(TAG, "[WatchHistory] Error persisting progress: ${e.message}")
            }
        }
    }

    private fun updateWatchProgress() {
        // Timer-based sync - uses the current position already set by PlayerBridge
        persistWatchProgress()
        progressUpdateHandler.postDelayed(progressUpdateRunnable, 15000)
    }
}
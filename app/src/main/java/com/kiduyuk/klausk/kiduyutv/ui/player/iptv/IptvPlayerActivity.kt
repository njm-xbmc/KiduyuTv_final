package com.kiduyuk.klausk.kiduyutv.ui.player.iptv

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TabHost
import android.widget.TabWidget
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionView
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog

/**
 * Activity for playing IPTV live streams using ExoPlayer.
 *
 * Bottom control bar (right-to-left, matching the screenshot layout):
 *   [PiP placeholder] [Resize] [Tracks] [Volume/Mute] [CC/Subtitles] [Fill label]
 *
 * Resize cycles through FIT → FILL → ZOOM.
 * Volume button toggles mute; icon reflects current state.
 * Tracks button opens a tabbed dialog (Video | Audio | Text).
 * CC button is a shortcut directly to the Text tab of the same dialog.
 */
@OptIn(UnstableApi::class)
class IptvPlayerActivity : ComponentActivity() {

    // ── Resize mode cycling ──────────────────────────────────────────────────
    private enum class ResizeMode(
        val exoMode: Int,
        val label: String
    ) {
        FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Fit"),
        FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Fill"),
        ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Zoom");

        fun next(): ResizeMode = entries[(ordinal + 1) % entries.size]
    }

    companion object {
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_STREAM_URL   = "stream_url"
        const val EXTRA_CHANNEL_LOGO = "channel_logo"

        fun createIntent(
            context: Context,
            channelName: String,
            streamUrl: String,
            channelLogo: String? = null
        ): Intent = Intent(context, IptvPlayerActivity::class.java).apply {
            putExtra(EXTRA_CHANNEL_NAME, channelName)
            putExtra(EXTRA_STREAM_URL,   streamUrl)
            putExtra(EXTRA_CHANNEL_LOGO, channelLogo)
        }

        // Tab tags for the Tracks dialog
        const val TAB_VIDEO = "video"
        const val TAB_AUDIO = "audio"
        const val TAB_TEXT  = "text"

        // dp → px helper (used inside the Activity via extension)
        private fun Context.dp(value: Int): Int =
            (value * resources.displayMetrics.density).toInt()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ── View / player refs ───────────────────────────────────────────────────
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private lateinit var trackSelector: DefaultTrackSelector

    private var channelName: String = ""
    private var streamUrl:   String = ""

    // Control buttons that need state updates after creation
    private var btnVolume: ImageButton? = null
    private var btnResize: ImageButton? = null
    private var resizeLabel: TextView?  = null
    private var currentResize = ResizeMode.FIT
    private var isMuted = false

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while playing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Live TV"
        streamUrl   = intent.getStringExtra(EXTRA_STREAM_URL)   ?: ""

        if (streamUrl.isBlank()) { finish(); return }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = showExitConfirmationDialog()
        })

        setupPlayer()
    }

    // ── Player setup ─────────────────────────────────────────────────────────

    private fun setupPlayer() {
        trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
            )
        }

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setHandleAudioBecomingNoisy(true)
            .build()

        playerView = PlayerView(this).apply {
            player = this@IptvPlayerActivity.player
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            resizeMode = currentResize.exoMode

            // Hide built-in buttons that are replaced by the custom bar
            setShowSubtitleButton(false)
            setShowFastForwardButton(true)
            setShowRewindButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)

            // Fullscreen button exits playback (matches original behaviour)
            setFullscreenButtonClickListener { isFullScreen ->
                if (!isFullScreen) showExitConfirmationDialog()
            }

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Root FrameLayout: PlayerView fills it, custom bar overlays at the bottom
        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(playerView)
            addView(buildControlBar())
        }

        setContentView(rootLayout)

        player?.apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
            playWhenReady = true
            addListener(playerListener)
            prepare()
        }
    }

    // ── Custom control bar ────────────────────────────────────────────────────

    /**
     * Builds the horizontal bottom bar that sits above the PlayerView's own controller.
     * Layout (left → right):
     *   ResizeLabel | [spacer] | CC | Volume | Tracks | PiP(stub)
     *
     * The bar is elevated over the PlayerView so it is always visible.
     * It has a subtle gradient scrim so icons stay readable over any content.
     */
    private fun buildControlBar(): LinearLayout {
        // Scrim gradient: transparent top → semi-black bottom
        val scrim = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, 0xCC000000.toInt())
        )

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background  = scrim
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { bottomMargin = dp(48) } // sit just above PlayerView's own seekbar row

            // ── Resize label (cycles on click) ───────────────────────────────
            resizeLabel = TextView(context).apply {
                text      = currentResize.label
                textSize  = 14f
                setTextColor(Color.WHITE)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = roundedBg(0x44FFFFFF.toInt(), dp(6))
                setOnClickListener { cycleResizeMode() }
            }
            addView(resizeLabel, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            // Spacer pushes everything else to the right
            addView(LinearLayout(context), LinearLayout.LayoutParams(0, 0, 1f))

            // ── CC (opens Tracks dialog on Text tab) ─────────────────────────
            addView(iconButton(R.drawable.exo_icon_subtitle_on) {
                showTracksDialog(initialTab = TAB_TEXT)
            })

            addView(spacer())

            // ── Volume / Mute toggle ─────────────────────────────────────────
            btnVolume = iconButton(R.drawable.exo_icon_audio) {
                toggleMute()
            }
            addView(btnVolume)

            addView(spacer())

            // ── Video Tracks (opens Tracks dialog on Video tab) ──────────────
            addView(iconButton(R.drawable.exo_icon_quality) {
                showTracksDialog(initialTab = TAB_VIDEO)
            })

            addView(spacer())

            // ── PiP placeholder (greyed out, no-op) ──────────────────────────
            addView(iconButton(R.drawable.exo_icon_picture_in_picture, enabled = false) {
                /* PiP not implemented */
            })
        }
    }

    // ── Resize cycling ───────────────────────────────────────────────────────

    private fun cycleResizeMode() {
        currentResize = currentResize.next()
        playerView?.resizeMode = currentResize.exoMode
        resizeLabel?.text      = currentResize.label
    }

    // ── Mute toggle ──────────────────────────────────────────────────────────

    private fun toggleMute() {
        isMuted = !isMuted
        player?.volume = if (isMuted) 0f else 1f
        btnVolume?.setImageResource(
            if (isMuted) R.drawable.exo_icon_audio_disabled
            else         R.drawable.exo_icon_audio
        )
    }

    // ── Tabbed Tracks dialog ─────────────────────────────────────────────────

    /**
     * Shows a tabbed dialog mirroring the screenshot: Video | Audio | Text.
     * Each tab hosts a [TrackSelectionView] for the corresponding track type.
     * [initialTab] controls which tab is selected when the dialog opens.
     */
    private fun showTracksDialog(initialTab: String = TAB_VIDEO) {
        val currentPlayer = player ?: return

        val dialog = Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }

        // ── Root container ───────────────────────────────────────────────────
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = roundedBg(0xFF2A1A1A.toInt(), dp(16))
            setPadding(0, dp(8), 0, dp(16))
        }

        // ── TabHost ──────────────────────────────────────────────────────────
        val tabHost = TabHost(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val tabWidget = TabWidget(this).apply { id = android.R.id.tabs }
        val tabContent = FrameLayout(this).apply {
            id = android.R.id.tabcontent
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(300)
            )
        }

        tabHost.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabWidget)
            addView(tabContent)
        })

        tabHost.setup()

        // Helper: create a TrackSelectionView tab
        fun addTrackTab(tag: String, label: String, trackType: Int, allowMulti: Boolean) {
            val (trackGroups, overrides) = TrackSelectionView.getTracksAndOverride(
                currentPlayer.currentTracks, trackType, false,
                trackSelector, trackSelector.parameters
            )

            val view = TrackSelectionView(this).apply {
                setShowDisableOption(trackType == C.TRACK_TYPE_TEXT)
                setAllowMultipleOverrides(allowMulti)
                init(trackGroups, false, overrides) { disabled, newOverrides ->
                    val builder = trackSelector.buildUponParameters()
                    if (disabled) {
                        builder.setTrackTypeDisabled(trackType, true)
                    } else {
                        builder.setTrackTypeDisabled(trackType, false)
                        newOverrides.values.forEach { builder.addOverride(it) }
                    }
                    trackSelector.setParameters(builder)
                }
                setBackgroundColor(0xFF2A1A1A.toInt())
            }

            val frame = FrameLayout(this).apply { addView(view) }
            tabContent.addView(frame)

            tabHost.addTab(
                tabHost.newTabSpec(tag)
                    .setIndicator(tabLabel(label))
                    .setContent { frame }
            )
        }

        addTrackTab(TAB_VIDEO, "Video", C.TRACK_TYPE_VIDEO, false)
        addTrackTab(TAB_AUDIO, "Audio", C.TRACK_TYPE_AUDIO, false)
        addTrackTab(TAB_TEXT,  "Text",  C.TRACK_TYPE_TEXT,  false)

        // Colour the tab strip to match the dark theme
        tabWidget.setBackgroundColor(0xFF2A1A1A.toInt())

        root.addView(tabHost)

        // ── Cancel / OK buttons ──────────────────────────────────────────────
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.END
            setPadding(dp(16), dp(8), dp(16), 0)
        }

        fun dialogBtn(text: String, action: () -> Unit) = TextView(this).apply {
            this.text = text
            textSize  = 14f
            setTextColor(0xFFEECFA0.toInt())
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { action() }
        }

        btnRow.addView(dialogBtn("CANCEL") { dialog.dismiss() })
        btnRow.addView(dialogBtn("OK")     { dialog.dismiss() })
        root.addView(btnRow)

        dialog.setContentView(root)

        // Select the requested tab after the dialog is shown
        tabHost.currentTab = when (initialTab) {
            TAB_AUDIO -> 1
            TAB_TEXT  -> 2
            else      -> 0
        }

        dialog.show()
    }

    // ── Key events (hardware MENU / SETTINGS opens tracks dialog) ───────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_SETTINGS -> {
                    showTracksDialog()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ── Player error handling ────────────────────────────────────────────────

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            showErrorDialog(error.message ?: "Playback error occurred")
        }
    }

    private fun showErrorDialog(message: String) {
        QuitDialog(
            context           = this,
            title             = "Playback Error",
            message           = message,
            positiveButtonText = "Retry",
            negativeButtonText = "Exit",
            lottieAnimRes     = R.raw.exit,
            onNo              = { finish() },
            onYes             = { player?.prepare(); player?.play() }
        ).show()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onResume() { super.onResume(); player?.play()  }
    override fun onPause()  { super.onPause();  player?.pause() }
    override fun onStop()   { super.onStop();   player?.pause() }

    override fun onDestroy() {
        super.onDestroy()
        player?.removeListener(playerListener)
        player?.release()
        player     = null
        playerView = null
        btnVolume  = null
        btnResize  = null
        resizeLabel = null
    }

    // ── Exit dialog ──────────────────────────────────────────────────────────

    private fun showExitConfirmationDialog() {
        QuitDialog(
            context            = this,
            title              = "Stop Playback?",
            message            = "Are you sure you want to stop watching $channelName?",
            positiveButtonText = "Stop",
            negativeButtonText = "Continue",
            lottieAnimRes      = R.raw.exit,
            onNo               = { },
            onYes              = { finish() }
        ).show()
    }

    // ── View helpers ─────────────────────────────────────────────────────────

    /** Small fixed-width spacer between icon buttons. */
    private fun spacer() = LinearLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(8), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** Icon button with tint and optional enabled state. */
    private fun iconButton(
        resId: Int,
        enabled: Boolean = true,
        onClick: () -> Unit
    ) = ImageButton(this).apply {
        setImageResource(resId)
        background = null
        imageTintList = ColorStateList.valueOf(
            if (enabled) Color.WHITE else 0x55FFFFFF.toInt()
        )
        isEnabled = enabled
        val pad = dp(8)
        setPadding(pad, pad, pad, pad)
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        setOnClickListener { onClick() }
    }

    /** Rounded-corner background drawable. */
    private fun roundedBg(color: Int, radius: Int) =
        GradientDrawable().apply {
            shape       = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    /** Styled tab label TextView. */
    private fun tabLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize  = 14f
        gravity   = Gravity.CENTER
        setTextColor(Color.WHITE)
        setPadding(dp(16), dp(12), dp(16), dp(12))
    }
}

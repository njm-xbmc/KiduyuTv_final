package com.kiduyuk.klausk.kiduyutv.ui.player.iptv

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
import androidx.media3.ui.TrackSelectionDialogBuilder
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog

@OptIn(UnstableApi::class)
class IptvPlayerActivity : ComponentActivity() {

    private enum class ResizeMode(val exoMode: Int, val label: String) {
        FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Fit"),
        FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Fill"),
        ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Zoom");
        fun next(): ResizeMode = entries[(ordinal + 1) % entries.size]
    }

    companion object {
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_STREAM_URL   = "stream_url"
        
        fun createIntent(context: Context, channelName: String, streamUrl: String) = 
            Intent(context, IptvPlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_STREAM_URL, streamUrl)
            }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private lateinit var trackSelector: DefaultTrackSelector
    private var channelName: String = ""
    private var streamUrl: String = ""
    private var currentResize = ResizeMode.FIT
    private var isMuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Live TV"
        streamUrl   = intent.getStringExtra(EXTRA_STREAM_URL)   ?: ""

        if (streamUrl.isBlank()) { finish(); return }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = showExitConfirmationDialog()
        })

        setupPlayer()
    }

    private fun setupPlayer() {
        trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setViewportSizeToPhysicalDisplaySize(this@IptvPlayerActivity, true))
        }

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(DefaultRenderersFactory(this))
            .setTrackSelector(trackSelector)
            .build()

        playerView = PlayerView(this).apply {
            player = this@IptvPlayerActivity.player
            resizeMode = currentResize.exoMode
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }

        val rootLayout = FrameLayout(this).apply {
            addView(playerView)
            addView(buildControlBar())
        }
        setContentView(rootLayout)

        player?.apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
            prepare()
            playWhenReady = true
        }
    }

    private fun buildControlBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(48) }
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, 0xCC000000.toInt()))

            addView(iconButton(R.drawable.exo_icon_quality) { showTrackDialog(C.TRACK_TYPE_VIDEO) })
            addView(iconButton(R.drawable.exo_icon_audio) { showTrackDialog(C.TRACK_TYPE_AUDIO) })
            addView(iconButton(R.drawable.exo_icon_subtitle_on) { showTrackDialog(C.TRACK_TYPE_TEXT) })
        }
    }

    private fun showTrackDialog(trackType: Int) {
        player?.let {
            TrackSelectionDialogBuilder(this, "Select Track", it, trackType)
                .setShowDisableOption(trackType != C.TRACK_TYPE_VIDEO)
                .build()
                .show()
        }
    }

    private fun iconButton(resId: Int, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(resId)
        background = null
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setOnClickListener { onClick() }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_SETTINGS)) {
            showTrackDialog(C.TRACK_TYPE_VIDEO)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showExitConfirmationDialog() {
        QuitDialog(this, "Stop Playback?", "Exit $channelName?", "Stop", "Continue", R.raw.exit, {}, { finish() }).show()
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }
}


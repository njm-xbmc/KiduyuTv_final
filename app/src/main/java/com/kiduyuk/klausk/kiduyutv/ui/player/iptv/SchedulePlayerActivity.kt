package com.kiduyuk.klausk.kiduyutv.ui.player.iptv

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.data.model.ChannelWatchPage
import com.kiduyuk.klausk.kiduyutv.data.model.PlayerOption
import com.kiduyuk.klausk.kiduyutv.data.repository.ScheduleRepository
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.MouseCursorView
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Schedule Player Activity for playing scheduled channels from dlhd.pk
 * Extends the existing PlayerActivity functionality with schedule-specific features
 * Takes an iframe HTML as intent extra and plays the scheduled channel in WebView
 * Includes a focusable row of player source options at the top for easy stream switching
 * Uses ChannelWatchPage and playerOptions for handling multiple streams
 */
class SchedulePlayerActivity : ComponentActivity() {

    private lateinit var webView: android.webkit.WebView
    private lateinit var cursorView: MouseCursorView
    private lateinit var rootLayout: FrameLayout
    private var cursorX = 0f
    private var cursorY = 0f
    private val moveSpeed = 50f
    private var screenWidth = 0
    private var screenHeight = 0

    private var isCursorDisabled = false
    private var currentIframeHtml: String? = null
    private var channelName: String = "Channel"
    private var eventTitle: String = "Channel"

    // Prevent multiple activity launches when a stream is sniffed
    private var isStreamSniffed = false

    // Player sources - now uses ChannelWatchPage and PlayerOption
    private var playerOptions: List<PlayerOption> = emptyList()
    private var selectedPlayerIndex: Int = 0
    private var channelWatchPage: ChannelWatchPage? = null

    // Direct iframe URLs passed from scraped channels
    private var iframeUrls: List<String> = emptyList()
    private var hasDirectIframeUrls: Boolean = false

    // UI State — backed by Compose state so the UI reacts to changes
    private val isTopBarVisible = mutableStateOf(true)

    companion object {
        private const val TAG = "SchedulePlayer"

        // Intent extras
        const val EXTRA_CHANNEL_ID = "CHANNEL_ID"
        const val EXTRA_CHANNEL_NAME = "CHANNEL_NAME"
        const val EXTRA_EVENT_TITLE = "EVENT_TITLE"
        const val EXTRA_SELECTED_PLAYER = "SELECTED_PLAYER"
        const val EXTRA_IFRAME_URLS = "IFRAME_URLS"

        /**
         * Creates an intent to launch the SchedulePlayerActivity
         */
        fun createIntent(
            context: Context,
            channelId: String,
            channelName: String,
            eventTitle: String,
            iframeUrls: List<String> = emptyList(),
            selectedPlayerIndex: Int = 0
        ) = android.content.Intent(context, SchedulePlayerActivity::class.java).apply {
            putExtra(EXTRA_CHANNEL_ID, channelId)
            putExtra(EXTRA_CHANNEL_NAME, channelName)
            putExtra(EXTRA_EVENT_TITLE, eventTitle)
            putExtra(EXTRA_SELECTED_PLAYER, selectedPlayerIndex)
            putStringArrayListExtra(EXTRA_IFRAME_URLS, ArrayList(iframeUrls))
        }
    }

    // Unified idle timer — hides both cursor and top bar
    private val cursorHideHandler = Handler(Looper.getMainLooper())
    private val cursorHideRunnable = Runnable {
        if (!isCursorDisabled && isCursorVisible) {
            cursorView.animate().alpha(0f).setDuration(500).start()
            isCursorVisible = false
        }
        hideTopBar()
    }
    private var isCursorVisible = false

    // Top bar auto-hide timer
    private val topBarHideHandler = Handler(Looper.getMainLooper())
    private val topBarHideRunnable = Runnable {
        if (isTopBarVisible.value && !isDpadNavigating) {
            hideTopBar()
        }
    }
    private var isDpadNavigating = false
    private val TOPBAR_HIDE_DELAY_MS = 5000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: ""
        channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Channel"
        eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Event"
        selectedPlayerIndex = intent.getIntExtra(EXTRA_SELECTED_PLAYER, 0)

        val passedIframeUrls = intent.getStringArrayListExtra(EXTRA_IFRAME_URLS)
        if (!passedIframeUrls.isNullOrEmpty()) {
            iframeUrls = passedIframeUrls
            hasDirectIframeUrls = true
            android.util.Log.i(TAG, "Received ${iframeUrls.size} iframe URLs from intent")
        }

        if (channelId.isEmpty() && iframeUrls.isEmpty()) {
            Toast.makeText(this, "No channel ID or stream URLs provided", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        detectDeviceType()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })

        setupLayout()

        if (hasDirectIframeUrls) {
            setupWithDirectIframeUrls()
        } else {
            fetchChannelWatchPage(channelId)
        }
    }

    /**
     * Unified handler for successfully sniffing a stream.
     * Routes the extracted URL to the native ExoPlayer and tears down the WebView.
     */
    private fun handleSniffedStream(streamUrl: String) {
        if (isStreamSniffed) return
        isStreamSniffed = true

        android.util.Log.i(TAG, "[StreamSniffer] SUCCESS! Routing to native player: $streamUrl")

        runOnUiThread {
            Toast.makeText(this, "Stream detected! Launching native player...", Toast.LENGTH_SHORT).show()

            val intent = IptvPlayerActivity.createIntent(
                context = this@SchedulePlayerActivity,
                channelName = channelName,
                streamUrl = streamUrl,
                channelLogo = null,
                tvgId = intent.getStringExtra(EXTRA_CHANNEL_ID),
                tvgName = eventTitle,
                group = "Scheduled Events"
            )

            startActivity(intent)

            if (::webView.isInitialized) {
                webView.loadUrl("about:blank")
            }
            finish()
        }
    }

    private fun fetchChannelWatchPage(channelId: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                ScheduleRepository.getInstance().fetchChannelWatchPage(channelId)
            }

            result.fold(
                onSuccess = { watchPage ->
                    channelWatchPage = watchPage
                    playerOptions = watchPage.playerOptions

                    if (playerOptions.isEmpty()) {
                        currentIframeHtml = ScheduleRepository.getInstance()
                            .generateIframeHtml(watchPage.defaultIframeUrl)
                    } else {
                        val playerToUse = playerOptions.getOrNull(selectedPlayerIndex)
                            ?: playerOptions.find { it.isActive }
                            ?: playerOptions.first()

                        currentIframeHtml = ScheduleRepository.getInstance()
                            .generateIframeHtml(playerToUse.url)
                        selectedPlayerIndex = playerOptions.indexOf(playerToUse)
                    }

                    loadCurrentStream()
                    updateTopBar()
                },
                onFailure = { error ->
                    android.util.Log.e(TAG, "Failed to fetch watch page: ${error.message}")
                    currentIframeHtml = ScheduleRepository.getInstance()
                        .generateIframeHtml("https://dlhd.pk/player/stream-$channelId.php")
                    loadCurrentStream()
                }
            )
        }
    }

    private fun loadCurrentStream() {
        currentIframeHtml?.let { html ->
            if (::webView.isInitialized) {
                webView.loadDataWithBaseURL(
                    "https://dlhd.pk",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    }

    private fun setupWithDirectIframeUrls() {
        if (iframeUrls.isEmpty()) {
            Toast.makeText(this, "No stream URLs available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val totalIframes = iframeUrls.size
        Toast.makeText(this, "$totalIframes stream option(s) available for $channelName", Toast.LENGTH_LONG).show()

        playerOptions = iframeUrls.mapIndexed { index, url ->
            PlayerOption(
                playerNumber = index + 1,
                url = url,
                isActive = index == selectedPlayerIndex
            )
        }

        if (selectedPlayerIndex >= playerOptions.size) {
            selectedPlayerIndex = 0
        }

        if (iframeUrls.isNotEmpty()) {
            currentIframeHtml = generateIframeHtml(iframeUrls[selectedPlayerIndex])
            loadCurrentStream()
        }

        updateTopBar()
    }

    private fun generateIframeHtml(streamUrl: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
                    iframe { width: 100%; height: 100%; border: 0; }
                </style>
            </head>
            <body>
                <iframe src="$streamUrl" width="100%" height="100%" scrolling="no" frameborder="0" allowfullscreen="true" allow="autoplay;" allowtransparency="true" id="thatframe"></iframe>
            </body>
            </html>
        """.trimIndent()
    }

    private fun updateTopBar() {
        isTopBarVisible.value = true
        scheduleTopBarHide()
    }

    private fun injectVideoVolumeController() {
        if (!::webView.isInitialized) return
        val jsCode = """
            (function() {
                console.log('[VideoController] Initializing video volume controller');
                function setVideoVolumeMax(video) {
                    try {
                        if (video.volume !== 1 || video.muted) {
                            video.volume = 1;
                            video.muted = false;
                        }
                    } catch(e) { }
                }
                function processAllVideos() {
                    try {
                        var videos = document.querySelectorAll('video');
                        if (videos.length > 0) videos.forEach(function(v) { setVideoVolumeMax(v); });
                        
                        var iframes = document.querySelectorAll('iframe');
                        iframes.forEach(function(iframe) {
                            try {
                                if (iframe.contentDocument) {
                                    var iframeVideos = iframe.contentDocument.querySelectorAll('video');
                                    if (iframeVideos.length > 0) iframeVideos.forEach(function(v) { setVideoVolumeMax(v); });
                                }
                            } catch(e) { }
                        });
                    } catch(e) { }
                }
                
                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        if (mutation.addedNodes && mutation.addedNodes.length > 0) {
                            for (var i = 0; i < mutation.addedNodes.length; i++) {
                                var node = mutation.addedNodes[i];
                                if (node.nodeName && node.nodeName.toLowerCase() === 'video') setVideoVolumeMax(node);
                                if (node.querySelectorAll) {
                                    var videos = node.querySelectorAll('video');
                                    if (videos.length > 0) videos.forEach(function(v) { setVideoVolumeMax(v); });
                                }
                            }
                        }
                    });
                });
                
                if (document.body) {
                    observer.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['src'] });
                }
                processAllVideos();
                setInterval(processAllVideos, 2000);
            })();
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupLayout() {
        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        webView = createWebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY)
            overScrollMode = View.OVER_SCROLL_NEVER

            if (isCursorDisabled) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            } else {
                setLayerType(View.LAYER_TYPE_NONE, null)
            }

            webViewClient = object : android.webkit.WebViewClient() {
                
                override fun shouldInterceptRequest(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?
                ): android.webkit.WebResourceResponse? {
                    val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                    val headers = request.requestHeaders

                    if (!isStreamSniffed) {
                        // 1. Master Catch-All Stream Sniffer
                        val isKnownExtension = url.contains(".m3u8") || 
                                               url.contains(".mpd") || 
                                               url.contains("master.m3u8") || 
                                               url.contains("playlist.m3u8")

                        // Prevent false positives on js/css files
                        val isMediaChunk = url.contains("chunk") && !url.endsWith(".js") && !url.endsWith(".css")

                        // 2. Catch Stream Headers
                        val acceptHeader = headers?.get("Accept") ?: ""
                        val isVideoRequest = acceptHeader.contains("video/") || 
                                             acceptHeader.contains("application/x-mpegURL") || 
                                             acceptHeader.contains("application/dash+xml")

                        if (isKnownExtension || isMediaChunk || isVideoRequest) {
                            handleSniffedStream(url)
                        }
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    android.util.Log.i(TAG, "[WebView] Page finished: $url")

                    // Inject CSS to hide ads
                    view?.evaluateJavascript("""
                        (function() {
                            var style = document.createElement('style');
                            style.innerHTML = 'div[id^="ad"], div[class^="ad"], .popup, .overlay, iframe[src*="ads"] { display: none !important; }';
                            document.head.appendChild(style);
                        })();
                    """.trimIndent(), null)

                    injectVideoVolumeController()

                    // 3. Edge Case: JavaScript Blob Streams (XHR/Fetch Interceptor)
                    val blobSnifferJs = """
                        (function() {
                            console.log('[StreamSniffer] Injecting Fetch/XHR interceptors...');

                            const originalFetch = window.fetch;
                            window.fetch = async function(...args) {
                                const reqUrl = typeof args[0] === 'string' ? args[0] : args[0]?.url;
                                if (reqUrl && (reqUrl.includes('.m3u8') || reqUrl.includes('.mpd'))) {
                                    if (window.Android && window.Android.onStreamSniffed) {
                                        window.Android.onStreamSniffed(reqUrl);
                                    }
                                }
                                return originalFetch.apply(this, args);
                            };

                            const originalOpen = XMLHttpRequest.prototype.open;
                            XMLHttpRequest.prototype.open = function(method, reqUrl) {
                                if (reqUrl && (reqUrl.includes('.m3u8') || reqUrl.includes('.mpd'))) {
                                    if (window.Android && window.Android.onStreamSniffed) {
                                        window.Android.onStreamSniffed(reqUrl);
                                    }
                                }
                                originalOpen.apply(this, arguments);
                            };
                        })();
                    """.trimIndent()

                    view?.evaluateJavascript(blobSnifferJs, null)
                }

                override fun onReceivedError(
                    view: android.webkit.WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        android.util.Log.e(TAG, "[WebView] Error: ${error?.description}")
                        if (hasDirectIframeUrls) {
                            tryNextStreamUrl()
                        } else {
                            tryNextPlayer()
                        }
                    }
                }
            }

            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                }
            }
        }

        // Set up JavaScript interface for showing toasts and catching blob streams
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun showToast(message: String) {
                runOnUiThread {
                    Toast.makeText(this@SchedulePlayerActivity, message, Toast.LENGTH_LONG).show()
                }
            }

            @JavascriptInterface
            fun onStreamSniffed(url: String) {
                handleSniffedStream(url)
            }
        }, "Android")

        cursorView = MouseCursorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val composeView = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 0
            }
            setContent {
                val topBarVisible by isTopBarVisible
                MaterialTheme {
                    AnimatedVisibility(
                        visible = topBarVisible,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it }
                    ) {
                        PlayerSourceTopBar(
                            channelName = channelName,
                            eventTitle = eventTitle,
                            playerOptions = playerOptions,
                            selectedIndex = selectedPlayerIndex,
                            onSourceSelected = { index ->
                                if (index != selectedPlayerIndex && index in playerOptions.indices) {
                                    switchToPlayer(index)
                                }
                            },
                            onBackPressed = { showExitConfirmationDialog() }
                        )
                    }
                }
            }
        }

        rootLayout.addView(webView)
        rootLayout.addView(composeView)
        if (!isCursorDisabled) {
            rootLayout.addView(cursorView)
            cursorView.bringToFront()
        }

        setContentView(rootLayout)

        rootLayout.isFocusable = true
        rootLayout.isFocusableInTouchMode = true
        rootLayout.requestFocus()

        rootLayout.post {
            screenWidth = rootLayout.width
            screenHeight = rootLayout.height
            if (!isCursorDisabled) {
                cursorX = screenWidth / 2f
                cursorY = screenHeight / 2f
                updateCursorPosition()
                showCursorAndResetTimer()
            }
        }

        scheduleTopBarHide()
    }

    private fun switchToPlayer(index: Int) {
        if (index in playerOptions.indices) {
            isStreamSniffed = false // Reset sniffer for new source
            selectedPlayerIndex = index
            val player = playerOptions[index]
            currentIframeHtml = generateIframeHtml(player.url)
            loadCurrentStream()
            Toast.makeText(this, "Switched to: Player ${player.playerNumber}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryNextPlayer() {
        if (playerOptions.size > 1) {
            val nextIndex = (selectedPlayerIndex + 1) % playerOptions.size
            Toast.makeText(this, "Stream failed. Trying: Player ${playerOptions[nextIndex].playerNumber}", Toast.LENGTH_SHORT).show()
            switchToPlayer(nextIndex)
        }
    }

    private fun tryNextStreamUrl() {
        if (iframeUrls.size > 1) {
            isStreamSniffed = false // Reset sniffer for new source
            val nextIndex = (selectedPlayerIndex + 1) % iframeUrls.size
            Toast.makeText(this, "Stream failed. Trying: Player ${nextIndex + 1}", Toast.LENGTH_SHORT).show()
            selectedPlayerIndex = nextIndex
            currentIframeHtml = generateIframeHtml(iframeUrls[nextIndex])
            loadCurrentStream()
        }
    }

    private fun detectDeviceType() {
        val uiModeManager = getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        if (uiModeManager.currentModeType != android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
            isCursorDisabled = true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: android.content.Context): android.webkit.WebView {
        val webView = android.webkit.WebView(context)
        val isHardwareAccelerated = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_HARDWARE_ACCELERATED != 0

        if (isHardwareAccelerated) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        return webView
    }

    private fun showExitConfirmationDialog() {
        QuitDialog(
            context = this,
            title = "Stop Playback?",
            message = "Are you sure you want to stop watching $channelName?",
            positiveButtonText = "Stop",
            negativeButtonText = "Continue",
            lottieAnimRes = R.raw.exit,
            onNo = { },
            onYes = { finish() }
        ).show()
    }

    private fun hideTopBar() {
        isTopBarVisible.value = false
    }

    private fun scheduleTopBarHide() {
        topBarHideHandler.removeCallbacks(topBarHideRunnable)
        topBarHideHandler.postDelayed(topBarHideRunnable, TOPBAR_HIDE_DELAY_MS)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) {
            webView.onPause()
            webView.pauseTimers()
        }
        topBarHideHandler.removeCallbacks(topBarHideRunnable)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.evaluateJavascript("if(window.__videoControllerInterval) clearInterval(window.__videoControllerInterval);", null)
        }
        
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        topBarHideHandler.removeCallbacks(topBarHideRunnable)

        if (::webView.isInitialized) {
            try {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.apply {
                    stopLoading()
                    webChromeClient = android.webkit.WebChromeClient()
                    webViewClient = android.webkit.WebViewClient()
                    clearHistory()
                    clearCache(true)
                    loadUrl("about:blank")
                    onPause()
                    removeAllViews()
                    destroy()
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error during WebView cleanup: ${e.message}")
            }
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (isDpadKey(event)) {
            isDpadNavigating = true
            showCursorAndResetTimer()
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> return onKeyDown(event.keyCode, event)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isDpadKeyCode(keyCode)) {
            isDpadNavigating = true
            showCursorAndResetTimer()
        }

        if (isCursorDisabled) return super.onKeyDown(keyCode, event)

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                showCursorAndResetTimer()
                cursorY = (cursorY - moveSpeed).coerceAtLeast(0f)
                updateCursorPosition()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                showCursorAndResetTimer()
                cursorY = (cursorY + moveSpeed).coerceAtMost(screenHeight.toFloat())
                updateCursorPosition()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                showCursorAndResetTimer()
                cursorX = (cursorX - moveSpeed).coerceAtLeast(0f)
                updateCursorPosition()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                showCursorAndResetTimer()
                cursorX = (cursorX + moveSpeed).coerceAtMost(screenWidth.toFloat())
                updateCursorPosition()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                showCursorAndResetTimer()
                simulateClick(cursorX, cursorY)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun isDpadKey(event: KeyEvent): Boolean {
        return event.source and android.view.InputDevice.SOURCE_DPAD == android.view.InputDevice.SOURCE_DPAD ||
                event.keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS
        )
    }

    private fun isDpadKeyCode(keyCode: Int): Boolean {
        return keyCode in listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS
        )
    }

    private fun updateCursorPosition() {
        if (isCursorDisabled) return
        cursorView.x = cursorX
        cursorView.y = cursorY
        cursorView.bringToFront()
        cursorView.invalidate()
    }

    private fun simulateClick(x: Float, y: Float) {
        val downTime = android.os.SystemClock.uptimeMillis()
        val eventTime = android.os.SystemClock.uptimeMillis()

        val downEvent = android.view.MotionEvent.obtain(downTime, eventTime, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
        val upEvent = android.view.MotionEvent.obtain(downTime, eventTime + 100, android.view.MotionEvent.ACTION_UP, x, y, 0)

        downEvent.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        upEvent.source = android.view.InputDevice.SOURCE_TOUCHSCREEN

        window.decorView.dispatchTouchEvent(downEvent)
        window.decorView.dispatchTouchEvent(upEvent)

        downEvent.recycle()
        upEvent.recycle()
    }

    private fun showCursorAndResetTimer() {
        isTopBarVisible.value = true
        topBarHideHandler.removeCallbacks(topBarHideRunnable)

        if (isCursorDisabled) {
            cursorHideHandler.removeCallbacks(cursorHideRunnable)
            cursorHideHandler.postDelayed(cursorHideRunnable, 5000)
            return
        }

        cursorView.animate().cancel()
        cursorView.alpha = 1f
        isCursorVisible = true
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        cursorHideHandler.postDelayed(cursorHideRunnable, 5000)
    }
}

// ============================================================================
// COMPOSE — Player Source Selection Top Bar
// ============================================================================

@Composable
fun PlayerSourceTopBar(
    channelName: String,
    eventTitle: String,
    playerOptions: List<PlayerOption>,
    selectedIndex: Int,
    onSourceSelected: (Int) -> Unit,
    onBackPressed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color(0xCC000000))
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBackPressed = onBackPressed)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = channelName,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = eventTitle,
                        color = Color(0xFFB0B0B0),
                        fontSize = 14.sp
                    )
                }
            }

            if (playerOptions.isNotEmpty()) {
                Text(
                    text = "${playerOptions.size} Players",
                    color = Color(0xFF888888),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        if (playerOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sources:",
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                itemsIndexed(playerOptions) { index, playerOption ->
                    PlayerOptionButton(
                        playerNumber = playerOption.playerNumber,
                        isSelected = index == selectedIndex,
                        onClick = { onSourceSelected(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BackButton(
    onBackPressed: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier
            .focusable()
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            .clickable { onBackPressed() },
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) Color(0xFF424242) else Color.Transparent,
        border = if (isFocused) BorderStroke(1.dp, Color(0xFF448AFF)) else null
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = if (isFocused) Color(0xFF448AFF) else Color.White,
            modifier = Modifier.padding(8.dp).size(24.dp)
        )
    }
}

@Composable
private fun PlayerOptionButton(
    playerNumber: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor = when {
        isSelected -> Color(0xFF2196F3)
        isFocused -> Color(0xFF2D2D2D)
        else -> Color(0xFF1A1A1A)
    }

    val textColor = when {
        isSelected -> Color.White
        isFocused -> Color(0xFF448AFF)
        else -> Color(0xFFE0E0E0)
    }

    val borderColor = when {
        isSelected -> Color(0xFFFF1744)
        isFocused -> Color(0xFF448AFF)
        else -> Color(0xFF404040)
    }

    Surface(
        modifier = Modifier
            .focusable()
            .onFocusChanged { focusState -> isFocused = focusState.isFocused }
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = BorderStroke(2.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "P$playerNumber",
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

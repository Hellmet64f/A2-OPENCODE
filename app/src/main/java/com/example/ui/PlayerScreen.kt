package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.AppState
import com.example.model.FocusCalibrationManager
import com.example.model.ScreenZoomManager
import com.example.model.WatchHistoryItem
import com.example.model.WatchHistoryManager
import com.example.network.SearchService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun PlayerScreen(
    playingState: AppState.Playing,
    onBackToPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Master Controls Visibility State: Starts visible then auto-hides for immersive zoom viewing
    var areControlsVisible by remember { mutableStateOf(true) }
    var isLoadingPage by remember { mutableStateOf(true) }
    var showEpisodeSheet by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }

    // Focus Calibration state (persisted for all movies & series)
    val initialCalibration = remember { FocusCalibrationManager.getCalibration(context) }
    var focusXRatio by remember { mutableFloatStateOf(initialCalibration.first) }
    var focusYRatio by remember { mutableFloatStateOf(initialCalibration.second) }
    var isCalibrationOpen by remember { mutableStateOf(false) }
    var testClickIndicatorTrigger by remember { mutableLongStateOf(0L) }

    // Screen Zoom state (persisted for all movies & series)
    val initialZoom = remember { ScreenZoomManager.getZoomConfig(context) }
    var zoomScale by remember { mutableFloatStateOf(initialZoom.scale) }
    var panX by remember { mutableFloatStateOf(initialZoom.panX) }
    var panY by remember { mutableFloatStateOf(initialZoom.panY) }
    var zoomPreset by remember { mutableStateOf(initialZoom.preset) }
    var showZoomSheet by remember { mutableStateOf(false) }

    fun cycleZoomPreset() {
        val next = ScreenZoomManager.cycleNextPreset(context, ScreenZoomManager.ZoomConfig(zoomScale, panX, panY, zoomPreset))
        zoomScale = next.scale
        panX = next.panX
        panY = next.panY
        zoomPreset = next.preset
        val label = ScreenZoomManager.getPresetLabel(next.preset, next.scale)
        val msg = "🔍 Zoom: $label"
        com.example.MainActivity.seekOsdMessage = msg
        coroutineScope.launch {
            delay(1800)
            if (com.example.MainActivity.seekOsdMessage == msg) {
                com.example.MainActivity.seekOsdMessage = null
            }
        }
    }

    LaunchedEffect(zoomScale, panX, panY, zoomPreset) {
        com.example.MainActivity.onToggleZoomCallback = {
            cycleZoomPreset()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            com.example.MainActivity.onToggleZoomCallback = null
        }
    }

    var showDownloadEditDialog by remember { mutableStateOf(false) }
    var capturedMediaUrl by remember { mutableStateOf<String?>(null) }

    fun extractMediaUrlFromWebView() {
        val wv = webViewRef ?: return
        wv.evaluateJavascript(
            """
            (function() {
                try {
                    var v = document.querySelector('video');
                    if (v && (v.currentSrc || v.src)) {
                        return v.currentSrc || v.src;
                    }
                    var s = document.querySelector('source');
                    if (s && s.src) {
                        return s.src;
                    }
                    var a = document.querySelector('a[href*=".mp4"], a[download]');
                    if (a && a.href) {
                        return a.href;
                    }
                } catch(e) {}
                return '';
            })();
            """.trimIndent()
        ) { result ->
            val clean = result?.trim('"', '\'', ' ', '\n', '\r')
            if (!clean.isNullOrBlank() && clean != "null") {
                capturedMediaUrl = clean
            }
        }
    }

    LaunchedEffect(Unit) {
        com.example.MainActivity.onOpenDownloadEditCallback = {
            extractMediaUrlFromWebView()
            showDownloadEditDialog = true
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            com.example.MainActivity.onOpenDownloadEditCallback = null
        }
    }

    var nextEpisodeUrl by remember { mutableStateOf<String?>(null) }
    var prevEpisodeUrl by remember { mutableStateOf<String?>(null) }

    val episodes = playingState.episodes
    var currentEpIndex by remember(playingState.currentEpisodeIndex, playingState.url) {
        val idx = if (episodes.isNotEmpty()) {
            val found = episodes.indexOfFirst { it.url == playingState.url }
            if (found >= 0) found else playingState.currentEpisodeIndex.coerceIn(0, episodes.size - 1)
        } else {
            0
        }
        mutableStateOf(idx)
    }
    var currentUrl by remember(playingState.url) { mutableStateOf(playingState.url) }
    val currentEpisode = episodes.getOrNull(currentEpIndex)
    val isSeriesEpisode = playingState.isSeries || episodes.isNotEmpty() ||
            currentUrl.contains("/episodios/") || currentUrl.contains("/anime/")

    // Helper: AniChan URL Regex
    val aniChanMatch = remember(currentUrl) {
        Regex("""/anime/(\d+)/([^/]+)/(\d+)/?""").find(currentUrl)
    }

    // Connect global reference so TV remote key events can reveal controls if hidden
    LaunchedEffect(areControlsVisible) {
        com.example.MainActivity.arePlayerControlsVisible = areControlsVisible
    }
    LaunchedEffect(com.example.MainActivity.arePlayerControlsVisible) {
        areControlsVisible = com.example.MainActivity.arePlayerControlsVisible
    }

    // Auto-hide controls for full-screen immersive watching
    LaunchedEffect(Unit) {
        delay(4000)
        areControlsVisible = false
    }

    // Navigation URLs (Next/Prev Episode)
    LaunchedEffect(currentUrl) {
        val videoId = Regex("""-(\d+)/?$""").find(currentUrl)?.groupValues?.get(1)
        if (!videoId.isNullOrBlank()) {
            coroutineScope.launch {
                val data = SearchService.fetchPlayerData(videoId)
                if (data != null) {
                    if (!data.nextEpisodeUrl.isNullOrBlank()) {
                        nextEpisodeUrl = data.nextEpisodeUrl
                    }
                    if (!data.prevEpisodeUrl.isNullOrBlank()) {
                        prevEpisodeUrl = data.prevEpisodeUrl
                    }
                }
            }
        }
    }

    fun playEpisodeByIndex(index: Int) {
        if (index in episodes.indices) {
            currentEpIndex = index
            val newEp = episodes[index]
            currentUrl = newEp.url
            webViewRef?.loadUrl(newEp.url)
            showEpisodeSheet = false

            // Update persistent watch history
            try {
                val seriesTitle = playingState.title.substringBefore(" - ").takeIf { it.isNotBlank() } ?: playingState.title
                val historyItem = WatchHistoryItem(
                    id = playingState.url.substringBefore("/episodio/").takeIf { it.isNotBlank() } ?: playingState.url,
                    title = seriesTitle,
                    coverUrl = "",
                    currentUrl = newEp.url,
                    isSeries = true,
                    isAnime = playingState.url.contains("anichan.to") || newEp.url.contains("anichan.to"),
                    season = newEp.season,
                    episodeNumber = newEp.number,
                    episodeTitle = newEp.title
                )
                WatchHistoryManager.saveProgress(context, historyItem)
            } catch (_: Exception) {}
        }
    }

    fun playNextEpisode() {
        if (episodes.isNotEmpty() && currentEpIndex < episodes.size - 1) {
            playEpisodeByIndex(currentEpIndex + 1)
        } else if (!nextEpisodeUrl.isNullOrBlank()) {
            val target = nextEpisodeUrl!!
            nextEpisodeUrl = null
            currentUrl = target
            webViewRef?.loadUrl(target)
        } else if (aniChanMatch != null) {
            val id = aniChanMatch.groupValues[1]
            val slug = aniChanMatch.groupValues[2]
            val epNum = aniChanMatch.groupValues[3].toIntOrNull() ?: 1
            val nextUrl = "https://anichan.to/anime/$id/$slug/${epNum + 1}"
            currentUrl = nextUrl
            webViewRef?.loadUrl(nextUrl)

            // Update history for AniChan next ep
            try {
                val historyItem = WatchHistoryItem(
                    id = "https://anichan.to/anime/$id/$slug",
                    title = playingState.title.substringBefore(" - ").takeIf { it.isNotBlank() } ?: playingState.title,
                    coverUrl = "",
                    currentUrl = nextUrl,
                    isSeries = true,
                    isAnime = true,
                    season = 1,
                    episodeNumber = epNum + 1,
                    episodeTitle = "Episódio ${epNum + 1}"
                )
                WatchHistoryManager.saveProgress(context, historyItem)
            } catch (_: Exception) {}
        } else {
            webViewRef?.evaluateJavascript(
                "if (window.playNextEpisode) window.playNextEpisode();",
                null
            )
        }
    }

    fun playPrevEpisode() {
        if (episodes.isNotEmpty() && currentEpIndex > 0) {
            playEpisodeByIndex(currentEpIndex - 1)
        } else if (!prevEpisodeUrl.isNullOrBlank()) {
            val target = prevEpisodeUrl!!
            prevEpisodeUrl = null
            currentUrl = target
            webViewRef?.loadUrl(target)
        } else if (aniChanMatch != null) {
            val id = aniChanMatch.groupValues[1]
            val slug = aniChanMatch.groupValues[2]
            val epNum = aniChanMatch.groupValues[3].toIntOrNull() ?: 1
            if (epNum > 1) {
                val prevUrl = "https://anichan.to/anime/$id/$slug/${epNum - 1}"
                currentUrl = prevUrl
                webViewRef?.loadUrl(prevUrl)

                // Update history for AniChan prev ep
                try {
                    val historyItem = WatchHistoryItem(
                        id = "https://anichan.to/anime/$id/$slug",
                        title = playingState.title.substringBefore(" - ").takeIf { it.isNotBlank() } ?: playingState.title,
                        coverUrl = "",
                        currentUrl = prevUrl,
                        isSeries = true,
                        isAnime = true,
                        season = 1,
                        episodeNumber = epNum - 1,
                        episodeTitle = "Episódio ${epNum - 1}"
                    )
                    WatchHistoryManager.saveProgress(context, historyItem)
                } catch (_: Exception) {}
            }
        } else {
            webViewRef?.evaluateJavascript(
                "if (window.playPrevEpisode) window.playPrevEpisode();",
                null
            )
        }
    }

    // Register Volume Up and Volume Down to change episodes
    LaunchedEffect(currentEpIndex, episodes, nextEpisodeUrl, prevEpisodeUrl, aniChanMatch, isSeriesEpisode) {
        if (isSeriesEpisode) {
            com.example.MainActivity.onNextEpisodeCallback = {
                val nextNum = if (episodes.isNotEmpty() && currentEpIndex < episodes.size - 1) {
                    episodes[currentEpIndex + 1].number
                } else if (aniChanMatch != null) {
                    (aniChanMatch.groupValues[3].toIntOrNull() ?: 1) + 1
                } else null

                val msg = if (nextNum != null) "⏭️ Próximo Episódio: Ep $nextNum" else "⏭️ Próximo Episódio"
                com.example.MainActivity.seekOsdMessage = msg
                playNextEpisode()
                coroutineScope.launch {
                    delay(1500)
                    if (com.example.MainActivity.seekOsdMessage == msg) {
                        com.example.MainActivity.seekOsdMessage = null
                    }
                }
            }
            com.example.MainActivity.onPrevEpisodeCallback = {
                val prevNum = if (episodes.isNotEmpty() && currentEpIndex > 0) {
                    episodes[currentEpIndex - 1].number
                } else if (aniChanMatch != null) {
                    (aniChanMatch.groupValues[3].toIntOrNull() ?: 1) - 1
                } else null

                val msg = if (prevNum != null) "⏮️ Episódio Anterior: Ep $prevNum" else "⏮️ Episódio Anterior"
                com.example.MainActivity.seekOsdMessage = msg
                playPrevEpisode()
                coroutineScope.launch {
                    delay(1500)
                    if (com.example.MainActivity.seekOsdMessage == msg) {
                        com.example.MainActivity.seekOsdMessage = null
                    }
                }
            }
        } else {
            com.example.MainActivity.onNextEpisodeCallback = null
            com.example.MainActivity.onPrevEpisodeCallback = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            com.example.MainActivity.onNextEpisodeCallback = null
            com.example.MainActivity.onPrevEpisodeCallback = null
        }
    }

    fun seekVideo(seconds: Int) {
        val wv = webViewRef ?: return
        val msg = if (seconds < 0) "⏪ ${seconds}s" else "⏩ +${seconds}s"
        com.example.MainActivity.seekOsdMessage = msg
        coroutineScope.launch {
            delay(1300)
            if (com.example.MainActivity.seekOsdMessage == msg) {
                com.example.MainActivity.seekOsdMessage = null
            }
        }
        wv.evaluateJavascript(
            """
            (function() {
                var delta = $seconds;
                document.querySelectorAll('video').forEach(function(v) {
                    try {
                        v.currentTime = Math.max(0, Math.min(v.duration || 999999, v.currentTime + delta));
                    } catch(e) {}
                });
                document.querySelectorAll('iframe').forEach(function(ifr) {
                    try {
                        ifr.contentWindow.postMessage(JSON.stringify({ event: 'command', func: 'seekBy', args: [delta] }), '*');
                        ifr.contentWindow.postMessage({ type: 'seek', seconds: delta }, '*');
                    } catch(e) {}
                });
            })();
            """.trimIndent(),
            null
        )
    }

    fun dispatchAccuratePlayClick(xRatio: Float, yRatio: Float) {
        val wv = webViewRef ?: return

        // 1. Android MotionEvent dispatch directly on the WebView View
        try {
            val w = wv.width
            val h = wv.height
            if (w > 0 && h > 0) {
                val targetPxX = w * xRatio
                val targetPxY = h * yRatio
                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, targetPxX, targetPxY, 0)
                wv.dispatchTouchEvent(down)
                down.recycle()
                val up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, targetPxX, targetPxY, 0)
                wv.dispatchTouchEvent(up)
                up.recycle()
            }
        } catch (_: Exception) {}

        // 2. Synthesized pointerdown/mousedown/click via JavaScript targeting calibrated point
        wv.evaluateJavascript(
            """
            (function() {
                try {
                    var xRatio = $xRatio;
                    var yRatio = $yRatio;
                    var targetX = Math.round(window.innerWidth * xRatio);
                    var targetY = Math.round(window.innerHeight * yRatio);

                    // Ensure Dublado is selected first if available
                    var dubladoBtn = document.querySelector('.dublado-btn, [data-audio="Dublado"], .btn-audio[data-type="dublado"], .servers a[href*="dublado"]');
                    if (dubladoBtn && !dubladoBtn.classList.contains('active')) {
                        try { dubladoBtn.click(); } catch(e){}
                    }

                    // Direct video play if present
                    var vids = document.querySelectorAll('video');
                    var played = false;
                    vids.forEach(function(vid) {
                        try {
                            vid.muted = false;
                            var p = vid.play();
                            if (p && p.catch) {
                                p.catch(function() {
                                    vid.muted = true;
                                    vid.play();
                                });
                            }
                            played = true;
                        } catch(e) {}
                    });

                    // Target calibrated element at (targetX, targetY)
                    var el = document.elementFromPoint(targetX, targetY);
                    if (el) {
                        var opts = {
                            bubbles: true,
                            cancelable: true,
                            view: window,
                            clientX: targetX,
                            clientY: targetY,
                            screenX: targetX,
                            screenY: targetY
                        };
                        try { el.dispatchEvent(new PointerEvent('pointerdown', opts)); } catch(e){}
                        try { el.dispatchEvent(new MouseEvent('mousedown', opts)); } catch(e){}
                        try { el.dispatchEvent(new PointerEvent('pointerup', opts)); } catch(e){}
                        try { el.dispatchEvent(new MouseEvent('mouseup', opts)); } catch(e){}
                        try { el.dispatchEvent(new MouseEvent('click', opts)); } catch(e){}
                        if (typeof el.click === 'function') {
                            try { el.click(); } catch(e) {}
                        }
                    }

                    // Fallback to query known play button selectors
                    if (!played) {
                        var btns = document.querySelectorAll(
                            '.vjs-big-play-button, .captcha_l, .play-btn, .btn-play, #play, .play, ' +
                            '.jw-display-icon-container, button[aria-label*="Play"], button[title*="Play"], ' +
                            '.plyr__control--overlaid, .player-area .button, #view .button'
                        );
                        btns.forEach(function(b) { try { b.click(); } catch(e){} });
                    }

                    // Send postMessage to iframes
                    document.querySelectorAll('iframe').forEach(function(ifr) {
                        try {
                            ifr.contentWindow.postMessage('{"event":"command","func":"playVideo","args":""}', '*');
                            ifr.contentWindow.postMessage('play', '*');
                        } catch(e) {}
                    });

                    // Focus player container
                    var pEl = document.querySelector('video, iframe.player, iframe, #view, .player-area');
                    if (pEl) {
                        pEl.setAttribute('tabindex', '0');
                        try { pEl.focus(); } catch(e){}
                    }
                } catch(err) {}
            })();
            """.trimIndent(),
            null
        )
    }

    fun forcePlayVideo() {
        dispatchAccuratePlayClick(focusXRatio, focusYRatio)
    }

    fun testSimulateClick(xRatio: Float, yRatio: Float) {
        testClickIndicatorTrigger = System.currentTimeMillis()
        dispatchAccuratePlayClick(xRatio, yRatio)
        val msg = "🎯 Testando clique em ${(xRatio * 100).toInt()}% x ${(yRatio * 100).toInt()}%"
        com.example.MainActivity.seekOsdMessage = msg
        coroutineScope.launch {
            delay(1500)
            if (com.example.MainActivity.seekOsdMessage == msg) {
                com.example.MainActivity.seekOsdMessage = null
            }
        }
    }

    // Calibration Mode key events & remote synchronization
    LaunchedEffect(isCalibrationOpen, focusXRatio, focusYRatio) {
        com.example.MainActivity.isCalibrationModeActive = isCalibrationOpen
        if (isCalibrationOpen) {
            com.example.MainActivity.onMoveCalibrationReticle = { dx, dy ->
                focusXRatio = (focusXRatio + dx).coerceIn(0.05f, 0.95f)
                focusYRatio = (focusYRatio + dy).coerceIn(0.05f, 0.95f)
            }
            com.example.MainActivity.onTestCalibrationClick = {
                testSimulateClick(focusXRatio, focusYRatio)
            }
            com.example.MainActivity.onSaveCalibration = {
                FocusCalibrationManager.saveCalibration(context, focusXRatio, focusYRatio)
                val msg = "🎯 Posição salva para todos os filmes e séries!"
                com.example.MainActivity.seekOsdMessage = msg
                isCalibrationOpen = false
                coroutineScope.launch {
                    delay(2500)
                    if (com.example.MainActivity.seekOsdMessage == msg) {
                        com.example.MainActivity.seekOsdMessage = null
                    }
                }
            }
            com.example.MainActivity.onExitCalibration = {
                isCalibrationOpen = false
            }
        } else {
            com.example.MainActivity.onMoveCalibrationReticle = null
            com.example.MainActivity.onTestCalibrationClick = null
            com.example.MainActivity.onSaveCalibration = null
            com.example.MainActivity.onExitCalibration = null
        }
    }

    BackHandler(enabled = true) {
        if (showDownloadEditDialog) {
            showDownloadEditDialog = false
        } else if (showZoomSheet) {
            showZoomSheet = false
        } else if (isCalibrationOpen) {
            isCalibrationOpen = false
        } else if (showCreditsDialog) {
            showCreditsDialog = false
        } else if (showEpisodeSheet) {
            showEpisodeSheet = false
        } else if (!areControlsVisible) {
            // If controls were hidden, back button reveals controls instead of leaving screen!
            areControlsVisible = true
        } else if (customView != null) {
            customViewCallback?.onCustomViewHidden()
        } else {
            val wv = webViewRef
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else {
                onBackToPrevious()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (showCreditsDialog) {
            CreditsDialog(onDismiss = { showCreditsDialog = false })
        }

        // Main In-App Streaming WebView in Fullscreen Cinema Mode (100% Nativo sem distorção)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panX
                        translationY = panY
                    },
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    com.example.MainActivity.playerWebViewRef = this
                    val defaultUa = settings.userAgentString
                    settings.userAgentString = defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        allowFileAccess = true
                        allowContentAccess = true
                        databaseEnabled = true
                        loadsImagesAutomatically = true
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoadingPage = true
                            view?.evaluateJavascript("""
                                (function() {
                                    try {
                                        window.open = function() { return null; };
                                        window.alert = function() {};
                                        window.confirm = function() { return true; };
                                        window.prompt = function() { return null; };

                                        // Set early viewport meta tag so WebView doesn't scale to 980px desktop
                                        var meta = document.querySelector('meta[name="viewport"]');
                                        if (!meta) {
                                            meta = document.createElement('meta');
                                            meta.name = 'viewport';
                                            if (document.head) document.head.appendChild(meta);
                                        }
                                        if (meta) meta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no');

                                        if (typeof window.ipsSettings === 'undefined') {
                                            window.ipsSettings = { baseURL: 'https://www.pobreflixtv.delivery/' };
                                        } else {
                                            window.ipsSettings.baseURL = 'https://www.pobreflixtv.delivery/';
                                        }
                                        if (!window._origFetch) {
                                            window._origFetch = window.fetch;
                                            window.fetch = function(input, init) {
                                                var urlStr = (typeof input === 'string') ? input : (input ? input.url : '');
                                                if (urlStr && urlStr.indexOf('pobreflixtv.city') !== -1) {
                                                    input = urlStr.replace('pobreflixtv.city', 'pobreflixtv.delivery');
                                                }
                                                return window._origFetch.apply(this, arguments).then(function(response) {
                                                    var clone = response.clone();
                                                    return clone.json().then(function(json) {
                                                        if (json && json.redirect && typeof json.redirect === 'string') {
                                                            return window._origFetch(json.redirect, init);
                                                        }
                                                        return response;
                                                    }).catch(function() {
                                                        return response;
                                                    });
                                                });
                                            };
                                        }
                                    } catch(e) {}
                                })();
                            """.trimIndent(), null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoadingPage = false

                            // Schedule accurate native + JS clicks at calibrated target
                            view?.postDelayed({
                                dispatchAccuratePlayClick(focusXRatio, focusYRatio)
                            }, 800)
                            view?.postDelayed({
                                dispatchAccuratePlayClick(focusXRatio, focusYRatio)
                            }, 2000)
                            view?.postDelayed({
                                dispatchAccuratePlayClick(focusXRatio, focusYRatio)
                            }, 3500)

                            view?.evaluateJavascript("""
                                (function() {
                                    window.open = function() { return null; };
                                    document.querySelectorAll('a[target="_blank"]').forEach(function(a) {
                                        a.removeAttribute('target');
                                        a.setAttribute('target', '_self');
                                    });

                                    // 1. Force strict viewport meta tag for responsive phone, PC, and TV scaling
                                    var meta = document.querySelector('meta[name="viewport"]');
                                    if (!meta) {
                                        meta = document.createElement('meta');
                                        meta.name = 'viewport';
                                        document.head.appendChild(meta);
                                    }
                                    meta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no');

                                    // 2. Clean TV Layout: Video player displays cleanly without ads or site headers
                                    var existingStyle = document.getElementById('inapp-cinema-style');
                                    if (existingStyle) existingStyle.remove();

                                    var css = `
                                        html, body {
                                            margin: 0 !important;
                                            padding: 0 !important;
                                            background: #000000 !important;
                                            color: #FFFFFF !important;
                                        }

                                        /* Responsive video container without black overlay traps */
                                        #view, #player, #player-wrapper, .player-area, .player-container, .watch-area, [id*="player"], [class*="player"] {
                                            margin: 0 auto !important;
                                            width: 100% !important;
                                            max-width: 100% !important;
                                            display: block !important;
                                            visibility: visible !important;
                                            opacity: 1 !important;
                                        }

                                        /* Video element and iframe fill the view cleanly */
                                        iframe.player, iframe[src*="embed"], iframe[src*="http"], iframe[allowfullscreen], video {
                                            width: 100% !important;
                                            min-height: 480px !important;
                                            border: none !important;
                                            display: block !important;
                                            visibility: visible !important;
                                            opacity: 1 !important;
                                        }

                                        /* Server and Audio buttons (Dublado, Legendado, Byse) remain visible and neat */
                                        .servers, #servers, .servers-container, [class*="server"], [id*="server"],
                                        .audio-selector, .dublado-btn, .legendado-btn, .select-server, .servers-list,
                                        .btn-audio, [class*="audio"] {
                                            visibility: visible !important;
                                            opacity: 1 !important;
                                        }

                                        /* Hide only unnecessary site junk, ads, comments, and headers */
                                        header, footer, nav, .site-header, .site-footer, #ipsLayout_header, #ipsLayout_footer,
                                        .sidebar, #comments, .comments, .download-btn, .cAuthorPane {
                                            display: none !important;
                                        }
                                    `;
                                    var style = document.createElement('style');
                                    style.id = 'inapp-cinema-style';
                                    style.innerHTML = css;
                                    if (document.head) document.head.appendChild(style);

                                    function triggerPlay() {
                                        // 1. Auto select Dublado audio if present and not yet selected
                                        var dubladoBtn = document.querySelector('.dublado-btn, [data-audio="Dublado"], .btn-audio[data-type="dublado"], .servers a[href*="dublado"]');
                                        if (dubladoBtn && !dubladoBtn.classList.contains('active')) {
                                            try { dubladoBtn.click(); } catch(e){}
                                        }

                                        // 2. Remove focus and tabindex from audio buttons so TV remote focus doesn't trap on DUBLADO
                                        document.querySelectorAll('.dublado-btn, .legendado-btn, .select-server, [data-server], .servers a').forEach(function(el) {
                                            el.setAttribute('tabindex', '-1');
                                            try { el.blur(); } catch(e){}
                                        });

                                        // 3. Play HTML5 video if present
                                        var played = false;
                                        document.querySelectorAll('video').forEach(function(vid) {
                                            try {
                                                vid.muted = false;
                                                var p = vid.play();
                                                if (p && p.catch) {
                                                    p.catch(function() {
                                                        vid.muted = true;
                                                        vid.play();
                                                    });
                                                }
                                                played = true;
                                            } catch(e) {}
                                        });

                                        // 4. Click at calibrated target coordinates
                                        var targetX = Math.round(window.innerWidth * $focusXRatio);
                                        var targetY = Math.round(window.innerHeight * $focusYRatio);
                                        var el = document.elementFromPoint(targetX, targetY);
                                        if (el) {
                                            var opts = {
                                                bubbles: true,
                                                cancelable: true,
                                                view: window,
                                                clientX: targetX,
                                                clientY: targetY,
                                                screenX: targetX,
                                                screenY: targetY
                                            };
                                            try { el.dispatchEvent(new PointerEvent('pointerdown', opts)); } catch(e){}
                                            try { el.dispatchEvent(new MouseEvent('mousedown', opts)); } catch(e){}
                                            try { el.dispatchEvent(new PointerEvent('pointerup', opts)); } catch(e){}
                                            try { el.dispatchEvent(new MouseEvent('mouseup', opts)); } catch(e){}
                                            try { el.dispatchEvent(new MouseEvent('click', opts)); } catch(e){}
                                            if (typeof el.click === 'function') {
                                                try { el.click(); } catch(e){}
                                            }
                                        }

                                        // 5. Click play button if video hasn't started
                                        if (!played) {
                                            var playBtns = document.querySelectorAll(
                                                '.vjs-big-play-button, .captcha_l, .play-btn, .btn-play, #play, .play, ' +
                                                '.jw-display-icon-container, button[aria-label*="Play"], button[title*="Play"], ' +
                                                '.plyr__control--overlaid, .player-area .button, #view .button'
                                            );
                                            playBtns.forEach(function(b) { try { b.click(); } catch(e){} });
                                        }

                                        // 6. Send play command to iframes
                                        document.querySelectorAll('iframe').forEach(function(ifr) {
                                            try {
                                                ifr.contentWindow.postMessage('{"event":"command","func":"playVideo","args":""}', '*');
                                                ifr.contentWindow.postMessage('play', '*');
                                            } catch(e) {}
                                        });

                                        // 7. Transfer remote focus directly to the video/player
                                        var pEl = document.querySelector('video, iframe.player, iframe, #view, .player-area');
                                        if (pEl) {
                                            pEl.setAttribute('tabindex', '0');
                                            try { pEl.focus(); } catch(e){}
                                        }

                                        // 8. Clean up ad overlays or click traps safely without removing player
                                        document.querySelectorAll('.ads, .banner, .alert-carousel, #adb, .floating-ads').forEach(function(adEl) {
                                            if (!adEl.querySelector('video, iframe') && !adEl.closest('#view, .player-area, #player')) {
                                                try { adEl.remove(); } catch(e){}
                                            }
                                        });
                                    }
                                    triggerPlay();
                                    setTimeout(triggerPlay, 800);
                                    setTimeout(triggerPlay, 2000);
                                    setTimeout(triggerPlay, 3500);
                                })();
                            """.trimIndent(), null)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val uri = request?.url ?: return false
                            val scheme = uri.scheme?.lowercase() ?: ""
                            val host = uri.host?.lowercase() ?: ""
                            if (scheme != "http" && scheme != "https") {
                                return true
                            }
                            val adKeywords = listOf(
                                "googlesyndication", "doubleclick", "googleadservices",
                                "popads", "propellerads", "popcash", "exoclick",
                                "onclickads", "adserver", "ad-delivery", "taboola",
                                "1xbet", "blaze.com", "bet365", "aposta", "casino",
                                "adsterra", "monetag", "trafficjunky", "adcash", "yllix",
                                "oldnessdernly.qpon"
                            )
                            if (adKeywords.any { host.contains(it) || uri.toString().contains(it) }) {
                                return true
                            }
                            return false
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val u = request?.url.toString().lowercase()
                            val adKeywords = listOf(
                                "googlesyndication", "doubleclick", "googleadservices",
                                "popads", "propellerads", "popcash", "exoclick",
                                "onclickads", "adserver", "ad-delivery", "taboola",
                                "1xbet", "blaze.com", "bet365", "aposta", "casino",
                                "adsterra", "monetag", "trafficjunky", "adcash", "yllix",
                                "oldnessdernly.qpon"
                            )
                            if (adKeywords.any { u.contains(it) }) {
                                return WebResourceResponse(
                                    "text/plain",
                                    "utf-8",
                                    ByteArrayInputStream("".toByteArray())
                                )
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            return false
                        }

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            super.onShowCustomView(view, callback)
                            customView = view
                            customViewCallback = callback
                        }

                        override fun onHideCustomView() {
                            super.onHideCustomView()
                            customView = null
                            customViewCallback = null
                        }
                    }
                    loadUrl(currentUrl)
                }
            },
            update = {
                webViewRef = it
                com.example.MainActivity.playerWebViewRef = it
            }
        )
        }

        // Fullscreen overlay view
        if (customView != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        customView?.let { v ->
                            (v.parent as? ViewGroup)?.removeView(v)
                            addView(v)
                        }
                    }
                }
            )
        }

        // Central On-Screen Display (OSD) feedback for Seek (TV feedback)
        val activeOsd = com.example.MainActivity.seekOsdMessage
        AnimatedVisibility(
            visible = activeOsd != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color(0xF012131A),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, Color(0xFFE50914)),
                shadowElevation = 14.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = activeOsd ?: "",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 1. TOP HEADER BAR & SERVER SELECTOR (Animated visibility with hide/show)
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Surface(
                    color = Color(0xDD0E0F14),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            TvIconButton(
                                icon = Icons.Default.ArrowBack,
                                contentDescription = "Voltar",
                                onClick = onBackToPrevious
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (currentEpisode != null) {
                                        "Ep ${currentEpisode.number}: ${currentEpisode.title}"
                                    } else if (aniChanMatch != null) {
                                        val num = aniChanMatch.groupValues[3]
                                        "Episódio $num • AniChan"
                                    } else if (playingState.title.isNotBlank()) {
                                        playingState.title
                                    } else {
                                        "A2 Miguel • Reproduzindo"
                                    },
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (episodes.isNotEmpty()) {
                                        "Episódio ${currentEpIndex + 1} de ${episodes.size}"
                                    } else {
                                        "A2 Miguel • Servidor Normal (Sem Anúncios)"
                                    },
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Baixar & Editar Button (PC e TV)
                            TvControlPill(
                                icon = Icons.Default.Download,
                                label = "📥 Baixar / Editar",
                                color = Color(0xFF2E7D32),
                                onClick = {
                                    extractMediaUrlFromWebView()
                                    showDownloadEditDialog = true
                                }
                            )

                            // Zoom da Tela Button
                            TvControlPill(
                                icon = Icons.Default.ZoomIn,
                                label = "🔍 Zoom da Tela",
                                color = Color(0xFF0077B6),
                                onClick = { showZoomSheet = true }
                            )

                            // Primary "Esconder Opções" button in header bar
                            TvControlPill(
                                icon = Icons.Default.VisibilityOff,
                                label = "Esconder Opções",
                                color = Color(0xFFE50914),
                                onClick = { areControlsVisible = false }
                            )

                            // Credits Button
                            TvControlPill(
                                icon = Icons.Default.Info,
                                label = "Créditos",
                                color = Color(0xFF22242B),
                                onClick = { showCreditsDialog = true }
                            )

                            // In-App Episode Selector Button
                            if (isSeriesEpisode) {
                                TvControlPill(
                                    icon = Icons.Default.FormatListNumbered,
                                    label = "Mudar Ep",
                                    color = Color(0xFF6C5CE7),
                                    onClick = { showEpisodeSheet = true }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Loading bar
        if (isLoadingPage && areControlsVisible) {
            LinearProgressIndicator(
                color = Color(0xFFE50914),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            )
        }

        // 2. BOTTOM CONTROLS BAR (Animated visibility with hide/show)
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(14.dp)
        ) {
            Surface(
                color = Color(0xEE14151C),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF2E313D)),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Force Start Button
                    TvControlPill(
                        icon = Icons.Default.PlayArrow,
                        label = "Tocar Vídeo",
                        color = Color(0xFFE50914),
                        onClick = { forcePlayVideo() }
                    )

                    // Baixar / Editar Button
                    TvControlPill(
                        icon = Icons.Default.Download,
                        label = "📥 Baixar",
                        color = Color(0xFF2E7D32),
                        onClick = {
                            extractMediaUrlFromWebView()
                            showDownloadEditDialog = true
                        }
                    )

                    // Zoom da Tela Button
                    TvControlPill(
                        icon = Icons.Default.ZoomIn,
                        label = "🔍 Zoom",
                        color = Color(0xFF0077B6),
                        onClick = { showZoomSheet = true }
                    )



                    // Seek -10s for TV Box Remote
                    TvControlPill(
                        icon = Icons.Default.Replay10,
                        label = "-10s",
                        onClick = { seekVideo(-10) }
                    )

                    // Seek +10s for TV Box Remote
                    TvControlPill(
                        icon = Icons.Default.Forward10,
                        label = "+10s",
                        onClick = { seekVideo(10) }
                    )

                    // Previous Episode
                    if (isSeriesEpisode) {
                        TvControlPill(
                            icon = Icons.Default.SkipPrevious,
                            label = "Ep Ant.",
                            onClick = { playPrevEpisode() }
                        )
                    }

                    // Next Episode
                    if (isSeriesEpisode) {
                        TvControlPill(
                            icon = Icons.Default.SkipNext,
                            label = "Próx Ep",
                            color = Color(0xFFE50914),
                            onClick = { playNextEpisode() }
                        )
                    }

                    // Native Episode List Modal Button
                    if (isSeriesEpisode) {
                        TvControlPill(
                            icon = Icons.Default.FormatListNumbered,
                            label = "Lista Eps",
                            color = Color(0xFF2A2D3A),
                            onClick = { showEpisodeSheet = true }
                        )
                    }

                    // Dedicated Button to Hide Controls inside bottom bar
                    TvControlPill(
                        icon = Icons.Default.VisibilityOff,
                        label = "Esconder",
                        color = Color(0xFF1E2028),
                        onClick = { areControlsVisible = false }
                    )

                    IconButton(
                        onClick = { areControlsVisible = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Ocultar Controles",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }



        // NATIVE IN-APP EPISODE SELECTOR DRAWER
        AnimatedVisibility(
            visible = showEpisodeSheet,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color(0xF514151C),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = BorderStroke(1.dp, Color(0xFF2E313D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Mudar Episódio",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (episodes.isNotEmpty()) {
                                    "${episodes.size} episódios na temporada"
                                } else if (aniChanMatch != null) {
                                    "Anime AniChan • Episódio Atual: ${aniChanMatch.groupValues[3]}"
                                } else {
                                    "Selecione o próximo episódio ou navegue com o controle"
                                },
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        TvIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Fechar",
                            onClick = { showEpisodeSheet = false }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TvButton(
                            text = "Ep Anterior",
                            icon = Icons.Default.SkipPrevious,
                            containerColor = Color(0xFF22242B),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                playPrevEpisode()
                                showEpisodeSheet = false
                            }
                        )
                        TvButton(
                            text = "Próximo Ep",
                            icon = Icons.Default.SkipNext,
                            containerColor = Color(0xFFE50914),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                playNextEpisode()
                                showEpisodeSheet = false
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (episodes.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(episodes) { index, ep ->
                                val isSelected = index == currentEpIndex
                                var isFocused by remember { mutableStateOf(false) }
                                Surface(
                                    color = if (isSelected) Color(0xFFE50914) else if (isFocused) Color(0xFF2E3345) else Color(0xFF1E2028),
                                    shape = RoundedCornerShape(12.dp),
                                    border = if (isFocused) BorderStroke(3.dp, Color.White) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .clickable { playEpisodeByIndex(index) }
                                        .focusable()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(
                                                        if (isSelected) Color.White.copy(alpha = 0.2f) else Color(0xFF2C2F3B),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${ep.number}",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = ep.title,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Temporada ${ep.season} • Episódio ${ep.number}",
                                                    color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color.Gray,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Text(
                                                text = "Assistindo",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                modifier = Modifier
                                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (aniChanMatch != null) {
                        val currentNum = aniChanMatch.groupValues[3].toIntOrNull() ?: 1
                        val id = aniChanMatch.groupValues[1]
                        val slug = aniChanMatch.groupValues[2]
                        Text("Pular para Episódio:", color = Color.LightGray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            val totalEps = (1..50).toList()
                            val columns = 5
                            val rows = (totalEps.size + columns - 1) / columns
                            items(rows) { rowIndex ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (col in 0 until columns) {
                                        val idx = rowIndex * columns + col
                                        if (idx < totalEps.size) {
                                            val num = totalEps[idx]
                                            val isCur = num == currentNum
                                            var isFocused by remember { mutableStateOf(false) }
                                            Surface(
                                                color = if (isCur) Color(0xFF6C5CE7) else if (isFocused) Color(0xFF2E3345) else Color(0xFF1E2028),
                                                shape = RoundedCornerShape(8.dp),
                                                border = if (isFocused) BorderStroke(2.dp, Color.White) else null,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .onFocusChanged { isFocused = it.isFocused }
                                                    .clickable {
                                                        val targetUrl = "https://anichan.to/anime/$id/$slug/$num"
                                                        currentUrl = targetUrl
                                                        webViewRef?.loadUrl(targetUrl)
                                                        showEpisodeSheet = false
                                                    }
                                                    .focusable()
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(vertical = 12.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "EP $num",
                                                        color = Color.White,
                                                        fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // FOCUS CALIBRATION OVERLAY (Active when user clicks Calibrar Foco or uses remote)
        if (isCalibrationOpen) {
            FocusCalibrationOverlay(
                focusX = focusXRatio,
                focusY = focusYRatio,
                onMove = { dx, dy ->
                    focusXRatio = (focusXRatio + dx).coerceIn(0.05f, 0.95f)
                    focusYRatio = (focusYRatio + dy).coerceIn(0.05f, 0.95f)
                },
                onTestClick = {
                    testSimulateClick(focusXRatio, focusYRatio)
                },
                onSave = {
                    FocusCalibrationManager.saveCalibration(context, focusXRatio, focusYRatio)
                    val msg = "🎯 Posição salva para todos os filmes e séries!"
                    com.example.MainActivity.seekOsdMessage = msg
                    isCalibrationOpen = false
                    coroutineScope.launch {
                        delay(2500)
                        if (com.example.MainActivity.seekOsdMessage == msg) {
                            com.example.MainActivity.seekOsdMessage = null
                        }
                    }
                },
                onReset = {
                    FocusCalibrationManager.resetCalibration(context)
                    focusXRatio = FocusCalibrationManager.DEFAULT_FOCUS_X
                    focusYRatio = FocusCalibrationManager.DEFAULT_FOCUS_Y
                    val msg = "🔄 Posição restaurada para o centro padrão"
                    com.example.MainActivity.seekOsdMessage = msg
                    coroutineScope.launch {
                        delay(1800)
                        if (com.example.MainActivity.seekOsdMessage == msg) {
                            com.example.MainActivity.seekOsdMessage = null
                        }
                    }
                },
                onClose = { isCalibrationOpen = false },
                testTrigger = testClickIndicatorTrigger
            )
        }

        // ZOOM AND DISPLAY ENQUADRAMENTO MODAL
        AnimatedVisibility(
            visible = showZoomSheet,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color(0xF714151C),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = BorderStroke(1.dp, Color(0xFF2E313D)),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = null,
                                tint = Color(0xFF00F5D4),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "🔍 Zoom e Enquadramento da Tela",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Ajuste o tamanho do vídeo para preencher a TV perfeitamente sem barras",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        TvIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Fechar",
                            onClick = { showZoomSheet = false }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Preset buttons row
                    Text(
                        text = "Modos Rápidos de Zoom:",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ZoomPresetButton(
                            title = "Normal",
                            subtitle = "100%",
                            isSelected = zoomPreset == ScreenZoomManager.PRESET_NORMAL,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                zoomScale = 1.00f
                                panX = 0f
                                panY = 0f
                                zoomPreset = ScreenZoomManager.PRESET_NORMAL
                                ScreenZoomManager.saveZoomConfig(context, 1.00f, 0f, 0f, ScreenZoomManager.PRESET_NORMAL)
                            }
                        )
                        ZoomPresetButton(
                            title = "Ajustar",
                            subtitle = "115%",
                            isSelected = zoomPreset == ScreenZoomManager.PRESET_FIT,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                zoomScale = 1.15f
                                panX = 0f
                                panY = 0f
                                zoomPreset = ScreenZoomManager.PRESET_FIT
                                ScreenZoomManager.saveZoomConfig(context, 1.15f, 0f, 0f, ScreenZoomManager.PRESET_FIT)
                            }
                        )
                        ZoomPresetButton(
                            title = "Cinema",
                            subtitle = "130%",
                            isSelected = zoomPreset == ScreenZoomManager.PRESET_CINEMA,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                zoomScale = 1.30f
                                panX = 0f
                                panY = 0f
                                zoomPreset = ScreenZoomManager.PRESET_CINEMA
                                ScreenZoomManager.saveZoomConfig(context, 1.30f, 0f, 0f, ScreenZoomManager.PRESET_CINEMA)
                            }
                        )
                        ZoomPresetButton(
                            title = "Tela Cheia",
                            subtitle = "150%",
                            isSelected = zoomPreset == ScreenZoomManager.PRESET_FULL,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                zoomScale = 1.50f
                                panX = 0f
                                panY = 0f
                                zoomPreset = ScreenZoomManager.PRESET_FULL
                                ScreenZoomManager.saveZoomConfig(context, 1.50f, 0f, 0f, ScreenZoomManager.PRESET_FULL)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Fine-tuning row: Stepper for scale + Pan arrows
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Zoom scale stepper
                        Surface(
                            color = Color(0xFF1E2028),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF2E313D)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Aproximação: ${(zoomScale * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TvIconButton(
                                        icon = Icons.Default.Remove,
                                        contentDescription = "Diminuir Zoom",
                                        onClick = {
                                            zoomScale = (zoomScale - 0.05f).coerceIn(1.0f, 2.5f)
                                            zoomPreset = ScreenZoomManager.PRESET_CUSTOM
                                            ScreenZoomManager.saveZoomConfig(context, zoomScale, panX, panY, zoomPreset)
                                        }
                                    )
                                    TvIconButton(
                                        icon = Icons.Default.Add,
                                        contentDescription = "Aumentar Zoom",
                                        onClick = {
                                            zoomScale = (zoomScale + 0.05f).coerceIn(1.0f, 2.5f)
                                            zoomPreset = ScreenZoomManager.PRESET_CUSTOM
                                            ScreenZoomManager.saveZoomConfig(context, zoomScale, panX, panY, zoomPreset)
                                        }
                                    )
                                }
                            }
                        }

                        // Pan controls
                        Surface(
                            color = Color(0xFF1E2028),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF2E313D)),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Enquadrar:",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TvIconButton(
                                        icon = Icons.Default.ArrowBack,
                                        contentDescription = "Esquerda",
                                        onClick = {
                                            panX = (panX - 25f).coerceIn(-400f, 400f)
                                            zoomPreset = ScreenZoomManager.PRESET_CUSTOM
                                            ScreenZoomManager.saveZoomConfig(context, zoomScale, panX, panY, zoomPreset)
                                        }
                                    )
                                    TvIconButton(
                                        icon = Icons.Default.ArrowUpward,
                                        contentDescription = "Cima",
                                        onClick = {
                                            panY = (panY - 25f).coerceIn(-300f, 300f)
                                            zoomPreset = ScreenZoomManager.PRESET_CUSTOM
                                            ScreenZoomManager.saveZoomConfig(context, zoomScale, panX, panY, zoomPreset)
                                        }
                                    )
                                    TvIconButton(
                                        icon = Icons.Default.ArrowDownward,
                                        contentDescription = "Baixo",
                                        onClick = {
                                            panY = (panY + 25f).coerceIn(-300f, 300f)
                                            zoomPreset = ScreenZoomManager.PRESET_CUSTOM
                                            ScreenZoomManager.saveZoomConfig(context, zoomScale, panX, panY, zoomPreset)
                                        }
                                    )
                                    TvIconButton(
                                        icon = Icons.Default.ArrowForward,
                                        contentDescription = "Direita",
                                        onClick = {
                                            panX = (panX + 25f).coerceIn(-400f, 400f)
                                            zoomPreset = ScreenZoomManager.PRESET_CUSTOM
                                            ScreenZoomManager.saveZoomConfig(context, zoomScale, panX, panY, zoomPreset)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvButton(
                            text = "Salvar Padrão",
                            icon = Icons.Default.Check,
                            containerColor = Color(0xFF2ECC71),
                            modifier = Modifier.weight(1.3f),
                            onClick = {
                                ScreenZoomManager.saveZoomConfig(context, zoomScale, panX, panY, zoomPreset)
                                val msg = "💾 Zoom salvo para todos os filmes e séries!"
                                com.example.MainActivity.seekOsdMessage = msg
                                showZoomSheet = false
                                coroutineScope.launch {
                                    delay(2000)
                                    if (com.example.MainActivity.seekOsdMessage == msg) {
                                        com.example.MainActivity.seekOsdMessage = null
                                    }
                                }
                            }
                        )

                        TvButton(
                            text = "Redefinir (100%)",
                            icon = Icons.Default.RestartAlt,
                            containerColor = Color(0xFF333742),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val def = ScreenZoomManager.resetZoom(context)
                                zoomScale = def.scale
                                panX = def.panX
                                panY = def.panY
                                zoomPreset = def.preset
                            }
                        )

                        TvButton(
                            text = "Fechar",
                            icon = Icons.Default.Close,
                            containerColor = Color(0xFF22242B),
                            modifier = Modifier.weight(0.7f),
                            onClick = { showZoomSheet = false }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "🎮 Controle da TV: Pressione a tecla Amarela ou botão Zoom no controle remoto para alternar o tamanho rapidamente.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (showDownloadEditDialog) {
            DownloadAndEditDialog(
                initialTitle = playingState.title,
                episodeNumber = currentEpisode?.number?.toString(),
                currentMediaUrl = capturedMediaUrl ?: currentUrl,
                currentZoomPreset = zoomPreset,
                onApplyZoomPreset = { preset ->
                    val next = ScreenZoomManager.applyPreset(context, preset)
                    zoomScale = next.scale
                    panX = next.panX
                    panY = next.panY
                    zoomPreset = next.preset
                },
                onOpenFocusCalibration = {
                    isCalibrationOpen = true
                },
                onTriggerForcePlay = {
                    forcePlayVideo()
                },
                onDismissRequest = {
                    showDownloadEditDialog = false
                }
            )
        }
    }
}

@Composable
fun ZoomPresetButton(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        color = if (isSelected) Color(0xFFE50914) else if (isFocused) Color(0xFF2E3345) else Color(0xFF1E2028),
        shape = RoundedCornerShape(12.dp),
        border = if (isFocused) BorderStroke(3.dp, Color(0xFFFFD166)) else if (isSelected) BorderStroke(2.dp, Color.White) else BorderStroke(1.dp, Color(0xFF2E313D)),
        modifier = modifier
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun FocusCalibrationOverlay(
    focusX: Float,
    focusY: Float,
    onMove: (deltaX: Float, deltaY: Float) -> Unit,
    onTestClick: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    testTrigger: Long,
    modifier: Modifier = Modifier
) {
    val isTesting = remember(testTrigger) {
        testTrigger > 0 && System.currentTimeMillis() - testTrigger < 700
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val centerX = widthPx * focusX
        val centerY = heightPx * focusY

        // Canvas drawing the Reticle / Crosshair directly on screen
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(centerX, centerY)
            val outerRadius = 38.dp.toPx()
            val innerRadius = 14.dp.toPx()
            val crosshairLen = 22.dp.toPx()
            val reticleColor = if (isTesting) Color(0xFFFFD166) else Color(0xFF00F5D4)
            val glowColor = if (isTesting) Color(0x66FFD166) else Color(0x3300F5D4)

            // Outer pulse glow
            drawCircle(
                color = glowColor,
                radius = outerRadius + 8.dp.toPx(),
                center = center
            )

            // Outer circle
            drawCircle(
                color = reticleColor,
                radius = outerRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )

            // Inner circle
            drawCircle(
                color = reticleColor.copy(alpha = 0.85f),
                radius = innerRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )

            // Center targeting point
            drawCircle(
                color = if (isTesting) Color(0xFFFF3366) else Color(0xFFE50914),
                radius = 4.5.dp.toPx(),
                center = center
            )

            // 4 Crosshair lines (Top, Bottom, Left, Right)
            val strokeW = 2.dp.toPx()
            // Top line
            drawLine(
                color = reticleColor,
                start = Offset(center.x, center.y - innerRadius - 2.dp.toPx()),
                end = Offset(center.x, center.y - outerRadius - crosshairLen),
                strokeWidth = strokeW
            )
            // Bottom line
            drawLine(
                color = reticleColor,
                start = Offset(center.x, center.y + innerRadius + 2.dp.toPx()),
                end = Offset(center.x, center.y + outerRadius + crosshairLen),
                strokeWidth = strokeW
            )
            // Left line
            drawLine(
                color = reticleColor,
                start = Offset(center.x - innerRadius - 2.dp.toPx(), center.y),
                end = Offset(center.x - outerRadius - crosshairLen, center.y),
                strokeWidth = strokeW
            )
            // Right line
            drawLine(
                color = reticleColor,
                start = Offset(center.x + innerRadius + 2.dp.toPx(), center.y),
                end = Offset(center.x + outerRadius + crosshairLen, center.y),
                strokeWidth = strokeW
            )
        }

        // Live coordinate label badge floating next to reticle
        Box(
            modifier = Modifier
                .offset(
                    x = with(LocalDensity.current) { (centerX + 44.dp.toPx()).toDp().coerceIn(12.dp, maxWidth - 230.dp) },
                    y = with(LocalDensity.current) { (centerY - 20.dp.toPx()).toDp().coerceIn(70.dp, maxHeight - 120.dp) }
                )
        ) {
            Surface(
                color = Color(0xF013141C),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.5.dp, if (isTesting) Color(0xFFFFD166) else Color(0xFF00F5D4)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isTesting) "💥 CLIQUE DISPARADO" else "🎯 Alvo: X ${(focusX * 100).toInt()}% • Y ${(focusY * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Top Header Banner
        Surface(
            color = Color(0xE614151C),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            border = BorderStroke(1.dp, Color(0xFF2E313D)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.92f)
                .statusBarsPadding()
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎯 Calibração Visual do Ponto de Play na TV",
                        color = Color(0xFF00F5D4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Posicione a mira sobre o botão de Play do vídeo. Salvar aplicará para todos os filmes e séries automaticamente!",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
                TvIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Fechar Calibração",
                    onClick = onClose
                )
            }
        }

        // Bottom Controls Bar (with D-pad directional helpers and primary actions)
        Surface(
            color = Color(0xF214151C),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0xFF2E313D)),
            shadowElevation = 14.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // TV D-pad visual controls for easy click or touch
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvControlPill(
                        icon = Icons.Default.ArrowBack,
                        label = "◄ Esquerda",
                        color = Color(0xFF1E2028),
                        onClick = { onMove(-0.02f, 0f) }
                    )
                    TvControlPill(
                        icon = Icons.Default.ArrowUpward,
                        label = "▲ Subir",
                        color = Color(0xFF1E2028),
                        onClick = { onMove(0f, -0.02f) }
                    )
                    TvControlPill(
                        icon = Icons.Default.ArrowDownward,
                        label = "▼ Descer",
                        color = Color(0xFF1E2028),
                        onClick = { onMove(0f, 0.02f) }
                    )
                    TvControlPill(
                        icon = Icons.Default.ArrowForward,
                        label = "► Direita",
                        color = Color(0xFF1E2028),
                        onClick = { onMove(0.02f, 0f) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvControlPill(
                        icon = Icons.Default.PlayArrow,
                        label = "Testar Clique (OK)",
                        color = Color(0xFF0077B6),
                        onClick = onTestClick
                    )
                    TvControlPill(
                        icon = Icons.Default.Check,
                        label = "Salvar Posição",
                        color = Color(0xFF2ECC71),
                        onClick = onSave
                    )
                    TvControlPill(
                        icon = Icons.Default.RestartAlt,
                        label = "Centro Padrão",
                        color = Color(0xFF333742),
                        onClick = onReset
                    )
                    TvControlPill(
                        icon = Icons.Default.Close,
                        label = "Sair",
                        color = Color(0xFF22242B),
                        onClick = onClose
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "🎮 Controle da TV: Setas movem a mira • Tecla OK testa o clique • Tecla Verde salva",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun TvControlPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = Color(0xFF22242B)
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        color = if (isFocused) Color(0xFFE50914) else color,
        shape = RoundedCornerShape(12.dp),
        border = if (isFocused) BorderStroke(3.dp, Color(0xFFFFD166)) else BorderStroke(1.dp, Color(0xFF2E313D)),
        shadowElevation = if (isFocused) 8.dp else 2.dp,
        modifier = Modifier
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        color = if (isFocused) Color(0xFFE50914) else Color(0xFF22242B),
        shape = CircleShape,
        border = if (isFocused) BorderStroke(2.5.dp, Color(0xFFFFD166)) else null,
        modifier = Modifier
            .size(36.dp)
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun TvButton(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        color = if (isFocused) Color(0xFFE50914) else containerColor,
        shape = RoundedCornerShape(12.dp),
        border = if (isFocused) BorderStroke(3.dp, Color(0xFFFFD166)) else null,
        modifier = modifier
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

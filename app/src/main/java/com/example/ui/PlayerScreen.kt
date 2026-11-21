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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.AppState
import com.example.model.Episode
import com.example.model.FocusCalibrationManager
import com.example.model.ScreenZoomManager
import com.example.model.WatchHistoryItem
import com.example.model.WatchHistoryManager
import com.example.network.AdBlockManager
import com.example.network.PlayerServer
import com.example.network.SearchService
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun PlayerScreen(
    playingState: AppState.Playing,
    watchedEpisodeKeys: Set<String> = emptySet(),
    onHistoryProgressSaved: (WatchHistoryItem) -> Unit = {},
    onPreferencesChanged: (zoomPreset: String, zoomScale: Float, focusX: Float, focusY: Float) -> Unit = { _, _, _, _ -> },
    onBackToPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    var areControlsVisible by remember { mutableStateOf(true) }
    var isLoadingPage by remember { mutableStateOf(true) }
    var showEpisodeSheet by remember { mutableStateOf(false) }

    val blockedCount by AdBlockManager.blockedSessionCount.collectAsState()
    val totalAdRules by AdBlockManager.ruleCountFlow.collectAsState()

    // Focus Calibration state
    val initialCalibration = remember { FocusCalibrationManager.getCalibration(context) }
    var focusXRatio by remember { mutableFloatStateOf(initialCalibration.first) }
    var focusYRatio by remember { mutableFloatStateOf(initialCalibration.second) }
    var isCalibrationOpen by remember { mutableStateOf(false) }
    var testClickIndicatorTrigger by remember { mutableLongStateOf(0L) }

    // Screen Zoom state
    val initialZoom = remember { ScreenZoomManager.getZoomConfig(context) }
    var zoomScale by remember { mutableFloatStateOf(initialZoom.scale) }
    var panX by remember { mutableFloatStateOf(initialZoom.panX) }
    var panY by remember { mutableFloatStateOf(initialZoom.panY) }
    var zoomPreset by remember { mutableStateOf(initialZoom.preset) }
    var showZoomSheet by remember { mutableStateOf(false) }

    var showDownloadEditDialog by remember { mutableStateOf(false) }
    var capturedMediaUrl by remember { mutableStateOf<String?>(null) }

    // Multi-server & Episode state
    var availableServers by remember { mutableStateOf<List<PlayerServer>>(emptyList()) }
    var selectedServerUrl by remember { mutableStateOf<String?>(null) }
    var nextEpisodeUrl by remember { mutableStateOf<String?>(null) }
    var prevEpisodeUrl by remember { mutableStateOf<String?>(null) }

    var currentEpisodes by remember(playingState.episodes) { mutableStateOf(playingState.episodes) }
    var availableSeasons by remember(playingState.availableSeasons) { mutableStateOf(playingState.availableSeasons.ifEmpty { listOf(playingState.season) }) }
    var selectedDrawerSeason by remember(playingState.season) { mutableIntStateOf(playingState.season.coerceAtLeast(1)) }
    var isLoadingDrawerSeason by remember { mutableStateOf(false) }
    var seriesVideoId by remember { mutableStateOf<String?>(null) }

    var currentUrl by remember(playingState.url) { mutableStateOf(playingState.url) }
    var currentEpIndex by remember(playingState.currentEpisodeIndex, playingState.url) {
        val idx = if (playingState.episodes.isNotEmpty()) {
            val found = playingState.episodes.indexOfFirst { it.url == playingState.url }
            if (found >= 0) found else playingState.currentEpisodeIndex.coerceIn(0, playingState.episodes.size - 1)
        } else 0
        mutableIntStateOf(idx)
    }

    val currentEpisode = currentEpisodes.getOrNull(currentEpIndex)
    val isSeriesEpisode = playingState.isSeries || currentEpisodes.isNotEmpty() || currentUrl.contains("/episodios/")

    fun cycleZoomPreset() {
        val next = ScreenZoomManager.cycleNextPreset(context, ScreenZoomManager.ZoomConfig(zoomScale, panX, panY, zoomPreset))
        zoomScale = next.scale
        panX = next.panX
        panY = next.panY
        zoomPreset = next.preset
        onPreferencesChanged(next.preset, next.scale, focusXRatio, focusYRatio)
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
        com.example.MainActivity.onToggleZoomCallback = { cycleZoomPreset() }
    }
    DisposableEffect(Unit) {
        onDispose { com.example.MainActivity.onToggleZoomCallback = null }
    }

    fun extractMediaUrlFromWebView() {
        val wv = webViewRef ?: return
        wv.evaluateJavascript(
            """
            (function() {
                try {
                    var v = document.querySelector('video');
                    if (v && (v.currentSrc || v.src)) return v.currentSrc || v.src;
                    var s = document.querySelector('source');
                    if (s && s.src) return s.src;
                    var a = document.querySelector('a[href*=".mp4"], a[download]');
                    if (a && a.href) return a.href;
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
        onDispose { com.example.MainActivity.onOpenDownloadEditCallback = null }
    }

    LaunchedEffect(areControlsVisible) {
        com.example.MainActivity.arePlayerControlsVisible = areControlsVisible
    }
    LaunchedEffect(com.example.MainActivity.arePlayerControlsVisible) {
        areControlsVisible = com.example.MainActivity.arePlayerControlsVisible
    }

    LaunchedEffect(Unit) {
        AdBlockManager.resetSessionBlocked()
        delay(4500)
        areControlsVisible = false
    }

    // Fetch multi-server list + next/prev navigation whenever currentUrl changes
    LaunchedEffect(currentUrl) {
        selectedServerUrl = null
        val videoId = Regex("""-(\d+)/?$""").find(currentUrl)?.groupValues?.get(1)
        if (!videoId.isNullOrBlank()) {
            val data = SearchService.fetchPlayerData(videoId)
            if (data != null) {
                availableServers = data.servers
                nextEpisodeUrl = data.nextEpisodeUrl
                prevEpisodeUrl = data.prevEpisodeUrl
            }
        }
    }

    // Load series metadata if opened from history/direct without full seasons list
    LaunchedEffect(playingState.seriesUrl) {
        val sUrl = playingState.seriesUrl
        if (sUrl.isNotBlank() && isSeriesEpisode) {
            val meta = SearchService.fetchSeriesMetadata(sUrl)
            if (meta != null) {
                if (meta.videoId.isNotBlank()) seriesVideoId = meta.videoId
                if (meta.availableSeasons.isNotEmpty()) {
                    availableSeasons = (availableSeasons + meta.availableSeasons).distinct().sorted()
                }
                if (currentEpisodes.isEmpty() && meta.videoId.isNotBlank()) {
                    val eps = SearchService.fetchSeasonEpisodes(meta.videoId, selectedDrawerSeason)
                    if (eps.isNotEmpty()) {
                        currentEpisodes = eps
                        val found = eps.indexOfFirst { it.url == currentUrl }
                        if (found >= 0) currentEpIndex = found
                    } else if (meta.fallbackEpisodes.isNotEmpty()) {
                        currentEpisodes = meta.fallbackEpisodes
                    }
                }
            }
        }
    }

    fun loadDrawerSeason(season: Int) {
        selectedDrawerSeason = season
        val existingInSeason = currentEpisodes.filter { it.season == season }
        if (existingInSeason.isNotEmpty()) return
        val vid = seriesVideoId ?: return
        isLoadingDrawerSeason = true
        coroutineScope.launch {
            val fetched = SearchService.fetchSeasonEpisodes(vid, season)
            if (fetched.isNotEmpty()) {
                currentEpisodes = (currentEpisodes.filter { it.season != season } + fetched)
                    .distinctBy { it.url }
                    .sortedWith(compareBy({ it.season }, { it.number }))
            }
            isLoadingDrawerSeason = false
        }
    }

    fun persistEpisodeProgress(ep: Episode) {
        val seriesId = playingState.seriesUrl.ifBlank { playingState.url.substringBefore("/episodio/").ifBlank { playingState.url } }
        val cleanTitle = playingState.title.substringBefore(" - T").substringBefore(" - Ep").ifBlank { playingState.title }
        val item = WatchHistoryItem(
            id = seriesId,
            title = cleanTitle,
            coverUrl = playingState.coverUrl,
            currentUrl = ep.url,
            isSeries = true,
            season = ep.season,
            episodeNumber = ep.number,
            episodeTitle = ep.title
        )
        WatchHistoryManager.saveProgress(context, item)
        onHistoryProgressSaved(item)
    }

    fun playEpisode(ep: Episode, indexInList: Int) {
        currentEpIndex = indexInList
        currentUrl = ep.url
        selectedServerUrl = null
        webViewRef?.loadUrl(ep.url)
        showEpisodeSheet = false
        persistEpisodeProgress(ep)
    }

    fun playEpisodeByIndex(index: Int) {
        if (index in currentEpisodes.indices) {
            playEpisode(currentEpisodes[index], index)
        }
    }

    fun playNextEpisode() {
        val seasonEps = currentEpisodes.filter { it.season == (currentEpisode?.season ?: selectedDrawerSeason) }
        val posInSeason = seasonEps.indexOfFirst { it.url == currentUrl }
        if (posInSeason >= 0 && posInSeason < seasonEps.size - 1) {
            val nextEp = seasonEps[posInSeason + 1]
            val idx = currentEpisodes.indexOfFirst { it.url == nextEp.url }.coerceAtLeast(0)
            playEpisode(nextEp, idx)
        } else if (currentEpisodes.isNotEmpty() && currentEpIndex < currentEpisodes.size - 1) {
            playEpisodeByIndex(currentEpIndex + 1)
        } else if (!nextEpisodeUrl.isNullOrBlank()) {
            val target = nextEpisodeUrl!!
            nextEpisodeUrl = null
            currentUrl = target
            selectedServerUrl = null
            webViewRef?.loadUrl(target)
        } else {
            webViewRef?.evaluateJavascript("if (window.playNextEpisode) window.playNextEpisode();", null)
        }
    }

    fun playPrevEpisode() {
        val seasonEps = currentEpisodes.filter { it.season == (currentEpisode?.season ?: selectedDrawerSeason) }
        val posInSeason = seasonEps.indexOfFirst { it.url == currentUrl }
        if (posInSeason > 0) {
            val prevEp = seasonEps[posInSeason - 1]
            val idx = currentEpisodes.indexOfFirst { it.url == prevEp.url }.coerceAtLeast(0)
            playEpisode(prevEp, idx)
        } else if (currentEpisodes.isNotEmpty() && currentEpIndex > 0) {
            playEpisodeByIndex(currentEpIndex - 1)
        } else if (!prevEpisodeUrl.isNullOrBlank()) {
            val target = prevEpisodeUrl!!
            prevEpisodeUrl = null
            currentUrl = target
            selectedServerUrl = null
            webViewRef?.loadUrl(target)
        } else {
            webViewRef?.evaluateJavascript("if (window.playPrevEpisode) window.playPrevEpisode();", null)
        }
    }

    fun restartCurrentVideo() {
        val target = selectedServerUrl ?: currentUrl
        val msg = "↺ Reiniciando reprodução..."
        com.example.MainActivity.seekOsdMessage = msg
        coroutineScope.launch {
            delay(1400)
            if (com.example.MainActivity.seekOsdMessage == msg) {
                com.example.MainActivity.seekOsdMessage = null
            }
        }
        webViewRef?.evaluateJavascript(
            """
            (function() {
                var reset = false;
                document.querySelectorAll('video').forEach(function(v) {
                    try {
                        v.currentTime = 0;
                        v.play();
                        reset = true;
                    } catch(e) {}
                });
                return reset;
            })();
            """.trimIndent()
        ) { res ->
            if (res != "true") {
                webViewRef?.loadUrl(target)
            }
        }
    }

    LaunchedEffect(currentEpIndex, currentEpisodes, nextEpisodeUrl, prevEpisodeUrl, isSeriesEpisode) {
        if (isSeriesEpisode) {
            com.example.MainActivity.onNextEpisodeCallback = {
                val msg = "⏭️ Próximo Episódio"
                com.example.MainActivity.seekOsdMessage = msg
                playNextEpisode()
                coroutineScope.launch {
                    delay(1500)
                    if (com.example.MainActivity.seekOsdMessage == msg) com.example.MainActivity.seekOsdMessage = null
                }
            }
            com.example.MainActivity.onPrevEpisodeCallback = {
                val msg = "⏮️ Episódio Anterior"
                com.example.MainActivity.seekOsdMessage = msg
                playPrevEpisode()
                coroutineScope.launch {
                    delay(1500)
                    if (com.example.MainActivity.seekOsdMessage == msg) com.example.MainActivity.seekOsdMessage = null
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
            if (com.example.MainActivity.seekOsdMessage == msg) com.example.MainActivity.seekOsdMessage = null
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

        wv.evaluateJavascript(
            """
            (function() {
                try {
                    var targetX = Math.round(window.innerWidth * $xRatio);
                    var targetY = Math.round(window.innerHeight * $yRatio);
                    var dubladoBtn = document.querySelector('.dublado-btn, [data-audio="Dublado"], .btn-audio[data-type="dublado"]');
                    if (dubladoBtn && !dubladoBtn.classList.contains('active')) {
                        try { dubladoBtn.click(); } catch(e){}
                    }
                    document.querySelectorAll('video').forEach(function(vid) {
                        try {
                            vid.muted = false;
                            var p = vid.play();
                            if (p && p.catch) p.catch(function() { vid.muted = true; vid.play(); });
                        } catch(e) {}
                    });
                    var el = document.elementFromPoint(targetX, targetY);
                    if (el && el.tagName !== 'BODY') {
                        try { el.click(); } catch(e) {}
                    }
                    var btns = document.querySelectorAll('.vjs-big-play-button, .play-btn, .btn-play, #play, .plyr__control--overlaid');
                    btns.forEach(function(b) { try { b.click(); } catch(e){} });
                } catch(err) {}
            })();
            """.trimIndent(),
            null
        )
    }

    fun testSimulateClick(xRatio: Float, yRatio: Float) {
        testClickIndicatorTrigger = System.currentTimeMillis()
        dispatchAccuratePlayClick(xRatio, yRatio)
        val msg = "🎯 Testando clique em ${(xRatio * 100).toInt()}% x ${(yRatio * 100).toInt()}%"
        com.example.MainActivity.seekOsdMessage = msg
        coroutineScope.launch {
            delay(1500)
            if (com.example.MainActivity.seekOsdMessage == msg) com.example.MainActivity.seekOsdMessage = null
        }
    }

    LaunchedEffect(isCalibrationOpen, focusXRatio, focusYRatio) {
        com.example.MainActivity.isCalibrationModeActive = isCalibrationOpen
        if (isCalibrationOpen) {
            com.example.MainActivity.onMoveCalibrationReticle = { dx, dy ->
                focusXRatio = (focusXRatio + dx).coerceIn(0.05f, 0.95f)
                focusYRatio = (focusYRatio + dy).coerceIn(0.05f, 0.95f)
            }
            com.example.MainActivity.onTestCalibrationClick = { testSimulateClick(focusXRatio, focusYRatio) }
            com.example.MainActivity.onSaveCalibration = {
                FocusCalibrationManager.saveCalibration(context, focusXRatio, focusYRatio)
                onPreferencesChanged(zoomPreset, zoomScale, focusXRatio, focusYRatio)
                isCalibrationOpen = false
                com.example.MainActivity.seekOsdMessage = "🎯 Posição salva!"
            }
            com.example.MainActivity.onExitCalibration = { isCalibrationOpen = false }
        } else {
            com.example.MainActivity.onMoveCalibrationReticle = null
            com.example.MainActivity.onTestCalibrationClick = null
            com.example.MainActivity.onSaveCalibration = null
            com.example.MainActivity.onExitCalibration = null
        }
    }

    BackHandler(enabled = true) {
        when {
            showDownloadEditDialog -> showDownloadEditDialog = false
            showZoomSheet -> showZoomSheet = false
            isCalibrationOpen -> isCalibrationOpen = false
            showEpisodeSheet -> showEpisodeSheet = false
            !areControlsVisible -> areControlsVisible = true
            customView != null -> customViewCallback?.onCustomViewHidden()
            else -> onBackToPrevious()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video WebView Container
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
                        settings.userAgentString = settings.userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
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
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        try {
                                            window.open = function() { return null; };
                                            window.alert = function() {};
                                            window.confirm = function() { return true; };
                                            window.prompt = function() { return null; };
                                            var meta = document.querySelector('meta[name="viewport"]');
                                            if (!meta) {
                                                meta = document.createElement('meta');
                                                meta.name = 'viewport';
                                                if (document.head) document.head.appendChild(meta);
                                            }
                                            if (meta) meta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no');
                                        } catch(e) {}
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoadingPage = false
                                view?.postDelayed({ dispatchAccuratePlayClick(focusXRatio, focusYRatio) }, 800)
                                view?.postDelayed({ dispatchAccuratePlayClick(focusXRatio, focusYRatio) }, 2200)
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        window.open = function() { return null; };
                                        document.querySelectorAll('a[target="_blank"]').forEach(function(a) {
                                            a.setAttribute('target', '_self');
                                        });
                                        var oldStyle = document.getElementById('inapp-cinema-style');
                                        if (oldStyle) oldStyle.remove();
                                        var css = 'html, body { margin:0!important; padding:0!important; background:#000!important; color:#fff!important; } ' +
                                            'header, footer, nav, .site-header, .site-footer, #ipsLayout_header, #ipsLayout_footer, .sidebar, #comments, .comments, .ads, .banner, .floating-ads, [id*="ad-"], [class*="popup"] { display:none!important; } ' +
                                            '#view, #player, .player-area, iframe, video { width:100%!important; max-width:100%!important; min-height:260px!important; border:none!important; }';
                                        var st = document.createElement('style');
                                        st.id = 'inapp-cinema-style';
                                        st.innerHTML = css;
                                        if (document.head) document.head.appendChild(st);
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val uri = request?.url ?: return false
                                val scheme = uri.scheme?.lowercase() ?: ""
                                if (scheme != "http" && scheme != "https") return true
                                val full = uri.toString()
                                if (AdBlockManager.isBlockedUrl(full)) {
                                    AdBlockManager.recordBlocked()
                                    return true
                                }
                                return false
                            }

                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                val u = request?.url?.toString().orEmpty()
                                if (AdBlockManager.isBlockedUrl(u)) {
                                    AdBlockManager.recordBlocked()
                                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean = false
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

        if (customView != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(android.graphics.Color.BLACK)
                        customView?.let { v ->
                            (v.parent as? ViewGroup)?.removeView(v)
                            addView(v)
                        }
                    }
                }
            )
        }

        // Persistent Compact Floating Toggle Pill for Mobile Touchscreens when Controls are Hidden
        AnimatedVisibility(
            visible = !areControlsVisible && !isCalibrationOpen && !showEpisodeSheet && !showZoomSheet,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            Surface(
                onClick = { areControlsVisible = true },
                color = ObsidianBg.copy(alpha = 0.78f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, ObsidianStroke),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("show_controls_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Mostrar controles", tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Controles", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (blockedCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("🛡️$blockedCount", color = EmeraldAdBlock, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Central OSD Feedback
        val activeOsd = com.example.MainActivity.seekOsdMessage
        AnimatedVisibility(
            visible = activeOsd != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = ObsidianSurface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, CrimsonPrimary),
                shadowElevation = 14.dp
            ) {
                Text(
                    text = activeOsd.orEmpty(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                )
            }
        }

        // 1. TOP HEADER + SERVER SWITCHER
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            Surface(color = ObsidianBg.copy(alpha = 0.90f), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            TvIconButton(
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                onClick = onBackToPrevious
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (currentEpisode != null) {
                                        "T${currentEpisode.season} E${currentEpisode.number}: ${currentEpisode.title}"
                                    } else {
                                        playingState.title.ifBlank { "A2 Miguel • Reproduzindo" }
                                    },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "🛡️ AdBlock ($totalAdRules+ regras) • $blockedCount bloqueados",
                                    color = EmeraldAdBlock,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TvControlPill(
                                icon = Icons.Default.VisibilityOff,
                                label = "Ocultar",
                                color = CrimsonPrimary,
                                onClick = { areControlsVisible = false }
                            )
                        }
                    }

                    // Server Switcher Row
                    if (availableServers.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Servidores:", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            availableServers.forEach { server ->
                                val isSel = selectedServerUrl == server.embedUrl
                                Surface(
                                    onClick = {
                                        selectedServerUrl = server.embedUrl
                                        webViewRef?.loadUrl(server.embedUrl)
                                    },
                                    color = if (isSel) CrimsonPrimary else ObsidianCard,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isSel) Color.White else ObsidianStroke),
                                    modifier = Modifier.heightIn(min = 38.dp)
                                ) {
                                    Text(
                                        text = server.name,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isLoadingPage && areControlsVisible) {
            LinearProgressIndicator(
                color = CrimsonPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }

        // 2. BOTTOM CONTROLS BAR (Scrollable horizontally on narrow phones)
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            Surface(
                color = ObsidianSurface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, ObsidianStroke),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvControlPill(
                        icon = Icons.Default.PlayArrow,
                        label = "Tocar",
                        color = CrimsonPrimary,
                        onClick = { dispatchAccuratePlayClick(focusXRatio, focusYRatio) }
                    )
                    TvControlPill(
                        icon = Icons.Default.Replay,
                        label = "Reiniciar",
                        color = ObsidianCardElevated,
                        onClick = { restartCurrentVideo() }
                    )
                    TvControlPill(
                        icon = Icons.Default.Replay10,
                        label = "-10s",
                        onClick = { seekVideo(-10) }
                    )
                    TvControlPill(
                        icon = Icons.Default.Forward10,
                        label = "+10s",
                        onClick = { seekVideo(10) }
                    )
                    if (isSeriesEpisode) {
                        TvControlPill(
                            icon = Icons.Default.SkipPrevious,
                            label = "Ep Ant.",
                            onClick = { playPrevEpisode() }
                        )
                        TvControlPill(
                            icon = Icons.Default.SkipNext,
                            label = "Próx Ep",
                            color = CrimsonPrimary,
                            onClick = { playNextEpisode() }
                        )
                        TvControlPill(
                            icon = Icons.Default.FormatListNumbered,
                            label = "Temp / Eps",
                            color = CyanAccent.copy(alpha = 0.25f),
                            onClick = { showEpisodeSheet = true }
                        )
                    }
                    TvControlPill(
                        icon = Icons.Default.ZoomIn,
                        label = "Zoom",
                        color = ObsidianCardElevated,
                        onClick = { showZoomSheet = true }
                    )
                    TvControlPill(
                        icon = Icons.Default.Download,
                        label = "Baixar",
                        color = EmeraldAdBlock.copy(alpha = 0.25f),
                        onClick = {
                            extractMediaUrlFromWebView()
                            showDownloadEditDialog = true
                        }
                    )
                }
            }
        }

        // IN-PLAYER SEASON + EPISODE SWITCHER DRAWER
        AnimatedVisibility(
            visible = showEpisodeSheet,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val drawerSeasonEps = remember(currentEpisodes, selectedDrawerSeason) {
                val filtered = currentEpisodes.filter { it.season == selectedDrawerSeason }
                if (filtered.isNotEmpty()) filtered else currentEpisodes
            }
            Surface(
                color = ObsidianSurface.copy(alpha = 0.97f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = BorderStroke(1.dp, ObsidianStroke),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Mudar Temporada & Episódio", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Temporada $selectedDrawerSeason • ${drawerSeasonEps.size} episódios",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        TvIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Fechar",
                            onClick = { showEpisodeSheet = false }
                        )
                    }

                    if (availableSeasons.size > 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(availableSeasons) { s ->
                                val isSel = s == selectedDrawerSeason
                                FilterChip(
                                    selected = isSel,
                                    onClick = { loadDrawerSeason(s) },
                                    label = { Text("Temp $s", fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CrimsonPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = ObsidianCard,
                                        labelColor = TextSecondary
                                    ),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TvButton(
                            text = "Ep Anterior",
                            icon = Icons.Default.SkipPrevious,
                            containerColor = ObsidianCardElevated,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                playPrevEpisode()
                                showEpisodeSheet = false
                            }
                        )
                        TvButton(
                            text = "Próximo Ep",
                            icon = Icons.Default.SkipNext,
                            containerColor = CrimsonPrimary,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                playNextEpisode()
                                showEpisodeSheet = false
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isLoadingDrawerSeason) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CrimsonPrimary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(drawerSeasonEps, key = { it.url }) { ep ->
                                val isSelected = ep.url == currentUrl
                                val isWatched = watchedEpisodeKeys.contains("${ep.season}x${ep.number}")
                                Surface(
                                    onClick = {
                                        val globalIdx = currentEpisodes.indexOfFirst { it.url == ep.url }.coerceAtLeast(0)
                                        playEpisode(ep, globalIdx)
                                    },
                                    color = if (isSelected) CrimsonPrimary else ObsidianCard,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (isWatched) EmeraldAdBlock.copy(alpha = 0.6f) else ObsidianStroke),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 54.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .background(
                                                        if (isSelected) Color.White.copy(alpha = 0.2f) else ObsidianCardElevated,
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("${ep.number}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Text("Assistindo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        } else if (isWatched) {
                                            Text("Visto ✓", color = EmeraldAdBlock, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isCalibrationOpen) {
            FocusCalibrationOverlay(
                focusX = focusXRatio,
                focusY = focusYRatio,
                onMove = { dx, dy ->
                    focusXRatio = (focusXRatio + dx).coerceIn(0.05f, 0.95f)
                    focusYRatio = (focusYRatio + dy).coerceIn(0.05f, 0.95f)
                },
                onTestClick = { testSimulateClick(focusXRatio, focusYRatio) },
                onSave = {
                    FocusCalibrationManager.saveCalibration(context, focusXRatio, focusYRatio)
                    onPreferencesChanged(zoomPreset, zoomScale, focusXRatio, focusYRatio)
                    isCalibrationOpen = false
                    com.example.MainActivity.seekOsdMessage = "🎯 Posição salva!"
                },
                onReset = {
                    FocusCalibrationManager.resetCalibration(context)
                    focusXRatio = FocusCalibrationManager.DEFAULT_FOCUS_X
                    focusYRatio = FocusCalibrationManager.DEFAULT_FOCUS_Y
                },
                onClose = { isCalibrationOpen = false },
                testTrigger = testClickIndicatorTrigger
            )
        }

        // ZOOM MODAL
        AnimatedVisibility(
            visible = showZoomSheet,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = ObsidianSurface.copy(alpha = 0.97f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = BorderStroke(1.dp, ObsidianStroke),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔍 Zoom e Enquadramento", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        TvIconButton(icon = Icons.Default.Close, contentDescription = "Fechar", onClick = { showZoomSheet = false })
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple(ScreenZoomManager.PRESET_NORMAL, "Normal", 1.00f),
                            Triple(ScreenZoomManager.PRESET_FIT, "Ajustar", 1.15f),
                            Triple(ScreenZoomManager.PRESET_CINEMA, "Cinema", 1.30f),
                            Triple(ScreenZoomManager.PRESET_FULL, "Cheia", 1.50f)
                        ).forEach { (preset, label, scale) ->
                            ZoomPresetButton(
                                title = label,
                                subtitle = "${(scale * 100).toInt()}%",
                                isSelected = zoomPreset == preset,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    zoomScale = scale
                                    panX = 0f
                                    panY = 0f
                                    zoomPreset = preset
                                    ScreenZoomManager.saveZoomConfig(context, scale, 0f, 0f, preset)
                                    onPreferencesChanged(preset, scale, focusXRatio, focusYRatio)
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Aproximação: ${(zoomScale * 100).toInt()}%", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TvIconButton(
                                icon = Icons.Default.Remove,
                                contentDescription = "Diminuir zoom",
                                onClick = {
                                    zoomScale = (zoomScale - 0.05f).coerceIn(1.0f, 2.5f)
                                    zoomPreset = ScreenZoomManager.PRESET_CUSTOM
                                    ScreenZoomManager.saveZoomConfig(context, zoomScale, panX, panY, zoomPreset)
                                    onPreferencesChanged(zoomPreset, zoomScale, focusXRatio, focusYRatio)
                                }
                            )
                            TvIconButton(
                                icon = Icons.Default.Add,
                                contentDescription = "Aumentar zoom",
                                onClick = {
                                    zoomScale = (zoomScale + 0.05f).coerceIn(1.0f, 2.5f)
                                    zoomPreset = ScreenZoomManager.PRESET_CUSTOM
                                    ScreenZoomManager.saveZoomConfig(context, zoomScale, panX, panY, zoomPreset)
                                    onPreferencesChanged(zoomPreset, zoomScale, focusXRatio, focusYRatio)
                                }
                            )
                        }
                    }
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
                    onPreferencesChanged(next.preset, next.scale, focusXRatio, focusYRatio)
                },
                onOpenFocusCalibration = { isCalibrationOpen = true },
                onTriggerForcePlay = { dispatchAccuratePlayClick(focusXRatio, focusYRatio) },
                onDismissRequest = { showDownloadEditDialog = false }
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
        onClick = onClick,
        color = if (isSelected) CrimsonPrimary else if (isFocused) ObsidianCardElevated else ObsidianCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isFocused) 2.5.dp else 1.dp, if (isFocused) AmberGold else if (isSelected) Color.White else ObsidianStroke),
        modifier = modifier
            .heightIn(min = 48.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 10.sp)
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
    val isTesting = remember(testTrigger) { testTrigger > 0 && System.currentTimeMillis() - testTrigger < 700 }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val centerX = widthPx * focusX
        val centerY = heightPx * focusY

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(centerX, centerY)
            val outerRadius = 36.dp.toPx()
            val innerRadius = 14.dp.toPx()
            val crosshairLen = 20.dp.toPx()
            val reticleColor = if (isTesting) AmberGold else CyanAccent

            drawCircle(color = reticleColor.copy(alpha = 0.25f), radius = outerRadius + 8.dp.toPx(), center = center)
            drawCircle(color = reticleColor, radius = outerRadius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            drawCircle(color = reticleColor, radius = innerRadius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
            drawCircle(color = CrimsonPrimary, radius = 4.5.dp.toPx(), center = center)

            val strokeW = 2.dp.toPx()
            drawLine(reticleColor, Offset(center.x, center.y - innerRadius - 2.dp.toPx()), Offset(center.x, center.y - outerRadius - crosshairLen), strokeW)
            drawLine(reticleColor, Offset(center.x, center.y + innerRadius + 2.dp.toPx()), Offset(center.x, center.y + outerRadius + crosshairLen), strokeW)
            drawLine(reticleColor, Offset(center.x - innerRadius - 2.dp.toPx(), center.y), Offset(center.x - outerRadius - crosshairLen, center.y), strokeW)
            drawLine(reticleColor, Offset(center.x + innerRadius + 2.dp.toPx(), center.y), Offset(center.x + outerRadius + crosshairLen, center.y), strokeW)
        }

        Box(
            modifier = Modifier
                .offset(
                    x = with(LocalDensity.current) { (centerX + 44.dp.toPx()).toDp().coerceIn(12.dp, maxWidth - 220.dp) },
                    y = with(LocalDensity.current) { (centerY - 20.dp.toPx()).toDp().coerceIn(70.dp, maxHeight - 120.dp) }
                )
        ) {
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.5.dp, if (isTesting) AmberGold else CyanAccent)
            ) {
                Text(
                    text = if (isTesting) "CLIQUE DISPARADO" else "Alvo: X ${(focusX * 100).toInt()}% • Y ${(focusY * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Surface(
            color = ObsidianSurface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, ObsidianStroke),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvControlPill(Icons.AutoMirrored.Filled.ArrowBack, "Esq", { onMove(-0.02f, 0f) }, ObsidianCard)
                    TvControlPill(Icons.Default.ArrowUpward, "Cima", { onMove(0f, -0.02f) }, ObsidianCard)
                    TvControlPill(Icons.Default.ArrowDownward, "Baixo", { onMove(0f, 0.02f) }, ObsidianCard)
                    TvControlPill(Icons.AutoMirrored.Filled.ArrowForward, "Dir", { onMove(0.02f, 0f) }, ObsidianCard)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvControlPill(Icons.Default.PlayArrow, "Testar", onTestClick, CyanAccent.copy(alpha = 0.25f))
                    TvControlPill(Icons.Default.Check, "Salvar", onSave, EmeraldAdBlock.copy(alpha = 0.25f))
                    TvControlPill(Icons.Default.RestartAlt, "Padrão", onReset, ObsidianCardElevated)
                    TvControlPill(Icons.Default.Close, "Sair", onClose, CrimsonPrimary)
                }
            }
        }
    }
}

@Composable
fun TvControlPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = ObsidianCard
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        color = if (isFocused) CrimsonPrimary else color,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isFocused) 2.5.dp else 1.dp, if (isFocused) AmberGold else ObsidianStroke),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
        onClick = onClick,
        color = if (isFocused) CrimsonPrimary else ObsidianCard,
        shape = CircleShape,
        border = if (isFocused) BorderStroke(2.dp, AmberGold) else BorderStroke(1.dp, ObsidianStroke),
        modifier = Modifier
            .size(48.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
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
        onClick = onClick,
        color = if (isFocused) CrimsonPrimary else containerColor,
        shape = RoundedCornerShape(12.dp),
        border = if (isFocused) BorderStroke(2.5.dp, AmberGold) else BorderStroke(1.dp, ObsidianStroke),
        modifier = modifier
            .heightIn(min = 48.dp)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

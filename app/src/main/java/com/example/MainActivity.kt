package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.AppState
import com.example.model.Episode
import com.example.model.Movie
import com.example.model.WatchHistoryItem
import com.example.model.WatchHistoryManager
import com.example.ui.NetflixHome
import com.example.ui.PlayerScreen
import com.example.ui.SearchScreen
import com.example.ui.SeriesDetailsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import org.json.JSONArray

class ScraperInterface(
    private val onExtracted: (List<Movie>) -> Unit,
    private val onAnimeExtracted: (List<Movie>) -> Unit,
    private val onSeasonsCallback: (List<Int>) -> Unit,
    private val onEpisodesCallback: (Int, List<Episode>) -> Unit
) {
    @JavascriptInterface
    fun onDataExtracted(json: String) {
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<Movie>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val t = obj.optString("title", "")
                val c = obj.optString("cover", "")
                val u = obj.optString("url", "")
                if (t.isNotBlank() && c.isNotBlank() && u.isNotBlank()) {
                    list.add(Movie(t.trim(), c.trim(), u.trim()))
                }
            }
            onExtracted(list)
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun onAnimeExtracted(json: String) {
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<Movie>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val t = obj.optString("title", "")
                val c = obj.optString("cover", "")
                val u = obj.optString("url", "")
                if (t.isNotBlank() && c.isNotBlank() && u.isNotBlank()) {
                    list.add(Movie(t.trim(), c.trim(), u.trim()))
                }
            }
            onAnimeExtracted(list)
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun onSeasonsDetected(json: String) {
        try {
            val arr = JSONArray(json)
            val seasons = mutableListOf<Int>()
            for (i in 0 until arr.length()) {
                seasons.add(arr.getInt(i))
            }
            if (seasons.isNotEmpty()) {
                onSeasonsCallback(seasons.sorted())
            }
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun onEpisodesExtractedForSeason(season: Int, json: String) {
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<Episode>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val t = obj.optString("title", "Episódio ${i + 1}")
                val u = obj.optString("url", "")
                val num = obj.optInt("number", i + 1)
                val s = obj.optInt("season", season)
                if (u.isNotBlank()) {
                    list.add(Episode(t, u, s, num))
                }
            }
            list.sortWith(compareBy({ it.season }, { it.number }))
            onEpisodesCallback(season, list)
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun onEpisodesExtracted(json: String) {
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<Episode>()
            val regex = Regex("-(\\d+)x(\\d+)-")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val t = obj.optString("title", "Episódio ${i + 1}")
                val u = obj.optString("url", "")
                if (u.isNotBlank()) {
                    val match = regex.find(u)
                    val s = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val ep = match?.groupValues?.get(2)?.toIntOrNull() ?: (i + 1)
                    list.add(Episode(t, u, s, ep))
                }
            }
            list.sortWith(compareBy({ it.season }, { it.number }))
            onEpisodesCallback(1, list)
        } catch (_: Exception) {}
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        var scraperWebViewRef: WebView? = null
        var playerWebViewRef: WebView? = null
        var currentState by mutableStateOf<AppState>(AppState.WebLoading)
        var arePlayerControlsVisible by mutableStateOf(true)
        var seekOsdMessage by mutableStateOf<String?>(null)
        var onNextEpisodeCallback: (() -> Unit)? = null
        var onPrevEpisodeCallback: (() -> Unit)? = null
        var onToggleZoomCallback: (() -> Unit)? = null
        var isCalibrationModeActive by mutableStateOf(false)
        var onMoveCalibrationReticle: ((deltaX: Float, deltaY: Float) -> Unit)? = null
        var onTestCalibrationClick: (() -> Unit)? = null
        var onSaveCalibration: (() -> Unit)? = null
        var onExitCalibration: (() -> Unit)? = null
    }

    private var seekJob: kotlinx.coroutines.Job? = null

    private fun triggerSeekVideo(deltaSeconds: Int) {
        val playerWv = playerWebViewRef ?: return
        val msg = if (deltaSeconds < 0) "⏪ ${deltaSeconds}s" else "⏩ +${deltaSeconds}s"
        seekOsdMessage = msg
        seekJob?.cancel()
        seekJob = lifecycleScope.launch {
            delay(1300)
            if (seekOsdMessage == msg) {
                seekOsdMessage = null
            }
        }
        playerWv.evaluateJavascript(
            """
            (function() {
                var delta = $deltaSeconds;
                var found = false;
                document.querySelectorAll('video').forEach(function(v) {
                    try {
                        v.currentTime = Math.max(0, Math.min(v.duration || 999999, v.currentTime + delta));
                        found = true;
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

    private fun triggerVideoPlayPause(playerWv: WebView?) {
        playerWv?.evaluateJavascript("""
            (function() {
                try {
                    var vids = document.querySelectorAll('video');
                    var toggled = false;
                    vids.forEach(function(vid) {
                        try {
                            if (vid.paused) {
                                vid.muted = false;
                                var p = vid.play();
                                if (p && p.catch) p.catch(function(){ vid.muted = true; vid.play(); });
                            } else {
                                vid.pause();
                            }
                            toggled = true;
                        } catch(e) {}
                    });

                    // If no direct HTML5 video was toggled, click the big play buttons
                    if (!toggled) {
                        var btns = document.querySelectorAll(
                            '.vjs-big-play-button, .captcha_l, .play-btn, .btn-play, #play, .play, ' +
                            '.jw-display-icon-container, button[aria-label*="Play"], button[title*="Play"], ' +
                            '.plyr__control--overlaid, .player-area .button, #view .button'
                        );
                        btns.forEach(function(b) {
                            try { b.click(); } catch(e){}
                        });
                    }

                    // Forward play/pause toggle to iframe players
                    document.querySelectorAll('iframe').forEach(function(ifr) {
                        try {
                            ifr.contentWindow.postMessage('{"event":"command","func":"playVideo","args":""}', '*');
                            ifr.contentWindow.postMessage('play', '*');
                        } catch(e) {}
                    });
                } catch(err) {}
            })();
        """.trimIndent(), null)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val playerWv = playerWebViewRef
            if (currentState is AppState.Playing) {
                if (isCalibrationModeActive) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            onMoveCalibrationReticle?.invoke(0f, -0.015f)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            onMoveCalibrationReticle?.invoke(0f, 0.015f)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onMoveCalibrationReticle?.invoke(-0.015f, 0f)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onMoveCalibrationReticle?.invoke(0.015f, 0f)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_BUTTON_A -> {
                            onTestCalibrationClick?.invoke()
                            return true
                        }
                        KeyEvent.KEYCODE_PROG_GREEN,
                        KeyEvent.KEYCODE_BUTTON_Y -> {
                            onSaveCalibration?.invoke()
                            return true
                        }
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE -> {
                            onExitCalibration?.invoke()
                            return true
                        }
                    }
                    return true
                }

                // TV D-pad Left and Right directly seek the video (as chosen by user)
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        triggerSeekVideo(-10)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        triggerSeekVideo(10)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_MENU -> {
                        arePlayerControlsVisible = true
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        val cb = onNextEpisodeCallback
                        if (cb != null) {
                            cb.invoke()
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        val cb = onPrevEpisodeCallback
                        if (cb != null) {
                            cb.invoke()
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        triggerVideoPlayPause(playerWv)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        if (!arePlayerControlsVisible) {
                            // When options are hidden, pressing OK plays/pauses the video directly
                            triggerVideoPlayPause(playerWv)
                            return true
                        }
                        // When options are visible, let Compose process the click on the focused control button
                    }
                    KeyEvent.KEYCODE_PROG_YELLOW,
                    KeyEvent.KEYCODE_WINDOW,
                    KeyEvent.KEYCODE_Z,
                    KeyEvent.KEYCODE_ZOOM_IN,
                    KeyEvent.KEYCODE_ZOOM_OUT -> {
                        val cb = onToggleZoomCallback
                        if (cb != null) {
                            cb.invoke()
                            return true
                        }
                    }
                }

                if (playerWv != null) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_PAGE_DOWN -> {
                            playerWv.evaluateJavascript("window.scrollBy({ top: 450, behavior: 'smooth' });", null)
                            return true
                        }
                        KeyEvent.KEYCODE_PAGE_UP -> {
                            playerWv.evaluateJavascript("window.scrollBy({ top: -450, behavior: 'smooth' });", null)
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            triggerSeekVideo(15)
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            triggerSeekVideo(-15)
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_CHANNEL_UP,
                        KeyEvent.KEYCODE_BUTTON_R2 -> {
                            playerWv.evaluateJavascript("if (window.playNextEpisode) window.playNextEpisode();", null)
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_CHANNEL_DOWN,
                        KeyEvent.KEYCODE_BUTTON_L2 -> {
                            playerWv.evaluateJavascript("if (window.playPrevEpisode) window.playPrevEpisode();", null)
                            return true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun loadSeason(seasonNum: Int) {
        scraperWebViewRef?.evaluateJavascript("""
            (function() {
                var sNum = $seasonNum;
                var opt = document.querySelector('.vbSeasonSelect__option[data-season="' + sNum + '"], [data-season="' + sNum + '"]');
                if (opt) { opt.click(); }
                var section = document.querySelector('.vbEpisodes');
                var videoId = section ? (section.getAttribute('data-video-id') || '') : '';
                if (!videoId) {
                    var el = document.querySelector('[data-video-id]');
                    if (el) videoId = el.getAttribute('data-video-id') || '';
                }
                if (videoId) {
                    var base = window.location.origin + '/';
                    var url = base + 'index.php?app=videobox&module=video&controller=view&do=episodesList&id=' + encodeURIComponent(videoId) + '&season=' + sNum + '&audio=Dublado';
                    fetch(url, {
                        headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' }
                    })
                    .then(function(res) { return res.json(); })
                    .then(function(data) {
                        if (data && data.episodes && data.episodes.length > 0) {
                            var eps = data.episodes.map(function(e) {
                                return {
                                    url: e.url,
                                    title: e.title,
                                    season: sNum,
                                    number: parseInt(e.number, 10) || 1
                                };
                            });
                            AndroidScraper.onEpisodesExtractedForSeason(sNum, JSON.stringify(eps));
                        }
                    })
                    .catch(function(err) {});
                }
            })();
        """.trimIndent(), null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F0F12))
                ) {
                    // Hidden Background Scraper WebView
                    AndroidView(
                        modifier = Modifier
                            .size(1.dp)
                            .align(Alignment.BottomEnd),
                        factory = { context ->
                            WebView(context).apply {
                                scraperWebViewRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = settings.userAgentString.replace("; wv", "")
                                addJavascriptInterface(
                                    ScraperInterface(
                                        onExtracted = { movies ->
                                            runOnUiThread {
                                                val savedHistory = WatchHistoryManager.getHistory(this@MainActivity)
                                                val current = currentState
                                                if (current is AppState.HomeLoaded) {
                                                    currentState = current.copy(
                                                        allMovies = movies,
                                                        watchHistory = if (current.watchHistory.isNotEmpty()) current.watchHistory else savedHistory
                                                    )
                                                } else if (current is AppState.WebLoading) {
                                                    currentState = AppState.HomeLoaded(
                                                        allMovies = movies,
                                                        watchHistory = savedHistory
                                                    )
                                                }
                                            }
                                        },
                                        onAnimeExtracted = { animes ->
                                            runOnUiThread {
                                                val current = currentState
                                                if (current is AppState.HomeLoaded) {
                                                    currentState = current.copy(
                                                        animeMovies = animes,
                                                        isLoadingAnime = false
                                                    )
                                                }
                                            }
                                        },
                                        onSeasonsCallback = { seasons ->
                                            runOnUiThread {
                                                val current = currentState
                                                if (current is AppState.SeriesDetails) {
                                                    val updatedSeasons = (current.availableSeasons + seasons).distinct().sorted()
                                                    currentState = current.copy(availableSeasons = updatedSeasons)
                                                }
                                            }
                                        },
                                        onEpisodesCallback = { season, newEps ->
                                            runOnUiThread {
                                                val current = currentState
                                                if (current is AppState.SeriesDetails) {
                                                    val merged = (current.episodes.filter { it.season != season } + newEps)
                                                        .distinctBy { it.url }
                                                        .sortedWith(compareBy({ it.season }, { it.number }))
                                                    val seasons = (current.availableSeasons + season).distinct().sorted()
                                                    currentState = current.copy(
                                                        episodes = merged,
                                                        availableSeasons = seasons,
                                                        isLoading = false,
                                                        isLoadingSeason = false
                                                    )
                                                }
                                            }
                                        }
                                    ),
                                    "AndroidScraper"
                                )
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        view?.evaluateJavascript("""
                                            (function() {
                                                var host = window.location.host;
                                                // 1. AniChan (Anime Tab) Scraping
                                                if (host.indexOf('anichan.to') !== -1) {
                                                    var path = window.location.pathname;
                                                    if (path.indexOf('/anime/') !== -1 && path.split('/').filter(Boolean).length >= 2) {
                                                        var eps = [];
                                                        document.querySelectorAll('a[href*="/anime/"]').forEach(function(a) {
                                                            var href = a.href;
                                                            var m = href.match(/\/anime\/\d+\/[^\/]+\/(\d+)/);
                                                            if (m) {
                                                                var epNum = parseInt(m[1], 10);
                                                                if (!eps.find(function(e) { return e.number === epNum; })) {
                                                                    var title = a.innerText.trim() || ('Episódio ' + epNum);
                                                                    eps.push({
                                                                        url: href,
                                                                        title: title,
                                                                        season: 1,
                                                                        number: epNum
                                                                    });
                                                                }
                                                            }
                                                        });
                                                        if (eps.length === 0) {
                                                            var baseHref = window.location.href.replace(/\/$/, '');
                                                            if (!baseHref.match(/\/\d+$/)) baseHref = baseHref + '/1';
                                                            eps.push({
                                                                url: baseHref,
                                                                title: 'Episódio 1',
                                                                season: 1,
                                                                number: 1
                                                            });
                                                        }
                                                        eps.sort(function(a, b) { return a.number - b.number; });
                                                        AndroidScraper.onEpisodesExtractedForSeason(1, JSON.stringify(eps));
                                                    } else {
                                                        function extractAniChan() {
                                                            var items = [];
                                                            document.querySelectorAll('a.card, a[href^="/anime/"]').forEach(function(a) {
                                                                var img = a.querySelector('img');
                                                                var href = a.href;
                                                                if (img && href && href.indexOf('/anime/') !== -1) {
                                                                    var t = img.getAttribute('alt') || a.getAttribute('title') || a.innerText.trim();
                                                                    var c = img.src || img.getAttribute('data-src') || '';
                                                                    if (c && c.indexOf(' ') !== -1) c = c.split(' ')[0];
                                                                    if (t && c && !items.find(function(x) { return x.url === href; })) {
                                                                        items.push({ title: t.trim(), cover: c, url: href });
                                                                    }
                                                                }
                                                            });
                                                            if (items.length > 0) {
                                                                var isSearch = window.location.href.indexOf('search') !== -1;
                                                                if (isSearch) {
                                                                    AndroidScraper.onDataExtracted(JSON.stringify(items));
                                                                } else {
                                                                    AndroidScraper.onAnimeExtracted(JSON.stringify(items));
                                                                }
                                                                return true;
                                                            }
                                                            return false;
                                                        }
                                                        extractAniChan();
                                                        setInterval(extractAniChan, 2000);
                                                    }
                                                    return;
                                                }

                                                // 2. Pobreflix Series & Movie Scraping
                                                var isSeriesPage = window.location.href.indexOf('/series/') !== -1;
                                                if (isSeriesPage) {
                                                    var seasonOpts = document.querySelectorAll('.vbSeasonSelect__option[data-season], [data-role="seasonMenu"] [data-season], [data-season]');
                                                    var foundSeasons = [];
                                                    seasonOpts.forEach(function(opt) {
                                                        var s = parseInt(opt.getAttribute('data-season') || opt.innerText.replace(/\D/g, ''), 10);
                                                        if (s && !foundSeasons.includes(s)) foundSeasons.push(s);
                                                    });
                                                    if (foundSeasons.length === 0) foundSeasons = [1];
                                                    foundSeasons.sort(function(a, b) { return a - b; });
                                                    AndroidScraper.onSeasonsDetected(JSON.stringify(foundSeasons));

                                                    var section = document.querySelector('.vbEpisodes');
                                                    var videoId = section ? (section.getAttribute('data-video-id') || '') : '';
                                                    if (!videoId) {
                                                        var el = document.querySelector('[data-video-id]');
                                                        if (el) videoId = el.getAttribute('data-video-id') || '';
                                                    }
                                                    if (videoId) {
                                                        var base = window.location.origin + '/';
                                                        foundSeasons.forEach(function(sNum) {
                                                            var fUrl = base + 'index.php?app=videobox&module=video&controller=view&do=episodesList&id=' + encodeURIComponent(videoId) + '&season=' + sNum + '&audio=Dublado';
                                                            fetch(fUrl, {
                                                                headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' }
                                                            })
                                                            .then(function(res) { return res.json(); })
                                                            .then(function(payload) {
                                                                if (payload && payload.episodes && payload.episodes.length > 0) {
                                                                    var eps = payload.episodes.map(function(ep) {
                                                                        return {
                                                                            url: ep.url,
                                                                            title: ep.title,
                                                                            season: sNum,
                                                                            number: parseInt(ep.number, 10) || 1
                                                                        };
                                                                    });
                                                                    AndroidScraper.onEpisodesExtractedForSeason(sNum, JSON.stringify(eps));
                                                                }
                                                            }).catch(function() {});
                                                        });
                                                    }

                                                    var links = document.querySelectorAll('a[href*="/episodios/online/"], .vbEpisodeCard a');
                                                    var fallbackEps = [];
                                                    links.forEach(function(a) {
                                                        var h = a.href;
                                                        if (h && !fallbackEps.find(function(i) { return i.url === h; })) {
                                                            var t = a.innerText.trim();
                                                            fallbackEps.push({ url: h, title: t });
                                                        }
                                                    });
                                                    if (fallbackEps.length > 0) {
                                                        AndroidScraper.onEpisodesExtracted(JSON.stringify(fallbackEps));
                                                    }
                                                } else {
                                                    function attemptExtract() {
                                                        var items = [];
                                                        document.querySelectorAll('a').forEach(function(a) {
                                                            var img = a.querySelector('img');
                                                            if (a && img) {
                                                                var t = img.getAttribute('alt') || a.getAttribute('title') || img.getAttribute('title') || a.innerText || 'Sem Título';
                                                                var c = img.src || img.getAttribute('data-src') || img.getAttribute('data-lazy-src') || '';
                                                                var u = a.href;
                                                                if (t && c && u && c.indexOf('data:image') === -1) {
                                                                    if (u.indexOf('/filmes/') !== -1 || u.indexOf('/series/') !== -1 || u.indexOf('/episodios/') !== -1) {
                                                                        if (!items.find(function(i) { return i.url === u; })) {
                                                                            items.push({ title: t.trim(), cover: c, url: u });
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        });
                                                        if (items.length > 0) {
                                                            AndroidScraper.onDataExtracted(JSON.stringify(items));
                                                            return true;
                                                        }
                                                        return false;
                                                    }
                                                    attemptExtract();
                                                    setInterval(attemptExtract, 2500);
                                                }
                                            })();
                                        """.trimIndent(), null)
                                    }
                                }
                                loadUrl("https://www.pobreflixtv.delivery/")
                            }
                        }
                    )

                    // UI States
                    when (val state = currentState) {
                        is AppState.WebLoading -> {
                            var showLoading by remember { mutableStateOf(true) }
                            LaunchedEffect(Unit) {
                                delay(6000)
                                showLoading = false
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0F0F12)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "A2 Miguel",
                                        color = Color(0xFFE50914),
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Criado por Miguel Rafael Montana",
                                        color = Color(0xFFA29BFE),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    CircularProgressIndicator(
                                        color = Color(0xFFE50914),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Carregando catálogo de filmes, séries e animes sem anúncios...",
                                        color = Color.LightGray,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        is AppState.HomeLoaded -> {
                            if (state.isSearching) {
                                SearchScreen(
                                    state = state,
                                    onMovieClick = { movie ->
                                        if (movie.isSeries || movie.isAnime) {
                                            currentState = AppState.SeriesDetails(series = movie, previousState = state)
                                        } else {
                                            // Save Movie to history
                                            val historyItem = WatchHistoryItem(
                                                id = movie.url,
                                                title = movie.title,
                                                coverUrl = movie.coverUrl,
                                                currentUrl = movie.url,
                                                isSeries = false,
                                                isAnime = false,
                                                season = 1,
                                                episodeNumber = 1,
                                                episodeTitle = movie.title
                                            )
                                            val updated = WatchHistoryManager.saveProgress(this@MainActivity, historyItem)
                                            currentState = AppState.Playing(
                                                url = movie.url,
                                                title = movie.title,
                                                isSeries = false,
                                                episodes = emptyList(),
                                                previousState = state.copy(watchHistory = updated)
                                            )
                                        }
                                    },
                                    onSearch = { query, source ->
                                        if (query.isNotBlank()) {
                                            currentState = state.copy(
                                                isLoadingSearch = true,
                                                isSearching = true,
                                                searchQuery = query
                                            )
                                            lifecycleScope.launch {
                                                val trimmed = query.trim()
                                                val results = if (source == "Animes") {
                                                    com.example.network.SearchService.searchAniChan(trimmed)
                                                } else {
                                                    com.example.network.SearchService.searchPobreflix(trimmed)
                                                }
                                                val localCatalog = if (source == "Animes") state.animeMovies else state.allMovies
                                                val localMatches = localCatalog.filter {
                                                    it.title.contains(trimmed, ignoreCase = true)
                                                }
                                                val combined = (results + localMatches).distinctBy { it.url }
                                                val current = currentState
                                                if (current is AppState.HomeLoaded) {
                                                    currentState = current.copy(
                                                        searchResults = combined,
                                                        isLoadingSearch = false
                                                    )
                                                }
                                            }
                                        } else {
                                            currentState = state.copy(
                                                isSearching = true,
                                                searchQuery = "",
                                                searchResults = emptyList(),
                                                isLoadingSearch = false
                                            )
                                        }
                                    },
                                    onClose = {
                                        currentState = state.copy(
                                            isSearching = false
                                        )
                                    }
                                )
                            } else {
                                NetflixHome(
                                    state = state,
                                    onMovieClick = { movie ->
                                        if (movie.isSeries || movie.isAnime) {
                                            currentState = AppState.SeriesDetails(series = movie, previousState = state)
                                        } else {
                                            val historyItem = WatchHistoryItem(
                                                id = movie.url,
                                                title = movie.title,
                                                coverUrl = movie.coverUrl,
                                                currentUrl = movie.url,
                                                isSeries = false,
                                                isAnime = false,
                                                season = 1,
                                                episodeNumber = 1,
                                                episodeTitle = movie.title
                                            )
                                            val updated = WatchHistoryManager.saveProgress(this@MainActivity, historyItem)
                                            currentState = AppState.Playing(
                                                url = movie.url,
                                                title = movie.title,
                                                isSeries = false,
                                                episodes = emptyList(),
                                                previousState = state.copy(watchHistory = updated)
                                            )
                                        }
                                    },
                                    onLoadMore = {
                                        currentState = state.copy(visibleCount = state.visibleCount + 12)
                                    },
                                    onOpenSearch = {
                                        currentState = state.copy(isSearching = true)
                                    },
                                    onSelectCategory = { cat ->
                                        currentState = state.copy(selectedCategory = cat)
                                    },
                                    onSelectTab = { tab ->
                                        if (tab == "Animes") {
                                            val shouldLoad = state.animeMovies.isEmpty()
                                            currentState = state.copy(
                                                currentTab = "Animes",
                                                isLoadingAnime = shouldLoad
                                            )
                                            if (shouldLoad) {
                                                scraperWebViewRef?.loadUrl("https://anichan.to/")
                                            }
                                        } else if (tab == "MeusTitulos") {
                                            val history = WatchHistoryManager.getHistory(this@MainActivity)
                                            currentState = state.copy(
                                                currentTab = "MeusTitulos",
                                                watchHistory = history
                                            )
                                        } else {
                                            val shouldLoad = state.allMovies.isEmpty()
                                            currentState = state.copy(currentTab = "Filmes")
                                            if (shouldLoad) {
                                                scraperWebViewRef?.loadUrl("https://www.pobreflixtv.delivery/")
                                            }
                                        }
                                    },
                                    onSelectAnimeGenre = { genre ->
                                        currentState = state.copy(selectedAnimeGenre = genre)
                                    },
                                    onSelectAnimeAudio = { audio ->
                                        currentState = state.copy(selectedAnimeAudio = audio)
                                    },
                                    onPlayHistoryItem = { item ->
                                        val updated = WatchHistoryManager.saveProgress(this@MainActivity, item)
                                        val title = if (item.isAnime) {
                                            "${item.title} - Episódio ${item.episodeNumber}"
                                        } else if (item.isSeries) {
                                            "${item.title} - T${item.season}E${item.episodeNumber}"
                                        } else {
                                            item.title
                                        }
                                        currentState = AppState.Playing(
                                            url = item.currentUrl,
                                            title = title,
                                            isSeries = item.isSeries || item.isAnime,
                                            episodes = emptyList(),
                                            currentEpisodeIndex = (item.episodeNumber - 1).coerceAtLeast(0),
                                            previousState = state.copy(watchHistory = updated)
                                        )
                                    },
                                    onOpenHistoryDetails = { item ->
                                        val m = Movie(
                                            title = item.title,
                                            coverUrl = item.coverUrl,
                                            url = item.id,
                                            isSeries = item.isSeries,
                                            isAnime = item.isAnime
                                        )
                                        currentState = AppState.SeriesDetails(
                                            series = m,
                                            previousState = state,
                                            selectedSeason = item.season
                                        )
                                    },
                                    onRemoveHistoryItem = { item ->
                                        val updated = WatchHistoryManager.removeItem(this@MainActivity, item.id)
                                        currentState = state.copy(watchHistory = updated)
                                    },
                                    onClearHistory = {
                                        val updated = WatchHistoryManager.clearAll(this@MainActivity)
                                        currentState = state.copy(watchHistory = updated)
                                    }
                                )
                            }
                        }

                        is AppState.SeriesDetails -> {
                            LaunchedEffect(state.series.url) {
                                scraperWebViewRef?.loadUrl(state.series.url)
                            }

                            SeriesDetailsScreen(
                                state = state,
                                onEpisodeClick = { ep ->
                                    val currentSeasonEps = if (state.episodes.isNotEmpty()) {
                                        state.episodes.filter { it.season == state.selectedSeason }
                                    } else {
                                        listOf(ep)
                                    }
                                    val epIndex = currentSeasonEps.indexOfFirst { it.url == ep.url }.coerceAtLeast(0)
                                    val title = if (state.series.isAnime) {
                                        "${state.series.title} - Episódio ${ep.number}"
                                    } else {
                                        "${state.series.title} - T${ep.season}E${ep.number}: ${ep.title}"
                                    }

                                    // Save series/anime progress to persistent watch history!
                                    val historyItem = WatchHistoryItem(
                                        id = state.series.url,
                                        title = state.series.title,
                                        coverUrl = state.series.coverUrl,
                                        currentUrl = ep.url,
                                        isSeries = true,
                                        isAnime = state.series.isAnime,
                                        season = ep.season,
                                        episodeNumber = ep.number,
                                        episodeTitle = ep.title
                                    )
                                    val updated = WatchHistoryManager.saveProgress(this@MainActivity, historyItem)

                                    currentState = AppState.Playing(
                                        url = ep.url,
                                        title = title,
                                        isSeries = true,
                                        episodes = if (currentSeasonEps.isNotEmpty()) currentSeasonEps else listOf(ep),
                                        currentEpisodeIndex = epIndex,
                                        previousState = state.previousState.copy(watchHistory = updated)
                                    )
                                },
                                onSeasonSelect = { season ->
                                    currentState = state.copy(
                                        selectedSeason = season,
                                        isLoadingSeason = true
                                    )
                                    loadSeason(season)
                                },
                                onBack = {
                                    currentState = state.previousState
                                }
                            )
                        }

                        is AppState.Playing -> {
                            PlayerScreen(
                                playingState = state,
                                onBackToPrevious = {
                                    val refreshedHistory = WatchHistoryManager.getHistory(this@MainActivity)
                                    val prev = state.previousState
                                    currentState = if (prev is AppState.HomeLoaded) {
                                        prev.copy(watchHistory = refreshedHistory)
                                    } else if (prev is AppState.SeriesDetails) {
                                        prev.copy(previousState = prev.previousState.copy(watchHistory = refreshedHistory))
                                    } else {
                                        AppState.HomeLoaded(allMovies = emptyList(), watchHistory = refreshedHistory)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

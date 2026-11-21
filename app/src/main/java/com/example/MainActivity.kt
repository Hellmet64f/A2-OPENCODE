package com.example

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.UiState
import com.example.data.UserDataRepository
import com.example.model.AppState
import com.example.model.Episode
import com.example.model.FocusCalibrationManager
import com.example.model.Movie
import com.example.model.ScreenZoomManager
import com.example.model.WatchHistoryItem
import com.example.model.WatchHistoryManager
import com.example.network.AdBlockManager
import com.example.network.SearchService
import com.example.ui.AuthScreen
import com.example.ui.GuestModePrefs
import com.example.ui.NetflixHome
import com.example.ui.PlayerScreen
import com.example.ui.SearchScreen
import com.example.ui.SeriesDetailsScreen
import com.example.ui.UserDataViewModel
import com.example.ui.signOutUser
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.EmeraldAdBlock
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

class ScraperInterface(
    private val onExtracted: (List<Movie>) -> Unit,
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
                    val normalizedU = SearchService.normalizeUrl(u)
                    list.add(
                        Movie(
                            title = t.trim(),
                            coverUrl = SearchService.normalizeUrl(c),
                            url = normalizedU,
                            isAnime = normalizedU.contains("anichan.to") || normalizedU.contains("/animes/")
                        )
                    )
                }
            }
            if (list.isNotEmpty()) onExtracted(list)
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
            if (seasons.isNotEmpty()) onSeasonsCallback(seasons.sorted())
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
                    list.add(Episode(t, SearchService.normalizeUrl(u), s, num))
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
                    list.add(Episode(t, SearchService.normalizeUrl(u), s, ep))
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
        var onOpenDownloadEditCallback: (() -> Unit)? = null
    }

    private var seekJob: kotlinx.coroutines.Job? = null

    private fun triggerSeekVideo(deltaSeconds: Int) {
        val playerWv = playerWebViewRef ?: return
        val msg = if (deltaSeconds < 0) "⏪ ${deltaSeconds}s" else "⏩ +${deltaSeconds}s"
        seekOsdMessage = msg
        seekJob?.cancel()
        seekJob = lifecycleScope.launch {
            delay(1300)
            if (seekOsdMessage == msg) seekOsdMessage = null
        }
        playerWv.evaluateJavascript(
            """
            (function() {
                var delta = $deltaSeconds;
                document.querySelectorAll('video').forEach(function(v) {
                    try { v.currentTime = Math.max(0, Math.min(v.duration || 999999, v.currentTime + delta)); } catch(e) {}
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
        playerWv?.evaluateJavascript(
            """
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
                    if (!toggled) {
                        var btns = document.querySelectorAll('.vjs-big-play-button, .play-btn, .btn-play, #play, .plyr__control--overlaid');
                        btns.forEach(function(b) { try { b.click(); } catch(e){} });
                    }
                } catch(err) {}
            })();
            """.trimIndent(),
            null
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val playerWv = playerWebViewRef
            if (currentState is AppState.Playing) {
                if (isCalibrationModeActive) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> { onMoveCalibrationReticle?.invoke(0f, -0.015f); return true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { onMoveCalibrationReticle?.invoke(0f, 0.015f); return true }
                        KeyEvent.KEYCODE_DPAD_LEFT -> { onMoveCalibrationReticle?.invoke(-0.015f, 0f); return true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { onMoveCalibrationReticle?.invoke(0.015f, 0f); return true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            onTestCalibrationClick?.invoke(); return true
                        }
                        KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_BUTTON_Y -> {
                            onSaveCalibration?.invoke(); return true
                        }
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                            onExitCalibration?.invoke(); return true
                        }
                    }
                    return true
                }

                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { triggerSeekVideo(-10); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { triggerSeekVideo(10); return true }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU -> {
                        arePlayerControlsVisible = true; return true
                    }
                    KeyEvent.KEYCODE_VOLUME_UP -> { onNextEpisodeCallback?.let { it(); return true } }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> { onPrevEpisodeCallback?.let { it(); return true } }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        triggerVideoPlayPause(playerWv); return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (!arePlayerControlsVisible) {
                            triggerVideoPlayPause(playerWv); return true
                        }
                    }
                    KeyEvent.KEYCODE_PROG_YELLOW, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_ZOOM_IN, KeyEvent.KEYCODE_ZOOM_OUT -> {
                        onToggleZoomCallback?.let { it(); return true }
                    }
                    KeyEvent.KEYCODE_D -> {
                        onOpenDownloadEditCallback?.let { it(); return true }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun loadSeasonInScraper(seasonNum: Int, audio: String = "Dublado") {
        val safeAudio = if (audio == "Legendado") "Legendado" else "Dublado"
        scraperWebViewRef?.evaluateJavascript(
            """
            (function() {
                var sNum = $seasonNum;
                var section = document.querySelector('.vbEpisodes');
                var videoId = section ? (section.getAttribute('data-video-id') || '') : '';
                if (!videoId) {
                    var el = document.querySelector('[data-video-id]');
                    if (el) videoId = el.getAttribute('data-video-id') || '';
                }
                if (videoId) {
                    var base = window.location.origin + '/';
                    var url = base + 'index.php?app=videobox&module=video&controller=view&do=episodesList&id=' + encodeURIComponent(videoId) + '&season=' + sNum + '&audio=' + encodeURIComponent('$safeAudio');
                    fetch(url, { headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' } })
                    .then(function(res) { return res.json(); })
                    .then(function(data) {
                        if (data && data.episodes && data.episodes.length > 0) {
                            var eps = data.episodes.map(function(e) {
                                return { url: e.url, title: e.title, season: sNum, number: parseInt(e.number, 10) || 1 };
                            });
                            AndroidScraper.onEpisodesExtractedForSeason(sNum, JSON.stringify(eps));
                        }
                    }).catch(function() {});
                }
            })();
            """.trimIndent(),
            null
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AdBlockManager.initialize(this)
        lifecycleScope.launch {
            AdBlockManager.autoUpdateIfNeeded(this@MainActivity, force = false)
        }

        setContent {
            MyApplicationTheme {
                var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }
                var isGuestMode by remember {
                    mutableStateOf(currentUser == null && GuestModePrefs.isGuestMode(this))
                }
                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { auth ->
                        val nextUser = auth.currentUser
                        currentUser = nextUser
                        if (nextUser != null) {
                            GuestModePrefs.setGuestMode(this@MainActivity, false)
                            isGuestMode = false
                        }
                    }
                    Firebase.auth.addAuthStateListener(listener)
                    onDispose { Firebase.auth.removeAuthStateListener(listener) }
                }

                if (currentUser == null && !isGuestMode) {
                    AuthScreen(
                        onAuthSuccess = {
                            GuestModePrefs.setGuestMode(this@MainActivity, false)
                            isGuestMode = false
                            currentUser = Firebase.auth.currentUser
                        },
                        onContinueAsGuest = {
                            GuestModePrefs.setGuestMode(this@MainActivity, true)
                            isGuestMode = true
                        }
                    )
                } else {
                    val sessionKey = currentUser?.uid ?: "guest_local_session"
                    key(sessionKey) {
                        AuthenticatedMainApp(
                            user = currentUser,
                            isGuestMode = currentUser == null && isGuestMode,
                            onSwitchToGoogleLogin = {
                                GuestModePrefs.setGuestMode(this@MainActivity, false)
                                isGuestMode = false
                            },
                            onTriggerSeasonLoad = { season, audio -> loadSeasonInScraper(season, audio) }
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun AuthenticatedMainApp(
        user: FirebaseUser?,
        isGuestMode: Boolean,
        onSwitchToGoogleLogin: () -> Unit,
        onTriggerSeasonLoad: (Int, String) -> Unit
    ) {
        val context = LocalContext.current
        val userViewModel: UserDataViewModel? = if (user != null) {
            val repository = remember(user.uid) { UserDataRepository(context) }
            viewModel(
                key = user.uid,
                factory = UserDataViewModel.Factory(user.uid, repository)
            )
        } else {
            null
        }

        val historyState by (userViewModel?.historyState?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf<UiState<List<WatchHistoryItem>>>(UiState.Success(WatchHistoryManager.getHistory(context))) })
        val prefsState by (userViewModel?.preferencesState?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf<UiState<com.example.data.UserPreferencesDoc?>>(UiState.Success(null)) })
        val syncMessage by (userViewModel?.syncMessage?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf<String?>(null) })

        val adBlockRuleCount by AdBlockManager.ruleCountFlow.collectAsStateWithLifecycle()
        val adBlockBlockedSession by AdBlockManager.blockedSessionCount.collectAsStateWithLifecycle()
        val adBlockUpdating by AdBlockManager.isUpdating.collectAsStateWithLifecycle()
        var adBlockAutoUpdate by remember { mutableStateOf(AdBlockManager.isAutoUpdateEnabled(context)) }
        val credentialManager = remember(context) { CredentialManager.create(context) }

        LaunchedEffect(syncMessage) {
            val msg = syncMessage
            if (!msg.isNullOrBlank()) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                userViewModel?.clearSyncMessage()
            }
        }

        // Sync cloud history down into local cache and AppState whenever Firestore updates
        LaunchedEffect(historyState, user?.uid) {
            if (userViewModel == null) return@LaunchedEffect
            val loaded = (historyState as? UiState.Success)?.data ?: return@LaunchedEffect
            val pendingGuestSync = GuestModePrefs.hasPendingGuestSync(context)
            val localOnly = WatchHistoryManager.getHistory(context)
            if (pendingGuestSync && localOnly.isNotEmpty()) {
                val cloudById = loaded.associateBy { it.id }
                val mergedMap = linkedMapOf<String, WatchHistoryItem>()
                (loaded + localOnly).forEach { item ->
                    val existing = mergedMap[item.id]
                    if (existing == null) {
                        mergedMap[item.id] = item
                    } else {
                        val watched = (existing.watchedEpisodes + item.watchedEpisodes).distinct()
                        val winner = if (item.timestamp >= existing.timestamp) item else existing
                        mergedMap[item.id] = winner.copy(
                            watchedEpisodes = watched,
                            favorite = existing.favorite || item.favorite,
                            completed = existing.completed || item.completed
                        )
                    }
                }
                val finalList = WatchHistoryManager.replaceHistory(context, mergedMap.values.toList())
                GuestModePrefs.clearPendingGuestSync(context)
                val unsynced = finalList.filter { item ->
                    val c = cloudById[item.id]
                    c == null || item.timestamp > c.timestamp
                }
                if (unsynced.isNotEmpty()) {
                    userViewModel.syncLocalBatch(unsynced)
                }
                val st = currentState
                if (st is AppState.HomeLoaded) {
                    currentState = st.copy(watchHistory = finalList)
                }
            } else if (loaded.isNotEmpty()) {
                val merged = WatchHistoryManager.replaceHistory(context, loaded)
                val st = currentState
                if (st is AppState.HomeLoaded) {
                    currentState = st.copy(watchHistory = merged)
                }
            } else if (localOnly.isNotEmpty()) {
                userViewModel.syncLocalBatch(localOnly)
            }
        }

        // Sync cloud preferences down into local managers
        LaunchedEffect(prefsState, user?.uid) {
            if (userViewModel == null) return@LaunchedEffect
            val p = (prefsState as? UiState.Success)?.data ?: return@LaunchedEffect
            ScreenZoomManager.saveZoomConfig(context, p.zoomScale.toFloat(), 0f, 0f, p.zoomPreset.uppercase())
            FocusCalibrationManager.saveCalibration(context, p.focusX.toFloat(), p.focusY.toFloat())
            adBlockAutoUpdate = p.adBlockAutoUpdate
            AdBlockManager.setAutoUpdateEnabled(context, p.adBlockAutoUpdate)
            if (p.recentSearches.isNotEmpty()) {
                WatchHistoryManager.saveRecentSearches(context, p.recentSearches)
                val st = currentState
                if (st is AppState.HomeLoaded) {
                    currentState = st.copy(recentSearches = p.recentSearches)
                }
            }
        }

        // Carrega catálogo AniChan (Animes) em paralelo logo no início + fallback OkHttp caso WebView demore >2.2s
        LaunchedEffect(Unit) {
            val aniDeferred = async { SearchService.fetchAniChanCatalog() }
            delay(2200)
            val aniList = try { aniDeferred.await() } catch (_: Exception) { emptyList() }
            if (currentState is AppState.WebLoading) {
                val okHttpMovies = try { SearchService.fetchPobreflixHomeCatalog() } catch (_: Exception) { emptyList() }
                val combined = (okHttpMovies + aniList).distinctBy { it.url }
                val savedHistory = WatchHistoryManager.getHistory(context)
                val recents = WatchHistoryManager.getRecentSearches(context)
                if (combined.isNotEmpty() && currentState is AppState.WebLoading) {
                    currentState = AppState.HomeLoaded(
                        allMovies = combined,
                        watchHistory = savedHistory,
                        recentSearches = recents
                    )
                }
            } else if (aniList.isNotEmpty()) {
                val cur = currentState
                if (cur is AppState.HomeLoaded) {
                    currentState = cur.copy(
                        allMovies = (cur.allMovies + aniList).distinctBy { it.url }
                    )
                }
            }
        }

        fun syncCurrentPreferences() {
            val vm = userViewModel ?: return
            val zoom = ScreenZoomManager.getZoomConfig(context)
            val cal = FocusCalibrationManager.getCalibration(context)
            val recents = WatchHistoryManager.getRecentSearches(context)
            vm.savePreferences(
                zoomPreset = zoom.preset,
                zoomScale = zoom.scale.toDouble(),
                focusX = cal.first.toDouble(),
                focusY = cal.second.toDouble(),
                adBlockAutoUpdate = adBlockAutoUpdate,
                recentSearches = recents
            )
        }

        fun saveAndSyncHistoryItem(item: WatchHistoryItem, markCurrentWatched: Boolean = true): List<WatchHistoryItem> {
            val updatedList = WatchHistoryManager.saveProgress(context, item, markCurrentWatched)
            updatedList.firstOrNull { it.id == item.id }?.let { finalItem ->
                userViewModel?.saveHistoryItem(finalItem)
            }
            return updatedList
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
        ) {
            // Hidden Background Pobreflix Scraper WebView (Zero AniChan)
            AndroidView(
                modifier = Modifier
                    .size(1.dp)
                    .align(Alignment.BottomEnd),
                factory = { ctx ->
                    WebView(ctx).apply {
                        scraperWebViewRef = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = settings.userAgentString.replace("; wv", "")
                        addJavascriptInterface(
                            ScraperInterface(
                                onExtracted = { movies ->
                                    runOnUiThread {
                                        val savedHistory = WatchHistoryManager.getHistory(context)
                                        val recents = WatchHistoryManager.getRecentSearches(context)
                                        val current = currentState
                                        if (current is AppState.HomeLoaded) {
                                            val mergedMovies = (movies + current.allMovies).distinctBy { it.url }
                                            currentState = current.copy(
                                                allMovies = mergedMovies,
                                                watchHistory = if (current.watchHistory.isNotEmpty()) current.watchHistory else savedHistory,
                                                recentSearches = if (current.recentSearches.isNotEmpty()) current.recentSearches else recents,
                                                isRefreshingCatalog = false
                                            )
                                        } else if (current is AppState.WebLoading) {
                                            currentState = AppState.HomeLoaded(
                                                allMovies = movies,
                                                watchHistory = savedHistory,
                                                recentSearches = recents
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
                                view?.evaluateJavascript(
                                    """
                                    (function() {
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
                                                    fetch(fUrl, { headers: { 'X-Requested-With': 'XMLHttpRequest', 'Accept': 'application/json' } })
                                                    .then(function(res) { return res.json(); })
                                                    .then(function(payload) {
                                                        if (payload && payload.episodes && payload.episodes.length > 0) {
                                                            var eps = payload.episodes.map(function(ep) {
                                                                return { url: ep.url, title: ep.title, season: sNum, number: parseInt(ep.number, 10) || 1 };
                                                            });
                                                            AndroidScraper.onEpisodesExtractedForSeason(sNum, JSON.stringify(eps));
                                                        }
                                                    }).catch(function() {});
                                                });
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
                                                            if (u.indexOf('/filmes/') !== -1 || u.indexOf('/series/') !== -1 || u.indexOf('/episodios/') !== -1 || u.indexOf('/animes/') !== -1) {
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
                                            setTimeout(attemptExtract, 1800);
                                        }
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }
                        }
                        loadUrl("https://www.pobreflixtv.codes/")
                    }
                }
            )

            when (val state = currentState) {
                is AppState.WebLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ObsidianBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_cinema_backdrop),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.3f)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(ObsidianBg.copy(alpha = 0.6f), ObsidianBg)
                                    )
                                )
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_a2_logo),
                                contentDescription = "A2 Miguel",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            Text(
                                text = "A2 MIGUEL",
                                color = CrimsonPrimary,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "Filmes, Séries e Animes • AdBlock $adBlockRuleCount+ Ativo",
                                color = EmeraldAdBlock,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            CircularProgressIndicator(color = CrimsonPrimary, modifier = Modifier.size(34.dp))
                            Text(
                                text = "Carregando catálogo e histórico na nuvem...",
                                color = TextSecondary,
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
                                    val historyItem = WatchHistoryItem(
                                        id = movie.url,
                                        title = movie.title,
                                        coverUrl = movie.coverUrl,
                                        currentUrl = movie.url,
                                        isSeries = false,
                                        isAnime = movie.isAnime,
                                        season = 1,
                                        episodeNumber = 1,
                                        episodeTitle = movie.title
                                    )
                                    val updated = saveAndSyncHistoryItem(historyItem, markCurrentWatched = true)
                                    currentState = AppState.Playing(
                                        url = movie.url,
                                        title = movie.title,
                                        seriesUrl = movie.url,
                                        coverUrl = movie.coverUrl,
                                        isSeries = false,
                                        previousState = state.copy(watchHistory = updated, isSearching = false)
                                    )
                                }
                            },
                            onSearch = { query, saveRecent ->
                                val trimmed = query.trim()
                                val latest = currentState as? AppState.HomeLoaded ?: state
                                if (trimmed.isNotBlank()) {
                                    val updatedRecents = if (saveRecent) {
                                        WatchHistoryManager.addRecentSearch(context, trimmed)
                                    } else {
                                        latest.recentSearches
                                    }
                                    currentState = latest.copy(
                                        isLoadingSearch = true,
                                        isSearching = true,
                                        searchQuery = trimmed,
                                        recentSearches = updatedRecents
                                    )
                                    if (saveRecent) syncCurrentPreferences()
                                    lifecycleScope.launch {
                                        val remote = SearchService.searchAll(trimmed)
                                        val localMatches = latest.allMovies.filter {
                                            it.title.contains(trimmed, ignoreCase = true)
                                        }
                                        val combined = (remote + localMatches).distinctBy { it.url }
                                        val cur = currentState
                                        if (cur is AppState.HomeLoaded && cur.searchQuery == trimmed) {
                                            currentState = cur.copy(
                                                searchResults = combined,
                                                isLoadingSearch = false
                                            )
                                        }
                                    }
                                } else {
                                    currentState = latest.copy(
                                        isSearching = true,
                                        searchQuery = "",
                                        searchResults = emptyList(),
                                        isLoadingSearch = false
                                    )
                                }
                            },
                            onRemoveRecentSearch = { q ->
                                val updated = WatchHistoryManager.removeRecentSearch(context, q)
                                currentState = state.copy(recentSearches = updated)
                                syncCurrentPreferences()
                            },
                            onClose = {
                                currentState = state.copy(isSearching = false)
                            }
                        )
                    } else {
                        NetflixHome(
                            state = state,
                            userEmail = user?.email,
                            userName = user?.displayName,
                            isGuestMode = isGuestMode,
                            adBlockRuleCount = adBlockRuleCount,
                            adBlockBlockedSession = adBlockBlockedSession,
                            adBlockUpdating = adBlockUpdating,
                            adBlockAutoUpdate = adBlockAutoUpdate,
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
                                        isAnime = movie.isAnime,
                                        season = 1,
                                        episodeNumber = 1,
                                        episodeTitle = movie.title
                                    )
                                    val updated = saveAndSyncHistoryItem(historyItem, markCurrentWatched = true)
                                    currentState = AppState.Playing(
                                        url = movie.url,
                                        title = movie.title,
                                        seriesUrl = movie.url,
                                        coverUrl = movie.coverUrl,
                                        isSeries = false,
                                        previousState = state.copy(watchHistory = updated)
                                    )
                                }
                            },
                            onLoadMore = {
                                currentState = state.copy(visibleCount = state.visibleCount + 18)
                            },
                            onRefreshCatalog = {
                                currentState = state.copy(isRefreshingCatalog = true)
                                lifecycleScope.launch {
                                    val (freshPf, freshAni) = coroutineScope {
                                        val pfDef = async { SearchService.fetchPobreflixHomeCatalog() }
                                        val aniDef = async { SearchService.fetchAniChanCatalog() }
                                        pfDef.await() to aniDef.await()
                                    }
                                    val cur = currentState
                                    if (cur is AppState.HomeLoaded) {
                                        currentState = cur.copy(
                                            allMovies = (freshPf + freshAni + cur.allMovies).distinctBy { it.url },
                                            isRefreshingCatalog = false
                                        )
                                    }
                                }
                                scraperWebViewRef?.loadUrl("https://www.pobreflixtv.codes/")
                            },
                            onOpenSearch = {
                                currentState = state.copy(isSearching = true)
                            },
                            onSelectCategory = { cat ->
                                currentState = state.copy(selectedCategory = cat)
                            },
                            onSelectTab = { tab ->
                                if (tab == "MeusTitulos") {
                                    val history = WatchHistoryManager.getHistory(context)
                                    currentState = state.copy(currentTab = "MeusTitulos", watchHistory = history)
                                } else {
                                    currentState = state.copy(currentTab = tab)
                                }
                            },
                            onPlayHistoryItem = { item ->
                                val updated = saveAndSyncHistoryItem(item, markCurrentWatched = false)
                                val title = if (item.isSeries) "${item.title} - T${item.season}E${item.episodeNumber}" else item.title
                                currentState = AppState.Playing(
                                    url = item.currentUrl,
                                    title = title,
                                    seriesUrl = item.id,
                                    coverUrl = item.coverUrl,
                                    isSeries = item.isSeries,
                                    season = item.season,
                                    currentEpisodeIndex = (item.episodeNumber - 1).coerceAtLeast(0),
                                    previousState = state.copy(watchHistory = updated)
                                )
                            },
                            onRestartHistoryItem = { item ->
                                if (item.isSeries || item.isAnime) {
                                    val m = Movie(
                                        title = item.title,
                                        coverUrl = item.coverUrl,
                                        url = item.id,
                                        isAnime = item.isAnime,
                                        isSeries = true
                                    )
                                    currentState = AppState.SeriesDetails(series = m, previousState = state, selectedSeason = 1)
                                } else {
                                    val restarted = item.copy(progressSeconds = 0L, completed = false)
                                    val updated = saveAndSyncHistoryItem(restarted, markCurrentWatched = false)
                                    currentState = AppState.Playing(
                                        url = item.id,
                                        title = item.title,
                                        seriesUrl = item.id,
                                        coverUrl = item.coverUrl,
                                        isSeries = false,
                                        previousState = state.copy(watchHistory = updated)
                                    )
                                }
                            },
                            onOpenHistoryDetails = { item ->
                                val m = Movie(
                                    title = item.title,
                                    coverUrl = item.coverUrl,
                                    url = item.id,
                                    isAnime = item.isAnime,
                                    isSeries = item.isSeries || item.isAnime
                                )
                                currentState = AppState.SeriesDetails(
                                    series = m,
                                    previousState = state,
                                    selectedSeason = item.season
                                )
                            },
                            onToggleFavoriteMovie = { movie ->
                                val (updatedList, updatedItem) = WatchHistoryManager.toggleFavorite(context, movie)
                                userViewModel?.saveHistoryItem(updatedItem)
                                currentState = state.copy(watchHistory = updatedList)
                            },
                            onToggleCompletedHistory = { item ->
                                val (updatedList, updatedItem) = WatchHistoryManager.markCompleted(context, item.id, !item.completed)
                                if (updatedItem != null) userViewModel?.saveHistoryItem(updatedItem)
                                currentState = state.copy(watchHistory = updatedList)
                            },
                            onRemoveHistoryItem = { item ->
                                val updated = WatchHistoryManager.removeItem(context, item.id)
                                userViewModel?.removeHistoryItem(item.id)
                                currentState = state.copy(watchHistory = updated)
                            },
                            onClearHistory = {
                                val updated = WatchHistoryManager.clearAll(context)
                                userViewModel?.clearAllHistory()
                                currentState = state.copy(watchHistory = updated)
                            },
                            onToggleAdBlockAutoUpdate = { enabled ->
                                adBlockAutoUpdate = enabled
                                AdBlockManager.setAutoUpdateEnabled(context, enabled)
                                syncCurrentPreferences()
                            },
                            onForceUpdateAdBlock = {
                                lifecycleScope.launch {
                                    val count = AdBlockManager.autoUpdateIfNeeded(context, force = true)
                                    Toast.makeText(context, "AdBlock atualizado: $count regras ativas!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSyncCloudNow = {
                                userViewModel?.syncLocalBatch(state.watchHistory)
                                syncCurrentPreferences()
                            },
                            onImportJsonBackup = { jsonText ->
                                val merged = WatchHistoryManager.importFromJson(context, jsonText)
                                val recents = WatchHistoryManager.getRecentSearches(context)
                                currentState = state.copy(watchHistory = merged, recentSearches = recents)
                                userViewModel?.syncLocalBatch(merged)
                                syncCurrentPreferences()
                            },
                            onSwitchToGoogleLogin = onSwitchToGoogleLogin,
                            onSignOut = {
                                signOutUser(credentialManager, lifecycleScope)
                            }
                        )
                    }
                }

                is AppState.SeriesDetails -> {
                    var seriesVideoId by remember(state.series.url) { mutableStateOf<String?>(null) }

                    LaunchedEffect(state.series.url) {
                        scraperWebViewRef?.loadUrl(state.series.url)
                        val meta = SearchService.fetchSeriesMetadata(state.series.url)
                        if (meta != null) {
                            if (meta.videoId.isNotBlank()) seriesVideoId = meta.videoId
                            val cur = currentState
                            if (cur is AppState.SeriesDetails && cur.series.url == state.series.url) {
                                val seasons = (cur.availableSeasons + meta.availableSeasons).distinct().sorted()
                                val seasonEps = if (meta.videoId.isNotBlank()) {
                                    SearchService.fetchSeasonEpisodes(meta.videoId, cur.selectedSeason, cur.selectedAudio)
                                } else meta.fallbackEpisodes
                                val mergedEps = (cur.episodes + seasonEps).distinctBy { it.url }
                                    .sortedWith(compareBy({ it.season }, { it.number }))
                                currentState = cur.copy(
                                    availableSeasons = seasons,
                                    episodes = mergedEps,
                                    isLoading = false,
                                    isLoadingSeason = false
                                )
                            }
                        }
                    }

                    val existingHistory = remember(state.previousState.watchHistory, state.series.url) {
                        state.previousState.watchHistory.find { it.id == state.series.url }
                    }

                    fun launchEpisode(ep: Episode) {
                        val currentSeasonEps = state.episodes.filter { it.season == ep.season }.ifEmpty { listOf(ep) }
                        val epIndex = currentSeasonEps.indexOfFirst { it.url == ep.url }.coerceAtLeast(0)
                        val title = "${state.series.title} - T${ep.season}E${ep.number}: ${ep.title}"
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
                        val updatedHistory = saveAndSyncHistoryItem(historyItem, markCurrentWatched = true)
                        currentState = AppState.Playing(
                            url = ep.url,
                            title = title,
                            seriesUrl = state.series.url,
                            coverUrl = state.series.coverUrl,
                            isSeries = true,
                            season = ep.season,
                            availableSeasons = state.availableSeasons,
                            episodes = state.episodes.ifEmpty { currentSeasonEps },
                            currentEpisodeIndex = epIndex,
                            previousState = state.previousState.copy(watchHistory = updatedHistory)
                        )
                    }

                    SeriesDetailsScreen(
                        state = state,
                        historyItem = existingHistory,
                        onEpisodeClick = { ep -> launchEpisode(ep) },
                        onContinueSeries = {
                            val h = existingHistory
                            if (h != null) {
                                val matchingEp = state.episodes.find { it.season == h.season && it.number == h.episodeNumber }
                                    ?: Episode(title = h.episodeTitle.ifBlank { "Episódio ${h.episodeNumber}" }, url = h.currentUrl, season = h.season, number = h.episodeNumber)
                                launchEpisode(matchingEp)
                            }
                        },
                        onRestartSeries = {
                            val firstEp = state.episodes.filter { it.season == 1 }.minByOrNull { it.number }
                                ?: state.episodes.firstOrNull()
                            if (firstEp != null) {
                                launchEpisode(firstEp)
                            } else if (!seriesVideoId.isNullOrBlank()) {
                                lifecycleScope.launch {
                                    val s1 = SearchService.fetchSeasonEpisodes(seriesVideoId!!, 1, state.selectedAudio)
                                    s1.firstOrNull()?.let { launchEpisode(it) }
                                }
                            }
                        },
                        onToggleFavorite = {
                            val (updatedList, updatedItem) = WatchHistoryManager.toggleFavorite(context, state.series)
                            userViewModel?.saveHistoryItem(updatedItem)
                            currentState = state.copy(previousState = state.previousState.copy(watchHistory = updatedList))
                        },
                        onSeasonSelect = { season ->
                            currentState = state.copy(selectedSeason = season, isLoadingSeason = true)
                            onTriggerSeasonLoad(season, state.selectedAudio)
                            lifecycleScope.launch {
                                val vid = seriesVideoId ?: SearchService.fetchSeriesMetadata(state.series.url)?.videoId.orEmpty()
                                if (vid.isNotBlank()) {
                                    seriesVideoId = vid
                                    val eps = SearchService.fetchSeasonEpisodes(vid, season, state.selectedAudio)
                                    val cur = currentState
                                    if (cur is AppState.SeriesDetails) {
                                        val merged = (cur.episodes.filter { it.season != season } + eps)
                                            .distinctBy { it.url }
                                            .sortedWith(compareBy({ it.season }, { it.number }))
                                        currentState = cur.copy(
                                            episodes = merged,
                                            isLoading = false,
                                            isLoadingSeason = false
                                        )
                                    }
                                }
                            }
                        },
                        onAudioSelect = { audio ->
                            currentState = state.copy(selectedAudio = audio, isLoadingSeason = true)
                            onTriggerSeasonLoad(state.selectedSeason, audio)
                            lifecycleScope.launch {
                                val vid = seriesVideoId ?: SearchService.fetchSeriesMetadata(state.series.url)?.videoId.orEmpty()
                                if (vid.isNotBlank()) {
                                    seriesVideoId = vid
                                    val eps = SearchService.fetchSeasonEpisodes(vid, state.selectedSeason, audio)
                                    val cur = currentState
                                    if (cur is AppState.SeriesDetails) {
                                        val merged = (cur.episodes.filter { it.season != state.selectedSeason } + eps)
                                            .distinctBy { it.url }
                                            .sortedWith(compareBy({ it.season }, { it.number }))
                                        currentState = cur.copy(
                                            episodes = merged,
                                            isLoading = false,
                                            isLoadingSeason = false
                                        )
                                    }
                                }
                            }
                        },
                        onBack = { currentState = state.previousState }
                    )
                }

                is AppState.Playing -> {
                    val seriesHistory = remember(state.previousState, state.seriesUrl) {
                        val homeHist = when (val prev = state.previousState) {
                            is AppState.HomeLoaded -> prev.watchHistory
                            is AppState.SeriesDetails -> prev.previousState.watchHistory
                            else -> emptyList()
                        }
                        homeHist.find { it.id == state.seriesUrl }
                    }
                    PlayerScreen(
                        playingState = state,
                        watchedEpisodeKeys = seriesHistory?.watchedEpisodes.orEmpty().toSet(),
                        onHistoryProgressSaved = { item ->
                            userViewModel?.saveHistoryItem(item)
                        },
                        onPreferencesChanged = { _, _, _, _ ->
                            syncCurrentPreferences()
                        },
                        onBackToPrevious = {
                            val refreshedHistory = WatchHistoryManager.getHistory(context)
                            val prev = state.previousState
                            currentState = when (prev) {
                                is AppState.HomeLoaded -> prev.copy(watchHistory = refreshedHistory)
                                is AppState.SeriesDetails -> prev.copy(previousState = prev.previousState.copy(watchHistory = refreshedHistory))
                                else -> AppState.HomeLoaded(allMovies = emptyList(), watchHistory = refreshedHistory)
                            }
                        }
                    )
                }
            }
        }
    }
}

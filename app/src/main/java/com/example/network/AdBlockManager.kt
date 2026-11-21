package com.example.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Motor AdBlock 60+ com atualização automática remota e cache local.
 * Protege o player contra popups, redirecionamentos, rastreadores e anúncios de apostas.
 */
object AdBlockManager {
    private const val PREFS_NAME = "a2_miguel_adblock_prefs"
    private const val KEY_LAST_UPDATE = "adblock_last_update"
    private const val KEY_AUTO_UPDATE = "adblock_auto_update"
    private const val CACHE_FILE_NAME = "adblock_dynamic_rules.txt"
    private const val UPDATE_INTERVAL_MS = 12L * 60L * 60L * 1000L // 12 horas

    // 72 regras nativas sempre ativas (Popups, Vídeo Ads, Apostas, Trackers, Redirects)
    val BASELINE_RULES: List<String> = listOf(
        "googlesyndication.com",
        "doubleclick.net",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "propellerclick.com",
        "onclickads.net",
        "exoclick.com",
        "exosrv.com",
        "realsrv.com",
        "syndication.realsrv.com",
        "adsterra.com",
        "highperformanceformat.com",
        "effectivegatecpm.com",
        "monetag.com",
        "al5sm.com",
        "trafficjunky.net",
        "trafficfactory.biz",
        "adcash.com",
        "acscdn.com",
        "yllix.com",
        "taboola.com",
        "outbrain.com",
        "mgid.com",
        "revcontent.com",
        "juicyads.com",
        "plugrush.com",
        "hilltopads.net",
        "richads.com",
        "clickadu.com",
        "admaven.com",
        "bidvertiser.com",
        "infolinks.com",
        "criteo.com",
        "criteo.net",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "appnexus.com",
        "adnxs.com",
        "smartadserver.com",
        "mediavine.com",
        "adthrive.com",
        "1xbet",
        "bet365",
        "betano",
        "blaze.com",
        "sportingbet",
        "pixbet",
        "estrelabet",
        "cassino",
        "casino",
        "aposta",
        "tigrinho",
        "fortune-tiger",
        "oldnessdernly.qpon",
        "parklogic.com",
        "pushwoosh.com",
        "onesignal.com",
        "hotjar.com",
        "histats.com",
        "amung.us",
        "whos.amung.us",
        "adf.ly",
        "ouo.io",
        "shorte.st",
        "linkvertise.com"
    )

    private val REMOTE_FILTER_SOURCES = listOf(
        "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=plain&showintro=0&mimetype=plaintext",
        "https://raw.githubusercontent.com/AdAway/adaway.github.io/master/hosts.txt"
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var activeRulesSet: Set<String> = BASELINE_RULES.toSet()

    private val _ruleCountFlow = MutableStateFlow(BASELINE_RULES.size)
    val ruleCountFlow: StateFlow<Int> = _ruleCountFlow.asStateFlow()

    private val _blockedSessionCount = MutableStateFlow(0)
    val blockedSessionCount: StateFlow<Int> = _blockedSessionCount.asStateFlow()

    private val _lastUpdateTimestamp = MutableStateFlow(0L)
    val lastUpdateTimestamp: StateFlow<Long> = _lastUpdateTimestamp.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    fun initialize(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _lastUpdateTimestamp.value = prefs.getLong(KEY_LAST_UPDATE, 0L)

        val cacheFile = File(appCtx.filesDir, CACHE_FILE_NAME)
        val merged = LinkedHashSet<String>(BASELINE_RULES)
        if (cacheFile.exists()) {
            try {
                cacheFile.readLines().forEach { line ->
                    val clean = line.trim().lowercase()
                    if (clean.length >= 4 && !clean.startsWith("#")) {
                        merged.add(clean)
                    }
                }
            } catch (_: Exception) {}
        }
        activeRulesSet = merged
        _ruleCountFlow.value = merged.size
    }

    fun isAutoUpdateEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_UPDATE, true)
    }

    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_UPDATE, enabled)
            .apply()
    }

    suspend fun autoUpdateIfNeeded(context: Context, force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        initialize(appCtx)

        if (!force && !isAutoUpdateEnabled(appCtx)) {
            return@withContext activeRulesSet.size
        }

        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_UPDATE, 0L)
        val now = System.currentTimeMillis()
        if (!force && (now - last) < UPDATE_INTERVAL_MS && activeRulesSet.size >= BASELINE_RULES.size) {
            return@withContext activeRulesSet.size
        }

        _isUpdating.value = true
        val downloadedDomains = LinkedHashSet<String>()
        try {
            for (sourceUrl in REMOTE_FILTER_SOURCES) {
                try {
                    val req = Request.Builder()
                        .url(sourceUrl)
                        .get()
                        .addHeader("User-Agent", "A2Miguel-AdBlock/2.0")
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            body.lineSequence().forEach { rawLine ->
                                if (downloadedDomains.size >= 1500) return@forEach
                                val domain = parseHostLine(rawLine)
                                if (domain != null && !isWhitelistedDomain(domain)) {
                                    downloadedDomains.add(domain)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            if (downloadedDomains.isNotEmpty()) {
                val merged = LinkedHashSet<String>(BASELINE_RULES)
                merged.addAll(downloadedDomains)
                activeRulesSet = merged
                _ruleCountFlow.value = merged.size

                try {
                    val cacheFile = File(appCtx.filesDir, CACHE_FILE_NAME)
                    cacheFile.writeText(downloadedDomains.joinToString("\n"))
                } catch (_: Exception) {}

                prefs.edit().putLong(KEY_LAST_UPDATE, now).apply()
                _lastUpdateTimestamp.value = now
            }
        } finally {
            _isUpdating.value = false
        }

        activeRulesSet.size
    }

    internal fun parseHostLine(rawLine: String): String? {
        val trimmed = rawLine.trim().lowercase()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) return null
        val noComment = trimmed.substringBefore("#").trim()
        val parts = noComment.split(Regex("\\s+"))
        val candidate = when {
            parts.size >= 2 && (parts[0] == "127.0.0.1" || parts[0] == "0.0.0.0") -> parts[1]
            parts.size == 1 -> parts[0].removePrefix("||").substringBefore("^")
            else -> return null
        }
        if (candidate == "localhost" || !candidate.contains(".") || candidate.length < 4 || candidate.length > 100) {
            return null
        }
        return candidate
    }

    private fun isWhitelistedDomain(domain: String): Boolean {
        return domain.contains("pobreflixtv") ||
            domain.contains("tmdb.org") ||
            domain.contains("byse") ||
            domain.contains("mixdrop") ||
            domain.contains("streamtape") ||
            domain.contains("googleapis.com") ||
            domain.contains("gstatic.com")
    }

    fun isBlockedUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (lower.startsWith("data:") || lower.startsWith("blob:") || lower.startsWith("about:")) {
            return false
        }
        if (isWhitelistedDomain(lower)) return false

        val rules = activeRulesSet
        return rules.any { rule -> lower.contains(rule) }
    }

    fun recordBlocked() {
        _blockedSessionCount.value = _blockedSessionCount.value + 1
    }

    fun resetSessionBlocked() {
        _blockedSessionCount.value = 0
    }

    fun getBaselineCount(): Int = BASELINE_RULES.size
}

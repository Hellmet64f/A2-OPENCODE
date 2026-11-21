package com.example.network

import com.example.model.Episode
import com.example.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class PlayerServer(
    val name: String,
    val embedUrl: String,
    val audio: String = "Dublado"
)

data class PlayerDataResult(
    val servers: List<PlayerServer>,
    val nextEpisodeUrl: String? = null,
    val prevEpisodeUrl: String? = null
)

data class SeriesPageMetadata(
    val videoId: String,
    val availableSeasons: List<Int>,
    val fallbackEpisodes: List<Episode>
)

object SearchService {
    const val BASE_URL = "https://www.pobreflixtv.codes"
    const val ANICHAN_BASE_URL = "https://anichan.to"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
            .replace("pobreflixtv.city", "pobreflixtv.codes")
            .replace("pobreflixtv.delivery", "pobreflixtv.codes")
        return when {
            trimmed.isBlank() -> ""
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "$BASE_URL$trimmed"
            else -> "$BASE_URL/$trimmed"
        }
    }

    internal fun slugifyTitle(title: String): String {
        val normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return normalized.ifBlank { "anime" }
    }

    private fun stripAccents(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .trim()
    }

    private fun cleanHtmlText(raw: String): String {
        return raw
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun fetchPobreflixHomeCatalog(): List<Movie> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/")
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "text/html,application/xhtml+xml")
                .build()
            client.newCall(request).execute().use { resp ->
                val html = resp.body?.string().orEmpty()
                if (html.isNotBlank()) return@withContext parseCardsFromHtml(html)
            }
        } catch (_: Exception) {}
        emptyList()
    }

    suspend fun fetchAniChanCatalog(): List<Movie> = withContext(Dispatchers.IO) {
        val combined = mutableListOf<Movie>()
        val endpoints = listOf(
            "$ANICHAN_BASE_URL/api/catalog/airing",
            "$ANICHAN_BASE_URL/api/catalog/trending"
        )
        for (ep in endpoints) {
            try {
                val request = Request.Builder()
                    .url(ep)
                    .get()
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "application/json")
                    .addHeader("Referer", "$ANICHAN_BASE_URL/")
                    .build()
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        combined.addAll(parseAniChanResultsJson(body))
                    }
                }
            } catch (_: Exception) {}
        }
        combined.distinctBy { it.url }
    }

    suspend fun searchAniChan(query: String): List<Movie> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val request = Request.Builder()
                .url("$ANICHAN_BASE_URL/api/search?q=$encoded")
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .addHeader("Referer", "$ANICHAN_BASE_URL/search?q=$encoded")
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    return@withContext parseAniChanResultsJson(body)
                }
            }
        } catch (_: Exception) {}
        emptyList()
    }

    internal fun parseAniChanResultsJson(jsonString: String): List<Movie> {
        val results = mutableListOf<Movie>()
        try {
            val root = JSONObject(jsonString)
            val arr = root.optJSONArray("results") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optInt("id", 0)
                if (id <= 0) continue
                val title = obj.optString("title", "")
                    .ifBlank { obj.optString("titleRomaji", "") }
                    .ifBlank { "Anime #$id" }
                val poster = obj.optString("poster", "")
                if (poster.isBlank()) continue
                val slug = slugifyTitle(title)
                val year = obj.optJSONObject("startDate")?.optInt("year", 0)
                    ?.takeIf { it > 1900 }?.toString() ?: ""
                val url = "$ANICHAN_BASE_URL/anime/$id/$slug"
                results.add(
                    Movie(
                        title = title,
                        coverUrl = poster,
                        url = url,
                        isAnime = true,
                        isSeries = true,
                        genre = "Anime",
                        audio = "Dub / Leg",
                        year = year
                    )
                )
            }
        } catch (_: Exception) {}
        return results.distinctBy { it.url }
    }

    suspend fun searchPobreflix(query: String): List<Movie> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()
        val attempts = listOf(cleanQuery, stripAccents(cleanQuery))
            .filter { it.isNotBlank() }
            .distinct()

        for (attempt in attempts) {
            val combined = mutableListOf<Movie>()

            // 1. Endpoint AJAX oficial no domínio ativo (pobreflixtv.codes)
            try {
                val url = "$BASE_URL/index.php?app=videobox&module=video&controller=index&do=buscarContent"
                val formBody = FormBody.Builder()
                    .add("q", attempt)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .post(formBody)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("Origin", BASE_URL)
                    .addHeader("Referer", "$BASE_URL/buscar/")
                    .build()

                client.newCall(request).execute().use { response ->
                    val jsonString = response.body?.string().orEmpty()
                    if (jsonString.isNotBlank() && jsonString.trimStart().startsWith("{")) {
                        val json = JSONObject(jsonString)
                        val html = json.optString("html", "")
                        if (html.isNotBlank()) {
                            combined.addAll(parseCardsFromHtml(html))
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Fallback HTML caso o site retorne página estática
            if (combined.isEmpty()) {
                try {
                    val encoded = URLEncoder.encode(attempt, "UTF-8")
                    val request = Request.Builder()
                        .url("$BASE_URL/buscar/?q=$encoded")
                        .get()
                        .addHeader("User-Agent", USER_AGENT)
                        .build()
                    client.newCall(request).execute().use { resp ->
                        val html = resp.body?.string().orEmpty()
                        if (html.isNotBlank()) {
                            combined.addAll(parseCardsFromHtml(html))
                        }
                    }
                } catch (_: Exception) {}
            }

            if (combined.isNotEmpty()) {
                return@withContext combined.distinctBy { it.url }
            }
        }
        emptyList()
    }

    suspend fun searchAll(query: String): List<Movie> = coroutineScope {
        val clean = query.trim()
        if (clean.isBlank()) return@coroutineScope emptyList()
        val pfDeferred = async { searchPobreflix(clean) }
        val aniDeferred = async { searchAniChan(clean) }
        val pfResults = pfDeferred.await()
        val aniResults = aniDeferred.await()

        // Intercala resultados de Filmes/Séries e Animes priorizando correspondência exata
        val combined = mutableListOf<Movie>()
        val maxSize = maxOf(pfResults.size, aniResults.size)
        for (i in 0 until maxSize) {
            if (i < pfResults.size) combined.add(pfResults[i])
            if (i < aniResults.size) combined.add(aniResults[i])
        }
        combined.distinctBy { it.url }
    }

    internal fun parseCardsFromHtml(html: String): List<Movie> {
        val results = mutableListOf<Movie>()
        if (html.isBlank()) return results

        val linkPattern = Pattern.compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val imgPattern = Pattern.compile("<img[^>]+(?:src|data-src|data-lazy-src)=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val altPattern = Pattern.compile("alt=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val titlePattern = Pattern.compile("title=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val yearPattern = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b")

        val matcher = linkPattern.matcher(html)
        while (matcher.find()) {
            val rawHref = matcher.group(1) ?: continue
            val content = matcher.group(2) ?: ""
            val href = normalizeUrl(rawHref)
            if (!(href.contains("/filmes/") || href.contains("/series/") || href.contains("/episodios/") || href.contains("/animes/"))) {
                continue
            }

            val imgMatcher = imgPattern.matcher(content)
            if (imgMatcher.find()) {
                val rawCover = imgMatcher.group(1) ?: ""
                val cover = normalizeUrl(rawCover)
                if (cover.isBlank() || cover.startsWith("data:image")) continue

                val altMatcher = altPattern.matcher(content)
                val titleMatcher = titlePattern.matcher(content)
                val rawTitle = when {
                    altMatcher.find() -> altMatcher.group(1).orEmpty()
                    titleMatcher.find() -> titleMatcher.group(1).orEmpty()
                    else -> content
                }
                val title = cleanHtmlText(rawTitle).ifBlank { "Sem Título" }
                val yearMatch = yearPattern.matcher(content)
                val year = if (yearMatch.find()) yearMatch.group(1).orEmpty() else ""
                val isAnimeItem = href.contains("/animes/")
                val isSeriesItem = isAnimeItem || href.contains("/series/") || href.contains("/episodios/")

                if (results.none { it.url == href }) {
                    results.add(
                        Movie(
                            title = title,
                            coverUrl = cover,
                            url = href,
                            isAnime = isAnimeItem,
                            isSeries = isSeriesItem,
                            genre = if (isAnimeItem) "Anime" else "Geral",
                            year = year
                        )
                    )
                }
            }
        }
        return results
    }

    suspend fun fetchSeriesMetadata(seriesUrl: String): SeriesPageMetadata? = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeUrl(seriesUrl)
        if (cleanUrl.isBlank()) return@withContext null

        if (cleanUrl.contains("anichan.to")) {
            return@withContext fetchAniChanSeriesMetadata(cleanUrl)
        }

        try {
            val request = Request.Builder()
                .url(cleanUrl)
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { resp ->
                val html = resp.body?.string().orEmpty()
                if (html.isNotBlank()) {
                    return@withContext parseSeriesMetadataFromHtml(html)
                }
            }
        } catch (_: Exception) {}
        null
    }

    internal fun parseAniChanEpisodesMetadata(
        animeId: Int,
        slug: String,
        episodesJson: String
    ): SeriesPageMetadata {
        val json = JSONObject(episodesJson)
        val count = json.optInt("episodes", 1).coerceIn(1, 2000)
        val dubAvailable = json.optBoolean("dubAvailable", false)
        val audioLabel = if (dubAvailable) "Dublado / Legendado" else "Legendado"
        val eps = (1..count).map { epNum ->
            Episode(
                title = "Episódio $epNum",
                url = "$ANICHAN_BASE_URL/anime/$animeId/$slug/$epNum",
                season = 1,
                number = epNum,
                audio = audioLabel
            )
        }
        return SeriesPageMetadata(
            videoId = "anichan_$animeId",
            availableSeasons = listOf(1),
            fallbackEpisodes = eps
        )
    }

    private suspend fun fetchAniChanSeriesMetadata(animeUrl: String): SeriesPageMetadata? = withContext(Dispatchers.IO) {
        val match = Regex("""anichan\.to/anime/(\d+)(?:/([^/?#]+))?""").find(animeUrl) ?: return@withContext null
        val animeId = match.groupValues[1].toIntOrNull() ?: return@withContext null
        val slug = match.groupValues[2].ifBlank { "anime" }
        try {
            val req = Request.Builder()
                .url("$ANICHAN_BASE_URL/api/watch/episodes?anilistId=$animeId")
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .addHeader("Referer", animeUrl)
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    return@withContext parseAniChanEpisodesMetadata(animeId, slug, body)
                }
            }
        } catch (_: Exception) {}

        // Fallback direto com Episódio 1
        SeriesPageMetadata(
            videoId = "anichan_$animeId",
            availableSeasons = listOf(1),
            fallbackEpisodes = listOf(
                Episode(
                    title = "Episódio 1",
                    url = "$ANICHAN_BASE_URL/anime/$animeId/$slug/1",
                    season = 1,
                    number = 1,
                    audio = "Dub / Leg"
                )
            )
        )
    }

    internal fun parseSeriesMetadataFromHtml(html: String): SeriesPageMetadata {
        val videoId = Regex("""data-video-id=["'](\d+)["']""").find(html)?.groupValues?.get(1).orEmpty()
        val seasonsSet = sortedSetOf<Int>()
        Regex("""data-season=["'](\d+)["']""").findAll(html).forEach { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 1..200 }?.let { seasonsSet.add(it) }
        }
        if (seasonsSet.isEmpty()) seasonsSet.add(1)

        val fallbackEps = mutableListOf<Episode>()
        val epLinkPattern = Pattern.compile("<a[^>]+href=[\"']([^\"']*/episodios/[^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val matcher = epLinkPattern.matcher(html)
        val seRegex = Regex("""-(\d+)x(\d+)-""")
        var index = 1
        while (matcher.find()) {
            val href = normalizeUrl(matcher.group(1).orEmpty())
            if (href.isBlank() || fallbackEps.any { it.url == href }) continue
            val text = cleanHtmlText(matcher.group(2).orEmpty()).ifBlank { "Episódio $index" }
            val seMatch = seRegex.find(href)
            val s = seMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val epNum = seMatch?.groupValues?.get(2)?.toIntOrNull() ?: index
            fallbackEps.add(Episode(title = text, url = href, season = s, number = epNum))
            index++
        }
        fallbackEps.sortWith(compareBy({ it.season }, { it.number }))
        return SeriesPageMetadata(
            videoId = videoId,
            availableSeasons = seasonsSet.toList(),
            fallbackEpisodes = fallbackEps
        )
    }

    suspend fun fetchSeasonEpisodes(
        videoId: String,
        season: Int,
        audio: String = "Dublado"
    ): List<Episode> = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || videoId.startsWith("anichan_")) return@withContext emptyList()
        try {
            val safeAudio = if (audio.equals("Legendado", ignoreCase = true)) "Legendado" else "Dublado"
            val url = "$BASE_URL/index.php?app=videobox&module=video&controller=view&do=episodesList&id=${URLEncoder.encode(videoId, "UTF-8")}&season=$season&audio=$safeAudio"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (body.isNotBlank()) {
                    return@withContext parseEpisodesJson(body, season, safeAudio)
                }
            }
        } catch (_: Exception) {}
        emptyList()
    }

    internal fun parseEpisodesJson(jsonString: String, season: Int, audio: String = "Dublado"): List<Episode> {
        val list = mutableListOf<Episode>()
        try {
            val json = JSONObject(jsonString)
            val epsArray = json.optJSONArray("episodes") ?: return list
            for (i in 0 until epsArray.length()) {
                val epObj = epsArray.optJSONObject(i) ?: continue
                val url = normalizeUrl(epObj.optString("url", ""))
                if (url.isBlank()) continue
                val title = cleanHtmlText(epObj.optString("title", "Episódio ${i + 1}")).ifBlank { "Episódio ${i + 1}" }
                val number = epObj.optString("number", "").toIntOrNull()
                    ?: epObj.optInt("number", i + 1)
                list.add(
                    Episode(
                        title = title,
                        url = url,
                        season = season.coerceAtLeast(1),
                        number = number.coerceAtLeast(1),
                        audio = audio
                    )
                )
            }
            list.sortBy { it.number }
        } catch (_: Exception) {}
        return list
    }

    suspend fun fetchPlayerData(
        videoId: String,
        preferredAudio: String = "Dublado"
    ): PlayerDataResult? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        try {
            val url = "$BASE_URL/index.php?app=videobox&module=video&controller=view&do=playerData&id=$videoId"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val jsonString = response.body?.string().orEmpty()
                if (jsonString.isNotBlank()) {
                    return@withContext parsePlayerDataJson(jsonString, preferredAudio)
                }
            }
        } catch (_: Exception) {}
        null
    }

    internal fun parsePlayerDataJson(jsonString: String, preferredAudio: String = "Dublado"): PlayerDataResult? {
        return try {
            val json = JSONObject(jsonString)
            val playersArray = json.optJSONArray("players") ?: return null

            fun parseServersMap(raw: String): Map<String, String> {
                val map = mutableMapOf<String, String>()
                raw.replace("&amp;", "&").split("&").forEach { pair ->
                    val parts = pair.split("=", limit = 2)
                    val valToken = parts.getOrNull(1)?.trim().orEmpty()
                    if (parts.size == 2 &&
                        valToken.isNotBlank() &&
                        !valToken.equals("hide", ignoreCase = true) &&
                        !valToken.equals("null", ignoreCase = true) &&
                        !valToken.equals("none", ignoreCase = true)
                    ) {
                        map[parts[0].lowercase().trim()] = valToken
                    }
                }
                return map
            }

            val dubMap = parseServersMap(json.optString("servers_dub", ""))
            val legMap = parseServersMap(json.optString("servers_leg", ""))

            val servers = mutableListOf<PlayerServer>()
            fun collectFrom(sourceMap: Map<String, String>, audioLabel: String) {
                for (i in 0 until playersArray.length()) {
                    val p = playersArray.optJSONObject(i) ?: continue
                    val label = p.optString("label", "Servidor")
                    val key = label.lowercase().replace(Regex("[^a-z0-9]"), "")
                    val baseUrl = p.optString("url", "")
                    val token = sourceMap[key]
                    if (!token.isNullOrBlank() && baseUrl.isNotBlank()) {
                        val embed = baseUrl + token
                        if (!AdBlockManager.isBlockedUrl(embed)) {
                            servers.add(
                                PlayerServer(
                                    name = "$label ($audioLabel)",
                                    embedUrl = embed,
                                    audio = audioLabel
                                )
                            )
                        }
                    }
                }
            }

            if (preferredAudio.equals("Legendado", ignoreCase = true)) {
                collectFrom(legMap, "Legendado")
                collectFrom(dubMap, "Dublado")
            } else {
                collectFrom(dubMap, "Dublado")
                collectFrom(legMap, "Legendado")
            }

            servers.sortWith(compareBy<PlayerServer> {
                if (it.audio.equals(preferredAudio, ignoreCase = true)) 0 else 1
            }.thenBy { server ->
                val n = server.name.lowercase()
                when {
                    n.contains("byse") -> 0
                    n.contains("mixdrop") -> 1
                    n.contains("streamtape") -> 2
                    n.contains("dood") -> 10
                    else -> 5
                }
            })

            val navObj = json.optJSONObject("navigation")
            val nextUrl = navObj?.optString("next", "")?.takeIf { it.isNotBlank() }?.let { normalizeUrl(it) }
            val prevUrl = navObj?.optString("prev", "")?.takeIf { it.isNotBlank() }?.let { normalizeUrl(it) }

            PlayerDataResult(
                servers = servers.distinctBy { it.embedUrl },
                nextEpisodeUrl = nextUrl,
                prevEpisodeUrl = prevUrl
            )
        } catch (_: Exception) {
            null
        }
    }
}

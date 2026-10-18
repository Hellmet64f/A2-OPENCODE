package com.example.network

import com.example.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class PlayerServer(
    val name: String,
    val embedUrl: String
)

data class PlayerDataResult(
    val servers: List<PlayerServer>,
    val nextEpisodeUrl: String? = null,
    val prevEpisodeUrl: String? = null
)

object SearchService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun searchPobreflix(query: String): List<Movie> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Movie>()
        if (query.isBlank()) return@withContext results
        try {
            val url = "https://www.pobreflixtv.delivery/index.php?app=videobox&module=video&controller=index&do=buscarContent"
            val formBody = FormBody.Builder()
                .add("q", query)
                .build()
            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Origin", "https://www.pobreflixtv.delivery")
                .addHeader("Referer", "https://www.pobreflixtv.delivery/buscar/")
                .build()

            val response = client.newCall(request).execute()
            val jsonString = response.body?.string() ?: ""
            if (jsonString.isNotBlank()) {
                val json = JSONObject(jsonString)
                val html = json.optString("html", "")
                if (html.isNotBlank()) {
                    val linkPattern = Pattern.compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.DOTALL)
                    val imgPattern = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']")
                    val altPattern = Pattern.compile("alt=[\"']([^\"']+)[\"']")
                    val titlePattern = Pattern.compile("title=[\"']([^\"']+)[\"']")

                    val matcher = linkPattern.matcher(html)
                    while (matcher.find()) {
                        val href = matcher.group(1) ?: ""
                        val content = matcher.group(2) ?: ""
                        if (href.contains("/filmes/") || href.contains("/series/") || href.contains("/episodios/")) {
                            val imgMatcher = imgPattern.matcher(content)
                            if (imgMatcher.find()) {
                                val cover = imgMatcher.group(1) ?: ""
                                val altMatcher = altPattern.matcher(content)
                                val titleMatcher = titlePattern.matcher(content)
                                var title = if (altMatcher.find()) {
                                    altMatcher.group(1) ?: ""
                                } else if (titleMatcher.find()) {
                                    titleMatcher.group(1) ?: ""
                                } else {
                                    "Sem Título"
                                }
                                title = title.replace(Regex("<[^>]+>"), "").trim()
                                if (cover.isNotBlank() && results.none { it.url == href }) {
                                    results.add(Movie(title = title, coverUrl = cover, url = href))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    suspend fun searchAniChan(query: String): List<Movie> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Movie>()
        if (query.isBlank()) return@withContext results
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://anichan.to/search?keyword=$encoded"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            if (html.isNotBlank()) {
                val cardPattern = Pattern.compile("<a[^>]+class=[\"'][^\"']*card[^\"']*[\"'][^>]+href=[\"']([^\"']+)[\"'][^>]*>.*?<img[^>]+src=[\"']([^\"']+)[\"'][^>]*alt=[\"']([^\"']+)[\"']", Pattern.DOTALL)
                val matcher = cardPattern.matcher(html)
                while (matcher.find()) {
                    var href = matcher.group(1) ?: ""
                    val cover = matcher.group(2) ?: ""
                    val title = matcher.group(3) ?: ""
                    if (!href.startsWith("http")) {
                        href = "https://anichan.to$href"
                    }
                    if (title.isNotBlank() && cover.isNotBlank() && results.none { it.url == href }) {
                        val tLower = title.lowercase()
                        val detectedGenre = when {
                            tLower.contains("shippuden") || tLower.contains("piece") || tLower.contains("bleach") || tLower.contains("dragon") || tLower.contains("hunter") || tLower.contains("jujutsu") || tLower.contains("hero") || tLower.contains("naruto") || tLower.contains("boruto") || tLower.contains("clover") -> "Shonen"
                            tLower.contains("slime") || tLower.contains("re:zero") || tLower.contains("sword art") || tLower.contains("shield") || tLower.contains("overlord") || tLower.contains("mushoku") || tLower.contains("isekai") || tLower.contains("world") -> "Isekai"
                            tLower.contains("attack") || tLower.contains("titan") || tLower.contains("solo") || tLower.contains("demon") || tLower.contains("slayer") || tLower.contains("chainsaw") || tLower.contains("fate") || tLower.contains("tokyo") -> "Ação"
                            tLower.contains("kaguya") || tLower.contains("horimiya") || tLower.contains("toradora") || tLower.contains("dress-up") || tLower.contains("love") -> "Romance"
                            tLower.contains("magic") || tLower.contains("dungeon") || tLower.contains("fairy") || tLower.contains("danmachi") || tLower.contains("frieren") -> "Fantasia"
                            else -> "Geral"
                        }
                        results.add(
                            Movie(
                                title = title.trim(),
                                coverUrl = cover.trim(),
                                url = href,
                                genre = detectedGenre,
                                audio = if (tLower.contains("dub")) "Dublado" else "Dub/Leg"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    suspend fun fetchPlayerData(videoId: String): PlayerDataResult? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        try {
            val url = "https://www.pobreflixtv.delivery/index.php?app=videobox&module=video&controller=view&do=playerData&id=$videoId"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val jsonString = response.body?.string() ?: ""
            if (jsonString.isNotBlank()) {
                val json = JSONObject(jsonString)
                val playersArray = json.optJSONArray("players") ?: return@withContext null
                
                fun parseServers(raw: String): Map<String, String> {
                    val map = mutableMapOf<String, String>()
                    raw.replace("&amp;", "&").split("&").forEach { pair ->
                        val parts = pair.split("=", limit = 2)
                        if (parts.size == 2 && parts[1].isNotBlank()) {
                            map[parts[0].lowercase().trim()] = parts[1].trim()
                        }
                    }
                    return map
                }

                val dub = parseServers(json.optString("servers_dub", ""))
                val leg = parseServers(json.optString("servers_leg", ""))
                val active = if (dub.isNotEmpty()) dub else leg

                val servers = mutableListOf<PlayerServer>()
                for (i in 0 until playersArray.length()) {
                    val p = playersArray.getJSONObject(i)
                    val label = p.optString("label", "Servidor")
                    val key = label.lowercase().replace(Regex("[^a-z0-9]"), "")
                    val baseUrl = p.optString("url", "")
                    val token = active[key]
                    if (!token.isNullOrBlank() && baseUrl.isNotBlank()) {
                        servers.add(PlayerServer(name = label, embedUrl = baseUrl + token))
                    }
                }

                // Prioritize servers without anti-bot/captcha: Byse (#1), MixDrop (#2), Streamtape (#3)
                servers.sortWith(compareBy { server ->
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
                val nextUrl = navObj?.optString("next", null)?.takeIf { it.isNotBlank() }
                val prevUrl = navObj?.optString("prev", null)?.takeIf { it.isNotBlank() }

                return@withContext PlayerDataResult(
                    servers = servers,
                    nextEpisodeUrl = nextUrl,
                    prevEpisodeUrl = prevUrl
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}

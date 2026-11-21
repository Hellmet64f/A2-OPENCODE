package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FirestoreCodec
import com.example.model.Movie
import com.example.model.WatchHistoryItem
import com.example.model.WatchHistoryManager
import com.example.network.AdBlockManager
import com.example.network.SearchService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class A2MiguelCoreUnitTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WatchHistoryManager.clearAll(context)
        WatchHistoryManager.saveRecentSearches(context, emptyList())
        AdBlockManager.initialize(context)
    }

    @Test
    fun `adBlockManager has at least 100 baseline rules and blocks heuristic ads and redirects without blocking streams`() {
        assertTrue(AdBlockManager.getBaselineCount() >= 100)
        assertTrue(AdBlockManager.isBlockedUrl("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(AdBlockManager.isBlockedUrl("https://www.popads.net/pop.js"))
        assertTrue(AdBlockManager.isBlockedUrl("https://casino-tigrinho.example.com/ad"))
        assertTrue(AdBlockManager.isBlockedUrl("https://mxdrop.click/xads.js?v=2.0.1"))
        assertTrue(AdBlockManager.isBlockedUrl("https://qq.soakysecrets.com/script.js"))
        assertTrue(AdBlockManager.isBlockedUrl("https://ww1.parklogic.com/landing"))
        assertFalse(AdBlockManager.isBlockedUrl("https://www.pobreflixtv.codes/filmes/teste"))
        assertFalse(AdBlockManager.isBlockedUrl("https://anichan.to/anime/21/one-piece/1"))
        assertFalse(AdBlockManager.isBlockedUrl("https://bysebuho.com/e/abc123"))

        assertTrue(AdBlockManager.isAllowedTopFrameNavigation("https://bysebuho.com/e/abc123", "https://bysebuho.com/e/abc123?t=1"))
        assertTrue(AdBlockManager.isAllowedTopFrameNavigation("https://anichan.to/anime/21/one-piece/1", "https://anichan.to/anime/21/one-piece/2"))
        assertFalse(AdBlockManager.isAllowedTopFrameNavigation("https://mxdrop.click/e/abc123", "https://qq.soakysecrets.com/offer"))

        assertEquals("ads.tracker.net", AdBlockManager.parseHostLine("0.0.0.0 ads.tracker.net # ad"))
        assertEquals("banner.example.org", AdBlockManager.parseHostLine("127.0.0.1 banner.example.org"))
        assertNull(AdBlockManager.parseHostLine("# comment line"))
        assertNull(AdBlockManager.parseHostLine("127.0.0.1 localhost"))
    }

    @Test
    fun `watchHistoryManager tracks watched episodes favorites completion and json roundTrip`() {
        val seriesItem = WatchHistoryItem(
            id = "https://www.pobreflixtv.delivery/series/breaking-bad/",
            title = "Breaking Bad",
            coverUrl = "https://image.tmdb.org/t/p/w500/poster.jpg",
            currentUrl = "https://www.pobreflixtv.delivery/episodios/breaking-bad-1x2/",
            isSeries = true,
            season = 1,
            episodeNumber = 2,
            episodeTitle = "Cat's in the Bag..."
        )
        WatchHistoryManager.saveProgress(context, seriesItem, markCurrentWatched = true)
        val afterFav = WatchHistoryManager.toggleFavorite(
            context,
            Movie("Breaking Bad", seriesItem.coverUrl, seriesItem.id, isSeries = true)
        ).first
        assertTrue(afterFav.first().favorite)
        assertTrue(afterFav.first().isEpisodeWatched(1, 2))

        val afterComplete = WatchHistoryManager.markCompleted(context, seriesItem.id, true).first
        assertTrue(afterComplete.first().completed)

        WatchHistoryManager.addRecentSearch(context, "Breaking Bad")
        val exportedJson = WatchHistoryManager.exportToJson(context)

        WatchHistoryManager.clearAll(context)
        WatchHistoryManager.saveRecentSearches(context, emptyList())
        assertTrue(WatchHistoryManager.getHistory(context).isEmpty())

        val imported = WatchHistoryManager.importFromJson(context, exportedJson)
        assertEquals(1, imported.size)
        assertEquals("Breaking Bad", imported.first().title)
        assertTrue(imported.first().favorite)
        assertTrue(imported.first().completed)
        assertTrue(imported.first().isEpisodeWatched(1, 2))
        assertEquals(listOf("Breaking Bad"), WatchHistoryManager.getRecentSearches(context))
    }

    @Test
    fun `searchService parsers extract catalog cards series metadata episodes player servers and anichan results`() {
        val html = """
            <div>
              <a href="/filmes/deadpool-e-wolverine-2024/">
                <img data-src="https://image.tmdb.org/t/p/w500/dp.jpg" alt="Deadpool &amp; Wolverine" />
                <span>2024</span>
              </a>
              <a href="https://www.pobreflixtv.city/series/the-boys/">
                <img src="/uploads/boys.jpg" title="The Boys" />
                <span>2024</span>
              </a>
              <a href="https://www.pobreflixtv.delivery/filmes/batman-begins/">
                <img src="/uploads/batman.jpg" alt="Batman Begins" />
                <span>2005</span>
              </a>
            </div>
        """.trimIndent()
        val cards = SearchService.parseCardsFromHtml(html)
        assertEquals(3, cards.size)
        assertEquals("Deadpool & Wolverine", cards[0].title)
        assertFalse(cards[0].isSeries)
        assertEquals("https://www.pobreflixtv.codes/series/the-boys/", cards[1].url)
        assertTrue(cards[1].isSeries)
        assertEquals("https://www.pobreflixtv.codes/filmes/batman-begins/", cards[2].url)

        val seriesHtml = """
            <div data-video-id="98765">
              <button data-season="1">T1</button>
              <button data-season="2">T2</button>
              <a href="/episodios/the-boys-1x1-piloto/">Episódio 1</a>
            </div>
        """.trimIndent()
        val meta = SearchService.parseSeriesMetadataFromHtml(seriesHtml)
        assertEquals("98765", meta.videoId)
        assertEquals(listOf(1, 2), meta.availableSeasons)
        assertEquals(1, meta.fallbackEpisodes.size)

        val episodesJson = """
            {"episodes":[
              {"number":"2","title":"Ep 2","url":"/episodios/the-boys-1x2/"},
              {"number":"1","title":"Ep 1","url":"/episodios/the-boys-1x1/"}
            ]}
        """.trimIndent()
        val eps = SearchService.parseEpisodesJson(episodesJson, season = 1, audio = "Dublado")
        assertEquals(2, eps.size)
        assertEquals(1, eps[0].number)
        assertEquals(2, eps[1].number)

        val playerJson = """
            {
              "players": [
                {"label": "MixDrop", "url": "https://mxdrop.click/e/"},
                {"label": "Byse", "url": "https://bysebuho.com/e/"},
                {"label": "StreamTape", "url": "https://streamtape.com/e/"}
              ],
              "servers_dub": "byse=abc123&mixdrop=xyz789&streamtape=hide",
              "servers_leg": "byse=leg999&mixdrop=null",
              "navigation": {"prev": "/episodios/ep-1/", "next": "/episodios/ep-3/"}
            }
        """.trimIndent()
        val playerData = SearchService.parsePlayerDataJson(playerJson, preferredAudio = "Dublado")
        assertNotNull(playerData)
        assertEquals(3, playerData!!.servers.size)
        assertTrue(playerData.servers.none { it.embedUrl.endsWith("/hide") || it.embedUrl.endsWith("/null") })
        assertTrue(playerData.servers.first().name.contains("Byse"))
        assertEquals("Dublado", playerData.servers.first().audio)
        assertEquals("https://www.pobreflixtv.codes/episodios/ep-3/", playerData.nextEpisodeUrl)

        val aniChanSearchJson = """
            {"results":[
              {"id": 21, "title": "ONE PIECE", "poster": "https://s4.anilist.co/onepiece.jpg", "startDate": {"year": 1999}},
              {"id": 188055, "title": "Dragon Ball DAIMA", "poster": "https://s4.anilist.co/daima.jpg", "startDate": {"year": 2024}}
            ]}
        """.trimIndent()
        val aniItems = SearchService.parseAniChanResultsJson(aniChanSearchJson)
        assertEquals(2, aniItems.size)
        assertTrue(aniItems[0].isAnime)
        assertTrue(aniItems[0].isSeries)
        assertEquals("https://anichan.to/anime/21/one-piece", aniItems[0].url)
        assertEquals("1999", aniItems[0].year)

        val aniMeta = SearchService.parseAniChanEpisodesMetadata(
            animeId = 188055,
            slug = "dragon-ball-daima",
            episodesJson = """{"episodes": 3, "dubAvailable": true}"""
        )
        assertEquals("anichan_188055", aniMeta.videoId)
        assertEquals(3, aniMeta.fallbackEpisodes.size)
        assertEquals("https://anichan.to/anime/188055/dragon-ball-daima/2", aniMeta.fallbackEpisodes[1].url)
    }

    @Test
    fun `firestoreCodec generates rules-compliant docIds and create vs update payloads`() {
        val url = "https://www.pobreflixtv.delivery/series/fallout/"
        val docId = FirestoreCodec.stableDocId(url)
        assertEquals(40, docId.length)
        assertTrue(docId.matches(Regex("^[a-zA-Z0-9_\\-]+$")))

        val item = WatchHistoryItem(
            id = url,
            title = "Fallout",
            coverUrl = "https://image.tmdb.org/t/p/w500/fallout.jpg",
            currentUrl = "https://www.pobreflixtv.delivery/episodios/fallout-1x1/",
            isSeries = true,
            season = 1,
            episodeNumber = 1
        )
        val createMap = FirestoreCodec.toHistoryCreateMap("uid_123", item)
        assertEquals("uid_123", createMap["userId"])
        assertEquals("series", createMap["mediaType"])
        assertTrue(createMap.containsKey("createdAt"))
        assertTrue(createMap.containsKey("updatedAt"))

        val updateMap = FirestoreCodec.toHistoryUpdateMap("uid_123", item)
        assertFalse(updateMap.containsKey("userId"))
        assertFalse(updateMap.containsKey("createdAt"))
        assertTrue(updateMap.containsKey("updatedAt"))

        val prefsCreate = FirestoreCodec.toPreferencesCreateMap(
            userId = "uid_123",
            zoomPreset = "CINEMA",
            zoomScale = 1.3,
            focusX = 0.5,
            focusY = 0.48,
            adBlockAutoUpdate = true,
            recentSearches = listOf("Fallout")
        )
        assertEquals("cinema", prefsCreate["zoomPreset"])
        assertTrue(prefsCreate.containsKey("createdAt"))

        val prefsUpdate = FirestoreCodec.toPreferencesUpdateMap(
            userId = "uid_123",
            zoomPreset = "FULL",
            zoomScale = 1.5,
            focusX = 0.5,
            focusY = 0.48,
            adBlockAutoUpdate = true,
            recentSearches = listOf("Fallout")
        )
        assertFalse(prefsUpdate.containsKey("userId"))
        assertFalse(prefsUpdate.containsKey("createdAt"))
        assertEquals("full", prefsUpdate["zoomPreset"])
    }

    @Test
    fun `guestModePrefs toggles local guest mode and pending cloud sync flag`() {
        com.example.ui.GuestModePrefs.setGuestMode(context, false)
        com.example.ui.GuestModePrefs.clearPendingGuestSync(context)
        assertFalse(com.example.ui.GuestModePrefs.isGuestMode(context))
        assertFalse(com.example.ui.GuestModePrefs.hasPendingGuestSync(context))

        com.example.ui.GuestModePrefs.setGuestMode(context, true)
        assertTrue(com.example.ui.GuestModePrefs.isGuestMode(context))
        assertTrue(com.example.ui.GuestModePrefs.hasPendingGuestSync(context))

        com.example.ui.GuestModePrefs.setGuestMode(context, false)
        assertFalse(com.example.ui.GuestModePrefs.isGuestMode(context))
        assertTrue(com.example.ui.GuestModePrefs.hasPendingGuestSync(context))

        com.example.ui.GuestModePrefs.clearPendingGuestSync(context)
        assertFalse(com.example.ui.GuestModePrefs.hasPendingGuestSync(context))
    }

    @Test
    fun `anime watchHistoryItem preserves isAnime flag and serializes anime mediaType for firestore`() {
        val animeUrl = "https://anichan.to/anime/21/one-piece"
        val animeItem = WatchHistoryItem(
            id = animeUrl,
            title = "ONE PIECE",
            coverUrl = "https://s4.anilist.co/onepiece.jpg",
            currentUrl = "https://anichan.to/anime/21/one-piece/12",
            isSeries = true,
            isAnime = true,
            season = 1,
            episodeNumber = 12,
            episodeTitle = "Episódio 12"
        )
        WatchHistoryManager.saveProgress(context, animeItem, markCurrentWatched = true)
        val json = WatchHistoryManager.exportToJson(context)
        WatchHistoryManager.clearAll(context)
        val imported = WatchHistoryManager.importFromJson(context, json)
        assertEquals(1, imported.size)
        assertTrue(imported.first().isAnime)
        assertTrue(imported.first().isSeries)

        val createMap = FirestoreCodec.toHistoryCreateMap("uid_anime", animeItem)
        assertEquals("anime", createMap["mediaType"])
    }
}

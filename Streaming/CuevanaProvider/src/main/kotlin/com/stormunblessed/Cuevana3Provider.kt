package com.stormunblessed

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink

class Cuevana3Provider : MainAPI() {
    override var mainUrl = "https://cuevana3.cz"
    override var name = "Cuevana3"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun org.jsoup.nodes.Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a") ?: return null
        val href = fixUrl(a.attr("href"))
        val title = selectFirst("h2.Title")?.text()?.trim() ?: return null
        val poster = selectFirst("img")?.attr("src")
        return if (href.contains("/serie/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
        }
    }

    // ── Main page ─────────────────────────────────────────────────────────────

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        val items = mutableListOf<HomePageList>()

        doc.selectFirst("section.home-movies ul.MovieList")?.let { ul ->
            val list = ul.select("li.TPostMv").mapNotNull { it.toSearchResult() }
            if (list.isNotEmpty()) items.add(HomePageList("Películas", list))
        }

        doc.selectFirst("section.home-episodes ul.MovieList")?.let { ul ->
            val list = ul.select("li.TPostMv").mapNotNull { li ->
                val a = li.selectFirst("a") ?: return@mapNotNull null
                val href = fixUrl(a.attr("href"))
                val title = li.selectFirst("h2.Title")?.text()?.trim() ?: return@mapNotNull null
                val epSpan = li.selectFirst("p span")?.text() ?: ""
                val poster = li.selectFirst("img")?.attr("src")
                newTvSeriesSearchResponse("$title $epSpan", href, TvType.TvSeries) { posterUrl = poster }
            }
            if (list.isNotEmpty()) items.add(HomePageList("Últimos Episodios", list))
        }

        doc.select("section.home-series:not(.home-episodes) ul.MovieList").firstOrNull()?.let { ul ->
            val list = ul.select("li.TPostMv").mapNotNull { it.toSearchResult() }
            if (list.isNotEmpty()) items.add(HomePageList("Series", list))
        }

        if (items.isEmpty()) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    // ── Search ────────────────────────────────────────────────────────────────

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/explorar?s=${query}").document
        return doc.select("li.TPostMv").mapNotNull { it.toSearchResult() }
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, timeout = 120).document

        val isSerie = url.contains("/serie/") && !url.contains("/episodio-")

        if (isSerie) {
            // Series page (/serie/slug or /serie/slug/temporada-N)
            val title = doc.selectFirst("h1.title")?.text()
                ?.replace(Regex("-\\s*Temporada.*"), "")
                ?.trim() ?: return null
            val poster = doc.selectFirst("figure.poster img")?.attr("src")
            val backdrop = doc.selectFirst("figure.backdrop img")?.attr("src")

            // Collect season links from this page (nav may list temporada-1, temporada-2, …)
            val seasonUrls = doc.select("a[href*='/temporada-']")
                .map { fixUrl(it.attr("href")) }
                .distinct()
                .ifEmpty { listOf(url) }

            val episodes = mutableListOf<Episode>()
            for (seasonUrl in seasonUrls) {
                val seasonDoc = if (seasonUrl == url) doc else app.get(seasonUrl).document
                val seasonNum = Regex("""/temporada-(\d+)""").find(seasonUrl)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: 1

                seasonDoc.select("li.objects-item a[href*='/episodio-']").forEach { a ->
                    val epUrl = fixUrl(a.attr("href"))
                    val epNum = Regex("""/episodio-\d+x(\d+)""").find(epUrl)
                        ?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                    val epPoster = a.selectFirst("img")?.attr("src")
                    episodes.add(
                        newEpisode(epUrl) {
                            this.season = seasonNum
                            this.episode = epNum
                            this.posterUrl = epPoster
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
            }
        }

        // Movie or episode page
        val title = doc.selectFirst("h1.title")?.text()?.trim() ?: return null
        val poster = doc.selectFirst("div.backdrop-info figure img")?.attr("src")
        val backdrop = doc.selectFirst("div.backdrop-image figure img")?.attr("src")
        val description = doc.selectFirst("div.backdrop-info p")?.text()?.trim()
        val tags = doc.select("div.section a.jump-link").map { it.text().trim() }
        val year = doc.select("div.section")
            .firstOrNull {
                it.selectFirst("h2.subtitle")?.text()?.contains("Año", ignoreCase = true) == true
            }
            ?.selectFirst("p")?.text()?.trim()?.toIntOrNull()

        // Both movies and episode pages share the same player, so we use url as data
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backdrop
            this.plot = description
            this.tags = tags
            this.year = year
        }
    }

    // ── loadLinks ─────────────────────────────────────────────────────────────
    //
    // Player structure on both movie and episode pages:
    //   ul.tabs-video > li.tab-video-item
    //     div > div.tab-item-name   ("Latino", "Castellano", "Subtitulado", …)
    //     ul > li[data-server="https://video.cuevana3.cz/?token=…"]
    //
    // Each data-server is a redirect/proxy page that contains an <iframe src="…">.
    // We fetch it and pass the iframe src to loadExtractor.

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        doc.select("ul.tabs-video li.tab-video-item").forEach { tab ->
            val lang = tab.selectFirst("div.tab-item-name")?.ownText()?.trim()
                ?.ifBlank { "Unknown" } ?: "Unknown"

            tab.select("ul li[data-server]").forEach { li ->
                val serverUrl = li.attr("data-server").trim()
                if (serverUrl.isBlank()) return@forEach
                val serverName = li.selectFirst("span")?.text()?.trim() ?: "Server"
                val sourceName = "[$lang] $serverName"

                try {
                    val proxyDoc = app.get(serverUrl, referer = mainUrl).document
                    val iframeSrc = proxyDoc.selectFirst("iframe[src]")?.attr("src")?.trim()
                        ?: proxyDoc.selectFirst("iframe[data-src]")?.attr("data-src")?.trim()
                    if (!iframeSrc.isNullOrBlank()) {
                        loadExtractor(iframeSrc, mainUrl, subtitleCallback) { link ->
                            callback(
                                newExtractorLink(
                                    source = sourceName,
                                    name = sourceName,
                                    url = link.url,
                                ) {
                                    this.quality = link.quality
                                    this.type = link.type
                                    this.referer = link.referer
                                    this.headers = link.headers
                                    this.extractorData = link.extractorData
                                }
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Skip unreachable servers silently
                }
            }
        }
        return true
    }
}

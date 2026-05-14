package com.latino

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class DoramasFlixProvider : MainAPI() {

    override var mainUrl            = "https://doramasflix.in"
    override var name               = "DoramasFlix"
    override val hasMainPage        = true
    override var lang               = "es"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "$mainUrl/dorama"          to "🇰🇷 Doramas Coreanos",
        "$mainUrl/dorama-chino"    to "🇨🇳 Doramas Chinos",
        "$mainUrl/dorama-japones"  to "🇯🇵 Doramas Japoneses",
        "$mainUrl/pelicula-dorama" to "🎬 Películas Asiáticas",
        "$mainUrl/genero/romance"  to "❤️ Romance",
        "$mainUrl/genero/comedia"  to "😂 Comedia",
        "$mainUrl/genero/accion"   to "💥 Acción",
        "$mainUrl/genero/historico" to "📜 Histórico",
        "$mainUrl/genero/fantasia" to "🧙 Fantasía",
        "$mainUrl/estrenos"        to "⭐ Estrenos"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url   = if (page == 1) request.data else "${request.data}?page=$page"
        val doc   = app.get(url).document
        val items = doc.select(".Card--Big, article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title  = selectFirst("h2, h3, .name")?.text()?.trim() ?: return null
        val href   = selectFirst("a")?.attr("abs:href") ?: return null
        val poster = selectFirst("img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } }
        val type   = if (href.contains("/pelicula")) TvType.Movie else TvType.TvSeries
        return newMovieSearchResponse(title, href, type) { posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/buscar?q=$query").document
            .select(".Card--Big").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc    = app.get(url).document
        val title  = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = doc.selectFirst(".Image img")?.attr("src")
        val desc   = doc.selectFirst(".Description")?.text()?.trim()
        val year   = doc.selectFirst(".year, .Date")?.text()?.trim()?.toIntOrNull()
        val tags   = doc.select(".genres a").map { it.text() }
        val isSeries = !url.contains("/pelicula")
        return if (isSeries) {
            val eps = mutableListOf<Episode>()
            doc.select(".list-episode a").forEachIndexed { idx, ep ->
                eps.add(newEpisode(ep.attr("abs:href")) {
                    this.name = ep.text()
                    this.episode = idx + 1
                })
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                posterUrl = poster; plot = desc; this.year = year; this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                posterUrl = poster; plot = desc; this.year = year; this.tags = tags
            }
        }
    }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select("track[kind=subtitles]").forEach { t ->
            val src = t.attr("src")
            if (src.isNotEmpty()) subtitleCallback(SubtitleFile("es", src))
        }
        doc.select("iframe, .player-src").forEach { el ->
            val src = el.attr("src").ifEmpty { el.attr("data-src") }
            if (src.isNotEmpty()) loadExtractor(src, data, subtitleCallback, callback)
        }
        return true
    }
}

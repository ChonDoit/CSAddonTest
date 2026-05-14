package com.latino

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class CuevanaProvider : MainAPI() {

    override var mainUrl            = "https://cuevana3.io"
    override var name               = "Cuevana"
    override val hasMainPage        = true
    override var lang               = "es"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/peliculas"              to "🎬 Películas",
        "$mainUrl/series"                 to "📺 Series",
        "$mainUrl/genero/accion"          to "💥 Acción",
        "$mainUrl/genero/aventura"        to "🗺️ Aventura",
        "$mainUrl/genero/comedia"         to "😂 Comedia",
        "$mainUrl/genero/animacion"       to "🎨 Animación",
        "$mainUrl/genero/ciencia-ficcion" to "🚀 Ciencia Ficción",
        "$mainUrl/genero/romance"         to "❤️ Romance",
        "$mainUrl/estrenos/peliculas"     to "⭐ Estrenos"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}?page=$page"
        val doc = app.get(url).document
        val items = doc.select(".MovieList article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title  = selectFirst("h2, .Title")?.text()?.trim() ?: return null
        val href   = selectFirst("a")?.attr("abs:href") ?: return null
        val poster = selectFirst("img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } }
        val type   = if (href.contains("/serie/")) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, href, type) { posterUrl = poster }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/?s=$query").document
            .select(".MovieList article").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc    = app.get(url).document
        val title  = doc.selectFirst("h1.Title")?.text()?.trim() ?: return null
        val poster = doc.selectFirst("div.Image img")?.attr("src")
        val desc   = doc.selectFirst("div.Description p")?.text()?.trim()
        val year   = doc.selectFirst("span.Date")?.text()?.trim()?.toIntOrNull()
        val tags   = doc.select("p.Genre a").map { it.text() }
        val isSeries = url.contains("/serie/")
        return if (isSeries) {
            val eps = doc.select(".TPostMv").mapIndexed { idx, ep ->
                newEpisode(ep.selectFirst("a")?.attr("abs:href") ?: "") {
                    this.name = ep.selectFirst("h4")?.text()
                    this.episode = idx + 1
                }
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
        doc.select("li.JFClose a, .TPlayBx li").forEach { btn ->
            val link = btn.attr("data-url").ifEmpty { btn.attr("href") }
            if (link.isNotEmpty()) loadExtractor(link, data, subtitleCallback, callback)
        }
        return true
    }
}

package com.cuevana

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class CuevanaProvider : MainAPI() {
    override var mainUrl = "https://cuevana.biz"
    override var name = "Cuevana"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    override var lang = "es"
    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl).document
        val items = document.select("li.v_j_sc").mapNotNull {
            val title = it.selectFirst("h2")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src")
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
        return newHomePageResponse(listOf(HomePageList("Novedades", items)), false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?q=$query").document
        return document.select("li.v_j_sc").mapNotNull {
            val title = it.selectFirst("h2")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src")
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }
}

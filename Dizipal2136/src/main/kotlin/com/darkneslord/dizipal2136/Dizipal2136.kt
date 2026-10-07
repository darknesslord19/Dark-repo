package com.darkneslord.dizipal2136

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Bu dosya cloudstream_super.py tarafindan otomatik uretildi.
// Notlar: Film sayfasinda iframe/alternatif kaynak yok (video JS ile sonradan ekleniyor olabilir); video zinciri tam dogrulanamadi
class Dizipal2136 : MainAPI() {
    override var mainUrl = "https://dizipal2136.com"
    override var name = "Dizipal2136"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val ua =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val baseHeaders = mapOf("User-Agent" to ua, "Accept" to "*/*")

    // ---------------------------------------------------------------- ANA SAYFA
    override val mainPage = mainPageOf(
        "$mainUrl/" to "Son Eklenenler",
        "$mainUrl/filmler" to "Filmler",
        "$mainUrl/diziler" to "Diziler",
        "$mainUrl/trend" to "Trend",
        "$mainUrl/platform/blutv" to "BluTV",
        "$mainUrl/platform/disney-plus" to "Disney",
        "$mainUrl/platform/exxen" to "Exxen",
        "$mainUrl/platform/gain" to "Gain",
        "$mainUrl/platform/max" to "Max",
        "$mainUrl/platform/netflix" to "Netflix",
        "$mainUrl/platform/paramount-plus" to "Paramount",
        "$mainUrl/platform/prime-video" to "Prime Video",
        "$mainUrl/kategori/aile" to "Aile",
        "$mainUrl/kategori/aksiyon" to "Aksiyon",
        "$mainUrl/kategori/aksiyon-macera" to "Aksiyon & Macera",
        "$mainUrl/kategori/animasyon" to "Animasyon",
        "$mainUrl/kategori/belgesel" to "Belgesel",
        "$mainUrl/kategori/bilim-kurgu-fantazi" to "Bilim Kurgu & Fantazi",
        "$mainUrl/kategori/bilim-kurgu" to "Bilim-Kurgu",
        "$mainUrl/kategori/bilim-kurgu-fantastik" to "Bilim-Kurgu & Fantastik",
        "$mainUrl/kategori/cocuk" to "Çocuk",
        "$mainUrl/kategori/cocuklar" to "Çocuklar",
        "$mainUrl/kategori/dram" to "Dram",
        "$mainUrl/kategori/fantastik" to "Fantastik",
        "$mainUrl/kategori/gerceklik" to "Gerçeklik",
        "$mainUrl/kategori/gerilim" to "Gerilim",
        "$mainUrl/kategori/gizem" to "Gizem",
        "$mainUrl/kategori/komedi" to "Komedi",
        "$mainUrl/kategori/korku" to "Korku",
        "$mainUrl/kategori/macera" to "Macera",
        "$mainUrl/kategori/muzik" to "Müzik",
        "$mainUrl/kategori/pembe-dizi" to "Pembe Dizi",
        "$mainUrl/kategori/realite" to "Realite",
        "$mainUrl/kategori/romantik" to "Romantik",
        "$mainUrl/kategori/savas" to "Savaş",
        "$mainUrl/kategori/savas-politik" to "Savaş & Politik",
        "$mainUrl/kategori/suc" to "Suç",
        "$mainUrl/kategori/talk-show" to "Talk Show",
        "$mainUrl/kategori/tarih" to "Tarih",
        "$mainUrl/kategori/tv-film" to "TV film",
        "$mainUrl/kategori/vahsi-bati" to "Vahşi Batı",
        "$mainUrl/kategori/western" to "Western",
        "$mainUrl/bolumler" to "Tümünü Gör"
    )

    private fun pageUrl(base: String, page: Int): String =
        if (page <= 1) base else base + (if (base.contains("?")) "&" else "?") + "page=$page"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = try {
            val doc = app.get(pageUrl(request.data, page), headers = baseHeaders).document
            doc.select("li.content-card").mapNotNull { it.toSearchResult() }.distinctBy { it.url }
        } catch (e: Exception) {
            emptyList()
        }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a[href]") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = ((selectFirst("img")?.attr("alt")) ?: "").replace(Regex("""\s*(film(i)?\s+)?izle\s*$""", RegexOption.IGNORE_CASE), "").trim()
        if (title.isBlank()) return null
        val poster = fixUrlNull(selectFirst("img")?.attr("data-src"))
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
    }


    private fun Element.toSearchResultSearch(): SearchResponse? {
        val a = selectFirst("a[href]") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = ((selectFirst("img")?.attr("alt")) ?: "").replace(Regex("""\s*(film(i)?\s+)?izle\s*$""", RegexOption.IGNORE_CASE), "").trim()
        if (title.isBlank()) return null
        val poster = fixUrlNull(selectFirst("img")?.attr("data-src"))
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
    }

    // ---------------------------------------------------------------- ARAMA
    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query, "UTF-8")
        return try {
            val doc = app.get("$mainUrl/search?q=$q", headers = baseHeaders).document
            doc.select("article.content-card").mapNotNull { it.toSearchResultSearch() }.distinctBy { it.url }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // ---------------------------------------------------------------- DETAY
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = baseHeaders).document
        val title = (doc.selectFirst("h1")?.text() ?: doc.selectFirst("meta[property=og:title]")?.attr("content"))?.trim()
            ?.replace(Regex("""\s*(film(i)?\s+)?izle\s*(\|.*)?$""", RegexOption.IGNORE_CASE), "")?.trim()
            ?.takeIf { it.isNotBlank() } ?: return null
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() } ?: ldValues(doc, "image").firstOrNull())
        val plot = (doc.selectFirst("p.series-description")?.text() ?: doc.selectFirst("meta[property=og:description]")?.attr("content"))?.trim()
        val year = Regex("""\b(19|20)\d{2}\b""").find(title)?.value?.toIntOrNull()
            ?: Regex("""(?iu)(?:y[ıi]l|vizyon)\D{0,20}((?:19|20)\d{2})""").find(doc.text())?.groupValues?.get(1)?.toIntOrNull()
            ?: ldValues(doc, "datePublished").firstOrNull()?.take(4)?.toIntOrNull()
        val tags = ldValues(doc, "genre")
        val actors = emptyList<String>().ifEmpty { labelValue(doc, "Oyuncular")?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList() }.ifEmpty { ldValues(doc, "actor") }.distinct()
        val duration = Regex("""(?iu)(\d{1,2}):(\d{2})\s*(?:минут|мин|dk|min)""").find(doc.text())
            ?.let { m -> (m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()).takeIf { it > 0 } }
            ?: Regex("""(\d{2,3})\s*(?:dk|dakika|min)\b""", RegexOption.IGNORE_CASE).find(doc.text())?.groupValues?.get(1)?.toIntOrNull()
            ?: ldValues(doc, "duration").firstOrNull()?.let { d ->
                Regex("""PT(?:(\d+)H)?(?:(\d+)M)?""").find(d)?.let { m ->
                    (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
                }
            }?.takeIf { it > 0 }
        val rating = Regex("""(?iu)(?:IMDb|Кинопоиск|Kinopoisk)\D{0,15}(\d(?:[.,]\d)?)""").find(doc.text())?.groupValues?.get(1)?.replace(",", ".")
            ?: ldValues(doc, "ratingValue").firstOrNull()
        val trailer = doc.selectFirst("iframe[src*=youtube],iframe[data-src*=youtube],iframe[data-litespeed-src*=youtube]")?.let { f ->
            listOf("src", "data-src", "data-litespeed-src").map { f.attr(it) }.firstOrNull { it.contains("youtube") }
        }
        val recs = emptyList<SearchResponse>()
        val episodes = doc.select("a[href*='bolum']").mapNotNull { a ->
            val href = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val txt = a.text().trim()
            val blob = "$href $txt"
            val s = Regex("""(?iu)(\d+)[-.\s]*sezon|(?:sezon|season)[-\s]?(\d+)""").find(blob)
                ?.let { m -> m.groupValues[1].ifBlank { m.groupValues[2] }.toIntOrNull() }
            val e = Regex("""(?iu)(\d+)[-.\s]*(?:bolum|bölüm)|(?:bolum|bölüm|episode)[-\s]?(\d+)""").find(blob)
                ?.let { m -> m.groupValues[1].ifBlank { m.groupValues[2] }.toIntOrNull() }
            newEpisode(href) {
                this.name = txt.ifBlank { null }
                this.season = s
                this.episode = e
            }
        }.distinctBy { it.data }
        if (episodes.size > 1) {
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
                this.duration = duration
                this.recommendations = recs
                addActors(actors)
                this.score = rating?.toDoubleOrNull()?.let { Score.from10(it) }
                if (!trailer.isNullOrBlank()) addTrailer(trailer)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.tags = tags
            this.duration = duration
            this.recommendations = recs
            addActors(actors)
            this.score = rating?.toDoubleOrNull()?.let { Score.from10(it) }
            if (!trailer.isNullOrBlank()) addTrailer(trailer)
        }
    }

    // ---------------------------------------------------------------- VIDEO
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val page = app.get(data, headers = baseHeaders).document
        val embeds = (page.select("iframe").mapNotNull { f ->
            listOf("data-litespeed-src", "data-src", "data-lazy-src", "src")
                .map { f.attr(it) }
                .firstOrNull { it.startsWith("http") || it.startsWith("//") }
        })
            .map { fixUrl(it) }
            .filterNot { it.contains("youtube", true) }
            .distinct()
        var found = false
        for (embed in embeds) {
            val ok = try {
                loadBePlayer(embed, data, subtitleCallback, callback) ||
                    loadExtractor(embed, data, subtitleCallback, callback) ||
                    loadDirect(embed, data, callback)
            } catch (e: Exception) {
                false
            }
            if (ok) found = true
        }
        return found
    }

    // iframe -> embed (Referer: film sayfasi) -> bePlayer(ARG1, JSON) -> AES coz -> video_location (master HLS)
    private suspend fun loadBePlayer(
        embed: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(embed, referer = referer, headers = baseHeaders).text
        val m = Regex("""bePlayer\(\s*(['"])(.*?)\1\s*,\s*(['"])(\{.*?\})\3""", RegexOption.DOT_MATCHES_ALL)
            .find(html) ?: return false
        val arg1 = m.groupValues[2]
        val json = m.groupValues[4].replace("\\/", "/")
        val plain = decryptBePlayer(arg1, json, embed, referer) ?: return false
        val o = JSONObject(plain)
        val master = o.optString("video_location").takeIf { it.isNotBlank() } ?: return false
        val origin = "https://" + URI(embed).host

        o.optJSONArray("strSubtitles")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                if (s.isNull("file")) continue
                val f = s.optString("file")
                if (f.isBlank()) continue
                val sub = if (f.startsWith("http")) f else origin + f
                subtitleCallback.invoke(newSubtitleFile(s.optString("label", "Türkçe"), sub))
            }
        }

        // Master istegi Referer(embed)+Origin ister, segmentler User-Agent ister. URL oturuma bagli: her oynatmada bastan coz.
        val streamHeaders = mapOf(
            "User-Agent" to ua,
            "Accept" to "*/*",
            "Referer" to embed,
            "Origin" to origin
        )
        callback.invoke(
            newExtractorLink(
                source = name,
                name = "$name HLS",
                url = master,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = embed
                this.quality = Qualities.Unknown.value
                this.headers = streamHeaders
            }
        )
        return true
    }

    // Sifre cozme algoritmasi bulunamadi (rapordaki bePlayerFn'e bak); bu oynatici icin loadExtractor/loadDirect denenir
    private fun decryptBePlayer(arg1: String, json: String, embed: String, referer: String): String? = null

    // "В ролях: a, b, c" gibi etiketli metinlerin degerini verir
    private fun labelValue(doc: org.jsoup.nodes.Document, label: String): String? {
        val el = doc.select("*:containsOwn($label)").firstOrNull() ?: return null
        val box = if (el.text().length > label.length + 3) el else (el.parent() ?: el)
        return box.text().substringAfter(label).trimStart(':', ' ').trim().takeIf { it.isNotBlank() }
    }

    // JSON-LD icinden anahtar degerlerini toplar (genre, actor, datePublished, ratingValue ...)
    private fun ldValues(doc: org.jsoup.nodes.Document, key: String): List<String> {
        val out = mutableListOf<String>()
        fun add(v: Any?) {
            when (v) {
                is String -> out.add(v)
                is Number -> out.add(v.toString())
                is JSONObject -> v.optString("name").takeIf { it.isNotBlank() }?.let { out.add(it) }
                is org.json.JSONArray -> for (i in 0 until v.length()) add(v.opt(i))
            }
        }
        fun walk(o: Any?) {
            when (o) {
                is JSONObject -> {
                    if (o.has(key)) add(o.get(key))
                    o.keys().forEach { walk(o.opt(it)) }
                }
                is org.json.JSONArray -> for (i in 0 until o.length()) walk(o.opt(i))
            }
        }
        doc.select("script[type=application/ld+json]").forEach {
            try {
                walk(org.json.JSONTokener(it.data()).nextValue())
            } catch (e: Exception) {
            }
        }
        return out.filter { it.isNotBlank() }.distinct()
    }

    // Sayfada dogrudan gecen m3u8/mp4 adreslerini bulur (yedek yontem)
    private suspend fun loadDirect(
        embed: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = try {
            app.get(embed, referer = referer, headers = baseHeaders).text.replace("\\/", "/")
        } catch (e: Exception) {
            return false
        }
        val urls = Regex("""https?://[^"'\s<>\\]+?\.(?:m3u8|mp4)[^"'\s<>\\]*""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.value }.distinct().take(4).toList()
        for (u in urls) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = u,
                    type = if (u.contains("m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = embed
                    this.quality = Qualities.Unknown.value
                }
            )
        }
        return urls.isNotEmpty()
    }

}

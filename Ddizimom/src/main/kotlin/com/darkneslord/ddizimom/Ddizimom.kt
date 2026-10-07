package com.darkneslord.ddizimom

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Bu dosya cloudstream_super.py tarafindan otomatik uretildi.
// Notlar: sayfalama dogrulanamadi; video zinciri tam dogrulanamadi
class Ddizimom : MainAPI() {
    override var mainUrl = "https://ddizimom.com"
    override var name = "Ddizimom"
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
        "$mainUrl/locale/tr" to "🇹🇷 Türkçe",
        "$mainUrl/locale/en" to "🇬🇧 English",
        "$mainUrl/locale/de" to "🇩🇪 Deutsch",
        "$mainUrl/locale/fr" to "🇫🇷 Français",
        "$mainUrl/locale/it" to "🇮🇹 Italiano",
        "$mainUrl/locale/es" to "🇪🇸 Español",
        "$mainUrl/locale/ar" to "🇸🇦 العربية",
        "$mainUrl/locale/ru" to "🇷🇺 Русский",
        "$mainUrl/locale/zh" to "🇨🇳 中文",
        "$mainUrl/filmler" to "Filmler",
        "$mainUrl/diziler" to "Diziler",
        "$mainUrl/animes" to "Animeler",
        "$mainUrl/takvim" to "Takvim",
        "$mainUrl/platform/amazon-prime" to "Amazon Prime",
        "$mainUrl/platform/atv" to "ATV",
        "$mainUrl/platform/blutv" to "BluTV",
        "$mainUrl/platform/crunchyroll" to "Crunchyroll",
        "$mainUrl/platform/disney" to "Disney",
        "$mainUrl/platform/exxen" to "Exxen",
        "$mainUrl/platform/hbo-max" to "HBO Max",
        "$mainUrl/platform/hulu" to "Hulu",
        "$mainUrl/platform/kanal-d" to "Kanal D",
        "$mainUrl/platform/lionsgate-plus" to "Lionsgate",
        "$mainUrl/platform/mgm-plus" to "MGM",
        "$mainUrl/platform/mubi" to "MUBI",
        "$mainUrl/platform/netflix" to "Netflix",
        "$mainUrl/platform/now" to "Now",
        "$mainUrl/platform/paramount-plus" to "Paramount",
        "$mainUrl/platform/peacock" to "Peacock",
        "$mainUrl/platform/puhutv" to "puhutv",
        "$mainUrl/platform/show-tv" to "Show TV",
        "$mainUrl/platform/star-tv" to "Star TV",
        "$mainUrl/platform/tabii" to "tabii",
        "$mainUrl/platform/trt-1" to "TRT 1",
        "$mainUrl/platform/tv-plus" to "TV",
        "$mainUrl/tur/aile" to "Aile",
        "$mainUrl/tur/aksiyon" to "Aksiyon",
        "$mainUrl/tur/aksiyon-macera" to "Aksiyon & Macera",
        "$mainUrl/tur/animasyon" to "Animasyon",
        "$mainUrl/tur/belgesel" to "Belgesel",
        "$mainUrl/tur/bilim-kurgu-fantastik" to "Bilim Kurgu & Fantazi",
        "$mainUrl/tur/bilim-kurgu" to "Bilim-Kurgu",
        "$mainUrl/tur/cocuklar" to "Çocuklar",
        "$mainUrl/tur/dram" to "Dram",
        "$mainUrl/tur/fantastik" to "Fantastik",
        "$mainUrl/tur/reality" to "Gerçeklik",
        "$mainUrl/tur/gerilim" to "Gerilim",
        "$mainUrl/tur/gizem" to "Gizem",
        "$mainUrl/tur/komedi" to "Komedi",
        "$mainUrl/tur/korku" to "Korku",
        "$mainUrl/tur/macera" to "Macera",
        "$mainUrl/tur/muzik" to "Müzik",
        "$mainUrl/tur/pembe-dizi" to "Pembe Dizi",
        "$mainUrl/tur/romantik" to "Romantik",
        "$mainUrl/tur/savas" to "Savaş",
        "$mainUrl/tur/savas-politika" to "Savaş & Politik",
        "$mainUrl/tur/suc" to "Suç",
        "$mainUrl/tur/talk" to "Talk",
        "$mainUrl/tur/tarih" to "Tarih",
        "$mainUrl/tur/tv-film" to "TV film",
        "$mainUrl/tur/western" to "Vahşi Batı",
        "$mainUrl/trend" to "Trend İçerikler"
    )

    private fun pageUrl(base: String, page: Int): String =
        if (page <= 1) base else base.trimEnd('/') + "/page/$page/"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = try {
            val doc = app.get(pageUrl(request.data, page), headers = baseHeaders).document
            doc.select("div.flex.snap-start.items-end").mapNotNull { it.toSearchResult() }.distinctBy { it.url }
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
        val poster = fixUrlNull(selectFirst("img")?.attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
    }


    private fun Element.toSearchResultSearch(): SearchResponse? {
        val a = selectFirst("a[href]") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = ((selectFirst("img")?.attr("alt")) ?: "").replace(Regex("""\s*(film(i)?\s+)?izle\s*$""", RegexOption.IGNORE_CASE), "").trim()
        if (title.isBlank()) return null
        val poster = fixUrlNull(selectFirst("img")?.attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
    }

    // ---------------------------------------------------------------- ARAMA
    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query, "UTF-8")
        return try {
            val doc = app.get("$mainUrl/ara?q=$q", headers = baseHeaders).document
            doc.select("a.group.block").mapNotNull { it.toSearchResultSearch() }.distinctBy { it.url }
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
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() } ?: doc.selectFirst("img.h-full.w-full.object-cover")?.attr("src") ?: ldValues(doc, "image").firstOrNull())
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim() ?: ldValues(doc, "description").firstOrNull()
        val year = Regex("""\b(19|20)\d{2}\b""").find(title)?.value?.toIntOrNull()
            ?: Regex("""(?iu)(?:y[ıi]l|vizyon)\D{0,20}((?:19|20)\d{2})""").find(doc.text())?.groupValues?.get(1)?.toIntOrNull()
            ?: ldValues(doc, "datePublished").firstOrNull()?.take(4)?.toIntOrNull()
        val tags = doc.select("div.flex.flex-wrap a[href*='/tur/']").map { it.text().trim() }.filter { it.isNotBlank() }.ifEmpty { ldValues(doc, "genre") }.distinct()
        val actors = doc.select("div.grid.overflow-hidden.md:max-h-none a[href*='/oyuncu/']").map { it.text().trim() }.filter { it.isNotBlank() }.ifEmpty { labelValue(doc, "Oyuncular")?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList() }.ifEmpty { ldValues(doc, "actor") }.distinct()
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
        val resp = app.get(data, headers = baseHeaders)
        val page = resp.document
        val embeds = (page.select("iframe").mapNotNull { f ->
            listOf("data-litespeed-src", "data-src", "data-lazy-src", "src")
                .map { f.attr(it) }
                .firstOrNull { it.startsWith("http") || it.startsWith("//") }
        } + deepEmbeds(resp.text, data))
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
        if (!found) {
            // sayfa gercek bir (gizli) tarayicida yuklenir; oynatici medya istegi yakalanir
            try {
                val r = app.get(
                    data, headers = baseHeaders,
                    interceptor = WebViewResolver(Regex("""\.(m3u8|mpd|mp4)(\?|$)"""))
                )
                val media = r.url
                if (Regex("""\.(m3u8|mpd|mp4)""", RegexOption.IGNORE_CASE).containsMatchIn(media)) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = media,
                            type = if (media.contains("m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.referer = data
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    found = true
                }
            } catch (e: Exception) {
            }
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


    // iframe sayfada yoksa: JS icine gomulu iframe / atob(base64) / "embed":"..." / DooPlay REST ile gelen oynaticilar
    private suspend fun deepEmbeds(html: String, pageUrl: String): List<String> {
        val out = mutableListOf<String>()
        val text = html.replace("\\/", "/").replace("\\u002F", "/").replace("&amp;", "&")
        val texts = mutableListOf(text)
        Regex("""atob\(\s*[\x27\x22]([A-Za-z0-9+/=_-]{16,})[\x27\x22]\s*\)""").findAll(text).forEach {
            try {
                texts.add(String(Base64.decode(it.groupValues[1], Base64.DEFAULT)))
            } catch (e: Exception) {
            }
        }
        for (t in texts) {
            Regex("""<iframe[^>]+?(?:src|data-src)\s*=\s*\\?[\x27\x22]([^\x27\x22\\ >]+)""", RegexOption.IGNORE_CASE)
                .findAll(t).forEach { out.add(it.groupValues[1]) }
            Regex("""\x22(?:embed_url|embedUrl|embed|iframe|player|video_url|videoUrl|file|source)\x22\s*:\s*\x22(https?:[^\x22]+)\x22""")
                .findAll(t).forEach { out.add(it.groupValues[1]) }
        }
        // DooPlay benzeri: data-post + data-nume
        val doc = org.jsoup.Jsoup.parse(html)
        for (el in doc.select("[data-post][data-nume]").take(6)) {
            val post = el.attr("data-post")
            val nume = el.attr("data-nume")
            val type = el.attr("data-type").ifBlank { "movie" }
            try {
                val r = app.get("$mainUrl/wp-json/dooplayer/v2/$post/$type/$nume", referer = pageUrl, headers = baseHeaders).text
                    .replace("\\/", "/")
                Regex("""(?:embed_url|src)\x22?\s*[:=]\s*\\?\x22?\\?[\x27\x22]?(https?:[^\x27\x22\\ >]+)""")
                    .find(r)?.let { out.add(it.groupValues[1]) }
            } catch (e: Exception) {
            }
        }
        return out.map { fixUrl(it) }
            .filterNot { it.contains("youtube", true) || Regex("""\.(js|css|jpe?g|png|webp|svg|gif)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            .distinct()
    }

}

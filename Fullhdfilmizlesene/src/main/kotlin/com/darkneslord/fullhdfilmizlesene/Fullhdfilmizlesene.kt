package com.darkneslord.fullhdfilmizlesene

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Bu dosya cloudstream_super.py tarafindan otomatik uretildi.
// Notlar: site elle/rapordan uretildi: secicilerin gercek sayfada dogrulanmasi gerekir
class Fullhdfilmizlesene : MainAPI() {
    override var mainUrl = "https://www.fullhdfilmizlesene.now"
    override var name = "Fullhdfilmizlesene"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie)

    private val ua =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val baseHeaders = mapOf("User-Agent" to ua, "Accept" to "*/*")
    private val cfKiller = CloudflareKiller()   // Cloudflare 403/503 dogrulamasini asar

    // ---------------------------------------------------------------- ANA SAYFA
    override val mainPage = mainPageOf(
        "$mainUrl/" to "Son Eklenenler",
        "$mainUrl/en-cok-izlenen-filmler" to "En Çok İzlenen Filmler",
        "$mainUrl/seri-filmler" to "Seri Filmler",
        "$mainUrl/filmizle/aile-filmleri" to "Aile Filmleri",
        "$mainUrl/filmizle/aksiyon-filmleri" to "Aksiyon Filmleri",
        "$mainUrl/filmizle/animasyon-filmleri" to "Animasyon Filmleri",
        "$mainUrl/filmizle/bilim-kurgu-filmleri" to "Bilim Kurgu Filmleri",
        "$mainUrl/filmizle/dram-filmler-izle" to "Dram Filmleri",
        "$mainUrl/filmizle/komedi-filmleri" to "Komedi Filmleri",
        "$mainUrl/filmizle/korku-filmleri" to "Korku Filmleri",
        "$mainUrl/yil/2026-filmleri-izle" to "2026 Filmleri",
        "$mainUrl/yil/2025-filmler-izle" to "2025 Filmleri",
        "$mainUrl/yil/2024-filmleri-izle-1" to "2024 Filmleri",
        "$mainUrl/filmizle/turkce-dublaj-filmler-1" to "Türkçe Dublaj",
        "$mainUrl/filmizle/turkce-altyazili-filmler-1" to "Türkçe Altyazılı",
        "$mainUrl/filmizle/1080p-filmler-2" to "1080p Filmler",
        "$mainUrl/filmizle/4k-filmler" to "4K Filmler"
    )

    private fun pageUrl(base: String, page: Int): String =
        if (page <= 1) base else if (base.trimEnd('/') == mainUrl) "$mainUrl/yeni-filmler/$page" else base.trimEnd('/') + "/$page"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = try {
            val doc = app.get(pageUrl(request.data, page), headers = baseHeaders, interceptor = cfKiller).document
            doc.select("a[href*='/film/']:has(img)").mapNotNull { it.toSearchResult() }.distinctBy { it.url }
        } catch (e: Exception) {
            emptyList()
        }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = selectFirst("a[href*='/film/']") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = ((attr("title").takeIf { it.isNotBlank() } ?: selectFirst("img")?.attr("alt")) ?: "").replace(Regex("""\s*(film(i)?\s+)?izle\s*$""", RegexOption.IGNORE_CASE), "").trim()
        if (title.isBlank()) return null
        val poster = fixUrlNull(selectFirst("img")?.let { i -> listOf("data-src", "data-lazy-src", "data-original", "src").map { i.attr(it) }.firstOrNull { it.isNotBlank() && !it.startsWith("data:") } })
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
    }

    // ---------------------------------------------------------------- ARAMA
    // Arama adresi dogrulanamadi: yaygin kaliplar sirayla denenir, ilk sonuc veren kullanilir
    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query, "UTF-8")
        val urls = listOf(
            "$mainUrl/arama/$q",
            "$mainUrl/?s=$q",
            "$mainUrl/ara/$q",
            "$mainUrl/search/$q",
            "$mainUrl/index.php?do=search&subaction=search&story=$q"
        )
        for (u in urls) {
            try {
                val doc = app.get(u, headers = baseHeaders, interceptor = cfKiller).document
                val r = doc.select("a[href*='/film/']:has(img)").mapNotNull { it.toSearchResult() }.distinctBy { it.url }
                if (r.isNotEmpty()) return r
            } catch (e: Exception) {
            }
        }
        return emptyList()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // ---------------------------------------------------------------- DETAY
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = baseHeaders, interceptor = cfKiller).document
        val title = (doc.selectFirst("h1")?.text() ?: doc.selectFirst("meta[property=og:title]")?.attr("content"))?.trim()
            ?.replace(Regex("""\s*(film(i)?\s+)?izle\s*(\|.*)?$""", RegexOption.IGNORE_CASE), "")?.trim()
            ?.takeIf { it.isNotBlank() } ?: return null
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() } ?: ldValues(doc, "image").firstOrNull())
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim() ?: ldValues(doc, "description").firstOrNull()
        val year = Regex("""\b(19|20)\d{2}\b""").find(title)?.value?.toIntOrNull()
            ?: Regex("""(?iu)(?:y[ıi]l|vizyon)\D{0,20}((?:19|20)\d{2})""").find(doc.text())?.groupValues?.get(1)?.toIntOrNull()
            ?: ldValues(doc, "datePublished").firstOrNull()?.take(4)?.toIntOrNull()
        val tags = doc.select("main a[href*='/filmizle/'], article a[href*='/filmizle/']").map { it.text().trim() }.filter { it.isNotBlank() }.ifEmpty { ldValues(doc, "genre") }.distinct()
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
        val resp = app.get(data, headers = baseHeaders, interceptor = cfKiller)
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
                    interceptor = WebViewResolver(
                        Regex("""\.(m3u8|mpd|mp4)(\?|$)"""),
                        script = "setTimeout(function(){['.part-btn','.ply','.play','[class*=play]'].forEach(function(s){var e=document.querySelector(s);if(e){try{e.click()}catch(x){}}})},1500);"
                    )
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
        val html = app.get(embed, referer = referer, headers = baseHeaders, interceptor = cfKiller).text
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
            app.get(embed, referer = referer, headers = baseHeaders, interceptor = cfKiller).text.replace("\\/", "/")
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
                val r = app.get("$mainUrl/wp-json/dooplayer/v2/$post/$type/$nume", referer = pageUrl, headers = baseHeaders, interceptor = cfKiller).text
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

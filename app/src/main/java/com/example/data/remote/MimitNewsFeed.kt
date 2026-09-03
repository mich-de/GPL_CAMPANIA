package com.example.data.remote

import com.example.data.local.NewsItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/**
 * Notizie sui carburanti dalla sala stampa del MIMIT — lo stesso ministero che pubblica i prezzi
 * che l'app mostra.
 *
 * È una fonte ufficiale e attribuita, ma è la sala stampa **generale** del ministero: la gran parte
 * delle voci parla di industria, incentivi e tavoli di crisi. Per questo il feed viene filtrato per
 * argomento invece di essere ripubblicato tal quale, ed è normale che in certe settimane non ci sia
 * nulla da mostrare. In quel caso l'app dice che non c'è nulla, non riempie lo spazio con altro.
 *
 * Il feed pesa ~9 KB: aggiornarlo costa quanto caricare una pagina di testo.
 */
object MimitNewsFeed {

    const val FEED_URL = "https://www.mimit.gov.it/it/notizie-stampa?format=feed&type=rss"
    const val SOURCE = "MIMIT — Notizie e stampa"

    private const val USER_AGENT = "GPLCampaniaApp/1.0 (+https://github.com/mich-de/GPL_CAMPANIA)"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Ritorna `null` se la fonte non ha risposto: da distinguere da "ha risposto, niente in tema". */
    suspend fun fetch(fetchedAt: Long): List<NewsItem>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(FEED_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/rss+xml, application/xml")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parseRssItems(body, SOURCE, fetchedAt).filter { it.matchesGplTopic() }
            }
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}

private val ITEM = Regex("""<item>(.*?)</item>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val TITLE = Regex("""<title>(.*?)</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val LINK = Regex("""<link>(.*?)</link>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val DESCRIPTION = Regex("""<description>(.*?)</description>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val PUB_DATE = Regex("""<pubDate>(.*?)</pubDate>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val CDATA = Regex("""<!\[CDATA\[(.*?)]]>""", RegexOption.DOT_MATCHES_ALL)
private val HTML_TAG = Regex("""<[^>]+>""")
private val WHITESPACE = Regex("""\s+""")

/**
 * Estrae le voci del feed. Regex e non un parser XML perché il feed è un RSS 2.0 piatto e
 * l'alternativa (`XmlPullParser`) è una API di Android che renderebbe questa funzione impossibile
 * da verificare senza un device.
 */
internal fun parseRssItems(xml: String, source: String, fetchedAt: Long): List<NewsItem> =
    ITEM.findAll(xml).mapNotNull { match ->
        val block = match.groupValues[1]
        val link = LINK.find(block)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        val title = TITLE.find(block)?.groupValues?.get(1)?.let(::cleanText).orEmpty()
        if (link.isBlank() || title.isBlank()) return@mapNotNull null
        NewsItem(
            link = link,
            title = title,
            summary = DESCRIPTION.find(block)?.groupValues?.get(1)?.let(::cleanText).orEmpty(),
            publishedAt = parseRfc822Millis(PUB_DATE.find(block)?.groupValues?.get(1).orEmpty()),
            source = source,
            fetchedAt = fetchedAt
        )
    }.toList()

/** Toglie CDATA, tag HTML ed entità: resta il testo che la fonte ha scritto, senza markup. */
internal fun cleanText(raw: String): String {
    val withoutCdata = CDATA.replace(raw) { it.groupValues[1] }
    return HTML_TAG.replace(withoutCdata, " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#8217;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(WHITESPACE, " ")
        .trim()
}

/**
 * Parole che identificano una notizia sui carburanti. Quelle lunghe bastano come sottostringa
 * ("carburant" prende carburante, carburanti, carburanti-gate); quelle corte richiedono i confini
 * di parola, altrimenti "gpl" si troverebbe dentro sigle qualsiasi e "accise" dentro "accisero".
 */
private val TOPIC_FRAGMENTS = listOf(
    "carburant", "osservaprezz", "benzina", "gasolio", "metano", "petrolifer",
    "rifornimento", "prezzi alla pompa", "distributori di carburante", "caro-carburanti"
)

private val TOPIC_WORDS = Regex("""\b(gpl|accis[ae]|diesel|pompe bianche)\b""")

/**
 * Vera se la notizia parla di carburanti. Il confronto avviene su testo normalizzato (minuscole,
 * accenti rimossi) perché i titoli ministeriali alternano "Gpl", "GPL" e "G.P.L." — e perché un
 * filtro che sbaglia riempirebbe la sezione di notizie fuori tema, che è il modo più rapido di
 * rendere inutile una fonte per il resto buona.
 */
fun NewsItem.matchesGplTopic(): Boolean = matchesGplTopic(title, summary)

fun matchesGplTopic(title: String, summary: String): Boolean {
    val text = foldForMatching("$title $summary")
    return TOPIC_FRAGMENTS.any { it in text } || TOPIC_WORDS.containsMatchIn(text)
}

/** Minuscole senza accenti: "Perché" e "perche" devono contare come la stessa parola. */
internal fun foldForMatching(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "")

private val MONTHS_EN = listOf(
    "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"
)

private val RFC822 = Regex(
    """(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})\s+(\d{2}):(\d{2})(?::(\d{2}))?\s*([+-]\d{4})?"""
)

/**
 * Converte un `pubDate` RFC 822 (`Mon, 10 Aug 2026 09:24:43 +0200`) in epoch millis, 0 se
 * illeggibile. Scritto a mano per lo stesso motivo di [parseHttpDayKey]: i nomi dei mesi sono
 * inglesi mentre il device è italiano, e riusa [epochDayOf] invece di reimplementare il calendario.
 */
internal fun parseRfc822Millis(raw: String): Long {
    val match = RFC822.find(raw.trim()) ?: return 0L
    val day = match.groupValues[1].toIntOrNull() ?: return 0L
    val month = MONTHS_EN.indexOf(match.groupValues[2].lowercase()) + 1
    val year = match.groupValues[3].toIntOrNull() ?: return 0L
    if (month == 0) return 0L
    val epochDay = epochDayOf(year * 10_000 + month * 100 + day) ?: return 0L

    val hours = match.groupValues[4].toIntOrNull() ?: return 0L
    val minutes = match.groupValues[5].toIntOrNull() ?: return 0L
    val seconds = match.groupValues[6].toIntOrNull() ?: 0

    val offset = match.groupValues[7]
    val offsetMinutes = if (offset.length == 5) {
        val sign = if (offset[0] == '-') -1 else 1
        sign * (offset.substring(1, 3).toInt() * 60 + offset.substring(3, 5).toInt())
    } else {
        0
    }

    val local = epochDay * 86_400L + hours * 3_600L + minutes * 60L + seconds
    return (local - offsetMinutes * 60L) * 1000L
}

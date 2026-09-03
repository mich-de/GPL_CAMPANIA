package com.example.data.remote

import com.example.data.local.NewsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il feed stampa del MIMIT è un RSS 2.0 di Joomla: le voci hanno `<description>` in CDATA con
 * dentro un `<img>` e un `<p>`, e la stragrande maggioranza non parla di carburanti. Qui si verifica
 * che quello che arriva agli occhi dell'utente sia il testo del ministero, e solo quando è in tema.
 */
class MimitNewsFeedTest {

    /** Struttura reale del feed, con i tre casi che contano: in tema, fuori tema, senza link. */
    private val feed = """
        <?xml version="1.0" encoding="utf-8"?>
        <rss version="2.0">
          <channel>
            <title>Notizie e stampa</title>
            <item>
              <title>Carburanti, pubblicati i prezzi medi settimanali</title>
              <link>https://www.mimit.gov.it/it/notizie-stampa/carburanti-prezzi-medi</link>
              <guid isPermaLink="true">https://www.mimit.gov.it/it/notizie-stampa/carburanti-prezzi-medi</guid>
              <description><![CDATA[<img src="/images/foto.jpg" alt="" /><p>Il Ministero ha pubblicato i prezzi medi dei carburanti&nbsp;rilevati dall&#8217;Osservaprezzi.</p>]]></description>
              <category>NOTIZIE</category>
              <pubDate>Mon, 10 Aug 2026 09:24:43 +0200</pubDate>
            </item>
            <item>
              <title>Al via il tavolo sulla siderurgia</title>
              <link>https://www.mimit.gov.it/it/notizie-stampa/tavolo-siderurgia</link>
              <description><![CDATA[<p>Convocato il tavolo di crisi con le parti sociali.</p>]]></description>
              <pubDate>Fri, 07 Aug 2026 15:10:00 +0200</pubDate>
            </item>
            <item>
              <title>Accise, entra in vigore la nuova aliquota sul GPL</title>
              <link></link>
              <description><![CDATA[<p>Senza link non è raggiungibile.</p>]]></description>
              <pubDate>Thu, 06 Aug 2026 11:00:00 +0200</pubDate>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `estrae le voci con link e titolo, ripulite dal markup`() {
        val items = parseRssItems(feed, MimitNewsFeed.SOURCE, fetchedAt = 42L)

        // La terza voce non ha link: senza identità non è archiviabile né apribile.
        assertEquals(2, items.size)

        val prima = items[0]
        assertEquals("Carburanti, pubblicati i prezzi medi settimanali", prima.title)
        assertEquals("https://www.mimit.gov.it/it/notizie-stampa/carburanti-prezzi-medi", prima.link)
        assertEquals(MimitNewsFeed.SOURCE, prima.source)
        assertEquals(42L, prima.fetchedAt)
        // CDATA, tag, entità e spazi doppi spariscono; il testo del ministero resta intatto.
        assertEquals(
            "Il Ministero ha pubblicato i prezzi medi dei carburanti rilevati dall'Osservaprezzi.",
            prima.summary
        )
        assertFalse(prima.summary.contains("<"))
    }

    @Test
    fun `tiene le notizie sui carburanti e scarta le altre`() {
        val items = parseRssItems(feed, MimitNewsFeed.SOURCE, fetchedAt = 0L)
        val inTema = items.filter { it.matchesGplTopic() }

        assertEquals(1, inTema.size)
        assertTrue(inTema.single().title.startsWith("Carburanti"))
    }

    @Test
    fun `le parole in tema si riconoscono anche accentate, maiuscole o al plurale`() {
        assertTrue(matchesGplTopic("Nuove regole per il GPL", ""))
        assertTrue(matchesGplTopic("Nuove regole per il Gpl", ""))
        assertTrue(matchesGplTopic("Carburante agricolo agevolato", ""))
        assertTrue(matchesGplTopic("Rincaro dei carburanti", ""))
        assertTrue(matchesGplTopic("Accise: perché cambiano", ""))
        assertTrue(matchesGplTopic("Accisa sul gasolio", ""))
        assertTrue(matchesGplTopic("Osservaprezzi carburanti online", ""))
        assertTrue(matchesGplTopic("Pompe bianche, nuove regole", ""))
        // Anche solo nel sommario: il titolo ministeriale spesso è generico.
        assertTrue(matchesGplTopic("Decreto approvato", "Interviene sui prezzi alla pompa."))
    }

    @Test
    fun `non tiene notizie che parlano d'altro`() {
        assertFalse(matchesGplTopic("Al via il tavolo sulla siderurgia", "Convocato il tavolo di crisi."))
        assertFalse(matchesGplTopic("Incentivi per l'elettrodomestico", ""))
        // "distributori" da solo è ambiguo: distributori automatici, di energia, cinematografici.
        assertFalse(matchesGplTopic("Distributori automatici, nuove norme", ""))
        // "gpl" dentro una sigla qualsiasi non è una notizia sul GPL.
        assertFalse(matchesGplTopic("Il programma AGPLUS entra nel vivo", ""))
    }

    @Test
    fun `converte la data del feed tenendo conto del fuso dichiarato`() {
        // 10 agosto 2026, 09:24:43 con offset +02:00 = 07:24:43 UTC.
        val atteso = (20_675L * 86_400L + 7 * 3_600L + 24 * 60L + 43L) * 1000L
        assertEquals(atteso, parseRfc822Millis("Mon, 10 Aug 2026 09:24:43 +0200"))

        // Lo stesso istante scritto in UTC deve dare lo stesso valore.
        assertEquals(atteso, parseRfc822Millis("Mon, 10 Aug 2026 07:24:43 GMT"))
    }

    @Test
    fun `una data assente o illeggibile vale zero, mai l'istante del download`() {
        assertEquals(0L, parseRfc822Millis(""))
        assertEquals(0L, parseRfc822Millis("lunedì scorso"))
        // Mese in italiano: il feed è inglese, un mese italiano non è una data valida qui.
        assertEquals(0L, parseRfc822Millis("Lun, 10 Ago 2026 09:24:43 +0200"))
    }

    @Test
    fun `una notizia gia salvata resta la stessa notizia`() {
        val items = parseRssItems(feed, MimitNewsFeed.SOURCE, fetchedAt = 1L)
        val riletti = parseRssItems(feed, MimitNewsFeed.SOURCE, fetchedAt = 2L)

        // La chiave è il link: rileggere il feed non duplica nulla in Room.
        assertEquals(items.map(NewsItem::link), riletti.map(NewsItem::link))
    }
}

package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * L'aggregazione nazionale sulle stesse forme di riga che il MIMIT pubblica davvero: intestazione
 * alla seconda riga, campi separati da `|`, e qualche riga con la provincia scritta fuori formato.
 */
class NationalGplStatsTest {

    private fun price(value: Double) = CsvGplPrice(price = value, isSelf = false, dtComu = "10/08/2026 08:00:00")

    private fun registry(vararg rows: Pair<String, String>): java.io.BufferedReader {
        val header = "idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine"
        val body = rows.joinToString("\n") { (id, provincia) ->
            "$id|Gestore|Eni|Stradale|Impianto $id|Via Roma 1|CITTA|$provincia|40.85|14.27"
        }
        return "Estrazione del 10/08/2026\n$header\n$body\n".reader().buffered()
    }

    @Test
    fun `attribuisce ogni prezzo alla regione della sua provincia`() {
        val prices = mapOf(
            "1" to price(0.700),
            "2" to price(0.720),
            "3" to price(0.900)
        )
        val collected = aggregateByRegion(
            registry("1" to "NA", "2" to "SA", "3" to "MI"),
            prices
        )

        val stats = collected.toStats(publishedDayKey = 20260810, csvLastModified = "x")
        assertNotNull(stats)
        stats!!

        assertEquals(3, stats.stationCount)
        assertEquals(0, stats.skippedRows)
        // Campania: media di 0,700 e 0,720. Lombardia: 0,900.
        assertEquals(0.710, stats.averageOf("Campania")!!.averagePrice, 1e-9)
        assertEquals(2, stats.averageOf("Campania")!!.stationCount)
        assertEquals(0.900, stats.averageOf("Lombardia")!!.averagePrice, 1e-9)
    }

    @Test
    fun `la classifica va dalla piu economica alla piu cara`() {
        val collected = aggregateByRegion(
            registry("1" to "NA", "2" to "MI", "3" to "AO"),
            mapOf("1" to price(0.716), "2" to price(0.737), "3" to price(0.879))
        )
        val stats = collected.toStats(20260810, "x")!!

        assertEquals(listOf("Campania", "Lombardia", "Valle d'Aosta"), stats.regions.map { it.region })
        assertEquals(1, stats.rankOf("Campania"))
        assertEquals(3, stats.rankOf("Valle d'Aosta"))
        assertEquals("Campania", stats.cheapest!!.region)
        assertEquals("Valle d'Aosta", stats.priciest!!.region)
        // Una regione assente non è "prima": è fuori classifica.
        assertEquals(0, stats.rankOf("Molise"))
        assertNull(stats.averageOf("Molise"))
    }

    @Test
    fun `una provincia fuori formato viene contata, non attribuita a caso`() {
        val collected = aggregateByRegion(
            registry("1" to "NA", "2" to "ALESSANDRIA", "3" to "VIA FELICE CAVALLOTTI 12 65015"),
            mapOf("1" to price(0.700), "2" to price(0.800), "3" to price(0.900))
        )
        val stats = collected.toStats(20260810, "x")!!

        assertEquals(1, stats.stationCount)
        assertEquals(2, stats.skippedRows)
        // Le due righe scartate non spostano la media di nessuna regione.
        assertEquals(0.700, stats.averagePrice, 1e-9)
    }

    @Test
    fun `gli impianti senza prezzo GPL restano fuori dall'anagrafica aggregata`() {
        val collected = aggregateByRegion(
            registry("1" to "NA", "2" to "NA", "3" to "NA"),
            mapOf("2" to price(0.750))
        )
        val stats = collected.toStats(20260810, "x")!!

        assertEquals(1, stats.stationCount)
        assertEquals(0, stats.skippedRows)
    }

    @Test
    fun `senza nessun prezzo utilizzabile non si produce nessuna statistica`() {
        val collected = aggregateByRegion(registry("1" to "NA"), emptyMap())
        assertNull(collected.toStats(20260810, "x"))
    }

    @Test
    fun `la mediana regge i valori fuori scala meglio della media`() {
        assertEquals(0.75, median(listOf(0.70, 0.75, 0.80)), 1e-9)
        assertEquals(0.725, median(listOf(0.70, 0.75, 0.80, 0.70)), 1e-9)
        // Un impianto a 3 €/L sposta la media (1,3125) ma quasi non tocca la mediana.
        val fuoriScala = listOf(0.70, 0.75, 0.80, 3.00)
        assertEquals(0.775, median(fuoriScala), 1e-9)
        assertEquals(1.3125, fuoriScala.average(), 1e-9)
        assertEquals(0.0, median(emptyList()), 1e-9)
    }

    @Test
    fun `il giorno di pubblicazione viene dall'header Last-Modified, con mesi inglesi`() {
        assertEquals(20260810, parseHttpDayKey("Mon, 10 Aug 2026 06:45:06 GMT"))
        assertEquals(20260101, parseHttpDayKey("Thu, 01 Jan 2026 06:45:06 GMT"))
        assertEquals(20261231, parseHttpDayKey("Thu, 31 Dec 2026 06:45:06 GMT"))
    }

    @Test
    fun `un Last-Modified assente o illeggibile vale zero, non una data inventata`() {
        assertEquals(0, parseHttpDayKey(""))
        assertEquals(0, parseHttpDayKey("ieri mattina"))
        assertEquals(0, parseHttpDayKey("Mon, 10 Ago 2026 06:45:06 GMT"))
    }
}

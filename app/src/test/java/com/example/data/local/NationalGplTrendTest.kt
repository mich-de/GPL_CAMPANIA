package com.example.data.local

import com.example.data.remote.ItalianRegions
import com.example.data.remote.NationalGplStats
import com.example.data.remote.RegionGplAverage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'andamento è l'unica cosa che l'app afferma e la fonte non dice: va costruito solo su fotografie
 * realmente conservate, mai su un'interpolazione fra due giorni vicini.
 */
class NationalGplTrendTest {

    private fun snapshot(dayKey: Int, average: Double, regions: List<RegionGplAverage> = emptyList()) =
        NationalGplSnapshot(
            dayKey = dayKey,
            capturedAt = 1_000L,
            averagePrice = average,
            medianPrice = average,
            stationCount = 4599,
            skippedRows = 19,
            regionsEncoded = encodeRegions(regions)
        )

    @Test
    fun `senza storico sufficiente non si dichiara nessuna tendenza`() {
        assertEquals(NationalGplTrend(), computeTrend(emptyList()))

        val unaSola = computeTrend(listOf(snapshot(20260810, 0.7546)))
        assertNull(unaSola.sinceWeek)
        assertNull(unaSola.sinceMonth)
        assertEquals(1, unaSola.snapshotCount)

        // Tre letture ravvicinate restano insufficienti: nessuna arriva a sette giorni di distanza.
        val ravvicinate = computeTrend(
            listOf(snapshot(20260810, 0.7546), snapshot(20260809, 0.7550), snapshot(20260808, 0.7552))
        )
        assertNull(ravvicinate.sinceWeek)
        assertEquals(3, ravvicinate.snapshotCount)
    }

    @Test
    fun `confronta con la lettura piu recente fra quelle abbastanza lontane`() {
        val trend = computeTrend(
            listOf(
                snapshot(20260810, 0.7500),
                snapshot(20260809, 0.7510),  // troppo vicina
                snapshot(20260802, 0.7600),  // 8 giorni: è questa che vince
                snapshot(20260801, 0.7700),  // 9 giorni: più vecchia, scartata
                snapshot(20260705, 0.8000)   // 36 giorni: serve per il mese
            )
        )

        val week = trend.sinceWeek!!
        assertEquals(20260802, week.fromDayKey)
        assertEquals(8, week.daysApart)
        assertEquals(-0.0100, week.delta, 1e-9)

        val month = trend.sinceMonth!!
        assertEquals(20260705, month.fromDayKey)
        assertEquals(36, month.daysApart)
        assertEquals(-0.0500, month.delta, 1e-9)
    }

    @Test
    fun `il giorno di distanza e quello reale, non arrotondato a una settimana`() {
        // Un buco nello storico (l'app non è stata aperta) non deve diventare "una settimana fa".
        val trend = computeTrend(listOf(snapshot(20260810, 0.7500), snapshot(20260610, 0.8000)))
        assertEquals(61, trend.sinceWeek!!.daysApart)
        assertEquals(61, trend.sinceMonth!!.daysApart)
    }

    @Test
    fun `la variazione percentuale e calcolata sul valore di partenza`() {
        // Da 0,8000 a 0,7600: -0,04 su 0,80 = -5%.
        val change = PriceChange(fromDayKey = 20260710, daysApart = 31, delta = -0.04)
        assertEquals(-5.0, change.percent(reference = 0.76), 1e-9)
        // Un riferimento non valido non produce un numero assurdo.
        assertEquals(0.0, change.percent(reference = 0.0), 1e-9)
    }

    @Test
    fun `la classifica regionale sopravvive al salvataggio e al rilettura`() {
        val regions = listOf(
            RegionGplAverage("Campania", 0.7163, 426),
            RegionGplAverage("Friuli-Venezia Giulia", 0.7278, 85),
            RegionGplAverage("Valle d'Aosta", 0.8792, 5)
        )
        val riletta = snapshot(20260810, 0.7546, regions).regions

        assertEquals(3, riletta.size)
        assertEquals("Campania", riletta[0].region)
        assertEquals(0.7163, riletta[0].averagePrice, 1e-4)
        assertEquals(426, riletta[0].stationCount)
        // I nomi con trattino e apostrofo non si rompono nella serializzazione compatta.
        assertEquals("Friuli-Venezia Giulia", riletta[1].region)
        assertEquals("Valle d'Aosta", riletta[2].region)
    }

    @Test
    fun `una stringa vuota o corrotta non produce regioni fantasma`() {
        assertTrue(decodeRegions("").isEmpty())
        assertTrue(decodeRegions("Campania:non-un-numero:426").isEmpty())
        assertEquals(1, decodeRegions("rotta;Campania:0.7163:426").size)
    }

    @Test
    fun `la Campania si ritrova nella lettura salvata con la sua posizione`() {
        val snap = snapshot(
            20260810, 0.7546,
            listOf(
                RegionGplAverage("Campania", 0.7163, 426),
                RegionGplAverage("Lombardia", 0.7368, 502)
            )
        )
        assertEquals(ItalianRegions.HOME_REGION, snap.homeRegion()!!.region)
        assertEquals(426, snap.homeRegion()!!.stationCount)
        assertEquals(1, snap.homeRegionRank())

        // Se la lettura non contiene la Campania non le si assegna una posizione.
        val senzaCampania = snapshot(20260810, 0.7546, listOf(RegionGplAverage("Lombardia", 0.7368, 502)))
        assertNull(senzaCampania.homeRegion())
        assertEquals(0, senzaCampania.homeRegionRank())
    }

    @Test
    fun `la fotografia conserva i numeri della lettura da cui nasce`() {
        val stats = NationalGplStats(
            averagePrice = 0.7546,
            medianPrice = 0.7460,
            stationCount = 4580,
            skippedRows = 19,
            regions = listOf(RegionGplAverage("Campania", 0.7163, 426)),
            publishedDayKey = 20260810,
            csvLastModified = "Mon, 10 Aug 2026 06:45:06 GMT"
        )

        val snap = stats.toSnapshot(capturedAt = 5_000L)

        assertEquals(20260810, snap.dayKey)
        assertEquals(5_000L, snap.capturedAt)
        assertEquals(0.7546, snap.averagePrice, 1e-9)
        assertEquals(0.7460, snap.medianPrice, 1e-9)
        assertEquals(4580, snap.stationCount)
        assertEquals(19, snap.skippedRows)
        assertEquals(1, snap.regions.size)
    }
}

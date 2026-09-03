package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Da quanto tempo i gestori hanno comunicato i prezzi che l'app sta mostrando. */
class PriceFreshnessTest {

    private fun station(priceDay: Int, id: Long = priceDay.toLong()) = RemoteGplStation(
        impiantoId = id,
        nome = "Distributore $id",
        brand = "Eni",
        via = "Via Roma 1",
        comune = "NAPOLI",
        provincia = "NA",
        latitude = 40.85,
        longitude = 14.27,
        gplPrice = 0.72,
        gplIsSelf = false,
        priceDate = "",
        priceDay = priceDay
    )

    @Test
    fun `separa oggi, ultima settimana e impianti fermi da oltre un mese`() {
        val stations = listOf(
            station(20260810),  // oggi
            station(20260809),  // ieri
            station(20260804),  // 6 giorni fa: dentro la settimana
            station(20260803),  // 7 giorni fa: fuori dalla settimana, ma non ancora "fermo"
            station(20260701)   // 40 giorni fa: fermo
        )

        val freshness = stations.priceFreshness(todayKey = 20260810)

        assertEquals(1, freshness.today)
        assertEquals(3, freshness.withinWeek)
        assertEquals(1, freshness.olderThanMonth)
        assertEquals(0, freshness.withoutDate)
    }

    @Test
    fun `una data assente non viene mai contata come recente`() {
        val freshness = listOf(station(0), station(20260810)).priceFreshness(todayKey = 20260810)

        assertEquals(1, freshness.withoutDate)
        assertEquals(1, freshness.today)
        assertEquals(1, freshness.withinWeek)
    }

    @Test
    fun `una comunicazione datata domani conta come odierna, non come negativa`() {
        // Capita con l'orologio del device indietro rispetto alla fonte: l'unica lettura sensata di
        // un prezzo appena comunicato è "di oggi".
        val freshness = listOf(station(20260811)).priceFreshness(todayKey = 20260810)

        assertEquals(1, freshness.today)
        assertEquals(0, freshness.olderThanMonth)
    }

    @Test
    fun `il calcolo dei giorni attraversa mesi, anni e anni bisestili`() {
        // 2024 è bisestile: dal 28/02 al 01/03 passano due giorni, non uno.
        assertEquals(2L, epochDayOf(20240301)!! - epochDayOf(20240228)!!)
        // 2026 non lo è.
        assertEquals(1L, epochDayOf(20260301)!! - epochDayOf(20260228)!!)
        // Capodanno.
        assertEquals(1L, epochDayOf(20260101)!! - epochDayOf(20251231)!!)
        // Riferimento noto: l'epoch Unix.
        assertEquals(0L, epochDayOf(19700101))
    }

    @Test
    fun `una data non riconoscibile resta sconosciuta invece di diventare una data plausibile`() {
        assertNull(epochDayOf(0))
        assertNull(epochDayOf(20261301))
        assertNull(epochDayOf(20260800))
    }
}

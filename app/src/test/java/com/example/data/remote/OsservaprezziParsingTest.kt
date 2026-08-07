package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifica del filtro GPL su una risposta reale dell'API Osservaprezzi (record copiati senza
 * modifiche da `POST /ospzApi/search/servicearea`). Robolectric serve solo a fornire `org.json`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OsservaprezziParsingTest {

    /** Il primo impianto vende GPL (self e servito allo stesso prezzo), il secondo no. */
    private val realResponse = """
        {"results":[
          {"id":56697,"name":"FUCCI FUEL 3","fuels":[
             {"id":120927615,"price":1.929,"name":"Benzina","fuelId":1,"isSelf":false},
             {"id":120927612,"price":1.959,"name":"Gasolio","fuelId":2,"isSelf":true},
             {"id":120927617,"price":0.659,"name":"GPL","fuelId":4,"isSelf":false},
             {"id":120927616,"price":0.659,"name":"GPL","fuelId":4,"isSelf":true}],
           "location":{"lat":41.30291,"lng":16.30747},
           "insertDate":"2026-08-07T11:54:54+02:00",
           "address":"SS 16 BIS DIR.BARI KM.748+700 SN 76121 - BARLETTA BT",
           "brand":"PompeBianche","distance":null},
          {"id":54253,"name":"8426","fuels":[
             {"id":120928669,"price":1.999,"name":"Benzina","fuelId":1,"isSelf":true},
             {"id":120831071,"price":2.099,"name":"Gasolio","fuelId":2,"isSelf":true}],
           "location":{"lat":37.672982,"lng":13.2368707},
           "insertDate":"2026-08-07T12:24:10+02:00",
           "address":"PAPA GIOVANNI XXII SNC 90030 - GIULIANA PA",
           "brand":"Tamoil","distance":null}
        ]}
    """.trimIndent()

    @Test
    fun `tiene solo gli impianti che vendono GPL`() {
        val stations = OsservaprezziApiClient.parseResponse(realResponse, "BT")

        assertEquals(1, stations.size)
        assertEquals(56697L, stations.single().impiantoId)
    }

    @Test
    fun `estrae prezzo, coordinate e data reale di comunicazione`() {
        val station = OsservaprezziApiClient.parseResponse(realResponse, "BT").single()

        assertEquals(0.659, station.gplPrice, 0.0001)
        assertEquals(41.30291, station.latitude!!, 0.00001)
        assertEquals(16.30747, station.longitude!!, 0.00001)
        assertEquals("07 Ago 2026", station.priceDate)
        assertEquals("BARLETTA", station.comune)
        assertEquals("BT", station.provincia)
    }

    @Test
    fun `a parita di prezzo vince il self, che e la modalita disponibile a quella cifra`() {
        val station = OsservaprezziApiClient.parseResponse(realResponse, "BT").single()

        assertTrue(station.gplIsSelf)
    }

    @Test
    fun `un impianto senza prezzo GPL valido viene scartato invece di riceverne uno finto`() {
        val noPrice = """
            {"results":[{"id":1,"name":"X","fuels":[{"price":0,"fuelId":4,"isSelf":true}],
             "location":{"lat":40.8,"lng":14.2},"insertDate":"2026-08-07T11:54:54+02:00",
             "address":"VIA TEST 1 80100 - NAPOLI NA","brand":"Eni"}]}
        """.trimIndent()

        assertTrue(OsservaprezziApiClient.parseResponse(noPrice, "NA").isEmpty())
    }

    @Test
    fun `coordinate assenti restano null e la provincia ricade su quella richiesta`() {
        val noLocation = """
            {"results":[{"id":2,"name":"Y","fuels":[{"price":0.699,"fuelId":4,"isSelf":false}],
             "insertDate":"2026-08-07T11:54:54+02:00","address":"","brand":"Eni"}]}
        """.trimIndent()

        val station = OsservaprezziApiClient.parseResponse(noLocation, "SA").single()

        assertNull(station.latitude)
        assertNull(station.longitude)
        assertEquals("SA", station.provincia)
        assertEquals("", station.comune)
    }

    @Test
    fun `una risposta vuota o malformata non produce stazioni`() {
        assertTrue(OsservaprezziApiClient.parseResponse("", "NA").isEmpty())
        assertTrue(OsservaprezziApiClient.parseResponse("{}", "NA").isEmpty())
    }
}

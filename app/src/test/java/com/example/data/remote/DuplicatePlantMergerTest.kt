package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Casi presi dai 428 distributori campani realmente restituiti dalla fonte ufficiale il 7 agosto
 * 2026: due iscrizioni doppie da unire e le coppie ravvicinate che invece sono impianti distinti.
 */
class DuplicatePlantMergerTest {

    private fun station(
        id: Long,
        nome: String,
        via: String,
        comune: String = "Pompei",
        lat: Double? = 40.723622169200425,
        lng: Double? = 14.497255235910416,
        price: Double = 0.679,
        priceDay: Int = 20260803
    ) = RemoteGplStation(
        impiantoId = id, nome = nome, brand = "PompeBianche", via = via, comune = comune,
        provincia = "NA", latitude = lat, longitude = lng, gplPrice = price, gplIsSelf = false,
        priceDate = "03 Ago 2026", priceDay = priceDay
    )

    @Test
    fun `la stessa pompa iscritta due volte diventa una sola riga`() {
        // Stesse coordinate al centimetro, stesso prezzo, stessa data: cambia solo "e C." / "& C.".
        val merged = listOf(
            station(63247, "FRATELLI LONGOBARDI DI DEL GAUDIO NUNZIA e C. SAS", "FONTANELLE 341"),
            station(33882, "FRATELLI LONGOBARDI DI DEL GAUDIO NUNZIA & C. SAS", "FONTANELLE 341")
        ).mergeDuplicatePlants()

        assertEquals(1, merged.size)
        // A parità di data e prezzo resta l'iscrizione più recente.
        assertEquals(63247L, merged.single().impiantoId)
    }

    @Test
    fun `fra due iscrizioni resta quella che ha comunicato il prezzo piu di recente`() {
        val merged = listOf(
            station(
                20334, "STAZIONE DI SERVIZIO ENI", "VIA POMIGLIANO SNC", comune = "Somma Vesuviana",
                lat = 40.89165649482885, lng = 14.422374665737152, price = 0.742, priceDay = 20260709
            ),
            station(
                62436, "38149 SOMMA VESUVIANA VIA POMIGLIANO", "VIA POMIGLIANO SNC",
                comune = "Somma Vesuviana", lat = 40.89159, lng = 14.422277,
                price = 0.744, priceDay = 20260806
            )
        ).mergeDuplicatePlants()

        // 11 m di distanza: stessa pompa. Vince il 6 agosto, anche se costa 0,2 centesimi in più:
        // il prezzo fermo al 9 luglio non descrive più la realtà.
        assertEquals(1, merged.size)
        assertEquals(62436L, merged.single().impiantoId)
        assertEquals(0.744, merged.single().gplPrice, 0.0001)
    }

    @Test
    fun `due civici diversi della stessa via restano due distributori`() {
        // Mugnano di Napoli: via Pietro Nenni 42 e 11 distano 15 m ma sono impianti diversi.
        val merged = listOf(
            station(57272, "Distributore A", "Via Pietro Nenni 42", comune = "Mugnano Di Napoli",
                lat = 40.9101, lng = 14.2101),
            station(48596, "Distributore B", "VIA PIETRO NENNI 11", comune = "Mugnano Di Napoli",
                lat = 40.91023, lng = 14.2101)
        ).mergeDuplicatePlants()

        assertEquals(2, merged.size)
    }

    @Test
    fun `stesso indirizzo ma lontani restano due distributori`() {
        val merged = listOf(
            station(1, "Uno", "VIA ROMA SNC", lat = 40.8500, lng = 14.2700),
            station(2, "Due", "VIA ROMA SNC", lat = 40.8600, lng = 14.2700) // ~1,1 km
        ).mergeDuplicatePlants()

        assertEquals(2, merged.size)
    }

    @Test
    fun `senza coordinate non si unisce niente`() {
        val merged = listOf(
            station(1, "Uno", "VIA ROMA SNC", lat = null, lng = null),
            station(2, "Due", "VIA ROMA SNC", lat = null, lng = null)
        ).mergeDuplicatePlants()

        assertEquals(2, merged.size)
    }

    @Test
    fun `una lista senza doppioni resta identica, nello stesso ordine`() {
        val stations = listOf(
            station(1, "Uno", "VIA ALFA 1", lat = 40.85, lng = 14.27),
            station(2, "Due", "VIA BETA 2", lat = 40.86, lng = 14.28),
            station(3, "Tre", "VIA GAMMA 3", lat = 40.87, lng = 14.29)
        )

        assertEquals(stations, stations.mergeDuplicatePlants())
    }
}

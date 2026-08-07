package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Casi presi dalle risposte reali dell'API Osservaprezzi: sono le forme che l'indirizzo assume
 * davvero, comprese quelle che avevano fatto fallire il primo parser a regex singola.
 */
class MimitAddressParserTest {

    @Test
    fun `indirizzo con CAP viene scomposto in via, cap, comune e provincia`() {
        val parsed = MimitAddressParser.parse("Via delle Dune 5  81039 - VILLA LITERNO CE")!!

        assertEquals("Via delle Dune 5", parsed.via)
        assertEquals("81039", parsed.cap)
        assertEquals("VILLA LITERNO", parsed.comune)
        assertEquals("CE", parsed.provincia)
    }

    @Test
    fun `comune composto da piu parole resta intero`() {
        val parsed = MimitAddressParser.parse("PAPA GIOVANNI XXII SNC 90030 - GIULIANA PA")!!

        assertEquals("PAPA GIOVANNI XXII SNC", parsed.via)
        assertEquals("GIULIANA", parsed.comune)
        assertEquals("PA", parsed.provincia)
    }

    @Test
    fun `senza CAP lo split avviene sull'ultimo trattino, non sul primo`() {
        val parsed = MimitAddressParser.parse("S.S. 7/IV - KM 2+650   - CELLOLE CE")!!

        // Il primo trattino fa parte del nome della strada e va conservato.
        assertEquals("S.S. 7/IV - KM 2+650", parsed.via)
        assertEquals("", parsed.cap)
        assertEquals("CELLOLE", parsed.comune)
        assertEquals("CE", parsed.provincia)
    }

    @Test
    fun `senza CAP con comune di due parole`() {
        val parsed =
            MimitAddressParser.parse("STRADA STATALE 162 DIR. KM6 + 513 DIREZIONE NAPOLI   - POLLENA TROCCHIA NA")!!

        assertEquals("STRADA STATALE 162 DIR. KM6 + 513 DIREZIONE NAPOLI", parsed.via)
        assertEquals("POLLENA TROCCHIA", parsed.comune)
        assertEquals("NA", parsed.provincia)
    }

    @Test
    fun `spazi multipli e separatori in coda vengono normalizzati`() {
        val parsed = MimitAddressParser.parse("SS 16 BIS DIR.BARI KM.748+700 SN 76121 - BARLETTA BT")!!

        assertEquals("SS 16 BIS DIR.BARI KM.748+700 SN", parsed.via)
        assertEquals("76121", parsed.cap)
        assertEquals("BARLETTA", parsed.comune)
    }

    @Test
    fun `un indirizzo irriconoscibile torna null invece di essere indovinato`() {
        assertNull(MimitAddressParser.parse(null))
        assertNull(MimitAddressParser.parse("   "))
        assertNull(MimitAddressParser.parse("VIA SENZA COMUNE NE PROVINCIA"))
    }
}

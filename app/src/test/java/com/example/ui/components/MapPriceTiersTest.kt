package com.example.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il colore di un pin è un'affermazione sul prezzo, e come tutte le affermazioni dell'app deve
 * poggiare sui dati reali: qui si verifica che le soglie nascano dai prezzi in lista e che, quando
 * quei prezzi non bastano a distinguere niente, nessuna fascia venga dichiarata.
 */
class MapPriceTiersTest {

    /** Nove prezzi reali, come si trovano in una lista di distributori campani. */
    private val prezzi = listOf(
        0.699, 0.705, 0.709, 0.715, 0.719, 0.722, 0.729, 0.735, 0.749
    )

    @Test
    fun `le soglie dividono in tre i prezzi realmente presenti`() {
        val tiers = computePriceTiers(prezzi)

        assertTrue(tiers.isMeaningful)
        assertEquals(9, tiers.sampleSize)
        // Primo terzo: 0,699 0,705 0,709 -> il verde arriva fino a 0,709.
        assertEquals(0.709, tiers.cheapMax!!, 1e-9)
        // Ultimo terzo: 0,729 0,735 0,749 -> il rosso comincia da 0,729.
        assertEquals(0.729, tiers.priceyMin!!, 1e-9)
    }

    @Test
    fun `ogni prezzo finisce nella fascia che gli spetta`() {
        val tiers = computePriceTiers(prezzi)

        assertEquals(PriceTier.ECONOMICO, tiers.tierOf(0.699))
        assertEquals(PriceTier.ECONOMICO, tiers.tierOf(0.709))   // il confine appartiene al verde
        assertEquals(PriceTier.MEDIO, tiers.tierOf(0.715))
        assertEquals(PriceTier.MEDIO, tiers.tierOf(0.722))
        assertEquals(PriceTier.CARO, tiers.tierOf(0.729))        // il confine appartiene al rosso
        assertEquals(PriceTier.CARO, tiers.tierOf(0.749))
    }

    @Test
    fun `con pochi distributori non si dichiara nessuna fascia`() {
        val pochi = computePriceTiers(listOf(0.699, 0.719, 0.749))

        assertFalse(pochi.isMeaningful)
        assertNull(pochi.cheapMax)
        assertNull(pochi.priceyMin)
        assertEquals(3, pochi.sampleSize)
        // Un prezzo esiste, ma non c'è un confronto: il pin resta neutro invece di mentire.
        assertEquals(PriceTier.NEUTRA, pochi.tierOf(0.699))
    }

    @Test
    fun `una lista vuota non produce soglie`() {
        val vuoto = computePriceTiers(emptyList())

        assertFalse(vuoto.isMeaningful)
        assertEquals(0, vuoto.sampleSize)
        assertEquals(PriceTier.NEUTRA, vuoto.tierOf(0.719))
    }

    @Test
    fun `i prezzi mancanti non spostano le soglie`() {
        // Nella fonte un prezzo assente arriva come zero: contarlo abbasserebbe il terzo economico
        // fino a colorare di verde distributori che non lo sono.
        val conBuchi = computePriceTiers(prezzi + listOf(0.0, 0.0, -1.0))

        assertEquals(9, conBuchi.sampleSize)
        assertEquals(0.709, conBuchi.cheapMax!!, 1e-9)
        assertEquals(0.729, conBuchi.priceyMin!!, 1e-9)
        // Un prezzo non valido non appartiene a nessuna fascia.
        assertEquals(PriceTier.NEUTRA, conBuchi.tierOf(0.0))
    }

    @Test
    fun `se i prezzi sono tutti uguali non esiste un terzo piu caro`() {
        val identici = computePriceTiers(List(20) { 0.719 })

        assertFalse(identici.isMeaningful)
        assertEquals(20, identici.sampleSize)
        assertEquals(PriceTier.NEUTRA, identici.tierOf(0.719))
    }

    @Test
    fun `le soglie seguono il mercato invece di restare ferme nel codice`() {
        // Stessa distribuzione, tutta più alta di 5 centesimi: le fasce si spostano con lei, e i
        // distributori più economici restano verdi invece di diventare rossi in blocco.
        val rincarati = computePriceTiers(prezzi.map { it + 0.05 })

        assertEquals(0.759, rincarati.cheapMax!!, 1e-9)
        assertEquals(0.779, rincarati.priceyMin!!, 1e-9)
        assertEquals(PriceTier.ECONOMICO, rincarati.tierOf(0.749))
    }
}

package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La tabella province → regioni è l'unico punto in cui una media regionale può sbagliare in modo
 * invisibile: un codice attribuito alla regione sbagliata sposta decine di impianti senza che nulla
 * segnali l'errore.
 */
class ItalianRegionsTest {

    @Test
    fun `copre le venti regioni e le cento e passa province`() {
        assertEquals(20, ItalianRegions.all.size)
        assertEquals(107, ItalianRegions.provinceCodes.size)
    }

    @Test
    fun `le cinque province della Campania stanno in Campania`() {
        listOf("AV", "BN", "CE", "NA", "SA").forEach { code ->
            assertEquals("la provincia $code", "Campania", ItalianRegions.of(code))
        }
        assertEquals("Campania", ItalianRegions.HOME_REGION)
    }

    @Test
    fun `riconosce i codici anche minuscoli o con spazi, come arrivano dal CSV`() {
        assertEquals("Lombardia", ItalianRegions.of("mi"))
        assertEquals("Lazio", ItalianRegions.of(" RM "))
    }

    @Test
    fun `un valore fuori formato non viene attribuito a nessuna regione`() {
        // Nell'anagrafica reale del MIMIT 19 righe hanno qui il nome della città o un indirizzo:
        // meglio nessuna regione che una regione sbagliata.
        assertNull(ItalianRegions.of("ALESSANDRIA"))
        assertNull(ItalianRegions.of("VIA FELICE CAVALLOTTI 12 65015"))
        assertNull(ItalianRegions.of(""))
    }

    @Test
    fun `regioni note che si scrivono facilmente sbagliate`() {
        assertEquals("Trentino-Alto Adige", ItalianRegions.of("BZ"))
        assertEquals("Trentino-Alto Adige", ItalianRegions.of("TN"))
        assertEquals("Friuli-Venezia Giulia", ItalianRegions.of("TS"))
        assertEquals("Valle d'Aosta", ItalianRegions.of("AO"))
        // Province istituite di recente, che una tabella vecchia non avrebbe.
        assertEquals("Sardegna", ItalianRegions.of("SU"))
        assertEquals("Marche", ItalianRegions.of("FM"))
        assertEquals("Lombardia", ItalianRegions.of("MB"))
    }

    @Test
    fun `l'elenco delle regioni non contiene doppioni`() {
        assertEquals(ItalianRegions.all.size, ItalianRegions.all.distinct().size)
        assertTrue(ItalianRegions.HOME_REGION in ItalianRegions.all)
    }
}

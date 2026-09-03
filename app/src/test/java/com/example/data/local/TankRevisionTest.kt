package com.example.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Il promemoria del serbatoio è l'unica funzione dell'app che riguarda un dato personale, e sbagliare
 * qui costa: un serbatoio scaduto non fa passare la revisione. Nessuna data viene dedotta — l'app
 * conta i giorni e basta.
 */
class TankRevisionTest {

    @Test
    fun `conta i giorni che mancano e quelli passati`() {
        val revision = TankRevision(expiryDayKey = 20260901)

        assertEquals(22, revision.daysRemaining(todayKey = 20260810))
        assertEquals(0, revision.daysRemaining(todayKey = 20260901))
        assertEquals(-9, revision.daysRemaining(todayKey = 20260910))
    }

    @Test
    fun `una scadenza non valida non produce un conto alla rovescia`() {
        assertNull(TankRevision(expiryDayKey = 0).daysRemaining(20260810))
        assertNull(TankRevision(expiryDayKey = 20261301).daysRemaining(20260810))
        assertNull(TankRevision(expiryDayKey = 20260901).daysRemaining(todayKey = 0))
        assertNull(TankRevision(expiryDayKey = 0).status(20260810))
    }

    @Test
    fun `i tre stati cambiano al confine dei novanta giorni di preavviso`() {
        val revision = TankRevision(expiryDayKey = 20261110)

        // 92 giorni prima: ancora valido senza urgenza.
        assertEquals(TankRevision.Status.VALIDO, revision.status(todayKey = 20260810))
        // Esattamente 90 giorni prima: comincia il preavviso.
        assertEquals(TankRevision.Status.IN_SCADENZA, revision.status(todayKey = 20260812))
        // Il giorno stesso conta ancora come in scadenza, non come scaduto.
        assertEquals(TankRevision.Status.IN_SCADENZA, revision.status(todayKey = 20261110))
        assertEquals(TankRevision.Status.SCADUTO, revision.status(todayKey = 20261111))
    }

    @Test
    fun `la proposta somma i dieci anni del Regolamento UNECE 67`() {
        assertEquals(20360610, expiryAfterValidityPeriod(20260610))
        assertEquals(20301231, expiryAfterValidityPeriod(20201231))
        assertEquals(TankRevision.VALIDITY_YEARS, 10)
    }

    @Test
    fun `il 29 febbraio arretra al 28 quando fra dieci anni non esiste`() {
        // 2024 è bisestile, 2034 no.
        assertEquals(20340228, expiryAfterValidityPeriod(20240229))
        // 2016 -> 2026: nemmeno il 2026 è bisestile.
        assertEquals(20260228, expiryAfterValidityPeriod(20160229))
        // 2012 -> 2022: idem. Un anno bisestile di arrivo terrebbe il 29.
        assertEquals(20220228, expiryAfterValidityPeriod(20120229))
    }

    @Test
    fun `senza una data di riferimento valida non si propone nessuna scadenza`() {
        assertNull(expiryAfterValidityPeriod(0))
        assertNull(expiryAfterValidityPeriod(20261340))
        assertNull(expiryAfterValidityPeriod(19690101))
    }

    @Test
    fun `legge le date come le scrive una persona`() {
        assertEquals(20260810, parseItalianDate("10/08/2026"))
        assertEquals(20260810, parseItalianDate("10-8-2026"))
        assertEquals(20260810, parseItalianDate("10.08.2026"))
        assertEquals(20260810, parseItalianDate("  10 / 08 / 2026  "))
    }

    @Test
    fun `rifiuta le date che sul calendario non esistono`() {
        assertNull(parseItalianDate("31/02/2026"))
        assertNull(parseItalianDate("29/02/2026"))   // 2026 non è bisestile
        assertEquals(20240229, parseItalianDate("29/02/2024"))
        assertNull(parseItalianDate("31/04/2026"))
        assertNull(parseItalianDate("10/13/2026"))
        assertNull(parseItalianDate("10/08"))
        assertNull(parseItalianDate("domani"))
        assertNull(parseItalianDate(""))
    }

    @Test
    fun `formatta la data come la si legge sul libretto`() {
        assertEquals("10/08/2026", formatDayKey(20260810))
        assertEquals("01/01/2030", formatDayKey(20300101))
        assertEquals("", formatDayKey(0))
        assertEquals("", formatDayKey(20261340))
    }

    @Test
    fun `il conto alla rovescia usa le parole che userebbe una persona`() {
        assertEquals("scade oggi", formatCountdown(0))
        assertEquals("scade domani", formatCountdown(1))
        assertEquals("fra 12 giorni", formatCountdown(12))
        assertEquals("fra circa 5 mesi", formatCountdown(150))
        assertEquals("fra poco più di un anno", formatCountdown(400))
        assertEquals("fra 8 anni", formatCountdown(3000))
        assertEquals("scaduto da ieri", formatCountdown(-1))
        assertEquals("scaduto da 40 giorni", formatCountdown(-40))
    }
}

package com.example.data.local

import com.example.data.remote.epochDayOf

/**
 * Scadenza del serbatoio GPL dell'auto di chi usa l'app.
 *
 * Il dato è **suo**, preso dal suo libretto o dalla punzonatura sul serbatoio: l'app non lo deduce
 * dalla targa né lo stima. Ricorda soltanto una data che è facile dimenticare e costosa da
 * dimenticare, perché un serbatoio scaduto non fa passare la revisione periodica.
 *
 * [referenceDayKey] è la data da cui la scadenza è stata calcolata (collaudo dell'impianto o prima
 * immatricolazione se il GPL è di serie), conservata solo per poter rimostrare all'utente da dove
 * viene il numero. Vale 0 quando la scadenza è stata inserita direttamente.
 */
data class TankRevision(
    val expiryDayKey: Int,
    val referenceDayKey: Int = 0,
    val plate: String = ""
) {
    enum class Status { SCADUTO, IN_SCADENZA, VALIDO }

    /** Giorni che mancano; negativo se la scadenza è passata. `null` se la data non è valida. */
    fun daysRemaining(todayKey: Int): Int? {
        val expiry = epochDayOf(expiryDayKey) ?: return null
        val today = epochDayOf(todayKey) ?: return null
        return (expiry - today).toInt()
    }

    fun status(todayKey: Int): Status? = when (val remaining = daysRemaining(todayKey)) {
        null -> null
        in Int.MIN_VALUE..-1 -> Status.SCADUTO
        in 0..WARNING_DAYS -> Status.IN_SCADENZA
        else -> Status.VALIDO
    }

    companion object {
        /**
         * Tre mesi di preavviso: è il margine che serve davvero, perché le officine autorizzate a
         * sostituire il serbatoio hanno spesso liste d'attesa di settimane.
         */
        const val WARNING_DAYS = 90

        /** Validità del serbatoio GPL per autotrazione secondo il Regolamento UNECE n. 67. */
        const val VALIDITY_YEARS = 10
    }
}

/**
 * Scadenza calcolata dalla data di collaudo dell'impianto (o di prima immatricolazione, se il GPL è
 * montato di serie) applicando i dieci anni di validità del Regolamento UNECE 67.
 *
 * È solo una **proposta**, che resta modificabile: il valore che conta è quello scritto sul libretto
 * o punzonato sul serbatoio, e in caso di discordanza vince quello. Il 29 febbraio arretra al 28,
 * perché fra dieci anni quel giorno non esiste.
 */
fun expiryAfterValidityPeriod(referenceDayKey: Int): Int? {
    if (epochDayOf(referenceDayKey) == null) return null
    val year = referenceDayKey / 10_000 + TankRevision.VALIDITY_YEARS
    val month = (referenceDayKey / 100) % 100
    val day = referenceDayKey % 100
    val safeDay = if (month == 2 && day == 29 && !isLeapYear(year)) 28 else day
    return year * 10_000 + month * 100 + safeDay
}

private fun isLeapYear(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

/** `20260810` -> `"10/08/2026"`. Stringa vuota se la data non è valida. */
fun formatDayKey(dayKey: Int): String {
    if (epochDayOf(dayKey) == null) return ""
    return "%02d/%02d/%04d".format(dayKey % 100, (dayKey / 100) % 100, dayKey / 10_000)
}

/** `"10/08/2026"` o `"10-8-2026"` -> `20260810`. `null` se non è una data reale. */
fun parseItalianDate(text: String): Int? {
    val parts = text.trim().split('/', '-', '.').mapNotNull { it.trim().toIntOrNull() }
    if (parts.size != 3) return null
    val (day, month, year) = parts
    val dayKey = year * 10_000 + month * 100 + day
    // epochDayOf rifiuta mesi e giorni fuori intervallo: se passa, la data esiste sul calendario.
    return if (epochDayOf(dayKey) != null && day <= daysInMonth(year, month)) dayKey else null
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 0
}

/** "fra 3 mesi", "fra 12 giorni", "scaduto da 40 giorni": il tempo come lo direbbe una persona. */
fun formatCountdown(days: Int): String = when {
    days < -1 -> "scaduto da ${-days} giorni"
    days == -1 -> "scaduto da ieri"
    days == 0 -> "scade oggi"
    days == 1 -> "scade domani"
    days < 60 -> "fra $days giorni"
    days < 365 -> "fra circa ${days / 30} mesi"
    days < 730 -> "fra poco più di un anno"
    else -> "fra ${days / 365} anni"
}

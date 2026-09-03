package com.example.data.remote

import java.util.Calendar

/**
 * Data odierna del device in forma `aaaammgg`, la stessa usata dalla fonte per le comunicazioni.
 *
 * Qui `Calendar` è la scelta giusta proprio perché dipende dal fuso: "oggi" è il giorno di chi sta
 * guardando lo schermo. Le funzioni che fanno i conti sulle date restano invece pure e ricevono
 * questo valore dall'esterno, così i test non cambiano risultato a seconda di quando girano.
 */
fun todayDateKey(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.YEAR) * 10_000 +
        (calendar.get(Calendar.MONTH) + 1) * 100 +
        calendar.get(Calendar.DAY_OF_MONTH)
}

/**
 * Da quanto tempo i gestori hanno comunicato i prezzi che l'app sta mostrando.
 *
 * È la misura che dice se un aggiornamento "riuscito" è servito davvero: 426 righe scaricate con
 * prezzi comunicati un mese fa restano 426 righe vecchie. Il conteggio si basa sulla data reale di
 * comunicazione (`insertDate` dell'API, `dtComu` dei CSV), mai sull'istante del download.
 */
data class PriceFreshness(
    /** Comunicati oggi. */
    val today: Int = 0,
    /** Comunicati negli ultimi 7 giorni, oggi incluso. */
    val withinWeek: Int = 0,
    /** Comunicati più di 30 giorni fa: impianti di fatto fermi. */
    val olderThanMonth: Int = 0,
    /** Data di comunicazione assente o non riconoscibile: non si finge di saperla. */
    val withoutDate: Int = 0
)

/**
 * [todayKey] è la data del device in forma `aaaammgg`, la stessa forma di
 * [RemoteGplStation.priceDay]. Passarla dall'esterno mantiene la funzione pura e i test
 * indipendenti dal giorno in cui girano.
 *
 * Una data futura (orologio del device indietro rispetto alla fonte) viene contata come "oggi":
 * è l'unica lettura sensata di un dato appena comunicato.
 */
fun List<RemoteGplStation>.priceFreshness(todayKey: Int): PriceFreshness {
    val today = epochDayOf(todayKey) ?: return PriceFreshness(withoutDate = size)

    var communicatedToday = 0
    var withinWeek = 0
    var olderThanMonth = 0
    var withoutDate = 0

    for (station in this) {
        val day = epochDayOf(station.priceDay)
        if (day == null) {
            withoutDate++
            continue
        }
        val ageInDays = (today - day).coerceAtLeast(0L)
        if (ageInDays == 0L) communicatedToday++
        if (ageInDays < 7L) withinWeek++
        if (ageInDays > 30L) olderThanMonth++
    }

    return PriceFreshness(communicatedToday, withinWeek, olderThanMonth, withoutDate)
}

/**
 * Converte `aaaammgg` nel numero di giorni dal 1970-01-01, per poter fare differenze fra date.
 *
 * L'aritmetica è quella dell'algoritmo `days_from_civil` di Howard Hinnant: gestisce da sé anni
 * bisestili e secoli. Serve perché `java.time` è disponibile solo dall'API 26 e questa app parte
 * dalla 24; `Calendar` funzionerebbe, ma dipende dal fuso orario del device e renderebbe i test
 * non deterministici.
 */
internal fun epochDayOf(dateKey: Int): Long? {
    if (dateKey <= 0) return null
    val year = dateKey / 10_000
    val month = (dateKey / 100) % 100
    val day = dateKey % 100
    if (year < 1970 || month !in 1..12 || day !in 1..31) return null

    val shiftedYear = if (month <= 2) year - 1 else year
    val era = (if (shiftedYear >= 0) shiftedYear else shiftedYear - 399) / 400
    val yearOfEra = shiftedYear - era * 400
    val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era.toLong() * 146_097L + dayOfEra.toLong() - 719_468L
}

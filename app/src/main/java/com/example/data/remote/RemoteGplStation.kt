package com.example.data.remote

/**
 * Distributore GPL così come arriva dall'Osservaprezzi carburanti del MIMIT, normalizzato in modo
 * identico dalle due fonti (API JSON e CSV open data) così che il repository non debba sapere
 * quale delle due ha risposto.
 *
 * [latitude]/[longitude] sono le coordinate **ufficiali dell'impianto**, non una stima: se
 * mancassero restano `null`, mai sostituite da un valore verosimile.
 */
data class RemoteGplStation(
    val impiantoId: Long,
    val nome: String,
    val brand: String,
    val via: String,
    val comune: String,
    val provincia: String,
    val latitude: Double?,
    val longitude: Double?,
    val gplPrice: Double,
    val gplIsSelf: Boolean,
    /** Data reale di comunicazione del prezzo, già formattata (es. "05 Ago 2026"). Vuota se assente. */
    val priceDate: String,
    /** La stessa data in forma confrontabile (`aaaammgg`), 0 se ignota. Serve solo a ordinare. */
    val priceDay: Int = 0
)

/** Le 5 province della Campania, nell'ordine usato per le richieste e i log. */
val CAMPANIA_PROVINCES = listOf("AV", "BN", "CE", "NA", "SA")

/** Identificativo del GPL nel censimento carburanti del MIMIT (`/ospzApi/registry/fuels`). */
const val FUEL_ID_GPL = 4

private val MONTHS = arrayOf(
    "Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic"
)

/**
 * Data di comunicazione del prezzo nelle due forme che servono: quella da mostrare e quella da
 * confrontare. [sortKey] vale `aaaammgg`, oppure 0 quando la data non è riconoscibile.
 */
data class MimitPriceDate(val formatted: String, val sortKey: Int) {
    companion object {
        val UNKNOWN = MimitPriceDate("", 0)
    }
}

/**
 * Formatta una data in italiano senza dipendere dal `Locale` del device (che renderebbe i test
 * non deterministici). Ritorna [MimitPriceDate.UNKNOWN] se la data non è riconoscibile: meglio un
 * campo vuoto che una data inventata.
 */
private fun priceDate(year: Int, month: Int, day: Int): MimitPriceDate =
    if (month in 1..12 && day in 1..31) {
        MimitPriceDate("%02d %s %d".format(day, MONTHS[month - 1], year), year * 10000 + month * 100 + day)
    } else {
        MimitPriceDate.UNKNOWN
    }

/** Converte l'`insertDate` dell'API, in forma ISO `2026-08-05T07:52:09+02:00`. */
fun parseIsoPriceDate(raw: String?): MimitPriceDate {
    val value = raw?.trim().orEmpty()
    if (value.length < 10) return MimitPriceDate.UNKNOWN
    val year = value.substring(0, 4).toIntOrNull() ?: return MimitPriceDate.UNKNOWN
    val month = value.substring(5, 7).toIntOrNull() ?: return MimitPriceDate.UNKNOWN
    val day = value.substring(8, 10).toIntOrNull() ?: return MimitPriceDate.UNKNOWN
    return priceDate(year, month, day)
}

/** Converte il `dtComu` dei CSV open data, in forma `05/08/2026 20:00:08`. */
fun parseCsvPriceDate(raw: String?): MimitPriceDate {
    val value = raw?.trim().orEmpty()
    if (value.length < 10) return MimitPriceDate.UNKNOWN
    val day = value.substring(0, 2).toIntOrNull() ?: return MimitPriceDate.UNKNOWN
    val month = value.substring(3, 5).toIntOrNull() ?: return MimitPriceDate.UNKNOWN
    val year = value.substring(6, 10).toIntOrNull() ?: return MimitPriceDate.UNKNOWN
    return priceDate(year, month, day)
}

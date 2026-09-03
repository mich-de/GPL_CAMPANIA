package com.example.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException

/** Prezzo medio del GPL in una regione, con quanti impianti lo compongono. */
data class RegionGplAverage(
    val region: String,
    val averagePrice: Double,
    val stationCount: Int
)

/**
 * Fotografia del GPL in Italia ricavata dalla pubblicazione open data del MIMIT di una certa
 * mattina. Nessun valore è stimato: media, mediana e classifica escono dai prezzi che i gestori
 * hanno comunicato al ministero.
 *
 * [skippedRows] sono gli impianti con un prezzo GPL valido che non è stato possibile attribuire a
 * nessuna regione, perché nell'anagrafica la colonna `Provincia` contiene un valore fuori formato.
 * Vengono contati e mostrati invece di essere silenziosamente assorbiti in qualche regione.
 */
data class NationalGplStats(
    val averagePrice: Double,
    val medianPrice: Double,
    val stationCount: Int,
    val skippedRows: Int,
    /** Regioni ordinate dalla più economica alla più cara. */
    val regions: List<RegionGplAverage>,
    val publishedDayKey: Int,
    val csvLastModified: String
) {
    /** Posizione della regione in classifica (1 = la più economica), 0 se assente. */
    fun rankOf(region: String): Int = regions.indexOfFirst { it.region == region } + 1

    fun averageOf(region: String): RegionGplAverage? = regions.firstOrNull { it.region == region }

    val cheapest: RegionGplAverage? get() = regions.firstOrNull()
    val priciest: RegionGplAverage? get() = regions.lastOrNull()
}

/** Esito di una lettura delle statistiche nazionali. */
sealed interface NationalStatsResult {
    data class Data(val stats: NationalGplStats) : NationalStatsResult

    /** Gli open data non hanno risposto: nessun numero inventato al loro posto. */
    data object Unavailable : NationalStatsResult
}

/**
 * Calcola le medie nazionali e regionali del GPL leggendo gli stessi due CSV del MIMIT già usati
 * come fallback per la Campania, ma tenendo questa volta l'Italia intera.
 *
 * È un'operazione **costosa e volontaria**: ~7,5 MB non comprimibili, quindi non parte mai da sola.
 * La chiama solo chi apre il pannello e chiede esplicitamente di aggiornare, e una volta al giorno
 * basta — la fonte viene ripubblicata ogni mattina intorno alle 6:45 UTC.
 */
object NationalGplStatsFetcher {

    suspend fun fetch(context: Context): NationalStatsResult = withContext(Dispatchers.IO) {
        try {
            // Nessun If-Modified-Since qui: chi apre il pannello vuole i numeri adesso, e un 304
            // lascerebbe la schermata vuota invece che aggiornata.
            val priced = MimitCsvFallback.request(MimitCsvFallback.PRICES_URL, null).use { response ->
                if (!response.isSuccessful) return@withContext NationalStatsResult.Unavailable
                with(MimitCsvFallback) {
                    response.header("Last-Modified").orEmpty() to readGplPrices(response.reader())
                }
            }
            val (lastModified, prices) = priced
            if (prices.isEmpty()) return@withContext NationalStatsResult.Unavailable

            val byRegion = MimitCsvFallback.request(MimitCsvFallback.REGISTRY_URL, null).use { response ->
                if (!response.isSuccessful) return@withContext NationalStatsResult.Unavailable
                with(MimitCsvFallback) { aggregateByRegion(response.reader(), prices) }
            }
            val stats = byRegion.toStats(
                publishedDayKey = parseHttpDayKey(lastModified),
                csvLastModified = lastModified
            ) ?: return@withContext NationalStatsResult.Unavailable
            NationalStatsResult.Data(stats)
        } catch (e: IOException) {
            NationalStatsResult.Unavailable
        } catch (e: Exception) {
            NationalStatsResult.Unavailable
        }
    }
}

/** Prezzi GPL raccolti per regione, prima di diventare medie. */
internal class RegionalPrices {
    val byRegion = LinkedHashMap<String, MutableList<Double>>()
    var skipped = 0
        private set

    fun add(provinceCode: String, price: Double) {
        val region = ItalianRegions.of(provinceCode)
        if (region == null) {
            skipped++
            return
        }
        byRegion.getOrPut(region) { mutableListOf() }.add(price)
    }

    fun toStats(publishedDayKey: Int, csvLastModified: String): NationalGplStats? {
        val all = byRegion.values.flatten()
        if (all.isEmpty()) return null
        val regions = byRegion
            .map { (region, prices) -> RegionGplAverage(region, prices.average(), prices.size) }
            .sortedWith(compareBy({ it.averagePrice }, { it.region }))
        return NationalGplStats(
            averagePrice = all.average(),
            medianPrice = median(all),
            stationCount = all.size,
            skippedRows = skipped,
            regions = regions,
            publishedDayKey = publishedDayKey,
            csvLastModified = csvLastModified
        )
    }
}

/**
 * Scorre l'anagrafica nazionale attribuendo a ogni regione i prezzi GPL già raccolti dal CSV prezzi.
 * Funzione pura sul flusso di righe: un `BufferedReader` su una stringa la rende verificabile senza
 * rete.
 */
internal fun aggregateByRegion(
    registryReader: BufferedReader,
    prices: Map<String, CsvGplPrice>
): RegionalPrices {
    val collected = RegionalPrices()
    forEachCsvRow(registryReader) { columns, header ->
        val id = columns.csvValue(header, CSV_ID_COLUMN)
        val price = prices[id] ?: return@forEachCsvRow
        collected.add(columns.csvValue(header, "Provincia"), price.price)
    }
    return collected
}

/** La mediana regge meglio della media i pochi impianti fuori scala, quindi si mostrano entrambe. */
internal fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

private val HTTP_MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private val HTTP_DATE = Regex("""(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})""")

/**
 * Giorno di pubblicazione ricavato dall'header `Last-Modified`
 * (`Mon, 10 Aug 2026 06:45:06 GMT`) in forma `aaaammgg`, 0 se non riconoscibile.
 *
 * Scritto a mano invece che con `SimpleDateFormat` perché i nomi dei mesi negli header HTTP sono
 * sempre inglesi mentre il device è italiano: un parser dipendente dal `Locale` fallirebbe proprio
 * sul dispositivo reale.
 */
internal fun parseHttpDayKey(lastModified: String): Int {
    val match = HTTP_DATE.find(lastModified) ?: return 0
    val day = match.groupValues[1].toIntOrNull() ?: return 0
    val month = HTTP_MONTHS.indexOfFirst { it.equals(match.groupValues[2], ignoreCase = true) } + 1
    val year = match.groupValues[3].toIntOrNull() ?: return 0
    if (month == 0 || day !in 1..31) return 0
    return year * 10_000 + month * 100 + day
}

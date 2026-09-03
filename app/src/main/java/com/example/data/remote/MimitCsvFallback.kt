package com.example.data.remote

import android.content.Context
import com.example.data.local.BackendPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Esito della lettura dei CSV open data. */
sealed interface CsvFallbackResult {
    data class Data(val stations: List<RemoteGplStation>) : CsvFallbackResult

    /** Il MIMIT ha risposto 304: i prezzi non sono cambiati dall'ultima lettura riuscita. */
    data object Unchanged : CsvFallbackResult

    /** Non è stato possibile leggere i CSV. Nessun dato, e nessuna invenzione al loro posto. */
    data object Unavailable : CsvFallbackResult
}

internal data class CsvGplPrice(val price: Double, val isSelf: Boolean, val dtComu: String)

/**
 * Fallback sulla pubblicazione open data del MIMIT, usata solo quando l'API dell'Osservaprezzi non
 * risponde. Sono gli stessi dati, in forma documentata e ufficiale, ma molto più pesanti: i due CSV
 * coprono l'Italia intera (~7,5 MB complessivi) e non supportano gzip.
 *
 * Per questo la lettura è **in streaming** e in quest'ordine:
 *  1. `prezzo_alle_8.csv`, trattenendo in memoria solo le 4.712 righe GPL di tutta Italia;
 *  2. `anagrafica_impianti_attivi.csv`, trattenendo solo gli impianti campani già presenti al punto 1.
 *
 * Il `Last-Modified` del CSV prezzi viene rispedito come `If-Modified-Since`: un **304 non è un
 * errore**, significa "niente di nuovo" e permette di evitare del tutto il download dell'anagrafica.
 */
object MimitCsvFallback {

    internal const val PRICES_URL = "https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv"
    internal const val REGISTRY_URL =
        "https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv"
    private const val USER_AGENT = "GPLCampaniaApp/1.0 (+https://github.com/mich-de/GPL_CAMPANIA)"

    private const val GPL_LABEL = "GPL"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.MINUTES)
        .build()

    /**
     * [allowConditional] va passato `false` quando in Room non c'è nulla da preservare: in quel caso
     * un 304 lascerebbe l'utente con una lista vuota, quindi si scarica comunque tutto.
     */
    suspend fun fetch(context: Context, allowConditional: Boolean): CsvFallbackResult =
        withContext(Dispatchers.IO) {
            try {
                val knownLastModified =
                    BackendPreferences.getCsvPricesLastModified(context).takeIf { allowConditional }

                val priced = request(PRICES_URL, knownLastModified).use { response ->
                    when {
                        response.code == 304 -> return@withContext CsvFallbackResult.Unchanged
                        !response.isSuccessful -> null
                        else -> response.header("Last-Modified") to readGplPrices(response.reader())
                    }
                } ?: return@withContext CsvFallbackResult.Unavailable

                val (lastModified, prices) = priced
                if (prices.isEmpty()) return@withContext CsvFallbackResult.Unavailable

                val stations = request(REGISTRY_URL, null).use { response ->
                    if (response.isSuccessful) readCampaniaStations(response.reader(), prices) else null
                } ?: return@withContext CsvFallbackResult.Unavailable
                if (stations.isEmpty()) return@withContext CsvFallbackResult.Unavailable

                // Salvato solo a lettura completata: un download interrotto non deve far credere
                // al prossimo avvio che i dati siano già stati acquisiti.
                lastModified?.let { BackendPreferences.setCsvPricesLastModified(context, it) }
                CsvFallbackResult.Data(stations)
            } catch (e: IOException) {
                CsvFallbackResult.Unavailable
            } catch (e: Exception) {
                CsvFallbackResult.Unavailable
            }
        }

    /** Unica richiesta HTTP verso gli open data: la riusa anche chi calcola le medie nazionali. */
    internal fun request(url: String, ifModifiedSince: String?): Response {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/csv")
        ifModifiedSince?.let { builder.header("If-Modified-Since", it) }
        return httpClient.newCall(builder.build()).execute()
    }

    internal fun Response.reader(): BufferedReader =
        BufferedReader(InputStreamReader(body!!.byteStream(), Charsets.UTF_8), 64 * 1024)

    /**
     * `idImpianto|descCarburante|prezzo|isSelf|dtComu`. Un impianto può pubblicare il GPL sia self
     * sia servito: si tiene il più conveniente, coerentemente con l'API.
     */
    internal fun readGplPrices(reader: BufferedReader): Map<String, CsvGplPrice> {
        val prices = HashMap<String, CsvGplPrice>(6000)
        forEachCsvRow(reader) { columns, header ->
            if (columns.csvValue(header, "descCarburante") != GPL_LABEL) return@forEachCsvRow
            val id = columns.csvValue(header, CSV_ID_COLUMN)
            if (id.isEmpty()) return@forEachCsvRow
            val price = columns.csvValue(header, "prezzo").toDoubleOrNull() ?: return@forEachCsvRow
            if (price <= 0.0) return@forEachCsvRow

            // Stesso criterio dell'API: il prezzo più basso e, a parità, la modalità self.
            val isSelf = columns.csvValue(header, "isSelf") == "1"
            val current = prices[id]
            if (current == null || price < current.price || (price == current.price && isSelf && !current.isSelf)) {
                prices[id] = CsvGplPrice(
                    price = price,
                    isSelf = isSelf,
                    dtComu = columns.csvValue(header, "dtComu")
                )
            }
        }
        return prices
    }

    /**
     * `idImpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine`.
     * Qui comune, provincia e coordinate sono già colonne separate: nessun indirizzo da scomporre.
     */
    internal fun readCampaniaStations(
        reader: BufferedReader,
        prices: Map<String, CsvGplPrice>
    ): List<RemoteGplStation> {
        val stations = mutableListOf<RemoteGplStation>()
        forEachCsvRow(reader) { columns, header ->
            val provincia = columns.csvValue(header, "Provincia").uppercase()
            if (provincia !in CAMPANIA_PROVINCES) return@forEachCsvRow
            val id = columns.csvValue(header, CSV_ID_COLUMN)
            val price = prices[id] ?: return@forEachCsvRow
            val communicated = parseCsvPriceDate(price.dtComu)

            stations.add(
                RemoteGplStation(
                    impiantoId = id.toLongOrNull() ?: return@forEachCsvRow,
                    nome = columns.csvValue(header, "Nome Impianto"),
                    brand = columns.csvValue(header, "Bandiera"),
                    via = columns.csvValue(header, "Indirizzo"),
                    comune = columns.csvValue(header, "Comune"),
                    provincia = provincia,
                    latitude = columns.csvCoordinate(header, "Latitudine"),
                    longitude = columns.csvCoordinate(header, "Longitudine"),
                    gplPrice = price.price,
                    gplIsSelf = price.isSelf,
                    priceDate = communicated.formatted,
                    priceDay = communicated.sortKey
                )
            )
        }
        return stations
    }
}

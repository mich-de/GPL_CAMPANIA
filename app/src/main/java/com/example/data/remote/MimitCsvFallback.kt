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

    private const val PRICES_URL = "https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv"
    private const val REGISTRY_URL =
        "https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv"
    private const val USER_AGENT = "GPLCampaniaApp/1.0 (+https://github.com/mich-de/GPL_CAMPANIA)"

    private const val DELIMITER = '|'
    private const val ID_COLUMN = "idImpianto"
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

    private fun request(url: String, ifModifiedSince: String?): Response {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/csv")
        ifModifiedSince?.let { builder.header("If-Modified-Since", it) }
        return httpClient.newCall(builder.build()).execute()
    }

    private fun Response.reader(): BufferedReader =
        BufferedReader(InputStreamReader(body!!.byteStream(), Charsets.UTF_8), 64 * 1024)

    /**
     * `idImpianto|descCarburante|prezzo|isSelf|dtComu`. Un impianto può pubblicare il GPL sia self
     * sia servito: si tiene il più conveniente, coerentemente con l'API.
     */
    internal fun readGplPrices(reader: BufferedReader): Map<String, CsvGplPrice> {
        val prices = HashMap<String, CsvGplPrice>(6000)
        forEachRow(reader) { columns, header ->
            if (columns.value(header, "descCarburante") != GPL_LABEL) return@forEachRow
            val id = columns.value(header, ID_COLUMN)
            if (id.isEmpty()) return@forEachRow
            val price = columns.value(header, "prezzo").toDoubleOrNull() ?: return@forEachRow
            if (price <= 0.0) return@forEachRow

            // Stesso criterio dell'API: il prezzo più basso e, a parità, la modalità self.
            val isSelf = columns.value(header, "isSelf") == "1"
            val current = prices[id]
            if (current == null || price < current.price || (price == current.price && isSelf && !current.isSelf)) {
                prices[id] = CsvGplPrice(
                    price = price,
                    isSelf = isSelf,
                    dtComu = columns.value(header, "dtComu")
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
        forEachRow(reader) { columns, header ->
            val provincia = columns.value(header, "Provincia").uppercase()
            if (provincia !in CAMPANIA_PROVINCES) return@forEachRow
            val id = columns.value(header, ID_COLUMN)
            val price = prices[id] ?: return@forEachRow
            val communicated = parseCsvPriceDate(price.dtComu)

            stations.add(
                RemoteGplStation(
                    impiantoId = id.toLongOrNull() ?: return@forEachRow,
                    nome = columns.value(header, "Nome Impianto"),
                    brand = columns.value(header, "Bandiera"),
                    via = columns.value(header, "Indirizzo"),
                    comune = columns.value(header, "Comune"),
                    provincia = provincia,
                    latitude = columns.coordinate(header, "Latitudine"),
                    longitude = columns.coordinate(header, "Longitudine"),
                    gplPrice = price.price,
                    gplIsSelf = price.isSelf,
                    priceDate = communicated.formatted,
                    priceDay = communicated.sortKey
                )
            )
        }
        return stations
    }

    /**
     * Scorre il CSV riga per riga senza mai materializzarlo: la prima riga (`Estrazione del …`) va
     * scartata, la seconda è l'intestazione da cui si ricavano le posizioni delle colonne.
     */
    private inline fun forEachRow(
        reader: BufferedReader,
        onRow: (columns: List<String>, header: Map<String, Int>) -> Unit
    ) {
        var header: Map<String, Int>? = null
        var line = reader.readLine()
        while (line != null) {
            val current = header
            if (current == null) {
                if (line.startsWith(ID_COLUMN)) {
                    header = line.split(DELIMITER)
                        .withIndex()
                        .associate { (index, name) -> name.trim() to index }
                }
            } else if (line.isNotBlank()) {
                onRow(line.split(DELIMITER), current)
            }
            line = reader.readLine()
        }
    }

    private fun List<String>.value(header: Map<String, Int>, name: String): String =
        header[name]?.let { getOrNull(it) }?.trim().orEmpty()

    /** Coordinata reale o `null`: uno 0.0 nell'anagrafica significa "non rilevata", non "equatore". */
    private fun List<String>.coordinate(header: Map<String, Int>, name: String): Double? =
        value(header, name).toDoubleOrNull()?.takeIf { !it.isNaN() && it != 0.0 }
}

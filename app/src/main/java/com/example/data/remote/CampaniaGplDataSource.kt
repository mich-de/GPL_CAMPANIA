package com.example.data.remote

import android.content.Context

/**
 * Sollevata quando nessuna fonte reale è raggiungibile da questo device. Non innesca mai un
 * fallback a dati finti: l'app mostra l'errore e conserva l'ultimo dato reale già in Room.
 */
class DataFetchException(message: String) : Exception(message)

/** Esito di un tentativo di aggiornamento dei distributori GPL della Campania. */
sealed interface CampaniaGplData {
    data class Stations(val stations: List<RemoteGplStation>, val source: String) : CampaniaGplData

    /** I dati ufficiali non sono cambiati: quelli già in Room sono aggiornati. */
    data object Unchanged : CampaniaGplData
}

/**
 * Unica porta d'ingresso ai dati reali: prova l'API dell'Osservaprezzi e, solo se non risponde,
 * ripiega sui CSV open data dello stesso ministero.
 *
 * L'ordine non è arbitrario: l'API costa ~172 KB compressi e restituisce già i soli impianti
 * campani, i CSV costano ~7,5 MB non comprimibili e coprono l'Italia intera. Il fallback esiste
 * perché `ospzApi` è il backend del sito pubblico, non un servizio con termini d'uso pubblicati,
 * mentre i CSV sono una pubblicazione ufficiale e documentata.
 */
object CampaniaGplDataSource {

    const val SOURCE_API = "API Osservaprezzi MIMIT"
    const val SOURCE_CSV = "open data MIMIT (CSV)"

    /**
     * [hasCachedData] dice se in Room c'è già un dato reale da preservare. Serve al fallback CSV per
     * decidere se usare `If-Modified-Since`: con il database vuoto un 304 lascerebbe l'utente senza
     * niente, quindi in quel caso si scarica comunque tutto.
     */
    suspend fun fetch(context: Context, hasCachedData: Boolean): CampaniaGplData {
        // Entrambe le fonti passano da `mergeDuplicatePlants`: l'anagrafica è la stessa, e con essa
        // le poche pompe iscritte due volte.
        val fromApi = OsservaprezziApiClient.fetchCampaniaGplStations()
        if (fromApi.isNotEmpty()) {
            return CampaniaGplData.Stations(fromApi.mergeDuplicatePlants(), SOURCE_API)
        }

        return when (val fromCsv = MimitCsvFallback.fetch(context, allowConditional = hasCachedData)) {
            is CsvFallbackResult.Data ->
                CampaniaGplData.Stations(fromCsv.stations.mergeDuplicatePlants(), SOURCE_CSV)
            CsvFallbackResult.Unchanged -> CampaniaGplData.Unchanged
            CsvFallbackResult.Unavailable -> throw DataFetchException(
                "Dati ufficiali non raggiungibili: né l'API dell'Osservaprezzi carburanti né gli " +
                    "open data del MIMIT hanno risposto. Controlla la connessione e riprova."
            )
        }
    }
}

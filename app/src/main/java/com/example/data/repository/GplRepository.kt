package com.example.data.repository

import android.content.Context
import com.example.data.geocoding.GeocodeDao
import com.example.data.geocoding.GeocodingEngine
import com.example.data.local.BackendPreferences
import com.example.data.local.GplDao
import com.example.data.local.MonitoringReport
import com.example.data.local.RefreshDiagnostics
import com.example.data.model.GplStation
import com.example.data.model.UserPriceReport
import com.example.data.remote.CampaniaGplData
import com.example.data.remote.CampaniaGplDataSource
import com.example.data.remote.DataFetchException
import com.example.data.remote.RemoteGplStation
import com.example.data.remote.priceFreshness
import com.example.data.remote.todayDateKey
import com.example.data.util.ItalianCapUtils
import com.example.data.util.distanceMeters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

private const val CACHE_TTL_MILLIS = 15 * 60 * 1000L // 15 minuti, come il backend Python

/** Soglia entro cui due righe con coordinate reali sono considerate lo stesso impianto: stretta
 * abbastanza da non confondere distributori diversi, larga abbastanza da assorbire lo scarto tra
 * una posizione geocodificata e quella ufficiale dell'impianto. */
private const val FAVORITE_MATCH_METERS = 150.0

private val MULTIPLE_SPACES = Regex("""\s+""")

class GplRepository(
    private val context: Context,
    private val dao: GplDao,
    private val geocodeDao: GeocodeDao
) {

    private val geocodingEngine = GeocodingEngine(geocodeDao)

    /** Un solo refresh alla volta: un pull-to-refresh doppio o un auto-refresh che si sovrappone a
     * uno manuale non devono poter interlacciare delete e insert sulle stesse righe. */
    private val refreshMutex = Mutex()

    val allStations: Flow<List<GplStation>> = dao.getAllStations()
    val favoriteStations: Flow<List<GplStation>> = dao.getFavoriteStations()

    /**
     * Aggiorna i distributori dalla fonte ufficiale (Osservaprezzi carburanti del MIMIT, con
     * fallback sugli open data dello stesso ministero). Le coordinate arrivano già dalla fonte,
     * quindi al termine di un refresh riuscito ogni distributore è posizionabile sulla mappa:
     * il geocoding non fa più parte di questo percorso.
     *
     * Entro il TTL di 15 minuti e senza `forceRefresh` non fa alcuna chiamata di rete. Se nessuna
     * fonte risponde (es. device offline) l'eccezione si propaga senza toccare Room: l'ultimo dato
     * reale ottenuto resta la cache offline. Preferiti e stazioni aggiunte manualmente dall'utente
     * non vengono mai cancellati da un refresh.
     */
    suspend fun refreshStations(forceRefresh: Boolean = false): Int = refreshMutex.withLock {
        val lastRefresh = BackendPreferences.getLastRefreshTimestamp(context)
        val now = System.currentTimeMillis()
        val cachedCount = dao.countBackendSourcedStations()
        if (!forceRefresh && lastRefresh != null && (now - lastRefresh) < CACHE_TTL_MILLIS) {
            return@withLock cachedCount
        }

        val startedAt = System.currentTimeMillis()
        val fetched = try {
            CampaniaGplDataSource.fetch(context, hasCachedData = cachedCount > 0)
        } catch (e: Exception) {
            recordFailure(startedAt, e.message.orEmpty())
            throw e
        }

        if (fetched is CampaniaGplData.Unchanged) {
            // Un 304 non è un aggiornamento e non è un errore: si annota l'esito senza toccare le
            // misure dell'ultimo scarico riuscito, che descrivono ancora i dati in Room.
            saveDiagnostics(
                BackendPreferences.getRefreshDiagnostics(context).copy(
                    attemptedAt = startedAt,
                    outcome = RefreshDiagnostics.Outcome.UNCHANGED,
                    source = CampaniaGplDataSource.SOURCE_CSV,
                    durationMillis = System.currentTimeMillis() - startedAt,
                    message = ""
                )
            )
            return@withLock cachedCount
        }

        val result = fetched as CampaniaGplData.Stations
        val stations = result.stations.mapNotNull { it.toGplStation() }
        if (stations.isEmpty()) {
            val reason = "La fonte ufficiale ha risposto ma non contiene distributori GPL in Campania. " +
                "L'ultimo dato reale resta disponibile."
            recordFailure(startedAt, reason)
            throw DataFetchException(reason)
        }

        val withRestoredFavorites = restoreFavorites(stations)
        // Delete + insert in un'unica transazione Room: un kill di processo a metà non lascia mai
        // la lista stazioni vuota.
        dao.replaceBackendSourcedStations(withRestoredFavorites)

        val freshness = result.stations.priceFreshness(todayDateKey())
        saveDiagnostics(
            RefreshDiagnostics(
                attemptedAt = startedAt,
                outcome = RefreshDiagnostics.Outcome.SUCCESS,
                source = result.source,
                durationMillis = System.currentTimeMillis() - startedAt,
                message = "",
                stationsWritten = withRestoredFavorites.size,
                duplicatesMerged = (result.rawCount - result.stations.size).coerceAtLeast(0),
                withoutCoordinates = withRestoredFavorites.count { it.latitude == null || it.longitude == null },
                pricesToday = freshness.today,
                pricesWithinWeek = freshness.withinWeek,
                pricesOlderThanMonth = freshness.olderThanMonth,
                pricesWithoutDate = freshness.withoutDate
            )
        )
        withRestoredFavorites.size
    }

    /** Il tentativo è fallito: si registra il motivo reale della fonte, lasciando intatte le misure
     * dell'ultimo scarico riuscito — sono ancora quelle che descrivono i dati mostrati. */
    private fun recordFailure(startedAt: Long, reason: String) {
        saveDiagnostics(
            BackendPreferences.getRefreshDiagnostics(context).copy(
                attemptedAt = startedAt,
                outcome = RefreshDiagnostics.Outcome.FAILED,
                source = "",
                durationMillis = System.currentTimeMillis() - startedAt,
                message = reason
            )
        )
    }

    private fun saveDiagnostics(diagnostics: RefreshDiagnostics) {
        BackendPreferences.setRefreshDiagnostics(context, diagnostics)
    }

    /**
     * Stato corrente dell'app per il pannello di diagnostica: la diagnostica persistita più i
     * conteggi letti adesso da Room. Non fa nessuna chiamata di rete.
     */
    suspend fun buildMonitoringReport(): MonitoringReport {
        val total = dao.countAllStations()
        val official = dao.countBackendSourcedStations()
        return MonitoringReport(
            diagnostics = BackendPreferences.getRefreshDiagnostics(context),
            lastRefreshTimestamp = BackendPreferences.getLastRefreshTimestamp(context),
            cacheTtlMillis = CACHE_TTL_MILLIS,
            totalStations = total,
            officialStations = official,
            userStations = (total - official).coerceAtLeast(0),
            favorites = dao.countFavoriteStations(),
            priceReports = dao.countPriceReports(),
            withoutCoordinates = dao.countStationsWithoutCoordinates(),
            csvLastModified = BackendPreferences.getCsvPricesLastModified(context),
            generatedAt = System.currentTimeMillis()
        )
    }

    /** Fa scadere la cache di 15 minuti senza cancellare niente: i dati reali restano al loro posto. */
    fun invalidateCacheTtl() {
        BackendPreferences.clearLastRefreshTimestamp(context)
    }

    /**
     * Ritrova i preferiti nella nuova lista prima che le righe vecchie vengano cancellate.
     * Normalmente basta l'id, che la fonte ufficiale mantiene stabile; le altre due corrispondenze
     * servono a non perdere i preferiti salvati quando gli id avevano un'altra forma (ad esempio
     * dopo il passaggio dallo scraping alla fonte ufficiale, dove cambiano tutti).
     */
    internal suspend fun restoreFavorites(fresh: List<GplStation>): List<GplStation> {
        val previous = dao.getFavoriteBackendStations()
        if (previous.isEmpty()) return fresh

        val byId = fresh.associateBy { it.id }
        val byAddress = fresh.associateBy { addressKey(it.city, it.address) }

        val favoriteIds = mutableSetOf<String>()
        for (old in previous) {
            val match = byId[old.id]
                ?: byAddress[addressKey(old.city, old.address)]
                ?: nearestWithin(fresh, old.latitude, old.longitude, FAVORITE_MATCH_METERS)
                ?: continue
            favoriteIds += match.id
            if (match.id != old.id) dao.remapPriceReports(old.id, match.id)
        }
        return fresh.map { if (it.id in favoriteIds) it.copy(isFavorite = true) else it }
    }

    /** Comune + via ridotti a sole lettere e cifre: regge le differenze di punteggiatura,
     * abbreviazioni con o senza punto e spaziatura tra una fonte e l'altra. */
    private fun addressKey(city: String, address: String): String =
        (city + "|" + address).lowercase(Locale.ROOT).filter { it.isLetterOrDigit() || it == '|' }

    /** Stazione più vicina entro [maxMeters], o null. Richiede coordinate reali su entrambi i lati:
     * senza posizione non si tenta nessuna corrispondenza. */
    private fun nearestWithin(
        candidates: List<GplStation>,
        lat: Double?,
        lng: Double?,
        maxMeters: Double
    ): GplStation? {
        if (lat == null || lng == null) return null
        var best: GplStation? = null
        var bestDistance = maxMeters
        for (candidate in candidates) {
            val cLat = candidate.latitude ?: continue
            val cLng = candidate.longitude ?: continue
            val distance = distanceMeters(lat, lng, cLat, cLng)
            if (distance <= bestDistance) {
                best = candidate
                bestDistance = distance
            }
        }
        return best
    }

    internal fun RemoteGplStation.toGplStation(): GplStation? {
        if (gplPrice <= 0.0) return null
        val cityFormatted = titleCase(comune)
        val displayName = nome.ifBlank { brand }.trim()

        return GplStation(
            // L'id è quello nazionale dell'impianto: stabile nel tempo e tra le due fonti, così i
            // preferiti sopravvivono ai refresh. Il prefisso "gpl_" resta indispensabile, perché
            // è ciò che distingue le righe da fonte ufficiale da quelle aggiunte dall'utente.
            id = "gpl_mimit_$impiantoId",
            name = if (displayName.isNotBlank() && cityFormatted.isNotBlank()) "$displayName ($cityFormatted)"
            else displayName.ifBlank { "Distributore GPL $cityFormatted" },
            brand = brand.ifBlank { "Pompe Bianche" },
            address = via.ifBlank { "Distributore GPL $cityFormatted" },
            city = cityFormatted,
            province = provincia,
            latitude = latitude,
            longitude = longitude,
            gplPrice = gplPrice,
            priceLastUpdated = priceDate,
            // Orari/apertura: l'Osservaprezzi non li pubblica — restano null (nessun default inventato).
            services = if (gplIsSelf) "GPL,Self" else "GPL,Servito",
            phone = "",
            notes = ""
        )
    }

    /** La fonte ufficiale scrive i comuni in maiuscolo: qui tornano leggibili ("SANT'ANASTASIA"
     * -> "Sant'Anastasia"), senza alterare il dato oltre la sola presentazione. */
    private fun titleCase(value: String): String {
        val normalized = value.trim().replace(MULTIPLE_SPACES, " ").lowercase(Locale.ITALIAN)
        val result = StringBuilder(normalized.length)
        var startOfWord = true
        for (char in normalized) {
            result.append(if (startOfWord) char.titlecaseChar() else char)
            // Anche l'apostrofo apre una parola: in italiano "SANT'ANASTASIA" si scrive "Sant'Anastasia".
            startOfWord = char == ' ' || char == '\'' || char == '-'
        }
        return result.toString()
    }

    suspend fun seedInitialDataIfEmpty() {
        refreshStations()
    }

    /** Id stabile per una stazione importata da un file POI (myLPG.eu, Ecomotori): derivato da
     * coordinate e nome, resta identico se lo stesso file viene importato più volte. Un indice
     * locale che riparte da 0 a ogni import, usato prima, causava sovrascritture silenziose tra
     * stazioni non correlate tramite OnConflictStrategy.REPLACE. */
    private fun stablePoiId(prefix: String, lat: Double, lng: Double, name: String): String {
        val key = "%.5f_%.5f_%s".format(Locale.ROOT, lat, lng, name.trim().lowercase(Locale.ROOT))
        return "${prefix}_${key.hashCode().toUInt().toString(16)}"
    }

    suspend fun importMyLpgPoiFormat(poiContent: String, isKmlOrXml: Boolean = false) {
        val newStations = mutableListOf<GplStation>()
        val seenCoordinates = mutableSetOf<String>()

        if (isKmlOrXml || poiContent.contains("<kml") || poiContent.contains("<gpx")) {
            // Parse KML / GPX XML string from myLPG.eu
            val placemarkRegex = Regex("""<Placemark>[\s\S]*?</Placemark>""", RegexOption.IGNORE_CASE)
            val coordRegex = Regex("""<coordinates>([\d\.\,\s\-]+)</coordinates>""", RegexOption.IGNORE_CASE)
            val nameRegex = Regex("""<name>([\s\S]*?)</name>""", RegexOption.IGNORE_CASE)
            val descRegex = Regex("""<description>([\s\S]*?)</description>""", RegexOption.IGNORE_CASE)

            for (match in placemarkRegex.findAll(poiContent)) {
                val block = match.value
                val coordMatch = coordRegex.find(block)
                val nameMatch = nameRegex.find(block)
                val descMatch = descRegex.find(block)

                if (coordMatch != null) {
                    val coordsStr = coordMatch.groupValues[1].trim()
                    val parts = coordsStr.split(",")
                    if (parts.size >= 2) {
                        try {
                            val lng = parts[0].trim().toDouble()
                            val lat = parts[1].trim().toDouble()
                            
                            // Deduplicate duplicate Placemarks in KML
                            val coordKey = "%.5f_%.5f".format(lat, lng)
                            if (seenCoordinates.contains(coordKey)) continue
                            seenCoordinates.add(coordKey)

                            val rawName = nameMatch?.groupValues?.get(1)?.trim()?.replace("<![CDATA[", "")?.replace("]]>", "") ?: "Distributore GPL myLPG.eu"
                            val desc = descMatch?.groupValues?.get(1)?.trim() ?: ""

                            val parsedAddress = ItalianCapUtils.parseDescription(desc)
                            // defaultPrice = 0.0: se il testo non contiene un prezzo reale, la
                            // stazione resta un POI valido senza prezzo, non un prezzo inventato.
                            val priceInfo = ItalianCapUtils.extractPriceAndDate(desc + " " + rawName, defaultPrice = 0.0)

                            val brand = when {
                                rawName.contains("Energas", true) -> "Energas"
                                rawName.contains("Eni", true) || rawName.contains("Agip", true) -> "Eni"
                                rawName.contains("IP", true) -> "IP"
                                rawName.contains("Q8", true) -> "Q8"
                                rawName.contains("Beyfin", true) -> "Beyfin"
                                rawName.contains("Tamoil", true) -> "Tamoil"
                                rawName.contains("Esso", true) -> "Esso"
                                else -> "Pompe Bianche"
                            }

                            newStations.add(
                                GplStation(
                                    id = stablePoiId("mylpg", lat, lng, rawName),
                                    name = if (rawName.length <= 8) "$rawName ${parsedAddress.city}" else rawName,
                                    brand = brand,
                                    address = parsedAddress.fullFormattedAddress,
                                    city = parsedAddress.city,
                                    province = parsedAddress.province,
                                    latitude = lat,
                                    longitude = lng,
                                    gplPrice = priceInfo.price,
                                    priceLastUpdated = if (priceInfo.price > 0.0) priceInfo.lastUpdated else "",
                                    // Orari/apertura: non presenti nel formato POI myLPG.eu — restano null.
                                    services = "GPL,myLPG.eu,Servito",
                                    isFavorite = false,
                                    phone = "",
                                    notes = if (priceInfo.price > 0.0)
                                        "Importato da myLPG.eu Italia - Prezzo aggiornato (${priceInfo.price} €/L)"
                                    else
                                        "Importato da myLPG.eu Italia - prezzo non disponibile nella fonte"
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
        } else {
            // Parse CSV / TomTom format from myLPG.eu
            val lines = poiContent.lines()
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("Longitude") || line.startsWith("lon")) continue
                val parts = line.split(Regex(""";|,"""))
                if (parts.size >= 3) {
                    try {
                        val lng = parts[0].trim().replace("\"", "").toDouble()
                        val lat = parts[1].trim().replace("\"", "").toDouble()

                        val coordKey = "%.5f_%.5f".format(lat, lng)
                        if (seenCoordinates.contains(coordKey)) continue
                        seenCoordinates.add(coordKey)

                        val rawName = parts[2].trim().replace("\"", "")
                        val rawAddress = if (parts.size > 3) parts[3].trim().replace("\"", "") else "myLPG.eu POI"
                        val parsedAddress = ItalianCapUtils.parseDescription(rawAddress)
                        // defaultPrice = 0.0: niente prezzo inventato quando la riga non ne contiene uno reale.
                        // Si cerca solo nell'indirizzo, non nell'intera riga: includerla faceva leggere
                        // un frammento di coordinata (es. "40.6358") come se fosse un prezzo vero.
                        val priceInfo = ItalianCapUtils.extractPriceAndDate(rawAddress, defaultPrice = 0.0)

                        val brand = when {
                            rawName.contains("Energas", true) -> "Energas"
                            rawName.contains("Eni", true) || rawName.contains("Agip", true) -> "Eni"
                            rawName.contains("IP", true) -> "IP"
                            rawName.contains("Q8", true) -> "Q8"
                            rawName.contains("Beyfin", true) -> "Beyfin"
                            rawName.contains("Tamoil", true) -> "Tamoil"
                            rawName.contains("Esso", true) -> "Esso"
                            else -> "Pompe Bianche"
                        }

                        newStations.add(
                            GplStation(
                                id = stablePoiId("mylpg_csv", lat, lng, rawName),
                                name = if (rawName.isBlank()) "Distributore GPL myLPG.eu" else if (rawName.length <= 8) "$rawName ${parsedAddress.city}" else rawName,
                                brand = brand,
                                address = parsedAddress.fullFormattedAddress,
                                city = parsedAddress.city,
                                province = parsedAddress.province,
                                latitude = lat,
                                longitude = lng,
                                gplPrice = priceInfo.price,
                                priceLastUpdated = if (priceInfo.price > 0.0) priceInfo.lastUpdated else "",
                                // Orari/apertura: non presenti nel formato CSV myLPG.eu — restano null.
                                services = "GPL,myLPG.eu,Servito",
                                isFavorite = false,
                                phone = "",
                                notes = if (priceInfo.price > 0.0)
                                    "Importato da myLPG.eu Italia - Prezzo aggiornato (${priceInfo.price} €/L)"
                                else
                                    "Importato da myLPG.eu Italia - prezzo non disponibile nella fonte"
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
        }

        if (newStations.isNotEmpty()) {
            dao.insertStations(newStations)
        }
    }

    suspend fun importEcomotoriGplCsv(csvContent: String) {
        val lines = csvContent.lines()
        val newStations = mutableListOf<GplStation>()
        for (line in lines) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("Longitude")) continue
            val parts = line.split(",")
            if (parts.size >= 3) {
                try {
                    val lng = parts[0].trim().toDouble()
                    val lat = parts[1].trim().toDouble()
                    val rawName = parts[2].trim().replace("\"", "")

                    val brand = when {
                        rawName.contains("Eni", true) || rawName.contains("Agip", true) -> "Eni"
                        rawName.contains("IP", true) -> "IP"
                        rawName.contains("Q8", true) -> "Q8"
                        rawName.contains("Beyfin", true) -> "Beyfin"
                        rawName.contains("Tamoil", true) -> "Tamoil"
                        rawName.contains("Esso", true) -> "Esso"
                        rawName.contains("Econogas", true) -> "Econogas"
                        else -> "Pompe Bianche"
                    }

                    newStations.add(
                        GplStation(
                            id = stablePoiId("ecomotori", lat, lng, rawName),
                            name = rawName.ifBlank { "Distributore GPL Ecomotori" },
                            brand = brand,
                            address = "Coordinate Ecomotori GPL",
                            city = "Campania / Penisola",
                            province = "NA",
                            latitude = lat,
                            longitude = lng,
                            // Il CSV EcomotoriGPLIta contiene solo coordinate e nome, mai un prezzo
                            // reale: 0.0 è la sentinella "prezzo sconosciuto" già usata altrove
                            // (vedi toGplStation), non un valore inventato spacciato per reale.
                            gplPrice = 0.0,
                            priceLastUpdated = "",
                            // Orari/apertura: non presenti nel CSV EcomotoriGPLIta — restano null.
                            services = "GPL,Ecomotori POI,Servito",
                            isFavorite = false,
                            phone = "",
                            notes = "Importato da EcomotoriGPLIta.csv POI Database - prezzo non disponibile nella fonte"
                        )
                    )
                } catch (_: Exception) {}
            }
        }
        if (newStations.isNotEmpty()) {
            dao.insertStations(newStations)
        }
    }

    suspend fun toggleFavorite(stationId: String, currentStatus: Boolean) {
        dao.updateFavoriteStatus(stationId, !currentStatus)
    }

    /**
     * Segnalazione di prezzo reale (inserita da chi usa il device), salvata direttamente in Room.
     * Non più condivisa con altri device (il backend LAN condiviso non esiste più).
     */
    suspend fun updateGplPrice(station: GplStation, newPrice: Double, reporterName: String, notes: String) {
        val dateStr = "Oggi, " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.ITALIAN).format(java.util.Date())
        dao.updateGplPrice(station.id, newPrice, dateStr)
        dao.insertPriceReport(
            UserPriceReport(
                stationId = station.id,
                reportedGplPrice = newPrice,
                reporterName = if (reporterName.isNotBlank()) reporterName else "Utente Anagrafica",
                notes = notes
            )
        )
    }

    /**
     * Aggiunge una stazione reale segnalata da chi usa il device: geocoding reale via Nominatim
     * (mai coordinate inventate — restano null se l'indirizzo non è geocodificabile), poi salvataggio
     * diretto in Room. Id con prefisso "user_" per non essere mai cancellata da un refresh
     * (che cancella solo le stazioni con prefisso "gpl_").
     */
    suspend fun addStation(
        provinciaSlug: String,
        name: String,
        brand: String,
        address: String,
        city: String,
        gplPrice: Double,
        openHours: String,
        phone: String,
        services: String
    ) {
        val geocode = geocodingEngine.geocodeAddress(address, city, provinciaSlug)
        val dateStr = "Oggi, " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.ITALIAN).format(java.util.Date())
        val station = GplStation(
            id = "user_${System.currentTimeMillis()}",
            name = name.ifBlank { "Distributore GPL" },
            brand = brand.ifBlank { "Pompe Bianche" },
            address = address.ifBlank { "Distributore GPL $city" },
            city = city,
            province = provinceSlugToCode(provinciaSlug),
            latitude = geocode.latitude,
            longitude = geocode.longitude,
            gplPrice = gplPrice,
            priceLastUpdated = dateStr,
            openHoursWeekday = openHours.ifBlank { null },
            isOpening24h = openHours.contains("24", ignoreCase = true),
            services = services.ifBlank { "GPL,Servito" },
            phone = phone,
            notes = ""
        )
        dao.insertStation(station)
    }

    private fun provinceSlugToCode(slug: String): String = when (slug.lowercase(Locale.ROOT)) {
        "avellino" -> "AV"
        "benevento" -> "BN"
        "caserta" -> "CE"
        "salerno" -> "SA"
        else -> "NA"
    }

    fun getPriceReportsForStation(stationId: String): Flow<List<UserPriceReport>> {
        return dao.getPriceReportsForStation(stationId)
    }
}

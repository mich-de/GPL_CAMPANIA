package com.example.data.geocoding

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class GeocodeResult(val latitude: Double?, val longitude: Double?, val precision: String?) {
    companion object {
        val NOT_FOUND = GeocodeResult(null, null, null)
    }
}

/**
 * Porting 1:1 della cascata di geocoding di `geocode_address` in `app/geocoding.py`:
 * 7 tentativi progressivamente meno rigidi + fallback centro-comune, cache-first.
 * Nessuna coordinata viene mai inventata: se tutti i tentativi falliscono, la stazione
 * resta senza lat/lng ([GeocodeResult.NOT_FOUND]).
 *
 * Due ottimizzazioni rispetto al Python, entrambe a parità di risultato:
 * - una query già tentata per lo stesso indirizzo non viene ripetuta (nel Python i tentativi 1 e 2
 *   producono URL identici quando manca il CAP, e 3 e 4 quando l'indirizzo è già pulito: erano due
 *   attese da 1,1 s buttate su quasi ogni indirizzo);
 * - il centro del comune è memorizzato per comune, non per indirizzo: prima ogni stazione non
 *   risolta di Napoli rifaceva la stessa identica ricerca "Napoli, Campania, Italia".
 */
class GeocodingEngine(private val geocodeDao: GeocodeDao) {

    /** Chiave di cache di un indirizzo (identica a `normalize_address` del Python). */
    fun cacheKey(address: String, city: String): String = AddressCleaning.normalizeAddress(address, city)

    /**
     * Risultati già in cache per un insieme di indirizzi, con una sola query invece di N.
     * Le chiavi assenti dalla mappa sono quelle che richiedono una chiamata di rete.
     */
    suspend fun cachedResults(keys: List<String>): Map<String, GeocodeResult> {
        if (keys.isEmpty()) return emptyMap()
        val result = HashMap<String, GeocodeResult>(keys.size)
        // SQLite limita il numero di parametri di una IN (default 999): richieste a blocchi.
        for (chunk in keys.distinct().chunked(500)) {
            for (entity in geocodeDao.getCachedBatch(chunk)) {
                result[entity.normalizedAddress] = entity.toResult()
            }
        }
        return result
    }

    suspend fun geocodeAddress(address: String, city: String, province: String = ""): GeocodeResult {
        val normalized = cacheKey(address, city)
        geocodeDao.getCached(normalized)?.let { return it.toResult() }

        val cleanedStreet = AddressCleaning.cleanStreet(address)
        val cap = AddressCleaning.extractCap(address)
        val countyOrNull = province.ifBlank { null }

        // Ogni query già tentata per QUESTO indirizzo viene saltata: se avesse trovato qualcosa
        // ci saremmo già fermati, quindi ripeterla darebbe per forza di nuovo null.
        val tried = HashSet<String>()
        suspend fun structured(street: String, postalcode: String?): LatLng? {
            if (!tried.add("s|$street|$city|$countyOrNull|$postalcode")) return null
            return NominatimClient.searchStructured(street, city, countyOrNull, postalcode)
        }
        suspend fun freeText(query: String): LatLng? {
            if (!tried.add("f|$query")) return null
            return NominatimClient.searchFreeText(query)
        }

        // Tentativo 1: query strutturata (via + comune + provincia + CAP).
        var coords = structured(cleanedStreet, cap)
        // Tentativo 2: strutturata senza CAP.
        if (coords == null) coords = structured(cleanedStreet, null)
        // Tentativo 3: full-text con indirizzo ripulito.
        if (coords == null) coords = freeText("$cleanedStreet, $city, Campania, Italia")
        // Tentativo 4: full-text con indirizzo grezzo originale.
        if (coords == null) coords = freeText("$address, $city, Campania, Italia")

        // Tentativo 5: come 1/2 ma con le iniziali puntate del nome proprio rimosse.
        val strippedStreet = AddressCleaning.stripInitials(cleanedStreet)
        if (coords == null && strippedStreet != cleanedStreet) {
            coords = structured(strippedStreet, cap)
            if (coords == null) coords = structured(strippedStreet, null)
        }

        // Tentativo 6: indirizzo ripulito anche da km/direzioni (statali/provinciali).
        val roadCleaned = AddressCleaning.stripRoadMarkers(strippedStreet)
        if (coords == null && roadCleaned.isNotBlank() && roadCleaned != strippedStreet) {
            coords = freeText("$roadCleaned, $city, Campania, Italia")
        }

        // Tentativo 7: sigla autostradale nuda (es. "A1", "A16").
        val motorwayCode = AddressCleaning.extractMotorwayCode(address)
        if (coords == null && motorwayCode != null) {
            coords = freeText("$motorwayCode, $city, Campania, Italia")
        }

        if (coords != null) {
            storeResult(normalized, "$address, $city", coords.lat, coords.lng, found = true, precision = "indirizzo")
            return GeocodeResult(coords.lat, coords.lng, "indirizzo")
        }

        // Fallback finale: centro del comune reale (verificato da Nominatim, non inventato).
        val comuneCoords = comuneCenter(city)
        if (comuneCoords != null) {
            storeResult(normalized, "$address, $city", comuneCoords.lat, comuneCoords.lng, found = true, precision = "comune")
            return GeocodeResult(comuneCoords.lat, comuneCoords.lng, "comune")
        }

        storeResult(normalized, "$address, $city", null, null, found = false, precision = "indirizzo")
        return GeocodeResult.NOT_FOUND
    }

    /**
     * Centro del comune, memorizzato una volta sola per comune sotto una chiave dedicata:
     * decine di stazioni non risolte nello stesso comune condividono lo stesso identico risultato.
     */
    private suspend fun comuneCenter(city: String): LatLng? {
        val key = "__comune__|" + city.trim().lowercase(Locale.ROOT)
        geocodeDao.getCached(key)?.let { cached ->
            return if (cached.found && cached.latitude != null && cached.longitude != null) {
                LatLng(cached.latitude, cached.longitude)
            } else {
                null
            }
        }
        val coords = NominatimClient.searchFreeText("$city, Campania, Italia")
        storeResult(key, city, coords?.lat, coords?.lng, found = coords != null, precision = "comune")
        return coords
    }

    private fun GeocodeCacheEntity.toResult(): GeocodeResult =
        if (found) GeocodeResult(latitude, longitude, precision) else GeocodeResult.NOT_FOUND

    private suspend fun storeResult(
        normalized: String,
        rawAddress: String,
        lat: Double?,
        lng: Double?,
        found: Boolean,
        precision: String
    ) {
        val now = nowIso()
        geocodeDao.upsert(
            GeocodeCacheEntity(
                normalizedAddress = normalized,
                rawAddress = rawAddress,
                latitude = lat,
                longitude = lng,
                found = found,
                precision = precision,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun nowIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}

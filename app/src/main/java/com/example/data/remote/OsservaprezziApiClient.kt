package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client dell'API pubblica dell'Osservaprezzi carburanti (MIMIT), la stessa che alimenta
 * `carburanti.mise.gov.it`. Rispetto allo scraping HTML che sostituisce ha due vantaggi decisivi:
 * i prezzi sono quelli ufficialmente comunicati dai gestori e **le coordinate dell'impianto
 * arrivano già dalla fonte**, quindi non serve geocodificare nulla.
 *
 * Il filtro che il server applica davvero è `province`; `fuelId` nel corpo della richiesta viene
 * ignorato, quindi il GPL si seleziona qui, lato client. Una richiesta per provincia pesa ~70 KB
 * compressi: OkHttp negozia e decomprime gzip da sé, purché non si imposti a mano
 * `Accept-Encoding`.
 */
object OsservaprezziApiClient {

    private const val SEARCH_URL = "https://carburanti.mise.gov.it/ospzApi/search/servicearea"
    private const val USER_AGENT = "GPLCampaniaApp/1.0 (+https://github.com/mich-de/GPL_CAMPANIA)"
    private val JSON = "application/json".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Scarica le 5 province in parallelo: sono richieste indipendenti, quindi il tempo totale è
     * quello della più lenta e non la somma. Una provincia che fallisce viene semplicemente
     * saltata; decidere cosa fare di un risultato vuoto spetta al chiamante, che non deve mai
     * ripiegare su dati inventati.
     */
    suspend fun fetchCampaniaGplStations(): List<RemoteGplStation> = coroutineScope {
        CAMPANIA_PROVINCES.map { province ->
            async(Dispatchers.IO) { fetchProvince(province) }
        }.awaitAll().flatten()
    }

    private suspend fun fetchProvince(province: String): List<RemoteGplStation> =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("province", province).toString()
            val request = Request.Builder()
                .url(SEARCH_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .post(payload.toRequestBody(JSON))
                .build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string().orEmpty()
                    parseResponse(body, province)
                }
            } catch (e: IOException) {
                emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * Estrae i soli impianti che vendono GPL. Separata dalla parte di rete per poter essere
     * verificata nei test unitari su una risposta reale, senza toccare la rete.
     *
     * [fallbackProvince] copre il caso in cui l'indirizzo non riporti la sigla: si usa la provincia
     * effettivamente richiesta, che è un dato certo, non una supposizione.
     */
    fun parseResponse(body: String, fallbackProvince: String): List<RemoteGplStation> {
        if (body.isBlank()) return emptyList()
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()

        val stations = mutableListOf<RemoteGplStation>()
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val fuels = item.optJSONArray("fuels") ?: continue

            // Un impianto può pubblicare il GPL sia self sia servito: si tiene il più conveniente,
            // e a parità di prezzo il self, che è la modalità effettivamente disponibile a quella cifra.
            var bestPrice: Double? = null
            var bestIsSelf = false
            for (f in 0 until fuels.length()) {
                val fuel = fuels.optJSONObject(f) ?: continue
                if (fuel.optInt("fuelId", -1) != FUEL_ID_GPL) continue
                val price = fuel.optDouble("price", Double.NaN)
                if (price.isNaN() || price <= 0.0) continue
                val isSelf = fuel.optBoolean("isSelf", false)
                if (bestPrice == null || price < bestPrice || (price == bestPrice && isSelf && !bestIsSelf)) {
                    bestPrice = price
                    bestIsSelf = isSelf
                }
            }
            val price = bestPrice ?: continue

            val parsed = MimitAddressParser.parse(item.optString("address"))
            val communicated = parseIsoPriceDate(item.optString("insertDate"))
            val location = item.optJSONObject("location")
            val lat = location?.optDouble("lat", Double.NaN)?.takeIf { !it.isNaN() && it != 0.0 }
            val lng = location?.optDouble("lng", Double.NaN)?.takeIf { !it.isNaN() && it != 0.0 }

            stations.add(
                RemoteGplStation(
                    impiantoId = item.optLong("id"),
                    nome = item.optString("name").trim(),
                    brand = item.optString("brand").trim(),
                    via = parsed?.via.orEmpty(),
                    comune = parsed?.comune.orEmpty(),
                    provincia = parsed?.provincia?.takeIf { it.isNotBlank() } ?: fallbackProvince,
                    latitude = lat,
                    longitude = lng,
                    gplPrice = price,
                    gplIsSelf = bestIsSelf,
                    priceDate = communicated.formatted,
                    priceDay = communicated.sortKey
                )
            )
        }
        return stations
    }
}

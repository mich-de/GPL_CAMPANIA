package com.example.data.geocoding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

data class LatLng(val lat: Double, val lng: Double)

/**
 * Client Nominatim on-device: stesso User-Agent e stessi parametri della versione Python
 * (`app/geocoding.py`), con rate limiting ≥1.1s tra richieste (fair-use policy).
 *
 * Miglioria rispetto al Python: su HTTP 429 non si passa subito al tier successivo (che ha
 * causato il blocco visto durante lo sviluppo) — si applica un backoff (Retry-After se
 * presente, altrimenti 5s fissi), un solo retry, poi si abbandona la richiesta.
 */
object NominatimClient {
    private const val NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
    private const val USER_AGENT = "GPLCampaniaApp/1.0 (+https://github.com/mich-de/GPL_CAMPANIA)"
    private const val MIN_REQUEST_INTERVAL_MS = 1100L
    private const val RETRY_AFTER_FALLBACK_MS = 5000L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val rateLimitMutex = Mutex()
    private var lastRequestAtMs = 0L

    private suspend fun rateLimit() {
        rateLimitMutex.withLock {
            val elapsed = System.currentTimeMillis() - lastRequestAtMs
            if (elapsed < MIN_REQUEST_INTERVAL_MS) {
                delay(MIN_REQUEST_INTERVAL_MS - elapsed)
            }
            lastRequestAtMs = System.currentTimeMillis()
        }
    }

    suspend fun searchStructured(street: String, city: String, county: String?, postalcode: String?): LatLng? {
        val urlBuilder = NOMINATIM_URL.toHttpUrl().newBuilder()
            .addQueryParameter("street", street)
            .addQueryParameter("city", city)
            .addQueryParameter("state", "Campania")
            .addQueryParameter("country", "Italia")
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "1")
            .addQueryParameter("countrycodes", "it")
        if (!county.isNullOrBlank()) urlBuilder.addQueryParameter("county", county)
        if (!postalcode.isNullOrBlank()) urlBuilder.addQueryParameter("postalcode", postalcode)
        return execute(urlBuilder.build())
    }

    suspend fun searchFreeText(query: String): LatLng? {
        val url = NOMINATIM_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "1")
            .addQueryParameter("countrycodes", "it")
            .build()
        return execute(url)
    }

    private suspend fun execute(url: HttpUrl, retried: Boolean = false): LatLng? = withContext(Dispatchers.IO) {
        rateLimit()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    if (retried) return@withContext null
                    val retryAfterMs = response.header("Retry-After")?.toLongOrNull()?.times(1000)
                        ?: RETRY_AFTER_FALLBACK_MS
                    delay(retryAfterMs)
                    return@withContext execute(url, retried = true)
                }
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
                val arr = JSONArray(body)
                if (arr.length() == 0) return@withContext null
                val first = arr.getJSONObject(0)
                LatLng(first.getString("lat").toDouble(), first.getString("lon").toDouble())
            }
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}

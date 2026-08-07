package com.example.data.local

import android.content.Context

/** Preferenze locali dell'app e impostazioni personali dell'utente. */
object BackendPreferences {
    private const val PREFS_NAME = "gpl_backend_prefs"
    private const val KEY_REPORTER_NAME = "reporter_name"
    private const val KEY_LAST_REFRESH_TIMESTAMP = "last_refresh_timestamp"
    private const val KEY_USER_LAT = "user_location_lat"
    private const val KEY_USER_LNG = "user_location_lng"
    private const val KEY_USER_LOCATION_IS_MANUAL = "user_location_is_manual"
    private const val KEY_CSV_PRICES_LAST_MODIFIED = "csv_prices_last_modified"

    /** Nome salvato dall'utente per pre-compilare le segnalazioni prezzo/nuove stazioni. */
    fun getReporterName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_REPORTER_NAME, null)?.trim().orEmpty()
    }

    fun setReporterName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_REPORTER_NAME, name.trim()).apply()
    }

    /** Timestamp (epoch millis) dell'ultimo refresh riuscito dei dati reali dal backend. */
    fun getLastRefreshTimestamp(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getLong(KEY_LAST_REFRESH_TIMESTAMP, -1L)
        return value.takeIf { it > 0 }
    }

    fun setLastRefreshTimestamp(context: Context, timestampMillis: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_REFRESH_TIMESTAMP, timestampMillis).apply()
    }

    /**
     * `Last-Modified` dell'ultimo CSV prezzi del MIMIT letto per intero, rispedito come
     * `If-Modified-Since` per farsi rispondere 304 (0 byte) quando i prezzi non sono cambiati.
     */
    fun getCsvPricesLastModified(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CSV_PRICES_LAST_MODIFIED, null)?.takeIf { it.isNotBlank() }
    }

    fun setCsvPricesLastModified(context: Context, lastModified: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CSV_PRICES_LAST_MODIFIED, lastModified).apply()
    }

    /** Ultima posizione reale nota (da GPS o inserita manualmente dall'utente), persistita tra i riavvii. */
    fun getUserLocation(context: Context): Pair<Double, Double>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_USER_LAT) || !prefs.contains(KEY_USER_LNG)) return null
        val lat = prefs.getFloat(KEY_USER_LAT, 0f).toDouble()
        val lng = prefs.getFloat(KEY_USER_LNG, 0f).toDouble()
        return Pair(lat, lng)
    }

    fun setUserLocation(context: Context, lat: Double, lng: Double, isManual: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_USER_LAT, lat.toFloat())
            .putFloat(KEY_USER_LNG, lng.toFloat())
            .putBoolean(KEY_USER_LOCATION_IS_MANUAL, isManual)
            .apply()
    }

    fun isUserLocationManual(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USER_LOCATION_IS_MANUAL, false)
    }
}

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
    private const val KEY_DIAG_PREFIX = "diagnostics_"
    private const val KEY_TANK_EXPIRY = "tank_expiry_day"
    private const val KEY_TANK_REFERENCE = "tank_reference_day"
    private const val KEY_TANK_PLATE = "tank_plate"
    private const val KEY_NEWS_LAST_FETCH = "news_last_fetch"

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
     * Fa scadere subito la cache di 15 minuti senza toccare Room: i dati reali restano visibili e
     * il prossimo avvio riscarica invece di aspettare. È l'unica azione "amministrativa" che serve
     * davvero, ed è reversibile per costruzione.
     */
    fun clearLastRefreshTimestamp(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LAST_REFRESH_TIMESTAMP).apply()
    }

    /** Diagnostica dell'ultimo tentativo di aggiornamento, persistita per sopravvivere ai riavvii. */
    fun getRefreshDiagnostics(context: Context): RefreshDiagnostics {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val outcome = prefs.getString(KEY_DIAG_PREFIX + "outcome", null)
            ?.let { name -> RefreshDiagnostics.Outcome.entries.firstOrNull { it.name == name } }
            ?: return RefreshDiagnostics()
        val unmeasured = RefreshDiagnostics.UNMEASURED
        return RefreshDiagnostics(
            attemptedAt = prefs.getLong(KEY_DIAG_PREFIX + "attempted_at", 0L),
            outcome = outcome,
            source = prefs.getString(KEY_DIAG_PREFIX + "source", "").orEmpty(),
            durationMillis = prefs.getLong(KEY_DIAG_PREFIX + "duration", 0L),
            message = prefs.getString(KEY_DIAG_PREFIX + "message", "").orEmpty(),
            stationsWritten = prefs.getInt(KEY_DIAG_PREFIX + "stations", unmeasured),
            duplicatesMerged = prefs.getInt(KEY_DIAG_PREFIX + "duplicates", unmeasured),
            withoutCoordinates = prefs.getInt(KEY_DIAG_PREFIX + "no_coords", unmeasured),
            pricesToday = prefs.getInt(KEY_DIAG_PREFIX + "prices_today", unmeasured),
            pricesWithinWeek = prefs.getInt(KEY_DIAG_PREFIX + "prices_week", unmeasured),
            pricesOlderThanMonth = prefs.getInt(KEY_DIAG_PREFIX + "prices_month", unmeasured),
            pricesWithoutDate = prefs.getInt(KEY_DIAG_PREFIX + "prices_no_date", unmeasured)
        )
    }

    fun setRefreshDiagnostics(context: Context, diagnostics: RefreshDiagnostics) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_DIAG_PREFIX + "attempted_at", diagnostics.attemptedAt)
            .putString(KEY_DIAG_PREFIX + "outcome", diagnostics.outcome.name)
            .putString(KEY_DIAG_PREFIX + "source", diagnostics.source)
            .putLong(KEY_DIAG_PREFIX + "duration", diagnostics.durationMillis)
            .putString(KEY_DIAG_PREFIX + "message", diagnostics.message)
            .putInt(KEY_DIAG_PREFIX + "stations", diagnostics.stationsWritten)
            .putInt(KEY_DIAG_PREFIX + "duplicates", diagnostics.duplicatesMerged)
            .putInt(KEY_DIAG_PREFIX + "no_coords", diagnostics.withoutCoordinates)
            .putInt(KEY_DIAG_PREFIX + "prices_today", diagnostics.pricesToday)
            .putInt(KEY_DIAG_PREFIX + "prices_week", diagnostics.pricesWithinWeek)
            .putInt(KEY_DIAG_PREFIX + "prices_month", diagnostics.pricesOlderThanMonth)
            .putInt(KEY_DIAG_PREFIX + "prices_no_date", diagnostics.pricesWithoutDate)
            .apply()
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

    /**
     * Scadenza del serbatoio GPL inserita dall'utente. `null` finché non l'ha inserita: l'app non
     * ipotizza una data al posto suo, e senza data non mostra nessun conto alla rovescia.
     */
    fun getTankRevision(context: Context): TankRevision? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getInt(KEY_TANK_EXPIRY, 0)
        if (expiry <= 0) return null
        return TankRevision(
            expiryDayKey = expiry,
            referenceDayKey = prefs.getInt(KEY_TANK_REFERENCE, 0),
            plate = prefs.getString(KEY_TANK_PLATE, "").orEmpty()
        )
    }

    fun setTankRevision(context: Context, revision: TankRevision) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_TANK_EXPIRY, revision.expiryDayKey)
            .putInt(KEY_TANK_REFERENCE, revision.referenceDayKey)
            .putString(KEY_TANK_PLATE, revision.plate.trim())
            .apply()
    }

    fun clearTankRevision(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_TANK_EXPIRY)
            .remove(KEY_TANK_REFERENCE)
            .remove(KEY_TANK_PLATE)
            .apply()
    }

    /** Quando le notizie sono state cercate l'ultima volta, riuscita o meno. */
    fun getNewsLastFetch(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_NEWS_LAST_FETCH, -1L).takeIf { it > 0 }
    }

    fun setNewsLastFetch(context: Context, timestampMillis: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_NEWS_LAST_FETCH, timestampMillis).apply()
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

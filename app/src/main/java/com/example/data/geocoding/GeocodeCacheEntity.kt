package com.example.data.geocoding

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cache permanente di geocoding (mai scaduta), inclusi i risultati "non trovato": stessa
 * struttura della tabella SQLite `geocode_cache` di `app/geocoding.py`. */
@Entity(tableName = "geocode_cache")
data class GeocodeCacheEntity(
    @PrimaryKey val normalizedAddress: String,
    val rawAddress: String,
    val latitude: Double?,
    val longitude: Double?,
    val found: Boolean,
    val precision: String,
    val createdAt: String,
    val updatedAt: String
)

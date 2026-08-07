package com.example.data.geocoding

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GeocodeDao {
    @Query("SELECT * FROM geocode_cache WHERE normalizedAddress = :normalized LIMIT 1")
    suspend fun getCached(normalized: String): GeocodeCacheEntity?

    /** Lookup in blocco: una sola query per tutte le stazioni di un refresh, invece di N query singole. */
    @Query("SELECT * FROM geocode_cache WHERE normalizedAddress IN (:normalized)")
    suspend fun getCachedBatch(normalized: List<String>): List<GeocodeCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GeocodeCacheEntity)

    /** Import del seed: le voci già presenti (ottenute dal device) hanno sempre la precedenza. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(entities: List<GeocodeCacheEntity>)

    @Query("SELECT COUNT(*) FROM geocode_cache")
    suspend fun count(): Int
}

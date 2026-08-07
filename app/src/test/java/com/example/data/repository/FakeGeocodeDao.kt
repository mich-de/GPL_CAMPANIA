package com.example.data.repository

import com.example.data.geocoding.GeocodeCacheEntity
import com.example.data.geocoding.GeocodeDao

/** In-memory GeocodeDao per i test: nessun Room/SQLite reale, nessuna chiamata a Nominatim. */
class FakeGeocodeDao : GeocodeDao {
    private val cache = LinkedHashMap<String, GeocodeCacheEntity>()

    override suspend fun getCached(normalized: String): GeocodeCacheEntity? = cache[normalized]

    override suspend fun getCachedBatch(normalized: List<String>): List<GeocodeCacheEntity> =
        normalized.mapNotNull { cache[it] }

    override suspend fun upsert(entity: GeocodeCacheEntity) {
        cache[entity.normalizedAddress] = entity
    }

    override suspend fun insertIgnoringExisting(entities: List<GeocodeCacheEntity>) {
        entities.forEach { cache.putIfAbsent(it.normalizedAddress, it) }
    }

    override suspend fun count(): Int = cache.size
}

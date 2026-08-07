package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.geocoding.GeocodeCacheEntity
import com.example.data.geocoding.GeocodeDao
import com.example.data.model.GplStation
import com.example.data.model.UserPriceReport

@Database(
    entities = [GplStation::class, UserPriceReport::class, GeocodeCacheEntity::class],
    version = 3,
    exportSchema = false
)
abstract class GplDatabase : RoomDatabase() {
    abstract fun gplDao(): GplDao
    abstract fun geocodeDao(): GeocodeDao

    companion object {
        @Volatile
        private var INSTANCE: GplDatabase? = null

        fun getDatabase(context: Context): GplDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GplDatabase::class.java,
                    "gpl_campania_db"
                )
                    // Pre-release, nessun utente reale: ogni bump di schema ricostruisce il DB
                    // locale invece di scrivere una migrazione dedicata (v3: cache geocoding).
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

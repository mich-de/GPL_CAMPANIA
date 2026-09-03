package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.geocoding.GeocodeCacheEntity
import com.example.data.geocoding.GeocodeDao
import com.example.data.model.GplStation
import com.example.data.model.UserPriceReport

@Database(
    entities = [
        GplStation::class,
        UserPriceReport::class,
        GeocodeCacheEntity::class,
        NationalGplSnapshot::class,
        NewsItem::class
    ],
    version = 4,
    exportSchema = false
)
abstract class GplDatabase : RoomDatabase() {
    abstract fun gplDao(): GplDao
    abstract fun geocodeDao(): GeocodeDao
    abstract fun nationalStatsDao(): NationalStatsDao
    abstract fun newsDao(): NewsDao

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
                    // La v4 aggiunge solo due tabelle nuove e non tocca quelle esistenti, quindi
                    // vale la pena scriverla: ricostruire il DB perderebbe i preferiti e le
                    // segnalazioni prezzo dell'utente, che sono gli unici dati che l'app non può
                    // riscaricare da nessuna fonte.
                    .addMigrations(MIGRATION_3_4)
                    // Rete di sicurezza per qualsiasi altro percorso di versione (installazioni di
                    // sviluppo rimaste a v1/v2): i distributori si riscaricano dalla fonte ufficiale.
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * v3 -> v4: due tabelle nuove, nessuna riga toccata.
         *
         * Le istruzioni ricalcano esattamente quelle che Room genera dalle entità (stessi tipi,
         * stessi `NOT NULL`): Room verifica lo schema all'apertura e rifiuta il database se non
         * combaciano.
         */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `national_gpl_snapshots` (" +
                        "`dayKey` INTEGER NOT NULL, " +
                        "`capturedAt` INTEGER NOT NULL, " +
                        "`averagePrice` REAL NOT NULL, " +
                        "`medianPrice` REAL NOT NULL, " +
                        "`stationCount` INTEGER NOT NULL, " +
                        "`skippedRows` INTEGER NOT NULL, " +
                        "`regionsEncoded` TEXT NOT NULL, " +
                        "PRIMARY KEY(`dayKey`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `news_items` (" +
                        "`link` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`summary` TEXT NOT NULL, " +
                        "`publishedAt` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`link`))"
                )
            }
        }
    }
}

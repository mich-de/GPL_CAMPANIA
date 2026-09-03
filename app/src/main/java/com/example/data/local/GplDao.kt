package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.GplStation
import com.example.data.model.UserPriceReport
import kotlinx.coroutines.flow.Flow

@Dao
interface GplDao {
    @Query("SELECT * FROM gpl_stations ORDER BY gplPrice ASC")
    fun getAllStations(): Flow<List<GplStation>>

    @Query("SELECT * FROM gpl_stations WHERE isFavorite = 1 ORDER BY gplPrice ASC")
    fun getFavoriteStations(): Flow<List<GplStation>>

    @Query("SELECT * FROM gpl_stations WHERE id = :id LIMIT 1")
    suspend fun getStationById(id: String): GplStation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<GplStation>)

    @Query("DELETE FROM gpl_stations")
    suspend fun deleteAllStations()

    @Query("DELETE FROM gpl_stations WHERE id LIKE 'gpl\\_%' ESCAPE '\\'")
    suspend fun deleteBackendSourcedStations()

    @Query("SELECT COUNT(*) FROM gpl_stations WHERE id LIKE 'gpl\\_%' ESCAPE '\\'")
    suspend fun countBackendSourcedStations(): Int

    @Query("SELECT id FROM gpl_stations WHERE isFavorite = 1")
    suspend fun getFavoriteStationIds(): List<String>

    // Conteggi per il pannello di diagnostica: letti al momento dell'apertura, perché cambiano
    // anche fuori dal refresh (un preferito, una segnalazione, un distributore aggiunto a mano).

    @Query("SELECT COUNT(*) FROM gpl_stations")
    suspend fun countAllStations(): Int

    @Query("SELECT COUNT(*) FROM gpl_stations WHERE latitude IS NULL OR longitude IS NULL")
    suspend fun countStationsWithoutCoordinates(): Int

    @Query("SELECT COUNT(*) FROM gpl_stations WHERE isFavorite = 1")
    suspend fun countFavoriteStations(): Int

    @Query("SELECT COUNT(*) FROM price_reports")
    suspend fun countPriceReports(): Int

    /** Preferiti provenienti dalla fonte ufficiale: righe intere, servono indirizzo e coordinate
     * per ritrovare la stessa stazione anche se la fonte le ha cambiato identificativo. */
    @Query("SELECT * FROM gpl_stations WHERE isFavorite = 1 AND id LIKE 'gpl\\_%' ESCAPE '\\'")
    suspend fun getFavoriteBackendStations(): List<GplStation>

    /** Sposta le segnalazioni di prezzo sull'id nuovo quando una stazione viene ritrovata sotto
     * un identificativo diverso: la cronologia inserita dall'utente non va persa. */
    @Query("UPDATE price_reports SET stationId = :newId WHERE stationId = :oldId")
    suspend fun remapPriceReports(oldId: String, newId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: GplStation)

    @Update
    suspend fun updateStation(station: GplStation)

    @Query("UPDATE gpl_stations SET isFavorite = :isFav WHERE id = :id")
    suspend fun updateFavoriteStatus(id: String, isFav: Boolean)

    @Query("UPDATE gpl_stations SET gplPrice = :newPrice, priceLastUpdated = :updatedDate WHERE id = :id")
    suspend fun updateGplPrice(id: String, newPrice: Double, updatedDate: String)

    /** Aggiorna la sola posizione di una stazione: usata dal geocoding progressivo in background,
     * che pubblica ogni coordinata reale appena la ottiene senza riscrivere il resto della riga. */
    @Query("UPDATE gpl_stations SET latitude = :lat, longitude = :lng WHERE id = :id")
    suspend fun updateCoordinates(id: String, lat: Double?, lng: Double?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceReport(report: UserPriceReport)

    @Query("SELECT * FROM price_reports WHERE stationId = :stationId ORDER BY timestamp DESC")
    fun getPriceReportsForStation(stationId: String): Flow<List<UserPriceReport>>
}

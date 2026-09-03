package com.example.data.repository

import com.example.data.local.GplDao
import com.example.data.model.GplStation
import com.example.data.model.UserPriceReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory GplDao per i test: nessun Room/SQLite reale coinvolto. */
class FakeGplDao : GplDao {
    private val stationsFlow = MutableStateFlow<List<GplStation>>(emptyList())
    private val priceReportsFlow = MutableStateFlow<List<UserPriceReport>>(emptyList())

    override fun getAllStations(): Flow<List<GplStation>> = stationsFlow

    override fun getFavoriteStations(): Flow<List<GplStation>> = stationsFlow.map { list -> list.filter { it.isFavorite } }

    override suspend fun getStationById(id: String): GplStation? = stationsFlow.value.find { it.id == id }

    override suspend fun insertStations(stations: List<GplStation>) {
        val byId = stationsFlow.value.associateBy { it.id }.toMutableMap()
        stations.forEach { byId[it.id] = it }
        stationsFlow.value = byId.values.toList()
    }

    override suspend fun deleteAllStations() {
        stationsFlow.value = emptyList()
    }

    override suspend fun deleteBackendSourcedStations() {
        stationsFlow.value = stationsFlow.value.filterNot { it.id.startsWith("gpl_") }
    }

    override suspend fun countBackendSourcedStations(): Int =
        stationsFlow.value.count { it.id.startsWith("gpl_") }

    override suspend fun getFavoriteStationIds(): List<String> =
        stationsFlow.value.filter { it.isFavorite }.map { it.id }

    override suspend fun countAllStations(): Int = stationsFlow.value.size

    override suspend fun countStationsWithoutCoordinates(): Int =
        stationsFlow.value.count { it.latitude == null || it.longitude == null }

    override suspend fun countFavoriteStations(): Int = stationsFlow.value.count { it.isFavorite }

    override suspend fun countPriceReports(): Int = priceReportsFlow.value.size

    override suspend fun getFavoriteBackendStations(): List<GplStation> =
        stationsFlow.value.filter { it.isFavorite && it.id.startsWith("gpl_") }

    override suspend fun remapPriceReports(oldId: String, newId: String) {
        priceReportsFlow.value = priceReportsFlow.value.map {
            if (it.stationId == oldId) it.copy(stationId = newId) else it
        }
    }

    override suspend fun updateCoordinates(id: String, lat: Double?, lng: Double?) {
        stationsFlow.value = stationsFlow.value.map {
            if (it.id == id) it.copy(latitude = lat, longitude = lng) else it
        }
    }

    override suspend fun insertStation(station: GplStation) = insertStations(listOf(station))

    override suspend fun updateStation(station: GplStation) = insertStations(listOf(station))

    override suspend fun updateFavoriteStatus(id: String, isFav: Boolean) {
        stationsFlow.value = stationsFlow.value.map { if (it.id == id) it.copy(isFavorite = isFav) else it }
    }

    override suspend fun updateGplPrice(id: String, newPrice: Double, updatedDate: String) {
        stationsFlow.value = stationsFlow.value.map {
            if (it.id == id) it.copy(gplPrice = newPrice, priceLastUpdated = updatedDate) else it
        }
    }

    override suspend fun insertPriceReport(report: UserPriceReport) {
        priceReportsFlow.value = priceReportsFlow.value + report
    }

    override fun getPriceReportsForStation(stationId: String): Flow<List<UserPriceReport>> =
        priceReportsFlow.map { list -> list.filter { it.stationId == stationId } }

    fun currentStations(): List<GplStation> = stationsFlow.value

    fun currentPriceReports(): List<UserPriceReport> = priceReportsFlow.value
}

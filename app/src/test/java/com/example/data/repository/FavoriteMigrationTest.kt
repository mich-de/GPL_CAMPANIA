package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GplStation
import com.example.data.model.UserPriceReport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Il passaggio alla fonte ufficiale cambia tutti gli id delle stazioni (da `gpl_<provincia>_<hash>`
 * a `gpl_mimit_<idImpianto>`): senza riaccoppiamento i preferiti salvati sparirebbero al primo
 * aggiornamento. Qui si verifica che vengano ritrovati, e con loro le segnalazioni di prezzo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoriteMigrationTest {

    private fun newRepository(): Pair<GplRepository, FakeGplDao> {
        val dao = FakeGplDao()
        val context = ApplicationProvider.getApplicationContext<Context>()
        return GplRepository(context, dao, FakeGeocodeDao()) to dao
    }

    private fun station(
        id: String,
        address: String = "Via Roma 1",
        city: String = "Napoli",
        lat: Double? = 40.8500,
        lng: Double? = 14.2700,
        isFavorite: Boolean = false
    ) = GplStation(
        id = id, name = "Distributore", brand = "Eni", address = address, city = city,
        province = "NA", latitude = lat, longitude = lng, gplPrice = 0.699,
        priceLastUpdated = "07 Ago 2026", services = "GPL,Servito", isFavorite = isFavorite
    )

    @Test
    fun `un preferito con lo stesso id resta preferito`() = runTest {
        val (repo, dao) = newRepository()
        dao.insertStation(station("gpl_mimit_56697", isFavorite = true))

        val migrated = repo.restoreFavorites(listOf(station("gpl_mimit_56697")))

        assertTrue(migrated.single().isFavorite)
    }

    @Test
    fun `un preferito con id vecchio viene ritrovato da comune e via`() = runTest {
        val (repo, dao) = newRepository()
        // Id e formattazione dell'indirizzo com'erano ai tempi dello scraping.
        dao.insertStation(
            station("gpl_napoli_-1483920", address = "VIA ROMA, 1", city = "Napoli", isFavorite = true)
        )

        val migrated = repo.restoreFavorites(listOf(station("gpl_mimit_56697", address = "Via Roma 1")))

        assertEquals("gpl_mimit_56697", migrated.single().id)
        assertTrue(migrated.single().isFavorite)
    }

    @Test
    fun `un preferito con indirizzo scritto diversamente viene ritrovato dalla posizione`() = runTest {
        val (repo, dao) = newRepository()
        dao.insertStation(
            station(
                "gpl_napoli_-1483920",
                address = "Strada Statale 18 km 3",
                lat = 40.8500,
                lng = 14.2700,
                isFavorite = true
            )
        )

        // ~85 m più a nord: stesso impianto, posizione ufficiale invece di quella geocodificata.
        val fresh = station("gpl_mimit_56697", address = "SS 18 KM 3+100 SNC", lat = 40.85077, lng = 14.2700)
        val migrated = repo.restoreFavorites(listOf(fresh))

        assertTrue(migrated.single().isFavorite)
    }

    @Test
    fun `un distributore diverso a un chilometro di distanza non eredita il preferito`() = runTest {
        val (repo, dao) = newRepository()
        dao.insertStation(
            station("gpl_napoli_-1483920", address = "Via Alfa 1", lat = 40.8500, lng = 14.2700, isFavorite = true)
        )

        val fresh = station("gpl_mimit_99999", address = "Via Beta 2", lat = 40.8590, lng = 14.2700)
        val migrated = repo.restoreFavorites(listOf(fresh))

        assertFalse(migrated.single().isFavorite)
    }

    @Test
    fun `le segnalazioni di prezzo seguono la stazione sul nuovo id`() = runTest {
        val (repo, dao) = newRepository()
        dao.insertStation(station("gpl_napoli_-1483920", address = "VIA ROMA, 1", isFavorite = true))
        dao.insertPriceReport(
            UserPriceReport(
                stationId = "gpl_napoli_-1483920",
                reportedGplPrice = 0.689,
                reporterName = "Michele",
                notes = ""
            )
        )

        repo.restoreFavorites(listOf(station("gpl_mimit_56697", address = "Via Roma 1")))

        assertEquals("gpl_mimit_56697", dao.currentPriceReports().single().stationId)
    }

    @Test
    fun `senza preferiti salvati la lista nuova resta invariata`() = runTest {
        val (repo, dao) = newRepository()
        dao.insertStation(station("gpl_mimit_1"))

        val fresh = listOf(station("gpl_mimit_1"), station("gpl_mimit_2"))
        val migrated = repo.restoreFavorites(fresh)

        assertEquals(fresh, migrated)
    }
}

package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BackendPreferences
import com.example.data.local.RefreshDiagnostics
import com.example.data.local.asPlainText
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

/** Il pannello di diagnostica conta quello che c'è davvero, e ammette ciò che non ha mai misurato. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonitoringReportTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun station(
        id: String,
        latitude: Double? = 40.85,
        isFavorite: Boolean = false
    ) = GplStation(
        id = id,
        name = "Distributore $id",
        brand = "Eni",
        address = "Via Roma 1",
        city = "Napoli",
        province = "NA",
        latitude = latitude,
        longitude = if (latitude == null) null else 14.27,
        gplPrice = 0.72,
        priceLastUpdated = "10 Ago 2026",
        services = "GPL,Servito",
        isFavorite = isFavorite
    )

    @Test
    fun `distingue le righe della fonte ufficiale da quelle aggiunte a mano`() = runTest {
        val dao = FakeGplDao()
        dao.insertStations(
            listOf(
                station("gpl_mimit_1"),
                station("gpl_mimit_2", isFavorite = true),
                station("user_1700000000000", latitude = null)
            )
        )
        dao.insertPriceReport(
            UserPriceReport(stationId = "gpl_mimit_1", reportedGplPrice = 0.70, reporterName = "Michele", notes = "")
        )

        val report = GplRepository(context, dao, FakeGeocodeDao()).buildMonitoringReport()

        assertEquals(3, report.totalStations)
        assertEquals(2, report.officialStations)
        assertEquals(1, report.userStations)
        assertEquals(1, report.favorites)
        assertEquals(1, report.priceReports)
        // La stazione aggiunta a mano non è geocodificabile: resta senza coordinate, non ne riceve
        // di plausibili.
        assertEquals(1, report.withoutCoordinates)
    }

    @Test
    fun `senza nessun tentativo registrato i valori restano non misurati, non zero`() = runTest {
        val report = GplRepository(context, FakeGplDao(), FakeGeocodeDao()).buildMonitoringReport()

        assertEquals(RefreshDiagnostics.Outcome.NEVER, report.diagnostics.outcome)
        assertEquals(RefreshDiagnostics.UNMEASURED, report.diagnostics.stationsWritten)
        assertTrue(report.asPlainText().contains("righe scritte: —"))
    }

    @Test
    fun `la diagnostica sopravvive alla chiusura dell'app`() = runTest {
        BackendPreferences.setRefreshDiagnostics(
            context,
            RefreshDiagnostics(
                attemptedAt = 1_786_000_000_000L,
                outcome = RefreshDiagnostics.Outcome.FAILED,
                source = "",
                durationMillis = 12_345L,
                message = "Dati ufficiali non raggiungibili",
                stationsWritten = 426,
                duplicatesMerged = 2,
                withoutCoordinates = 0,
                pricesToday = 8,
                pricesWithinWeek = 380,
                pricesOlderThanMonth = 12,
                pricesWithoutDate = 0
            )
        )

        val reloaded = BackendPreferences.getRefreshDiagnostics(context)

        assertEquals(RefreshDiagnostics.Outcome.FAILED, reloaded.outcome)
        assertEquals("Dati ufficiali non raggiungibili", reloaded.message)
        // L'errore non cancella le misure dell'ultimo scarico riuscito: descrivono ancora i dati
        // che l'utente sta guardando.
        assertEquals(426, reloaded.stationsWritten)
        assertEquals(12, reloaded.pricesOlderThanMonth)
    }

    @Test
    fun `la cache scade quando il timestamp e piu vecchio del TTL`() = runTest {
        val repository = GplRepository(context, FakeGplDao(), FakeGeocodeDao())
        BackendPreferences.setLastRefreshTimestamp(context, System.currentTimeMillis())

        assertTrue(repository.buildMonitoringReport().isCacheValid)

        repository.invalidateCacheTtl()

        val afterInvalidation = repository.buildMonitoringReport()
        assertFalse(afterInvalidation.isCacheValid)
        // Invalidare la cache non tocca i dati: azzera solo il conto alla rovescia.
        assertEquals(null, afterInvalidation.lastRefreshTimestamp)
    }
}

package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.RemoteGplStation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Traduzione da dato ufficiale MIMIT a riga di Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemoteStationMappingTest {

    private fun newRepository(): GplRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return GplRepository(context, FakeGplDao(), FakeGeocodeDao())
    }

    private fun remote(
        impiantoId: Long = 56697,
        nome: String = "FUCCI FUEL 3",
        brand: String = "PompeBianche",
        comune: String = "SANT'ANASTASIA",
        latitude: Double? = 40.8695,
        gplIsSelf: Boolean = false,
        gplPrice: Double = 0.659
    ) = RemoteGplStation(
        impiantoId = impiantoId, nome = nome, brand = brand, via = "Via Roma 1",
        comune = comune, provincia = "NA", latitude = latitude, longitude = 14.4021,
        gplPrice = gplPrice, gplIsSelf = gplIsSelf, priceDate = "07 Ago 2026"
    )

    @Test
    fun `l'id usa l'identificativo nazionale dell'impianto e mantiene il prefisso gpl_`() = runTest {
        val repo = newRepository()

        val station = with(repo) { remote().toGplStation() }!!

        // Il prefisso "gpl_" è ciò che distingue le righe da fonte ufficiale (cancellate a ogni
        // refresh) da quelle aggiunte dall'utente, che non vanno mai toccate.
        assertEquals("gpl_mimit_56697", station.id)
    }

    @Test
    fun `il comune torna leggibile senza alterare il dato`() = runTest {
        val repo = newRepository()

        val station = with(repo) { remote(comune = "SANT'ANASTASIA").toGplStation() }!!

        assertEquals("Sant'Anastasia", station.city)
        assertEquals("FUCCI FUEL 3 (Sant'Anastasia)", station.name)
    }

    @Test
    fun `la modalita self o servito arriva dalla fonte, non da un default`() = runTest {
        val repo = newRepository()

        assertEquals("GPL,Self", with(repo) { remote(gplIsSelf = true).toGplStation() }!!.services)
        assertEquals("GPL,Servito", with(repo) { remote(gplIsSelf = false).toGplStation() }!!.services)
    }

    @Test
    fun `orari mai inventati e coordinate assenti che restano null`() = runTest {
        val repo = newRepository()

        val station = with(repo) { remote(latitude = null).toGplStation() }!!

        assertNull(station.latitude)
        assertNull(station.openHoursWeekday)
        assertNull(station.isOpenNow)
        assertNull(station.isOpening24h)
    }

    @Test
    fun `un impianto senza prezzo GPL valido non diventa una riga`() = runTest {
        val repo = newRepository()

        assertNull(with(repo) { remote(gplPrice = 0.0).toGplStation() })
    }
}

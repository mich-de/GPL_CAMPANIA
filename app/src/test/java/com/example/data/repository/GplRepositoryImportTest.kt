package com.example.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GplRepositoryImportTest {

    private fun newRepository(): Pair<GplRepository, FakeGplDao> {
        val dao = FakeGplDao()
        val context = ApplicationProvider.getApplicationContext<Context>()
        return GplRepository(context, dao, FakeGeocodeDao()) to dao
    }

    @Test
    fun `importMyLpgPoiFormat parses CSV rows into real stations`() = runTest {
        val (repo, dao) = newRepository()
        val csv = """
            Longitude,Latitude,Name,Address
            14.4082,40.6358,"Eni Sorrento","Via Roma 1"
        """.trimIndent()

        repo.importMyLpgPoiFormat(csv, isKmlOrXml = false)

        val stations = dao.currentStations()
        assertEquals(1, stations.size)
        assertEquals("Eni", stations[0].brand)
        assertEquals(40.6358, stations[0].latitude!!, 0.0001)
        assertEquals(14.4082, stations[0].longitude!!, 0.0001)
    }

    @Test
    fun `importMyLpgPoiFormat deduplicates rows with identical coordinates`() = runTest {
        val (repo, dao) = newRepository()
        val csv = """
            Longitude,Latitude,Name,Address
            14.4082,40.6358,"Eni Sorrento","Via Roma 1"
            14.40820,40.63580,"Eni Sorrento Duplicate","Via Roma 1"
        """.trimIndent()

        repo.importMyLpgPoiFormat(csv, isKmlOrXml = false)

        assertEquals(1, dao.currentStations().size)
    }

    @Test
    fun `importMyLpgPoiFormat parses KML placemarks into real stations`() = runTest {
        val (repo, dao) = newRepository()
        val kml = """
            <kml>
              <Placemark>
                <name>IP Vico Equense</name>
                <description>Via Marina 5 , 80069 VICO EQUENSE - myLPG.eu</description>
                <Point><coordinates>14.3900,40.6600,0</coordinates></Point>
              </Placemark>
            </kml>
        """.trimIndent()

        repo.importMyLpgPoiFormat(kml, isKmlOrXml = true)

        val stations = dao.currentStations()
        assertEquals(1, stations.size)
        assertEquals("IP", stations[0].brand)
        assertEquals("Vico Equense", stations[0].city)
    }

    @Test
    fun `importMyLpgPoiFormat never invents opening hours for imported stations`() = runTest {
        val (repo, dao) = newRepository()
        val csv = """
            Longitude,Latitude,Name,Address
            14.4082,40.6358,"Eni Sorrento","Via Roma 1"
        """.trimIndent()

        repo.importMyLpgPoiFormat(csv, isKmlOrXml = false)

        val station = dao.currentStations().single()
        assertNull(station.openHoursWeekday)
        assertNull(station.isOpenNow)
    }

    @Test
    fun `toggleFavorite flips favorite status via dao`() = runTest {
        val (repo, dao) = newRepository()
        dao.insertStation(
            com.example.data.model.GplStation(
                id = "s1", name = "Test", brand = "Eni", address = "Via Test", city = "Napoli",
                province = "NA", latitude = 40.85, longitude = 14.27, gplPrice = 0.72,
                priceLastUpdated = "Oggi", services = "GPL", isFavorite = false
            )
        )

        repo.toggleFavorite("s1", currentStatus = false)

        assertEquals(true, dao.currentStations().single().isFavorite)
    }
}

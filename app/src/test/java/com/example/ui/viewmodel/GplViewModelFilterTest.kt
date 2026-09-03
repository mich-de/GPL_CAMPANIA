package com.example.ui.viewmodel

import com.example.data.model.GplStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GplViewModelFilterTest {

    private fun station(
        id: String,
        name: String = "Distributore $id",
        brand: String = "Eni",
        city: String = "Napoli",
        province: String = "NA",
        address: String = "Via Test $id",
        latitude: Double? = 40.85,
        longitude: Double? = 14.27,
        gplPrice: Double = 0.72,
        isFavorite: Boolean = false
    ) = GplStation(
        id = id,
        name = name,
        brand = brand,
        address = address,
        city = city,
        province = province,
        latitude = latitude,
        longitude = longitude,
        gplPrice = gplPrice,
        priceLastUpdated = "Oggi",
        services = "GPL",
        isFavorite = isFavorite
    )

    @Test
    fun `search query matches name address city and brand`() {
        val stations = listOf(
            station(id = "1", name = "Eni Sorrento", city = "Sorrento"),
            station(id = "2", name = "IP Pompei", city = "Pompei", brand = "IP")
        )

        val result = GplViewModel.applyFiltersAndSort(stations, FilterParams(searchQuery = "sorrento"), 0.0, 0.0)

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `city filter for Penisola Sorrentina matches only its comuni`() {
        val stations = listOf(
            station(id = "1", city = "Sorrento"),
            station(id = "2", city = "Vico Equense"),
            station(id = "3", city = "Napoli")
        )

        val result = GplViewModel.applyFiltersAndSort(stations, FilterParams(selectedCity = "Penisola Sorrentina"), 0.0, 0.0)

        assertEquals(setOf("1", "2"), result.map { it.id }.toSet())
    }

    @Test
    fun `province filter matches by province code`() {
        val stations = listOf(
            station(id = "1", province = "SA", city = "Battipaglia"),
            station(id = "2", province = "AV", city = "Avellino")
        )

        val result = GplViewModel.applyFiltersAndSort(stations, FilterParams(selectedCity = "Salerno (Provincia)"), 0.0, 0.0)

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `brand filter is case insensitive`() {
        val stations = listOf(
            station(id = "1", brand = "eni"),
            station(id = "2", brand = "IP")
        )

        val result = GplViewModel.applyFiltersAndSort(stations, FilterParams(selectedBrand = "Eni"), 0.0, 0.0)

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `favorites only filter keeps just favorite stations`() {
        val stations = listOf(
            station(id = "1", isFavorite = true),
            station(id = "2", isFavorite = false)
        )

        val result = GplViewModel.applyFiltersAndSort(stations, FilterParams(filterFavoritesOnly = true), 0.0, 0.0)

        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `sort by price ascending orders cheapest first`() {
        val stations = listOf(
            station(id = "1", gplPrice = 0.75),
            station(id = "2", gplPrice = 0.68)
        )

        val result = GplViewModel.applyFiltersAndSort(stations, FilterParams(sortMode = SortMode.PRICE_ASC), 0.0, 0.0)

        assertEquals(listOf("2", "1"), result.map { it.id })
    }

    @Test
    fun `sort by distance never invents a position for stations without real coordinates`() {
        val stations = listOf(
            station(id = "far", latitude = 41.0, longitude = 15.0),
            station(id = "no-coords", latitude = null, longitude = null),
            station(id = "near", latitude = 40.6358, longitude = 14.4082)
        )

        val result = GplViewModel.applyFiltersAndSort(
            stations, FilterParams(sortMode = SortMode.DISTANCE), userLat = 40.6358, userLng = 14.4082
        )

        assertEquals("near", result.first().id)
        assertEquals("no-coords", result.last().id)
    }

    @Test
    fun `sort by brand is alphabetical for a person, not for the ASCII table`() {
        // Marchi copiati dall'anagrafica ufficiale della Campania, con le loro maiuscole irregolari.
        val stations = listOf(
            station(id = "toil", brand = "Toil"),
            station(id = "bpetrol", brand = "bpetrol"),
            station(id = "aps", brand = "APStazionidiServizio"),
            station(id = "agip", brand = "AgipEni")
        )

        val result = GplViewModel.applyFiltersAndSort(stations, FilterParams(sortMode = SortMode.BRAND), 0.0, 0.0)

        assertEquals(listOf("agip", "aps", "bpetrol", "toil"), result.map { it.id })
    }

    @Test
    fun `within the same brand the cheapest comes first`() {
        val stations = listOf(
            station(id = "q8-caro", brand = "Q8", gplPrice = 0.79),
            station(id = "q8-economico", brand = "Q8", gplPrice = 0.69),
            station(id = "esso", brand = "Esso", gplPrice = 0.74)
        )

        val result = GplViewModel.applyFiltersAndSort(stations, FilterParams(sortMode = SortMode.BRAND), 0.0, 0.0)

        assertEquals(listOf("esso", "q8-economico", "q8-caro"), result.map { it.id })
    }

    @Test
    fun `sort by name ignores case and accents`() {
        val stations = listOf(
            station(id = "3", name = "Àvila Carburanti"),
            station(id = "2", name = "beneco pompei"),
            station(id = "1", name = "AGIP Sorrento")
        )

        val result = GplViewModel.applyFiltersAndSort(stations, FilterParams(sortMode = SortMode.NAME), 0.0, 0.0)

        assertEquals(listOf("1", "3", "2"), result.map { it.id })
    }

    @Test
    fun `computeAvailableCities merges predefined provinces with detected cities without duplicates`() {
        val stations = listOf(station(id = "1", city = "Sorrento"), station(id = "2", city = "Sorrento"))

        val cities = GplViewModel.computeAvailableCities(stations)

        assertEquals(1, cities.count { it == "Sorrento" })
        assertTrue(cities.contains("Tutti"))
    }
}

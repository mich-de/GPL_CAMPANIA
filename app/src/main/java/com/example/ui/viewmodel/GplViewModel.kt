package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.BackendPreferences
import com.example.data.model.GplStation
import com.example.data.repository.GplRepository
import com.example.data.remote.DataFetchException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class SortMode {
    DISTANCE, PRICE_ASC, BRAND, NAME
}

data class FilterParams(
    val searchQuery: String = "",
    val selectedCity: String = "Tutti",
    val selectedBrand: String = "Tutti",
    val sortMode: SortMode = SortMode.DISTANCE,
    val filterFavoritesOnly: Boolean = false
)

data class UiDialogState(
    val selectedStationForDetail: GplStation? = null,
    val isCalculatorOpen: Boolean = false,
    val isAddStationOpen: Boolean = false,
    val stationToReportPrice: GplStation? = null,
    val isMapView: Boolean = false,
    val isScrapingLoading: Boolean = false,
    val statusMessage: String? = null,
    val isConnectionSettingsOpen: Boolean = false,
    val isLocationLoading: Boolean = false,
    val isCityFilterOpen: Boolean = false
)

data class GplUiState(
    val stations: List<GplStation> = emptyList(),
    val availableCities: List<String> = emptyList(),
    val searchQuery: String = "",
    val selectedCity: String = "Tutti",
    val selectedBrand: String = "Tutti",
    val sortMode: SortMode = SortMode.DISTANCE,
    val selectedStationForDetail: GplStation? = null,
    val isCalculatorOpen: Boolean = false,
    val isAddStationOpen: Boolean = false,
    val stationToReportPrice: GplStation? = null,
    val isMapView: Boolean = false,
    val userLat: Double = 40.6358,
    val userLng: Double = 14.4082,
    val filterFavoritesOnly: Boolean = false,
    val isScrapingLoading: Boolean = false,
    val statusMessage: String? = null,
    val isConnectionSettingsOpen: Boolean = false,
    val reporterName: String = "",
    val lastRefreshTimestamp: Long? = null,
    val isLocationLoading: Boolean = false,
    val isLocationManual: Boolean = false,
    val isCityFilterOpen: Boolean = false
)

class GplViewModel(private val repository: GplRepository, private val appContext: Context) : ViewModel() {

    private val _filterParams = MutableStateFlow(FilterParams())
    private val _uiDialogState = MutableStateFlow(UiDialogState())
    private val _reporterName = MutableStateFlow(BackendPreferences.getReporterName(appContext))
    private val _lastRefreshTimestamp = MutableStateFlow(BackendPreferences.getLastRefreshTimestamp(appContext))

    // Sorrento resta solo come centro-mappa iniziale finché non arriva una posizione reale (GPS o
    // inserita manualmente dall'utente) — se un'ultima posizione reale era già salvata, si riparte da quella.
    private val _userLocation = MutableStateFlow(BackendPreferences.getUserLocation(appContext) ?: Pair(40.6358, 14.4082))
    private val _isLocationManual = MutableStateFlow(BackendPreferences.isUserLocationManual(appContext))

    init {
        refreshStations()
    }

    /** Aggiorna la posizione utente con un fix GPS reale del device (mai una posizione inventata). */
    fun updateUserLocation(lat: Double, lng: Double) {
        BackendPreferences.setUserLocation(appContext, lat, lng, isManual = false)
        _isLocationManual.value = false
        _userLocation.value = Pair(lat, lng)
        _uiDialogState.value = _uiDialogState.value.copy(
            isLocationLoading = false,
            statusMessage = "Posizione GPS aggiornata (${"%.5f".format(lat)}, ${"%.5f".format(lng)})."
        )
    }

    /** L'utente sta per richiedere un fix GPS: mostra un caricamento finché non arriva successo/errore. */
    fun setLocationLoading(loading: Boolean) {
        _uiDialogState.value = _uiDialogState.value.copy(isLocationLoading = loading)
    }

    /** Il fix GPS reale non è arrivato (permesso negato, GPS spento, nessun segnale): nessuna posizione finta al suo posto. */
    fun onLocationUpdateFailed(reason: String) {
        _uiDialogState.value = _uiDialogState.value.copy(isLocationLoading = false, statusMessage = reason)
    }

    /** Posizione impostata a mano dall'utente (es. GPS indisponibile in casa): resta comunque una posizione reale, scelta da chi la usa. */
    fun setManualLocation(lat: Double, lng: Double) {
        BackendPreferences.setUserLocation(appContext, lat, lng, isManual = true)
        _isLocationManual.value = true
        _userLocation.value = Pair(lat, lng)
        _uiDialogState.value = _uiDialogState.value.copy(
            statusMessage = "Posizione impostata manualmente (${"%.5f".format(lat)}, ${"%.5f".format(lng)})."
        )
    }

    private val combinedFilterAndDialog = combine(_filterParams, _uiDialogState) { filters, dialogs ->
        Pair(filters, dialogs)
    }

    private val userLocationWithManualFlag = combine(_userLocation, _isLocationManual) { location, isManual ->
        Triple(location.first, location.second, isManual)
    }

    val uiState: StateFlow<GplUiState> = combine(
        repository.allStations,
        combinedFilterAndDialog,
        userLocationWithManualFlag,
        _reporterName,
        _lastRefreshTimestamp
    ) { rawStations, (filters, dialogs), userLocation, reporterName, lastRefreshTimestamp ->
        val userLat = userLocation.first
        val userLng = userLocation.second
        val isLocationManual = userLocation.third

        val availableCities = computeAvailableCities(rawStations)
        val filtered = applyFiltersAndSort(rawStations, filters, userLat, userLng)

        GplUiState(
            stations = filtered,
            availableCities = availableCities,
            searchQuery = filters.searchQuery,
            selectedCity = filters.selectedCity,
            selectedBrand = filters.selectedBrand,
            sortMode = filters.sortMode,
            selectedStationForDetail = dialogs.selectedStationForDetail,
            isCalculatorOpen = dialogs.isCalculatorOpen,
            isAddStationOpen = dialogs.isAddStationOpen,
            stationToReportPrice = dialogs.stationToReportPrice,
            isMapView = dialogs.isMapView,
            userLat = userLat,
            userLng = userLng,
            filterFavoritesOnly = filters.filterFavoritesOnly,
            isScrapingLoading = dialogs.isScrapingLoading,
            statusMessage = dialogs.statusMessage,
            isConnectionSettingsOpen = dialogs.isConnectionSettingsOpen,
            reporterName = reporterName,
            lastRefreshTimestamp = lastRefreshTimestamp,
            isLocationLoading = dialogs.isLocationLoading,
            isLocationManual = isLocationManual,
            isCityFilterOpen = dialogs.isCityFilterOpen
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GplUiState()
    )

    fun refreshStations(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiDialogState.value = _uiDialogState.value.copy(
                isScrapingLoading = true,
                statusMessage = "Aggiornamento dati dall'Osservaprezzi carburanti (MIMIT)..."
            )
            try {
                val count = repository.refreshStations(forceRefresh)
                val now = System.currentTimeMillis()
                BackendPreferences.setLastRefreshTimestamp(appContext, now)
                _lastRefreshTimestamp.value = now

                // La fonte ufficiale fornisce già le coordinate: al termine la lista è completa e
                // tutti i distributori sono posizionati, senza nessuna ricerca in background.
                _uiDialogState.value = _uiDialogState.value.copy(
                    isScrapingLoading = false,
                    statusMessage = "Aggiornati $count distributori GPL reali."
                )
            } catch (e: DataFetchException) {
                _uiDialogState.value = _uiDialogState.value.copy(
                    isScrapingLoading = false,
                    statusMessage = e.message
                )
            } catch (e: Exception) {
                _uiDialogState.value = _uiDialogState.value.copy(
                    isScrapingLoading = false,
                    statusMessage = "Errore imprevisto durante l'aggiornamento: ${e.message}"
                )
            }
        }
    }

    fun clearStatusMessage() {
        _uiDialogState.value = _uiDialogState.value.copy(statusMessage = null)
    }

    fun openConnectionSettings() {
        _uiDialogState.value = _uiDialogState.value.copy(isConnectionSettingsOpen = true)
    }

    fun closeConnectionSettings() {
        _uiDialogState.value = _uiDialogState.value.copy(isConnectionSettingsOpen = false)
    }

    fun openCityFilter() {
        _uiDialogState.value = _uiDialogState.value.copy(isCityFilterOpen = true)
    }

    fun closeCityFilter() {
        _uiDialogState.value = _uiDialogState.value.copy(isCityFilterOpen = false)
    }

    fun setReporterName(name: String) {
        BackendPreferences.setReporterName(appContext, name)
        _reporterName.value = name.trim()
    }

    fun setSearchQuery(query: String) {
        _filterParams.value = _filterParams.value.copy(searchQuery = query)
    }

    fun setSelectedCity(city: String) {
        _filterParams.value = _filterParams.value.copy(selectedCity = city)
        _uiDialogState.value = _uiDialogState.value.copy(isCityFilterOpen = false)
    }

    fun setSelectedBrand(brand: String) {
        _filterParams.value = _filterParams.value.copy(selectedBrand = brand)
    }

    fun setSortMode(mode: SortMode) {
        _filterParams.value = _filterParams.value.copy(sortMode = mode)
    }

    fun setFavoritesOnly(favOnly: Boolean) {
        _filterParams.value = _filterParams.value.copy(filterFavoritesOnly = favOnly)
    }

    fun setSelectedStation(station: GplStation?) {
        _uiDialogState.value = _uiDialogState.value.copy(selectedStationForDetail = station)
    }

    fun setCalculatorOpen(isOpen: Boolean) {
        _uiDialogState.value = _uiDialogState.value.copy(isCalculatorOpen = isOpen)
    }

    fun setAddStationOpen(isOpen: Boolean) {
        _uiDialogState.value = _uiDialogState.value.copy(isAddStationOpen = isOpen)
    }

    fun setStationToReportPrice(station: GplStation?) {
        _uiDialogState.value = _uiDialogState.value.copy(stationToReportPrice = station)
    }

    fun toggleMapView() {
        _uiDialogState.value = _uiDialogState.value.copy(isMapView = !_uiDialogState.value.isMapView)
    }

    fun toggleFavorite(station: GplStation) {
        viewModelScope.launch {
            repository.toggleFavorite(station.id, station.isFavorite)
        }
    }

    fun submitPriceUpdate(station: GplStation, newPrice: Double, reporterName: String, notes: String) {
        viewModelScope.launch {
            try {
                repository.updateGplPrice(station, newPrice, reporterName, notes)
                _uiDialogState.value = _uiDialogState.value.copy(
                    stationToReportPrice = null,
                    statusMessage = "Prezzo aggiornato sul dispositivo."
                )
            } catch (e: Exception) {
                _uiDialogState.value = _uiDialogState.value.copy(statusMessage = "Errore durante il salvataggio: ${e.message}")
            }
        }
    }

    fun importMyLpgPoi(content: String, isKml: Boolean = false) {
        viewModelScope.launch {
            repository.importMyLpgPoiFormat(content, isKml)
        }
    }

    fun addNewStation(
        provinciaSlug: String, name: String, brand: String, address: String, city: String,
        gplPrice: Double, openHours: String, phone: String, services: String
    ) {
        viewModelScope.launch {
            try {
                repository.addStation(provinciaSlug, name, brand, address, city, gplPrice, openHours, phone, services)
                _uiDialogState.value = _uiDialogState.value.copy(
                    isAddStationOpen = false,
                    statusMessage = "Distributore aggiunto (posizione verificata da OpenStreetMap)."
                )
            } catch (e: Exception) {
                _uiDialogState.value = _uiDialogState.value.copy(statusMessage = "Errore durante il salvataggio: ${e.message}")
            }
        }
    }

    fun launchGoogleMapsDirections(context: Context, station: GplStation) {
        // Clean name: remove trailing " (Comune)" appended al nome dell'impianto
        val cleanName = station.name
            .replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
            .trim()
        // Full query: "Nome Pompa, Indirizzo, Comune"
        val fullQuery = "$cleanName, ${station.address}, ${station.city}"
        val navUri = Uri.parse("google.navigation:q=${Uri.encode(fullQuery)}&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, navUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: browser Google Maps search
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(fullQuery)}")
            val fallbackIntent = Intent(Intent.ACTION_VIEW, webUri)
            context.startActivity(fallbackIntent)
        }
    }


    companion object {
        fun computeAvailableCities(stations: List<GplStation>): List<String> {
            val detectedCities = stations.map { it.city }.filter { it.isNotBlank() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
            val predefinedList = listOf("Tutti", "Napoli (Provincia)", "Salerno (Provincia)", "Caserta (Provincia)", "Avellino (Provincia)", "Benevento (Provincia)", "Penisola Sorrentina")
            return (predefinedList + detectedCities).distinct()
        }

        fun applyFiltersAndSort(stations: List<GplStation>, filters: FilterParams, userLat: Double, userLng: Double): List<GplStation> {
            val filtered = stations.filter { station ->
                val matchesQuery = filters.searchQuery.isBlank() ||
                        station.name.contains(filters.searchQuery, ignoreCase = true) ||
                        station.address.contains(filters.searchQuery, ignoreCase = true) ||
                        station.city.contains(filters.searchQuery, ignoreCase = true) ||
                        station.brand.contains(filters.searchQuery, ignoreCase = true)

                val matchesCity = when (filters.selectedCity) {
                    "Tutti" -> true
                    "Penisola Sorrentina" -> listOf("Sorrento", "Piano di Sorrento", "Sant'Agnello", "Meta", "Vico Equense", "Massa Lubrense").contains(station.city)
                    "Avellino (Provincia)", "Avellino" -> station.province.equals("AV", ignoreCase = true) || station.city.equals("Avellino", ignoreCase = true)
                    "Benevento (Provincia)", "Benevento" -> station.province.equals("BN", ignoreCase = true) || station.city.equals("Benevento", ignoreCase = true)
                    "Caserta (Provincia)", "Caserta" -> station.province.equals("CE", ignoreCase = true) || station.city.equals("Caserta", ignoreCase = true)
                    "Napoli (Provincia)", "Napoli" -> station.province.equals("NA", ignoreCase = true) || station.city.equals("Napoli", ignoreCase = true)
                    "Salerno (Provincia)", "Salerno" -> station.province.equals("SA", ignoreCase = true) || station.city.equals("Salerno", ignoreCase = true)
                    else -> station.city.equals(filters.selectedCity, ignoreCase = true)
                }

                val matchesBrand = filters.selectedBrand == "Tutti" || station.brand.equals(filters.selectedBrand, ignoreCase = true)
                val matchesFav = !filters.filterFavoritesOnly || station.isFavorite

                matchesQuery && matchesCity && matchesBrand && matchesFav
            }

            return when (filters.sortMode) {
                // Stazioni senza coordinate reali (geocoding non riuscito) vanno in coda, mai una distanza inventata.
                SortMode.DISTANCE -> filtered.sortedBy { calculateDistanceKm(userLat, userLng, it.latitude, it.longitude) ?: Double.MAX_VALUE }
                SortMode.PRICE_ASC -> filtered.sortedBy { it.gplPrice }
                SortMode.BRAND -> filtered.sortedBy { it.brand }
                SortMode.NAME -> filtered.sortedBy { it.name }
            }
        }

        /** Restituisce null se la stazione non ha coordinate reali (geocoding non riuscito) — mai una distanza inventata. */
        fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double?, lon2: Double?): Double? {
            if (lat2 == null || lon2 == null) return null
            val r = 6371.0 // Earth radius in km
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return (r * c * 10).toInt() / 10.0
        }

        fun Factory(repository: GplRepository, appContext: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GplViewModel(repository, appContext) as T
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AddStationDialog
import com.example.ui.components.CityFilterDialog
import com.example.ui.components.ConnectionSettingsDialog
import com.example.ui.components.GplItaliaSheet
import com.example.ui.components.MonitoringPanelDialog
import com.example.ui.components.GplCalculatorSheet
import com.example.ui.components.GplStationCard
import com.example.ui.components.InteractiveMapView
import com.example.ui.components.PriceReportDialog
import com.example.ui.components.StationDetailDialog
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.SorrentoBlue
import com.example.ui.viewmodel.GplUiState
import com.example.ui.viewmodel.GplViewModel
import com.example.ui.viewmodel.SortMode

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GplViewModel,
    modifier: Modifier = Modifier,
    onRequestLocationRefresh: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var isBrandFilterOpen by remember { mutableStateOf(false) }
    // Attivato solo quando l'utente invia una segnalazione di prezzo: evita che il primo
    // statusMessage generico (es. quello del refresh automatico all'avvio) faccia comparire
    // uno snackbar fuori contesto.
    var awaitingPriceReportFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage
        if (awaitingPriceReportFeedback && message != null) {
            awaitingPriceReportFeedback = false
            snackbarHostState.showSnackbar(message)
        }
    }

    // A tutto schermo il tasto indietro riporta all'elenco invece di chiudere l'app; se c'è una
    // scheda aperta, il primo indietro chiude quella. Il secondo handler è registrato dopo perché
    // in Compose ha la precedenza quando è attivo.
    BackHandler(enabled = uiState.isMapView) { viewModel.toggleMapView() }
    BackHandler(enabled = uiState.isMapView && uiState.mapFocusedStation != null) {
        viewModel.setMapFocus(null)
    }

    val citiesList = listOf(
        "Tutti",
        "Avellino",
        "Benevento",
        "Caserta",
        "Napoli",
        "Salerno",
        "Penisola Sorrentina",
        "Sorrento",
        "Piano di Sorrento",
        "Pozzuoli",
        "Acerra"
    )

    val brandsList = listOf(
        "Tutti", "Eni", "IP", "Beyfin", "Q8", "Pompe Bianche", "Tamoil"
    )

    Scaffold(
        modifier = modifier.testTag("home_screen_scaffold"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Sulla mappa la barra non compare: insieme a ricerca e filtri occupava un terzo dello
        // schermo, e su quel che restava lo zoom a due dita era scomodo. I comandi che servono
        // davvero sulla mappa stanno sulla mappa.
        topBar = {
            if (!uiState.isMapView) TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LocalGasStation,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GPL Campania",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        }
                        // Solo stazioni con un prezzo reale entrano nella media: quelle importate da
                        // POI senza prezzo (gplPrice = 0.0) non devono abbassarla artificialmente.
                        val pricedStations = uiState.stations.filter { it.gplPrice > 0.0 }
                        val liveAvg = if (pricedStations.isNotEmpty()) pricedStations.map { it.gplPrice }.average() else 0.0
                        val liveCount = uiState.stations.size
                        val lastRefreshText = uiState.lastRefreshTimestamp?.let { ts ->
                            "Aggiornato alle " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.ITALY).format(java.util.Date(ts))
                        } ?: "Dati salvati sul dispositivo"
                        Text(
                            text = if (liveCount > 0) "$liveCount impianti • Media € ${String.format("%.3f", liveAvg)}/L" else lastRefreshText,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    // Backend Sync Button
                    if (uiState.isScrapingLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp)
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.refreshStations() },
                            modifier = Modifier.testTag("refresh_official_data_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDownload,
                                contentDescription = "Aggiorna dal backend",
                                tint = Color.White
                            )
                        }
                    }

                    // "GPL in Italia": numeri nazionali, notizie ufficiali e scadenza serbatoio.
                    IconButton(
                        onClick = { viewModel.openItalia() },
                        modifier = Modifier.testTag("open_italia_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = "GPL in Italia",
                            tint = Color.White
                        )
                    }

                    // Connection Settings Button
                    IconButton(
                        onClick = { viewModel.openConnectionSettings() },
                        modifier = Modifier.testTag("connection_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Impostazioni connessione",
                            tint = Color.White
                        )
                    }

                    // View Toggle (Map / List)
                    IconButton(
                        onClick = { viewModel.toggleMapView() },
                        modifier = Modifier.testTag("view_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isMapView) Icons.Filled.ViewList else Icons.Filled.Map,
                            contentDescription = "Cambia Vista",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EcoGreenPrimary)
            )
        },
        floatingActionButton = {
            // Questi due pulsanti coprivano l'angolo in basso a destra della mappa, proprio dove
            // finisce il pollice quando si pizzica per ingrandire.
            if (!uiState.isMapView) Column(horizontalAlignment = Alignment.End) {
                // Calculator FAB
                FloatingActionButton(
                    onClick = { viewModel.setCalculatorOpen(true) },
                    containerColor = FlameOrange,
                    contentColor = Color.White,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .testTag("open_calculator_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Calculate, contentDescription = "Calcolatore")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Calcola Risparmio", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Add Station FAB
                FloatingActionButton(
                    onClick = { viewModel.setAddStationOpen(true) },
                    containerColor = EcoGreenPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_station_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Aggiungi Distributore")
                }
            }
        }
    ) { innerPadding ->

        if (uiState.isMapView) {
            // A tutto schermo: la mappa disegna anche sotto le barre di sistema, mentre i suoi
            // comandi restano dentro `innerPadding` per non finirci sotto.
            InteractiveMapView(
                stations = uiState.stations,
                userLat = uiState.userLat,
                userLng = uiState.userLng,
                // Toccare un pin evidenzia il distributore sulla mappa; la finestra di dettaglio
                // si apre solo se l'utente la chiede, altrimenti coprirebbe la mappa appena
                // toccata rendendo invisibile la scheda in fondo.
                focusedStation = uiState.mapFocusedStation,
                onFocusStation = { viewModel.setMapFocus(it) },
                onOpenDetail = { viewModel.setSelectedStation(it) },
                onDirectionsClick = { viewModel.launchGoogleMapsDirections(context, it) },
                contentPadding = innerPadding,
                onBackToList = { viewModel.toggleMapView() }
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Scraping Status Banner
            AnimatedVisibility(visible = uiState.statusMessage != null) {
                uiState.statusMessage?.let { msg ->
                    Surface(
                        color = SorrentoBlue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearStatusMessage() },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Chiudi",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Search & Filter Header Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EcoGreenPrimary)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Search Input Box
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Cerca distributore, città o via...", color = Color.Gray, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Cerca", tint = EcoGreenPrimary) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Pulisci", tint = Color.Gray)
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        // Il container resta bianco fisso in entrambi i temi: il colore del testo
                        // deve restare scuro fisso, altrimenti in dark mode eredita il testo chiaro
                        // del tema e diventa illeggibile su sfondo bianco.
                        focusedTextColor = Color(0xFF0C1526),
                        unfocusedTextColor = Color(0xFF0C1526),
                        cursorColor = EcoGreenPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("search_station_input")
                )

                // Feedback immediato: rende visibile che la ricerca sta davvero filtrando la lista
                if (uiState.searchQuery.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (uiState.stations.isEmpty())
                            "Nessun distributore trovato per \"${uiState.searchQuery}\""
                        else
                            "${uiState.stations.size} risultat${if (uiState.stations.size == 1) "o" else "i"} per \"${uiState.searchQuery}\"",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("search_result_count")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pulsante compatto che apre la lista filtrabile di comuni (prima: riga orizzontale
                // con una chip per ognuno dei comuni rilevati, troppo lunga da scorrere), affiancato
                // dal filtro marchio — che prima esisteva solo nel ViewModel, senza alcun modo per
                // raggiungerlo dall'interfaccia.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { viewModel.openCityFilter() },
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_city_filter")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.LocationCity, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text(uiState.selectedCity, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Cambia luogo", tint = Color.White)
                        }
                    }

                    Surface(
                        onClick = { isBrandFilterOpen = true },
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_brand_filter")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.LocalGasStation, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text(uiState.selectedBrand, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Cambia marchio", tint = Color.White)
                        }
                    }
                }
            }

            // Dedicated Primary Sort Tabs Bar: "Ordinate per Prezzo" & "Ordinate per Distanza"
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sort: Price ASC (Cheapest)
                    FilterChip(
                        selected = uiState.sortMode == SortMode.PRICE_ASC,
                        onClick = { viewModel.setSortMode(SortMode.PRICE_ASC) },
                        leadingIcon = { Icon(Icons.Filled.TrendingDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Più economici", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FlameOrange,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("sort_price_chip")
                    )

                    // Sort: Distance
                    FilterChip(
                        selected = uiState.sortMode == SortMode.DISTANCE,
                        onClick = { viewModel.setSortMode(SortMode.DISTANCE) },
                        leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Più vicini", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SorrentoBlue,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("sort_distance_chip")
                    )

                    // Sort: Brand
                    FilterChip(
                        selected = uiState.sortMode == SortMode.BRAND,
                        onClick = { viewModel.setSortMode(SortMode.BRAND) },
                        label = { Text("Marchio", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("sort_brand_chip")
                    )

                    // Sort: nome del distributore. SortMode.NAME esisteva già nel ViewModel ma
                    // nessun comando lo raggiungeva.
                    FilterChip(
                        selected = uiState.sortMode == SortMode.NAME,
                        onClick = { viewModel.setSortMode(SortMode.NAME) },
                        label = { Text("Nome", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("sort_name_chip")
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Favorites Only Toggle Chip
                    FilterChip(
                        selected = uiState.filterFavoritesOnly,
                        onClick = { viewModel.setFavoritesOnly(!uiState.filterFavoritesOnly) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (uiState.filterFavoritesOnly) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Preferiti", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EcoGreenPrimary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        ),
                        modifier = Modifier.testTag("filter_favorites_chip")
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // Elenco dei distributori (la mappa vive a tutto schermo, sopra).
            run {
                if (uiState.stations.isEmpty()) {
                    // Lista vuota per due motivi diversi: filtri troppo stretti (l'utente cambia i
                    // filtri) o nessun dato mai scaricato/fallito (l'utente deve ritentare).
                    val hasActiveFilters = uiState.searchQuery.isNotBlank() ||
                        uiState.selectedCity != "Tutti" ||
                        uiState.selectedBrand != "Tutti" ||
                        uiState.filterFavoritesOnly
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.LocalGasStation,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (hasActiveFilters)
                                    "Nessun distributore GPL trovato con i filtri selezionati."
                                else
                                    "Impossibile caricare i distributori. Verifica la connessione e riprova.",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            if (!hasActiveFilters) {
                                Spacer(modifier = Modifier.height(12.dp))
                                androidx.compose.material3.Button(
                                    onClick = { viewModel.refreshStations() },
                                    modifier = Modifier.testTag("empty_state_retry_button")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Riprova")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("stations_lazy_list"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ── TOP 5 Cheapest horizontal scroll ───────────────
                        // Le stazioni senza prezzo reale (import POI, gplPrice = 0.0) non sono
                        // "le più economiche": vanno escluse da media e classifica, non solo mostrate
                        // come N/D nella card.
                        val stationsWithRealPrice = uiState.stations.filter { it.gplPrice > 0.0 }
                        val allAvgPrice = if (stationsWithRealPrice.isNotEmpty()) stationsWithRealPrice.map { it.gplPrice }.average() else 0.0
                        val top5 = stationsWithRealPrice.sortedBy { it.gplPrice }.take(5)
                        if (top5.isNotEmpty()) {
                            item {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.TrendingDown, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Top 5 Più Economici", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = FlameOrange)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(end = 4.dp)
                                    ) {
                                        items(top5, key = { "top5_${it.id}" }) { s ->
                                            val dist = GplViewModel.calculateDistanceKm(uiState.userLat, uiState.userLng, s.latitude, s.longitude)
                                            val savPct = if (allAvgPrice > 0 && s.gplPrice < allAvgPrice) ((allAvgPrice - s.gplPrice) / allAvgPrice * 100).toInt() else 0
                                            Card(
                                                onClick = { viewModel.setSelectedStation(s) },
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                elevation = CardDefaults.cardElevation(2.dp),
                                                modifier = Modifier.width(160.dp).testTag("top5_card_${s.id}")
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(com.example.ui.components.formatBrandLabel(s.brand), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = SorrentoBlue, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Text(String.format(java.util.Locale.ITALY, "€ %.3f", s.gplPrice), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = com.example.ui.theme.PriceBadgeGreen, maxLines = 1)
                                                    Text("/L GPL", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(s.city, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                    Text(dist?.let { String.format(java.util.Locale.ITALY, "%.1f km", it) } ?: "distanza n.d.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                    if (savPct > 0) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Surface(shape = RoundedCornerShape(6.dp), color = com.example.ui.theme.SavingsBadgeBg) {
                                                            Text("-$savPct%", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.SavingsBadgeFg)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // List of Stations
                        items(
                            items = uiState.stations,
                            key = { it.id }
                        ) { station ->
                            val dist = GplViewModel.calculateDistanceKm(
                                uiState.userLat, uiState.userLng,
                                station.latitude, station.longitude
                            )

                            GplStationCard(
                                station = station,
                                distanceKm = dist,
                                averagePrice = allAvgPrice,
                                onCardClick = { viewModel.setSelectedStation(station) },
                                onFavoriteClick = {
                                    val wasFavorite = station.isFavorite
                                    viewModel.toggleFavorite(station)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (wasFavorite) "Rimosso dai preferiti" else "Aggiunto ai preferiti"
                                        )
                                    }
                                },
                                onDirectionsClick = { viewModel.launchGoogleMapsDirections(context, station) },
                                onReportPriceClick = { viewModel.setStationToReportPrice(station) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheets & Dialogs
    uiState.selectedStationForDetail?.let { station ->
        val detailAvg = uiState.stations.filter { it.gplPrice > 0.0 }.let { priced ->
            if (priced.isNotEmpty()) priced.map { it.gplPrice }.average() else 0.0
        }
        StationDetailDialog(
            station = station,
            viewModel = viewModel,
            averagePrice = detailAvg,
            onDismiss = { viewModel.setSelectedStation(null) },
            onDirectionsClick = { viewModel.launchGoogleMapsDirections(context, station) },
            onReportPriceClick = {
                viewModel.setSelectedStation(null)
                viewModel.setStationToReportPrice(station)
            }
        )
    }

    if (isBrandFilterOpen) {
        BrandFilterDialog(
            brands = brandsList,
            selectedBrand = uiState.selectedBrand,
            onBrandSelected = { brand ->
                viewModel.setSelectedBrand(brand)
                isBrandFilterOpen = false
            },
            onDismiss = { isBrandFilterOpen = false }
        )
    }

    uiState.stationToReportPrice?.let { station ->
        PriceReportDialog(
            station = station,
            initialReporterName = uiState.reporterName,
            onDismiss = { viewModel.setStationToReportPrice(null) },
            onSubmitPrice = { newPrice, reporter, notes ->
                awaitingPriceReportFeedback = true
                viewModel.submitPriceUpdate(station, newPrice, reporter, notes)
            }
        )
    }

    if (uiState.isCalculatorOpen) {
        GplCalculatorSheet(
            avgGplPrice = uiState.stations.filter { it.gplPrice > 0.0 }.let { priced ->
                if (priced.isNotEmpty()) priced.map { it.gplPrice }.average() else 0.715
            },
            onDismiss = { viewModel.setCalculatorOpen(false) }
        )
    }

    if (uiState.isAddStationOpen) {
        AddStationDialog(
            onDismiss = { viewModel.setAddStationOpen(false) },
            onAddStation = { provinciaSlug, name, brand, address, city, price, hours, phone, services ->
                viewModel.addNewStation(provinciaSlug, name, brand, address, city, price, hours, phone, services)
            }
        )
    }

    if (uiState.isCityFilterOpen) {
        CityFilterDialog(
            cities = uiState.availableCities.ifEmpty { listOf("Tutti") },
            selectedCity = uiState.selectedCity,
            onCitySelected = { city -> viewModel.setSelectedCity(city) },
            onDismiss = { viewModel.closeCityFilter() }
        )
    }

    if (uiState.isConnectionSettingsOpen) {
        ConnectionSettingsDialog(
            reporterName = uiState.reporterName,
            lastRefreshTimestamp = uiState.lastRefreshTimestamp,
            currentLat = uiState.userLat,
            currentLng = uiState.userLng,
            isLocationManual = uiState.isLocationManual,
            isLocationLoading = uiState.isLocationLoading,
            onDismiss = { viewModel.closeConnectionSettings() },
            onSaveReporterName = { name -> viewModel.setReporterName(name) },
            onRequestLocationRefresh = onRequestLocationRefresh,
            onForceRefreshBackend = { viewModel.refreshStations(forceRefresh = true) },
            onOpenMonitoring = { viewModel.openMonitoringPanel() },
            onSetManualLocation = { lat, lng -> viewModel.setManualLocation(lat, lng) }
        )
    }

    if (uiState.italia.isOpen) {
        GplItaliaSheet(
            state = uiState.italia,
            onDismiss = { viewModel.closeItalia() },
            onSelectTab = { tab -> viewModel.setItaliaTab(tab) },
            onRefreshStats = { force -> viewModel.refreshNationalStats(force) },
            onRefreshNews = { viewModel.refreshNews() },
            onSaveTank = { revision -> viewModel.saveTankRevision(revision) },
            onClearTank = { viewModel.clearTankRevision() }
        )
    }

    if (uiState.isMonitoringOpen) {
        MonitoringPanelDialog(
            report = uiState.monitoringReport,
            isRefreshing = uiState.isScrapingLoading,
            onDismiss = { viewModel.closeMonitoringPanel() },
            onForceRefresh = { viewModel.refreshStations(forceRefresh = true) },
            onInvalidateCache = { viewModel.invalidateCacheTtl() }
        )
    }
}

/**
 * Filtro marchio: stesso pattern visivo del filtro comune ([CityFilterDialog]), ma senza campo di
 * ricerca perché l'elenco marchi è breve e fisso — non serve filtrarlo.
 */
@Composable
private fun BrandFilterDialog(
    brands: List<String>,
    selectedBrand: String,
    onBrandSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EcoGreenPrimary)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.LocalGasStation, contentDescription = null, tint = Color.White)
                    Text(
                        text = "Filtra per marchio",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(brands) { brand ->
                        val isSelected = selectedBrand.equals(brand, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) SorrentoBlue.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable(onClick = { onBrandSelected(brand) })
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                                .testTag("brand_filter_option_$brand"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = brand,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) SorrentoBlue else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = SorrentoBlue)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Chiudi")
                    }
                }
            }
        }
    }
}

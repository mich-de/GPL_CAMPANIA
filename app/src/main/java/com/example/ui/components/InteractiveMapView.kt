package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.model.GplStation
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.SorrentoBlue
import com.example.ui.viewmodel.GplViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.ScaleBarOverlay
import java.io.File
import java.util.Locale

private val TIER_GREEN = Color(0xFF2E7D32)
private val TIER_RED = Color(0xFFD32F2F)
private val TIER_GREY = Color(0xFF607D8B)

private fun PriceTier.color(): Color = when (this) {
    PriceTier.ECONOMICO -> TIER_GREEN
    PriceTier.MEDIO -> SorrentoBlue
    PriceTier.CARO -> TIER_RED
    PriceTier.NEUTRA -> TIER_GREY
}

private fun euro(price: Double): String = String.format(Locale.ITALY, "%.3f", price)

/** Genera un pin circolare pieno con bordo bianco (dimensione maggiore se selezionato). */
private fun createStationDotBitmap(colorArgb: Int, isSelected: Boolean, density: Float): Bitmap {
    val radiusPx = (if (isSelected) 15f else 9f) * density
    val borderPx = density * 2f
    val size = ((radiusPx + borderPx) * 2).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f

    // Alone tenue solo sul pin selezionato: lo stacca dai vicini quando i pin si sovrappongono.
    if (isSelected) {
        canvas.drawCircle(center, center, radiusPx + borderPx, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            alpha = 70
        })
    }
    canvas.drawCircle(center, center, radiusPx, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb })
    canvas.drawCircle(center, center, radiusPx, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = borderPx
    })
    canvas.drawCircle(center, center, radiusPx * 0.32f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    })
    return bitmap
}

/**
 * Pin del distributore evidenziato, con il prezzo scritto sopra.
 *
 * L'etichetta è solo sul pin selezionato: stamparla su tutti e 428 significherebbe centinaia di
 * bitmap diverse in memoria e una mappa illeggibile appena si allarga lo zoom.
 */
private fun createSelectedPinBitmap(colorArgb: Int, price: Double, density: Float): Bitmap {
    val dot = createStationDotBitmap(colorArgb, isSelected = true, density = density)
    val label = "€ ${euro(price)}"
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 12f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val bounds = Rect()
    textPaint.getTextBounds(label, 0, label.length, bounds)

    val padH = 6f * density
    val padV = 3f * density
    val badgeW = bounds.width() + padH * 2
    val badgeH = bounds.height() + padV * 2
    val gap = 3f * density

    val width = maxOf(badgeW, dot.width.toFloat()).toInt().coerceAtLeast(1)
    // Sotto al pallino si lascia lo stesso spazio che l'etichetta occupa sopra: così il centro della
    // bitmap coincide con il centro del pallino e l'ancoraggio resta quello di tutti gli altri pin,
    // senza che il punto disegnato scivoli via dalle coordinate reali del distributore.
    val topSpace = badgeH + gap
    val height = ((topSpace + dot.height / 2f) * 2).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centerX = width / 2f

    val badgeLeft = centerX - badgeW / 2f
    canvas.drawRoundRect(
        badgeLeft, 0f, badgeLeft + badgeW, badgeH, 4f * density, 4f * density,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb }
    )
    canvas.drawText(label, centerX - bounds.width() / 2f - bounds.left, padV - bounds.top, textPaint)
    canvas.drawBitmap(dot, centerX - dot.width / 2f, topSpace, null)
    return bitmap
}

/** Marker della posizione utente reale: cerchio blu con alone. */
private fun createUserLocationBitmap(colorArgb: Int, density: Float): Bitmap {
    val radiusPx = 7f * density
    val haloRadiusPx = 18f * density
    val size = (haloRadiusPx * 2).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f

    canvas.drawCircle(center, center, haloRadiusPx, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        alpha = 60
    })
    canvas.drawCircle(center, center, radiusPx + density * 2, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    })
    canvas.drawCircle(center, center, radiusPx, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb })
    return bitmap
}

/**
 * Riquadro che contiene tutti i distributori posizionati, con un margine per non incollarli ai bordi.
 * `null` se non c'è niente da inquadrare.
 */
private fun boundingBoxOf(stations: List<GplStation>): BoundingBox? {
    val points = stations.mapNotNull { s ->
        val lat = s.latitude ?: return@mapNotNull null
        val lng = s.longitude ?: return@mapNotNull null
        lat to lng
    }
    if (points.isEmpty()) return null

    val north = points.maxOf { it.first }
    val south = points.minOf { it.first }
    val east = points.maxOf { it.second }
    val west = points.minOf { it.second }
    // Con un solo distributore il riquadro sarebbe un punto: si allarga di ~1 km per avere una vista.
    val pad = maxOf((north - south) * 0.12, (east - west) * 0.12, 0.01)
    return BoundingBox(north + pad, east + pad, south - pad, west - pad)
}

/**
 * @param contentPadding spazio occupato dalle barre di sistema. La mappa ci disegna sotto — a tutto
 *   schermo lo spazio è lo strumento di lavoro principale — mentre i comandi si tengono dentro
 *   questo margine per non finire sotto l'orologio o sulla barra di navigazione.
 * @param onBackToList se presente, mostra il comando per tornare all'elenco: a tutto schermo la
 *   barra in alto non c'è più, e senza questo la mappa sarebbe senza uscita.
 */
@Composable
fun InteractiveMapView(
    stations: List<GplStation>,
    userLat: Double,
    userLng: Double,
    focusedStation: GplStation?,
    onFocusStation: (GplStation?) -> Unit,
    onOpenDetail: (GplStation) -> Unit,
    onDirectionsClick: (GplStation) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onBackToList: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // La densità reale del device: fissarla a 3 rendeva i pin sproporzionati su qualsiasi schermo
    // con una densità diversa da quella di sviluppo.
    val density = context.resources.displayMetrics.density

    remember {
        Configuration.getInstance().apply {
            // osmdroid legge da qui i limiti della cache su disco: senza `load` restano ai valori
            // non inizializzati e la cache delle tile si comporta in modo imprevedibile.
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            // La tile usage policy di OSM richiede uno User-Agent che identifichi l'applicazione:
            // il solo package name non basta a rintracciare il progetto in caso di abusi.
            userAgentValue = "GPLCampaniaApp/1.0 (+https://github.com/mich-de/GPL_CAMPANIA)"
            val basePath = File(context.applicationContext.cacheDir, "osmdroid")
            osmdroidBasePath = basePath
            osmdroidTileCache = File(basePath, "tiles")
        }
        true
    }

    val mapViewRef = remember { mutableMapOf<String, MapView>() }
    val stationMarkers = remember { mutableMapOf<String, Marker>() }
    /** Chiave dell'icona già applicata a ogni pin: evita di ricostruire 428 bitmap a ogni update. */
    val appliedIconKeys = remember { mutableMapOf<String, String>() }
    val iconCache = remember { mutableMapOf<String, BitmapDrawable>() }
    val userMarkerRef = remember { mutableMapOf<String, Marker>() }
    val cameraState = remember { mutableMapOf<String, Any>() }

    // A tutto schermo la mappa arriva sotto la barra di navigazione: attribuzione e barra della
    // scala vengono alzate di altrettanto, altrimenti finiscono sotto e l'attribuzione OSM — che è
    // una condizione d'uso delle tile — resterebbe illeggibile.
    val bottomInsetPx = with(LocalDensity.current) { contentPadding.calculateBottomPadding().roundToPx() }

    val tiers = remember(stations) { computePriceTiers(stations.map { it.gplPrice }) }
    val positioned = remember(stations) { stations.count { it.latitude != null && it.longitude != null } }

    fun fitToStations() {
        val map = mapViewRef["map"] ?: return
        boundingBoxOf(stations)?.let { map.zoomToBoundingBox(it, true, (24 * density).toInt()) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("interactive_map_view")
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    // Su uno schermo ad alta densità le tile a dimensione nativa risultano minuscole
                    // e le etichette illeggibili: questo le scala alla densità reale.
                    isTilesScaledToDpi = true
                    minZoomLevel = 7.0
                    maxZoomLevel = 19.0
                    controller.setZoom(12.0)
                    controller.setCenter(GeoPoint(userLat, userLng))
                    cameraState["center"] = GeoPoint(userLat, userLng)
                    mapViewRef["map"] = this

                    // Va aggiunto per primo: osmdroid propone il tocco agli overlay dall'ultimo al
                    // primo, così i pin hanno la precedenza e questo raccoglie solo i tocchi a vuoto.
                    overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            onFocusStation(null)
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    }))

                    // Attribuzione OpenStreetMap: è una condizione d'uso delle tile, non un ornamento.
                    overlays.add(CopyrightOverlay(ctx).apply {
                        setOffset((12 * density).toInt(), bottomInsetPx + (10 * density).toInt())
                    })
                    overlays.add(ScaleBarOverlay(this).apply {
                        setAlignBottom(true)
                        setScaleBarOffset((12 * density).toInt(), bottomInsetPx + (36 * density).toInt())
                    })

                    val userMarker = Marker(this).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = BitmapDrawable(ctx.resources, createUserLocationBitmap(SorrentoBlue.toArgb(), density))
                        title = "La tua posizione"
                    }
                    overlays.add(userMarker)
                    userMarkerRef["user"] = userMarker
                }
            },
            update = { mapView ->
                val newUserLocation = GeoPoint(userLat, userLng)
                userMarkerRef["user"]?.position = newUserLocation

                // La posizione reale arriva in modo asincrono dopo che la mappa è già stata creata
                // sull'ultimo punto noto: si ricentra una volta sola, alla prima posizione diversa.
                // Ricentrare a ogni aggiornamento GPS strapperebbe la mappa di mano all'utente
                // mentre la sta spostando; da lì in poi la camera è sua, e il pulsante dedicato
                // resta il modo per tornare sulla propria posizione.
                if (cameraState["recentered"] != true && cameraState["center"] != newUserLocation) {
                    mapView.controller.animateTo(newUserLocation)
                    cameraState["center"] = newUserLocation
                    cameraState["recentered"] = true
                }

                val currentIds = mutableSetOf<String>()
                stations.forEach { station ->
                    val lat = station.latitude ?: return@forEach
                    val lng = station.longitude ?: return@forEach
                    currentIds += station.id
                    val isSelected = focusedStation?.id == station.id
                    val color = (if (isSelected) FlameOrange else tiers.tierOf(station.gplPrice).color()).toArgb()

                    val marker = stationMarkers.getOrPut(station.id) {
                        Marker(mapView).also {
                            it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            mapView.overlays.add(it)
                        }
                    }
                    marker.position = GeoPoint(lat, lng)

                    val iconKey = if (isSelected) "sel-$color-${euro(station.gplPrice)}" else "dot-$color"
                    if (appliedIconKeys[station.id] != iconKey) {
                        marker.icon = iconCache.getOrPut(iconKey) {
                            val bitmap = if (isSelected) {
                                createSelectedPinBitmap(color, station.gplPrice, density)
                            } else {
                                createStationDotBitmap(color, isSelected = false, density = density)
                            }
                            BitmapDrawable(mapView.context.resources, bitmap)
                        }
                        // Il pin selezionato è più grande degli altri: senza questo resta sotto ai
                        // vicini disegnati dopo di lui e la selezione sembra non aver funzionato.
                        if (isSelected) {
                            mapView.overlays.remove(marker)
                            mapView.overlays.add(marker)
                        }
                        appliedIconKeys[station.id] = iconKey
                    }
                    marker.title = "${station.name} — € ${euro(station.gplPrice)}/L"
                    marker.setOnMarkerClickListener { _, _ ->
                        onFocusStation(station)
                        true
                    }
                }

                val staleIds = stationMarkers.keys - currentIds
                staleIds.forEach { id ->
                    stationMarkers.remove(id)?.let {
                        mapView.overlays.remove(it)
                        it.onDetach(mapView)
                    }
                    appliedIconKeys.remove(id)
                }

                // Il pin toccato può finire fuori schermo (arriva dalla lista, o la mappa è stata
                // spostata): ci si avvicina solo in quel caso, mai se è già in vista.
                focusedStation?.let { station ->
                    val lat = station.latitude
                    val lng = station.longitude
                    if (lat != null && lng != null && !mapView.boundingBox.contains(GeoPoint(lat, lng))) {
                        mapView.controller.animateTo(GeoPoint(lat, lng))
                    }
                }

                mapView.invalidate()
            }
        )

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapViewRef["map"]?.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapViewRef["map"]?.onPause()
                    else -> Unit
                }
            }
            // La mappa nasce a schermo già visibile: senza questa chiamata `onResume` non arriva mai
            // e il download delle tile parte solo dopo un ritorno dallo sfondo.
            mapViewRef["map"]?.onResume()
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapViewRef["map"]?.onDetach()
                stationMarkers.clear()
                appliedIconKeys.clear()
                iconCache.clear()
                mapViewRef.clear()
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(contentPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            onBackToList?.let { back ->
                FloatingActionButton(
                    onClick = back,
                    modifier = Modifier
                        .size(42.dp)
                        .testTag("map_back_to_list_button"),
                    containerColor = Color.White,
                    contentColor = EcoGreenPrimary
                ) {
                    Icon(Icons.Filled.ViewList, contentDescription = "Torna all'elenco")
                }
            }

            MapLegend(
                tiers = tiers,
                shownCount = stations.size,
                positionedCount = positioned
            )
        }

        // Comandi mappa: zoom, inquadratura di tutti i distributori, ritorno sulla propria posizione.
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(contentPadding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { mapViewRef["map"]?.controller?.zoomIn() },
                modifier = Modifier
                    .size(42.dp)
                    .testTag("zoom_in_button"),
                containerColor = Color.White,
                contentColor = SorrentoBlue
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom in")
            }

            FloatingActionButton(
                onClick = { mapViewRef["map"]?.controller?.zoomOut() },
                modifier = Modifier
                    .size(42.dp)
                    .testTag("zoom_out_button"),
                containerColor = Color.White,
                contentColor = SorrentoBlue
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
            }

            FloatingActionButton(
                onClick = { fitToStations() },
                modifier = Modifier
                    .size(42.dp)
                    .testTag("fit_bounds_button"),
                containerColor = Color.White,
                contentColor = SorrentoBlue
            ) {
                Icon(Icons.Filled.ZoomOutMap, contentDescription = "Inquadra tutti i distributori")
            }

            FloatingActionButton(
                onClick = {
                    mapViewRef["map"]?.controller?.animateTo(GeoPoint(userLat, userLng))
                    mapViewRef["map"]?.controller?.setZoom(13.0)
                },
                modifier = Modifier
                    .size(42.dp)
                    .testTag("recenter_button"),
                containerColor = SorrentoBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Ricentra sulla mia posizione")
            }
        }

        focusedStation?.let { station ->
            MapStationCard(
                station = station,
                tier = tiers.tierOf(station.gplPrice),
                userLat = userLat,
                userLng = userLng,
                onClose = { onFocusStation(null) },
                onDirectionsClick = { onDirectionsClick(station) },
                onOpenDetail = { onOpenDetail(station) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(contentPadding)
                    .padding(12.dp)
            )
        }
    }
}

/**
 * Legenda dei colori con le soglie realmente in uso.
 *
 * Senza i numeri i tre colori non dicono niente di verificabile; con i numeri l'utente può leggere
 * un pin verde come "sotto 0,712 €/L fra quelli che sto vedendo" e controllarlo da sé.
 */
@Composable
private fun MapLegend(
    tiers: PriceTiers,
    shownCount: Int,
    positionedCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("map_legend"),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = when {
                    shownCount == 0 -> "Nessun distributore da mostrare"
                    positionedCount < shownCount ->
                        "$positionedCount di $shownCount distributori sulla mappa"
                    else -> "$shownCount distributori GPL"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (tiers.isMeaningful) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(PriceTier.ECONOMICO.color(), "≤ ${euro(tiers.cheapMax!!)}")
                    Spacer(modifier = Modifier.width(10.dp))
                    LegendDot(PriceTier.MEDIO.color(), "medi")
                    Spacer(modifier = Modifier.width(10.dp))
                    LegendDot(PriceTier.CARO.color(), "≥ ${euro(tiers.priceyMin!!)}")
                }
                Text(
                    text = "soglie sui prezzi in lista, in €/L",
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            } else if (shownCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    // Colorare per fascia con pochi prezzi darebbe una graduatoria che i dati non
                    // sostengono: meglio un colore neutro e dirlo.
                    text = "troppo pochi prezzi per un confronto",
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Scheda del distributore toccato sulla mappa. */
@Composable
private fun MapStationCard(
    station: GplStation,
    tier: PriceTier,
    userLat: Double,
    userLng: Double,
    onClose: () -> Unit,
    onDirectionsClick: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dist = GplViewModel.calculateDistanceKm(userLat, userLng, station.latitude, station.longitude)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("map_station_selected_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = station.brand,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoGreenPrimary
                    )
                    Text(
                        text = station.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = station.address,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "${station.city} • ${dist?.let { "$it km da te" } ?: "distanza non disponibile"}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("map_card_close_button")
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Chiudi la scheda",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = tier.color().copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "€ ${euro(station.gplPrice)}/L",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = tier.color()
                        )
                    }
                    if (tier != PriceTier.NEUTRA) {
                        Text(
                            text = when (tier) {
                                PriceTier.ECONOMICO -> "fra i più bassi"
                                PriceTier.CARO -> "fra i più alti"
                                else -> "in media"
                            },
                            fontSize = 9.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = onDirectionsClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("map_directions_button"),
                    containerColor = SorrentoBlue,
                    contentColor = Color.White
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Indicazioni", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                FloatingActionButton(
                    onClick = onOpenDetail,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("map_details_button"),
                    containerColor = Color(0xFFF1F5F9),
                    contentColor = Color.DarkGray
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dettagli", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

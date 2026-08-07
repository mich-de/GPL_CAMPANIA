package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/** Genera un pin circolare pieno con bordo bianco (dimensione maggiore se selezionato). */
private fun createStationDotBitmap(colorArgb: Int, isSelected: Boolean): Bitmap {
    val radiusDp = if (isSelected) 16f else 11f
    val density = 3f // fixed density fattore per nitidezza a prescindere dal device
    val radiusPx = radiusDp * density
    val size = (radiusPx * 2 + 6).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = density * 1.5f
    }

    canvas.drawCircle(center, center, radiusPx, fillPaint)
    canvas.drawCircle(center, center, radiusPx, borderPaint)
    canvas.drawCircle(center, center, radiusPx * 0.35f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    })
    return bitmap
}

/** Marker della posizione utente reale: cerchio blu con alone, stesso stile della vecchia mappa Canvas. */
private fun createUserLocationBitmap(colorArgb: Int): Bitmap {
    val density = 3f
    val radiusPx = 8f * density
    val haloRadiusPx = 20f * density
    val size = (haloRadiusPx * 2).toInt()
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
    canvas.drawCircle(center, center, radiusPx, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
    })
    return bitmap
}

private fun priceTierColor(price: Double, isSelected: Boolean): Color = when {
    isSelected -> FlameOrange
    price <= 0.705 -> Color(0xFF2E7D32)
    price <= 0.725 -> SorrentoBlue
    else -> Color(0xFFD32F2F)
}

@Composable
fun InteractiveMapView(
    stations: List<GplStation>,
    userLat: Double,
    userLng: Double,
    selectedStation: GplStation?,
    onStationSelect: (GplStation) -> Unit,
    onDirectionsClick: (GplStation) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember {
        Configuration.getInstance().apply {
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
    val userMarkerRef = remember { mutableMapOf<String, Marker>() }
    val centeredLocationRef = remember { mutableMapOf<String, GeoPoint>() }

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
                    minZoomLevel = 7.0
                    maxZoomLevel = 19.0
                    controller.setZoom(12.0)
                    controller.setCenter(GeoPoint(userLat, userLng))
                    centeredLocationRef["center"] = GeoPoint(userLat, userLng)
                    mapViewRef["map"] = this

                    val userMarker = Marker(this).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = BitmapDrawable(ctx.resources, createUserLocationBitmap(SorrentoBlue.toArgb()))
                        title = "La tua posizione"
                    }
                    overlays.add(userMarker)
                    userMarkerRef["user"] = userMarker
                }
            },
            update = { mapView ->
                val newUserLocation = GeoPoint(userLat, userLng)
                userMarkerRef["user"]?.position = newUserLocation

                // La posizione reale arriva in modo asincrono da FusedLocationProviderClient, dopo
                // che la mappa è già stata creata sul fallback Sorrento: quando cambia, ricentra la
                // camera così l'utente vede concretamente che la sua posizione è stata usata.
                if (centeredLocationRef["center"] != newUserLocation) {
                    mapView.controller.animateTo(newUserLocation)
                    centeredLocationRef["center"] = newUserLocation
                }

                val currentIds = mutableSetOf<String>()
                stations.forEach { station ->
                    val lat = station.latitude ?: return@forEach
                    val lng = station.longitude ?: return@forEach
                    currentIds += station.id
                    val isSelected = selectedStation?.id == station.id
                    val color = priceTierColor(station.gplPrice, isSelected).toArgb()

                    val marker = stationMarkers.getOrPut(station.id) {
                        Marker(mapView).also { mapView.overlays.add(it) }
                    }
                    marker.position = GeoPoint(lat, lng)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.icon = BitmapDrawable(mapView.context.resources, createStationDotBitmap(color, isSelected))
                    marker.title = "${station.name} — €${String.format("%.3f", station.gplPrice)}/L"
                    marker.setOnMarkerClickListener { _, _ ->
                        onStationSelect(station)
                        true
                    }
                }

                val staleIds = stationMarkers.keys - currentIds
                staleIds.forEach { id ->
                    stationMarkers[id]?.let { mapView.overlays.remove(it) }
                    stationMarkers.remove(id)
                }

                mapView.invalidate()
            }
        )

        DisposableEffect(lifecycleOwner) {
            val mapView = mapViewRef["map"]
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapView?.onDetach()
            }
        }

        // Map Legend / Region Header
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(EcoGreenPrimary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mappa Distributori GPL Campania",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Map Control Floating Buttons (Zoom In/Out, Center)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
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

        // Selected Station Card Overlay on Map Bottom
        selectedStation?.let { station ->
            val dist = GplViewModel.calculateDistanceKm(userLat, userLng, station.latitude, station.longitude)

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .testTag("map_station_selected_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                                text = "${station.city} • ${dist?.let { "$it km da te" } ?: "distanza non disponibile"}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        // Price Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Text(
                                text = "€${String.format("%.3f", station.gplPrice)}/L",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { onDirectionsClick(station) },
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
                                Text("Google Maps", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(
                            onClick = { onStationSelect(station) },
                            modifier = Modifier.background(Color(0xFFF1F5F9), CircleShape)
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = "Dettagli", tint = Color.DarkGray)
                        }
                    }
                }
            }
        }
    }
}

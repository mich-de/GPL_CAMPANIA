package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.data.local.GplDatabase
import com.example.data.repository.GplItaliaRepository
import com.example.data.repository.GplRepository
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.GplTheme
import com.example.ui.viewmodel.GplViewModel

private const val LOCATION_FIX_TIMEOUT_MS = 20_000L

class MainActivity : ComponentActivity() {

    private val viewModel: GplViewModel by viewModels {
        val database = GplDatabase.getDatabase(applicationContext)
        val repository = GplRepository(applicationContext, database.gplDao(), database.geocodeDao())
        val italiaRepository = GplItaliaRepository(
            applicationContext,
            database.nationalStatsDao(),
            database.newsDao()
        )
        GplViewModel.Factory(repository, italiaRepository, applicationContext)
    }

    /** Vero se almeno uno tra permesso fine/coarse è concesso. Unica fonte di verità, riusata in tutti i punti che devono decidere se chiedere il permesso o procedere. */
    private fun hasLocationPermission(context: Context): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    /** Chiede la posizione reale del device una sola volta; se non arriva o il permesso è negato, resta il fallback iniziale su Sorrento. */
    private fun fetchRealDeviceLocation() {
        if (!hasLocationPermission(this)) {
            viewModel.onLocationUpdateFailed("Permesso di localizzazione non concesso: impossibile ottenere una posizione GPS reale.")
            return
        }

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!gpsEnabled && !networkEnabled) {
            viewModel.onLocationUpdateFailed("Localizzazione disattivata sul device: attiva il GPS nelle impostazioni di sistema, oppure imposta la posizione manualmente.")
            return
        }

        viewModel.setLocationLoading(true)

        // Preferisce il chip GPS reale (fix satellitare) alla posizione di rete (WiFi/celle),
        // coerente con l'alta accuratezza richiesta in precedenza via Play Services.
        val provider = if (gpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        val mainHandler = Handler(Looper.getMainLooper())
        var finished = false

        lateinit var listener: LocationListener
        val timeoutRunnable = Runnable {
            if (finished) return@Runnable
            finished = true
            locationManager.removeUpdates(listener)
            viewModel.onLocationUpdateFailed("GPS non ha trovato un fix reale (serve cielo libero e qualche secondo). Riprova all'aperto o imposta la posizione manualmente dalle Impostazioni.")
        }

        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (finished) return
                finished = true
                mainHandler.removeCallbacks(timeoutRunnable)
                locationManager.removeUpdates(this)
                viewModel.updateUserLocation(location.latitude, location.longitude)
            }

            @Deprecated("Deprecated in Java", ReplaceWith(""))
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {
                if (finished) return
                finished = true
                mainHandler.removeCallbacks(timeoutRunnable)
                locationManager.removeUpdates(this)
                viewModel.onLocationUpdateFailed("Il provider di posizione è stato disattivato durante la ricerca del fix.")
            }
        }

        try {
            locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            mainHandler.postDelayed(timeoutRunnable, LOCATION_FIX_TIMEOUT_MS)
        } catch (e: SecurityException) {
            viewModel.onLocationUpdateFailed("Errore GPS: ${e.message ?: "posizione reale non disponibile"}. Puoi impostarla manualmente dalle Impostazioni.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GplTheme {
                val context = LocalContext.current
                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { fetchRealDeviceLocation() }

                // Sopravvive alla rotazione (Activity ricreata da zero): evita di ri-chiedere
                // permesso/GPS a ogni cambio di orientamento, non solo al primo avvio.
                var hasRequestedLocation by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (hasRequestedLocation) return@LaunchedEffect
                    hasRequestedLocation = true
                    if (hasLocationPermission(context)) {
                        fetchRealDeviceLocation()
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(
                        viewModel = viewModel,
                        onRequestLocationRefresh = {
                            if (hasLocationPermission(context)) {
                                fetchRealDeviceLocation()
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

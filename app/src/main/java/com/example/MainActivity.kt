package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.data.local.GplDatabase
import com.example.data.repository.GplRepository
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.GplTheme
import com.example.ui.viewmodel.GplViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : ComponentActivity() {

    private val viewModel: GplViewModel by viewModels {
        val database = GplDatabase.getDatabase(applicationContext)
        val repository = GplRepository(applicationContext, database.gplDao(), database.geocodeDao())
        GplViewModel.Factory(repository, applicationContext)
    }

    /** Chiede la posizione reale del device una sola volta; se non arriva o il permesso è negato, resta il fallback iniziale su Sorrento. */
    private fun fetchRealDeviceLocation() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            viewModel.onLocationUpdateFailed("Permesso di localizzazione non concesso: impossibile ottenere una posizione GPS reale.")
            return
        }

        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        if (!gpsEnabled && !networkEnabled) {
            viewModel.onLocationUpdateFailed("Localizzazione disattivata sul device: attiva il GPS nelle impostazioni di sistema, oppure imposta la posizione manualmente.")
            return
        }

        viewModel.setLocationLoading(true)

        // PRIORITY_HIGH_ACCURACY forza l'uso del chip GPS reale (fix satellitare), non della
        // posizione di rete (WiFi/celle) che richiederebbe una connessione internet attiva.
        val client = LocationServices.getFusedLocationProviderClient(this)
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.updateUserLocation(location.latitude, location.longitude)
                } else {
                    viewModel.onLocationUpdateFailed("GPS non ha trovato un fix reale (serve cielo libero e qualche secondo). Riprova all'aperto o imposta la posizione manualmente dalle Impostazioni.")
                }
            }
            .addOnFailureListener { e ->
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

                LaunchedEffect(Unit) {
                    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fineGranted || coarseGranted) {
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
                            val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (fineGranted || coarseGranted) {
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


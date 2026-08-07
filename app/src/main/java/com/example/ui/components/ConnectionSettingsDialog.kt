package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.theme.EcoGreenPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatLastRefresh(timestampMillis: Long?): String {
    if (timestampMillis == null) return "Mai aggiornato in questa sessione"
    val formatter = SimpleDateFormat("dd/MM/yyyy 'alle' HH:mm", Locale.ITALIAN)
    return formatter.format(Date(timestampMillis))
}

@Composable
fun ConnectionSettingsDialog(
    reporterName: String,
    lastRefreshTimestamp: Long?,
    currentLat: Double,
    currentLng: Double,
    isLocationManual: Boolean,
    isLocationLoading: Boolean,
    onDismiss: () -> Unit,
    onSaveReporterName: (String) -> Unit,
    onRequestLocationRefresh: () -> Unit,
    onForceRefreshBackend: () -> Unit,
    onSetManualLocation: (Double, Double) -> Unit
) {
    var reporterNameText by remember { mutableStateOf(reporterName) }
    var latText by remember { mutableStateOf(String.format(Locale.ITALIAN, "%.5f", currentLat)) }
    var lngText by remember { mutableStateOf(String.format(Locale.ITALIAN, "%.5f", currentLng)) }
    var manualLocationError by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("connection_settings_dialog"),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Impostazioni",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Segnalazioni",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoGreenPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Nome usato come predefinito quando segnali un prezzo o aggiungi una stazione, così non lo riscrivi ogni volta.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reporterNameText,
                    onValueChange = { reporterNameText = it },
                    label = { Text("Il tuo nome") },
                    placeholder = { Text("es. Marco Sorrentino") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings_reporter_name_input")
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Posizione e Dati",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoGreenPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Posizione attuale: ${String.format(Locale.ITALIAN, "%.5f", currentLat)}, ${String.format(Locale.ITALIAN, "%.5f", currentLng)}" +
                        if (isLocationManual) " (impostata manualmente)" else " (da GPS reale)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("settings_current_location_text")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onRequestLocationRefresh,
                    enabled = !isLocationLoading,
                    modifier = Modifier.fillMaxWidth().testTag("settings_refresh_location_button")
                ) {
                    if (isLocationLoading) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ricerca GPS in corso...")
                    } else {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aggiorna posizione GPS reale")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "In alternativa, se il GPS non trova un fix (es. al chiuso), inserisci qui le coordinate reali della tua posizione:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it; manualLocationError = "" },
                        label = { Text("Latitudine") },
                        placeholder = { Text("es. 40.6358") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("manual_lat_input")
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it; manualLocationError = "" },
                        label = { Text("Longitudine") },
                        placeholder = { Text("es. 14.4082") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("manual_lng_input")
                    )
                }
                if (manualLocationError.isNotEmpty()) {
                    Text(
                        text = manualLocationError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val lat = latText.replace(",", ".").toDoubleOrNull()
                        val lng = lngText.replace(",", ".").toDoubleOrNull()
                        if (lat == null || lng == null || lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
                            manualLocationError = "Coordinate non valide (lat -90/90, lng -180/180)"
                        } else {
                            onSetManualLocation(lat, lng)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("settings_set_manual_location_button")
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Imposta posizione manuale")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onForceRefreshBackend,
                    modifier = Modifier.fillMaxWidth().testTag("settings_force_refresh_button")
                ) {
                    Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Forza aggiornamento dati")
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ultimo aggiornamento: ${formatLastRefresh(lastRefreshTimestamp)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("settings_last_refresh_text")
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = EcoGreenPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GPL Campania v${BuildConfig.VERSION_NAME}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Prezzi, indirizzi e coordinate dall'Osservaprezzi carburanti del MIMIT (dati ufficiali comunicati dai gestori). OpenStreetMap/Nominatim solo per i distributori aggiunti a mano. Nessun dato (prezzo, orario, posizione) viene mai inventato: se non è disponibile realmente, non viene mostrato.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveReporterName(reporterNameText.trim())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_connection_settings_button")
            ) {
                Text("Annulla")
            }
        }
    )
}

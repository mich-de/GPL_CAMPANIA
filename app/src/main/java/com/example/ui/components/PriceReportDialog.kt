package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GplStation
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.FlameOrange

@Composable
fun PriceReportDialog(
    station: GplStation,
    initialReporterName: String = "",
    onDismiss: () -> Unit,
    onSubmitPrice: (newPrice: Double, reporterName: String, notes: String) -> Unit
) {
    var priceText by remember { mutableStateOf(station.gplPrice.toString()) }
    var reporterName by remember { mutableStateOf(initialReporterName) }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("price_report_dialog"),
        shape = RoundedCornerShape(16.dp),
        title = {
            Column {
                Text(
                    text = "Aggiorna Prezzo GPL",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${station.name} (${station.city})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Inserisci il nuovo prezzo rilevato alla pompa per il GPL (€ al litro):",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = priceText,
                    onValueChange = {
                        priceText = it
                        errorMessage = ""
                    },
                    label = { Text("Prezzo GPL (€/Litri)") },
                    placeholder = { Text("es. 0.719") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_gpl_price_field")
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = reporterName,
                    onValueChange = { reporterName = it },
                    label = { Text("Il tuo Nome / Nome Utente") },
                    placeholder = { Text("es. Marco Sorrentino") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_reporter_name_field")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Note opzionali") },
                    placeholder = { Text("es. Servito o Self, orario di rilevamento") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_price_notes_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedPrice = priceText.replace(",", ".").toDoubleOrNull()
                    if (parsedPrice == null || parsedPrice < 0.40 || parsedPrice > 2.00) {
                        errorMessage = "Inserisci un prezzo valido compreso tra €0.40 e €2.00"
                    } else {
                        onSubmitPrice(parsedPrice, reporterName, notes)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("submit_price_report_button")
            ) {
                Text("Invia Segnalazione")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_price_report_button")
            ) {
                Text("Annulla")
            }
        }
    )
}

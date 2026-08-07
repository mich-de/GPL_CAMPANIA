package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import com.example.ui.theme.EcoGreenPrimary

private val CAMPANIA_PROVINCES = listOf(
    "Avellino" to "avellino",
    "Benevento" to "benevento",
    "Caserta" to "caserta",
    "Napoli" to "napoli",
    "Salerno" to "salerno"
)

@Composable
fun AddStationDialog(
    onDismiss: () -> Unit,
    onAddStation: (
        provinciaSlug: String, name: String, brand: String, address: String, city: String,
        gplPrice: Double, openHours: String, phone: String, services: String
    ) -> Unit
) {
    var provinciaSlug by remember { mutableStateOf("napoli") }
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("Eni") }
    var city by remember { mutableStateOf("Piano di Sorrento") }
    var address by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("0.719") }
    var openHours by remember { mutableStateOf("07:00 - 19:30") }
    var phone by remember { mutableStateOf("") }
    var services by remember { mutableStateOf("GPL,Servito,Bancomat") }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("add_station_dialog"),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Segnala Nuovo Distributore GPL",
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Distributore") },
                    placeholder = { Text("es. Eni Station Sorrento") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_station_name_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Provincia",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    CAMPANIA_PROVINCES.forEach { (label, slug) ->
                        FilterChip(
                            selected = provinciaSlug == slug,
                            onClick = { provinciaSlug = slug },
                            label = { Text(label, fontSize = 11.sp) },
                            modifier = Modifier.testTag("add_station_province_chip_$slug")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Marca (Eni, IP, Q8, Beyfin, Pompe Bianche...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_station_brand_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Città (es. Sorrento, Vico Equense, Pompei)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_station_city_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Indirizzo e Via") },
                    placeholder = { Text("es. SS145 Sorrentina Km 12") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_station_address_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Prezzo GPL (€/Litro)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_station_price_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = openHours,
                    onValueChange = { openHours = it },
                    label = { Text("Orari di Apertura") },
                    placeholder = { Text("es. 07:00 - 19:30 o H24") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = services,
                    onValueChange = { services = it },
                    label = { Text("Serviti separati da virgola") },
                    placeholder = { Text("es. GPL, Bar, Lavaggio, Bancomat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedPrice = priceText.replace(",", ".").toDoubleOrNull()
                    if (name.isBlank() || address.isBlank() || city.isBlank()) {
                        errorMessage = "Compila tutti i campi obbligatori (Nome, Città, Indirizzo)"
                    } else if (parsedPrice == null || parsedPrice <= 0.3) {
                        errorMessage = "Inserisci un prezzo GPL valido"
                    } else {
                        onAddStation(provinciaSlug, name, brand, address, city, parsedPrice, openHours, phone, services)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("submit_add_station_button")
            ) {
                Text("Salva Distributore")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_add_station_button")
            ) {
                Text("Annulla")
            }
        }
    )
}

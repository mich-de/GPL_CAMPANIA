package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.SorrentoBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GplCalculatorSheet(
    avgGplPrice: Double = 0.715,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var tankCapacityLiters by remember { mutableFloatStateOf(42f) }
    var annualKm by remember { mutableFloatStateOf(15000f) }
    var kmPerLiter by remember { mutableFloatStateOf(12f) }

    val petrolPrice = 1.849
    val dieselPrice = 1.729

    // Calculations
    val fullTankGpl = tankCapacityLiters * avgGplPrice
    val fullTankPetrol = tankCapacityLiters * petrolPrice

    val litersNeededYear = annualKm / kmPerLiter
    val gplCostYear = litersNeededYear * avgGplPrice
    val petrolCostYear = litersNeededYear * petrolPrice
    val savingsYear = petrolCostYear - gplCostYear

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("gpl_calculator_sheet")
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(EcoGreenPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Calculate,
                            contentDescription = null,
                            tint = EcoGreenPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Calcolatore Risparmio GPL",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Confronto GPL vs Benzina in Penisola Sorrentina",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_calculator_button")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Chiudi")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Slider 1: Tank Capacity
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Capienza Serbatoio GPL:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "${tankCapacityLiters.toInt()} Litri", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SorrentoBlue)
                }
                Slider(
                    value = tankCapacityLiters,
                    onValueChange = { tankCapacityLiters = it },
                    valueRange = 20f..80f,
                    steps = 11,
                    colors = SliderDefaults.colors(thumbColor = SorrentoBlue, activeTrackColor = SorrentoBlue),
                    modifier = Modifier.testTag("slider_tank_capacity")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Slider 2: Annual Km
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Percorrenza Annua:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "${annualKm.toInt()} km / anno", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SorrentoBlue)
                }
                Slider(
                    value = annualKm,
                    onValueChange = { annualKm = it },
                    valueRange = 5000f..40000f,
                    steps = 34,
                    colors = SliderDefaults.colors(thumbColor = SorrentoBlue, activeTrackColor = SorrentoBlue),
                    modifier = Modifier.testTag("slider_annual_km")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hero Savings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Savings,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Risparmio Stimato Annuale",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "€ ${String.format("%.2f", savingsYear)}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF15803D)
                    )

                    Text(
                        text = "Risparmi oltre il 55% ad ogni rifornimento rispetto alla Benzina!",
                        fontSize = 12.sp,
                        color = Color(0xFF166534)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Full Tank Comparison
            Text(
                text = "Costo Pieno da ${tankCapacityLiters.toInt()} Litri",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // GPL Pieno Box
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "GPL (Medio)", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        Text(text = "€ ${String.format("%.2f", fullTankGpl)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                        Text(text = "€ ${String.format("%.3f", avgGplPrice)} /L", fontSize = 10.sp, color = Color.Gray)
                    }
                }

                // Benzina Pieno Box
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "Benzina", fontSize = 11.sp, color = FlameOrange, fontWeight = FontWeight.Bold)
                        Text(text = "€ ${String.format("%.2f", fullTankPetrol)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FlameOrange)
                        Text(text = "€ ${String.format("%.3f", petrolPrice)} /L", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

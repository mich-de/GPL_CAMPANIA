package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GplStation
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.PriceBadgeBg
import com.example.ui.theme.PriceBadgeBorder
import com.example.ui.theme.PriceBadgeGreen
import com.example.ui.theme.SavingsBadgeBg
import com.example.ui.theme.SavingsBadgeFg
import com.example.ui.theme.SorrentoBlue
import com.example.ui.viewmodel.GplViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StationDetailDialog(
    station: GplStation,
    viewModel: GplViewModel,
    onDismiss: () -> Unit,
    onDirectionsClick: () -> Unit,
    onReportPriceClick: () -> Unit,
    averagePrice: Double = 0.0
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val brandColors = getBrandColor(station.brand)
    val savingsPct = if (averagePrice > 0 && station.gplPrice > 0 && station.gplPrice < averagePrice) {
        ((averagePrice - station.gplPrice) / averagePrice * 100).toInt()
    } else 0
    val isFavorite = uiState.stations.find { it.id == station.id }?.isFavorite ?: station.isFavorite

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("station_detail_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            // ── HERO HEADER with brand gradient ──────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                brandColors.first.copy(alpha = 0.85f),
                                brandColors.first.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Brand badge row + close + fav
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(brandColors.first)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = station.brand.take(12).uppercase(),
                                    color = brandColors.second,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                            // Open / Closed pill — mostrato solo se lo stato è realmente noto
                            station.isOpenNow?.let { isOpen ->
                                val statusColor = if (isOpen) PriceBadgeGreen else MaterialTheme.colorScheme.error
                                Spacer(modifier = Modifier.width(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(statusColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(statusColor)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isOpen) "Aperto Ora" else "Chiuso",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                }
                            }
                        }
                        Row {
                            // Favorite toggle
                            IconButton(onClick = { viewModel.toggleFavorite(station) }) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Preferito",
                                    tint = if (isFavorite) FlameOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Close button
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_detail_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Chiudi")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Station title
                    Text(
                        text = station.name.replace(" (${station.city})", "").replace(" (${station.city.uppercase()})", ""),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Address
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = SorrentoBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${station.address}, ${station.city} (${station.province})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ── BODY ─────────────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {

                // ── Prices Grid: GPL / Benzina / Gasolio ─────────────────────
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Prezzi Carburanti",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // GPL — main hero
                    val hasRealPrice = station.gplPrice > 0.0
                    Surface(
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasRealPrice) PriceBadgeBg else MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (hasRealPrice) PriceBadgeBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalGasStation,
                                contentDescription = null,
                                tint = if (hasRealPrice) PriceBadgeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                "GPL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasRealPrice) PriceBadgeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (hasRealPrice) "€ ${String.format("%.3f", station.gplPrice)}" else "N/D",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (hasRealPrice) PriceBadgeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasRealPrice) {
                                Text("/L", fontSize = 10.sp, color = PriceBadgeGreen)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Benzina
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Benzina", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (station.services.contains("1.8") || station.services.contains("1.9")) {
                                        val m = Regex("1\\.[89]\\d{2}").find(station.services)?.value ?: "n/d"
                                        "€ $m"
                                    } else "n/d",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        // Gasolio
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Gasolio", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (station.services.contains("2.0") || station.services.contains("1.9")) {
                                        val m = Regex("2\\.0\\d{2}|1\\.9\\d{2}").find(station.services)?.value ?: "n/d"
                                        "€ $m"
                                    } else "n/d",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Savings badge
                if (savingsPct > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SavingsBadgeBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.TrendingDown, contentDescription = null, tint = SavingsBadgeFg, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Risparmio rispetto alla media regionale",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SavingsBadgeFg
                                )
                                Text(
                                    text = "-$savingsPct% · €${String.format("%.3f", averagePrice - station.gplPrice)}/L risparmiati",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SavingsBadgeFg
                                )
                            }
                        }
                    }
                }

                // ── Update timestamp — solo se la fonte ne ha comunicato una reale ──
                if (station.priceLastUpdated.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Ultimo aggiornamento: ${station.priceLastUpdated}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), thickness = 0.5.dp)

                // ── Come raggiungerlo — 2 CTA ─────────────────────────────────
                Text("Come raggiungerlo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDirectionsClick,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("detail_google_maps_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SorrentoBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Google Maps", fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Indirizzo GPL", "${station.address}, ${station.city}")
                            clipboard.setPrimaryClip(clip)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copia Via", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), thickness = 0.5.dp)

                // ── Opening Hours ─────────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccessTime, contentDescription = null, tint = SorrentoBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Orari di Apertura", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (station.openHoursWeekday == null && station.openHoursSunday == null) {
                            Text(
                                "Orari non disponibili — verifica sul posto",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            station.openHoursWeekday?.let { weekday ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Lun - Sab:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(weekday, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            station.openHoursSunday?.let { sunday ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Dom / Festivi:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(sunday, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (station.isOpening24h == true) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(shape = CircleShape, color = PriceBadgeGreen) {
                                Text(
                                    "✓ Aperto 24h",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White
                                )
                            }
                        }
                    }
                }

                // ── Services ─────────────────────────────────────────────────
                if (station.services.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Servizi Disponibili", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        station.services.split(",").map { it.trim() }.filter { it.isNotEmpty() && !it.contains(Regex("\\d\\.\\d")) }.forEach { service ->
                            Surface(
                                shape = CircleShape,
                                color = EcoGreenPrimary.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EcoGreenPrimary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "✓ $service",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = EcoGreenPrimary
                                )
                            }
                        }
                    }
                }

                // ── Phone ─────────────────────────────────────────────────────
                if (station.phone.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${station.phone}"))
                            context.startActivity(callIntent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Chiama (${station.phone})")
                    }
                }

                // ── Report Price CTA ──────────────────────────────────────────
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onReportPriceClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_report_price_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Segnala Nuovo Prezzo GPL", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

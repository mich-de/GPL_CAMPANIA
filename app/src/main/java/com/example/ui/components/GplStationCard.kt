package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GplStation
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.PriceBadgeBg
import com.example.ui.theme.PriceBadgeBorder
import com.example.ui.theme.PriceBadgeGreen
import com.example.ui.theme.SavingsBadgeBg
import com.example.ui.theme.SavingsBadgeFg
import com.example.ui.theme.SorrentoBlue
import java.util.Locale

/**
 * Card della lista. Il vincolo di progetto è lo schermo stretto: i dati ufficiali MIMIT contengono
 * nomi fino a 69 caratteri e indirizzi fino a 64, quindi ogni testo qui dentro ha un `weight` o un
 * limite di larghezza esplicito — nessun elemento può spingere gli altri fuori dalla card.
 */
@Composable
fun GplStationCard(
    station: GplStation,
    distanceKm: Double?,
    onCardClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDirectionsClick: () -> Unit,
    onReportPriceClick: () -> Unit,
    averagePrice: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val brandColors = getBrandColor(station.brand)
    val savingsPct = if (averagePrice > 0 && station.gplPrice > 0 && station.gplPrice < averagePrice) {
        ((averagePrice - station.gplPrice) / averagePrice * 100).toInt()
    } else 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .animateContentSize()
            .testTag("station_card_${station.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {

            // ── Riga 1: nome su tutta la larghezza + stella ────────────────────
            // Il nome ha la precedenza assoluta: è ciò che identifica il distributore.
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = station.name
                        .replace(" (${station.city})", "")
                        .replace(" (${station.city.uppercase()})", ""),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 6.dp)
                )

                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .size(44.dp) // area di tocco piena, non solo l'icona
                        .testTag("fav_button_${station.id}")
                ) {
                    Icon(
                        imageVector = if (station.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Preferito",
                        tint = if (station.isFavorite) FlameOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // ── Riga 2: marchio + distanza, le due etichette da leggere al volo ─
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(brandColors.first)
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = formatBrandLabel(station.brand),
                        color = brandColors.second,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Il marchio non può prendersi più di metà riga: la distanza deve restare
                        // sempre visibile, anche con "APStazionidiServizio".
                        modifier = Modifier.widthIn(max = 150.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.NearMe,
                        contentDescription = null,
                        tint = SorrentoBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = distanceKm?.let { String.format(Locale.ITALY, "%.1f km", it) }
                            ?: "distanza n.d.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SorrentoBlue,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Riga 3: indirizzo ──────────────────────────────────────────────
            Row(modifier = Modifier.padding(end = 8.dp)) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(15.dp)
                        .padding(top = 2.dp) // allineata alla prima riga di testo, non al blocco
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${station.address}, ${station.city} (${station.province})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Riga 4: prezzo (a sinistra) + risparmio e data (a destra) ──────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PriceBadgeBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PriceBadgeBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalGasStation,
                            contentDescription = "GPL",
                            tint = PriceBadgeGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format(Locale.ITALY, "€ %.3f", station.gplPrice),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PriceBadgeGreen,
                            maxLines = 1
                        )
                        Text(
                            text = "/L",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PriceBadgeGreen,
                            modifier = Modifier.padding(start = 2.dp, bottom = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    if (savingsPct > 0) {
                        Surface(shape = RoundedCornerShape(8.dp), color = SavingsBadgeBg) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = SavingsBadgeFg,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "-$savingsPct% sulla media",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SavingsBadgeFg,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Stato apertura: solo se davvero noto (i dati ufficiali non lo forniscono).
                    station.isOpenNow?.let { isOpen ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isOpen) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isOpen) Color(0xFF16A34A) else Color(0xFFDC2626))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isOpen) "Aperto" else "Chiuso",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOpen) Color(0xFF15803D) else Color(0xFFB91C1C)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (station.priceLastUpdated.isNotBlank()) {
                        Text(
                            text = "agg. ${station.priceLastUpdated}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Riga 5: azioni ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDirectionsClick,
                    colors = ButtonDefaults.buttonColors(containerColor = SorrentoBlue),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp) // sotto i 44dp il tocco sul telefono diventa impreciso
                        .testTag("directions_button_${station.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Directions,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Naviga", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onReportPriceClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("report_price_button_${station.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Segnala", fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

/**
 * I marchi ufficiali arrivano attaccati (`PompeBianche`, `AgipEni`): 185 distributori su 428 sono
 * `PompeBianche`, che troncato a 10 caratteri diventava "POMPEBIANC". Qui si separa sulle maiuscole
 * interne, senza alterare il dato: cambia solo la resa a schermo.
 */
internal fun formatBrandLabel(brand: String): String =
    brand.trim().replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").uppercase()

fun getBrandColor(brand: String): Pair<Color, Color> {
    return when {
        brand.contains("Energas", true) -> Pair(Color(0xFFFFE082), Color(0xFF3E2723))
        brand.contains("Esso", true)    -> Pair(Color(0xFFBBDEFB), Color(0xFF0D47A1))
        brand.contains("Eni", true) || brand.contains("Agip", true) -> Pair(Color(0xFFFFF59D), Color(0xFF212121))
        brand.contains("IP", true)      -> Pair(Color(0xFFC8E6C9), Color(0xFF1B5E20))
        brand.contains("Q8", true)      -> Pair(Color(0xFFFFCDD2), Color(0xFFB71C1C))
        brand.contains("Tamoil", true)  -> Pair(Color(0xFFE1BEE7), Color(0xFF4A148C))
        brand.contains("Gulf", true)    -> Pair(Color(0xFFFFE0B2), Color(0xFFBF360C))
        brand.contains("Api", true)     -> Pair(Color(0xFFB2EBF2), Color(0xFF006064))
        brand.contains("Beyfin", true)  -> Pair(Color(0xFFF3E5F5), Color(0xFF4A148C))
        else                            -> Pair(Color(0xFFEEEEEE), Color(0xFF424242))
    }
}

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.NationalGplSnapshot
import com.example.data.local.NationalGplTrend
import com.example.data.local.NewsItem
import com.example.data.local.PriceChange
import com.example.data.local.TankRevision
import com.example.data.local.formatDayKey
import com.example.data.local.formatCountdown
import com.example.data.local.formatItalianDateTime
import com.example.data.local.homeRegion
import com.example.data.local.homeRegionRank
import com.example.data.local.parseItalianDate
import com.example.data.local.expiryAfterValidityPeriod
import com.example.data.remote.ItalianRegions
import com.example.data.remote.RegionGplAverage
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.SorrentoBlue
import com.example.ui.viewmodel.GplItaliaState
import com.example.ui.viewmodel.ItaliaTab
import java.util.Locale

/**
 * "GPL in Italia": i numeri nazionali, le notizie ufficiali sui carburanti e la scadenza del
 * serbatoio.
 *
 * Le tre cose stanno insieme perché condividono la stessa regola: **niente parte da solo e niente è
 * stimato**. I numeri nazionali si scaricano quando li si chiede, le notizie sono quelle scritte dal
 * ministero senza riassunti, la scadenza è quella che l'utente legge sul suo libretto. Quando un
 * dato non c'è, lo spazio resta vuoto con scritto perché.
 */
@Composable
fun GplItaliaSheet(
    state: GplItaliaState,
    onDismiss: () -> Unit,
    onSelectTab: (ItaliaTab) -> Unit,
    onRefreshStats: (Boolean) -> Unit,
    onRefreshNews: () -> Unit,
    onSaveTank: (TankRevision) -> Unit,
    onClearTank: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("gpl_italia_sheet"),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "GPL in Italia",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = state.tab.ordinal, containerColor = Color.Transparent) {
                    ItaliaTab.entries.forEach { tab ->
                        Tab(
                            selected = state.tab == tab,
                            onClick = { onSelectTab(tab) },
                            modifier = Modifier.testTag("italia_tab_${tab.name.lowercase()}"),
                            text = { Text(tab.label, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (state.tab) {
                        ItaliaTab.NUMERI -> NumbersTab(state, onRefreshStats)
                        ItaliaTab.NOTIZIE -> NewsTab(state, onRefreshNews)
                        ItaliaTab.SERBATOIO -> TankTab(state, onSaveTank, onClearTank)
                    }
                }
                if (state.message != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = state.message,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("italia_message")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("close_italia_button")
            ) {
                Text("Chiudi")
            }
        }
    )
}

private val ItaliaTab.label: String
    get() = when (this) {
        ItaliaTab.NUMERI -> "I numeri"
        ItaliaTab.NOTIZIE -> "Notizie"
        ItaliaTab.SERBATOIO -> "Serbatoio"
    }

// ─────────────────────────────────────── I numeri ───────────────────────────────────────

@Composable
private fun NumbersTab(state: GplItaliaState, onRefreshStats: (Boolean) -> Unit) {
    val snapshot = state.data.latest

    if (snapshot == null) {
        EmptyState(
            title = "Nessuna lettura ancora",
            detail = "Le medie si calcolano sui prezzi che i gestori hanno comunicato al MIMIT: " +
                "circa 7,5 MB di open data, scaricati solo quando lo chiedi."
        )
    } else {
        HeadlineRow(
            left = "Media nazionale" to euro(snapshot.averagePrice),
            right = "Mediana" to euro(snapshot.medianPrice)
        )
        Spacer(modifier = Modifier.height(10.dp))
        StatRow("Impianti GPL censiti", snapshot.stationCount.toString(), testTag = "italia_station_count")
        StatRow("Pubblicazione del", formatDayKey(snapshot.dayKey).ifBlank { "—" })
        StatRow("Letta il", formatItalianDateTime(snapshot.capturedAt))
        if (snapshot.skippedRows > 0) {
            StatRow(
                label = "Impianti non attribuibili",
                value = snapshot.skippedRows.toString(),
                highlight = true,
                testTag = "italia_skipped_rows"
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hanno un prezzo valido ma nell'anagrafica ufficiale la provincia è scritta " +
                    "fuori formato: restano fuori dalle medie regionali invece di essere " +
                    "attribuiti a caso.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Separator()
        HomeRegionBlock(snapshot)

        Separator()
        TrendBlock(state.data.trend, snapshot)

        Separator()
        SectionTitle("Classifica regionale")
        Text(
            text = "Dalla più economica alla più cara, con il numero di impianti su cui è calcolata.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        snapshot.regions.forEachIndexed { index, region -> RegionRow(index + 1, region) }

        Separator()
    }

    OutlinedButton(
        onClick = { onRefreshStats(snapshot != null && snapshot.dayKey == state.todayKey) },
        enabled = !state.isStatsLoading,
        modifier = Modifier.fillMaxWidth().testTag("italia_refresh_stats_button")
    ) {
        if (state.isStatsLoading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Lettura degli open data...")
        } else {
            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scarica i numeri di oggi (~7,5 MB)")
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Fonte: open data del MIMIT, gli stessi prezzi che l'app usa per la Campania. " +
            "La pubblicazione esce una volta la mattina: scaricarla più volte nello stesso " +
            "giorno riscriverebbe gli stessi numeri.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** La Campania nel confronto nazionale: è la ragione per cui questa schermata esiste. */
@Composable
private fun HomeRegionBlock(snapshot: NationalGplSnapshot) {
    val home = snapshot.homeRegion()
    SectionTitle(ItalianRegions.HOME_REGION)
    if (home == null) {
        Text(
            text = "Questa lettura non contiene impianti in ${ItalianRegions.HOME_REGION}.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val rank = snapshot.homeRegionRank()
    val total = snapshot.regions.size
    val gap = home.averagePrice - snapshot.averagePrice

    HeadlineRow(
        left = "Media regionale" to euro(home.averagePrice),
        right = "In classifica" to if (rank > 0) "$rank° su $total" else "—"
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = when {
            gap < 0 -> "Costa ${euro(-gap)} in meno della media italiana, su ${home.stationCount} impianti."
            gap > 0 -> "Costa ${euro(gap)} in più della media italiana, su ${home.stationCount} impianti."
            else -> "In linea con la media italiana, su ${home.stationCount} impianti."
        },
        fontSize = 12.sp,
        modifier = Modifier.testTag("italia_home_region_gap")
    )
}

@Composable
private fun TrendBlock(trend: NationalGplTrend, snapshot: NationalGplSnapshot) {
    SectionTitle("Andamento")
    if (trend.sinceWeek == null && trend.sinceMonth == null) {
        Text(
            text = "Serve più di una lettura per parlare di andamento. Al momento ne " +
                if (trend.snapshotCount <= 1) "è conservata una sola." else "sono conservate ${trend.snapshotCount}, tutte troppo ravvicinate.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("italia_trend_empty")
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "La fonte pubblica solo la situazione di stamattina: l'unico modo onesto di dire " +
                "\"−1,2% in una settimana\" è aver salvato la fotografia di una settimana fa.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    trend.sinceWeek?.let { TrendRow("Ultima settimana", it, snapshot.averagePrice, "italia_trend_week") }
    trend.sinceMonth?.let { TrendRow("Ultimo mese", it, snapshot.averagePrice, "italia_trend_month") }
}

@Composable
private fun TrendRow(label: String, change: PriceChange, reference: Double, testTag: String) {
    val (icon, tint) = when {
        change.delta > 0.0005 -> Icons.Filled.TrendingUp to FlameOrange
        change.delta < -0.0005 -> Icons.Filled.TrendingDown to EcoGreenPrimary
        else -> Icons.Filled.TrendingFlat to SorrentoBlue
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$label: ${signed(change.delta)} (${signedPercent(change.percent(reference))})",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                modifier = Modifier.testTag(testTag)
            )
            Text(
                text = "rispetto alla lettura del ${formatDayKey(change.fromDayKey)}, ${change.daysApart} giorni fa",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RegionRow(position: Int, region: RegionGplAverage) {
    val isHome = region.region == ItalianRegions.HOME_REGION
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isHome) {
                    Modifier.background(EcoGreenPrimary.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$position.",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp)
        )
        Text(
            text = region.region,
            fontSize = 12.sp,
            fontWeight = if (isHome) FontWeight.Bold else FontWeight.Normal,
            color = if (isHome) EcoGreenPrimary else Color.Unspecified,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${region.stationCount}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp)
        )
        Text(
            text = euro(region.averagePrice),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ─────────────────────────────────────── Notizie ───────────────────────────────────────

@Composable
private fun NewsTab(state: GplItaliaState, onRefreshNews: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val news = state.data.news

    if (news.isEmpty()) {
        EmptyState(
            title = "Nessuna notizia sui carburanti",
            detail = "La sala stampa del MIMIT parla soprattutto d'altro: è normale che per " +
                "settimane non ci sia niente in tema. Quando non c'è, qui non compare nulla."
        )
    } else {
        news.forEach { item ->
            NewsRow(item) { uriHandler.openUri(item.link) }
            HorizontalDivider()
        }
        Spacer(modifier = Modifier.height(10.dp))
    }

    OutlinedButton(
        onClick = onRefreshNews,
        enabled = !state.isNewsLoading,
        modifier = Modifier.fillMaxWidth().testTag("italia_refresh_news_button")
    ) {
        if (state.isNewsLoading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Lettura del feed...")
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cerca notizie (~9 KB)")
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Ultima ricerca: ${formatItalianDateTime(state.data.newsLastFetch)}. " +
            "Titoli e testi sono quelli del ministero, non riscritti: toccare una notizia apre " +
            "la pagina originale.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun NewsRow(item: NewsItem, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp)
            .testTag("italia_news_item")
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Filled.OpenInNew,
                contentDescription = "Apri la notizia originale",
                tint = SorrentoBlue,
                modifier = Modifier.size(16.dp)
            )
        }
        if (item.summary.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.summary,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildString {
                append(item.source)
                if (item.publishedAt > 0) append(" • ${formatItalianDateTime(item.publishedAt)}")
            },
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────── Serbatoio ───────────────────────────────────────

@Composable
private fun TankTab(
    state: GplItaliaState,
    onSaveTank: (TankRevision) -> Unit,
    onClearTank: () -> Unit
) {
    val saved = state.data.tank
    var expiryText by remember(saved) { mutableStateOf(saved?.let { formatDayKey(it.expiryDayKey) }.orEmpty()) }
    var referenceText by remember(saved) {
        mutableStateOf(saved?.referenceDayKey?.takeIf { it > 0 }?.let { formatDayKey(it) }.orEmpty())
    }
    var plate by remember(saved) { mutableStateOf(saved?.plate.orEmpty()) }

    if (saved != null) {
        TankCountdown(saved, state.todayKey)
        Separator()
    }

    SectionTitle(if (saved == null) "Inserisci la scadenza" else "Modifica la scadenza")
    Text(
        text = "La data è quella punzonata sul serbatoio o scritta sul libretto. L'app non la " +
            "ricava dalla targa e non la indovina: se non la inserisci, non mostra nessun conto " +
            "alla rovescia.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
        value = expiryText,
        onValueChange = { expiryText = it },
        label = { Text("Scadenza (gg/mm/aaaa)") },
        singleLine = true,
        isError = expiryText.isNotBlank() && parseItalianDate(expiryText) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("tank_expiry_field")
    )

    Spacer(modifier = Modifier.height(10.dp))
    OutlinedTextField(
        value = referenceText,
        onValueChange = { referenceText = it },
        label = { Text("Collaudo o prima immatricolazione (facoltativo)") },
        singleLine = true,
        isError = referenceText.isNotBlank() && parseItalianDate(referenceText) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("tank_reference_field")
    )

    val referenceKey = parseItalianDate(referenceText)
    val proposal = referenceKey?.let { expiryAfterValidityPeriod(it) }
    if (proposal != null) {
        Spacer(modifier = Modifier.height(6.dp))
        TextButton(
            onClick = { expiryText = formatDayKey(proposal) },
            modifier = Modifier.testTag("tank_propose_expiry_button")
        ) {
            Text(
                text = "Proponi ${formatDayKey(proposal)} (+${TankRevision.VALIDITY_YEARS} anni)",
                fontSize = 12.sp
            )
        }
        Text(
            text = "Il Regolamento UNECE 67 dà dieci anni di validità al serbatoio. È solo una " +
                "proposta: se sul libretto c'è scritto altro, vale quello.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.height(10.dp))
    OutlinedTextField(
        value = plate,
        onValueChange = { plate = it.uppercase(Locale.ITALY) },
        label = { Text("Targa (facoltativa)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("tank_plate_field")
    )

    Spacer(modifier = Modifier.height(12.dp))
    val expiryKey = parseItalianDate(expiryText)
    Button(
        onClick = {
            expiryKey?.let {
                onSaveTank(
                    TankRevision(
                        expiryDayKey = it,
                        referenceDayKey = referenceKey ?: 0,
                        plate = plate.trim()
                    )
                )
            }
        },
        enabled = expiryKey != null,
        colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
        modifier = Modifier.fillMaxWidth().testTag("tank_save_button")
    ) {
        Text("Salva il promemoria")
    }

    if (saved != null) {
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onClearTank,
            modifier = Modifier.fillMaxWidth().testTag("tank_clear_button")
        ) {
            Text("Rimuovi il promemoria", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Resta tutto sul telefono: la data non viene inviata da nessuna parte.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TankCountdown(revision: TankRevision, todayKey: Int) {
    val days = revision.daysRemaining(todayKey)
    val status = revision.status(todayKey)
    val tint = when (status) {
        TankRevision.Status.SCADUTO -> MaterialTheme.colorScheme.error
        TankRevision.Status.IN_SCADENZA -> FlameOrange
        TankRevision.Status.VALIDO -> EcoGreenPrimary
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.EventBusy, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (days == null) "Data non valida" else formatCountdown(days).replaceFirstChar { it.uppercase() },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
                modifier = Modifier.testTag("tank_countdown")
            )
            Text(
                text = buildString {
                    append("Scadenza ${formatDayKey(revision.expiryDayKey)}")
                    if (revision.plate.isNotBlank()) append(" • ${revision.plate}")
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (status == TankRevision.Status.SCADUTO || status == TankRevision.Status.IN_SCADENZA) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Con il serbatoio scaduto l'auto non passa la revisione periodica, e le officine " +
                "autorizzate alla sostituzione hanno spesso settimane di attesa: meglio " +
                "prenotare con anticipo.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────── Pezzi comuni ───────────────────────────────────────

@Composable
private fun EmptyState(title: String, detail: String) {
    Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = detail,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(14.dp))
}

@Composable
private fun HeadlineRow(left: Pair<String, String>, right: Pair<String, String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Headline(left.first, left.second, Modifier.weight(1f))
        Headline(right.first, right.second, Modifier.weight(1f))
    }
}

@Composable
private fun Headline(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(EcoGreenPrimary.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = EcoGreenPrimary
        )
    }
}

@Composable
private fun StatRow(label: String, value: String, highlight: Boolean = false, testTag: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (highlight) FlameOrange else Color.Unspecified,
            modifier = Modifier
                .weight(1f)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EcoGreenPrimary)
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun Separator() {
    Spacer(modifier = Modifier.height(14.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(14.dp))
}

/** Tre decimali, virgola italiana: è il formato con cui i prezzi dei carburanti sono scritti ovunque. */
private fun euro(price: Double): String = String.format(Locale.ITALY, "%.3f €/L", price)

private fun signed(delta: Double): String =
    String.format(Locale.ITALY, "%+.3f €/L", delta)

private fun signedPercent(percent: Double): String =
    String.format(Locale.ITALY, "%+.1f%%", percent)

package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MonitoringReport
import com.example.data.local.RefreshDiagnostics
import com.example.data.local.asPlainText
import com.example.data.local.formatDuration
import com.example.data.local.formatElapsed
import com.example.data.local.formatItalianDateTime
import com.example.data.local.formatMeasure
import com.example.ui.theme.EcoGreenPrimary
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.SorrentoBlue

/**
 * Pannello di diagnostica: mostra come sta funzionando l'app, non cosa contiene.
 *
 * Ogni riga è una misura reale già registrata dal device — nessun valore stimato e nessun
 * segnaposto: quello che non è mai stato misurato si legge "—". Le azioni disponibili sono tutte
 * non distruttive (riscaricare, far scadere la cache, copiare): niente qui cancella dati.
 */
@Composable
fun MonitoringPanelDialog(
    report: MonitoringReport?,
    isRefreshing: Boolean,
    onDismiss: () -> Unit,
    onForceRefresh: () -> Unit,
    onInvalidateCache: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("monitoring_panel_dialog"),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Diagnostica",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (report == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Lettura dei parametri in corso...", fontSize = 13.sp)
                }
            } else {
                MonitoringContent(
                    report = report,
                    isRefreshing = isRefreshing,
                    onForceRefresh = onForceRefresh,
                    onInvalidateCache = onInvalidateCache,
                    onCopyReport = { clipboard.setText(AnnotatedString(report.asPlainText())) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EcoGreenPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("close_monitoring_button")
            ) {
                Text("Chiudi")
            }
        },
        dismissButton = {
            TextButton(onClick = onForceRefresh, enabled = !isRefreshing) {
                Text("Aggiorna")
            }
        }
    )
}

@Composable
private fun MonitoringContent(
    report: MonitoringReport,
    isRefreshing: Boolean,
    onForceRefresh: () -> Unit,
    onInvalidateCache: () -> Unit,
    onCopyReport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        OutcomeBanner(report)

        Spacer(modifier = Modifier.height(14.dp))
        SectionTitle("Ultimo aggiornamento")
        MonitoringRow(
            label = "Quando",
            value = buildString {
                append(formatItalianDateTime(report.diagnostics.attemptedAt.takeIf { it > 0 }))
                val elapsed = formatElapsed(report.diagnostics.attemptedAt, report.generatedAt)
                if (elapsed.isNotBlank()) append(" ($elapsed)")
            },
            testTag = "monitoring_last_attempt"
        )
        MonitoringRow("Fonte che ha risposto", report.diagnostics.source.ifBlank { "—" })
        MonitoringRow("Durata", formatDuration(report.diagnostics.durationMillis))
        MonitoringRow(
            label = "Cache 15 minuti",
            value = if (report.isCacheValid) {
                "valida per altri ${report.cacheRemainingMillis(report.generatedAt) / 60_000} min"
            } else {
                "scaduta: il prossimo avvio riscarica"
            },
            testTag = "monitoring_cache_state"
        )
        MonitoringRow("Last-Modified CSV", report.csvLastModified ?: "—")
        if (report.diagnostics.message.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = report.diagnostics.message,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("monitoring_error_message")
            )
        }

        SectionSeparator()

        SectionTitle("Dati sul dispositivo")
        MonitoringRow("Distributori totali", report.totalStations.toString(), testTag = "monitoring_total_stations")
        MonitoringRow("Da fonte ufficiale", report.officialStations.toString())
        MonitoringRow("Aggiunti a mano", report.userStations.toString())
        MonitoringRow("Preferiti", report.favorites.toString())
        MonitoringRow("Segnalazioni prezzo", report.priceReports.toString())
        MonitoringRow(
            label = "Senza coordinate reali",
            value = report.withoutCoordinates.toString(),
            highlight = report.withoutCoordinates > 0,
            testTag = "monitoring_without_coordinates"
        )

        SectionSeparator()

        SectionTitle("Qualità dell'ultimo scarico")
        MonitoringRow("Righe scritte", formatMeasure(report.diagnostics.stationsWritten))
        MonitoringRow("Doppioni uniti", formatMeasure(report.diagnostics.duplicatesMerged))
        MonitoringRow("Senza coordinate ufficiali", formatMeasure(report.diagnostics.withoutCoordinates))
        MonitoringRow("Prezzi comunicati oggi", formatMeasure(report.diagnostics.pricesToday))
        MonitoringRow("Negli ultimi 7 giorni", formatMeasure(report.diagnostics.pricesWithinWeek))
        MonitoringRow(
            label = "Oltre 30 giorni fa",
            value = formatMeasure(report.diagnostics.pricesOlderThanMonth),
            highlight = report.diagnostics.pricesOlderThanMonth > 0
        )
        MonitoringRow("Senza data di comunicazione", formatMeasure(report.diagnostics.pricesWithoutDate))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Le date sono quelle in cui il gestore ha comunicato il prezzo al MIMIT, " +
                "non quelle in cui l'app l'ha scaricato.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionSeparator()

        SectionTitle("Azioni")
        OutlinedButton(
            onClick = onForceRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.fillMaxWidth().testTag("monitoring_force_refresh_button")
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Aggiornamento in corso...")
            } else {
                Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Riscarica adesso dalla fonte")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onInvalidateCache,
            modifier = Modifier.fillMaxWidth().testTag("monitoring_invalidate_cache_button")
        ) {
            Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Fai scadere la cache")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCopyReport,
            modifier = Modifier.fillMaxWidth().testTag("monitoring_copy_button")
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Copia il rapporto")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Nessuna di queste azioni cancella dati: i distributori già scaricati restano " +
                "visibili anche se la fonte non risponde.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Esito dell'ultimo tentativo, in evidenza: è la sola riga che si guarda quando qualcosa non va. */
@Composable
private fun OutcomeBanner(report: MonitoringReport) {
    val (icon, tint) = when (report.diagnostics.outcome) {
        RefreshDiagnostics.Outcome.SUCCESS -> Icons.Filled.CheckCircle to EcoGreenPrimary
        RefreshDiagnostics.Outcome.UNCHANGED -> Icons.Filled.CheckCircle to SorrentoBlue
        RefreshDiagnostics.Outcome.FAILED -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        RefreshDiagnostics.Outcome.NEVER -> Icons.Filled.HelpOutline to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = report.diagnostics.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            modifier = Modifier.testTag("monitoring_outcome")
        )
    }
}

@Composable
private fun SectionSeparator() {
    Spacer(modifier = Modifier.height(14.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(14.dp))
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = EcoGreenPrimary
    )
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun MonitoringRow(
    label: String,
    value: String,
    highlight: Boolean = false,
    testTag: String? = null
) {
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
            // Il monospaziato allinea le cifre in colonna: si confrontano a colpo d'occhio.
            fontFamily = FontFamily.Monospace,
            color = if (highlight) FlameOrange else Color.Unspecified,
            modifier = Modifier
                .weight(1.2f)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        )
    }
}

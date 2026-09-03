package com.example.data.local

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cosa è successo nell'ultimo tentativo di aggiornamento dalla fonte ufficiale.
 *
 * È l'unica memoria che l'app tiene del proprio funzionamento: sopravvive alla chiusura, così un
 * aggiornamento fallito ieri sera è ancora leggibile stamattina. I campi numerici valgono
 * [UNMEASURED] finché non c'è una misura reale — nessuno di essi viene mai stimato.
 */
data class RefreshDiagnostics(
    val attemptedAt: Long = 0L,
    val outcome: Outcome = Outcome.NEVER,
    /** Quale delle due fonti ufficiali ha risposto (API o CSV), vuoto se non ha risposto nessuna. */
    val source: String = "",
    val durationMillis: Long = 0L,
    /** Messaggio d'errore reale della fonte, vuoto quando è andata bene. */
    val message: String = "",
    val stationsWritten: Int = UNMEASURED,
    val duplicatesMerged: Int = UNMEASURED,
    val withoutCoordinates: Int = UNMEASURED,
    val pricesToday: Int = UNMEASURED,
    val pricesWithinWeek: Int = UNMEASURED,
    val pricesOlderThanMonth: Int = UNMEASURED,
    val pricesWithoutDate: Int = UNMEASURED
) {
    enum class Outcome {
        /** Nessun tentativo registrato su questo device. */
        NEVER,
        SUCCESS,

        /** La fonte ha risposto "non è cambiato niente" (HTTP 304): Room era già aggiornato. */
        UNCHANGED,
        FAILED
    }

    val label: String
        get() = when (outcome) {
            Outcome.NEVER -> "Mai tentato"
            Outcome.SUCCESS -> "Riuscito"
            Outcome.UNCHANGED -> "Dati già aggiornati"
            Outcome.FAILED -> "Fallito"
        }

    companion object {
        /** Valore mai misurato: si mostra come "—", mai come zero. */
        const val UNMEASURED = -1
    }
}

/**
 * Stato del funzionamento dell'app in un dato istante: la diagnostica dell'ultimo aggiornamento
 * più i conteggi letti sul momento da Room, che cambiano anche fuori dal refresh (un preferito
 * aggiunto, una segnalazione di prezzo, un distributore inserito a mano).
 */
data class MonitoringReport(
    val diagnostics: RefreshDiagnostics = RefreshDiagnostics(),
    val lastRefreshTimestamp: Long? = null,
    val cacheTtlMillis: Long = 0L,
    val totalStations: Int = 0,
    val officialStations: Int = 0,
    val userStations: Int = 0,
    val favorites: Int = 0,
    val priceReports: Int = 0,
    val withoutCoordinates: Int = 0,
    val csvLastModified: String? = null,
    val generatedAt: Long = 0L
) {
    /** Millisecondi che mancano alla scadenza della cache; 0 se è già scaduta o mai riempita. */
    fun cacheRemainingMillis(now: Long): Long {
        val last = lastRefreshTimestamp ?: return 0L
        return (last + cacheTtlMillis - now).coerceAtLeast(0L)
    }

    val isCacheValid: Boolean get() = cacheRemainingMillis(generatedAt) > 0L
}

/** "—" per i valori mai misurati: uno zero direbbe una cosa falsa. */
fun formatMeasure(value: Int): String =
    if (value == RefreshDiagnostics.UNMEASURED) "—" else value.toString()

fun formatItalianDateTime(timestampMillis: Long?): String {
    if (timestampMillis == null || timestampMillis <= 0L) return "mai"
    return SimpleDateFormat("dd/MM/yyyy 'alle' HH:mm", Locale.ITALIAN).format(Date(timestampMillis))
}

/** Distanza in linguaggio naturale fra due istanti, arrotondata per difetto all'unità utile. */
fun formatElapsed(fromMillis: Long?, nowMillis: Long): String {
    if (fromMillis == null || fromMillis <= 0L) return ""
    val seconds = (nowMillis - fromMillis) / 1000
    return when {
        seconds < 0 -> ""
        seconds < 60 -> "meno di un minuto fa"
        seconds < 3600 -> "${seconds / 60} min fa"
        seconds < 86_400 -> "${seconds / 3600} h fa"
        else -> "${seconds / 86_400} g fa"
    }
}

fun formatDuration(millis: Long): String = when {
    millis <= 0L -> "—"
    millis < 1000L -> "$millis ms"
    else -> String.format(Locale.ITALIAN, "%.1f s", millis / 1000.0)
}

/**
 * Il rapporto in testo semplice, così com'è mostrato: serve al pulsante "copia", per poterlo
 * incollare in una segnalazione senza doverlo ribattere a mano da uno screenshot.
 */
fun MonitoringReport.asPlainText(): String = buildString {
    appendLine("GPL Campania — diagnostica del ${formatItalianDateTime(generatedAt)}")
    appendLine()
    appendLine("[Ultimo aggiornamento]")
    appendLine("esito: ${diagnostics.label}")
    appendLine("quando: ${formatItalianDateTime(diagnostics.attemptedAt.takeIf { it > 0 })}")
    appendLine("fonte: ${diagnostics.source.ifBlank { "—" }}")
    appendLine("durata: ${formatDuration(diagnostics.durationMillis)}")
    if (diagnostics.message.isNotBlank()) appendLine("messaggio: ${diagnostics.message}")
    appendLine("cache: ${if (isCacheValid) "valida (${cacheRemainingMillis(generatedAt) / 60_000} min residui)" else "scaduta"}")
    appendLine("Last-Modified CSV: ${csvLastModified ?: "—"}")
    appendLine()
    appendLine("[Dati sul dispositivo]")
    appendLine("distributori totali: $totalStations")
    appendLine("da fonte ufficiale: $officialStations")
    appendLine("aggiunti a mano: $userStations")
    appendLine("preferiti: $favorites")
    appendLine("segnalazioni prezzo: $priceReports")
    appendLine("senza coordinate reali: $withoutCoordinates")
    appendLine()
    appendLine("[Qualità dell'ultimo scarico]")
    appendLine("righe scritte: ${formatMeasure(diagnostics.stationsWritten)}")
    appendLine("doppioni uniti: ${formatMeasure(diagnostics.duplicatesMerged)}")
    appendLine("senza coordinate ufficiali: ${formatMeasure(diagnostics.withoutCoordinates)}")
    appendLine("prezzi comunicati oggi: ${formatMeasure(diagnostics.pricesToday)}")
    appendLine("negli ultimi 7 giorni: ${formatMeasure(diagnostics.pricesWithinWeek)}")
    appendLine("oltre 30 giorni fa: ${formatMeasure(diagnostics.pricesOlderThanMonth)}")
    appendLine("senza data di comunicazione: ${formatMeasure(diagnostics.pricesWithoutDate)}")
}

package com.example.data.remote

import com.example.data.util.distanceMeters
import java.util.Locale

/**
 * Distanza massima entro cui due iscrizioni allo stesso indirizzo sono considerate la stessa pompa.
 *
 * Misurata sui dati reali: le due coppie doppie della Campania distano 0 m e 11 m, mentre gli
 * impianti realmente distinti più vicini che condividono la via sono molto oltre. Il vincolo
 * dell'indirizzo identico è la parte che protegge davvero — da solo il criterio della distanza
 * unirebbe pompe diverse affacciate sulla stessa carreggiata (a Mugnano due civici della stessa
 * via distano 15 m).
 */
private const val SAME_PLANT_METERS = 50.0

private val NON_ALPHANUMERIC = Regex("[^a-z0-9]")

/**
 * Unisce le iscrizioni doppie dell'anagrafica ufficiale.
 *
 * Capita che lo stesso distributore risulti attivo con due `idImpianto` diversi — tipicamente una
 * re-iscrizione dopo un cambio di ragione sociale, con la vecchia mai chiusa: in Campania succede
 * a "Fratelli Longobardi" di Pompei (stesse coordinate al centimetro) e alla stazione Eni di via
 * Pomigliano a Somma Vesuviana (11 m, una delle due ferma da un mese).
 *
 * Non si fondono i dati dei due record: se ne **sceglie uno**, quello con la comunicazione di
 * prezzo più recente (a parità, il prezzo più basso; poi l'iscrizione più recente). Quello che
 * resta è quindi sempre un record reale della fonte, mai un ibrido.
 */
internal fun List<RemoteGplStation>.mergeDuplicatePlants(): List<RemoteGplStation> {
    if (size < 2) return this

    val kept = LinkedHashMap<String, MutableList<RemoteGplStation>>()
    for (station in this) {
        val group = kept.getOrPut(station.addressKey()) { mutableListOf() }
        val twinIndex = group.indexOfFirst { it.isSamePlantAs(station) }
        if (twinIndex < 0) {
            group += station
        } else if (station.isFresherThan(group[twinIndex])) {
            group[twinIndex] = station
        }
    }
    return kept.values.flatten()
}

/** Comune + via ridotti a lettere e cifre: assorbe punteggiatura e spaziatura diverse. */
private fun RemoteGplStation.addressKey(): String =
    NON_ALPHANUMERIC.replace((comune + "|" + via).lowercase(Locale.ROOT), "").ifBlank { "id$impiantoId" }

/**
 * Due record allo stesso indirizzo sono la stessa pompa solo se anche le coordinate ufficiali
 * coincidono. Senza coordinate non si decide: restano separati, perché unire il record sbagliato
 * farebbe sparire un distributore che esiste.
 */
private fun RemoteGplStation.isSamePlantAs(other: RemoteGplStation): Boolean {
    val lat = latitude ?: return false
    val lng = longitude ?: return false
    val otherLat = other.latitude ?: return false
    val otherLng = other.longitude ?: return false
    return distanceMeters(lat, lng, otherLat, otherLng) <= SAME_PLANT_METERS
}

/** Il record da tenere: prezzo comunicato più di recente, poi più basso, poi iscrizione più nuova. */
private fun RemoteGplStation.isFresherThan(other: RemoteGplStation): Boolean = when {
    priceDay != other.priceDay -> priceDay > other.priceDay
    gplPrice != other.gplPrice -> gplPrice < other.gplPrice
    else -> impiantoId > other.impiantoId
}

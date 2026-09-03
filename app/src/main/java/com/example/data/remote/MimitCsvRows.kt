package com.example.data.remote

import java.io.BufferedReader

/**
 * Lettura riga-per-riga dei CSV open data del MIMIT, condivisa da chi li usa.
 *
 * I due file (`prezzo_alle_8.csv` e `anagrafica_impianti_attivi.csv`) hanno la stessa forma:
 * delimitatore `|`, una prima riga di intestazione libera (`Estrazione del …`) da scartare e una
 * seconda riga con i nomi delle colonne. Coprono l'Italia intera e pesano ~7,5 MB complessivi senza
 * compressione, quindi non vanno mai materializzati in memoria: si scorrono e si trattiene solo il
 * poco che serve.
 */
internal const val CSV_DELIMITER = '|'

/** Colonna presente in entrambi i file: è anche il marcatore della riga di intestazione. */
internal const val CSV_ID_COLUMN = "idImpianto"

/** BOM UTF-8: se presente, sta solo in testa alla primissima riga del file, ma toglierlo da ogni
 * riga costa nulla ed evita che un file con BOM faccia fallire silenziosamente il riconoscimento
 * dell'intestazione (nessuna riga letta, nessun errore esplicito). */
private val UTF8_BOM = 0xFEFF.toChar().toString()

internal inline fun forEachCsvRow(
    reader: BufferedReader,
    onRow: (columns: List<String>, header: Map<String, Int>) -> Unit
) {
    var header: Map<String, Int>? = null
    var line = reader.readLine()
    while (line != null) {
        val cleaned = line.removePrefix(UTF8_BOM)
        val current = header
        if (current == null) {
            if (cleaned.startsWith(CSV_ID_COLUMN)) {
                header = cleaned.split(CSV_DELIMITER)
                    .withIndex()
                    .associate { (index, name) -> name.trim() to index }
            }
        } else if (cleaned.isNotBlank()) {
            onRow(cleaned.split(CSV_DELIMITER), current)
        }
        line = reader.readLine()
    }
}

internal fun List<String>.csvValue(header: Map<String, Int>, name: String): String =
    header[name]?.let { getOrNull(it) }?.trim().orEmpty()

/** Coordinata reale o `null`: uno 0.0 nell'anagrafica significa "non rilevata", non "equatore". */
internal fun List<String>.csvCoordinate(header: Map<String, Int>, name: String): Double? =
    csvValue(header, name).toDoubleOrNull()?.takeIf { !it.isNaN() && it != 0.0 }

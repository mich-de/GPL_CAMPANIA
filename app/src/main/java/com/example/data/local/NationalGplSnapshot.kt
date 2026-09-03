package com.example.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.data.remote.ItalianRegions
import com.example.data.remote.NationalGplStats
import com.example.data.remote.RegionGplAverage
import com.example.data.remote.epochDayOf
import java.util.Locale

/**
 * Una lettura del GPL in Italia, conservata sul device.
 *
 * Serve a una cosa che nessuna singola lettura può dare: l'andamento. La fonte pubblica solo la
 * situazione di stamattina, quindi l'unico modo onesto di dire "−1,2% in una settimana" è aver
 * salvato la fotografia di una settimana fa. Ogni riga è un giorno di pubblicazione reale
 * (`dayKey`), mai un giorno interpolato per riempire un buco.
 */
@Entity(tableName = "national_gpl_snapshots")
data class NationalGplSnapshot(
    /** Giorno di pubblicazione del CSV in forma `aaaammgg`: due letture dello stesso giorno sono
     * lo stesso dato, quindi la seconda sovrascrive la prima invece di aggiungersi. */
    @PrimaryKey val dayKey: Int,
    val capturedAt: Long,
    val averagePrice: Double,
    val medianPrice: Double,
    val stationCount: Int,
    val skippedRows: Int,
    /** Classifica regionale serializzata: `regione:media:impianti;…`, dalla più economica. */
    val regionsEncoded: String
) {
    val regions: List<RegionGplAverage> get() = decodeRegions(regionsEncoded)
}

@Dao
interface NationalStatsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: NationalGplSnapshot)

    @Query("SELECT * FROM national_gpl_snapshots ORDER BY dayKey DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<NationalGplSnapshot>

    @Query("SELECT COUNT(*) FROM national_gpl_snapshots")
    suspend fun count(): Int
}

/** Scostamento fra la lettura più recente e una precedente, con quanti giorni le separano davvero. */
data class PriceChange(val fromDayKey: Int, val daysApart: Int, val delta: Double) {
    fun percent(reference: Double): Double = if (reference > 0) delta / (reference - delta) * 100 else 0.0
}

/**
 * Andamento ricostruito dalle fotografie conservate. Entrambi i confronti sono `null` finché non
 * esiste una lettura abbastanza vecchia: meglio non dire niente che dedurre una tendenza da due
 * giorni di storico.
 */
data class NationalGplTrend(
    val sinceWeek: PriceChange? = null,
    val sinceMonth: PriceChange? = null,
    val snapshotCount: Int = 0
)

/**
 * [history] va passata dalla più recente alla più vecchia. Per ogni orizzonte si prende la lettura
 * **più recente fra quelle abbastanza lontane**: se manca il giorno esatto di sette giorni fa si usa
 * quella di otto o nove, e il numero di giorni effettivi viaggia con il risultato invece di essere
 * arrotondato a "una settimana".
 */
fun computeTrend(history: List<NationalGplSnapshot>): NationalGplTrend {
    val latest = history.firstOrNull() ?: return NationalGplTrend()
    return NationalGplTrend(
        sinceWeek = changeSince(latest, history, minDays = 7),
        sinceMonth = changeSince(latest, history, minDays = 30),
        snapshotCount = history.size
    )
}

private fun changeSince(
    latest: NationalGplSnapshot,
    history: List<NationalGplSnapshot>,
    minDays: Int
): PriceChange? {
    val latestDay = epochDayOf(latest.dayKey) ?: return null
    for (older in history) {
        val olderDay = epochDayOf(older.dayKey) ?: continue
        val gap = latestDay - olderDay
        if (gap >= minDays) {
            return PriceChange(
                fromDayKey = older.dayKey,
                daysApart = gap.toInt(),
                delta = latest.averagePrice - older.averagePrice
            )
        }
    }
    return null
}

/** La regione dell'app dentro la classifica nazionale, se la lettura la contiene. */
fun NationalGplSnapshot.homeRegion(): RegionGplAverage? =
    regions.firstOrNull { it.region == ItalianRegions.HOME_REGION }

fun NationalGplSnapshot.homeRegionRank(): Int =
    regions.indexOfFirst { it.region == ItalianRegions.HOME_REGION } + 1

fun NationalGplStats.toSnapshot(capturedAt: Long): NationalGplSnapshot = NationalGplSnapshot(
    dayKey = publishedDayKey,
    capturedAt = capturedAt,
    averagePrice = averagePrice,
    medianPrice = medianPrice,
    stationCount = stationCount,
    skippedRows = skippedRows,
    regionsEncoded = encodeRegions(regions)
)

/**
 * Serializzazione compatta della classifica: `Campania:0.7163:426;Lombardia:0.7368:502`.
 * I nomi delle regioni italiane non contengono né `;` né `:`, quindi non serve alcun escaping — e
 * una tabella in più in Room per venti righe al giorno non si giustifica.
 */
fun encodeRegions(regions: List<RegionGplAverage>): String =
    regions.joinToString(";") {
        "%s:%.4f:%d".format(Locale.ROOT, it.region, it.averagePrice, it.stationCount)
    }

fun decodeRegions(encoded: String): List<RegionGplAverage> =
    encoded.split(";").mapNotNull { entry ->
        val parts = entry.split(":")
        if (parts.size != 3) return@mapNotNull null
        val average = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        val count = parts[2].toIntOrNull() ?: return@mapNotNull null
        RegionGplAverage(parts[0], average, count)
    }

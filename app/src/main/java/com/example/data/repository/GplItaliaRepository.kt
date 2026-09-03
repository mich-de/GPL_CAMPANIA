package com.example.data.repository

import android.content.Context
import com.example.data.local.BackendPreferences
import com.example.data.local.NationalGplSnapshot
import com.example.data.local.NationalGplTrend
import com.example.data.local.NationalStatsDao
import com.example.data.local.NewsDao
import com.example.data.local.NewsItem
import com.example.data.local.TankRevision
import com.example.data.local.computeTrend
import com.example.data.local.toSnapshot
import com.example.data.remote.MimitNewsFeed
import com.example.data.remote.NationalGplStatsFetcher
import com.example.data.remote.NationalStatsResult

/** Quante letture nazionali conservare e confrontare: bastano a coprire il mese di storico. */
private const val HISTORY_DEPTH = 60

/** Quante notizie mostrare: la sezione è un promemoria, non un aggregatore. */
private const val NEWS_DEPTH = 15

/**
 * Tutto ciò che l'app sa del "GPL in Italia" in un dato momento, letto dal device.
 *
 * Ogni campo può essere vuoto o `null` e va mostrato così: nessuna media stimata prima di aver letto
 * la fonte, nessuna notizia inventata quando il feed non ha risposto, nessuna scadenza supposta
 * finché l'utente non ha inserito la sua.
 */
data class GplItaliaData(
    val latest: NationalGplSnapshot? = null,
    val trend: NationalGplTrend = NationalGplTrend(),
    val news: List<NewsItem> = emptyList(),
    val newsLastFetch: Long? = null,
    val tank: TankRevision? = null
)

/**
 * Le tre funzioni "Italia" dell'app — i numeri nazionali, le notizie ufficiali e la scadenza del
 * serbatoio — tenute insieme perché condividono una sola regola: niente parte da solo.
 *
 * I numeri nazionali costano ~7,5 MB non comprimibili, quindi si scaricano al massimo una volta al
 * giorno e solo su richiesta esplicita. Le notizie costano ~9 KB, ma restano comunque manuali per
 * non aggiungere traffico a un'app che vive di un refresh ogni quarto d'ora.
 */
class GplItaliaRepository(
    private val context: Context,
    private val statsDao: NationalStatsDao,
    private val newsDao: NewsDao
) {

    /** Quello che c'è già sul device: nessuna chiamata di rete, sempre istantaneo. */
    suspend fun cached(): GplItaliaData {
        val history = statsDao.recent(HISTORY_DEPTH)
        return GplItaliaData(
            latest = history.firstOrNull(),
            trend = computeTrend(history),
            news = newsDao.recent(NEWS_DEPTH),
            newsLastFetch = BackendPreferences.getNewsLastFetch(context),
            tank = BackendPreferences.getTankRevision(context)
        )
    }

    /**
     * Esito di una lettura dei numeri nazionali, dal punto di vista di chi l'ha chiesta.
     */
    enum class StatsOutcome {
        /** Scaricata e salvata una lettura nuova. */
        UPDATED,

        /** La pubblicazione di oggi era già sul device: nessun byte scaricato. */
        ALREADY_TODAY,

        /** La fonte non ha risposto: resta l'ultima lettura reale, se c'è. */
        UNAVAILABLE
    }

    /**
     * Scarica le medie nazionali e ne conserva la fotografia del giorno.
     *
     * Con [force] a `false` non scarica nulla se la pubblicazione di oggi è già stata salvata: la
     * fonte esce una volta la mattina, quindi rileggerla nello stesso giorno costerebbe 7,5 MB per
     * riscrivere gli stessi numeri.
     */
    suspend fun refreshNationalStats(
        force: Boolean = false,
        now: Long = System.currentTimeMillis(),
        todayKey: Int
    ): StatsOutcome {
        if (!force && statsDao.recent(1).firstOrNull()?.dayKey == todayKey) {
            return StatsOutcome.ALREADY_TODAY
        }
        return when (val result = NationalGplStatsFetcher.fetch(context)) {
            is NationalStatsResult.Data -> {
                // Il giorno è quello dichiarato dal `Last-Modified` della fonte. Se manca si usa
                // quello del device: la fotografia va comunque datata, altrimenti tutte le letture
                // finirebbero sulla stessa riga con chiave 0 sovrascrivendosi a vicenda.
                val stats = result.stats
                val dayKey = stats.publishedDayKey.takeIf { it > 0 } ?: todayKey
                statsDao.insert(stats.toSnapshot(now).copy(dayKey = dayKey))
                StatsOutcome.UPDATED
            }

            NationalStatsResult.Unavailable -> StatsOutcome.UNAVAILABLE
        }
    }

    /**
     * Rilegge il feed stampa del MIMIT tenendo solo le voci sui carburanti.
     *
     * Ritorna `false` solo quando la fonte non ha risposto. Una risposta valida senza notizie in
     * tema è un esito normale — succede nella maggior parte delle settimane — e lascia sul device le
     * notizie già salvate, che restano vere anche se non ne sono arrivate di nuove.
     */
    suspend fun refreshNews(now: Long = System.currentTimeMillis()): Boolean {
        val fetched = MimitNewsFeed.fetch(now)
        BackendPreferences.setNewsLastFetch(context, now)
        if (fetched == null) return false
        if (fetched.isNotEmpty()) newsDao.insertAll(fetched)
        return true
    }

    fun saveTankRevision(revision: TankRevision) {
        BackendPreferences.setTankRevision(context, revision)
    }

    fun clearTankRevision() {
        BackendPreferences.clearTankRevision(context)
    }
}

package com.example.ui.components

/**
 * Fascia di prezzo di un distributore rispetto agli altri attualmente in lista.
 *
 * `NEUTRA` non è una fascia intermedia: è l'assenza di giudizio, e serve quando i distributori
 * mostrati sono troppo pochi perché un confronto significhi qualcosa.
 */
enum class PriceTier { ECONOMICO, MEDIO, CARO, NEUTRA }

/**
 * Soglie con cui si colorano i pin sulla mappa.
 *
 * Nascono dai prezzi realmente presenti nella lista mostrata, non da numeri fissati nel codice: una
 * soglia scritta a mano invecchia con il mercato e nel giro di qualche mese dipinge di rosso tutta
 * la regione senza che nulla sia davvero cambiato. Dividendo in terzi la distribuzione reale, il
 * colore continua a dire l'unica cosa che l'utente può verificare — "costa meno degli altri qui e
 * ora" — e cambia solo quando cambiano i prezzi.
 */
data class PriceTiers(
    /** Prezzo di fine primo terzo: fino a qui il pin è verde. `null` se il campione è insufficiente. */
    val cheapMax: Double? = null,
    /** Prezzo di inizio ultimo terzo: da qui in su il pin è rosso. `null` se il campione è insufficiente. */
    val priceyMin: Double? = null,
    /** Quanti prezzi reali sono stati usati per calcolare le soglie. */
    val sampleSize: Int = 0
) {
    /** `true` quando le soglie sono state calcolate su abbastanza distributori da avere un senso. */
    val isMeaningful: Boolean get() = cheapMax != null && priceyMin != null

    fun tierOf(price: Double): PriceTier {
        val cheap = cheapMax
        val pricey = priceyMin
        if (cheap == null || pricey == null || price <= 0.0) return PriceTier.NEUTRA
        return when {
            price <= cheap -> PriceTier.ECONOMICO
            price >= pricey -> PriceTier.CARO
            else -> PriceTier.MEDIO
        }
    }

    companion object {
        /** Sotto questa soglia parlare di "terzo più economico" sarebbe una statistica inventata. */
        const val MIN_SAMPLE = 6
    }
}

/**
 * Divide in tre i prezzi realmente presenti. I prezzi non validi (zero o negativi, che nella fonte
 * indicano un dato mancante) non entrano nel calcolo e non spostano le soglie.
 */
fun computePriceTiers(prices: List<Double>): PriceTiers {
    val sorted = prices.filter { it > 0.0 }.sorted()
    if (sorted.size < PriceTiers.MIN_SAMPLE) return PriceTiers(sampleSize = sorted.size)

    val last = sorted.size - 1
    val cheapMax = sorted[(sorted.size / 3 - 1).coerceIn(0, last)]
    val priceyMin = sorted[(sorted.size * 2 / 3).coerceIn(0, last)]

    // Con prezzi quasi tutti uguali i due estremi possono coincidere: in quel caso non esiste un
    // terzo "caro" da segnalare, e dichiararlo sarebbe una differenza inventata.
    if (cheapMax >= priceyMin) return PriceTiers(sampleSize = sorted.size)

    return PriceTiers(cheapMax = cheapMax, priceyMin = priceyMin, sampleSize = sorted.size)
}

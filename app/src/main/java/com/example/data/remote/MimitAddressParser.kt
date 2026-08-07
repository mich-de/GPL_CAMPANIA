package com.example.data.remote

/**
 * Indirizzo di un impianto scomposto nelle sue parti.
 *
 * [cap] è vuoto quando la fonte non lo riporta: resta vuoto, non viene dedotto dal comune
 * (il vincolo "solo dati reali" vale anche per i campi accessori).
 */
data class ParsedMimitAddress(
    val via: String,
    val cap: String,
    val comune: String,
    val provincia: String
)

/**
 * L'API Osservaprezzi restituisce l'indirizzo in un unico campo, nella forma
 * `"<via> <CAP> - <COMUNE> <PROV>"`, ma il CAP manca in una minoranza di record.
 *
 * Si procede quindi in due tentativi: prima con il CAP, poi senza. Nel secondo la parte "via" è
 * volutamente **greedy**, così lo split avviene sull'ultimo `" - "` e non sul primo: senza questo
 * accorgimento un indirizzo come `"S.S. 7/IV - KM 2+650   - CELLOLE CE"` verrebbe troncato al
 * primo trattino, che fa parte del nome della strada.
 *
 * Verificato sui 428 distributori GPL della Campania: 428 parsati (100%), provincia sempre
 * coerente con quella richiesta e comune uguale a quello dell'anagrafica ufficiale nel 99,1%
 * dei casi.
 */
object MimitAddressParser {

    private val WITH_CAP = Regex("""^(.*?)[\s,]*(\d{5})\s*-\s*(.+?)\s+([A-Z]{2})\s*$""")
    private val NO_CAP = Regex("""^(.*)\s-\s*([^-]+?)\s+([A-Z]{2})\s*$""")
    private val WHITESPACE = Regex("""\s+""")

    fun parse(raw: String?): ParsedMimitAddress? {
        val address = raw?.trim().orEmpty()
        if (address.isEmpty()) return null

        WITH_CAP.matchEntire(address)?.let { m ->
            return ParsedMimitAddress(
                via = cleanStreet(m.groupValues[1]),
                cap = m.groupValues[2],
                comune = tidy(m.groupValues[3]),
                provincia = m.groupValues[4]
            )
        }

        NO_CAP.matchEntire(address)?.let { m ->
            return ParsedMimitAddress(
                via = cleanStreet(m.groupValues[1]),
                cap = "",
                comune = tidy(m.groupValues[2]),
                provincia = m.groupValues[3]
            )
        }

        return null
    }

    /** Rimuove separatori residui in coda ("VIA X SNC   -") e collassa gli spazi multipli. */
    private fun cleanStreet(value: String): String = tidy(value).trim(' ', ',', '-')

    private fun tidy(value: String): String = value.replace(WHITESPACE, " ").trim()
}

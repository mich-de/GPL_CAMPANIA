package com.example.data.remote

/**
 * Sigla della provincia -> regione, per le 107 province italiane in cui è divisa l'anagrafica
 * ufficiale del MIMIT.
 *
 * Serve perché i CSV open data pubblicano solo la sigla: la regione, che è il livello a cui ha
 * senso confrontare i prezzi, va ricostruita qui. Una sigla che non compare in questa tabella non
 * viene attribuita a nessuna regione e finisce fra le righe scartate — nell'anagrafica reale capita
 * con una manciata di record in cui la colonna `Provincia` contiene per errore un comune o un pezzo
 * di indirizzo, e in quel caso l'unico comportamento onesto è escluderli e dirlo.
 *
 * Le sigle sono quelle in vigore, confrontate una a una con i codici che l'anagrafica usa davvero:
 * combaciano esattamente. Le province soppresse nel 2016 (VS, OT, OG, CI) non compaiono nella fonte
 * e quindi non compaiono qui.
 */
object ItalianRegions {

    private val BY_PROVINCE = mapOf(
        "AG" to "Sicilia", "AL" to "Piemonte", "AN" to "Marche", "AO" to "Valle d'Aosta",
        "AP" to "Marche", "AQ" to "Abruzzo", "AR" to "Toscana", "AT" to "Piemonte",
        "AV" to "Campania", "BA" to "Puglia", "BG" to "Lombardia", "BI" to "Piemonte",
        "BL" to "Veneto", "BN" to "Campania", "BO" to "Emilia-Romagna", "BR" to "Puglia",
        "BS" to "Lombardia", "BT" to "Puglia", "BZ" to "Trentino-Alto Adige", "CA" to "Sardegna",
        "CB" to "Molise", "CE" to "Campania", "CH" to "Abruzzo", "CL" to "Sicilia",
        "CN" to "Piemonte", "CO" to "Lombardia", "CR" to "Lombardia", "CS" to "Calabria",
        "CT" to "Sicilia", "CZ" to "Calabria", "EN" to "Sicilia", "FC" to "Emilia-Romagna",
        "FE" to "Emilia-Romagna", "FG" to "Puglia", "FI" to "Toscana", "FM" to "Marche",
        "FR" to "Lazio", "GE" to "Liguria", "GO" to "Friuli-Venezia Giulia", "GR" to "Toscana",
        "IM" to "Liguria", "IS" to "Molise", "KR" to "Calabria", "LC" to "Lombardia",
        "LE" to "Puglia", "LI" to "Toscana", "LO" to "Lombardia", "LT" to "Lazio",
        "LU" to "Toscana", "MB" to "Lombardia", "MC" to "Marche", "ME" to "Sicilia",
        "MI" to "Lombardia", "MN" to "Lombardia", "MO" to "Emilia-Romagna", "MS" to "Toscana",
        "MT" to "Basilicata", "NA" to "Campania", "NO" to "Piemonte", "NU" to "Sardegna",
        "OR" to "Sardegna", "PA" to "Sicilia", "PC" to "Emilia-Romagna", "PD" to "Veneto",
        "PE" to "Abruzzo", "PG" to "Umbria", "PI" to "Toscana", "PN" to "Friuli-Venezia Giulia",
        "PO" to "Toscana", "PR" to "Emilia-Romagna", "PT" to "Toscana", "PU" to "Marche",
        "PV" to "Lombardia", "PZ" to "Basilicata", "RA" to "Emilia-Romagna", "RC" to "Calabria",
        "RE" to "Emilia-Romagna", "RG" to "Sicilia", "RI" to "Lazio", "RM" to "Lazio",
        "RN" to "Emilia-Romagna", "RO" to "Veneto", "SA" to "Campania", "SI" to "Toscana",
        "SO" to "Lombardia", "SP" to "Liguria", "SR" to "Sicilia", "SS" to "Sardegna",
        "SU" to "Sardegna", "SV" to "Liguria", "TA" to "Puglia", "TE" to "Abruzzo",
        "TN" to "Trentino-Alto Adige", "TO" to "Piemonte", "TP" to "Sicilia", "TR" to "Umbria",
        "TS" to "Friuli-Venezia Giulia", "TV" to "Veneto", "UD" to "Friuli-Venezia Giulia",
        "VA" to "Lombardia", "VB" to "Piemonte", "VC" to "Piemonte", "VE" to "Veneto",
        "VI" to "Veneto", "VR" to "Veneto", "VT" to "Lazio",
        "VV" to "Calabria"
    )

    /** Regione della provincia, o `null` se la sigla non è riconoscibile. */
    fun of(provinceCode: String): String? = BY_PROVINCE[provinceCode.trim().uppercase()]

    /** Le regioni distinte coperte dalla tabella: le 20 italiane. */
    val all: List<String> = BY_PROVINCE.values.distinct().sorted()

    /** Regione dell'app: è il riferimento rispetto a cui si legge ogni confronto nazionale. */
    const val HOME_REGION = "Campania"

    internal val provinceCodes: Set<String> get() = BY_PROVINCE.keys
}

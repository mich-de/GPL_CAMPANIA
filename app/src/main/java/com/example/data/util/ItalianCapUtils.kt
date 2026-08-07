package com.example.data.util

object ItalianCapUtils {

    // Map of standard CAPs for Penisola Sorrentina and surrounding Campania areas
    private val capToCityMap = mapOf(
        "80063" to Pair("Piano di Sorrento", "NA"),
        "80067" to Pair("Sorrento", "NA"),
        "80065" to Pair("Sant'Agnello", "NA"),
        "80062" to Pair("Meta", "NA"),
        "80069" to Pair("Vico Equense", "NA"),
        "80061" to Pair("Massa Lubrense", "NA"),
        "80053" to Pair("Castellammare di Stabia", "NA"),
        "80045" to Pair("Pompei", "NA"),
        "80058" to Pair("Torre Annunziata", "NA"),
        "80059" to Pair("Torre del Greco", "NA"),
        "80055" to Pair("Portici", "NA"),
        "80056" to Pair("Ercolano", "NA"),
        "80121" to Pair("Napoli", "NA"),
        "80122" to Pair("Napoli", "NA"),
        "80123" to Pair("Napoli", "NA"),
        "80125" to Pair("Napoli", "NA"),
        "80133" to Pair("Napoli", "NA"),
        "80142" to Pair("Napoli", "NA"),
        "84121" to Pair("Salerno", "SA"),
        "84011" to Pair("Amalfi", "SA"),
        "84010" to Pair("Positano", "SA")
    )

    data class ParsedAddress(
        val streetAddress: String,
        val cap: String,
        val city: String,
        val province: String,
        val fullFormattedAddress: String
    )

    fun parseDescription(description: String): ParsedAddress {
        // Example description: "G. MARESCA 33 , 80063 PIANO DI SORRENTO - myLPG.eu"
        var cleanDesc = description.replace("<![CDATA[", "").replace("]]>", "").trim()
        if (cleanDesc.contains("- myLPG.eu", ignoreCase = true)) {
            cleanDesc = cleanDesc.substringBefore("- myLPG.eu").trim()
        }

        val capRegex = Regex("""\b(\d{5})\b""")
        val capMatch = capRegex.find(cleanDesc)
        
        var cap = ""
        var city = "Campania"
        var province = "NA"

        if (capMatch != null) {
            cap = capMatch.value
            capToCityMap[cap]?.let { (c, p) ->
                city = c
                province = p
            }
        }

        // Check if city name is mentioned in description directly
        val upperDesc = cleanDesc.uppercase()
        when {
            upperDesc.contains("PIANO DI SORRENTO") -> { city = "Piano di Sorrento"; province = "NA" }
            upperDesc.contains("SANT'AGNELLO") || upperDesc.contains("SANT AGNELLO") -> { city = "Sant'Agnello"; province = "NA" }
            upperDesc.contains("SORRENTO") -> { city = "Sorrento"; province = "NA" }
            upperDesc.contains("META DI SORRENTO") || upperDesc.contains("META") -> { city = "Meta"; province = "NA" }
            upperDesc.contains("VICO EQUENSE") -> { city = "Vico Equense"; province = "NA" }
            upperDesc.contains("CASTELLAMMARE") || upperDesc.contains("STABIA") -> { city = "Castellammare di Stabia"; province = "NA" }
            upperDesc.contains("POMPEI") -> { city = "Pompei"; province = "NA" }
            upperDesc.contains("NAPOLI") -> { city = "Napoli"; province = "NA" }
            upperDesc.contains("SALERNO") -> { city = "Salerno"; province = "SA" }
        }

        // Format street address cleanly
        var street = cleanDesc
        if (cleanDesc.contains(",")) {
            val parts = cleanDesc.split(",")
            street = parts[0].trim()
        } else if (cap.isNotBlank()) {
            street = cleanDesc.substringBefore(cap).trim()
        }

        // Normalize street prefix
        val lowerStreet = street.lowercase()
        if (!lowerStreet.startsWith("via") && !lowerStreet.startsWith("viale") && !lowerStreet.startsWith("corso") && !lowerStreet.startsWith("piazza") && !lowerStreet.startsWith("ss") && !lowerStreet.startsWith("strada")) {
            street = "Via " + street
        }

        val fullFormatted = if (cap.isNotBlank()) {
            "$street, $cap $city ($province)"
        } else {
            "$street, $city"
        }

        return ParsedAddress(
            streetAddress = street,
            cap = cap,
            city = city,
            province = province,
            fullFormattedAddress = fullFormatted
        )
    }

    data class ParsedPriceInfo(
        val price: Double,
        val lastUpdated: String
    )

    fun extractPriceAndDate(text: String, defaultPrice: Double = 0.718): ParsedPriceInfo {
        var foundPrice = defaultPrice
        var lastUpdated = "Aggiornato da myLPG.eu"

        // Search for price patterns like 0.718, 0,718, 0.72 €/l, EUR 0.719
        val priceRegex = Regex("""(?:0[.,]\d{2,3})""")
        val priceMatch = priceRegex.find(text)
        if (priceMatch != null) {
            try {
                val parsedVal = priceMatch.value.replace(",", ".").toDouble()
                if (parsedVal in 0.50..1.20) {
                    foundPrice = parsedVal
                }
            } catch (_: Exception) {}
        }

        // Search for date patterns like 22.07.2026 or 22/07/2026 or 2026-07-22
        val dateRegex = Regex("""(\d{1,2}[./-]\d{1,2}[./-]\d{2,4})""")
        val dateMatch = dateRegex.find(text)
        if (dateMatch != null) {
            lastUpdated = "myLPG.eu (${dateMatch.value})"
        } else if (text.contains("oggi", ignoreCase = true) || text.contains("today", ignoreCase = true)) {
            lastUpdated = "Oggi (myLPG.eu)"
        }

        return ParsedPriceInfo(
            price = foundPrice,
            lastUpdated = lastUpdated
        )
    }
}


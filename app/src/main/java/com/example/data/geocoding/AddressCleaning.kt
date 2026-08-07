package com.example.data.geocoding

import java.text.Normalizer

/**
 * Porting 1:1 delle funzioni di pulizia indirizzo di `app/geocoding.py`.
 * Stessi pattern esatti, stesso ordine di applicazione: nessuna euristica nuova.
 */
object AddressCleaning {

    fun normalizeAddress(address: String, city: String): String {
        val text = "$address, $city, Campania, Italia"
        val nfkd = Normalizer.normalize(text, Normalizer.Form.NFKD)
        val ascii = nfkd
            .replace(Regex("\\p{Mn}"), "")
            .replace(Regex("[^\\x00-\\x7F]"), "")
        return ascii.replace(Regex("\\s+"), " ").trim().lowercase()
    }

    fun cleanStreet(address: String): String {
        var text = address.replace(Regex("\\b\\d{5}\\b\\s*$"), "")
        text = text.replace(Regex("\\bs\\.?\\s*n\\.?\\s*c\\.?\\b", RegexOption.IGNORE_CASE), "")
        return text.replace(Regex("\\s+"), " ").trim().trim(' ', ',')
    }

    fun stripRoadMarkers(street: String): String {
        var text = street
        text = text.replace(Regex("km\\.?\\s*[\\d.,]+(\\s*\\+\\s*[\\d.,]+)?", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("\\b(dir\\.?|direzione|verso)\\s+[\\wàèéìòù']+", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("\\b(nord|sud|est|ovest)\\b", RegexOption.IGNORE_CASE), "")
        return text.replace(Regex("\\s+"), " ").trim().trim(' ', ',', '-')
    }

    fun extractMotorwayCode(street: String): String? {
        val match = Regex("\\bA\\d{1,2}\\b", RegexOption.IGNORE_CASE).find(street)
        return match?.value?.uppercase()
    }

    fun extractCap(address: String): String? {
        val match = Regex("\\b(\\d{5})\\b\\s*$").find(address.trim())
        return match?.groupValues?.get(1)
    }

    fun stripInitials(street: String): String {
        return street.replace(Regex("\\b[A-Za-zÀ-ÿ]\\.\\s*"), "").trim()
    }
}

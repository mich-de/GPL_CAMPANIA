package com.example.data.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Distanza in metri fra due punti, senza arrotondamenti.
 *
 * Serve dove la soglia conta al metro: riaccoppiare un preferito al suo impianto dopo un cambio di
 * id, o riconoscere due iscrizioni della stessa pompa. La `calculateDistanceKm` di `GplViewModel`
 * non va bene per questo: è arrotondata a 100 m perché nata per l'etichetta in lista.
 */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
}

package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gpl_stations")
data class GplStation(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String, // e.g. "Eni", "IP", "Beyfin", "Q8", "Tamoil", "Enerpetroli", "Pompe Bianche"
    val address: String,
    val city: String, // e.g. "Vico Equense", "Piano di Sorrento", "Castellammare di Stabia", "Pompei", "Napoli", "Salerno"
    val province: String, // "NA" or "SA" or "CE" or "AV" or "BN"
    val latitude: Double?, // null se l'indirizzo non è stato geocodificato con successo (mai una posizione inventata)
    val longitude: Double?,
    val gplPrice: Double, // Price in EUR per Liter, e.g. 0.719
    val priceLastUpdated: String, // e.g. "Oggi, 08:30" or "22 Lug 2026"
    val isOpening24h: Boolean? = null, // null se l'orario non è noto (mai un default inventato)
    val openHoursWeekday: String? = null,
    val openHoursSunday: String? = null,
    val isOpenNow: Boolean? = null,
    val services: String, // Comma-separated: "GPL,Servito,Self 24h,Bar,Lavaggio,Bancomat,Aria/Acqua"
    val isFavorite: Boolean = false,
    val phone: String = "",
    val notes: String = ""
)

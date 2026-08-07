package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_reports")
data class UserPriceReport(
    @PrimaryKey(autoGenerate = true) val reportId: Long = 0,
    val stationId: String,
    val reportedGplPrice: Double,
    val reporterName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

package com.example.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Una notizia ufficiale che riguarda i carburanti, così come pubblicata dalla fonte.
 *
 * Titolo e sommario non vengono riscritti né riassunti: sono quelli del ministero, con il link
 * originale sempre a disposizione. La chiave è il link proprio per questo — è l'identità della
 * notizia, e ripescarla due volte non la duplica.
 */
@Entity(tableName = "news_items")
data class NewsItem(
    @PrimaryKey val link: String,
    val title: String,
    val summary: String,
    /** Data di pubblicazione dichiarata dalla fonte (epoch millis), 0 se assente. */
    val publishedAt: Long,
    val source: String,
    val fetchedAt: Long
)

@Dao
interface NewsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<NewsItem>)

    @Query("SELECT * FROM news_items ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<NewsItem>

    @Query("SELECT COUNT(*) FROM news_items")
    suspend fun count(): Int
}

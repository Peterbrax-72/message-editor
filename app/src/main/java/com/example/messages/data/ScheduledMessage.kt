package com.example.messages.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** status: "pending", "sent", "missed" (app was not default at fire time) or "failed" */
@Entity(tableName = "scheduled")
data class ScheduledMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipient: String,
    val body: String,
    val fireAt: Long,
    val status: String = "pending"
)

@Dao
interface ScheduledDao {
    @Query("SELECT * FROM scheduled ORDER BY fireAt DESC")
    fun observeAll(): Flow<List<ScheduledMessage>>

    @Query("SELECT * FROM scheduled WHERE status = 'pending'")
    suspend fun pending(): List<ScheduledMessage>

    @Query("SELECT * FROM scheduled WHERE id = :id")
    suspend fun get(id: Long): ScheduledMessage?

    @Insert
    suspend fun insert(item: ScheduledMessage): Long

    @Query("UPDATE scheduled SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("DELETE FROM scheduled WHERE id = :id")
    suspend fun delete(id: Long)
}

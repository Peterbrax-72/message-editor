package com.example.messages.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE folder = :folder ORDER BY createdAt DESC")
    fun getByFolder(folder: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long

    @Update
    suspend fun update(message: Message)

    @Delete
    suspend fun delete(message: Message)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): Message?

    @Query("SELECT * FROM messages WHERE synced = 0")
    suspend fun getUnsynced(): List<Message>

    @Query("UPDATE messages SET synced = 1, sysId = :sysId WHERE id = :id")
    suspend fun markSynced(id: Long, sysId: Long)
}

package com.example.messages.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipient: String,
    val body: String,
    val date: String,      // yyyy-MM-dd
    val time: String,      // HH:mm
    val folder: String,    // "inbox", "outbox", "draft"
    val createdAt: Long = System.currentTimeMillis()
)

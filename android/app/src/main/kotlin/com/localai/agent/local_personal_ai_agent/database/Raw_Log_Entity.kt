package com.localai.agent.local_personal_ai_agent.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Raw_Log")
data class Raw_Log_Entity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val Source_App: String,
    val Title: String,
    val Raw_Text: String,
    val Timestamp: Long,
    val Is_Processed: Boolean = false
)

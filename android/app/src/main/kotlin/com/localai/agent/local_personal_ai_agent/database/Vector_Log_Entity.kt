package com.localai.agent.local_personal_ai_agent.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

class Float_Array_Converter {
    @TypeConverter
    fun From_Float_Array(Array: FloatArray?): String? {
        return Array?.joinToString(",")
    }

    @TypeConverter
    fun To_Float_Array(Value: String?): FloatArray? {
        if (Value.isNullOrEmpty()) return null
        return Value.split(",").map { it.toFloat() }.toFloatArray()
    }
}

@Entity(tableName = "Vector_Log")
data class Vector_Log_Entity(
    @PrimaryKey(autoGenerate = true) val Id: Long = 0,
    val Raw_Log_Id: Long,
    val Embedding: FloatArray,
    val Structured_Entity: String,
    val Timestamp: Long
)

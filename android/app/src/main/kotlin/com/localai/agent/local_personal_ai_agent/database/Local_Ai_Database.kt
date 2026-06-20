package com.localai.agent.local_personal_ai_agent.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Raw_Log_Entity::class, Vector_Log_Entity::class], version = 1, exportSchema = false)
@TypeConverters(Float_Array_Converter::class)
abstract class Local_Ai_Database : RoomDatabase() {
    abstract fun Log_Dao(): Log_Dao

    companion object {
        @Volatile
        private var Instance: Local_Ai_Database? = null

        fun Get_Database(Context: Context): Local_Ai_Database {
            return Instance ?: synchronized(this) {
                val Created_Instance = Room.databaseBuilder(
                    Context.applicationContext,
                    Local_Ai_Database::class.java,
                    "local_ai_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                Instance = Created_Instance
                Created_Instance
            }
        }
    }
}

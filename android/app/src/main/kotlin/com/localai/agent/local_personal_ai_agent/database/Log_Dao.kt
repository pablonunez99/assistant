package com.localai.agent.local_personal_ai_agent.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface Log_Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun Insert_Raw_Log(Log: Raw_Log_Entity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun Insert_Vector_Log(Log: Vector_Log_Entity): Long

    @Query("SELECT * FROM Raw_Log WHERE Is_Processed = 0 ORDER BY Timestamp ASC")
    fun Get_Unprocessed_Raw_Logs(): List<Raw_Log_Entity>

    @Query("UPDATE Raw_Log SET Is_Processed = 1 WHERE Id IN (:Ids)")
    fun Mark_Raw_Logs_Processed(Ids: List<Long>)

    @Query("SELECT * FROM Vector_Log ORDER BY Timestamp DESC")
    fun Get_All_Vector_Logs(): List<Vector_Log_Entity>

    @Query("SELECT * FROM Raw_Log WHERE Id = :Id")
    fun Get_Raw_Log_By_Id(Id: Long): Raw_Log_Entity?
}

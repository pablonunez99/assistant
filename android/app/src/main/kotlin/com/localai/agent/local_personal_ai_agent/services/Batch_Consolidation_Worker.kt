package com.localai.agent.local_personal_ai_agent.services

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.localai.agent.local_personal_ai_agent.database.Local_Ai_Database
import com.localai.agent.local_personal_ai_agent.database.Vector_Log_Entity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Batch_Consolidation_Worker(
    Context: Context,
    Params: WorkerParameters
) : CoroutineWorker(Context, Params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i("Batch_Worker", "Starting batch consolidation process...")

        try {
            val Database = Local_Ai_Database.Get_Database(applicationContext)
            val Log_Dao = Database.Log_Dao()

            val Unprocessed_Logs = Log_Dao.Get_Unprocessed_Raw_Logs()
            if (Unprocessed_Logs.isEmpty()) {
                Log.i("Batch_Worker", "No new logs to process.")
                return@withContext Result.success()
            }

            Log.i("Batch_Worker", "Found ${Unprocessed_Logs.size} logs to vectorize.")

            val Engine = Local_Llama_Engine()
            Engine.Init_Engine("local_model_path_placeholder.gguf")

            val Processed_Ids = mutableListOf<Long>()

            for (Log_Entry in Unprocessed_Logs) {
                val Embedding = Engine.Generate_Embedding(Log_Entry.Raw_Text)

                val Structured_Entity = if (Log_Entry.Source_App.contains("whatsapp")) {
                    "{\"type\": \"chat\", \"contact\": \"${Log_Entry.Title}\", \"message\": \"${Log_Entry.Raw_Text}\"}"
                } else {
                    "{\"type\": \"financial_transaction\", \"source\": \"${Log_Entry.Title}\", \"content\": \"${Log_Entry.Raw_Text}\"}"
                }

                val Vector_Log = Vector_Log_Entity(
                    Raw_Log_Id = Log_Entry.Id,
                    Embedding = Embedding,
                    Structured_Entity = Structured_Entity,
                    Timestamp = Log_Entry.Timestamp
                )

                Log_Dao.Insert_Vector_Log(Vector_Log)
                Processed_Ids.add(Log_Entry.Id)
            }

            if (Processed_Ids.isNotEmpty()) {
                Log_Dao.Mark_Raw_Logs_Processed(Processed_Ids)
            }

            Engine.Free_Engine()

            Log.i("Batch_Worker", "Successfully processed ${Processed_Ids.size} logs.")
            Result.success()
        } catch (e: Exception) {
            Log.e("Batch_Worker", "Failed to run batch consolidation worker", e)
            Result.failure()
        }
    }
}

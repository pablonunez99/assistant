package com.localai.agent.local_personal_ai_agent

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.localai.agent.local_personal_ai_agent.database.Local_Ai_Database
import com.localai.agent.local_personal_ai_agent.database.Raw_Log_Entity
import com.localai.agent.local_personal_ai_agent.database.Vector_Log_Entity
import com.localai.agent.local_personal_ai_agent.services.Ai_Overlay_Service
import com.localai.agent.local_personal_ai_agent.services.Local_Llama_Engine
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class MainActivity : FlutterActivity() {

    private val Control_Channel = "com.localai.agent/control"
    private val Inference_Channel = "com.localai.agent/inference"
    private val Main_Scope = CoroutineScope(Dispatchers.Main)
    private var Event_Sink: EventChannel.EventSink? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, Control_Channel).setMethodCallHandler { Call, Result ->
            when (Call.method) {
                "forceBatchProcess" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val Database = Local_Ai_Database.Get_Database(applicationContext)
                            val Log_Dao = Database.Log_Dao()
                            val Unprocessed = Log_Dao.Get_Unprocessed_Raw_Logs()

                            val Engine = Local_Llama_Engine()
                            Engine.Init_Engine("local_model_path_placeholder.gguf")

                            var Count = 0
                            for (Log in Unprocessed) {
                                val Embedding = Engine.Generate_Embedding(Log.Raw_Text)
                                val Structured = if (Log.Source_App.contains("whatsapp")) {
                                    "{\"type\": \"chat\", \"contact\": \"${Log.Title}\", \"message\": \"${Log.Raw_Text}\"}"
                                } else {
                                    "{\"type\": \"financial_transaction\", \"source\": \"${Log.Title}\", \"content\": \"${Log.Raw_Text}\"}"
                                }
                                val Vector = Vector_Log_Entity(
                                    Raw_Log_Id = Log.Id,
                                    Embedding = Embedding,
                                    Structured_Entity = Structured,
                                    Timestamp = Log.Timestamp
                                )
                                Log_Dao.Insert_Vector_Log(Vector)
                                Log_Dao.Mark_Raw_Logs_Processed(listOf(Log.Id))
                                Count++
                            }
                            Engine.Free_Engine()

                            withContext(Dispatchers.Main) {
                                Result.success(Count)
                            }
                        } catch (E: Exception) {
                            withContext(Dispatchers.Main) {
                                Result.error("BATCH_FAILED", E.message, null)
                            }
                        }
                    }
                }
                "isServiceEnabled" -> {
                    val Service = Call.argument<String>("service")
                    val Enabled = when (Service) {
                        "accessibility" -> Is_Accessibility_Service_Enabled()
                        "notification" -> Is_Notification_Service_Enabled()
                        "overlay" -> Settings.canDrawOverlays(this)
                        else -> false
                    }
                    Result.success(Enabled)
                }
                "requestPermissions" -> {
                    val Service = Call.argument<String>("service")
                    when (Service) {
                        "accessibility" -> {
                            val Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            startActivity(Intent)
                        }
                        "notification" -> {
                            val Intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            startActivity(Intent)
                        }
                        "overlay" -> {
                            val Intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            startActivity(Intent)
                        }
                    }
                    Result.success(true)
                }
                "showOverlayBubble" -> {
                    if (Settings.canDrawOverlays(this)) {
                        val Intent = Intent(this, Ai_Overlay_Service::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            startForegroundService(Intent)
                        } else {
                            startService(Intent)
                        }
                        Result.success(true)
                    } else {
                        Result.error("PERMISSION_DENIED", "Overlay permission not granted", null)
                    }
                }
                "hideOverlayBubble" -> {
                    val Intent = Intent(this, Ai_Overlay_Service::class.java)
                    stopService(Intent)
                    Result.success(true)
                }
                "searchVectorLogs" -> {
                    val Query_Text = Call.argument<String>("query") ?: ""
                    val Limit = Call.argument<Int>("limit") ?: 3

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val Database = Local_Ai_Database.Get_Database(applicationContext)
                            val Log_Dao = Database.Log_Dao()

                            val Engine = Local_Llama_Engine()
                            val Query_Embedding = Engine.Generate_Embedding(Query_Text)

                            val All_Vectors = Log_Dao.Get_All_Vector_Logs()
                            val Matches = mutableListOf<Map<String, Any>>()

                            for (Vector in All_Vectors) {
                                val Similarity = Compute_Cosine_Similarity(Query_Embedding, Vector.Embedding)
                                val Raw_Log = Log_Dao.Get_Raw_Log_By_Id(Vector.Raw_Log_Id)

                                val Match_Data = mapOf(
                                    "id" to Vector.Id,
                                    "similarity" to Similarity,
                                    "structuredEntity" to Vector.Structured_Entity,
                                    "rawText" to (Raw_Log?.Raw_Text ?: ""),
                                    "sourceApp" to (Raw_Log?.Source_App ?: ""),
                                    "title" to (Raw_Log?.Title ?: ""),
                                    "timestamp" to Vector.Timestamp
                                )
                                Matches.add(Match_Data)
                            }

                            val Sorted = Matches.sortedByDescending { It["similarity"] as Double }.take(Limit)

                            withContext(Dispatchers.Main) {
                                Result.success(Sorted)
                            }
                        } catch (E: Exception) {
                            withContext(Dispatchers.Main) {
                                Result.error("SEARCH_FAILED", E.message, null)
                            }
                        }
                    }
                }
                "runInference" -> {
                    val Prompt = Call.argument<String>("prompt") ?: ""

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val Engine = Local_Llama_Engine()
                            Engine.Init_Engine("local_model_path_placeholder.gguf")

                            Engine.Perform_Inference_Stream(Prompt) { Token ->
                                Main_Scope.launch {
                                    Event_Sink?.success(Token)
                                }
                            }

                            Engine.Free_Engine()
                            withContext(Dispatchers.Main) {
                                Result.success(true)
                            }
                        } catch (E: Exception) {
                            withContext(Dispatchers.Main) {
                                Result.error("INFERENCE_FAILED", E.message, null)
                            }
                        }
                    }
                }
                else -> Result.notImplemented()
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, Inference_Channel).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(Arguments: Any?, Events: EventChannel.EventSink?) {
                    Event_Sink = Events
                }

                override fun onCancel(Arguments: Any?) {
                    Event_Sink = null
                }
            }
        )
    }

    private fun Is_Accessibility_Service_Enabled(): Boolean {
        val Service_Name = "$packageName/com.localai.agent.local_personal_ai_agent.services.Ai_Accessibility_Service"
        val Setting_Value = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val Splitter = TextUtils.SimpleStringSplitter(':')
        Splitter.setString(Setting_Value)
        while (Splitter.hasNext()) {
            val Accessibility_Service = Splitter.next()
            if (Accessibility_Service.equals(Service_Name, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun Is_Notification_Service_Enabled(): Boolean {
        val Package_Name = packageName
        val Flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!Flat.isNullOrEmpty()) {
            val Names = Flat.split(":")
            for (Name in Names) {
                if (Name.contains(Package_Name)) {
                    return true
                }
            }
        }
        return false
    }

    private fun Compute_Cosine_Similarity(Vector_A: FloatArray, Vector_B: FloatArray): Double {
        if (Vector_A.size != Vector_B.size) return 0.0
        var Dot_Product = 0.0
        var Norm_A = 0.0
        var Norm_B = 0.0
        for (i in Vector_A.indices) {
            Dot_Product += Vector_A[i] * Vector_B[i]
            Norm_A += Vector_A[i] * Vector_A[i]
            Norm_B += Vector_B[i] * Vector_B[i]
        }
        return if (Norm_A > 0 && Norm_B > 0) Dot_Product / (sqrt(Norm_A) * sqrt(Norm_B)) else 0.0
    }
}

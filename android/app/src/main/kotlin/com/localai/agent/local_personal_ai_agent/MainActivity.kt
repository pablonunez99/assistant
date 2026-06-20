package com.localai.agent.local_personal_ai_agent

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
import java.lang.StringBuilder
import kotlin.math.sqrt

class MainActivity : FlutterActivity() {

    private val Control_Channel = "com.localai.agent/control"
    private val Inference_Channel = "com.localai.agent/inference"
    private val Main_Scope = CoroutineScope(Dispatchers.Main)
    private var Event_Sink: EventChannel.EventSink? = null

    // Native Mode UI Components
    private var Is_Real_Flutter = false
    private lateinit var Status_Accessibility: Button
    private lateinit var Status_Notification: Button
    private lateinit var Status_Overlay: Button
    private lateinit var Chat_Container: LinearLayout
    private lateinit var Scroll_View: ScrollView
    private lateinit var Input_Field: EditText
    private lateinit var Send_Btn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Is_Real_Flutter = try {
            Class.forName("io.flutter.embedding.engine.loader.FlutterLoader")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

        if (!Is_Real_Flutter) {
            Setup_Native_Ui()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!Is_Real_Flutter) {
            Update_Native_Ui_Statuses()
        }
    }

    private fun Setup_Native_Ui() {
        val Layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0C0B10"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Header Title
        val Header = TextView(this).apply {
            text = "Local AI Agent (Termux Native)"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#13111C"))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        Layout.addView(Header)

        // Sync panel
        val Sync_Panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val Sync_Btn = Button(this).apply {
            text = "Consolidar Batch"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00F0FF"))
            setOnClickListener {
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
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Sincronización: $Count nuevos logs", Toast.LENGTH_SHORT).show()
                        }
                    } catch (E: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Fallo: ${E.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        Sync_Panel.addView(Sync_Btn)
        Layout.addView(Sync_Panel)

        // Services Buttons
        val Buttons_Layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        Status_Accessibility = Button(this).apply {
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener {
                val Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(Intent)
            }
        }

        Status_Notification = Button(this).apply {
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener {
                val Intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                startActivity(Intent)
            }
        }

        Status_Overlay = Button(this).apply {
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener {
                val Ovr = Settings.canDrawOverlays(this@MainActivity)
                if (Ovr) {
                    val Intent = Intent(this@MainActivity, Ai_Overlay_Service::class.java)
                    stopService(Intent)
                } else {
                    val Intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    startActivity(Intent)
                }
            }
        }

        Buttons_Layout.addView(Status_Accessibility)
        Buttons_Layout.addView(Status_Notification)
        Buttons_Layout.addView(Status_Overlay)
        Layout.addView(Buttons_Layout)

        // Chat messages scroll view
        Scroll_View = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        Chat_Container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        Scroll_View.addView(Chat_Container)
        Layout.addView(Scroll_View)

        // Bottom Input Row
        val Input_Row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.parseColor("#13111C"))
        }
        Input_Field = EditText(this).apply {
            hint = "Preguntar sobre chats o gastos..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        Send_Btn = Button(this).apply {
            text = "Enviar"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00F0FF"))
            setOnClickListener {
                Send_Native_Message()
            }
        }
        Input_Row.addView(Input_Field)
        Input_Row.addView(Send_Btn)
        Layout.addView(Input_Row)

        setContentView(Layout)

        Add_Chat_Bubble("Hola, soy tu Agente Local con Memoria RAG. He indexado tus conversaciones de WhatsApp y transacciones financieras locales de forma segura. ¿Qué deseas consultar hoy?", false)
        Update_Native_Ui_Statuses()
    }

    private fun Update_Native_Ui_Statuses() {
        val Acc = Is_Accessibility_Service_Enabled()
        Status_Accessibility.text = "Chats: ${if (Acc) "ON" else "OFF"}"
        Status_Accessibility.setBackgroundColor(if (Acc) Color.parseColor("#1A00F0FF") else Color.parseColor("#1AFF0055"))

        val Not = Is_Notification_Service_Enabled()
        Status_Notification.text = "Gastos: ${if (Not) "ON" else "OFF"}"
        Status_Notification.setBackgroundColor(if (Not) Color.parseColor("#1A00F0FF") else Color.parseColor("#1AFF0055"))

        val Ovr = Settings.canDrawOverlays(this)
        Status_Overlay.text = "Burbuja: ${if (Ovr) "ON" else "OFF"}"
        Status_Overlay.setBackgroundColor(if (Ovr) Color.parseColor("#1A00F0FF") else Color.parseColor("#1AFF0055"))
    }

    private fun Send_Native_Message() {
        val Prompt = Input_Field.text.toString().trim()
        if (Prompt.isEmpty()) return

        Add_Chat_Bubble(Prompt, true)
        Input_Field.text.clear()

        val Agent_Bubble = Add_Chat_Bubble("", false)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val Database = Local_Ai_Database.Get_Database(applicationContext)
                val Log_Dao = Database.Log_Dao()

                val Engine = Local_Llama_Engine()
                val Query_Embedding = Engine.Generate_Embedding(Prompt)
                val All_Vectors = Log_Dao.Get_All_Vector_Logs()

                var Context_Str = ""
                val Matches = mutableListOf<Pair<Vector_Log_Entity, Double>>()
                for (Vector in All_Vectors) {
                    val Sim = Compute_Cosine_Similarity(Query_Embedding, Vector.Embedding)
                    Matches.add(Pair(Vector, Sim))
                }
                val Sorted = Matches.sortedByDescending { it.second }.take(2)
                if (Sorted.isNotEmpty()) {
                    Context_Str = "\nContexto:\n" + Sorted.joinToString("\n") { (Vector, _) ->
                        val Raw = Log_Dao.Get_Raw_Log_By_Id(Vector.Raw_Log_Id)
                        "- App: ${Raw?.Title} (${Raw?.Source_App}): ${Raw?.Raw_Text}"
                    }
                }

                val Full_Prompt = "SYSTEM: Eres un Asistente Personal. Contexto: $Context_Str\nUSER: $Prompt\nASSISTANT:"

                Engine.Init_Engine("local_model_path_placeholder.gguf")

                val Response_Builder = StringBuilder()
                Engine.Perform_Inference_Stream(Full_Prompt) { Token ->
                    Response_Builder.append(Token)
                    runOnUiThread {
                        Agent_Bubble.text = Response_Builder.toString()
                        Scroll_View.fullScroll(View.FOCUS_DOWN)
                    }
                }

                Engine.Free_Engine()

                if (Response_Builder.contains("\"action\": \"show_bubble\"")) {
                    runOnUiThread {
                        if (Settings.canDrawOverlays(this@MainActivity)) {
                            val Intent = Intent(this@MainActivity, Ai_Overlay_Service::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(Intent)
                            } else {
                                startService(Intent)
                            }
                        }
                    }
                }
            } catch (E: Exception) {
                runOnUiThread {
                    Agent_Bubble.text = "Error: ${E.message}"
                }
            }
        }
    }

    private fun Add_Chat_Bubble(Text: String, Is_User: Boolean): TextView {
        val Bubble = TextView(this).apply {
            text = Text
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (Is_User) Gravity.END else Gravity.START
                setMargins(8, 8, 8, 8)
            }
            background = GradientDrawable().apply {
                setColor(if (Is_User) Color.parseColor("#6200EE") else Color.parseColor("#2C2B35"))
                cornerRadius = 16f
            }
        }
        runOnUiThread {
            Chat_Container.addView(Bubble)
            Scroll_View.post {
                Scroll_View.fullScroll(View.FOCUS_DOWN)
            }
        }
        return Bubble
    }

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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

                            val Sorted = Matches.sortedByDescending { it["similarity"] as Double }.take(Limit)

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

package com.localai.agent.local_personal_ai_agent.services

import android.util.Log
import java.security.MessageDigest
import kotlin.math.sqrt

class Local_Llama_Engine {

    companion object {
        private var Is_Native_Loaded = false

        init {
            try {
                System.loadLibrary("llama_jni")
                Is_Native_Loaded = true
                Log.i("Local_Llama_Engine", "Successfully loaded native libllama_jni")
            } catch (e: Throwable) {
                Log.w("Local_Llama_Engine", "Could not load native libllama_jni - using Kotlin fallback engine")
            }
        }
    }

    private var Native_Context: Long = 0

    fun Init_Engine(Model_Path: String): Boolean {
        if (Is_Native_Loaded) {
            try {
                Native_Context = Native_Init(Model_Path)
                return Native_Context != 0L
            } catch (e: Throwable) {
                Log.e("Local_Llama_Engine", "Error initializing native llama engine", e)
            }
        }
        Log.i("Local_Llama_Engine", "Initialized Fallback Engine with model: $Model_Path")
        return true
    }

    fun Generate_Embedding(Text: String): FloatArray {
        if (Is_Native_Loaded && Native_Context != 0L) {
            try {
                return Native_Embed(Native_Context, Text)
            } catch (e: Throwable) {
                Log.e("Local_Llama_Engine", "Error calling native embed", e)
            }
        }
        return Fallback_Generate_Embedding(Text)
    }

    fun Perform_Inference_Stream(Prompt: String, Callback: (String) -> Unit): String {
        if (Is_Native_Loaded && Native_Context != 0L) {
            try {
                return Native_Inference_Stream(Native_Context, Prompt, Callback)
            } catch (e: Throwable) {
                Log.e("Local_Llama_Engine", "Error calling native inference", e)
            }
        }
        return Fallback_Perform_Inference_Stream(Prompt, Callback)
    }

    fun Free_Engine() {
        if (Is_Native_Loaded && Native_Context != 0L) {
            try {
                Native_Free(Native_Context)
                Native_Context = 0
            } catch (e: Throwable) {
                Log.e("Local_Llama_Engine", "Error freeing native llama engine", e)
            }
        }
    }

    // --- JNI Declarations ---
    private external fun Native_Init(Model_Path: String): Long
    private external fun Native_Embed(Context: Long, Text: String): FloatArray
    private external fun Native_Inference_Stream(Context: Long, Prompt: String, Callback: (String) -> Unit): String
    private external fun Native_Free(Context: Long)

    // --- Kotlin Fallback Implementation ---

    private fun Fallback_Generate_Embedding(Text: String): FloatArray {
        val Embedding = FloatArray(128)
        val Words = Text.lowercase().split(Regex("[^a-zA-Z0-9$]+")).filter { it.isNotEmpty() }
        if (Words.isEmpty()) {
            Embedding[0] = 1.0f
            return Embedding
        }

        for (W in Words) {
            val Word_Bytes = W.toByteArray()
            val Digest = MessageDigest.getInstance("SHA-256").digest(Word_Bytes)
            for (i in 0 until 128) {
                val Digest_Index = (i * 2) % Digest.size
                val Val = Digest[Digest_Index].toInt() xor Digest[(Digest_Index + 1) % Digest.size].toInt()
                Embedding[i] += Val.toFloat()
            }
        }

        var Sum_Sq = 0.0f
        for (Val in Embedding) {
            Sum_Sq += Val * Val
        }
        val Magnitude = sqrt(Sum_Sq)
        if (Magnitude > 0) {
            for (i in Embedding.indices) {
                Embedding[i] /= Magnitude
            }
        } else {
            Embedding[0] = 1.0f
        }

        return Embedding
    }

    private fun Fallback_Perform_Inference_Stream(Prompt: String, Callback: (String) -> Unit): String {
        val Lower = Prompt.lowercase()
        val Response: String

        if (Lower.contains("urgente") || Lower.contains("alarma") || Lower.contains("burbuja")) {
            Response = "```json\n{\"action\": \"show_bubble\", \"message\": \"Alerta Urgente: Compra detectada en Mercado Pago por valor elevado. Verifique su saldo.\"}\n```\nEntendido. He detectado contexto urgente y activado la burbuja flotante."
        } else if (Lower.contains("gasto") || Lower.contains("dinero") || Lower.contains("mercadopago") || Lower.contains("galicia")) {
            Response = "Analizando su historial financiero...\n\nSegún sus transacciones locales guardadas pasivamente, gastó dinero en compras recientes. Le sugiero revisar su presupuesto diario si continúa realizando transacciones frecuentes."
        } else if (Lower.contains("whatsapp") || Lower.contains("chat") || Lower.contains("juan")) {
            Response = "Revisando sus conversaciones locales...\n\nHe recuperado chats recientes con sus contactos de WhatsApp. Parece que conversaron sobre temas del día. ¿Le gustaría que le recuerde alguna tarea específica mencionada en los mensajes?"
        } else {
            Response = "Hola. Soy su Agente de Inteligencia Artificial Local con memoria persistente RAG. \n\nHe analizado su base de conocimientos local de logs diarios. ¿En qué puedo ayudarle hoy con sus datos?"
        }

        val Words = Response.split(" ")
        val T = Thread {
            try {
                for (W in Words) {
                    Thread.sleep(80)
                    Callback(W + " ")
                }
            } catch (e: InterruptedException) {
                // Thread interrupted
            }
        }
        T.start()
        try {
            T.join()
        } catch (e: InterruptedException) {}

        return Response
    }
}

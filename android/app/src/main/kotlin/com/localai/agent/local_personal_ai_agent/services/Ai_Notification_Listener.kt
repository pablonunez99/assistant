package com.localai.agent.local_personal_ai_agent.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.localai.agent.local_personal_ai_agent.database.Local_Ai_Database
import com.localai.agent.local_personal_ai_agent.database.Raw_Log_Entity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class Ai_Notification_Listener : NotificationListenerService() {

    private val Service_Scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(Sbn: StatusBarNotification?) {
        if (Sbn == null) return

        val Package_Name = Sbn.packageName ?: return
        
        // Filter for Mercado Pago and Banco Galicia
        val Is_Mercado_Pago = Package_Name.contains("mercadopago", ignoreCase = true)
        val Is_Galicia = Package_Name.contains("bancogalicia", ignoreCase = true)
        
        if (!Is_Mercado_Pago && !Is_Galicia) return

        val Extras = Sbn.notification.extras
        val Title = Extras.getCharSequence("android.title")?.toString() ?: "No Title"
        val Text = Extras.getCharSequence("android.text")?.toString() ?: ""

        if (Text.isEmpty()) return

        Log.d("Ai_Notification", "Intercepted notification from $Package_Name: $Title - $Text")

        Service_Scope.launch {
            try {
                // Parse amount and concept for structured indexing
                val Parsed_Info = Parse_Financial_Notification(Text)
                val Clean_Log_Text = if (Parsed_Info != null) {
                    "Transaction Details: Amount=${Parsed_Info.first}, Concept=${Parsed_Info.second} | Original: $Text"
                } else {
                    Text
                }

                val Database = Local_Ai_Database.Get_Database(applicationContext)
                val Log_Dao = Database.Log_Dao()

                val Raw_Log = Raw_Log_Entity(
                    Source_App = Package_Name,
                    Title = Title,
                    Raw_Text = Clean_Log_Text,
                    Timestamp = System.currentTimeMillis(),
                    Is_Processed = false
                )
                Log_Dao.Insert_Raw_Log(Raw_Log)
                Log.d("Ai_Notification", "Logged transaction: $Clean_Log_Text")
            } catch (e: Exception) {
                Log.e("Ai_Notification", "Error saving notification log", e)
            }
        }
    }

    private fun Parse_Financial_Notification(Text: String): Pair<String, String>? {
        try {
            val Amount_Pattern = Pattern.compile("\\$\\s*([\\d.,]+)")
            val Matcher = Amount_Pattern.matcher(Text)
            if (Matcher.find()) {
                val Amount = Matcher.group(0) ?: ""
                
                var Concept = "Unknown Transaction"
                val Concept_Patterns = listOf(
                    Pattern.compile("en\\s+([A-Za-z0-9\\s]{3,})"),
                    Pattern.compile("a\\s+([A-Za-z0-9\\s]{3,})"),
                    Pattern.compile("de\\s+([A-Za-z0-9\\s]{3,})")
                )
                for (P in Concept_Patterns) {
                    val Concept_Matcher = P.matcher(Text)
                    if (Concept_Matcher.find()) {
                        Concept = Concept_Matcher.group(1)?.trim() ?: Concept
                        break
                    }
                }
                return Pair(Amount, Concept)
            }
        } catch (e: Exception) {
            Log.e("Ai_Notification", "Error parsing transaction text", e)
        }
        return null
    }

    override fun onNotificationRemoved(Sbn: StatusBarNotification?) {}
}

package com.localai.agent.local_personal_ai_agent.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.localai.agent.local_personal_ai_agent.database.Local_Ai_Database
import com.localai.agent.local_personal_ai_agent.database.Raw_Log_Entity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class Ai_Accessibility_Service : AccessibilityService() {

    private val Service_Scope = CoroutineScope(Dispatchers.IO)
    private val Processed_Hashes = ConcurrentHashMap.newKeySet<String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val Package_Name = event.packageName?.toString() ?: return
        if (Package_Name != "com.whatsapp") return // Capture WhatsApp chats only

        val Root_Node = rootInActiveWindow ?: return

        Service_Scope.launch {
            try {
                val Contact_Name = Find_Contact_Name(Root_Node) ?: "Unknown Contact"
                val Message_Texts = mutableListOf<String>()
                Collect_Message_Texts(Root_Node, Message_Texts)

                val Database = Local_Ai_Database.Get_Database(applicationContext)
                val Log_Dao = Database.Log_Dao()

                for (Text in Message_Texts) {
                    val Trimmed = Text.trim()
                    if (Trimmed.isEmpty() || Trimmed.length < 2) continue

                    // Hashing app + contact + text
                    val Hash_Input = "${Package_Name}_${Contact_Name}_${Trimmed}"
                    val Message_Hash = Md5_Hash(Hash_Input)

                    if (!Processed_Hashes.contains(Message_Hash)) {
                        Processed_Hashes.add(Message_Hash)

                        val Raw_Log = Raw_Log_Entity(
                            Source_App = Package_Name,
                            Title = Contact_Name,
                            Raw_Text = Trimmed,
                            Timestamp = System.currentTimeMillis(),
                            Is_Processed = false
                        )
                        Log_Dao.Insert_Raw_Log(Raw_Log)
                        Log.d("Ai_Accessibility", "Logged chat: [$Contact_Name] -> $Trimmed")
                    }
                }
            } catch (e: Exception) {
                Log.e("Ai_Accessibility", "Error parsing accessibility event", e)
            } finally {
                try {
                    Root_Node.recycle()
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun Find_Contact_Name(Node: AccessibilityNodeInfo): String? {
        for (i in 0 until Node.childCount) {
            val Child = Node.getChild(i) ?: continue
            if (Child.className == "android.widget.TextView") {
                val Text = Child.text?.toString()
                val View_Id = Child.viewIdResourceName
                if (View_Id != null && (View_Id.contains("contact_name") || View_Id.contains("conversation_title") || View_Id.contains("title"))) {
                    return Text
                }
            }
            val Found = Find_Contact_Name(Child)
            if (Found != null) return Found
        }
        return null
    }

    private fun Collect_Message_Texts(Node: AccessibilityNodeInfo, List: MutableList<String>) {
        if (Node.className == "android.widget.TextView") {
            val Text = Node.text?.toString()
            val View_Id = Node.viewIdResourceName
            if (!Text.isNullOrEmpty()) {
                if (View_Id != null && View_Id.contains("message_text")) {
                    List.add(Text)
                } else if (View_Id == null && Text.length > 2 && !Text.contains(":") && !Text.matches(Regex("\\d{1,2}:\\d{2}\\s*(AM|PM)?", RegexOption.IGNORE_CASE))) {
                    List.add(Text)
                }
            }
        }

        for (i in 0 until Node.childCount) {
            val Child = Node.getChild(i) ?: continue
            Collect_Message_Texts(Child, List)
        }
    }

    private fun Md5_Hash(Input: String): String {
        val Bytes = MessageDigest.getInstance("MD5").digest(Input.toByteArray())
        return Bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onInterrupt() {
        Log.d("Ai_Accessibility", "Accessibility service interrupted")
    }
}

package com.waleve.player.data.social

import android.content.Context
import com.waleve.player.domain.model.ChatLog
import com.waleve.player.domain.model.ChatMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listen Together chats live on the phone. Firestore only carries a message across for the few
 * seconds it takes; the host wipes them from the cloud when the session ends. Last 20 chats kept.
 */
@Singleton
class ChatStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("together_chats", Context.MODE_PRIVATE)

    private fun index(): MutableList<String> =
        prefs.getString("index", null)?.let { j -> JSONArray(j).let { a -> MutableList(a.length()) { a.getString(it) } } } ?: mutableListOf()

    @Synchronized
    fun save(key: String, hostName: String, startedAt: Long, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        val arr = JSONArray()
        messages.sortedBy { it.at }.forEach { m ->
            arr.put(JSONObject().put("id", m.id).put("uid", m.uid).put("name", m.name).put("text", m.text).put("at", m.at))
        }
        val idx = index().apply { remove(key); add(0, key) }
        val e = prefs.edit()
        while (idx.size > 20) e.remove("chat_" + idx.removeAt(idx.lastIndex))
        e.putString("index", JSONArray(idx).toString())
        e.putString("chat_$key", JSONObject().put("host", hostName).put("startedAt", startedAt).put("messages", arr).toString())
        e.apply()
    }

    fun load(key: String): ChatLog? {
        val raw = prefs.getString("chat_$key", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val a = o.getJSONArray("messages")
            ChatLog(
                key, o.optString("host"), o.optLong("startedAt"),
                List(a.length()) { i ->
                    val m = a.getJSONObject(i)
                    ChatMessage(m.getString("id"), m.getString("uid"), m.optString("name"), m.optString("text"), m.optLong("at"))
                },
            )
        }.getOrNull()
    }

    fun history(): List<ChatLog> = index().mapNotNull(::load)

    fun clearAll() { prefs.edit().clear().apply() }
}

package com.aisendiri.shared.android

import android.content.Context
import com.aisendiri.shared.chat.Conversation
import com.aisendiri.shared.chat.ConversationStore
import com.aisendiri.shared.settings.AppSettings
import com.aisendiri.shared.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class PrefsSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(
        prefs.getString(KEY, null)
            ?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
            ?: AppSettings(),
    )
    override val settings: StateFlow<AppSettings> = state

    override fun update(transform: (AppSettings) -> AppSettings) {
        state.update(transform)
        prefs.edit().putString(KEY, json.encodeToString(AppSettings.serializer(), state.value)).apply()
    }

    private companion object {
        const val KEY = "app_settings"
    }
}

/** Saves each chat as a JSON file in the app's private storage. Nothing leaves the phone. */
class FileConversationStore(context: Context) : ConversationStore {
    private val dir = File(context.filesDir, "chats").apply { mkdirs() }
    private val mutex = Mutex()

    private fun file(id: String) = File(dir, "${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")

    override suspend fun list(): List<Conversation> = io {
        dir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
            runCatching { json.decodeFromString(Conversation.serializer(), f.readText()) }.getOrNull()
        }
    }

    override suspend fun get(id: String): Conversation? = io {
        file(id).takeIf { it.exists() }?.let { runCatching { json.decodeFromString(Conversation.serializer(), it.readText()) }.getOrNull() }
    }

    override suspend fun save(conversation: Conversation) = io {
        val target = file(conversation.id)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(json.encodeToString(Conversation.serializer(), conversation))
        tmp.renameTo(target)
        Unit
    }

    override suspend fun delete(id: String) = io { file(id).delete(); Unit }

    override suspend fun deleteAll() = io { dir.listFiles().orEmpty().forEach { it.delete() } }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }
}

package com.aisendiri.shared.chat

import kotlinx.serialization.Serializable

enum class Role(val wire: String) { SYSTEM("system"), USER("user"), ASSISTANT("assistant") }

@Serializable
data class ChatMessage(
    val role: Role,
    val text: String,
    /** True once the user reported this AI reply as harmful or wrong. */
    val reported: Boolean = false,
)

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val modelId: String?,
    val messages: List<ChatMessage>,
)

interface ConversationStore {
    suspend fun list(): List<Conversation>
    suspend fun get(id: String): Conversation?
    suspend fun save(conversation: Conversation)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}

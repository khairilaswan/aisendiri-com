package com.aisendiri.shared.settings

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

enum class AppLanguage { SYSTEM, ENGLISH, MALAY }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Null engine values mean "automatic": the app picks them from the phone's hardware. */
@Serializable
data class AppSettings(
    val onboardingDone: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val advancedMode: Boolean = false,
    val activeModelId: String? = null,
    val contextSize: Int? = null,
    val threads: Int? = null,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val topP: Float = DEFAULT_TOP_P,
    val minP: Float = DEFAULT_MIN_P,
    val repeatPenalty: Float = DEFAULT_REPEAT_PENALTY,
    val maxReplyTokens: Int = DEFAULT_MAX_REPLY_TOKENS,
    val customSystemPrompt: String? = null,
) {
    /** Puts every Advanced value back to its default, keeping the user's other choices. */
    fun withAdvancedDefaults(): AppSettings = copy(
        contextSize = null,
        threads = null,
        temperature = DEFAULT_TEMPERATURE,
        topP = DEFAULT_TOP_P,
        minP = DEFAULT_MIN_P,
        repeatPenalty = DEFAULT_REPEAT_PENALTY,
        maxReplyTokens = DEFAULT_MAX_REPLY_TOKENS,
        customSystemPrompt = null,
    )

    companion object {
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_TOP_P = 0.9f
        const val DEFAULT_MIN_P = 0.05f
        const val DEFAULT_REPEAT_PENALTY = 1.1f
        const val DEFAULT_MAX_REPLY_TOKENS = 512
    }
}

interface SettingsStore {
    val settings: StateFlow<AppSettings>
    fun update(transform: (AppSettings) -> AppSettings)
}

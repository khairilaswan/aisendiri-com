package com.aisendiri.shared.model

/** Where the app's public pages live. The privacy policy URL is also entered in Play Console. */
object AppLinks {
    const val PRIVACY_POLICY = "https://aisendiri.com/privacy"
    const val GEMMA_PROHIBITED_USE = "https://ai.google.dev/gemma/prohibited_use_policy"
}

/** One line on the Licences screen. [notice] is text a licence asks apps to show. */
data class LicenseEntry(val name: String, val license: String, val url: String, val notice: String? = null)

/**
 * Licences for the AI files the app can download and the open-source code it ships.
 * Gemma's terms ask that users get its use restrictions; Llama's ask for "Built with Llama".
 */
object Licenses {
    private val gemmaNotice =
        "Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms. " +
            "Use is also subject to the Gemma Prohibited Use Policy: ${AppLinks.GEMMA_PROHIBITED_USE}"
    private const val LLAMA_NOTICE =
        "Built with Llama. Llama 3.2 is licensed under the Llama 3.2 Community License, " +
            "Copyright © Meta Platforms, Inc. All Rights Reserved."

    val aiModels: List<LicenseEntry> by lazy {
        val chat = (ModelCatalog.all + VisionCatalog.all.mapNotNull { it.photoModel }).distinctBy { it.id }
        val entries = chat.map { m ->
            LicenseEntry(
                name = m.family,
                license = m.license,
                url = m.licenseUrl,
                notice = when {
                    m.license.startsWith("Gemma") -> gemmaNotice
                    m.license.startsWith("Llama") -> LLAMA_NOTICE
                    else -> null
                },
            )
        }
        entries + LicenseEntry("Whisper (OpenAI)", "MIT", "https://github.com/openai/whisper/blob/main/LICENSE")
    }

    val software: List<LicenseEntry> = listOf(
        LicenseEntry("llama.cpp / ggml", "MIT", "https://github.com/ggml-org/llama.cpp/blob/master/LICENSE"),
        LicenseEntry("whisper.cpp", "MIT", "https://github.com/ggml-org/whisper.cpp/blob/master/LICENSE"),
        LicenseEntry("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
        LicenseEntry("Compose Multiplatform, AndroidX", "Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    )
}

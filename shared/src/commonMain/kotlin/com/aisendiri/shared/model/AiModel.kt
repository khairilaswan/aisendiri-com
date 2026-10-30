package com.aisendiri.shared.model

/** A file the app downloads from Hugging Face: a chat AI or a speech-recognition model. */
interface ModelFile {
    val id: String
    val hfRepo: String
    val fileName: String
    val approxSizeMb: Int
    /** The first four bytes of a valid file, checked after download. */
    val magic: String

    val downloadUrl: String get() = "https://huggingface.co/$hfRepo/resolve/main/$fileName"
    val approxSizeBytes: Long get() = approxSizeMb * 1024L * 1024L
}

/** How a model is presented to beginners. Technical details only show in Advanced mode. */
enum class ModelTier { FAST, BALANCED, SMART }

/**
 * A downloadable text AI. Model files are GGUF weights (data, not executable code),
 * which keeps downloading them within Google Play and App Store rules.
 *
 * RAM values are in GiB as Android reports them: a "4 GB" phone reports about 3.6.
 */
data class AiModel(
    override val id: String,
    val tier: ModelTier,
    val family: String,
    val parameters: String,
    val quantization: String,
    override val hfRepo: String,
    override val fileName: String,
    override val approxSizeMb: Int,
    val minRamGb: Double,
    val recommendedRamGb: Double,
    val license: String,
    val licenseUrl: String,
    /** Hidden from beginners; only listed in Advanced mode. */
    val advancedOnly: Boolean = false,
) : ModelFile {
    override val magic: String get() = "GGUF"
}

object ModelCatalog {
    val all: List<AiModel> = listOf(
        AiModel(
            id = "qwen2.5-0.5b-q4km",
            tier = ModelTier.FAST,
            family = "Qwen2.5 0.5B Instruct",
            parameters = "0.5B",
            quantization = "Q4_K_M",
            hfRepo = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            approxSizeMb = 400,
            minRamGb = 2.5,
            recommendedRamGb = 3.0,
            license = "Apache 2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct/blob/main/LICENSE",
        ),
        AiModel(
            id = "gemma3-1b-q4km",
            tier = ModelTier.BALANCED,
            family = "Gemma 3 1B Instruct",
            parameters = "1B",
            quantization = "Q4_K_M",
            hfRepo = "bartowski/google_gemma-3-1b-it-GGUF",
            fileName = "google_gemma-3-1b-it-Q4_K_M.gguf",
            approxSizeMb = 810,
            minRamGb = 3.4,
            recommendedRamGb = 5.0,
            license = "Gemma Terms of Use",
            licenseUrl = "https://ai.google.dev/gemma/terms",
        ),
        AiModel(
            id = "qwen2.5-1.5b-q4km",
            tier = ModelTier.SMART,
            family = "Qwen2.5 1.5B Instruct",
            parameters = "1.5B",
            quantization = "Q4_K_M",
            hfRepo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            approxSizeMb = 1120,
            minRamGb = 4.5,
            recommendedRamGb = 6.5,
            license = "Apache 2.0",
            licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct/blob/main/LICENSE",
        ),
        AiModel(
            id = "llama3.2-1b-q4km",
            tier = ModelTier.BALANCED,
            family = "Llama 3.2 1B Instruct",
            parameters = "1B",
            quantization = "Q4_K_M",
            hfRepo = "bartowski/Llama-3.2-1B-Instruct-GGUF",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            approxSizeMb = 810,
            minRamGb = 3.4,
            recommendedRamGb = 5.0,
            license = "Llama 3.2 Community License",
            licenseUrl = "https://www.llama.com/llama3_2/license/",
            advancedOnly = true,
        ),
    )

    fun byId(id: String?): AiModel? = all.firstOrNull { it.id == id }

    /** Smallest first, so fallbacks walk down the list. */
    val bySize: List<AiModel> get() = all.sortedBy { it.approxSizeMb }
}

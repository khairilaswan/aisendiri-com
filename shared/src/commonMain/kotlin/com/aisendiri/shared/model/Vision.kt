package com.aisendiri.shared.model

/**
 * What lets a chat AI understand photos: an image encoder ("mmproj" file), downloaded once
 * and used offline. Like the AIs, it is weights (data), not code.
 *
 * Some chat AIs were trained with an encoder and see photos themselves ([photoModel] null).
 * Others can't, so chats with a photo switch to [photoModel], a separate AI the encoder
 * belongs to; follow-up questions in that chat stay with it.
 */
data class VisionAddon(
    override val id: String,
    /** The chat AIs this add-on is offered for. */
    val forModelIds: Set<String>,
    override val hfRepo: String,
    override val fileName: String,
    override val approxSizeMb: Int,
    val license: String,
    val photoModel: AiModel? = null,
) : ModelFile {
    override val magic: String get() = "GGUF"

    /** Everything to download, biggest first. */
    val files: List<ModelFile> get() = listOfNotNull(photoModel, this)
    val totalSizeBytes: Long get() = files.sumOf { it.approxSizeBytes }

    /** The AI that reads the photo when [chatModel] is in use. */
    fun readerFor(chatModel: AiModel): AiModel = photoModel ?: chatModel
}

/**
 * Picked by a Bahasa Melayu screenshot and photo check (tools/vision-eval, docs/VISION-MODEL-CHECK.md).
 * Smart AI sees photos itself. The Light AIs can't, so their chats switch to Qwen3-VL 2B, which
 * reads screenshots well on 4 GB phones but sometimes answers with Indonesian words.
 */
object VisionCatalog {
    /** Reads photos for the Light AIs. */
    val qwen3Vl2b = AiModel(
        id = "qwen3-vl-2b-q4km",
        tier = ModelTier.BALANCED,
        family = "Qwen3-VL 2B Instruct",
        parameters = "2B",
        quantization = "Q4_K_M",
        hfRepo = "unsloth/Qwen3-VL-2B-Instruct-GGUF",
        fileName = "Qwen3-VL-2B-Instruct-Q4_K_M.gguf",
        approxSizeMb = 1056,
        minRamGb = 3.4,
        recommendedRamGb = 4.5,
        license = "Apache 2.0",
        licenseUrl = "https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct",
        advancedOnly = true,
    )

    val all: List<VisionAddon> = listOf(
        VisionAddon(
            id = "sealion-v4.5-e2b-mmproj-f16",
            forModelIds = setOf("sealion-v4.5-e2b-q4km"),
            hfRepo = "aisingapore/Gemma-SEA-LION-v4.5-E2B-IT-GGUF",
            fileName = "mmproj-Gemma-SEA-LION-v4.5-E2B-IT-F16.gguf",
            approxSizeMb = 940,
            license = "Gemma Terms of Use",
        ),
        VisionAddon(
            id = "qwen3-vl-2b-mmproj-q8",
            forModelIds = setOf("sailor2-1b-q4km", "qwen2.5-0.5b-q4km", "gemma3-1b-q4km", "qwen2.5-1.5b-q4km", "llama3.2-1b-q4km"),
            hfRepo = "ggml-org/Qwen3-VL-2B-Instruct-GGUF",
            fileName = "mmproj-Qwen3-VL-2B-Instruct-Q8_0.gguf",
            approxSizeMb = 424,
            license = "Apache 2.0",
            photoModel = qwen3Vl2b,
        ),
    )

    fun byId(id: String?): VisionAddon? = all.firstOrNull { it.id == id }

    /** The photo add-on for [model], or null when photos aren't available with that AI. */
    fun forModel(model: AiModel?): VisionAddon? = all.firstOrNull { model?.id in it.forModelIds }

    /** The add-on whose encoder belongs to [model] (as chat AI or photo AI). */
    fun encoderFor(model: AiModel): VisionAddon? =
        all.firstOrNull { it.photoModel?.id == model.id || (it.photoModel == null && model.id in it.forModelIds) }
}

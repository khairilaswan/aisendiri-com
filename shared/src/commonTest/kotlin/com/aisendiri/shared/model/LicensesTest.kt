package com.aisendiri.shared.model

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LicensesTest {
    @Test
    fun everyDownloadableAiIsListed() {
        val names = Licenses.aiModels.map { it.name }.toSet()
        (ModelCatalog.all + VisionCatalog.qwen3Vl2b).forEach { assertTrue(it.family in names, it.family) }
    }

    @Test
    fun gemmaAndLlamaCarryTheirNotices() {
        Licenses.aiModels.filter { it.license.startsWith("Gemma") }.forEach {
            assertTrue(assertNotNull(it.notice, it.name).contains("Prohibited Use Policy"), it.name)
        }
        Licenses.aiModels.filter { it.license.startsWith("Llama") }.forEach {
            assertTrue(assertNotNull(it.notice, it.name).startsWith("Built with Llama"), it.name)
        }
    }
}

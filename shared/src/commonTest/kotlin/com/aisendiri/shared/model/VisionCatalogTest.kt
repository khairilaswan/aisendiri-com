package com.aisendiri.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VisionCatalogTest {
    @Test
    fun everyCatalogAiCanReadPhotos() {
        ModelCatalog.all.forEach { model -> assertNotNull(VisionCatalog.forModel(model), model.id) }
    }

    @Test
    fun smartAiReadsPhotosItself() {
        val smart = ModelCatalog.byId("sealion-v4.5-e2b-q4km")!!
        val addon = VisionCatalog.forModel(smart)!!
        assertEquals(smart, addon.readerFor(smart))
        assertEquals(addon, VisionCatalog.encoderFor(smart))
        assertEquals(1, addon.files.size)
    }

    @Test
    fun lightAiSwitchesToPhotoAi() {
        val light = ModelCatalog.byId("sailor2-1b-q4km")!!
        val addon = VisionCatalog.forModel(light)!!
        val reader = addon.readerFor(light)
        assertEquals(VisionCatalog.qwen3Vl2b, reader)
        assertEquals(addon, VisionCatalog.encoderFor(reader))
        assertNull(VisionCatalog.encoderFor(light))
        assertEquals(listOf<ModelFile>(reader, addon), addon.files)
    }

    @Test
    fun noAiNoPhotos() {
        assertNull(VisionCatalog.forModel(null))
    }
}

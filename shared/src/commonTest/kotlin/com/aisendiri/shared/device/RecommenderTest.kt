package com.aisendiri.shared.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecommenderTest {
    private fun phone(ramGb: Double, freeGb: Double = 20.0, abi: String = "arm64-v8a") = DeviceProfile(
        manufacturer = "samsung", model = "test", chipset = null, osVersion = "14", cpuCores = 8,
        cpuFeatures = emptySet(), abis = listOf(abi),
        totalRamBytes = (ramGb * DeviceProfile.GIB).toLong(), availableRamBytes = 0, lowMemory = false,
        freeStorageBytes = (freeGb * DeviceProfile.GIB).toLong(), batteryPercent = null, isCharging = null,
        thermal = ThermalState.NORMAL, isOnWifi = true, isOnline = true,
    )

    @Test
    fun fourGbPhoneGetsTheLightAi() {
        // A "4 GB" Galaxy A51 / A15 reports about 3.6 GiB.
        assertEquals("qwen2.5-0.5b-q4km", Recommender.recommend(phone(3.6)).model?.id)
        assertEquals(4, phone(3.6).marketedRamGb)
    }

    @Test
    fun sixGbPhoneGetsTheBalancedAi() {
        assertEquals("gemma3-1b-q4km", Recommender.recommend(phone(5.5)).model?.id)
        assertEquals(6, phone(5.5).marketedRamGb)
    }

    @Test
    fun eightGbPhoneGetsTheSmartAi() {
        assertEquals("qwen2.5-1.5b-q4km", Recommender.recommend(phone(7.4)).model?.id)
        assertEquals(8, phone(7.4).marketedRamGb)
    }

    @Test
    fun tinyOrFullOr32BitPhonesGetNothing() {
        assertNull(Recommender.recommend(phone(1.8)).model)
        assertNull(Recommender.recommend(phone(7.4, freeGb = 1.0)).model)
        assertNull(Recommender.recommend(phone(7.4, abi = "armeabi-v7a")).model)
    }

    @Test
    fun fallsBackToSmallerAi() {
        val smart = Recommender.recommend(phone(7.4)).model!!
        assertEquals("gemma3-1b-q4km", Recommender.smallerThan(smart, phone(7.4))?.id)
    }

    @Test
    fun lowStorageFallsBackToLighterAi() {
        // 1.6 GB free: too little for the 0.8 GB AI plus 1 GB headroom, but fine for the 0.4 GB one.
        assertEquals("qwen2.5-0.5b-q4km", Recommender.recommend(phone(7.4, freeGb = 1.6)).model?.id)
    }
}

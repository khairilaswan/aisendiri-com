package com.aisendiri.shared.android

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import com.aisendiri.shared.device.DeviceProfile
import com.aisendiri.shared.device.ThermalState
import com.aisendiri.shared.platform.DeviceInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Reads hardware facts from system APIs only. Needs no runtime permissions. */
class AndroidDeviceInspector(private val context: Context) : DeviceInspector {

    override suspend fun inspect(): DeviceProfile = withContext(Dispatchers.IO) {
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        val battery = context.getSystemService(BatteryManager::class.java)
        val network = networkState()
        DeviceProfile(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            chipset = chipset(),
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuFeatures = cpuFeatures(),
            abis = Build.SUPPORTED_ABIS.toList(),
            totalRamBytes = memory.totalMem,
            availableRamBytes = memory.availMem,
            lowMemory = memory.lowMemory,
            freeStorageBytes = StatFs(context.filesDir.path).availableBytes,
            batteryPercent = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 },
            isCharging = battery?.isCharging,
            thermal = thermal(),
            isOnWifi = network.second,
            isOnline = network.first,
        )
    }

    private fun chipset(): String? {
        val soc = if (Build.VERSION.SDK_INT >= 31) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ")
        } else ""
        return soc.ifBlank { Build.HARDWARE.takeIf { it.isNotBlank() } }
    }

    private fun cpuFeatures(): Set<String> = runCatching {
        File("/proc/cpuinfo").readLines()
            .firstOrNull { it.startsWith("Features") }
            ?.substringAfter(':')
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.toSet()
    }.getOrNull() ?: emptySet()

    private fun thermal(): ThermalState {
        if (Build.VERSION.SDK_INT < 29) return ThermalState.UNKNOWN
        val status = context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: return ThermalState.UNKNOWN
        return when {
            status <= PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.NORMAL
            status == PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.WARM
            else -> ThermalState.HOT
        }
    }

    /** (online, onWifiOrUnmetered) */
    private fun networkState(): Pair<Boolean, Boolean> {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false to false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false to false
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return online to wifi
    }
}

package com.deskflow.app.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object DeviceSyncV24Helper {
    /** #315 Read Android phone battery percentage & charging status and sync to DeskFlow server. */
    suspend fun syncPhoneBattery(context: Context, baseUrl: String) = withContext(Dispatchers.IO) {
        try {
            val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
            val charging = plugged != 0
            val conn = (URL("${baseUrl.trimEnd('/')}/api/app/device/battery").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
            }
            conn.outputStream.use { it.write("""{"level":$pct,"charging":$charging}""".toByteArray()) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {}
    }

    /** #317 Forward incoming call notification to DeskFlow ESP32. */
    suspend fun notifyIncomingCall(baseUrl: String, caller: String, number: String = "") = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("${baseUrl.trimEnd('/')}/api/app/device/call").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
            }
            val safeCaller = caller.replace("\"", "'")
            val safeNum = number.replace("\"", "'")
            conn.outputStream.use { it.write("""{"caller":"$safeCaller","number":"$safeNum"}""".toByteArray()) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {}
    }

    /** #269 Verify scanned QR code for Alarm Mission. */
    fun verifyAlarmQrMission(scanned: String, expected: String = "DESKFLOW-WAKEUP"): Boolean {
        return scanned.trim().equals(expected.trim(), ignoreCase = true) || scanned.contains("DESKFLOW", ignoreCase = true)
    }
}

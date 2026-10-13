package dev.morse.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dev.morse.core.power.PowerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn

/**
 * The current time, updated once a minute by the system's TIME_TICK broadcast
 * (and when the time or time zone changes). The receiver only exists while
 * something on screen is collecting, so a hidden launcher costs nothing.
 */
class ClockTicker(private val context: Context, scope: CoroutineScope) {
    val now: StateFlow<Long> = callbackFlow {
        trySend(System.currentTimeMillis())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(System.currentTimeMillis())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate().stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), System.currentTimeMillis())
}

/** Battery level, charging state and Battery Saver, from sticky system broadcasts. */
class BatteryMonitor(private val context: Context, scope: CoroutineScope) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var lastBattery: Intent? = null

    val state: StateFlow<PowerState> = callbackFlow {
        fun emitFrom(intent: Intent?) {
            trySend(read(intent))
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) emitFrom(intent) else emitFrom(null)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        val sticky = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        emitFrom(sticky)
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate().stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), currentState())

    fun currentState(): PowerState {
        val sticky = ContextCompat.registerReceiver(
            context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return read(sticky)
    }

    private fun read(intent: Intent?): PowerState {
        val source = intent ?: lastBattery
        if (intent != null) lastBattery = intent
        val saver = powerManager?.isPowerSaveMode == true
        if (source == null) return PowerState.UNKNOWN.copy(systemSaver = saver)
        val level = source.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = source.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 100
        val status = source.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return PowerState(percent, charging, saver)
    }
}

/** Keep receivers alive briefly across configuration changes, then drop them. */
internal const val STOP_TIMEOUT_MS = 2_000L

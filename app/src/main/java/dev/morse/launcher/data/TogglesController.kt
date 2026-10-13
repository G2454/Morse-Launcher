package dev.morse.launcher.data

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

data class ToggleStates(
    val wifi: Boolean = false,
    val bluetooth: Boolean = false,
    val torch: Boolean = false,
    val doNotDisturb: Boolean = false,
)

/**
 * Quick toggles. Android does not let apps switch Wi-Fi or Bluetooth directly
 * any more, so those open the system panel; the torch and Do Not Disturb are
 * switched in place (DND needs one-time approval).
 */
class TogglesController(private val context: Context) {

    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val bluetooth = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val camera = context.getSystemService(CameraManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val torchId: String? by lazy {
        val cam = camera ?: return@lazy null
        try {
            cam.cameraIdList.firstOrNull { id ->
                cam.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: CameraAccessException) {
            null
        } catch (e: RuntimeException) {
            null
        }
    }
    private var torchOn = false

    @SuppressLint("MissingPermission")
    private fun read() = ToggleStates(
        wifi = wifi?.isWifiEnabled == true,
        bluetooth = try { bluetooth?.isEnabled == true } catch (e: SecurityException) { false },
        torch = torchOn,
        doNotDisturb = notifications?.currentInterruptionFilter?.let {
            it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        } == true,
    )

    val states: Flow<ToggleStates> = callbackFlow {
        trySend(read())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(read())
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val torchCallback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchId) {
                    torchOn = enabled
                    trySend(read())
                }
            }
        }
        camera?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        awaitClose {
            context.unregisterReceiver(receiver)
            camera?.unregisterTorchCallback(torchCallback)
        }
    }.conflate().distinctUntilChanged()

    val hasTorch: Boolean get() = torchId != null

    /** Returns an intent to start when the toggle needs the system UI, else null. */
    fun toggleWifi(): Intent = Intent(Settings.Panel.ACTION_WIFI)

    fun toggleBluetooth(): Intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

    fun setTorch(on: Boolean) {
        val id = torchId ?: return
        try {
            camera?.setTorchMode(id, on)
        } catch (e: CameraAccessException) {
            // Camera in use by another app: nothing to do.
        } catch (e: IllegalArgumentException) {
            // Same.
        }
    }

    fun toggleTorch() = setTorch(!torchOn)

    /** Toggles Do Not Disturb, or returns the settings intent that grants access first. */
    fun toggleDoNotDisturb(): Intent? {
        val nm = notifications ?: return null
        if (!nm.isNotificationPolicyAccessGranted) return Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        )
        return null
    }
}

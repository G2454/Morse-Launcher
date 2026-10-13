package dev.morse.launcher.data

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.SizeF

/** One installable widget from another app, as shown in the widget picker. */
data class ProviderEntry(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val minWidthDp: Float,
    val minHeightDp: Float,
    val targetColumns: Int,
    val targetRows: Int,
) {
    val provider: ComponentName get() = info.provider
}

/**
 * Hosts widgets from other apps. Binding needs the person's one-time approval
 * (handled by the activity with [bindIntent]); configuration activities run
 * through [startConfigure].
 */
class WidgetHostManager(private val context: Context) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context.applicationContext, HOST_ID)

    fun startListening() {
        try {
            host.startListening()
        } catch (e: RuntimeException) {
            // System server restarting; widgets will reconnect on the next start.
        }
    }

    fun stopListening() {
        try {
            host.stopListening()
        } catch (e: RuntimeException) {
            // Ignore.
        }
    }

    fun providers(): List<ProviderEntry> {
        val pm = context.packageManager
        val density = context.resources.displayMetrics.density
        val profiles = context.getSystemService(UserManager::class.java)?.userProfiles.orEmpty()
        val infos = profiles.flatMap { user ->
            try {
                manager.getInstalledProvidersForProfile(user)
            } catch (e: RuntimeException) {
                emptyList()
            }
        }
        return infos.map { info ->
            val appLabel = try {
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            } catch (e: Exception) {
                info.provider.packageName
            }
            val (tc, tr) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                info.targetCellWidth to info.targetCellHeight
            } else {
                0 to 0
            }
            ProviderEntry(
                info = info,
                label = info.loadLabel(pm) ?: appLabel,
                appLabel = appLabel,
                minWidthDp = info.minWidth / density,
                minHeightDp = info.minHeight / density,
                targetColumns = tc,
                targetRows = tr,
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, { it.appLabel }).thenBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (e: RuntimeException) {
            // Already gone.
        }
    }

    /** Binds without a prompt when the launcher already has permission. */
    fun bindIfAllowed(id: Int, entry: ProviderEntry): Boolean = try {
        manager.bindAppWidgetIdIfAllowed(id, entry.info.profile, entry.provider, null)
    } catch (e: RuntimeException) {
        false
    }

    fun bindIntent(id: Int, entry: ProviderEntry): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, entry.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, entry.info.profile)

    fun needsConfigure(id: Int): Boolean {
        val info = manager.getAppWidgetInfo(id) ?: return false
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
            if (optional) return false
        }
        return true
    }

    fun startConfigure(activity: Activity, id: Int, requestCode: Int): Boolean = try {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
        true
    } catch (e: RuntimeException) {
        false
    }

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return try {
            host.createView(context, id, info)
        } catch (e: RuntimeException) {
            null
        }
    }

    /** Tells the provider how big it is drawn, so it can pick a fitting layout. */
    fun reportSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
        }
    }

    companion object {
        const val HOST_ID = 0x4D4F
    }
}

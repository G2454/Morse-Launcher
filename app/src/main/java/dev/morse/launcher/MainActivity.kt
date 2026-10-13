package dev.morse.launcher

import android.app.ActivityOptions
import android.appwidget.AppWidgetHostView
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.morse.core.apps.AppKey
import dev.morse.core.power.MotionMode
import dev.morse.core.settings.ThemeMode
import dev.morse.core.settings.WallpaperMode
import dev.morse.launcher.system.SystemActions
import dev.morse.launcher.ui.LauncherRoot
import dev.morse.launcher.ui.components.IconLoader
import dev.morse.launcher.ui.widgets.HostedWidgetViews
import dev.morse.launcher.ui.widgets.WidgetEnvironment
import kotlinx.coroutines.launch

/**
 * The home activity. It owns the window (wallpaper flag, refresh rate, system
 * bar colours), activity results (permissions, widget binding) and app launches;
 * everything else lives in [LauncherViewModel].
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy { applicationContext.container }
    private val vm: LauncherViewModel by viewModels { LauncherViewModel.Factory(container) }

    private lateinit var bindWidget: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            vm.onBindResult(result.resultCode == RESULT_OK)
        }
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.onPermissionsResult()
        }

        val metrics = resources.displayMetrics
        vm.setScreenSize(metrics.widthPixels / metrics.density, metrics.heightPixels / metrics.density)

        val iconLoader = IconLoader { key, original, px -> container.icons.load(key, original, px) }
        val hostedViews = object : HostedWidgetViews {
            override fun create(context: Context, hostedId: Int): AppWidgetHostView? = container.widgets.createView(context, hostedId)
            override fun reportSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) =
                container.widgets.reportSize(view, widthDp, heightDp)
        }

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val motion by vm.motion.collectAsStateWithLifecycle()
            val use24 = DateFormat.is24HourFormat(this)
            val environment = remember(settings.temperatureUnit, use24) {
                WidgetEnvironment(
                    now = container.clock.now,
                    use24Hour = use24,
                    power = container.battery.state,
                    paused = vm.paused,
                    weather = container.weather.state,
                    temperatureUnit = settings.temperatureUnit,
                    calendarPermission = container.calendar.permission,
                    events = container.calendar.events,
                    nowPlaying = container.media.nowPlaying,
                    mediaAccess = container.media.access,
                    toggles = container.toggles.states,
                    hasTorch = container.toggles.hasTorch,
                    reminders = container.reminders.items,
                    actions = vm.widgetActions,
                )
            }
            val dark = when (settings.theme) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.AUTO -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            SideEffect {
                applyWindow(dark, settings.wallpaper == WallpaperMode.SYSTEM, motion == MotionMode.LOW_POWER)
            }
            LauncherRoot(vm, environment, iconLoader, hostedViews)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.events.collect { handle(it) }
            }
        }
        handleLaunchIntent(intent)
    }

    private fun applyWindow(dark: Boolean, systemWallpaper: Boolean, lowPower: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
        if (systemWallpaper) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        }
        // Cap the home screen at 60 Hz in low-power mode (the panel can run at 144 Hz).
        val attrs = window.attributes
        val rate = if (lowPower) 60f else 0f
        if (attrs.preferredRefreshRate != rate) {
            attrs.preferredRefreshRate = rate
            window.attributes = attrs
        }
    }

    private fun handle(event: UiEvent) {
        when (event) {
            is UiEvent.Launch -> launchApp(event.app, event.bounds)
            is UiEvent.StartIntent -> if (!SystemActions.start(this, event.intent)) toast("Not available on this phone")
            is UiEvent.RequestPermissions -> permissionLauncher.launch(event.permissions.toTypedArray())
            is UiEvent.BindWidget -> try {
                bindWidget.launch(event.intent)
            } catch (e: RuntimeException) {
                vm.onBindResult(false)
            }
            is UiEvent.ConfigureWidget ->
                if (!container.widgets.startConfigure(this, event.appWidgetId, REQUEST_CONFIGURE)) vm.onConfigureResult(true)
            is UiEvent.OpenAppInfo -> container.apps.openAppInfo(event.app, event.bounds?.toAndroid())
            is UiEvent.Message -> toast(event.text)
        }
    }

    private fun launchApp(app: AppKey, bounds: Rect?) {
        val rect = bounds?.toAndroid()
        val options = if (vm.motion.value == MotionMode.LOW_POWER || rect == null) {
            ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            ActivityOptions.makeScaleUpAnimation(window.decorView, rect.left, rect.top, rect.width(), rect.height())
        }
        if (!container.apps.launch(app, rect, options.toBundle())) toast("Couldn't open that app")
    }

    private fun Rect.toAndroid() = android.graphics.Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    override fun onStart() {
        super.onStart()
        container.widgets.startListening()
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    override fun onStop() {
        super.onStop()
        container.widgets.stopListening()
        vm.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomePressed()
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.component?.className?.endsWith(".SettingsAlias") == true) vm.openSettings()
    }

    @Deprecated("Widget configuration still reports through onActivityResult.")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE) vm.onConfigureResult(resultCode == RESULT_OK)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) container.icons.clear()
    }

    private companion object {
        const val REQUEST_CONFIGURE = 0x4D31
    }
}

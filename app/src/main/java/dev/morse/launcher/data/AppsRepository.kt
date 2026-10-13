package dev.morse.launcher.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import dev.morse.core.apps.AppInfo
import dev.morse.core.apps.AppKey
import dev.morse.core.apps.IconResolver
import dev.morse.core.apps.Library
import dev.morse.core.apps.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Every launchable activity across the user's profiles, kept current through
 * [LauncherApps.Callback]. Also resolves which apps fill common [Role]s.
 */
class AppsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data object Loading : State
        data class Ready(val apps: List<AppInfo>) : State
    }

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val infos = ConcurrentHashMap<AppKey, LauncherActivityInfo>()

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _roles = MutableStateFlow<Map<Role, AppKey>>(emptyMap())
    val roles: StateFlow<Map<Role, AppKey>> = _roles.asStateFlow()

    private val _removed = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Packages that were uninstalled while the launcher was running. */
    val removedPackages: SharedFlow<String> = _removed.asSharedFlow()

    private var started = false
    private var loadJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            _removed.tryEmit(packageName)
            refresh()
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = scope.launch(Dispatchers.IO) { load() }
    }

    private fun load() {
        val roleByPackage = resolveRolePackages()
        val result = ArrayList<AppInfo>()
        val seen = HashSet<AppKey>()
        for (user in userManager.userProfiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val list = try {
                launcherApps.getActivityList(null, user)
            } catch (e: SecurityException) {
                emptyList()
            }
            for (info in list) {
                val cn = info.componentName
                val key = AppKey(cn.packageName, cn.className, serial)
                if (!seen.add(key)) continue
                infos[key] = info
                val appInfo = info.applicationInfo
                val role = if (serial == mainSerial()) roleByPackage[cn.packageName] else null
                val glyph = IconResolver.glyphFor(cn.packageName, role, context.packageName)
                result += AppInfo(
                    key = key,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { cn.packageName },
                    category = Library.categorize(appInfo.category, glyph),
                    installedAtMillis = info.firstInstallTime,
                    isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    glyph = glyph,
                )
            }
        }
        infos.keys.retainAll(seen)
        result.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        val roles = HashMap<Role, AppKey>()
        roleByPackage.forEach { (pkg, role) ->
            result.firstOrNull { it.key.packageName == pkg && it.key.userSerial == mainSerial() }?.let { roles[role] = it.key }
        }
        _roles.value = roles
        _state.value = State.Ready(result)
    }

    private fun mainSerial(): Long = userManager.getSerialNumberForUser(android.os.Process.myUserHandle())

    /** Asks Android which package handles each role. Ambiguous roles (no default chosen) are skipped. */
    private fun resolveRolePackages(): Map<String, Role> {
        val pm = context.packageManager
        val out = LinkedHashMap<String, Role>()
        for ((role, intent) in roleIntents()) {
            val pkg = try {
                @Suppress("DEPRECATION")
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            } catch (e: RuntimeException) {
                null
            }
            if (pkg != null && pkg != "android" && pkg !in out) out[pkg] = role
        }
        return out
    }

    private fun roleIntents(): List<Pair<Role, Intent>> = listOf(
        Role.DIALER to Intent(Intent.ACTION_DIAL),
        Role.SMS to Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
        Role.BROWSER to Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE),
        Role.CAMERA to Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        Role.GALLERY to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
        Role.EMAIL to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL),
        Role.MAPS to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS),
        Role.MUSIC to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC),
        Role.CALENDAR to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
        Role.CONTACTS to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS),
        Role.CALCULATOR to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR),
        Role.FILES to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES),
        Role.STORE to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MARKET),
        Role.WEATHER to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, "android.intent.category.APP_WEATHER"),
        Role.CLOCK to Intent(AlarmClock.ACTION_SHOW_ALARMS),
        Role.SETTINGS to Intent(Settings.ACTION_SETTINGS),
    )

    fun activityInfo(key: AppKey): LauncherActivityInfo? = infos[key]

    fun isInstalled(key: AppKey): Boolean = infos.containsKey(key)

    private fun userFor(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    /** Starts an app. [sourceBounds] lets the system animate from the icon. */
    fun launch(key: AppKey, sourceBounds: Rect?, options: Bundle?): Boolean {
        val user = userFor(key.userSerial) ?: return false
        return try {
            launcherApps.startMainActivity(ComponentName(key.packageName, key.className), user, sourceBounds, options)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: IllegalStateException) {
            false
        }
    }

    fun openAppInfo(key: AppKey, sourceBounds: Rect?) {
        val user = userFor(key.userSerial) ?: return
        try {
            launcherApps.startAppDetailsActivity(ComponentName(key.packageName, key.className), user, sourceBounds, null)
        } catch (e: RuntimeException) {
            // App vanished between the tap and now.
        }
    }

    fun uninstallIntent(key: AppKey): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

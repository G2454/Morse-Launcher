package dev.morse.launcher.system

import android.app.SearchManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Small helpers for starting system screens and other apps safely. */
object SystemActions {

    fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    fun openSettingsAction(context: Context, action: String): Boolean =
        start(context, Intent(action)) || start(context, Intent(Settings.ACTION_SETTINGS))

    fun webSearch(context: Context, query: String): Boolean {
        val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (start(context, search)) return true
        val url = "https://duckduckgo.com/?q=" + Uri.encode(query)
        return start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun dial(context: Context, number: String) =
        start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))

    fun message(context: Context, number: String) =
        start(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

    fun openContact(context: Context, lookupUri: Uri) =
        start(context, Intent(Intent.ACTION_VIEW, lookupUri))

    fun openNotificationAccess(context: Context) =
        start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun openAppPermissions(context: Context) =
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))

    fun openWallpaperPicker(context: Context) =
        start(context, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))

    fun isDefaultLauncher(context: Context): Boolean {
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME)
    }

    /** The intent that asks to become the default home app, or the system screen for it. */
    fun defaultLauncherIntent(context: Context): Intent {
        val rm = context.getSystemService(RoleManager::class.java)
        return if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
            rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }
    }
}

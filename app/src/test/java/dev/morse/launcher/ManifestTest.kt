package dev.morse.launcher

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.morse.launcher.service.MorseNotificationListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The manifest is what makes Morse a launcher; these checks keep it that way. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pm: PackageManager = context.packageManager

    @Test
    fun registersAsAHomeScreen() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName)
        val matches = pm.queryIntentActivities(home, 0)
        assertTrue("HOME intent not handled", matches.any { it.activityInfo.name == MainActivity::class.java.name })
    }

    @Test
    fun settingsEntryAppearsInTheAppList() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        val matches = pm.queryIntentActivities(launcher, 0)
        assertTrue(matches.any { it.activityInfo.name.endsWith(".SettingsAlias") })
        // The home activity itself must not show up as an app.
        assertTrue(matches.none { it.activityInfo.name == MainActivity::class.java.name })
    }

    @Test
    fun notificationListenerIsProtected() {
        val info = pm.getServiceInfo(ComponentName(context, MorseNotificationListener::class.java), 0)
        assertEquals(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, info.permission)
    }

    @Test
    fun requestsOnlyTheExpectedPermissions() {
        @Suppress("DEPRECATION")
        val requested = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        val expected = setOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_NOTIFICATION_POLICY,
            Manifest.permission.REQUEST_DELETE_PACKAGES,
            Manifest.permission.QUERY_ALL_PACKAGES,
        )
        // Libraries may add their own (e.g. a dynamic-receiver permission); ours must all be there,
        // and nothing heavy like fine location or background location may sneak in.
        assertTrue(requested.containsAll(expected))
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION !in requested)
        assertTrue(Manifest.permission.ACCESS_BACKGROUND_LOCATION !in requested)
        assertTrue(Manifest.permission.WAKE_LOCK !in requested)
    }
}

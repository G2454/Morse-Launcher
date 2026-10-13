package dev.morse.launcher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.morse.launcher.container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Packages that currently have a notification the person can dismiss. */
object NotificationBadges {
    private val _packages = MutableStateFlow<Set<String>>(emptySet())
    val packages: StateFlow<Set<String>> = _packages.asStateFlow()

    fun set(packages: Set<String>) {
        if (packages != _packages.value) _packages.value = packages
    }

    /** Pure rule, shared with tests: ongoing (music, navigation…) notifications never make a dot. */
    fun badgePackages(notifications: List<StatusBarNotification>): Set<String> =
        notifications.asSequence().filter { it.isClearable && !it.isOngoing }.map { it.packageName }.toSet()
}

/**
 * Feeds notification dots and lets the Music widget see media sessions.
 * Android runs it only after the person grants notification access.
 */
class MorseNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        refresh()
        applicationContext.container.media.start()
    }

    override fun onListenerDisconnected() {
        NotificationBadges.set(emptySet())
        applicationContext.container.media.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val active = try {
            activeNotifications
        } catch (e: SecurityException) {
            null
        } catch (e: RuntimeException) {
            null
        } ?: return
        NotificationBadges.set(NotificationBadges.badgePackages(active.toList()))
    }
}

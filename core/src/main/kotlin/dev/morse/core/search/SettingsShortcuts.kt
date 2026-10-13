package dev.morse.core.search

/**
 * System settings pages that search can open directly. [action] is an
 * `android.provider.Settings` intent action string.
 */
data class SettingShortcut(
    val title: String,
    val section: String,
    val action: String,
    val keywords: List<String> = emptyList(),
)

object SettingsShortcuts {
    val all: List<SettingShortcut> = listOf(
        SettingShortcut("Wi-Fi", "Connections", "android.settings.WIFI_SETTINGS", listOf("wifi", "wireless", "internet", "network")),
        SettingShortcut("Bluetooth", "Connections", "android.settings.BLUETOOTH_SETTINGS", listOf("headphones", "pair", "earbuds")),
        SettingShortcut("Mobile data", "Connections", "android.settings.DATA_ROAMING_SETTINGS", listOf("roaming", "sim", "4g", "5g")),
        SettingShortcut("Airplane mode", "Connections", "android.settings.AIRPLANE_MODE_SETTINGS", listOf("flight", "offline")),
        SettingShortcut("Hotspot & tethering", "Connections", "android.settings.WIRELESS_SETTINGS", listOf("tether", "share internet")),
        SettingShortcut("Battery", "Battery", "android.intent.action.POWER_USAGE_SUMMARY", listOf("power", "charge", "usage")),
        SettingShortcut("Battery saver", "Battery", "android.settings.BATTERY_SAVER_SETTINGS", listOf("power saving", "low power")),
        SettingShortcut("Display", "Display", "android.settings.DISPLAY_SETTINGS", listOf("brightness", "screen", "refresh rate", "dark theme")),
        SettingShortcut("Sound & vibration", "Sound", "android.settings.SOUND_SETTINGS", listOf("volume", "ringtone", "vibrate")),
        SettingShortcut("Do not disturb", "Sound", "android.settings.ZEN_MODE_SETTINGS", listOf("dnd", "silent", "focus")),
        SettingShortcut("Notifications", "Notifications", "android.settings.NOTIFICATION_SETTINGS", listOf("alerts", "badges")),
        SettingShortcut("Apps", "Apps", "android.settings.APPLICATION_SETTINGS", listOf("applications", "uninstall", "permissions")),
        SettingShortcut("Default apps", "Apps", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS", listOf("home app", "launcher", "browser")),
        SettingShortcut("Storage", "Storage", "android.settings.INTERNAL_STORAGE_SETTINGS", listOf("space", "memory", "files")),
        SettingShortcut("Location", "Privacy", "android.settings.LOCATION_SOURCE_SETTINGS", listOf("gps")),
        SettingShortcut("Security", "Privacy", "android.settings.SECURITY_SETTINGS", listOf("lock screen", "fingerprint", "password", "face")),
        SettingShortcut("Privacy", "Privacy", "android.settings.PRIVACY_SETTINGS", listOf("permissions")),
        SettingShortcut("Accessibility", "Accessibility", "android.settings.ACCESSIBILITY_SETTINGS", listOf("talkback", "font size")),
        SettingShortcut("Date & time", "System", "android.settings.DATE_SETTINGS", listOf("clock", "time zone")),
        SettingShortcut("Languages", "System", "android.settings.LOCALE_SETTINGS", listOf("language", "keyboard")),
        SettingShortcut("Accounts", "Accounts", "android.settings.SYNC_SETTINGS", listOf("google", "sync")),
        SettingShortcut("About phone", "System", "android.settings.DEVICE_INFO_SETTINGS", listOf("model", "version", "android")),
        SettingShortcut("Developer options", "System", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS", listOf("usb debugging", "adb")),
        SettingShortcut("Wallpaper", "Display", "android.intent.action.SET_WALLPAPER", listOf("background")),
        SettingShortcut("Call settings", "Phone", "android.telecom.action.SHOW_CALL_SETTINGS", listOf("call forwarding", "caller id", "voicemail")),
    )
}

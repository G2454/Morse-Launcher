package dev.morse.core.apps

/**
 * Identifies one launchable activity for one user profile.
 * [userSerial] is the profile's serial number (0 for the main profile).
 */
data class AppKey(
    val packageName: String,
    val className: String,
    val userSerial: Long = 0L,
) {
    fun flatten(): String = "$packageName/$className#$userSerial"

    companion object {
        fun parse(value: String?): AppKey? {
            if (value.isNullOrBlank()) return null
            val slash = value.indexOf('/')
            if (slash <= 0 || slash == value.lastIndex) return null
            val hash = value.lastIndexOf('#')
            val pkg = value.substring(0, slash)
            val cls: String
            val serial: Long
            if (hash > slash) {
                cls = value.substring(slash + 1, hash)
                serial = value.substring(hash + 1).toLongOrNull() ?: return null
            } else {
                cls = value.substring(slash + 1)
                serial = 0L
            }
            if (cls.isEmpty()) return null
            return AppKey(pkg, cls, serial)
        }
    }
}

/** Library groupings, in the order they are shown. */
enum class AppCategory(val title: String) {
    SOCIAL("Social"),
    PRODUCTIVITY("Productivity"),
    CREATIVITY("Creativity"),
    MEDIA("Media"),
    GAMES("Games"),
    UTILITIES("Utilities"),
    OTHER("Other"),
}

/** Everything the launcher knows about an installed app. */
data class AppInfo(
    val key: AppKey,
    val label: String,
    val category: AppCategory = AppCategory.OTHER,
    val installedAtMillis: Long = 0L,
    val isSystem: Boolean = false,
    /** The Morse glyph for this app, when one applies (see [IconResolver]). */
    val glyph: dev.morse.core.glyph.Glyph? = null,
)

/** Well-known jobs an app can do; used to pick defaults and glyphs. */
enum class Role {
    DIALER,
    SMS,
    BROWSER,
    CAMERA,
    GALLERY,
    EMAIL,
    MAPS,
    MUSIC,
    CALENDAR,
    CLOCK,
    CONTACTS,
    CALCULATOR,
    FILES,
    STORE,
    SETTINGS,
    WEATHER,
}

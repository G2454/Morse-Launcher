package dev.morse.core.apps

import dev.morse.core.glyph.Glyph

/**
 * Picks a Morse glyph for an app. Apps that fill a [Role] (found by asking
 * Android which app handles dialling, browsing and so on) get that role's glyph;
 * a short table covers common system packages. Everything else returns null and
 * is drawn from its monochrome icon layer or its initial.
 */
object IconResolver {

    val roleGlyphs: Map<Role, Glyph> = mapOf(
        Role.DIALER to Glyph.PHONE,
        Role.SMS to Glyph.MESSAGES,
        Role.BROWSER to Glyph.BROWSER,
        Role.CAMERA to Glyph.CAMERA,
        Role.GALLERY to Glyph.PHOTOS,
        Role.EMAIL to Glyph.MAIL,
        Role.MAPS to Glyph.MAPS,
        Role.MUSIC to Glyph.MUSIC,
        Role.CALENDAR to Glyph.CALENDAR,
        Role.CLOCK to Glyph.CLOCK,
        Role.CONTACTS to Glyph.CONTACTS,
        Role.CALCULATOR to Glyph.CALCULATOR,
        Role.FILES to Glyph.FILES,
        Role.STORE to Glyph.STORE,
        Role.SETTINGS to Glyph.SETTINGS,
        Role.WEATHER to Glyph.WEATHER,
    )

    /** System and first-party packages whose job is unambiguous. */
    val packageGlyphs: Map<String, Glyph> = mapOf(
        "com.android.settings" to Glyph.SETTINGS,
        "com.android.dialer" to Glyph.PHONE,
        "com.google.android.dialer" to Glyph.PHONE,
        "com.android.messaging" to Glyph.MESSAGES,
        "com.google.android.apps.messaging" to Glyph.MESSAGES,
        "com.android.contacts" to Glyph.CONTACTS,
        "com.google.android.contacts" to Glyph.CONTACTS,
        "com.android.camera" to Glyph.CAMERA,
        "com.android.camera2" to Glyph.CAMERA,
        "com.motorola.camera2" to Glyph.CAMERA,
        "com.motorola.camera3" to Glyph.CAMERA,
        "com.google.android.GoogleCamera" to Glyph.CAMERA,
        "com.google.android.apps.photos" to Glyph.PHOTOS,
        "com.android.gallery3d" to Glyph.PHOTOS,
        "com.android.chrome" to Glyph.BROWSER,
        "com.google.android.gm" to Glyph.MAIL,
        "com.android.email" to Glyph.MAIL,
        "com.google.android.apps.maps" to Glyph.MAPS,
        "com.google.android.calendar" to Glyph.CALENDAR,
        "com.android.calendar" to Glyph.CALENDAR,
        "com.google.android.deskclock" to Glyph.CLOCK,
        "com.android.deskclock" to Glyph.CLOCK,
        "com.google.android.calculator" to Glyph.CALCULATOR,
        "com.android.calculator2" to Glyph.CALCULATOR,
        "com.google.android.apps.nbu.files" to Glyph.FILES,
        "com.google.android.documentsui" to Glyph.FILES,
        "com.android.documentsui" to Glyph.FILES,
        "com.android.vending" to Glyph.STORE,
        "com.google.android.keep" to Glyph.NOTES,
        "com.google.android.apps.recorder" to Glyph.RECORDER,
        "com.android.soundrecorder" to Glyph.RECORDER,
        "com.google.android.apps.walletnfcrel" to Glyph.WALLET,
        "com.google.android.apps.translate" to Glyph.TRANSLATE,
        "com.google.android.apps.fitness" to Glyph.HEALTH,
        "com.google.android.apps.books" to Glyph.BOOKS,
        "com.google.android.apps.magazines" to Glyph.NEWS,
        "com.google.android.apps.podcasts" to Glyph.PODCASTS,
        "com.google.android.youtube" to Glyph.VIDEO,
        "com.google.android.apps.youtube.music" to Glyph.MUSIC,
        "com.termux" to Glyph.TERMINAL,
    )

    /**
     * @param ownPackage the launcher's own package; its settings entry gets the settings glyph.
     */
    fun glyphFor(packageName: String, role: Role?, ownPackage: String? = null): Glyph? {
        if (ownPackage != null && packageName == ownPackage) return Glyph.LAYERS
        role?.let { roleGlyphs[it] }?.let { return it }
        return packageGlyphs[packageName]
    }

    /** The character drawn on a tile when there is no glyph or monochrome layer. */
    fun initialFor(label: String): String {
        val first = label.trim().firstOrNull { it.isLetterOrDigit() } ?: return "#"
        return first.uppercaseChar().toString()
    }
}

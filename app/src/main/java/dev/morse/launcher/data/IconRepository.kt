package dev.morse.launcher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.morse.core.apps.AppKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a tile shows when the app has no Morse glyph. */
sealed interface IconImage {
    /** The app's own one-colour layer (Android 13+); tinted by the launcher. */
    data class Monochrome(val bitmap: ImageBitmap) : IconImage

    /** The app's full-colour icon, used when the person chose "Original icon". */
    data class Original(val bitmap: ImageBitmap) : IconImage

    /** Nothing usable: the tile shows the app's initial instead. */
    data object None : IconImage
}

/**
 * Loads app icons off the main thread and keeps a small cache. Monochrome layers
 * are rendered once to a bitmap; nothing is decoded for apps drawn with a glyph.
 */
class IconRepository(private val apps: AppsRepository) {

    private val cache = object : LruCache<String, IconImage>(MAX_BYTES) {
        override fun sizeOf(key: String, value: IconImage): Int = when (value) {
            is IconImage.Monochrome -> value.bitmap.width * value.bitmap.height * 4
            is IconImage.Original -> value.bitmap.width * value.bitmap.height * 4
            IconImage.None -> 64
        }
    }

    suspend fun load(key: AppKey, original: Boolean, sizePx: Int): IconImage {
        val cacheKey = "${key.flatten()}|$original|$sizePx"
        cache.get(cacheKey)?.let { return it }
        val image = withContext(Dispatchers.IO) { render(key, original, sizePx) }
        cache.put(cacheKey, image)
        return image
    }

    fun clear() = cache.evictAll()

    private fun render(key: AppKey, original: Boolean, sizePx: Int): IconImage {
        val info = apps.activityInfo(key) ?: return IconImage.None
        val drawable: Drawable = try {
            info.getIcon(0)
        } catch (e: RuntimeException) {
            return IconImage.None
        } ?: return IconImage.None
        if (original) {
            return IconImage.Original(drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
            val mono = drawable.monochrome ?: return IconImage.None
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            mono.setBounds(0, 0, sizePx, sizePx)
            mono.draw(Canvas(bitmap))
            return IconImage.Monochrome(bitmap.asImageBitmap())
        }
        return IconImage.None
    }

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}

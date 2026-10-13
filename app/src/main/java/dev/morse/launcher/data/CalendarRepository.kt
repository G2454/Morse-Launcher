package dev.morse.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dev.morse.core.calendar.CalendarEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Upcoming calendar instances (yesterday to ~6 weeks ahead). Re-queries only
 * when the calendar provider reports a change, and only while visible.
 */
class CalendarRepository(private val context: Context, scope: CoroutineScope) {

    private val _permission = MutableStateFlow(hasPermission())
    val permission: StateFlow<Boolean> = _permission.asStateFlow()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Call after a permission prompt or on resume. */
    fun recheckPermission() {
        _permission.value = hasPermission()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val events: StateFlow<List<CalendarEvent>> = _permission.flatMapLatest { granted ->
        if (!granted) flowOf(emptyList()) else observe()
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private fun observe() = callbackFlow {
        fun requery() {
            launch(Dispatchers.IO) { trySend(query()) }
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = requery()
        }
        context.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
        requery()
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    private fun query(): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        val now = System.currentTimeMillis()
        val begin = now - DAY
        val end = now + 42 * DAY
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val out = ArrayList<CalendarEvent>()
        try {
            context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Instances.VISIBLE} = 1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext() && out.size < MAX_EVENTS) {
                    out += CalendarEvent(
                        title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        startMillis = c.getLong(1),
                        endMillis = c.getLong(2),
                        allDay = c.getInt(3) == 1,
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return out
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val MAX_EVENTS = 300
    }
}

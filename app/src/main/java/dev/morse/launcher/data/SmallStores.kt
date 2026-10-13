package dev.morse.launcher.data

import dev.morse.core.apps.AppKey
import dev.morse.core.apps.Usage
import dev.morse.core.reminders.Reminder
import dev.morse.core.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** App launch counts (for Suggestions and search ranking). Never leaves the device. */
class UsageRepository(file: File, private val scope: CoroutineScope) {
    private val store = JsonFile(file)
    private val _counts = MutableStateFlow<Map<AppKey, Int>>(emptyMap())
    val counts: StateFlow<Map<AppKey, Int>> = _counts.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val loaded = Usage.decode(store.read())
            _counts.update { current -> loaded + current }
        }
    }

    fun recordLaunch(app: AppKey) {
        _counts.update { Usage.increment(it, app) }
        val snapshot = _counts.value
        scope.launch(Dispatchers.IO) { store.write(Usage.encode(snapshot)) }
    }
}

/** The on-device checklist behind the Reminders widget. */
class RemindersRepository(file: File, private val scope: CoroutineScope) {
    private val store = JsonFile(file)
    private val _items = MutableStateFlow<List<Reminder>>(emptyList())
    val items: StateFlow<List<Reminder>> = _items.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val loaded = Reminders.decode(store.read())
            _items.update { current -> if (current.isEmpty()) loaded else current }
        }
    }

    fun add(text: String) = change { Reminders.add(it, text) }
    fun toggle(id: String) = change { Reminders.toggle(it, id) }
    fun remove(id: String) = change { Reminders.remove(it, id) }
    fun clearDone() = change { Reminders.clearDone(it) }

    private fun change(transform: (List<Reminder>) -> List<Reminder>) {
        _items.update(transform)
        val snapshot = _items.value
        scope.launch(Dispatchers.IO) { store.write(Reminders.encode(snapshot)) }
    }
}

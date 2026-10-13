package dev.morse.launcher.data

import dev.morse.core.layout.HomeLayout
import dev.morse.core.layout.LayoutCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Holds the home layout in memory and writes it to disk shortly after each
 * change (changes made in quick succession, like a drag, cause one write).
 */
class LayoutRepository(
    file: File,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val saveDelayMillis: Long = 400L,
) {
    sealed interface State {
        data object Loading : State
        /** No layout saved yet: the first run. */
        data object Empty : State
        data class Ready(val layout: HomeLayout) : State
    }

    private val store = JsonFile(file)
    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()
    private var saveJob: Job? = null

    suspend fun load() {
        if (_state.value != State.Loading) return
        val decoded = withContext(io) { store.read()?.let { LayoutCodec.decode(it) } }
        _state.compareAndSet(State.Loading, if (decoded != null) State.Ready(decoded) else State.Empty)
    }

    fun set(layout: HomeLayout) {
        _state.value = State.Ready(layout)
        scheduleSave()
    }

    /** Applies [transform] to the current layout. Does nothing until a layout exists. */
    fun update(transform: (HomeLayout) -> HomeLayout) {
        var changed = false
        _state.update { s ->
            if (s is State.Ready) {
                val next = transform(s.layout)
                if (next !== s.layout && next != s.layout) {
                    changed = true
                    State.Ready(next)
                } else {
                    s
                }
            } else {
                s
            }
        }
        if (changed) scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch(io) {
            delay(saveDelayMillis)
            writeNow()
        }
    }

    /** Writes immediately; used when the launcher goes to the background. */
    suspend fun flush() {
        saveJob?.cancel()
        withContext(io) { writeNow() }
    }

    private fun writeNow() {
        val s = _state.value
        if (s is State.Ready) store.write(LayoutCodec.encode(s.layout))
    }
}

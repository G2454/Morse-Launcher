package dev.morse.launcher.data

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** A small text file written atomically, so a crash mid-write never leaves half a layout. */
class JsonFile(file: File) {
    private val atomic = AtomicFile(file)

    fun read(): String? = try {
        atomic.readFully().toString(Charsets.UTF_8)
    } catch (e: FileNotFoundException) {
        null
    } catch (e: IOException) {
        null
    }

    @Synchronized
    fun write(text: String): Boolean {
        val out = try {
            atomic.startWrite()
        } catch (e: IOException) {
            return false
        }
        return try {
            out.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(out)
            true
        } catch (e: IOException) {
            atomic.failWrite(out)
            false
        }
    }
}

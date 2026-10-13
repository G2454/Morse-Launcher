package dev.morse.launcher

import android.app.Application
import android.content.Context

class MorseApp : Application() {

    /** Created on first use so tests and the notification listener only pay for what they touch. */
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.container: AppContainer
    get() = (applicationContext as MorseApp).container

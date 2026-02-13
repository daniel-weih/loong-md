package com.loongmd

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.Frame
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowStateListener
import java.util.prefs.Preferences

private const val PREF_X = "window.x"
private const val PREF_Y = "window.y"
private const val PREF_WIDTH = "window.width"
private const val PREF_HEIGHT = "window.height"
private const val PREF_MAXIMIZED = "window.maximized"

private val windowPrefs = Preferences.userRoot().node("com.loongmd.window")

private data class SavedWindowBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val maximized: Boolean
)

fun main() = application {
    val savedBounds = remember { loadWindowBounds() }
    var composeWindow by remember { mutableStateOf<Frame?>(null) }

    Window(
        onCloseRequest = {
            composeWindow?.let { saveWindowBounds(it, flush = true) }
            exitApplication()
        },
        title = "LoongMD"
    ) {
        LaunchedEffect(window) {
            composeWindow = window
            savedBounds?.let { bounds ->
                window.setBounds(bounds.x, bounds.y, bounds.width, bounds.height)
                if (bounds.maximized) {
                    window.extendedState = window.extendedState or Frame.MAXIMIZED_BOTH
                }
            }
        }

        DisposableEffect(window) {
            val componentListener = object : ComponentAdapter() {
                override fun componentMoved(event: ComponentEvent?) {
                    saveWindowBounds(window)
                }

                override fun componentResized(event: ComponentEvent?) {
                    saveWindowBounds(window)
                }
            }
            val stateListener = WindowStateListener {
                saveWindowBounds(window)
            }

            window.addComponentListener(componentListener)
            window.addWindowStateListener(stateListener)

            onDispose {
                window.removeComponentListener(componentListener)
                window.removeWindowStateListener(stateListener)
                saveWindowBounds(window, flush = true)
            }
        }
        App()
    }
}

private fun loadWindowBounds(): SavedWindowBounds? {
    if (windowPrefs.get(PREF_X, null) == null || windowPrefs.get(PREF_Y, null) == null) {
        return null
    }

    val x = windowPrefs.getInt(PREF_X, 0)
    val y = windowPrefs.getInt(PREF_Y, 0)
    val width = windowPrefs.getInt(PREF_WIDTH, 1000)
    val height = windowPrefs.getInt(PREF_HEIGHT, 700)
    val maximized = windowPrefs.getBoolean(PREF_MAXIMIZED, false)

    if (width <= 0 || height <= 0) {
        return null
    }

    return SavedWindowBounds(
        x = x,
        y = y,
        width = width,
        height = height,
        maximized = maximized
    )
}

private fun saveWindowBounds(window: Frame, flush: Boolean = false) {
    val isMaximized = window.extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH

    if (!isMaximized) {
        windowPrefs.putInt(PREF_X, window.x)
        windowPrefs.putInt(PREF_Y, window.y)
        windowPrefs.putInt(PREF_WIDTH, window.width)
        windowPrefs.putInt(PREF_HEIGHT, window.height)
    }
    windowPrefs.putBoolean(PREF_MAXIMIZED, isMaximized)

    if (flush) {
        runCatching {
            windowPrefs.flush()
        }
    }
}

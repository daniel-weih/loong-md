package com.loongmd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import org.jetbrains.skia.Image

private const val CUSTOM_MD_ICON_PATH = "/Users/daniel/Downloads/jimeng-2026-02-15-2573.png"

@Composable
actual fun rememberCustomMarkdownFileIcon(): ImageBitmap? {
    return remember {
        val file = File(CUSTOM_MD_ICON_PATH)
        if (!file.exists() || !file.isFile) {
            null
        } else {
            runCatching {
                Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
            }.getOrNull()
        }
    }
}

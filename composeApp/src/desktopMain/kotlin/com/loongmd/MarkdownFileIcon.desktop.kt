package com.loongmd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

private const val CUSTOM_MD_ICON_RESOURCE = "/md_file_icon.png"

@Composable
actual fun rememberCustomMarkdownFileIcon(): ImageBitmap? {
    return remember {
        val iconBytes = object {}.javaClass
            .getResourceAsStream(CUSTOM_MD_ICON_RESOURCE)
            ?.use { it.readBytes() }
            ?: return@remember null
        runCatching { Image.makeFromEncoded(iconBytes).toComposeImageBitmap() }.getOrNull()
    }
}

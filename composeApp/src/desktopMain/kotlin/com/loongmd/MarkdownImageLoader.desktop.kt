package com.loongmd

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.io.File
import java.net.URI

actual suspend fun loadMarkdownImageBitmap(
    source: String,
    markdownFilePath: String?
): ImageBitmap? {
    val normalized = source.trim().removeSurrounding("<", ">")
    if (normalized.isBlank()) return null

    val bytes = runCatching {
        when {
            normalized.startsWith("http://", ignoreCase = true) ||
                normalized.startsWith("https://", ignoreCase = true) -> {
                URI(normalized).toURL().openStream().use { it.readBytes() }
            }

            normalized.startsWith("file://", ignoreCase = true) -> {
                File(URI(normalized)).takeIf { it.exists() }?.readBytes()
            }

            else -> {
                resolveLocalImageFile(normalized, markdownFilePath)?.readBytes()
            }
        }
    }.getOrNull() ?: return null

    return runCatching {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}

private fun resolveLocalImageFile(source: String, markdownFilePath: String?): File? {
    val direct = File(source)
    if (direct.isAbsolute) {
        return direct.takeIf { it.exists() && it.isFile }
    }

    val parent = markdownFilePath
        ?.let { File(it).parentFile }
        ?: return null

    val resolved = File(parent, source).toPath().normalize().toFile()
    return resolved.takeIf { it.exists() && it.isFile }
}

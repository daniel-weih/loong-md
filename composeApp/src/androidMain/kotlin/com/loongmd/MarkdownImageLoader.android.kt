package com.loongmd

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.net.URI

actual suspend fun loadMarkdownImageBitmap(
    source: String,
    markdownFilePath: String?
): ImageBitmap? {
    val normalized = source.trim().removeSurrounding("<", ">")
    if (normalized.isBlank()) return null

    return runCatching {
        when {
            normalized.startsWith("http://", ignoreCase = true) ||
                normalized.startsWith("https://", ignoreCase = true) -> {
                URI(normalized).toURL().openStream().use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }

            normalized.startsWith("file://", ignoreCase = true) -> {
                val file = File(URI(normalized))
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }

            else -> {
                val file = resolveLocalImageFile(normalized, markdownFilePath)
                BitmapFactory.decodeFile(file?.absolutePath)?.asImageBitmap()
            }
        }
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

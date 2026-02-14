package com.loongmd

import androidx.compose.ui.graphics.ImageBitmap

expect suspend fun loadMarkdownImageBitmap(
    source: String,
    markdownFilePath: String?
): ImageBitmap?

package com.loongmd

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

data class MarkdownFile(
    val id: String,
    val name: String,
    val path: String,
    val relativePath: String
)

sealed interface MarkdownTreeTarget {
    data class FileTarget(val file: MarkdownFile) : MarkdownTreeTarget
    data class DirectoryTarget(val relativePath: String) : MarkdownTreeTarget
}

interface MarkdownDataSource {
    val rootDescription: String
    val canSelectRoot: Boolean
    val supportsTreeContextActions: Boolean

    suspend fun listMarkdownFiles(): List<MarkdownFile>
    suspend fun readMarkdown(file: MarkdownFile): String
    suspend fun writeMarkdown(file: MarkdownFile, content: String)
    suspend fun refreshRoot(): String?
    suspend fun revealInFinder(target: MarkdownTreeTarget)
    suspend fun moveToTrash(target: MarkdownTreeTarget)
}

@Composable
expect fun rememberMarkdownDataSource(): MarkdownDataSource

@Composable
expect fun rememberCustomMarkdownFileIcon(): ImageBitmap?

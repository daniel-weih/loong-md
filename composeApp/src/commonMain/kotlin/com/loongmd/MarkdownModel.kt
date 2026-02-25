package com.loongmd

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class MarkdownFile(
    val id: String,
    val name: String,
    val path: String,
    val relativePath: String,
    val lastModified: Long = 0L
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
    fun observeFileTreeChanges(): Flow<Unit> = emptyFlow()
    suspend fun loadLastSelectedFileId(): String? = null
    suspend fun saveLastSelectedFileId(fileId: String?) {}
}

@Composable
expect fun rememberMarkdownDataSource(): MarkdownDataSource

@Composable
expect fun rememberCustomMarkdownFileIcon(): ImageBitmap?

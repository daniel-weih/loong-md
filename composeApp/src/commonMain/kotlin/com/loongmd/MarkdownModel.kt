package com.loongmd

import androidx.compose.runtime.Composable

data class MarkdownFile(
    val id: String,
    val name: String,
    val path: String,
    val relativePath: String
)

interface MarkdownDataSource {
    val rootDescription: String
    val canSelectRoot: Boolean

    suspend fun listMarkdownFiles(): List<MarkdownFile>
    suspend fun readMarkdown(file: MarkdownFile): String
    suspend fun writeMarkdown(file: MarkdownFile, content: String)
    suspend fun refreshRoot(): String?
}

@Composable
expect fun rememberMarkdownDataSource(): MarkdownDataSource

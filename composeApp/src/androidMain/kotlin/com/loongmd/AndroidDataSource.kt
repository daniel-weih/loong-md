package com.loongmd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private val demoDocs = mutableMapOf(
    "welcome.md" to """
        # 欢迎使用 LoongMD
        这是 Android 端的演示文档，支持 **粗体**、*斜体*、~~删除线~~ 和 [链接](https://kotlinlang.org)。

        ## 功能列表
        - 显示标题
        - 显示段落
        - 显示列表
        - 支持任务列表
          - [x] 已完成项
          - [ ] 未完成项

        ```kotlin
        fun main() {
            println("Hello Markdown")
        }
        ```

        > 这是一个引用块，用于展示提示信息。

        ---

        1. 有序列表第一项
        2. 有序列表第二项
    """.trimIndent(),
    "tips.md" to """
        # 使用建议

        将 Desktop 版本指向你的文档目录，即可批量查看大量 `.md` 文件。
    """.trimIndent()
)

private class AndroidMarkdownDataSource : MarkdownDataSource {
    override val rootDescription: String
        get() = "Android 演示数据"
    override val canSelectRoot: Boolean = false
    override val supportsTreeContextActions: Boolean = false

    override suspend fun listMarkdownFiles(): List<MarkdownFile> {
        return demoDocs.keys.sorted().map {
            MarkdownFile(
                id = it,
                name = it,
                path = it,
                relativePath = it
            )
        }
    }

    override suspend fun readMarkdown(file: MarkdownFile): String {
        return demoDocs[file.path].orEmpty()
    }

    override suspend fun writeMarkdown(file: MarkdownFile, content: String) {
        if (file.path !in demoDocs) {
            error("未找到文件: ${file.path}")
        }
        demoDocs[file.path] = content
    }

    override suspend fun refreshRoot(): String? = null

    override suspend fun revealInFinder(target: MarkdownTreeTarget) {
        error("Android 端不支持在 Finder 显示")
    }

    override suspend fun moveToTrash(target: MarkdownTreeTarget) {
        error("Android 端不支持移到废纸篓")
    }
}

@Composable
actual fun rememberMarkdownDataSource(): MarkdownDataSource {
    return remember { AndroidMarkdownDataSource() }
}

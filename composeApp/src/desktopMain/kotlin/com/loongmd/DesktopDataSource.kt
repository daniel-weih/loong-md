package com.loongmd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser

private class DesktopMarkdownDataSource : MarkdownDataSource {
    private var rootDir: File = loadRoot()

    override val rootDescription: String
        get() = "目录: ${rootDir.absolutePath}"
    override val canSelectRoot: Boolean = true
    override val supportsTreeContextActions: Boolean = true

    override suspend fun listMarkdownFiles(): List<MarkdownFile> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()

        return rootDir
            .walkTopDown()
            .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            .map {
                val relativePath = it.relativeTo(rootDir).path.replace(File.separatorChar, '/')
                MarkdownFile(
                    id = it.absolutePath,
                    name = it.name,
                    path = it.absolutePath,
                    relativePath = relativePath
                )
            }
            .toList()
            .sortedBy { it.relativePath.lowercase() }
    }

    override suspend fun readMarkdown(file: MarkdownFile): String {
        return File(file.path).readText()
    }

    override suspend fun writeMarkdown(file: MarkdownFile, content: String) {
        File(file.path).writeText(content)
    }

    override suspend fun refreshRoot(): String? {
        val chooser = JFileChooser(rootDir)
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = "选择 Markdown 根目录"
        chooser.isFileHidingEnabled = false

        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val selected = chooser.selectedFile
            if (selected != null && selected.isDirectory) {
                rootDir = selected
                saveRoot(selected)
                return rootDir.absolutePath
            }
        }
        return null
    }

    override suspend fun revealInFinder(target: MarkdownTreeTarget) {
        val targetFile = resolveTargetFile(target)
        if (!targetFile.exists()) {
            error("目标不存在: ${targetFile.absolutePath}")
        }

        val isMac = System.getProperty("os.name")
            ?.contains("mac", ignoreCase = true)
            ?: false
        if (isMac) {
            val result = ProcessBuilder("open", "-R", targetFile.absolutePath)
                .start()
                .waitFor()
            if (result != 0) {
                error("无法在 Finder 中显示: ${targetFile.absolutePath}")
            }
            return
        }

        if (!Desktop.isDesktopSupported()) {
            error("系统不支持桌面文件操作")
        }
        val toOpen = if (targetFile.isDirectory) targetFile else targetFile.parentFile ?: targetFile
        Desktop.getDesktop().open(toOpen)
    }

    override suspend fun moveToTrash(target: MarkdownTreeTarget) {
        val targetFile = resolveTargetFile(target)
        if (!targetFile.exists()) {
            error("目标不存在: ${targetFile.absolutePath}")
        }
        if (!Desktop.isDesktopSupported()) {
            error("系统不支持移到废纸篓")
        }
        if (!Desktop.getDesktop().moveToTrash(targetFile)) {
            error("移到废纸篓失败: ${targetFile.absolutePath}")
        }
    }

    private fun resolveTargetFile(target: MarkdownTreeTarget): File {
        return when (target) {
            is MarkdownTreeTarget.FileTarget -> File(target.file.path)
            is MarkdownTreeTarget.DirectoryTarget -> {
                if (target.relativePath.isBlank()) {
                    rootDir
                } else {
                    File(rootDir, target.relativePath.replace('/', File.separatorChar))
                }
            }
        }
    }

    private fun loadRoot(): File {
        val storedPath = preferences.get(LAST_ROOT_KEY, null)
        if (!storedPath.isNullOrBlank()) {
            val storedDir = File(storedPath)
            if (storedDir.exists() && storedDir.isDirectory) {
                return storedDir
            }
        }
        return defaultRoot()
    }

    private fun saveRoot(dir: File) {
        preferences.put(LAST_ROOT_KEY, dir.absolutePath)
    }

    private fun defaultRoot(): File {
        val home = System.getProperty("user.home") ?: "."
        val docs = File(home, "Documents")
        return if (docs.exists() && docs.isDirectory) docs else File(home)
    }

    companion object {
        private const val LAST_ROOT_KEY = "lastRootDir"
        private val preferences: Preferences =
            Preferences.userNodeForPackage(DesktopMarkdownDataSource::class.java)
    }
}

@Composable
actual fun rememberMarkdownDataSource(): MarkdownDataSource {
    return remember { DesktopMarkdownDataSource() }
}

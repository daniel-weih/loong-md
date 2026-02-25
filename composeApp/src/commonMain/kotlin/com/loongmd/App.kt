package com.loongmd

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun App() {
    val dataSource = rememberMarkdownDataSource()
    val scope = rememberCoroutineScope()
    val files = remember { mutableStateListOf<MarkdownFile>() }

    var selectedFile by remember { mutableStateOf<MarkdownFile?>(null) }
    var markdownText by remember { mutableStateOf("") }
    var editingText by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun loadFiles() {
        scope.launch {
            loading = true
            errorMessage = null
            val selectedId = selectedFile?.id
            runCatching { dataSource.listMarkdownFiles() }
                .onSuccess { latestFiles ->
                    files.clear()
                    files.addAll(latestFiles)
                    selectedFile = selectedId
                        ?.let { currentId -> latestFiles.firstOrNull { it.id == currentId } }
                        ?: latestFiles.firstOrNull()
                }
                .onFailure {
                    errorMessage = it.message ?: "加载文件失败"
                }
            loading = false
        }
    }

    fun loadContent(file: MarkdownFile?) {
        if (file == null) {
            markdownText = ""
            editingText = ""
            hasUnsavedChanges = false
            return
        }
        scope.launch {
            loading = true
            errorMessage = null
            runCatching { dataSource.readMarkdown(file) }
                .onSuccess {
                    markdownText = it
                    editingText = it
                    hasUnsavedChanges = false
                }
                .onFailure { errorMessage = it.message ?: "读取内容失败" }
            loading = false
        }
    }

    fun saveContent() {
        val currentFile = selectedFile ?: return
        scope.launch {
            saving = true
            errorMessage = null
            runCatching { dataSource.writeMarkdown(currentFile, editingText) }
                .onSuccess {
                    markdownText = editingText
                    hasUnsavedChanges = false
                }
                .onFailure {
                    errorMessage = it.message ?: "保存失败"
                }
            saving = false
        }
    }

    LaunchedEffect(Unit) {
        loadFiles()
    }

    LaunchedEffect(selectedFile?.id) {
        loadContent(selectedFile)
    }

    MaterialTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                FilePane(
                    files = files,
                    selectedFile = selectedFile,
                    rootDescription = dataSource.rootDescription,
                    canSelectRoot = dataSource.canSelectRoot,
                    supportsTreeContextActions = dataSource.supportsTreeContextActions,
                    loading = loading,
                    errorMessage = errorMessage,
                    onRefresh = { loadFiles() },
                    onSelectRoot = {
                        scope.launch {
                            dataSource.refreshRoot()
                            loadFiles()
                        }
                    },
                    onSelectFile = {
                        if (hasUnsavedChanges && selectedFile?.id != it.id) {
                            errorMessage = "当前文件有未保存修改，请先保存"
                        } else {
                            selectedFile = it
                            isEditing = false
                        }
                    },
                    onRevealInFinder = { target ->
                        scope.launch {
                            runCatching { dataSource.revealInFinder(target) }
                                .onFailure {
                                    errorMessage = it.message ?: "在 Finder 显示失败"
                                }
                        }
                    },
                    onMoveToTrash = { target ->
                        scope.launch {
                            runCatching { dataSource.moveToTrash(target) }
                                .onSuccess {
                                    loadFiles()
                                }
                                .onFailure {
                                    errorMessage = it.message ?: "移到废纸篓失败"
                                }
                        }
                    }
                )
                MarkdownPane(
                    markdownText = markdownText,
                    editingText = editingText,
                    markdownFilePath = selectedFile?.path,
                    isEditing = isEditing,
                    hasUnsavedChanges = hasUnsavedChanges,
                    saving = saving,
                    onSwitchMode = { isEditing = it },
                    onEditingTextChange = {
                        editingText = it
                        hasUnsavedChanges = it != markdownText
                    },
                    onSave = { saveContent() },
                    canSave = selectedFile != null
                )
            }
        }
    }
}

@Composable
private fun FilePane(
    files: List<MarkdownFile>,
    selectedFile: MarkdownFile?,
    rootDescription: String,
    canSelectRoot: Boolean,
    supportsTreeContextActions: Boolean,
    loading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onSelectRoot: () -> Unit,
    onSelectFile: (MarkdownFile) -> Unit,
    onRevealInFinder: (MarkdownTreeTarget) -> Unit,
    onMoveToTrash: (MarkdownTreeTarget) -> Unit
) {
    val tree = buildFileTree(files)
    val expandedDirectoryIds = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()
    val listFocusRequester = remember { FocusRequester() }
    val paneScope = rememberCoroutineScope()
    var keyboardSelectedItemId by remember { mutableStateOf<String?>(null) }
    var contextMenuItemId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tree) {
        val directoryIds = collectDirectoryIds(tree).toSet()

        expandedDirectoryIds.keys.toList().forEach { cachedId ->
            if (cachedId !in directoryIds) {
                expandedDirectoryIds.remove(cachedId)
            }
        }

        directoryIds.forEach { directoryId ->
            if (directoryId !in expandedDirectoryIds) {
                expandedDirectoryIds[directoryId] = true
            }
        }
    }

    val visibleItems = flattenTree(tree, expandedDirectoryIds)
    val customMdIcon = rememberCustomMarkdownFileIcon()
    val selectedFileItemId = selectedFile?.let { "file:${it.id}" }

    LaunchedEffect(visibleItems, selectedFileItemId) {
        if (visibleItems.isEmpty()) {
            keyboardSelectedItemId = null
            return@LaunchedEffect
        }
        val visibleIds = visibleItems.map { it.id }.toSet()
        if (contextMenuItemId != null && contextMenuItemId !in visibleIds) {
            contextMenuItemId = null
        }
        keyboardSelectedItemId = when {
            keyboardSelectedItemId != null && keyboardSelectedItemId in visibleIds -> keyboardSelectedItemId
            selectedFileItemId != null && selectedFileItemId in visibleIds -> selectedFileItemId
            else -> visibleItems.first().id
        }
    }

    LaunchedEffect(visibleItems.isNotEmpty()) {
        if (visibleItems.isNotEmpty()) {
            delay(50)
            listFocusRequester.requestFocus()
        }
    }

    fun setKeyboardSelection(index: Int) {
        if (visibleItems.isEmpty()) return
        val bounded = index.coerceIn(0, visibleItems.lastIndex)
        val selectedItem = visibleItems[bounded]
        keyboardSelectedItemId = selectedItem.id
        if (selectedItem is TreeListItem.FileItem) {
            onSelectFile(selectedItem.file)
        }
        paneScope.launch {
            listState.animateScrollToItem(bounded)
        }
    }

    fun handleActivate(item: TreeListItem) {
        when (item) {
            is TreeListItem.DirectoryItem -> {
                expandedDirectoryIds[item.id] = !(expandedDirectoryIds[item.id] ?: true)
            }

            is TreeListItem.FileItem -> {
                onSelectFile(item.file)
            }
        }
    }

    fun toTreeTarget(item: TreeListItem): MarkdownTreeTarget {
        return when (item) {
            is TreeListItem.DirectoryItem -> MarkdownTreeTarget.DirectoryTarget(
                relativePath = item.id.removePrefix("dir:")
            )

            is TreeListItem.FileItem -> MarkdownTreeTarget.FileTarget(item.file)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Markdown 文件", style = MaterialTheme.typography.titleLarge)
        Text(rootDescription, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canSelectRoot) {
                Button(onClick = onSelectRoot) {
                    Text("选择目录")
                }
            }
            TextButton(onClick = onRefresh) {
                Text("刷新")
            }
        }

        if (loading) {
            Text("加载中...")
        }

        if (errorMessage != null) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(listFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || visibleItems.isEmpty()) {
                        return@onPreviewKeyEvent false
                    }

                    val currentIndex = visibleItems.indexOfFirst { it.id == keyboardSelectedItemId }
                        .let { if (it >= 0) it else 0 }
                    val currentItem = visibleItems[currentIndex]

                    when (event.key) {
                        Key.DirectionUp -> {
                            setKeyboardSelection(currentIndex - 1)
                            true
                        }

                        Key.DirectionDown -> {
                            setKeyboardSelection(currentIndex + 1)
                            true
                        }

                        Key.DirectionRight -> {
                            if (currentItem is TreeListItem.DirectoryItem) {
                                expandedDirectoryIds[currentItem.id] = true
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionLeft -> {
                            if (currentItem is TreeListItem.DirectoryItem) {
                                expandedDirectoryIds[currentItem.id] = false
                                true
                            } else {
                                false
                            }
                        }

                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            handleActivate(currentItem)
                            contextMenuItemId = null
                            true
                        }

                        else -> false
                    }
                },
            state = listState,
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(visibleItems, key = { it.id }) { item ->
                when (item) {
                    is TreeListItem.DirectoryItem -> {
                        val keyboardSelected = item.id == keyboardSelectedItemId
                        val background = if (keyboardSelected) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        } else {
                            Color.Transparent
                        }
                        val target = toTreeTarget(item)

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(background, RoundedCornerShape(8.dp))
                                    .clickable {
                                        keyboardSelectedItemId = item.id
                                        contextMenuItemId = null
                                        listFocusRequester.requestFocus()
                                        expandedDirectoryIds[item.id] = !(expandedDirectoryIds[item.id] ?: true)
                                    }
                                    .pointerInput(item.id, supportsTreeContextActions) {
                                        if (!supportsTreeContextActions) return@pointerInput
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (
                                                    event.type == PointerEventType.Press &&
                                                    event.buttons.isSecondaryPressed
                                                ) {
                                                    keyboardSelectedItemId = item.id
                                                    contextMenuItemId = item.id
                                                    listFocusRequester.requestFocus()
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Spacer(modifier = Modifier.width((item.depth * 16).dp))
                                Text(
                                    text = "📁",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (item.expanded) "▾" else "▸",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                EllipsizedNameText(
                                    text = item.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (supportsTreeContextActions) {
                                DropdownMenu(
                                    expanded = contextMenuItemId == item.id,
                                    onDismissRequest = { contextMenuItemId = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("在Finder显示") },
                                        onClick = {
                                            contextMenuItemId = null
                                            onRevealInFinder(target)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("移到废纸篓") },
                                        onClick = {
                                            contextMenuItemId = null
                                            onMoveToTrash(target)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is TreeListItem.FileItem -> {
                        val selected = item.file.id == selectedFile?.id
                        val keyboardSelected = item.id == keyboardSelectedItemId
                        val background = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else if (keyboardSelected) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        } else {
                            Color.Transparent
                        }
                        val target = toTreeTarget(item)

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(background, RoundedCornerShape(8.dp))
                                    .clickable {
                                        keyboardSelectedItemId = item.id
                                        contextMenuItemId = null
                                        listFocusRequester.requestFocus()
                                        onSelectFile(item.file)
                                    }
                                    .pointerInput(item.id, supportsTreeContextActions) {
                                        if (!supportsTreeContextActions) return@pointerInput
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (
                                                    event.type == PointerEventType.Press &&
                                                    event.buttons.isSecondaryPressed
                                                ) {
                                                    keyboardSelectedItemId = item.id
                                                    contextMenuItemId = item.id
                                                    listFocusRequester.requestFocus()
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Spacer(modifier = Modifier.width((item.depth * 16).dp))
                                val isMarkdown = item.file.name.endsWith(".md", ignoreCase = true)
                                if (isMarkdown && customMdIcon != null) {
                                    Image(
                                        bitmap = customMdIcon,
                                        contentDescription = "Markdown file",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text(
                                        text = if (isMarkdown) "📝" else "📄",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                EllipsizedNameText(
                                    text = item.file.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            if (supportsTreeContextActions) {
                                DropdownMenu(
                                    expanded = contextMenuItemId == item.id,
                                    onDismissRequest = { contextMenuItemId = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("在Finder显示") },
                                        onClick = {
                                            contextMenuItemId = null
                                            onRevealInFinder(target)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("移到废纸篓") },
                                        onClick = {
                                            contextMenuItemId = null
                                            onMoveToTrash(target)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EllipsizedNameText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    fontWeight: FontWeight? = null
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(500)
            tooltipState.show()
        } else {
            tooltipState.dismiss()
        }
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = tooltipState,
        focusable = false,
        enableUserInput = false
    ) {
        Text(
            text = text,
            modifier = modifier.hoverable(interactionSource = interactionSource),
            style = style,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class TreeDirectory(
    val id: String,
    val name: String,
    val directories: List<TreeDirectory>,
    val files: List<MarkdownFile>
)

private sealed class TreeListItem {
    abstract val id: String

    data class DirectoryItem(
        override val id: String,
        val name: String,
        val depth: Int,
        val expanded: Boolean
    ) : TreeListItem()

    data class FileItem(
        override val id: String,
        val depth: Int,
        val file: MarkdownFile
    ) : TreeListItem()
}

private data class MutableDirectoryNode(
    val id: String,
    val name: String,
    val children: MutableMap<String, MutableDirectoryNode> = mutableMapOf(),
    val files: MutableList<MarkdownFile> = mutableListOf()
)

private val markdownTreeFileComparator = compareByDescending<MarkdownFile> { it.lastModified }
    .thenBy { it.name.lowercase() }

private fun buildFileTree(files: List<MarkdownFile>): List<TreeNode> {
    val root = MutableDirectoryNode(id = "__root__", name = "")

    files.forEach { file ->
        val parts = file.relativePath
            .split('/', '\\')
            .filter { it.isNotBlank() }

        if (parts.isEmpty()) {
            root.files.add(file)
            return@forEach
        }

        var current = root
        val dirParts = parts.dropLast(1)
        var currentPath = ""

        dirParts.forEach { part ->
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
            val child = current.children.getOrPut(currentPath) {
                MutableDirectoryNode(id = "dir:$currentPath", name = part)
            }
            current = child
        }

        current.files.add(file)
    }

    return buildDirectories(root).map { TreeNode.Directory(it) } + root.files
        .sortedWith(markdownTreeFileComparator)
        .map { TreeNode.File(it) }
}

private fun buildDirectories(root: MutableDirectoryNode): List<TreeDirectory> {
    return root.children.values
        .sortedBy { it.name.lowercase() }
        .map {
            TreeDirectory(
                id = it.id,
                name = it.name,
                directories = buildDirectories(it),
                files = it.files.sortedWith(markdownTreeFileComparator)
            )
        }
}

private fun collectDirectoryIds(nodes: List<TreeNode>): List<String> {
    val ids = mutableListOf<String>()
    nodes.forEach { node ->
        if (node is TreeNode.Directory) {
            ids.add(node.directory.id)
            ids.addAll(collectDirectoryIds(node.directory.directories.map { TreeNode.Directory(it) }))
        }
    }
    return ids
}

private fun flattenTree(
    nodes: List<TreeNode>,
    expandedDirectoryIds: Map<String, Boolean>,
    depth: Int = 0
): List<TreeListItem> {
    val result = mutableListOf<TreeListItem>()

    nodes.forEach { node ->
        when (node) {
            is TreeNode.Directory -> {
                val expanded = expandedDirectoryIds[node.directory.id] ?: true
                result.add(
                    TreeListItem.DirectoryItem(
                        id = node.directory.id,
                        name = node.directory.name,
                        depth = depth,
                        expanded = expanded
                    )
                )
                if (expanded) {
                    result.addAll(
                        flattenTree(
                            node.directory.directories.map { TreeNode.Directory(it) },
                            expandedDirectoryIds,
                            depth + 1
                        )
                    )
                    node.directory.files.forEach { file ->
                        result.add(
                            TreeListItem.FileItem(
                                id = "file:${file.id}",
                                depth = depth + 1,
                                file = file
                            )
                        )
                    }
                }
            }

            is TreeNode.File -> {
                result.add(
                    TreeListItem.FileItem(
                        id = "file:${node.file.id}",
                        depth = depth,
                        file = node.file
                    )
                )
            }
        }
    }

    return result
}

private sealed class TreeNode {
    data class Directory(val directory: TreeDirectory) : TreeNode()
    data class File(val file: MarkdownFile) : TreeNode()
}

private const val MarkdownLinkTag = "markdown_link"
private data class LinkBounds(val start: Int, val end: Int)
private data class ImageReference(
    val alt: String,
    val source: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null
)
private sealed interface MarkdownImageState {
    data object Loading : MarkdownImageState
    data class Success(val bitmap: ImageBitmap) : MarkdownImageState
    data object Error : MarkdownImageState
}
private val htmlImgInCellRegex = Regex(
    """(?is)^<img\b[^>]*\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))[^>]*>$"""
)
private val htmlImgAltInCellRegex = Regex(
    """(?is)\balt\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))"""
)
private val htmlImgWidthInCellRegex = Regex(
    """(?is)\bwidth\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))"""
)
private val htmlImgHeightInCellRegex = Regex(
    """(?is)\bheight\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))"""
)

@Composable
private fun MarkdownPane(
    markdownText: String,
    editingText: String,
    markdownFilePath: String?,
    isEditing: Boolean,
    hasUnsavedChanges: Boolean,
    saving: Boolean,
    onSwitchMode: (Boolean) -> Unit,
    onEditingTextChange: (String) -> Unit,
    onSave: () -> Unit,
    canSave: Boolean
) {
    val previewText = if (hasUnsavedChanges) editingText else markdownText
    val blocks = remember(previewText) { parseMarkdown(previewText) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { onSwitchMode(false) },
                enabled = isEditing
            ) {
                Text("预览")
            }
            TextButton(
                onClick = { onSwitchMode(true) },
                enabled = !isEditing
            ) {
                Text("编辑")
            }
            Button(
                onClick = onSave,
                enabled = canSave && hasUnsavedChanges && !saving && isEditing
            ) {
                Text(if (saving) "保存中..." else "保存")
            }
            if (hasUnsavedChanges) {
                Text(
                    text = "有未保存修改",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (isEditing) {
            OutlinedTextField(
                value = editingText,
                onValueChange = onEditingTextChange,
                modifier = Modifier.fillMaxSize(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 26.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
            return
        }

        if (previewText.isBlank()) {
            Text("请选择一个 .md 文件", style = MaterialTheme.typography.bodyLarge)
            return
        }

        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                blocks.forEach { block ->
                    MarkdownBlock(
                        block = block,
                        markdownFilePath = markdownFilePath
                    )
                    Spacer(Modifier.height(blockSpacing(block)))
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlock(
    block: MdBlock,
    markdownFilePath: String?
) {
    when (block) {
        is MdBlock.Heading -> {
            MarkdownRichText(
                text = block.text,
                style = headingStyle(block.level)
            )
        }

        is MdBlock.Paragraph -> {
            MarkdownRichText(
                text = block.text,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp)
            )
        }

        is MdBlock.Image -> {
            MarkdownImageBlock(
                alt = block.alt,
                source = block.source,
                widthPx = block.widthPx,
                heightPx = block.heightPx,
                markdownFilePath = markdownFilePath
            )
        }

        is MdBlock.TableRow -> {
            MarkdownTableRow(
                cells = block.cells,
                markdownFilePath = markdownFilePath
            )
        }

        is MdBlock.UnorderedList -> MarkdownList(
            items = block.items,
            ordered = false
        )

        is MdBlock.OrderedList -> MarkdownList(
            items = block.items,
            ordered = true
        )

        is MdBlock.Quote -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "▌",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                MarkdownRichText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 26.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is MdBlock.CodeFence -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF1F2933),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                if (!block.language.isNullOrBlank()) {
                    Text(
                        text = block.language.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8AB4F8),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Text(
                    text = block.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = Color(0xFFE6EDF3)
                )
            }
        }

        MdBlock.HorizontalRule -> {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun MarkdownTableRow(
    cells: List<String>,
    markdownFilePath: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        cells.forEach { cell ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                val image = remember(cell) { parseImageReference(cell) }
                if (image != null) {
                    MarkdownImageBlock(
                        alt = image.alt,
                        source = image.source,
                        widthPx = image.widthPx,
                        heightPx = image.heightPx,
                        markdownFilePath = markdownFilePath
                    )
                } else {
                    MarkdownRichText(
                        text = cell,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownImageBlock(
    alt: String,
    source: String,
    widthPx: Int?,
    heightPx: Int?,
    markdownFilePath: String?
) {
    val state by produceState<MarkdownImageState>(
        initialValue = MarkdownImageState.Loading,
        key1 = source,
        key2 = markdownFilePath
    ) {
        val bitmap = withContext(Dispatchers.Default) {
            loadMarkdownImageBitmap(source, markdownFilePath)
        }
        value = if (bitmap != null) {
            MarkdownImageState.Success(bitmap)
        } else {
            MarkdownImageState.Error
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (val imageState = state) {
            MarkdownImageState.Loading -> {
                Text(
                    text = "图片加载中: ${alt.ifBlank { source }}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            MarkdownImageState.Error -> {
                Text(
                    text = "无法加载图片: ${alt.ifBlank { source }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            is MarkdownImageState.Success -> {
                val ratio = imageState.bitmap.width.toFloat() /
                    imageState.bitmap.height.coerceAtLeast(1).toFloat()
                Image(
                    bitmap = imageState.bitmap,
                    contentDescription = alt.ifBlank { null },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .then(
                            imageSizeModifier(
                                widthPx = widthPx,
                                heightPx = heightPx,
                                ratio = ratio
                            )
                        )
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(6.dp)
                )
            }
        }

        if (alt.isNotBlank()) {
            Text(
                text = alt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun parseImageReference(text: String): ImageReference? {
    val trimmed = text.trim()
    if (trimmed.isBlank()) return null

    parseMarkdownImageReference(trimmed)?.let { return it }
    parseHtmlImageReference(trimmed)?.let { return it }
    return null
}

private fun parseMarkdownImageReference(text: String): ImageReference? {
    if (!text.startsWith("![")) return null
    val altEnd = text.indexOf(']')
    if (altEnd < 2 || altEnd + 1 >= text.length || text[altEnd + 1] != '(') return null
    val sourceEnd = text.lastIndexOf(')')
    if (sourceEnd <= altEnd + 2 || sourceEnd != text.lastIndex) return null

    val alt = text.substring(2, altEnd).trim()
    val raw = text.substring(altEnd + 2, sourceEnd).trim()
    if (raw.isBlank()) return null
    val source = raw.takeWhile { !it.isWhitespace() }.removeSurrounding("<", ">").trim()
    if (source.isBlank()) return null

    return ImageReference(
        alt = alt,
        source = source
    )
}

private fun parseHtmlImageReference(text: String): ImageReference? {
    val match = htmlImgInCellRegex.matchEntire(text) ?: return null
    val source = (match.groupValues[1] + match.groupValues[2] + match.groupValues[3]).trim()
    if (source.isBlank()) return null

    val altMatch = htmlImgAltInCellRegex.find(text)
    val alt = if (altMatch != null) {
        (altMatch.groupValues[1] + altMatch.groupValues[2] + altMatch.groupValues[3]).trim()
    } else {
        ""
    }

    val widthPx = parseHtmlSizePx(htmlImgWidthInCellRegex.find(text)?.extractHtmlAttrValue())
    val heightPx = parseHtmlSizePx(htmlImgHeightInCellRegex.find(text)?.extractHtmlAttrValue())

    return ImageReference(
        alt = alt,
        source = source,
        widthPx = widthPx,
        heightPx = heightPx
    )
}

private fun imageSizeModifier(
    widthPx: Int?,
    heightPx: Int?,
    ratio: Float
): Modifier {
    val normalizedRatio = ratio.coerceAtLeast(0.1f)

    return when {
        widthPx != null && heightPx != null -> {
            Modifier
                .width(widthPx.dp)
                .height(heightPx.dp)
        }

        widthPx != null -> {
            Modifier
                .width(widthPx.dp)
                .aspectRatio(normalizedRatio)
        }

        heightPx != null -> {
            Modifier
                .height(heightPx.dp)
                .aspectRatio(normalizedRatio, matchHeightConstraintsFirst = true)
        }

        else -> {
            Modifier
                .fillMaxWidth()
                .aspectRatio(normalizedRatio)
        }
    }
}

private fun MatchResult.extractHtmlAttrValue(): String {
    return (groupValues[1] + groupValues[2] + groupValues[3]).trim()
}

private fun parseHtmlSizePx(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    val normalized = raw.trim().lowercase()
    if (normalized.endsWith("%")) return null

    val numeric = if (normalized.endsWith("px")) {
        normalized.removeSuffix("px").trim()
    } else {
        normalized
    }

    val value = numeric.toFloatOrNull() ?: return null
    if (value <= 0f) return null
    return value.toInt()
}

@Composable
private fun MarkdownList(
    items: List<MdListItem>,
    ordered: Boolean
) {
    val orderedCounters = mutableMapOf<Int, Int>()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (item.indent * 18).dp)
            ) {
                val orderedPrefix = if (ordered) {
                    orderedCounters.keys
                        .filter { it > item.indent }
                        .toList()
                        .forEach { orderedCounters.remove(it) }
                    val next = (orderedCounters[item.indent] ?: 0) + 1
                    orderedCounters[item.indent] = next
                    "$next."
                } else {
                    null
                }

                val prefix = when {
                    item.checked == true -> "☑"
                    item.checked == false -> "☐"
                    orderedPrefix != null -> orderedPrefix
                    else -> "•"
                }

                Text(
                    text = prefix,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(28.dp)
                )

                MarkdownRichText(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle {
    return when (level.coerceIn(1, 6)) {
        1 -> MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            lineHeight = 42.sp
        )

        2 -> MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp
        )

        3 -> MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 32.sp
        )

        4 -> MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 30.sp
        )

        5 -> MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Medium,
            lineHeight = 26.sp
        )

        else -> MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Medium,
            lineHeight = 24.sp
        )
    }
}

private fun blockSpacing(block: MdBlock): Dp {
    return when (block) {
        is MdBlock.Heading -> 14.dp
        is MdBlock.CodeFence -> 16.dp
        MdBlock.HorizontalRule -> 14.dp
        else -> 10.dp
    }
}

@Composable
private fun MarkdownRichText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val inlineSpans = remember(text) { parseInline(text) }
    var hoveredLinkBounds by remember { mutableStateOf<LinkBounds?>(null) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val annotated = remember(inlineSpans, linkColor, hoveredLinkBounds) {
        buildMarkdownAnnotatedString(
            spans = inlineSpans,
            linkColor = linkColor,
            hoveredLinkBounds = hoveredLinkBounds
        )
    }

    BasicText(
        text = annotated,
        style = style,
        modifier = modifier
            .then(
                if (hoveredLinkBounds != null) {
                    Modifier.pointerHoverIcon(PointerIcon.Hand)
                } else {
                    Modifier
                }
            )
            .pointerInput(text, textLayoutResult) {
                awaitPointerEventScope {
                    var pressPosition: Offset? = null
                    var movedAfterPress = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointerPosition = event.changes.firstOrNull()?.position

                        when (event.type) {
                            PointerEventType.Press -> {
                                pressPosition = pointerPosition
                                movedAfterPress = false
                            }

                            PointerEventType.Move -> {
                                val down = pressPosition
                                val current = pointerPosition
                                if (down != null && current != null && !movedAfterPress) {
                                    movedAfterPress = (current - down).getDistance() > 2f
                                }
                            }

                            PointerEventType.Release -> {
                                if (!movedAfterPress) {
                                    annotationAtPosition(
                                        position = pointerPosition,
                                        layout = textLayoutResult,
                                        annotated = annotated
                                    )?.let { openMarkdownLink(uriHandler, it.item) }
                                }
                                pressPosition = null
                                movedAfterPress = false
                            }

                            PointerEventType.Exit -> {
                                pressPosition = null
                                movedAfterPress = false
                            }

                            else -> Unit
                        }

                        val nextHoveredLink = if (event.type == PointerEventType.Exit) {
                            null
                        } else {
                            linkBoundsAtPosition(
                                position = pointerPosition,
                                layout = textLayoutResult,
                                annotated = annotated
                            )
                        }

                        if (nextHoveredLink != hoveredLinkBounds) {
                            hoveredLinkBounds = nextHoveredLink
                        }
                    }
                }
            },
        onTextLayout = { textLayoutResult = it }
    )
}

private fun linkBoundsAtPosition(
    position: Offset?,
    layout: TextLayoutResult?,
    annotated: AnnotatedString
): LinkBounds? {
    val annotation = annotationAtPosition(position, layout, annotated) ?: return null
    return LinkBounds(start = annotation.start, end = annotation.end)
}

private fun annotationAtPosition(
    position: Offset?,
    layout: TextLayoutResult?,
    annotated: AnnotatedString
): AnnotatedString.Range<String>? {
    if (position == null || layout == null || annotated.text.isEmpty()) return null

    val width = layout.size.width.toFloat()
    val height = layout.size.height.toFloat()
    if (
        position.x < 0f ||
        position.y < 0f ||
        position.x > width ||
        position.y > height
    ) {
        return null
    }

    val offset = layout.getOffsetForPosition(position)
    return annotated.getStringAnnotations(MarkdownLinkTag, offset, offset).firstOrNull()
}

private fun openMarkdownLink(uriHandler: UriHandler, rawUrl: String) {
    val normalized = rawUrl.trim()
    if (normalized.isEmpty()) return

    val hasScheme =
        "://" in normalized ||
            normalized.startsWith("mailto:", ignoreCase = true) ||
            normalized.startsWith("tel:", ignoreCase = true)
    val candidates = if (hasScheme) {
        listOf(normalized)
    } else {
        listOf(normalized, "https://$normalized")
    }

    candidates.firstOrNull { candidate ->
        runCatching { uriHandler.openUri(candidate) }.isSuccess
    }
}

private fun buildMarkdownAnnotatedString(
    spans: List<MdInlineSpan>,
    linkColor: Color,
    hoveredLinkBounds: LinkBounds?
): AnnotatedString {
    return buildAnnotatedString {
        spans.forEach { span ->
            if (span.text.isEmpty()) return@forEach
            val start = length
            append(span.text)
            val end = length

            val hoverOnLink =
                span.style.link != null &&
                    hoveredLinkBounds != null &&
                    start < hoveredLinkBounds.end &&
                    end > hoveredLinkBounds.start

            val style = inlineSpanStyle(
                style = span.style,
                linkColor = linkColor,
                underlineLink = hoverOnLink
            )
            if (style != null) {
                addStyle(style, start, end)
            }

            span.style.link?.let { url ->
                addStringAnnotation(
                    tag = MarkdownLinkTag,
                    annotation = url,
                    start = start,
                    end = end
                )
            }
        }
    }
}

private fun inlineSpanStyle(
    style: MdInlineStyle,
    linkColor: Color,
    underlineLink: Boolean
): SpanStyle? {
    val decorations = mutableListOf<TextDecoration>()
    if (style.strikethrough) decorations += TextDecoration.LineThrough
    if (style.link != null && underlineLink) decorations += TextDecoration.Underline

    val spanStyle = SpanStyle(
        color = when {
            style.code -> Color(0xFF7F1D1D)
            style.link != null -> linkColor
            else -> Color.Unspecified
        },
        fontWeight = if (style.bold) FontWeight.Bold else null,
        fontStyle = if (style.italic) FontStyle.Italic else null,
        fontFamily = if (style.code) FontFamily.Monospace else null,
        background = if (style.code) Color(0xFFEFF1F4) else Color.Unspecified,
        textDecoration = when (decorations.size) {
            0 -> null
            1 -> decorations[0]
            else -> TextDecoration.combine(decorations)
        }
    )

    return if (
        spanStyle.color == Color.Unspecified &&
        spanStyle.fontWeight == null &&
        spanStyle.fontStyle == null &&
        spanStyle.fontFamily == null &&
        spanStyle.background == Color.Unspecified &&
        spanStyle.textDecoration == null
    ) {
        null
    } else {
        spanStyle
    }
}

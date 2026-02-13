package com.loongmd

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
            runCatching { dataSource.listMarkdownFiles() }
                .onSuccess {
                    files.clear()
                    files.addAll(it)
                    if (selectedFile == null || selectedFile !in it) {
                        selectedFile = it.firstOrNull()
                    }
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
                    }
                )
                MarkdownPane(
                    markdownText = markdownText,
                    editingText = editingText,
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
    loading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onSelectRoot: () -> Unit,
    onSelectFile: (MarkdownFile) -> Unit
) {
    val tree = buildFileTree(files)
    val expandedDirectoryIds = remember { mutableStateMapOf<String, Boolean>() }

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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(visibleItems, key = { it.id }) { item ->
                when (item) {
                    is TreeListItem.DirectoryItem -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedDirectoryIds[item.id] = !(expandedDirectoryIds[item.id] ?: true)
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
                    }

                    is TreeListItem.FileItem -> {
                        val selected = item.file.id == selectedFile?.id
                        val background = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(background, RoundedCornerShape(8.dp))
                                .clickable { onSelectFile(item.file) }
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Spacer(modifier = Modifier.width((item.depth * 16).dp))
                            val icon = if (item.file.name.endsWith(".md", ignoreCase = true)) "📝" else "📄"
                            Text(
                                text = icon,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            EllipsizedNameText(
                                text = item.file.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
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
        .sortedBy { it.name.lowercase() }
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
                files = it.files.sortedBy { file -> file.name.lowercase() }
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

@Composable
private fun MarkdownPane(
    markdownText: String,
    editingText: String,
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            blocks.forEach { block ->
                MarkdownBlock(block)
                Spacer(Modifier.height(blockSpacing(block)))
            }
        }
    }
}

@Composable
private fun MarkdownBlock(block: MdBlock) {
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

    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier
            .pointerHoverIcon(
                if (hoveredLinkBounds != null) PointerIcon.Hand else PointerIcon.Default
            )
            .pointerInput(textLayoutResult) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val nextHoveredLink = when {
                            event.type == PointerEventType.Exit -> null
                            textLayoutResult == null || annotated.text.isEmpty() -> null
                            else -> {
                                val pointerPosition = event.changes.firstOrNull()?.position ?: continue
                                val layout = textLayoutResult ?: continue
                                val width = layout.size.width.toFloat()
                                val height = layout.size.height.toFloat()
                                if (
                                    pointerPosition.x < 0f ||
                                    pointerPosition.y < 0f ||
                                    pointerPosition.x > width ||
                                    pointerPosition.y > height
                                ) {
                                    null
                                } else {
                                    val offset = layout.getOffsetForPosition(pointerPosition)
                                    annotated
                                        .getStringAnnotations(MarkdownLinkTag, offset, offset)
                                        .firstOrNull()
                                        ?.let { LinkBounds(start = it.start, end = it.end) }
                                }
                            }
                        }

                        if (nextHoveredLink != hoveredLinkBounds) {
                            hoveredLinkBounds = nextHoveredLink
                        }
                    }
                }
            },
        onTextLayout = { textLayoutResult = it },
        onClick = { offset ->
            annotated
                .getStringAnnotations(MarkdownLinkTag, offset, offset)
                .firstOrNull()
                ?.let { annotation ->
                    openMarkdownLink(uriHandler, annotation.item)
                }
        }
    )
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

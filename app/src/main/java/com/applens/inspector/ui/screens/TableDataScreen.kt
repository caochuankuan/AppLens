package com.applens.inspector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.applens.inspector.data.AppEntry
import com.applens.inspector.data.CellKind
import com.applens.inspector.data.CellValue
import com.applens.inspector.data.DbAccessMode
import com.applens.inspector.data.DbException
import com.applens.inspector.data.DbFile
import com.applens.inspector.data.DbRepository
import com.applens.inspector.data.Shell
import com.applens.inspector.data.TableInfo
import com.applens.inspector.data.TablePage
import com.applens.inspector.ui.AppTopBar
import com.applens.inspector.ui.EmptyState
import com.applens.inspector.ui.InfoBanner
import com.applens.inspector.ui.LoadingState
import com.applens.inspector.ui.LocalSnackbarHost
import com.applens.inspector.ui.MonoText
import com.applens.inspector.ui.Pill
import com.applens.inspector.ui.theme.AppAmber
import com.applens.inspector.ui.theme.AppBlue
import com.applens.inspector.ui.theme.AppMint
import com.applens.inspector.ui.theme.AppMuted
import com.applens.inspector.ui.theme.AppOutline
import kotlinx.coroutines.launch

@Composable
fun TableDataScreen(
    app: AppEntry,
    db: DbFile,
    table: TableInfo,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHost.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val horizontalScroll = rememberScrollState()
    val listState = rememberLazyListState()
    var page by remember(table.name) { mutableStateOf<TablePage?>(null) }
    var loading by remember(table.name) { mutableStateOf(true) }
    var error by remember(table.name) { mutableStateOf<String?>(null) }
    var offset by remember(table.name) { mutableIntStateOf(0) }
    var reloadToken by remember(table.name) { mutableIntStateOf(0) }

    LaunchedEffect(offset, reloadToken, table.name) {
        loading = true
        error = null
        runCatching {
            if (reloadToken > 0) {
                DbRepository.invalidateCache(context, db)
            }
            val mode = Shell.accessMode(app.packageName, app.isDebuggable)
            if (mode == DbAccessMode.DENIED) {
                throw DbException("Shizuku 权限已失效，无法读取数据库。")
            }
            DbRepository.readTablePage(
                context = context,
                app = app,
                db = db,
                mode = mode,
                table = table.name,
                offset = offset,
            )
        }.onSuccess {
            page = it
            loading = false
        }.onFailure {
            error = it.message ?: "读取表数据失败"
            loading = false
        }
    }

    LaunchedEffect(page?.loadedAt) {
        if (!page?.rows.isNullOrEmpty()) {
            listState.scrollToItem(0)
        }
    }

    val current = page
    val widths = remember(current) {
        current?.let { computeColumnWidths(it) } ?: emptyList()
    }
    val tableWidth = widths.fold(48.dp) { acc, width -> acc + width }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = table.name,
            subtitle = "${db.displayName} · 数据",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(table.name))
                        scope.launch { snackbar.showSnackbar("已复制表名") }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "复制表名",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { reloadToken++ },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "刷新数据",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(text = "${table.columns.size} 列", color = AppMint)
            Pill(
                text = if (table.isView) "VIEW" else "TABLE",
                color = AppBlue,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "每页 100 行",
                style = MaterialTheme.typography.labelSmall,
                color = AppMuted,
            )
        }

        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, AppOutline.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            MonoText(
                text = "SELECT * FROM \"${table.name}\" LIMIT 100 OFFSET $offset",
                maxLines = 1,
                color = AppMuted,
            )
        }

        Spacer(Modifier.height(10.dp))
        when {
            loading -> {
                LoadingState(
                    title = "正在读取数据",
                    message = "LIMIT 100 OFFSET $offset",
                    modifier = Modifier.weight(1f),
                )
            }

            error != null -> {
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    ) {
                        InfoBanner(
                            title = "读取失败",
                            message = error ?: "",
                            accent = MaterialTheme.colorScheme.error,
                            icon = Icons.Rounded.ErrorOutline,
                            action = {
                                TextButton(onClick = { reloadToken++ }) {
                                    Text("重试", color = MaterialTheme.colorScheme.error)
                                }
                            },
                        )
                    }
                }
            }

            current == null || current.rows.isEmpty() -> {
                Box(modifier = Modifier.weight(1f)) {
                    EmptyState(
                        icon = Icons.Rounded.TableChart,
                        title = "没有数据",
                        message = if (current == null) {
                            "表结构为空或查询未返回数据。"
                        } else {
                            "当前页没有行。可以返回上一页，或重新扫描数据库。"
                        },
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .horizontalScroll(horizontalScroll),
                ) {
                    Column(
                        modifier = Modifier
                            .width(tableWidth)
                            .fillMaxHeight(),
                    ) {
                        TableHeader(
                            columns = current.columns,
                            types = current.columnTypes,
                            widths = widths,
                        )
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            itemsIndexed(current.rows) { index, row ->
                                TableDataRow(
                                    rowIndex = current.offset + index + 1,
                                    row = row,
                                    widths = widths,
                                    alternate = index % 2 == 1,
                                    onCopy = { cell ->
                                        if (cell.kind != CellKind.NULL && cell.text.isNotBlank()) {
                                            clipboard.setText(AnnotatedString(cell.copyValue))
                                            scope.launch { snackbar.showSnackbar("已复制单元格") }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                PagerBar(
                    page = current,
                    onPrevious = {
                        if (offset >= 100) offset -= 100
                    },
                    onNext = {
                        if (current.hasMore) offset += 100
                    },
                )
            }
        }
    }
}

@Composable
private fun TableHeader(
    columns: List<String>,
    types: List<String>,
    widths: List<Dp>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(48.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "#",
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = AppMuted,
            )
        }
        columns.forEachIndexed { index, name ->
            Column(
                modifier = Modifier
                    .width(widths.getOrElse(index) { 120.dp })
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = types.getOrElse(index) { "" }.ifBlank { "TEXT" },
                    style = MaterialTheme.typography.labelSmall,
                    color = AppMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TableDataRow(
    rowIndex: Int,
    row: List<CellValue>,
    widths: List<Dp>,
    alternate: Boolean,
    onCopy: (CellValue) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(43.dp)
            .background(
                if (alternate) MaterialTheme.colorScheme.surfaceContainerLow
                else MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(48.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = rowIndex.toString(),
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = AppMuted,
            )
        }
        row.forEachIndexed { index, cell ->
            val color = when (cell.kind) {
                CellKind.NULL -> AppMuted
                CellKind.INTEGER, CellKind.REAL -> AppBlue
                CellKind.BLOB -> AppAmber
                CellKind.TEXT -> MaterialTheme.colorScheme.onSurface
            }
            Box(
                modifier = Modifier
                    .width(widths.getOrElse(index) { 120.dp })
                    .fillMaxHeight()
                    .clickable { onCopy(cell) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = cell.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontStyle = if (cell.kind == CellKind.NULL) FontStyle.Italic else FontStyle.Normal,
                    ),
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PagerBar(
    page: TablePage,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (page.rows.isEmpty()) {
                "无数据"
            } else {
                "第 ${page.offset + 1}–${page.offset + page.rows.size} 行"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onPrevious,
            enabled = page.offset > 0,
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Icon(Icons.Rounded.ArrowBack, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("上一页")
        }
        TextButton(
            onClick = onNext,
            enabled = page.hasMore,
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text("下一页")
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ArrowForward, null, Modifier.size(16.dp))
        }
    }
}

private fun computeColumnWidths(page: TablePage): List<Dp> {
    return page.columns.mapIndexed { index, name ->
        val typeLength = page.columnTypes.getOrElse(index) { "" }.length
        val maxValueLength = page.rows.take(40).maxOfOrNull { row ->
            row.getOrNull(index)?.text?.length ?: 0
        } ?: 0
        val length = maxOf(name.length, typeLength, maxValueLength)
        (length.coerceIn(7, 30) * 8 + 30).dp.coerceIn(96.dp, 250.dp)
    }
}

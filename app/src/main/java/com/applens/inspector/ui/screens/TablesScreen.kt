package com.applens.inspector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.ViewList
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.applens.inspector.data.AppEntry
import com.applens.inspector.data.DbAccessMode
import com.applens.inspector.data.DbException
import com.applens.inspector.data.DbFile
import com.applens.inspector.data.DbRepository
import com.applens.inspector.data.DbZone
import com.applens.inspector.data.Shell
import com.applens.inspector.data.TableInfo
import com.applens.inspector.ui.AppTopBar
import com.applens.inspector.ui.EmptyState
import com.applens.inspector.ui.InfoBanner
import com.applens.inspector.ui.LoadingState
import com.applens.inspector.ui.MonoText
import com.applens.inspector.ui.Pill
import com.applens.inspector.ui.SectionHeader
import com.applens.inspector.ui.theme.AppAmber
import com.applens.inspector.ui.theme.AppBlue
import com.applens.inspector.ui.theme.AppMint
import com.applens.inspector.ui.theme.AppMuted
import com.applens.inspector.ui.theme.AppOutline
import com.applens.inspector.ui.theme.AppSurfaceVariant

@Composable
fun TablesScreen(
    app: AppEntry,
    db: DbFile,
    onBack: () -> Unit,
    onOpenTable: (TableInfo) -> Unit,
) {
    val context = LocalContext.current
    var tables by remember(db.absolutePath) { mutableStateOf<List<TableInfo>?>(null) }
    var loading by remember(db.absolutePath) { mutableStateOf(true) }
    var error by remember(db.absolutePath) { mutableStateOf<String?>(null) }
    var reloadToken by remember(db.absolutePath) { mutableIntStateOf(0) }
    var mode by remember(db.absolutePath) { mutableStateOf<DbAccessMode?>(null) }

    LaunchedEffect(reloadToken, db.absolutePath) {
        loading = true
        error = null
        runCatching {
            val access = Shell.accessMode(app.packageName, app.isDebuggable)
            mode = access
            if (access == DbAccessMode.DENIED) {
                throw DbException("Shizuku 权限已失效，或当前为 Shell 模式且该应用不可 run-as。")
            }
            DbRepository.readTables(
                context = context,
                app = app,
                db = db,
                mode = access,
                force = reloadToken > 0,
            )
        }.onSuccess {
            tables = it
            loading = false
        }.onFailure {
            error = it.message ?: "读取表结构失败"
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = db.displayName,
            subtitle = "${app.label} · 表结构",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        reloadToken++
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "刷新数据库",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { DbSummaryCard(app = app, db = db, mode = mode) }

            val current = tables
            when {
                loading -> item {
                    LoadingState(
                        title = "正在打开只读副本",
                        message = "复制数据库与 WAL 旁路文件",
                    )
                }

                error != null -> item {
                    InfoBanner(
                        title = "无法打开数据库",
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

                current.isNullOrEmpty() -> item {
                    EmptyState(
                        icon = Icons.Rounded.Storage,
                        title = "没有可展示的表",
                        message = "这个数据库中只有 SQLite 内部表，或当前副本不包含任何用户表。",
                    )
                }

                else -> {
                    val tableCount = current.count { !it.isView }
                    val viewCount = current.size - tableCount
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "共 ${current.size} 个对象",
                                style = MaterialTheme.typography.labelMedium,
                                color = AppMuted,
                            )
                            Spacer(Modifier.weight(1f))
                            if (tableCount > 0) {
                                Pill(text = "$tableCount 表", color = AppMint)
                            }
                            if (viewCount > 0) {
                                Spacer(Modifier.width(5.dp))
                                Pill(text = "$viewCount 视图", color = AppBlue)
                            }
                        }
                    }
                    if (tableCount > 0) {
                        item {
                            SectionHeader(
                                title = "数据表",
                                trailing = "$tableCount 个",
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(current.filter { !it.isView }, key = { "t-${it.name}" }) { table ->
                            TableRow(table = table, accent = AppMint, onClick = { onOpenTable(table) })
                        }
                    }
                    if (viewCount > 0) {
                        item {
                            SectionHeader(
                                title = "视图",
                                trailing = "$viewCount 个",
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        items(current.filter { it.isView }, key = { "v-${it.name}" }) { table ->
                            TableRow(table = table, accent = AppBlue, onClick = { onOpenTable(table) })
                        }
                    }
                }
            }

            item {
                InfoBanner(
                    title = "表名与列名来自 sqlite_master",
                    message = "AppLens 会过滤 sqlite_ 开头的内部表。虚拟表、FTS 索引表会照常展示。",
                    accent = AppMuted,
                    icon = Icons.Rounded.TableChart,
                )
            }
        }
    }
}

@Composable
private fun DbSummaryCard(app: AppEntry, db: DbFile, mode: DbAccessMode?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, AppOutline.copy(alpha = 0.6f), MaterialTheme.shapes.large)
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppMint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = AppMint,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = db.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${app.label} · ${db.zone.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppMuted,
                    maxLines = 1,
                )
            }
            Pill(
                text = when (mode) {
                    DbAccessMode.ROOT -> "ROOT"
                    DbAccessMode.RUN_AS -> "RUN-AS"
                    else -> "只读"
                },
                color = if (mode == DbAccessMode.ROOT) AppMint else AppBlue,
            )
        }
        Spacer(Modifier.height(10.dp))
        MonoText(text = db.relativePath, maxLines = 2)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(
                text = db.zone.hint,
                color = AppMuted,
                container = MaterialTheme.colorScheme.surfaceVariant,
            )
            if (db.inDatabasesDir) {
                Pill(text = "databases/", color = AppMint)
            }
            Pill(
                text = "只读副本",
                color = AppAmber,
                container = AppAmber.copy(alpha = 0.11f),
            )
        }
    }
}

@Composable
private fun TableRow(
    table: TableInfo,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, AppOutline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (table.isView) Icons.Rounded.ViewList else Icons.Rounded.TableChart,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = table.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Pill(
                    text = if (table.isView) "VIEW" else "TABLE",
                    color = accent,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = table.columnSummary,
                style = MaterialTheme.typography.bodySmall,
                color = AppMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${table.columns.size} 列",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Rounded.ArrowForward,
            contentDescription = null,
            tint = AppMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

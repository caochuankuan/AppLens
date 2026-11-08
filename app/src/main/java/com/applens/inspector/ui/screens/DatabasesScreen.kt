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
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.applens.inspector.data.AppEntry
import com.applens.inspector.data.DbAccessMode
import com.applens.inspector.data.DbFile
import com.applens.inspector.data.DbListResult
import com.applens.inspector.data.DbRepository
import com.applens.inspector.data.DbZone
import com.applens.inspector.data.ShizukuBridge
import com.applens.inspector.ui.AppIcon
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
fun DatabasesScreen(
    app: AppEntry,
    onBack: () -> Unit,
    onOpenDatabase: (DbFile) -> Unit,
) {
    val context = LocalContext.current
    val cached = remember(app.packageName) { DbRepository.cachedList(app.packageName) }
    var result by remember(app.packageName) { mutableStateOf(cached) }
    var loading by remember(app.packageName) { mutableStateOf(cached == null) }
    var error by remember(app.packageName) { mutableStateOf<String?>(null) }
    var reloadToken by remember(app.packageName) { mutableIntStateOf(0) }

    LaunchedEffect(reloadToken, app.packageName) {
        if (reloadToken == 0 && result != null) return@LaunchedEffect
        loading = true
        error = null
        runCatching {
            DbRepository.listDatabases(context, app, force = reloadToken > 0)
        }.onSuccess {
            result = it
            loading = false
        }.onFailure {
            error = it.message ?: "扫描数据库失败"
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "SQLite 数据库",
            subtitle = app.label,
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        DbRepository.invalidateList(app.packageName)
                        reloadToken++
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "重新扫描",
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AppSummaryCard(app) }

            val current = result
            when {
                loading -> item {
                    LoadingState(
                        title = "正在扫描私有目录",
                        message = "databases · files · 外部存储",
                    )
                }

                error != null -> item {
                    InfoBanner(
                        title = "扫描失败",
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

                current != null && current.accessMode == DbAccessMode.DENIED -> item {
                    DeniedCard(
                        app = app,
                        message = current.message.orEmpty(),
                        onOpenManager = { ShizukuBridge.openManager(context) },
                        onRetry = {
                            DbRepository.invalidateList(app.packageName)
                            reloadToken++
                        },
                    )
                }

                current != null && current.files.isEmpty() -> item {
                    Column {
                        EmptyState(
                            icon = Icons.Rounded.Storage,
                            title = "没有发现数据库",
                            message = current.message ?: "该应用尚未创建 SQLite 数据库。",
                        )
                        Button(
                            onClick = {
                                DbRepository.invalidateList(app.packageName)
                                reloadToken++
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("重新扫描")
                        }
                    }
                }

                current != null -> {
                    if (current.accessMode == DbAccessMode.RUN_AS) {
                        item {
                            InfoBanner(
                                title = "run-as 受限模式",
                                message = "该应用是 debuggable，AppLens 通过 run-as 读取到部分数据库；" +
                                    "其他应用仍需 Root 级 Shizuku。",
                                accent = AppBlue,
                            )
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "共 ${current.files.size} 个数据库文件",
                                style = MaterialTheme.typography.labelMedium,
                                color = AppMuted,
                            )
                            Spacer(Modifier.weight(1f))
                            Pill(
                                text = if (current.accessMode == DbAccessMode.ROOT) "ROOT 只读" else "RUN-AS",
                                color = if (current.accessMode == DbAccessMode.ROOT) AppMint else AppBlue,
                            )
                        }
                    }
                    current.files.groupBy { it.zone }.forEach { (zone, files) ->
                        item(key = "zone-${zone.name}") {
                            SectionHeader(
                                title = zone.label,
                                trailing = "${files.size} 个 · ${zone.hint}",
                                modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                        items(files, key = { it.absolutePath }) { db ->
                            DatabaseRow(db = db, onClick = { onOpenDatabase(db) })
                        }
                    }
                }
            }

            item {
                InfoBanner(
                    title = "只读副本",
                    message = "打开数据库时会先复制主文件与 -wal/-shm 旁路文件到应用缓存，" +
                        "所有查询都在副本上执行，不会修改原数据。",
                    accent = AppMuted,
                    icon = Icons.Rounded.Storage,
                )
            }
        }
    }
}

@Composable
private fun AppSummaryCard(app: AppEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, AppOutline.copy(alpha = 0.6f), MaterialTheme.shapes.large)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName, label = app.label, size = 50.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            MonoText(text = app.packageName, maxLines = 1)
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Pill(
                    text = if (app.isSystem) "系统应用" else "用户应用",
                    color = if (app.isSystem) AppBlue else AppMint,
                )
                Pill(
                    text = "targetSdk ${app.targetSdk}",
                    color = AppMuted,
                    container = AppSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeniedCard(
    app: AppEntry,
    message: String,
    onOpenManager: () -> Unit,
    onRetry: () -> Unit,
) {
    val state = ShizukuBridge.state.value
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoBanner(
            title = if (state.ready) "需要 Root 级 Shizuku" else "需要 Shizuku 授权",
            message = message,
            accent = AppAmber,
            icon = Icons.Rounded.Lock,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, AppOutline.copy(alpha = 0.6f), MaterialTheme.shapes.large)
                .padding(16.dp),
        ) {
            Text(
                text = "为什么需要 Root？",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Shizuku 以 Shell 身份启动时，Android 的 SELinux 与目录权限会阻止它读取" +
                    "其他应用的 /data/user/0 和 /data/user_de/0。只有以 Root 方式启动 Shizuku，" +
                    "子进程才会继承 UID 0 并读取系统应用与用户应用的私有数据库。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (app.isDebuggable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "该应用标记为 debuggable，AppLens 已尝试 run-as 回退；若仍失败，" +
                        "说明系统限制了 run-as 或应用数据目录不可访问。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppBlue,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenManager,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppAmber,
                        contentColor = Color(0xFF2A1800),
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("打开 Shizuku")
                }
                TextButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp), tint = AppMuted)
                    Spacer(Modifier.width(5.dp))
                    Text("重新检测", color = AppMuted)
                }
            }
        }
    }
}

@Composable
private fun DatabaseRow(db: DbFile, onClick: () -> Unit) {
    val accent = when (db.zone) {
        DbZone.CREDENTIAL -> AppMint
        DbZone.DEVICE -> AppBlue
        DbZone.EXTERNAL -> AppAmber
    }
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
                imageVector = Icons.Rounded.Storage,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = db.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            MonoText(text = db.relativePath, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (db.inDatabasesDir) {
                    Pill(text = "databases/", color = accent)
                }
                Pill(
                    text = db.zone.hint,
                    color = AppMuted,
                    container = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
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

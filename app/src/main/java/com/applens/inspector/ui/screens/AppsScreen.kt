package com.applens.inspector.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.applens.inspector.data.AppEntry
import com.applens.inspector.data.AppRepository
import com.applens.inspector.ui.AppIcon
import com.applens.inspector.ui.AppTopBar
import com.applens.inspector.ui.EmptyState
import com.applens.inspector.ui.FeatureMode
import com.applens.inspector.ui.InfoBanner
import com.applens.inspector.ui.LoadingState
import com.applens.inspector.ui.MonoText
import com.applens.inspector.ui.Pill
import com.applens.inspector.ui.theme.AppBlue
import com.applens.inspector.ui.theme.AppMint
import com.applens.inspector.ui.theme.AppMuted
import com.applens.inspector.ui.theme.AppAmber
import com.applens.inspector.ui.theme.AppOutline
import com.applens.inspector.util.AppListPermission
import java.util.Locale

private enum class AppFilter(val label: String) {
    ALL("全部"),
    USER("用户"),
    SYSTEM("系统"),
}

@Composable
fun AppsScreen(
    mode: FeatureMode,
    onBack: () -> Unit,
    onSelect: (AppEntry) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(AppFilter.ALL.name) }
    val filter = AppFilter.entries.firstOrNull { it.name == filterName } ?: AppFilter.ALL

    val permissionSupported = remember { AppListPermission.isSupported(context) }
    var permissionGranted by remember {
        mutableStateOf(!permissionSupported || AppListPermission.isGranted(context))
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
        if (granted) {
            AppRepository.invalidate()
            reloadToken++
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (permissionSupported) {
            val granted = AppListPermission.isGranted(context)
            if (granted != permissionGranted) {
                permissionGranted = granted
                if (granted) {
                    AppRepository.invalidate()
                    reloadToken++
                }
            }
        }
    }

    LaunchedEffect(reloadToken, permissionGranted) {
        if (!permissionGranted) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching {
            AppRepository.loadInstalledApps(context, force = reloadToken > 0)
        }.onSuccess {
            apps = it
            loading = false
        }.onFailure {
            error = it.message ?: "读取应用列表失败"
            loading = false
        }
    }

    val all = apps.orEmpty()
    val userCount = remember(all) { all.count { !it.isSystem } }
    val systemCount = remember(all) { all.count { it.isSystem } }
    val filtered = remember(all, query, filterName) {
        val q = query.trim().lowercase(Locale.getDefault())
        all.filter { app ->
            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystem
                AppFilter.SYSTEM -> app.isSystem
            }
            val matchesQuery = q.isEmpty() ||
                app.label.lowercase(Locale.getDefault()).contains(q) ||
                app.packageName.lowercase(Locale.getDefault()).contains(q) ||
                app.uid.toString() == q
            matchesFilter && matchesQuery
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = mode.title,
            subtitle = "选择要检视的应用",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        if (permissionSupported && !AppListPermission.isGranted(context)) {
                            permissionGranted = false
                            permissionLauncher.launch(AppListPermission.PERMISSION)
                        } else {
                            AppRepository.invalidate()
                            reloadToken++
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "刷新应用列表",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        if (!permissionGranted) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InfoBanner(
                    title = "需要「获取应用列表」权限",
                    message = if (permissionDenied) {
                        "你已拒绝该权限。MIUI/HyperOS 会把应用列表限制为仅本应用，请重新授权或在系统设置中开启。"
                    } else {
                        "MIUI/HyperOS 在 QUERY_ALL_PACKAGES 之上增加了一层运行时权限。" +
                            "当前系统只向 AppLens 返回它自己，授予后才能展示全部应用。"
                    },
                    accent = AppAmber,
                    icon = Icons.Rounded.Lock,
                )
                Button(
                    onClick = {
                        permissionLauncher.launch(AppListPermission.PERMISSION)
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppAmber,
                        contentColor = Color(0xFF2A1800),
                    ),
                ) {
                    Text("请求权限")
                }
                androidx.compose.material3.TextButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        )
                        context.startActivity(intent)
                    },
                ) {
                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp), tint = AppMuted)
                    Spacer(Modifier.width(5.dp))
                    Text("打开系统设置", color = AppMuted)
                }
            }
            return@Column
        }

        if (loading && apps == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LoadingState(
                    title = "正在读取应用列表",
                    message = "包含用户应用与系统应用",
                )
            }
            return@Column
        }

        if (error != null && apps == null) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                InfoBanner(
                    title = "读取失败",
                    message = error ?: "",
                    accent = MaterialTheme.colorScheme.error,
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, AppOutline.copy(alpha = 0.65f), MaterialTheme.shapes.medium)
                .padding(horizontal = 13.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = AppMuted,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(9.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 11.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                singleLine = true,
                cursorBrush = SolidColor(AppMint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索应用名 / 包名 / UID",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppMuted,
                        )
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { query = "" },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "清空",
                        tint = AppMuted,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill(
                label = AppFilter.ALL.label,
                count = all.size,
                selected = filter == AppFilter.ALL,
                onClick = { filterName = AppFilter.ALL.name },
            )
            FilterPill(
                label = AppFilter.USER.label,
                count = userCount,
                selected = filter == AppFilter.USER,
                onClick = { filterName = AppFilter.USER.name },
            )
            FilterPill(
                label = AppFilter.SYSTEM.label,
                count = systemCount,
                selected = filter == AppFilter.SYSTEM,
                onClick = { filterName = AppFilter.SYSTEM.name },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${filtered.size} 个",
                style = MaterialTheme.typography.labelSmall,
                color = AppMuted,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        Spacer(Modifier.height(11.dp))
        if (filtered.isEmpty()) {
            EmptyState(
                icon = if (query.isBlank()) Icons.Rounded.Search else Icons.Rounded.Close,
                title = "没有匹配的应用",
                message = if (query.isBlank()) {
                    "当前筛选条件下没有应用。"
                } else {
                    "换个关键词试试，或清空搜索。"
                },
            )
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    AppRow(app = app, onClick = { onSelect(app) })
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) AppMint.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.surfaceContainerLow
    val borderColor = if (selected) AppMint.copy(alpha = 0.5f) else AppOutline.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) AppMint else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) AppMint.copy(alpha = 0.8f) else AppMuted,
        )
    }
}

@Composable
private fun AppRow(app: AppEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, AppOutline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName, label = app.label, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = AppMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Pill(
                    text = if (app.isSystem) "系统" else "用户",
                    color = if (app.isSystem) AppBlue else AppMint,
                    container = if (app.isSystem) AppBlue.copy(alpha = 0.12f)
                    else AppMint.copy(alpha = 0.12f),
                )
                if (app.isDebuggable) {
                    Pill(
                        text = "debuggable",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                Pill(
                    text = "UID ${app.uid}",
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

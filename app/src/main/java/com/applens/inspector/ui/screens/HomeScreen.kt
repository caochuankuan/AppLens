package com.applens.inspector.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.applens.inspector.data.ShizukuBridge
import com.applens.inspector.ui.FeatureMode
import com.applens.inspector.ui.GradientMark
import com.applens.inspector.ui.InfoBanner
import com.applens.inspector.ui.LocalSnackbarHost
import com.applens.inspector.ui.Pill
import com.applens.inspector.ui.SectionHeader
import com.applens.inspector.ui.theme.AppAmber
import com.applens.inspector.ui.theme.AppBlue
import com.applens.inspector.ui.theme.AppMint
import com.applens.inspector.ui.theme.AppMuted
import com.applens.inspector.ui.theme.AppOutline
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onOpenFeature: (FeatureMode) -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val state by ShizukuBridge.state.collectAsStateWithLifecycle()
    var previousAuthorized by remember { mutableStateOf(state.authorized) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        ShizukuBridge.refresh()
    }

    LaunchedEffect(state.binderAlive, state.authorized) {
        if (state.binderAlive && !state.authorized) {
            val prefs = context.getSharedPreferences("applens", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("auto_shizuku_requested", false)) {
                prefs.edit().putBoolean("auto_shizuku_requested", true).apply()
                ShizukuBridge.requestPermission()
            }
        }
    }

    LaunchedEffect(state.authorized) {
        if (state.authorized && !previousAuthorized) {
            snackbar.showSnackbar("Shizuku 授权成功")
        }
        previousAuthorized = state.authorized
    }

    val accent = when {
        state.ready -> AppMint
        state.binderAlive -> AppAmber
        else -> AppBlue
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GradientMark(size = 50.dp)
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AppLens",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "应用透镜 · 本地 Android 透视工具",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Pill(text = "v1.0", color = AppMuted, container = MaterialTheme.colorScheme.surfaceVariant)
            }
        }

        item {
            ShizukuCard(
                state = state,
                accent = accent,
                onRequest = {
                    if (!state.binderAlive) {
                        ShizukuBridge.openManager(context)
                        scope.launch {
                            snackbar.showSnackbar("请先在 Shizuku 中启动服务")
                        }
                    } else {
                        ShizukuBridge.requestPermission()
                    }
                },
                onOpenManager = { ShizukuBridge.openManager(context) },
                onRefresh = {
                    ShizukuBridge.refresh()
                    scope.launch { snackbar.showSnackbar("已重新检测 Shizuku") }
                },
            )
        }

        item {
            AnimatedVisibility(
                visible = state.ready,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    SectionHeader(
                        title = "工具箱",
                        trailing = if (state.isRoot) "Root 就绪" else "Shell 模式",
                    )
                    Spacer(Modifier.height(10.dp))
                    FeatureCard(
                        icon = Icons.Rounded.Storage,
                        accent = AppMint,
                        title = "SQLite 数据库",
                        subtitle = "全量应用 · 表结构 · 行数据",
                        description = "浏览用户应用与系统应用的数据库。选择应用后进入只读副本查看表和数据。",
                        footer = if (state.isRoot) {
                            "Root 权限已就绪，可读取私有目录"
                        } else {
                            "Shell 模式无法读取私有目录，请用 Root 启动 Shizuku"
                        },
                        footerWarning = !state.isRoot,
                        onClick = { onOpenFeature(FeatureMode.DATABASE) },
                    )
                    Spacer(Modifier.height(12.dp))
                    FeatureCard(
                        icon = Icons.Rounded.TravelExplore,
                        accent = AppBlue,
                        title = "网络接口雷达",
                        subtitle = "APK 静态扫描 · URL · 域名",
                        description = "扫描 DEX、资源、assets 与原生库，提取硬编码的网络请求地址和域名。",
                        footer = "扫描完全离线，不上传 APK",
                        footerWarning = false,
                        onClick = { onOpenFeature(FeatureMode.URL_SCAN) },
                    )
                }
            }
        }

        if (!state.ready) {
            item {
                Column {
                    Spacer(Modifier.height(4.dp))
                    SectionHeader(title = "三步启用")
                    Spacer(Modifier.height(10.dp))
                    SetupCard(
                        state = state,
                        onOpenManager = { ShizukuBridge.openManager(context) },
                        onRequest = {
                            if (state.binderAlive) {
                                ShizukuBridge.requestPermission()
                            } else {
                                ShizukuBridge.openManager(context)
                            }
                        },
                    )
                }
            }
        }

        item {
            InfoBanner(
                title = "关于数据库读取权限",
                message = "Android 禁止普通应用和 Shell 用户读取其他应用的 /data 私有目录。" +
                    "因此浏览全部应用的 SQLite 数据库需要 Shizuku 以 Root 模式启动；" +
                    "若 Shizuku 为 Shell 模式，AppLens 仍可扫描全部应用，但数据库页会明确提示权限不足。",
                accent = if (state.isRoot) AppMint else AppAmber,
                icon = Icons.Rounded.Lock,
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = AppMuted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "所有解析均在本机完成 · 不收集、不上传任何数据",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppMuted,
                )
            }
        }
    }
}

@Composable
private fun ShizukuCard(
    state: ShizukuBridge.State,
    accent: Color,
    onRequest: () -> Unit,
    onOpenManager: () -> Unit,
    onRefresh: () -> Unit,
) {
    val title = when {
        !state.managerInstalled -> "尚未安装 Shizuku"
        !state.binderAlive -> "Shizuku 服务未运行"
        !state.authorized -> "等待 Shizuku 授权"
        else -> "Shizuku 已连接"
    }
    val description = when {
        !state.managerInstalled ->
            "先安装 Shizuku。AppLens 通过它获得受控的系统级访问能力。"
        !state.binderAlive ->
            "打开 Shizuku，启动服务后再回到这里。Root 设备可直接点击启动。"
        !state.authorized ->
            "点击「请求授权」，在 Shizuku 弹窗中允许 AppLens 访问。"
        state.uid == 0 ->
            "当前以 Root 身份运行。可以读取应用私有目录与系统应用数据。"
        else ->
            "当前以 Shell 身份运行。可扫描 APK，但读取私有数据库需要 Root 启动。"
    }
    val statusIcon = when {
        !state.managerInstalled -> Icons.Rounded.ErrorOutline
        !state.binderAlive -> Icons.Rounded.Terminal
        !state.authorized -> Icons.Rounded.Lock
        else -> Icons.Rounded.CheckCircle
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.17f),
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    start = Offset.Zero,
                    end = Offset(1000f, 900f),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.25f), MaterialTheme.shapes.extraLarge),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = accent.copy(alpha = 0.08f),
                radius = size.minDimension * 0.52f,
                center = Offset(size.width * 0.92f, size.height * 0.04f),
            )
            drawCircle(
                color = accent.copy(alpha = 0.05f),
                radius = size.minDimension * 0.3f,
                center = Offset(size.width * 0.08f, size.height * 0.98f),
            )
        }
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SHIZUKU",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppMuted,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Pill(
                    text = state.modeLabel,
                    color = accent,
                    container = accent.copy(alpha = 0.13f),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.ready) {
                Spacer(Modifier.height(13.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Pill(
                        text = "UID ${state.uid}",
                        color = MaterialTheme.colorScheme.onSurface,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Pill(
                        text = "API ${state.version}",
                        color = MaterialTheme.colorScheme.onSurface,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    if (state.uid == 0) {
                        Pill(text = "ROOT", color = AppAmber, container = AppAmber.copy(alpha = 0.12f))
                    }
                }
            }

            Spacer(Modifier.height(17.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    !state.managerInstalled -> {
                        HomeActionButton(
                            text = "下载 Shizuku",
                            icon = Icons.Rounded.OpenInNew,
                            accent = accent,
                            onClick = onOpenManager,
                        )
                    }
                    !state.binderAlive -> {
                        HomeActionButton(
                            text = "打开 Shizuku",
                            icon = Icons.Rounded.OpenInNew,
                            accent = accent,
                            onClick = onOpenManager,
                        )
                        TextButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp), tint = AppMuted)
                            Spacer(Modifier.width(5.dp))
                            Text("重新检测", color = AppMuted)
                        }
                    }
                    !state.authorized -> {
                        HomeActionButton(
                            text = "请求授权",
                            icon = Icons.Rounded.Bolt,
                            accent = accent,
                            onClick = onRequest,
                        )
                        TextButton(onClick = onOpenManager) {
                            Text("打开 Shizuku", color = AppMuted)
                        }
                    }
                    else -> {
                        TextButton(onClick = onRefresh, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp), tint = AppMuted)
                            Spacer(Modifier.width(6.dp))
                            Text("重新检测", color = AppMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionButton(
    text: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = Color(0xFF04110D),
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    description: String,
    footer: String,
    footerWarning: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, AppOutline.copy(alpha = 0.7f), MaterialTheme.shapes.extraLarge)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppMuted,
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = "进入",
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(13.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (footerWarning) Icons.Rounded.Lock else Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (footerWarning) AppAmber else AppMint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = if (footerWarning) AppAmber else AppMuted,
            )
        }
    }
}

@Composable
private fun SetupCard(
    state: ShizukuBridge.State,
    onOpenManager: () -> Unit,
    onRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, AppOutline.copy(alpha = 0.6f), MaterialTheme.shapes.large)
            .padding(17.dp),
    ) {
        SetupStep(
            number = "01",
            title = "安装并打开 Shizuku",
            detail = if (state.managerInstalled) "已安装" else "尚未安装",
            done = state.managerInstalled,
        )
        Spacer(Modifier.height(14.dp))
        SetupStep(
            number = "02",
            title = "启动 Shizuku 服务",
            detail = if (state.binderAlive) "服务运行中" else "Root 设备：打开 Shizuku 点「启动」",
            done = state.binderAlive,
        )
        Spacer(Modifier.height(14.dp))
        SetupStep(
            number = "03",
            title = "回到 AppLens 授权",
            detail = if (state.authorized) "已授权" else "点击请求授权并允许",
            done = state.authorized,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onOpenManager,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Icon(Icons.Rounded.Android, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("打开 Shizuku")
            }
            if (state.binderAlive) {
                Button(
                    onClick = onRequest,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppMint,
                        contentColor = Color(0xFF04110D),
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text("立即授权")
                }
            }
        }
    }
}

@Composable
private fun SetupStep(
    number: String,
    title: String,
    detail: String,
    done: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (done) AppMint.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = AppMint,
                    modifier = Modifier.size(17.dp),
                )
            } else {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppMuted,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (done) AppMint else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

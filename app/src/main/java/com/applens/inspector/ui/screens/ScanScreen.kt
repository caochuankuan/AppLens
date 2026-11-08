package com.applens.inspector.ui.screens

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.applens.inspector.data.AppEntry
import com.applens.inspector.data.HostHit
import com.applens.inspector.data.ScanProgress
import com.applens.inspector.data.ScanResult
import com.applens.inspector.data.UrlHit
import com.applens.inspector.data.UrlScanner
import com.applens.inspector.ui.AppIcon
import com.applens.inspector.ui.AppTopBar
import com.applens.inspector.ui.EmptyState
import com.applens.inspector.ui.InfoBanner
import com.applens.inspector.ui.LocalSnackbarHost
import com.applens.inspector.ui.MetricTile
import com.applens.inspector.ui.MonoText
import com.applens.inspector.ui.Pill
import com.applens.inspector.ui.SectionHeader
import com.applens.inspector.ui.theme.AppAmber
import com.applens.inspector.ui.theme.AppBlue
import com.applens.inspector.ui.theme.AppMint
import com.applens.inspector.ui.theme.AppMuted
import com.applens.inspector.ui.theme.AppOutline
import com.applens.inspector.ui.theme.AppSurfaceVariant
import com.applens.inspector.util.formatBytes
import com.applens.inspector.util.formatCount
import com.applens.inspector.util.formatDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private enum class ScanTab(val label: String) {
    URLS("请求 URL"),
    HOSTS("域名"),
}

@Composable
fun ScanScreen(app: AppEntry, onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHost.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    val cached = remember(app.packageName) { UrlScanner.cached(app.packageName) }
    var result by remember(app.packageName) { mutableStateOf<ScanResult?>(cached) }
    var scanning by remember(app.packageName) { mutableStateOf(cached == null) }
    var error by remember(app.packageName) { mutableStateOf<String?>(null) }
    var scanToken by remember(app.packageName) { mutableIntStateOf(0) }
    val progressFlow = remember(app.packageName) {
        MutableStateFlow(
            ScanProgress(
                fraction = if (cached != null) 1f else 0f,
                title = if (cached != null) "读取缓存" else "准备扫描",
                detail = if (cached != null) "${cached.urls.size} 个 URL" else "",
            ),
        )
    }
    val progress by progressFlow.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var tabName by rememberSaveable { mutableStateOf(ScanTab.URLS.name) }
    var selectedHost by rememberSaveable { mutableStateOf<String?>(null) }
    val tab = ScanTab.entries.firstOrNull { it.name == tabName } ?: ScanTab.URLS

    LaunchedEffect(scanToken, app.packageName) {
        if (scanToken == 0 && result != null) return@LaunchedEffect
        scanning = true
        error = null
        try {
            val scanned = UrlScanner.scan(
                context = context,
                app = app,
                force = scanToken > 0,
                onProgress = { progressFlow.value = it },
            )
            result = scanned
            scanning = false
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            error = t.message ?: "扫描失败"
            scanning = false
        }
    }

    val current = result
    val filteredUrls = remember(current?.urls, query, selectedHost) {
        val q = query.trim().lowercase()
        current?.urls.orEmpty().filter { hit ->
            val hostOk = selectedHost == null || hit.host == selectedHost
            val queryOk = q.isEmpty() ||
                hit.url.lowercase().contains(q) ||
                hit.host.lowercase().contains(q) ||
                hit.sources.any { it.lowercase().contains(q) }
            hostOk && queryOk
        }
    }
    val filteredHosts = remember(current?.hosts, query) {
        val q = query.trim().lowercase()
        current?.hosts.orEmpty().filter { host ->
            q.isEmpty() || host.host.lowercase().contains(q)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "网络接口雷达",
            subtitle = "${app.label} · APK 静态扫描",
            onBack = onBack,
            actions = {
                if (current != null && current.urls.isNotEmpty()) {
                    IconButton(
                        onClick = { shareResult(context, app, current) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "分享结果",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = {
                        if (!scanning) {
                            UrlScanner.invalidate(app.packageName)
                            scanToken++
                        }
                    },
                    enabled = !scanning,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "重新扫描",
                        tint = if (scanning) AppMuted else MaterialTheme.colorScheme.onSurfaceVariant,
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
            item { ScanAppHeader(app) }

            when {
                scanning -> item {
                    ScanningCard(progress = progress)
                }

                error != null -> item {
                    InfoBanner(
                        title = "扫描失败",
                        message = error ?: "",
                        accent = MaterialTheme.colorScheme.error,
                        icon = Icons.Rounded.ErrorOutline,
                        action = {
                            TextButton(onClick = { scanToken++ }) {
                                Text("重试", color = MaterialTheme.colorScheme.error)
                            }
                        },
                    )
                }

                current != null -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile(
                                label = "唯一 URL",
                                value = formatCount(current.urls.size),
                                accent = AppMint,
                                modifier = Modifier.weight(1f),
                            )
                            MetricTile(
                                label = "域名",
                                value = formatCount(current.hosts.size),
                                accent = AppBlue,
                                modifier = Modifier.weight(1f),
                            )
                            MetricTile(
                                label = "耗时",
                                value = formatDuration(current.durationMs),
                                accent = AppAmber,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    if (current.warnings.isNotEmpty()) {
                        item {
                            InfoBanner(
                                title = "${current.warnings.size} 条扫描提示",
                                message = current.warnings.joinToString("\n"),
                                accent = AppAmber,
                                icon = Icons.Rounded.ErrorOutline,
                            )
                        }
                    }

                    item {
                        ScanSearchBar(
                            query = query,
                            onQueryChange = { query = it },
                            onClear = { query = "" },
                            keyboard = keyboard,
                        )
                    }

                    item {
                        ScanTabs(
                            selected = tab,
                            urlCount = current.urls.size,
                            hostCount = current.hosts.size,
                            onSelect = { tabName = it.name },
                        )
                    }

                    if (selectedHost != null) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "仅显示 ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppMuted,
                                )
                                Pill(
                                    text = selectedHost.orEmpty(),
                                    color = AppBlue,
                                    container = AppBlue.copy(alpha = 0.12f),
                                )
                                Spacer(Modifier.width(6.dp))
                                TextButton(
                                    onClick = { selectedHost = null },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                ) {
                                    Icon(Icons.Rounded.Close, null, Modifier.size(14.dp), tint = AppMuted)
                                    Spacer(Modifier.width(3.dp))
                                    Text("清除", color = AppMuted)
                                }
                            }
                        }
                    }

                    if (tab == ScanTab.URLS) {
                        item {
                            SectionHeader(
                                title = "提取到的请求地址",
                                trailing = "${filteredUrls.size} / ${current.urls.size}",
                            )
                        }
                        if (filteredUrls.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Rounded.TravelExplore,
                                    title = if (current.urls.isEmpty()) "未发现硬编码 URL" else "没有匹配结果",
                                    message = if (current.urls.isEmpty()) {
                                        "这个 APK 中没有找到 http(s)/ws(s) 形式的完整地址。" +
                                            "动态拼接、加密、服务端下发或 WebView 中的 URL 无法通过静态扫描发现。"
                                    } else {
                                        "试试更换搜索词，或清除域名筛选。"
                                    },
                                )
                            }
                        } else {
                            items(filteredUrls, key = { it.url }) { hit ->
                                UrlCard(
                                    hit = hit,
                                    onCopy = {
                                        clipboard.setText(AnnotatedString(hit.url))
                                        scope.launch { snackbar.showSnackbar("已复制 URL") }
                                    },
                                    onFilterHost = { selectedHost = hit.host },
                                )
                            }
                        }
                    } else {
                        item {
                            SectionHeader(
                                title = "请求域名",
                                trailing = "${filteredHosts.size} / ${current.hosts.size}",
                            )
                        }
                        if (filteredHosts.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Rounded.Dns,
                                    title = "没有域名",
                                    message = "未从完整 URL 中提取到域名，或当前搜索没有匹配项。",
                                )
                            }
                        } else {
                            items(filteredHosts, key = { it.host }) { host ->
                                HostCard(
                                    host = host,
                                    onClick = {
                                        selectedHost = host.host
                                        tabName = ScanTab.URLS.name
                                    },
                                )
                            }
                        }
                    }

                    item {
                        InfoBanner(
                            title = "静态扫描的边界",
                            message = "已扫描 ${formatCount(current.filesScanned)} 个文件、" +
                                "${formatBytes(current.bytesScanned)} 数据。" +
                                "只提取 APK 中硬编码的完整 URL；运行时拼接、加密字符串、" +
                                "服务端配置与 WebView 动态请求不会出现。整个过程不发起网络请求。",
                            accent = AppMuted,
                            icon = Icons.Rounded.Link,
                        )
                    }
                }

                else -> item {
                    EmptyState(
                        icon = Icons.Rounded.TravelExplore,
                        title = "尚未扫描",
                        message = "点击右上角刷新开始扫描。",
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanAppHeader(app: AppEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, AppOutline.copy(alpha = 0.6f), MaterialTheme.shapes.large)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName, label = app.label, size = 48.dp)
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
                Pill(text = "静态扫描", color = AppMint)
                Pill(text = "离线", color = AppBlue)
                if (app.isSystem) {
                    Pill(
                        text = "系统",
                        color = AppMuted,
                        container = AppSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanningCard(progress: ScanProgress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        AppMint.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    start = Offset.Zero,
                    end = Offset(900f, 900f),
                ),
            )
            .border(1.dp, AppMint.copy(alpha = 0.2f), MaterialTheme.shapes.extraLarge)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RadarAnimation()
        Spacer(Modifier.height(18.dp))
        Text(
            text = progress.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = progress.detail.ifBlank { "解析 APK 文件…" },
            style = MaterialTheme.typography.bodySmall,
            color = AppMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(18.dp))
        val fraction = progress.fraction.coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(AppMint, AppBlue))),
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DEX · resources · assets · native libs",
                style = MaterialTheme.typography.labelSmall,
                color = AppMuted,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = AppMint,
            )
        }
    }
}

@Composable
private fun RadarAnimation() {
    val transition = rememberInfiniteTransition(label = "radar")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
        ),
        label = "radarAngle",
    )
    Box(modifier = Modifier.size(152.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f - 6f
            listOf(0.32f, 0.58f, 0.84f).forEach { fraction ->
                drawCircle(
                    color = AppMint.copy(alpha = 0.12f),
                    radius = maxRadius * fraction,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            drawLine(
                color = AppMint.copy(alpha = 0.1f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = AppMint.copy(alpha = 0.1f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.dp.toPx(),
            )
            rotate(degrees = angle, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            AppMint.copy(alpha = 0.02f),
                            AppMint.copy(alpha = 0.28f),
                        ),
                        center = center,
                    ),
                    startAngle = 0f,
                    sweepAngle = 58f,
                    useCenter = true,
                    topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                    size = androidx.compose.ui.geometry.Size(maxRadius * 2f, maxRadius * 2f),
                )
                drawLine(
                    color = AppMint,
                    start = center,
                    end = Offset(center.x + maxRadius, center.y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = AppMint,
                    radius = 3.dp.toPx(),
                    center = Offset(center.x + maxRadius, center.y),
                )
            }
            drawCircle(
                color = AppMint.copy(alpha = 0.18f),
                radius = 24.dp.toPx(),
                center = center,
            )
        }
        CircularProgressIndicator(
            color = AppMint,
            strokeWidth = 2.dp,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun ScanSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 11.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(AppMint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = "搜索 URL / 域名 / 来源文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppMuted,
                    )
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "清空",
                    tint = AppMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun ScanTabs(
    selected: ScanTab,
    urlCount: Int,
    hostCount: Int,
    onSelect: (ScanTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, AppOutline.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScanTab.entries.forEach { tab ->
            val active = tab == selected
            val count = if (tab == ScanTab.URLS) urlCount else hostCount
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) AppMint.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) AppMint else AppMuted,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) AppMint.copy(alpha = 0.8f) else AppMuted,
                )
            }
        }
    }
}

@Composable
private fun UrlCard(
    hit: UrlHit,
    onCopy: () -> Unit,
    onFilterHost: () -> Unit,
) {
    val schemeColor = when (hit.scheme) {
        "https", "wss" -> AppMint
        "http", "ws" -> AppAmber
        else -> AppBlue
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, AppOutline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .clickable(onClick = onCopy)
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(text = hit.scheme.uppercase(), color = schemeColor)
            Spacer(Modifier.width(7.dp))
            Text(
                text = hit.host,
                style = MaterialTheme.typography.labelMedium,
                color = schemeColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onFilterHost),
            )
            Spacer(Modifier.width(6.dp))
            if (hit.occurrences > 1) {
                Text(
                    text = "×${hit.occurrences}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppMuted,
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = "复制",
                tint = AppMuted,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = hit.url,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = AppMuted,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = buildString {
                    append(hit.sources.take(2).joinToString(" · "))
                    if (hit.sourceCount > 2) append(" +${hit.sourceCount - 2}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = AppMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HostCard(host: HostHit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, AppOutline.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(AppBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Dns,
                contentDescription = null,
                tint = AppBlue,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = host.host,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            MonoText(text = host.sample, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${host.count}",
                style = MaterialTheme.typography.titleMedium,
                color = AppBlue,
            )
            Text(
                text = "URL",
                style = MaterialTheme.typography.labelSmall,
                color = AppMuted,
            )
        }
    }
}

private fun shareResult(context: android.content.Context, app: AppEntry, result: ScanResult) {
    val builder = StringBuilder()
    builder.appendLine("AppLens · ${app.label}")
    builder.appendLine(app.packageName)
    builder.appendLine("${result.urls.size} URLs · ${result.hosts.size} hosts")
    builder.appendLine()
    result.urls.take(400).forEach { builder.appendLine(it.url) }
    if (result.urls.size > 400) {
        builder.appendLine("… ${result.urls.size - 400} more")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${app.label} · URL 扫描结果")
        putExtra(Intent.EXTRA_TEXT, builder.toString())
    }
    context.startActivity(Intent.createChooser(intent, "分享扫描结果"))
}

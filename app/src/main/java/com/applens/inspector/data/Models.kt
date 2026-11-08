package com.applens.inspector.data

import androidx.compose.runtime.Immutable

@Immutable
data class AppEntry(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean,
    val isDebuggable: Boolean,
    val targetSdk: Int,
    val versionName: String?,
) {
    val initial: String
        get() = label.trim().take(1).uppercase().ifBlank { "?" }
}

enum class DbZone(val label: String, val hint: String) {
    CREDENTIAL("应用私有数据", "CE · 解锁后可用"),
    DEVICE("设备加密数据", "DE · 开机即可用"),
    EXTERNAL("外部存储", "App-specific external"),
}

data class DbFile(
    val absolutePath: String,
    val relativePath: String,
    val displayName: String,
    val zone: DbZone,
    val inDatabasesDir: Boolean,
)

data class ColumnInfo(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val primaryKey: Boolean,
)

data class TableInfo(
    val name: String,
    val type: String,
    val sql: String?,
    val columns: List<ColumnInfo>,
) {
    val isView: Boolean get() = type.equals("view", ignoreCase = true)
    val columnSummary: String
        get() = columns.joinToString(" · ") { it.name }.ifBlank { "无列信息" }
}

enum class CellKind { NULL, INTEGER, REAL, TEXT, BLOB }

@Immutable
data class CellValue(
    val text: String,
    val kind: CellKind,
) {
    val copyValue: String
        get() = if (kind == CellKind.NULL) "" else text
}

data class TablePage(
    val columns: List<String>,
    val columnTypes: List<String>,
    val rows: List<List<CellValue>>,
    val offset: Int,
    val pageSize: Int,
    val hasMore: Boolean,
    val loadedAt: Long,
)

data class DbListResult(
    val files: List<DbFile>,
    val accessMode: DbAccessMode,
    val message: String? = null,
)

enum class DbAccessMode {
    ROOT,
    RUN_AS,
    DENIED,
}

data class ScanProgress(
    val fraction: Float,
    val title: String,
    val detail: String,
)

@Immutable
data class UrlHit(
    val url: String,
    val host: String,
    val scheme: String,
    val sources: List<String>,
    val sourceCount: Int,
    val occurrences: Int,
)

@Immutable
data class HostHit(
    val host: String,
    val count: Int,
    val sample: String,
)

data class ScanResult(
    val urls: List<UrlHit>,
    val hosts: List<HostHit>,
    val filesScanned: Int,
    val bytesScanned: Long,
    val apkBytes: Long,
    val durationMs: Long,
    val warnings: List<String>,
)

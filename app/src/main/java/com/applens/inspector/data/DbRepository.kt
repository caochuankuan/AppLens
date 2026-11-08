package com.applens.inspector.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class DbException(message: String) : Exception(message)

object DbRepository {

    private const val TAG = "AppLensDb"
    const val PAGE_SIZE = 100
    private const val MAX_CELL_CHARS = 600
    private val listCache = mutableMapOf<String, DbListResult>()

    suspend fun listDatabases(
        context: Context,
        app: AppEntry,
        force: Boolean = false,
    ): DbListResult =
        withContext(Dispatchers.IO) {
            if (!force) {
                listCache[app.packageName]?.let { return@withContext it }
            }
            val mode = Shell.accessMode(app.packageName, app.isDebuggable)
            if (mode == DbAccessMode.DENIED) {
                return@withContext DbListResult(
                    files = emptyList(),
                    accessMode = mode,
                    message = denialMessage(app),
                )
            }
            val pkg = app.packageName
            val roots = listOf(
                "/data/user/0/$pkg" to DbZone.CREDENTIAL,
                "/data/user_de/0/$pkg" to DbZone.DEVICE,
                "/storage/emulated/0/Android/data/$pkg" to DbZone.EXTERNAL,
            )
            val script = buildString {
                append("for d in")
                roots.forEach { (root, _) ->
                    append(' ')
                    append(Shell.shellQuote(root))
                }
                append("; do [ -d \"\$d\" ] || continue; ")
                append("find \"\$d\" -maxdepth 8 -type f ")
                append("\\( -path '*/databases/*' -o -name '*.db' -o -name '*.sqlite' ")
                append("-o -name '*.sqlite3' -o -name '*.db3' -o -name '*.s3db' \\) ")
                append("! -name '*-wal' ! -name '*-shm' ! -name '*-journal' ")
                append("! -name '*-lock' ! -name '*.tmp' 2>/dev/null; done")
            }
            val result = Shell.execScript(
                script = script,
                runAsPackage = if (mode == DbAccessMode.RUN_AS) pkg else null,
                timeoutMs = 45_000,
            )
            if (result.exitCode != 0 && result.stdout.isBlank()) {
                return@withContext DbListResult(
                    files = emptyList(),
                    accessMode = mode,
                    message = result.errorMessage("扫描应用目录失败"),
                )
            }

            val byPath = LinkedHashMap<String, DbFile>()
            result.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { path ->
                    if (!path.startsWith("/")) return@forEach
                    val zone = roots.firstOrNull { (root, _) ->
                        path == root || path.startsWith("$root/")
                    }?.second ?: return@forEach
                    val root = roots.first { it.second == zone }.first
                    val relative = path.removePrefix("$root/")
                    byPath.getOrPut(path) {
                        DbFile(
                            absolutePath = path,
                            relativePath = relative,
                            displayName = path.substringAfterLast('/'),
                            zone = zone,
                            inDatabasesDir = "/databases/" in path,
                        )
                    }
                }
            val files = byPath.values.sortedWith(
                compareBy<DbFile> { it.zone.ordinal }
                    .thenByDescending { it.inDatabasesDir }
                    .thenBy { it.relativePath.lowercase() },
            )
            val output = DbListResult(
                files = files,
                accessMode = mode,
                message = if (files.isEmpty()) {
                    if (mode == DbAccessMode.RUN_AS) {
                        "run-as 只能访问 debuggable 应用的部分目录，未发现数据库。"
                    } else {
                        "未发现 SQLite 数据库文件。数据可能被加密、放在非标准路径，或应用尚未创建数据库。"
                    }
                } else {
                    null
                },
            )
            listCache[app.packageName] = output
            output
        }

    fun cachedList(packageName: String): DbListResult? = listCache[packageName]

    fun invalidateList(packageName: String? = null) {
        if (packageName == null) listCache.clear() else listCache.remove(packageName)
    }

    private fun denialMessage(app: AppEntry): String {
        val state = ShizukuBridge.state.value
        return when {
            !state.ready -> "需要先授权 Shizuku。"
            app.isDebuggable ->
                "当前 Shizuku 运行在 Shell 模式，且 run-as 无法访问该应用。"
            else ->
                "当前 Shizuku 以 Shell 身份运行，Android 禁止它读取其他应用的 /data 目录。请用 Root 方式启动 Shizuku 后重试。"
        }
    }

    suspend fun invalidateCache(context: Context, db: DbFile) {
        withContext(Dispatchers.IO) {
            cacheDir(context, db).deleteRecursively()
            tableCache.remove(db.absolutePath)
        }
    }

    suspend fun copyForReading(
        context: Context,
        app: AppEntry,
        db: DbFile,
        mode: DbAccessMode,
        force: Boolean = false,
    ): File = withContext(Dispatchers.IO) {
        if (mode == DbAccessMode.DENIED) throw DbException("没有权限读取该数据库")
        val dir = cacheDir(context, db)
        val target = File(dir, "database.db")
        val marker = File(dir, ".complete")
        if (!force && marker.exists() && target.exists() && target.length() > 0) {
            return@withContext target
        }
        dir.deleteRecursively()
        dir.mkdirs()

        val runAs = if (mode == DbAccessMode.RUN_AS) app.packageName else null
        val copied = Shell.copyToFile(db.absolutePath, target, runAs)
        if (!copied) {
            dir.deleteRecursively()
            throw DbException(
                "无法复制数据库。请确认 Shizuku 仍有权限，且数据库没有被其他进程独占。",
            )
        }
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val sidecar = File(dir, "database.db$suffix")
            if (!Shell.copyToFile("${db.absolutePath}$suffix", sidecar, runAs)) {
                sidecar.delete()
            }
        }
        if (!hasSqliteHeader(target)) {
            dir.deleteRecursively()
            throw DbException("这不是一个可读的 SQLite 数据库，可能使用了 SQLCipher 等加密方案。")
        }
        marker.writeText(System.currentTimeMillis().toString())
        target
    }

    private val tableCache = mutableMapOf<String, List<TableInfo>>()

    suspend fun readTables(
        context: Context,
        app: AppEntry,
        db: DbFile,
        mode: DbAccessMode,
        force: Boolean = false,
    ): List<TableInfo> = withContext(Dispatchers.IO) {
        if (!force) {
            tableCache[db.absolutePath]?.let { return@withContext it }
        }
        val file = copyForReading(context, app, db, mode, force = force)
        val output = openDatabase(file).use { database ->
            val tables = mutableListOf<TableInfo>()
            database.rawQuery(
                "SELECT name, type, sql FROM sqlite_master " +
                    "WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' " +
                    "ORDER BY (type = 'view'), name COLLATE NOCASE",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val type = cursor.getString(1) ?: "table"
                    val sql = cursor.getStringOrNull(2)
                    val columns = runCatching { readColumns(database, name) }
                        .getOrDefault(emptyList())
                    tables += TableInfo(name, type, sql, columns)
                }
            }
            tables
        }
        tableCache[db.absolutePath] = output
        output
    }

    suspend fun readTablePage(
        context: Context,
        app: AppEntry,
        db: DbFile,
        mode: DbAccessMode,
        table: String,
        offset: Int,
        pageSize: Int = PAGE_SIZE,
    ): TablePage = withContext(Dispatchers.IO) {
        val safeOffset = offset.coerceAtLeast(0)
        val safeSize = pageSize.coerceIn(1, 500)
        val file = copyForReading(context, app, db, mode)
        openDatabase(file).use { database ->
            val types = runCatching { readColumns(database, table) }
                .getOrDefault(emptyList())
                .associate { it.name to it.type }
            val sql = "SELECT * FROM ${quoteIdent(table)} LIMIT ${safeSize + 1} OFFSET $safeOffset"
            database.rawQuery(sql, null).use { cursor ->
                val columns = cursor.columnNames.toList()
                val columnTypes = columns.map { types[it].orEmpty() }
                val rows = ArrayList<List<CellValue>>(safeSize)
                var read = 0
                while (cursor.moveToNext() && read < safeSize) {
                    val row = ArrayList<CellValue>(columns.size)
                    for (index in columns.indices) {
                        row += readCell(cursor, index)
                    }
                    rows += row
                    read++
                }
                TablePage(
                    columns = columns,
                    columnTypes = columnTypes,
                    rows = rows,
                    offset = safeOffset,
                    pageSize = safeSize,
                    hasMore = cursor.count > safeSize,
                    loadedAt = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun openDatabase(file: File): SQLiteDatabase {
        return try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).also { db ->
                runCatching { db.execSQL("PRAGMA query_only = ON") }
            }
        } catch (t: SQLiteException) {
            Log.w(TAG, "open failed", t)
            throw DbException("无法打开数据库：${t.message ?: "格式损坏或已加密"}")
        }
    }

    private fun readColumns(database: SQLiteDatabase, table: String): List<ColumnInfo> {
        val result = mutableListOf<ColumnInfo>()
        database.rawQuery("PRAGMA table_info(${quoteIdent(table)})", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val typeIndex = cursor.getColumnIndex("type")
            val notNullIndex = cursor.getColumnIndex("notnull")
            val pkIndex = cursor.getColumnIndex("pk")
            while (cursor.moveToNext()) {
                result += ColumnInfo(
                    name = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "" else "",
                    type = if (typeIndex >= 0) cursor.getString(typeIndex).orEmpty() else "",
                    notNull = notNullIndex >= 0 && cursor.getInt(notNullIndex) != 0,
                    primaryKey = pkIndex >= 0 && cursor.getInt(pkIndex) != 0,
                )
            }
        }
        return result
    }

    private fun readCell(cursor: Cursor, index: Int): CellValue {
        return try {
            when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> CellValue("NULL", CellKind.NULL)
                Cursor.FIELD_TYPE_INTEGER ->
                    CellValue(cursor.getLong(index).toString(), CellKind.INTEGER)
                Cursor.FIELD_TYPE_FLOAT ->
                    CellValue(cursor.getDouble(index).toString(), CellKind.REAL)
                Cursor.FIELD_TYPE_BLOB -> {
                    val bytes = runCatching { cursor.getBlob(index) }.getOrNull()
                    val text = if (bytes == null) {
                        "<BLOB>"
                    } else {
                        val preview = bytes.take(48).joinToString("") { "%02x".format(it.toInt() and 0xff) }
                        "<BLOB ${bytes.size} B> $preview${if (bytes.size > 48) "…" else ""}"
                    }
                    CellValue(text, CellKind.BLOB)
                }
                else -> {
                    val raw = cursor.getString(index).orEmpty()
                    val normalized = raw
                        .replace("\r\n", "↵")
                        .replace('\n', '↵')
                        .replace('\t', ' ')
                    val text = if (normalized.length > MAX_CELL_CHARS) {
                        normalized.take(MAX_CELL_CHARS) + "…"
                    } else {
                        normalized
                    }
                    CellValue(text, CellKind.TEXT)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "read cell $index failed", t)
            CellValue("<读取失败>", CellKind.BLOB)
        }
    }

    private fun quoteIdent(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""

    private fun Cursor.getStringOrNull(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun cacheDir(context: Context, db: DbFile): File {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(db.absolutePath.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(context.cacheDir, "db/$digest")
    }

    private fun hasSqliteHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(16)
                if (input.read(header) != 16) return@use false
                String(header, Charsets.ISO_8859_1) == "SQLite format 3\u0000"
            }
        }.getOrDefault(false)
    }
}

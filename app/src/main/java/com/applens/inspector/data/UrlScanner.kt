package com.applens.inspector.data

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

class UrlScanException(message: String) : Exception(message)

/**
 * Static APK scanner. It finds URLs that are hard-coded in DEX, resources,
 * assets and native libraries. This is deliberately a static pass: URLs built
 * at runtime, decrypted, or loaded from a server cannot be discovered this way.
 */
object UrlScanner {

    private const val TAG = "AppLensScan"
    private const val CHUNK_SIZE = 1024 * 1024
    private const val CARRY_SIZE = 512
    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    private const val PROGRESS_INTERVAL_MS = 90L

    private val urlRegex = Regex("""(?i)\b(?:https?|wss?)://[^\s"'<>\\\u0000-\u001f]+""")
    private val trailingPunctuation = charArrayOf(
        '.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'',
    )
    private val resultCache = object : LinkedHashMap<String, ScanResult>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ScanResult>?): Boolean =
            size > 5
    }

    fun cached(packageName: String): ScanResult? = resultCache[packageName]

    fun invalidate(packageName: String? = null) {
        if (packageName == null) resultCache.clear() else resultCache.remove(packageName)
    }

    private val ignoredHosts = listOf(
        "schemas.android.com",
        "w3.org",
        "xmlpull.org",
        "java.sun.com",
        "apache.org",
        "json-schema.org",
        "ns.adobe.com",
        "purl.org",
        "xml.org",
        "openxmlformats.org",
        "ietf.org",
        "rfc-editor.org",
    )
    private val binaryExtensions = setOf(
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "ico", "svg",
        "ttf", "otf", "woff", "woff2", "mp3", "mp4", "m4a", "aac",
        "ogg", "wav", "webm", "mkv", "zip", "jar", "apk", "aar",
        "so", "dex", "arsc", "dat", "bin", "pak", "obb",
    )
    private val textExtensions = setOf(
        "json", "xml", "html", "htm", "js", "mjs", "txt", "properties",
        "conf", "cfg", "ini", "yaml", "yml", "pb", "proto", "csv", "tsv",
        "map", "graphql", "gql",
    )

    private class MutableHit(
        val url: String,
        val host: String,
        val scheme: String,
    ) {
        val sources = LinkedHashSet<String>()
        var occurrences = 0
    }

    suspend fun scan(
        context: Context,
        app: AppEntry,
        force: Boolean = false,
        onProgress: (ScanProgress) -> Unit,
    ): ScanResult = withContext(Dispatchers.IO) {
        if (!force) {
            resultCache[app.packageName]?.let { cached ->
                onProgress(ScanProgress(1f, "读取缓存", "${cached.urls.size} 个 URL"))
                return@withContext cached
            }
        }
        val startedAt = System.currentTimeMillis()
        val packageManager = context.packageManager
        val applicationInfo = try {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(app.packageName, 0)
        } catch (t: Throwable) {
            throw UrlScanException("无法读取应用信息：${t.message ?: "未知错误"}")
        }

        val apkPaths = buildList {
            applicationInfo.sourceDir?.let { add(it) }
            applicationInfo.splitSourceDirs?.forEach { if (it.isNotBlank()) add(it) }
        }.distinct()
        if (apkPaths.isEmpty()) throw UrlScanException("该应用没有可读取的 APK 文件")

        val hits = LinkedHashMap<String, MutableHit>()
        val warnings = LinkedHashSet<String>()
        var filesScanned = 0
        var bytesScanned = 0L
        var readAnyApk = false
        var lastProgressAt = 0L

        fun emit(progress: ScanProgress, force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (force || now - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                lastProgressAt = now
                onProgress(progress)
            }
        }

        apkPaths.forEachIndexed { apkIndex, apkPath ->
            currentCoroutineContext().ensureActive()
            emit(
                ScanProgress(
                    fraction = apkIndex.toFloat() / apkPaths.size,
                    title = "正在读取 ${app.label}",
                    detail = apkName(apkPath),
                ),
                force = true,
            )

            var temporary: File? = null
            var zip: ZipFile? = null
            try {
                val direct = File(apkPath)
                zip = runCatching { ZipFile(direct) }.getOrNull()
                if (zip == null) {
                    val temp = File(context.cacheDir, "scan_${System.nanoTime()}.apk")
                    if (Shell.copyToFile(apkPath, temp, timeoutMs = 300_000)) {
                        temporary = temp
                        zip = runCatching { ZipFile(temp) }.getOrNull()
                    }
                }
                if (zip == null) {
                    warnings += "无法读取 ${apkName(apkPath)}"
                    return@forEachIndexed
                }
                readAnyApk = true

                val entries = buildList {
                    val enumeration = zip.entries()
                    while (enumeration.hasMoreElements()) {
                        val entry = enumeration.nextElement()
                        if (!entry.isDirectory && entry.size > 0 && isRelevant(entry.name)) {
                            add(entry)
                        }
                    }
                }
                val zipTotal = entries.sumOf { it.size.coerceAtLeast(0) }.coerceAtLeast(1)
                var zipDone = 0L

                entries.forEachIndexed { entryIndex, entry ->
                    currentCoroutineContext().ensureActive()
                    if (entry.size > MAX_ENTRY_BYTES) {
                        warnings += "${entry.name} 超过 64 MB，已跳过"
                        return@forEachIndexed
                    }
                    val source = buildSourceName(apkIndex, entry.name)
                    val isDex = entry.name.startsWith("classes") && entry.name.endsWith(".dex")
                    val scanUtf16 = !isDex && (
                        entry.name == "resources.arsc" ||
                            entry.name == "AndroidManifest.xml"
                        )
                    try {
                        zip.getInputStream(entry).use { input ->
                            bytesScanned += scanStream(input, source, isDex, scanUtf16, hits)
                        }
                        filesScanned++
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        Log.w(TAG, "entry failed: ${entry.name}", t)
                        warnings += "读取失败：${entry.name}"
                    }
                    zipDone += entry.size
                    emit(
                        ScanProgress(
                            fraction = (apkIndex + (zipDone.toFloat() / zipTotal)) / apkPaths.size,
                            title = "正在扫描 ${app.label}",
                            detail = "$source · ${entryIndex + 1}/${entries.size}",
                        ),
                    )
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.w(TAG, "apk failed: $apkPath", t)
                warnings += "APK 读取失败：${apkName(apkPath)}"
            } finally {
                runCatching { zip?.close() }
                temporary?.delete()
            }
        }

        if (!readAnyApk) {
            throw UrlScanException(
                "无法读取该应用的 APK。静态扫描需要访问 APK 文件，请确认 Shizuku 可用后重试。",
            )
        }

        val urls = hits.values
            .sortedWith(compareBy({ it.host }, { it.url }))
            .map { hit ->
                UrlHit(
                    url = hit.url,
                    host = hit.host,
                    scheme = hit.scheme,
                    sources = hit.sources.take(6).toList(),
                    sourceCount = hit.sources.size,
                    occurrences = hit.occurrences,
                )
            }
        val hosts = hits.values
            .groupBy { it.host }
            .map { (host, group) ->
                HostHit(
                    host = host,
                    count = group.size,
                    sample = group.first().url,
                )
            }
            .sortedWith(compareByDescending<HostHit> { it.count }.thenBy { it.host })

        val apkBytes = apkPaths.sumOf { path ->
            runCatching { File(path).length() }.getOrDefault(0L)
        }
        emit(
            ScanProgress(1f, "扫描完成", "${urls.size} 个 URL"),
            force = true,
        )
        val result = ScanResult(
            urls = urls,
            hosts = hosts,
            filesScanned = filesScanned,
            bytesScanned = bytesScanned,
            apkBytes = apkBytes,
            durationMs = System.currentTimeMillis() - startedAt,
            warnings = warnings.take(8),
        )
        resultCache[app.packageName] = result
        result
    }

    private fun scanStream(
        input: InputStream,
        source: String,
        isDex: Boolean,
        scanUtf16: Boolean,
        hits: MutableMap<String, MutableHit>,
    ): Long {
        val buffer = ByteArray(CHUNK_SIZE + CARRY_SIZE)
        var carry = 0
        var total = 0L
        while (true) {
            val read = input.read(buffer, carry, CHUNK_SIZE)
            if (read <= 0) break
            total += read
            val length = carry + read
            extractFromText(
                String(buffer, 0, length, Charsets.ISO_8859_1),
                source,
                hits,
            )
            if (scanUtf16 && !isDex) {
                extractFromText(
                    String(buffer, 0, length - (length % 2), Charsets.UTF_16LE),
                    source,
                    hits,
                )
            }
            carry = minOf(CARRY_SIZE, length)
            System.arraycopy(buffer, length - carry, buffer, 0, carry)
        }
        return total
    }

    private fun extractFromText(
        text: String,
        source: String,
        hits: MutableMap<String, MutableHit>,
    ) {
        urlRegex.findAll(text).forEach { match ->
            val url = normalizeUrl(match.value) ?: return@forEach
            val host = hostOf(url) ?: return@forEach
            if (isIgnoredHost(host)) return@forEach
            val scheme = url.substringBefore("://").lowercase()
            val existing = hits[url]
            if (existing == null) {
                val hit = MutableHit(url, host, scheme)
                hit.sources += source
                hit.occurrences = 1
                hits[url] = hit
            } else {
                existing.occurrences++
                if (existing.sources.size < 32) existing.sources += source
            }
        }
    }

    private fun normalizeUrl(raw: String): String? {
        var value = raw
        while (value.isNotEmpty() && value.last() in trailingPunctuation) {
            value = value.dropLast(1)
        }
        if (value.length !in 8..2048) return null
        if (!value.contains("://")) return null
        val rest = value.substringAfter("://", "")
        if (rest.isBlank() || rest.startsWith("/")) return null
        return value
    }

    private fun hostOf(url: String): String? {
        val rest = url.substringAfter("://", "")
        if (rest.isBlank()) return null
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        val afterUser = authority.substringAfterLast('@')
        val host = if (afterUser.startsWith("[")) {
            afterUser.substringBefore("]") + "]"
        } else {
            afterUser.substringBefore(':')
        }
        if (host.isBlank() || host.length > 253) return null
        if (host.contains('%') || host.endsWith('.')) return null
        val isIpv6 = host.startsWith('[')
        if (!isIpv6 && !host.contains('.') && !host.equals("localhost", true)) return null
        return host.lowercase()
    }

    private fun isIgnoredHost(host: String): Boolean =
        ignoredHosts.any { ignored -> host == ignored || host.endsWith(".$ignored") }

    private fun isRelevant(name: String): Boolean {
        val lower = name.lowercase()
        if (lower.startsWith("classes") && lower.endsWith(".dex")) return true
        if (lower == "resources.arsc" || lower == "androidmanifest.xml") return true
        if (lower.startsWith("lib/") && lower.endsWith(".so")) return true
        val extension = lower.substringAfterLast('.', "")
        if (extension in binaryExtensions) return false
        if (lower.startsWith("assets/") || lower.startsWith("res/raw/") || lower.startsWith("res/xml/")) {
            return extension.isEmpty() || extension in textExtensions || extension in setOf("so", "arsc")
        }
        return extension in textExtensions
    }

    private fun buildSourceName(apkIndex: Int, entryName: String): String =
        if (apkIndex == 0) entryName else "split$apkIndex!$entryName"

    private fun apkName(path: String): String = path.substringAfterLast('/')
}

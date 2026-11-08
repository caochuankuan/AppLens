package com.applens.inspector.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.Reader
import java.util.concurrent.TimeUnit
import rikka.shizuku.ShizukuRemoteProcess

/**
 * Runs commands through Shizuku. The server UID determines whether "root"
 * commands actually run as root. We never use Runtime.exec(), because that
 * would run as this app's sandboxed UID.
 */
object Shell {

    private const val TAG = "AppLensShell"

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        val ok: Boolean get() = exitCode == 0
        fun errorMessage(fallback: String): String =
            stderr.trim().lineSequence().firstOrNull { it.isNotBlank() }
                ?: if (exitCode != 0) "$fallback（退出码 $exitCode）" else fallback
    }

    suspend fun accessMode(packageName: String, debuggable: Boolean): DbAccessMode {
        val state = ShizukuBridge.state.value
        if (!state.ready) return DbAccessMode.DENIED
        if (state.uid == 0) return DbAccessMode.ROOT
        if (!debuggable) return DbAccessMode.DENIED
        val probe = exec(listOf("run-as", packageName, "id"), timeoutMs = 5_000)
        return if (probe.ok && probe.stdout.contains("uid=")) {
            DbAccessMode.RUN_AS
        } else {
            DbAccessMode.DENIED
        }
    }

    suspend fun execScript(
        script: String,
        runAsPackage: String? = null,
        timeoutMs: Long = 30_000,
    ): Result {
        val command = if (runAsPackage != null) {
            arrayOf("run-as", runAsPackage, "sh", "-c", script)
        } else {
            arrayOf("sh", "-c", script)
        }
        return execArray(command, timeoutMs)
    }

    suspend fun exec(
        command: List<String>,
        runAsPackage: String? = null,
        timeoutMs: Long = 30_000,
    ): Result {
        val args = if (runAsPackage != null) {
            listOf("run-as", runAsPackage) + command
        } else {
            command
        }
        return execArray(args.toTypedArray(), timeoutMs)
    }

    private suspend fun execArray(command: Array<String>, timeoutMs: Long): Result =
        withContext(Dispatchers.IO) {
            var process: ShizukuRemoteProcess? = null
            try {
                val proc = ShizukuBridge.newProcess(command)
                process = proc
                val errorBuffer = StringBuilder()
                val errorThread = Thread {
                    runCatching {
                        proc.errorStream.bufferedReader().use { reader ->
                            errorBuffer.append(readLimited(reader, 64 * 1024))
                        }
                    }
                }.apply {
                    name = "applens-stderr"
                    isDaemon = true
                    start()
                }

                val stdout = proc.inputStream.bufferedReader().use { reader ->
                    readLimited(reader, 2 * 1024 * 1024)
                }
                val finished = proc.waitForTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                if (!finished) {
                    proc.destroy()
                    return@withContext Result(-1, stdout, "命令执行超时")
                }
                errorThread.join(1_000)
                Result(proc.exitValue(), stdout, errorBuffer.toString())
            } catch (t: Throwable) {
                Log.w(TAG, "exec failed: ${command.joinToString(" ")}", t)
                Result(-1, "", t.message ?: t.javaClass.simpleName)
            } finally {
                runCatching { process?.destroy() }
            }
        }

    /**
     * Streams a remote file to [destination]. Returns true only if the file was
     * copied completely and the remote command exited successfully.
     */
    suspend fun copyToFile(
        remotePath: String,
        destination: File,
        runAsPackage: String? = null,
        timeoutMs: Long = 180_000,
    ): Boolean = withContext(Dispatchers.IO) {
        var process: ShizukuRemoteProcess? = null
        destination.parentFile?.mkdirs()
        try {
            val command = if (runAsPackage != null) {
                arrayOf("run-as", runAsPackage, "cat", remotePath)
            } else {
                arrayOf("cat", remotePath)
            }
            val proc = ShizukuBridge.newProcess(command)
            process = proc
            val errorBuffer = StringBuilder()
            val errorThread = Thread {
                runCatching {
                    proc.errorStream.bufferedReader().use { reader ->
                        errorBuffer.append(readLimited(reader, 16 * 1024))
                    }
                }
            }.apply {
                name = "applens-copy-stderr"
                isDaemon = true
                start()
            }
            proc.inputStream.use { input ->
                destination.outputStream().buffered(128 * 1024).use { output ->
                    input.copyTo(output, 128 * 1024)
                }
            }
            val finished = proc.waitForTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroy()
                destination.delete()
                return@withContext false
            }
            errorThread.join(1_000)
            val ok = proc.exitValue() == 0 && destination.length() > 0
            if (!ok) {
                Log.w(
                    TAG,
                    "copy failed ($remotePath): ${proc.exitValue()} ${errorBuffer.toString().trim()}",
                )
                destination.delete()
            }
            ok
        } catch (t: Throwable) {
            Log.w(TAG, "copy failed: $remotePath", t)
            runCatching { destination.delete() }
            false
        } finally {
            runCatching { process?.destroy() }
        }
    }

    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun readLimited(reader: Reader, limit: Int): String {
        val buffer = CharArray(8 * 1024)
        val out = StringBuilder()
        while (out.length < limit) {
            val read = reader.read(buffer)
            if (read < 0) break
            val remaining = limit - out.length
            out.append(buffer, 0, minOf(read, remaining))
        }
        return out.toString()
    }
}

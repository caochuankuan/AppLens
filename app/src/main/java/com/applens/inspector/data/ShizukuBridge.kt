package com.applens.inspector.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

/**
 * Single place that owns the Shizuku binder lifecycle.
 *
 * Shizuku runs its server with the privileges it was started with. If it was
 * started through root, [State.uid] is 0 and shell commands spawned through
 * [newProcess] run as root, which is what private app databases require.
 */
object ShizukuBridge {

    const val MANAGER_PACKAGE = "moe.shizuku.privileged.api"
    private const val DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
    private const val REQUEST_CODE = 4201
    private const val TAG = "ShizukuBridge"

    data class State(
        val binderAlive: Boolean = false,
        val authorized: Boolean = false,
        val uid: Int = -1,
        val version: Int = 0,
        val managerInstalled: Boolean = false,
        val error: String? = null,
    ) {
        val ready: Boolean get() = binderAlive && authorized
        val isRoot: Boolean get() = ready && uid == 0
        val modeLabel: String
            get() = when {
                !binderAlive -> "服务未运行"
                !authorized -> "未授权"
                uid == 0 -> "Root 模式"
                else -> "Shell 模式"
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var initialized = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refresh()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refresh()
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Shizuku permission denied: $grantResult")
        }
        refresh()
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext

        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        }.onFailure { Log.w(TAG, "Unable to register Shizuku listeners", it) }

        refresh()
    }

    fun refresh() {
        val context = appContext ?: return
        val managerInstalled = isManagerInstalled(context)
        val newState = try {
            val alive = Shizuku.pingBinder()
            if (!alive) {
                State(
                    binderAlive = false,
                    authorized = false,
                    managerInstalled = managerInstalled,
                )
            } else {
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                State(
                    binderAlive = true,
                    authorized = granted,
                    uid = Shizuku.getUid(),
                    version = Shizuku.getVersion(),
                    managerInstalled = managerInstalled,
                )
            }
        } catch (t: Throwable) {
            State(
                managerInstalled = managerInstalled,
                error = t.message ?: t.javaClass.simpleName,
            )
        }
        _state.value = newState
    }

    fun requestPermission() {
        val current = _state.value
        if (!current.binderAlive) {
            refresh()
            return
        }
        runCatching {
            Shizuku.requestPermission(REQUEST_CODE)
        }.onFailure { Log.w(TAG, "requestPermission failed", it) }
    }

    fun openManager(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(MANAGER_PACKAGE)
        val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun isManagerInstalled(context: Context): Boolean {
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(MANAGER_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    /**
     * Spawns a process through Shizuku. The process inherits the server UID:
     * root (0) if Shizuku was started by root, otherwise shell (2000).
     */
    @Suppress("DEPRECATION")
    fun newProcess(command: Array<String>): ShizukuRemoteProcess {
        val current = _state.value
        check(current.ready) { "Shizuku 未授权" }
        return Shizuku.newProcess(command, null, null)
    }

    fun postRefresh() {
        mainHandler.post { refresh() }
    }
}

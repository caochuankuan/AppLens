package com.applens.inspector.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

object AppRepository {

    @Volatile
    private var cached: List<AppEntry>? = null

    suspend fun loadInstalledApps(
        context: Context,
        force: Boolean = false,
    ): List<AppEntry> = withContext(Dispatchers.IO) {
        if (!force) {
            cached?.let { return@withContext it }
        }
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val entries = ArrayList<AppEntry>(installed.size)
        for (info in installed) {
            val packageName = info.packageName ?: continue
            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrDefault(packageName)
                .ifBlank { packageName }
            val versionName = runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0).versionName
            }.getOrNull()
            val isSystem = (info.flags and (
                ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                )) != 0
            val debuggable = (info.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            entries += AppEntry(
                packageName = packageName,
                label = label,
                uid = info.uid,
                isSystem = isSystem,
                isDebuggable = debuggable,
                targetSdk = info.targetSdkVersion,
                versionName = versionName,
            )
        }
        val collator = Collator.getInstance(Locale.getDefault())
        entries.sortWith { a, b ->
            val byLabel = collator.compare(a.label, b.label)
            if (byLabel != 0) byLabel else a.packageName.compareTo(b.packageName)
        }
        cached = entries
        entries
    }

    fun invalidate() {
        cached = null
    }
}

package com.applens.inspector.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * MIUI / HyperOS adds a runtime permission on top of QUERY_ALL_PACKAGES.
 * Without it PackageManager returns only the calling app.
 */
object AppListPermission {
    const val PERMISSION = "com.android.permission.GET_INSTALLED_APPS"

    fun isSupported(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPermissionInfo(PERMISSION, 0).packageName ==
                "com.lbe.security.miui"
        }.getOrDefault(false)
    }

    fun isGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }
}

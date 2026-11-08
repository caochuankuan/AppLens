package com.applens.inspector.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.applens.inspector.data.AppEntry
import com.applens.inspector.data.DbFile
import com.applens.inspector.data.TableInfo

enum class FeatureMode(val title: String, val shortTitle: String) {
    DATABASE("数据库浏览器", "数据库"),
    URL_SCAN("接口雷达", "URL 扫描"),
}

sealed interface Screen {
    val key: String

    data object Home : Screen {
        override val key: String = "home"
    }

    data class Apps(val mode: FeatureMode) : Screen {
        override val key: String = "apps:${mode.name}"
    }

    data class Databases(val app: AppEntry) : Screen {
        override val key: String = "databases:${app.packageName}"
    }

    data class Tables(val app: AppEntry, val db: DbFile) : Screen {
        override val key: String =
            "tables:${app.packageName}:${db.absolutePath.hashCode()}"
    }

    data class TableData(
        val app: AppEntry,
        val db: DbFile,
        val table: TableInfo,
    ) : Screen {
        override val key: String =
            "table:${app.packageName}:${db.absolutePath.hashCode()}:${table.name.hashCode()}"
    }

    data class Scan(val app: AppEntry) : Screen {
        override val key: String = "scan:${app.packageName}"
    }
}

class AppNavigator {
    private val stack = mutableStateListOf<Screen>(Screen.Home)
    val canGoBack: Boolean get() = stack.size > 1
    val current: Screen get() = stack.last()

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

class MainViewModel : ViewModel() {
    val navigator = AppNavigator()
}

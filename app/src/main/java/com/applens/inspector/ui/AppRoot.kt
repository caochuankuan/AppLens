package com.applens.inspector.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.applens.inspector.ui.screens.AppsScreen
import com.applens.inspector.ui.screens.DatabasesScreen
import com.applens.inspector.ui.screens.HomeScreen
import com.applens.inspector.ui.screens.ScanScreen
import com.applens.inspector.ui.screens.TableDataScreen
import com.applens.inspector.ui.screens.TablesScreen

@Composable
fun AppRoot() {
    val viewModel: MainViewModel = viewModel()
    val navigator = viewModel.navigator
    val screen = navigator.current
    val snackbar = remember { SnackbarHostState() }
    val stateHolder = rememberSaveableStateHolder()

    BackHandler(enabled = navigator.canGoBack) {
        navigator.pop()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        CompositionLocalProvider(LocalSnackbarHost provides snackbar) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(130))
                },
                label = "screen",
            ) { target ->
                stateHolder.SaveableStateProvider(target.key) {
                    when (target) {
                        is Screen.Home -> HomeScreen(
                            onOpenFeature = { mode ->
                                navigator.push(Screen.Apps(mode))
                            },
                        )

                        is Screen.Apps -> AppsScreen(
                            mode = target.mode,
                            onBack = { navigator.pop() },
                            onSelect = { app ->
                                when (target.mode) {
                                    FeatureMode.DATABASE ->
                                        navigator.push(Screen.Databases(app))

                                    FeatureMode.URL_SCAN ->
                                        navigator.push(Screen.Scan(app))
                                }
                            },
                        )

                        is Screen.Databases -> DatabasesScreen(
                            app = target.app,
                            onBack = { navigator.pop() },
                            onOpenDatabase = { db ->
                                navigator.push(Screen.Tables(target.app, db))
                            },
                        )

                        is Screen.Tables -> TablesScreen(
                            app = target.app,
                            db = target.db,
                            onBack = { navigator.pop() },
                            onOpenTable = { table ->
                                navigator.push(Screen.TableData(target.app, target.db, table))
                            },
                        )

                        is Screen.TableData -> TableDataScreen(
                            app = target.app,
                            db = target.db,
                            table = target.table,
                            onBack = { navigator.pop() },
                        )

                        is Screen.Scan -> ScanScreen(
                            app = target.app,
                            onBack = { navigator.pop() },
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

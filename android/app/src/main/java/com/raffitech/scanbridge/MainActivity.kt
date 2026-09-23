package com.raffitech.scanbridge

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.raffitech.scanbridge.model.ConnectionState
import com.raffitech.scanbridge.ui.screens.BarcodeScreen
import com.raffitech.scanbridge.ui.screens.MethodScreen
import com.raffitech.scanbridge.ui.screens.ReadyScreen
import com.raffitech.scanbridge.ui.screens.UsbScreen
import com.raffitech.scanbridge.ui.screens.WifiScreen
import com.raffitech.scanbridge.ui.theme.ScanBridgeTheme
import com.raffitech.scanbridge.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false

        setContent {
            ScanBridgeTheme {
                val vm: MainViewModel = viewModel()
                val nav = rememberNavController()
                val connection by vm.connection.collectAsStateWithLifecycle()
                val current by nav.currentBackStackEntryAsState()
                LaunchedEffect(connection, current?.destination?.route) {
                    if (connection is ConnectionState.Connected && current?.destination?.route !in listOf("ready", "scanner")) {
                        nav.navigate("ready") {
                            popUpTo("method") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                NavHost(navController = nav, startDestination = "method") {
                    composable("method") {
                        MethodScreen(
                            onUsb = { nav.navigate("usb") },
                            onWifi = { nav.navigate("wifi") },
                            onHelp = { nav.navigate("help") },
                        )
                    }
                    composable("wifi") { WifiScreen(vm, onBack = { vm.disconnect(); nav.popBackStack() }) }
                    composable("usb") { UsbScreen(vm, onBack = { vm.disconnect(); nav.popBackStack() }) }
                    composable("ready") {
                        ReadyScreen(vm,
                            onScan = { vm.beginScanning(); nav.navigate("scanner") },
                            onDisconnect = {
                                vm.disconnect()
                                nav.navigate("method") { popUpTo("ready") { inclusive = true } }
                            },
                        )
                    }
                    composable("scanner") { BarcodeScreen(vm, onBack = { nav.popBackStack() }) }
                    composable("help") { com.raffitech.scanbridge.ui.screens.HelpScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}

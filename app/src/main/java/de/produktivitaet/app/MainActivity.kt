package de.produktivitaet.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        setContent {
            ProduktivitaetTheme {
                App()
            }
        }
    }
}

@Composable
private fun App(vm: MainViewModel = viewModel()) {
    // Zurück-Taste/-Geste des Handys: eine Ebene zurück; auf der Startseite schließt sie die App.
    BackHandler(enabled = vm.canGoBack) { vm.back() }

    Surface(Modifier.fillMaxSize(), color = Black, contentColor = White) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (val screen = vm.screen) {
                Screen.Home -> HomeScreen(vm)
                is Screen.Draw -> DrawScreen(vm, screen.taskId)
                Screen.Add -> AddScreen(vm)
                Screen.Editor -> EditorScreen(vm)
                Screen.Overview -> OverviewScreen(vm)
            }
        }
    }
}

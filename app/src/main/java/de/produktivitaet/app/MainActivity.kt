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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
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

    override fun onResume() {
        super.onResume()
        // Fällige "Automatisch hinzufügen"-Vorlagen beim Zurückkehren in die App anlegen.
        ViewModelProvider(this)[MainViewModel::class.java].store.runAutoAdd()
    }
}

@Composable
private fun App(vm: MainViewModel = viewModel()) {
    // Zurück-Taste/-Geste des Handys: eine Ebene zurück bzw. Pilz-Modus beenden;
    // auf der Startseite (ohne Pilz-Modus) schließt sie die App.
    BackHandler(enabled = vm.canHandleBack) { vm.onBackPressed() }

    CompositionLocalProvider(LocalAppViewModel provides vm) {
        Surface(Modifier.fillMaxSize(), color = Black, contentColor = White) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (vm.confirmDiscard) {
                    ConfirmDialog(
                        title = "Änderungen verwerfen?",
                        text = "Alles, was du seit Beginn des Pilz-Modus verändert hast, wird rückgängig gemacht. " +
                            "Zum Übernehmen tippe auf den Haken.",
                        confirmText = "Verwerfen",
                        onConfirm = vm::discardDecor,
                        onDismiss = { vm.confirmDiscard = false },
                    )
                }
                val screen = vm.screen
                val window = windowId(screen)
                if (window != null) {
                    key(window) {
                        DecoratedWindow(vm, window) { ScreenContent(vm, screen) }
                    }
                } else {
                    ScreenContent(vm, screen)
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(vm: MainViewModel, screen: Screen) {
    when (screen) {
        Screen.Home -> HomeScreen(vm)
        is Screen.Draw -> DrawScreen(vm, screen.taskId)
        Screen.Editor -> EditorScreen(vm)
        Screen.Overview -> OverviewScreen(vm)
        is Screen.PoolView -> PoolScreen(vm, screen.poolId)
        Screen.Fungarium -> FungariumScreen(vm)
        is Screen.Celebration -> CelebrationScreen(vm, screen.reward)
        Screen.Settings -> SettingsScreen(vm)
        Screen.Statistics -> StatisticsScreen(vm)
    }
}

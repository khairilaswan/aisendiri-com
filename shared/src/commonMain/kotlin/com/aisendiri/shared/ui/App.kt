package com.aisendiri.shared.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.aisendiri.shared.i18n.LocalStrings

@Composable
fun App(controller: AppController) {
    val settings by controller.settings.collectAsState()
    AiSendiriTheme(settings.theme) {
        CompositionLocalProvider(LocalStrings provides controller.strings(settings)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (val screen = controller.current) {
                    Screen.Welcome -> WelcomeScreen(controller)
                    Screen.CheckPhone -> CheckPhoneScreen(controller)
                    Screen.Recommend -> RecommendScreen(controller)
                    is Screen.Download -> DownloadScreen(controller, screen.model)
                    is Screen.FirstRun -> FirstRunScreen(controller, screen.model)
                    Screen.Home -> HomeScreen(controller)
                    is Screen.Chat -> ChatScreen(controller)
                    Screen.Talk -> TalkScreen(controller)
                    Screen.History -> HistoryScreen(controller)
                    Screen.Settings -> SettingsScreen(controller)
                    Screen.Models -> ModelsScreen(controller)
                    Screen.Advanced -> AdvancedScreen(controller)
                }
            }
        }
    }
}

package es.dan15d.grimorio.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

actual fun initialRoute(): Route = Route.Start

/** El «atrás» del sistema cierra la hoja de arriba o vuelve a la pantalla anterior. */
@Composable
actual fun PlatformHistory(nav: Navigator) {
    BackHandler(enabled = nav.canGoBack || nav.current != Route.Start) { nav.handleBack() }
}

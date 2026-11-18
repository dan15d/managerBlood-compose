package es.dan15d.grimorio

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    // Ventana con proporción de móvil: la app se diseña primero para el móvil.
    Window(onCloseRequest = ::exitApplication, title = "El Grimorio", state = rememberWindowState(width = 420.dp, height = 860.dp)) {
        App()
    }
}

package es.dan15d.grimorio.presentation.platform

import androidx.compose.runtime.Composable
import java.awt.Desktop
import java.awt.Toolkit
import java.net.URI

actual fun ring(sound: Boolean) {
    if (sound) runCatching { repeat(3) { Toolkit.getDefaultToolkit().beep(); Thread.sleep(250) } }
}

/** En escritorio la pantalla no se apaga sola con la app delante. */
@Composable
actual fun KeepScreenOn() = Unit

actual fun appUrl(): String = PUBLISHED_URL

/** Sin menú de compartir en escritorio: quien llama lo copia al portapapeles. */
actual suspend fun shareText(title: String, text: String, url: String?): Boolean = false

actual fun openUrl(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

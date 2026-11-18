package es.dan15d.grimorio.presentation.platform

import androidx.compose.runtime.Composable

// Lo que cada plataforma hace a su manera (vibrar, mantener la pantalla encendida, compartir…).

/** Avisa de que se ha acabado el tiempo: vibración y, si se pide, tres pitidos cortos. */
expect fun ring(sound: Boolean)

/** Mantiene la pantalla encendida mientras esté en pantalla (el Grimorio durante la partida). */
@Composable
expect fun KeepScreenOn()

/**
 * Dirección de la app para enlaces y QR de «unirse» (en la web, la URL donde se está sirviendo;
 * en Android y escritorio, la web publicada).
 */
expect fun appUrl(): String

/** Comparte un texto con el menú del sistema; false si no hay forma (entonces se copia). */
expect suspend fun shareText(title: String, text: String, url: String?): Boolean

/** Abre un enlace en el navegador. */
expect fun openUrl(url: String)

/** La web publicada (GitHub Pages), para enlaces desde Android y escritorio. */
const val PUBLISHED_URL = "https://dan15d.github.io/managerBlood-compose/"

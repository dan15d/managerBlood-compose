package es.dan15d.grimorio.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.window
import org.w3c.dom.events.Event

// En la web la ruta vive en el # de la URL (como en la versión React): se puede recargar, compartir
// el enlace de unirse y usar atrás/adelante del navegador. Las hojas abiertas se cierran con
// «atrás» sin cambiar de pantalla.

actual fun initialRoute(): Route = Route.parse(window.location.hash)

@Composable
actual fun PlatformHistory(nav: Navigator) {
    DisposableEffect(nav) {
        var ignoreNextPop = false
        nav.onRouteChange = { route, replace ->
            val url = "#" + route.path
            if (replace) window.history.replaceState(null, "", url) else window.history.pushState(null, "", url)
        }
        nav.platformBack = {
            when {
                // Las capas se cierran sin tocar el historial.
                nav.hasOverlay -> nav.closeTopOverlay()
                nav.stack.size > 1 -> {
                    window.history.back()
                    true
                }
                else -> false
            }
        }
        val onPop: (Event) -> Unit = {
            if (ignoreNextPop) {
                ignoreNextPop = false
            } else if (nav.hasOverlay) {
                // «Atrás» con una hoja abierta: se cierra la hoja y la URL vuelve a la pantalla actual.
                nav.closeTopOverlay()
                ignoreNextPop = false
                window.history.pushState(null, "", "#" + nav.current.path)
            } else {
                nav.syncTo(Route.parse(window.location.hash))
            }
        }
        window.addEventListener("popstate", onPop)
        onDispose {
            window.removeEventListener("popstate", onPop)
            nav.onRouteChange = null
            nav.platformBack = null
        }
    }
}

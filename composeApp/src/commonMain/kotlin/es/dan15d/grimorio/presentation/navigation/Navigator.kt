package es.dan15d.grimorio.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

// Rutas de la app: las mismas que la web con react-router (con # para GitHub Pages), así los
// enlaces y QR para unirse que ya se han compartido siguen valiendo.

sealed interface Route {
    val path: String

    data object Start : Route { override val path = "/" }
    data object AdminLogin : Route { override val path = "/narrador" }
    data class Join(val code: String? = null) : Route { override val path = if (code == null) "/unirse" else "/unirse/$code" }
    data class Play(val gameId: String, val seatId: String) : Route { override val path = "/jugar/$gameId/$seatId" }
    data object NewGame : Route { override val path = "/nueva" }
    data class GameRoute(val id: String) : Route { override val path = "/partida/$id" }
    data class Reveal(val id: String) : Route { override val path = "/partida/$id/revelar" }
    data object Scripts : Route { override val path = "/guiones" }
    data class ScriptEditor(val id: String? = null) : Route { override val path = if (id == null) "/guiones/nuevo" else "/guiones/$id" }
    data class ScriptView(val id: String) : Route { override val path = "/guiones/$id/ver" }
    data object Settings : Route { override val path = "/ajustes" }

    /** Pantallas del Narrador: sin la contraseña se vuelve a la portada. */
    val needsAdmin: Boolean
        get() = when (this) {
            Start, AdminLogin, is Join, is Play -> false
            else -> true
        }

    /** Pantallas de flujo (asistente, partida, editor): sin barra inferior, con sus propios botones. */
    val isFlow: Boolean
        get() = this is NewGame || this is GameRoute || this is Reveal || this is ScriptEditor || this is ScriptView

    /** Pantallas de jugador o de entrar: nunca enseñan la barra del Narrador. */
    val isPublic: Boolean get() = this is Join || this is Play || this is AdminLogin

    companion object {
        /** De «/partida/abc» (o «#/partida/abc») a la ruta; lo desconocido va a la portada. */
        fun parse(raw: String): Route {
            val parts = raw.removePrefix("#").substringBefore('?').trim('/').split('/').filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> Start
                parts == listOf("narrador") -> AdminLogin
                parts == listOf("unirse") -> Join()
                parts.size == 2 && parts[0] == "unirse" -> Join(parts[1])
                parts.size == 3 && parts[0] == "jugar" -> Play(parts[1], parts[2])
                parts == listOf("nueva") -> NewGame
                parts.size == 2 && parts[0] == "partida" -> GameRoute(parts[1])
                parts.size == 3 && parts[0] == "partida" && parts[2] == "revelar" -> Reveal(parts[1])
                parts == listOf("guiones") -> Scripts
                parts == listOf("guiones", "nuevo") -> ScriptEditor()
                parts.size == 2 && parts[0] == "guiones" -> ScriptEditor(parts[1])
                parts.size == 3 && parts[0] == "guiones" && parts[2] == "ver" -> ScriptView(parts[1])
                parts == listOf("ajustes") -> Settings
                else -> Start
            }
        }
    }
}

/**
 * Pila de pantallas y de capas (hojas, diálogos). «Atrás» (el del móvil, el del navegador o la
 * flecha de la app) cierra primero la capa de arriba y, si no hay ninguna, vuelve a la pantalla
 * anterior. Cada plataforma conecta su «atrás» en PlatformHistory.
 */
@Stable
class Navigator(initial: Route) {
    val stack = mutableStateListOf(initial)
    private val overlays = mutableStateListOf<() -> Unit>()

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = overlays.isNotEmpty() || stack.size > 1
    val hasOverlay: Boolean get() = overlays.isNotEmpty()

    /** La plataforma escucha para reflejar la ruta (el # de la web). */
    internal var onRouteChange: ((route: Route, replace: Boolean) -> Unit)? = null
    /** En la web, «atrás» pasa por el historial del navegador para que la URL vaya a la par. */
    internal var platformBack: (() -> Boolean)? = null

    fun navigate(route: Route, replace: Boolean = false) {
        if (route == current) return
        if (replace) stack[stack.lastIndex] = route
        else {
            // Ir a una pantalla que ya está en la pila (p. ej. la portada) vuelve a ella.
            val index = stack.indexOf(route)
            if (index >= 0) while (stack.size > index + 1) stack.removeAt(stack.lastIndex) else stack.add(route)
        }
        onRouteChange?.invoke(route, replace)
    }

    /** Vuelve a la portada dejando la pila limpia. */
    fun home() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        if (stack[0] != Route.Start) stack[0] = Route.Start
        onRouteChange?.invoke(Route.Start, true)
    }

    /** El «atrás» de la app (flecha o gesto). Devuelve false si no había adónde volver. */
    fun back(): Boolean {
        if (platformBack?.invoke() == true) return true
        return handleBack()
    }

    /** Lo llama la plataforma cuando el sistema pide «atrás». */
    internal fun handleBack(): Boolean {
        overlays.lastOrNull()?.let { close ->
            close()
            return true
        }
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        if (current != Route.Start) {
            stack[0] = Route.Start
            return true
        }
        return false
    }

    /** La plataforma cambió de ruta por su cuenta (el usuario tocó atrás/adelante en el navegador). */
    internal fun syncTo(route: Route) {
        val index = stack.lastIndexOf(route)
        if (index >= 0) while (stack.size > index + 1) stack.removeAt(stack.lastIndex)
        else stack.add(route)
    }

    internal fun closeTopOverlay(): Boolean {
        val close = overlays.lastOrNull() ?: return false
        close()
        return true
    }

    internal fun register(close: () -> Unit) = overlays.add(close)
    internal fun unregister(close: () -> Unit) = overlays.remove(close)
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Sin Navigator") }

/** Mientras esté en pantalla, «atrás» llama a [onBack] en vez de cambiar de pantalla (hojas, diálogos). */
@Composable
fun BackHandlerLayer(onBack: () -> Unit) {
    val nav = LocalNavigator.current
    val latest = rememberUpdatedState(onBack)
    DisposableEffect(nav) {
        val handler: () -> Unit = { latest.value() }
        nav.register(handler)
        onDispose { nav.unregister(handler) }
    }
}

/** Conecta el «atrás» y la URL de la plataforma con el Navigator. */
@Composable
expect fun PlatformHistory(nav: Navigator)

/** La ruta con que arranca la app (en la web, el # de la URL). */
expect fun initialRoute(): Route

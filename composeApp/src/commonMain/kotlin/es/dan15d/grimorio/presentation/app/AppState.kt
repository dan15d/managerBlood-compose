package es.dan15d.grimorio.presentation.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import es.dan15d.grimorio.application.ports.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Narrador (puede crear y llevar partidas) o invitado (solo puede unirse como jugador). */
enum class Role { Loading, Admin, Guest }

enum class ToastKind { Info, Error }

/** Botón dentro del aviso («Deshacer»): al tocarlo se cierra el aviso. */
data class ToastAction(val label: String, val onClick: () -> Unit)

data class Toast(val id: Long, val message: String, val kind: ToastKind, val action: ToastAction?, val key: String?)

/**
 * Estado de toda la app (lo que en la web era AppContext): el contenedor con los casos de uso,
 * el rol, los ajustes y los avisos.
 */
@Stable
class AppState(private val scope: CoroutineScope) {
    var settings by mutableStateOf(settingsStore.load())
        private set

    /** null mientras se carga el catálogo la primera vez. */
    var app by mutableStateOf<Container?>(null)
        private set

    var role by mutableStateOf(Role.Loading)
        private set

    val toasts = mutableStateListOf<Toast>()
    private var nextToastId = 0L

    fun start() {
        scope.launch { rebuild() }
    }

    private suspend fun rebuild() {
        try {
            app = buildContainer(settings)
            refreshRole()
        } catch (e: Throwable) {
            toast(errorMessage(e), ToastKind.Error)
            role = Role.Guest
        }
    }

    suspend fun refreshRole() {
        val container = app ?: return
        role = try {
            if (container.admin.check()) Role.Admin else Role.Guest
        } catch (_: Throwable) {
            Role.Guest
        }
    }

    fun updateSettings(next: AppSettings) {
        settingsStore.save(next)
        settings = next
        scope.launch { rebuild() }
    }

    fun toast(message: String, kind: ToastKind = ToastKind.Info, action: ToastAction? = null, key: String? = null) {
        val id = nextToastId++
        if (key != null) toasts.removeAll { it.key == key }
        toasts.add(Toast(id, message, kind, action, key))
        // Con botón dura un poco más, para que dé tiempo a tocarlo.
        val millis = if (kind == ToastKind.Error) 6000L else if (action != null) 5000L else 3000L
        scope.launch {
            delay(millis)
            toasts.removeAll { it.id == id }
        }
    }

    fun dismissToast(id: Long) {
        toasts.removeAll { it.id == id }
    }

    /** Lanza una tarea de la interfaz y enseña el error como aviso si falla. */
    fun run(block: suspend () -> Unit) = scope.launch {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            toast(errorMessage(e), ToastKind.Error)
        }
    }
}

val LocalAppState = staticCompositionLocalOf<AppState> { error("Sin AppState") }

/** Mensaje legible de cualquier error para enseñarlo en un aviso. */
fun errorMessage(e: Throwable): String = e.message ?: e.toString()

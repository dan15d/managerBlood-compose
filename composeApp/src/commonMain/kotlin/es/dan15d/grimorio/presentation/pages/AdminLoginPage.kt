package es.dan15d.grimorio.presentation.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.app.Role
import es.dan15d.grimorio.presentation.app.ToastKind
import es.dan15d.grimorio.presentation.app.errorMessage
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.GrimTextField
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.Page
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.theme.GrimType
import kotlinx.coroutines.CancellationException

/** Entrar como Narrador con la contraseña común (la comprueba la base de datos, no la web). */
@Composable
fun AdminLoginPage() {
    val state = LocalAppState.current
    val app = state.app ?: return
    val nav = LocalNavigator.current
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    if (state.role == Role.Admin) {
        LaunchedEffect(Unit) { nav.navigate(Route.Start, replace = true) }
        return
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val submit: () -> Unit = submit@{
        if (busy || password.isEmpty()) return@submit
        busy = true
        state.run {
            try {
                app.admin.login(password)
                state.refreshRole()
                state.toast("Bienvenido, Narrador.")
                nav.navigate(Route.Start, replace = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                state.toast(errorMessage(e), ToastKind.Error)
                password = ""
            } finally {
                busy = false
            }
        }
    }

    Page(flow = true, maxWidth = 440.dp) {
        Text("Modo Narrador", style = GrimType.title)
        Muted("Escribe la contraseña de Narrador. Este dispositivo la recordará.")
        GrimCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GrimTextField(
                    password,
                    { password = it },
                    Modifier.focusRequester(focus),
                    label = "Contraseña",
                    password = true,
                    onDone = submit,
                )
                GrimButton(
                    if (busy) "Comprobando…" else "Entrar",
                    submit,
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.Primary,
                    big = true,
                    enabled = !busy && password.isNotEmpty(),
                )
            }
        }
        GrimButton("Volver", { nav.home() }, kind = ButtonKind.Ghost)
    }
}

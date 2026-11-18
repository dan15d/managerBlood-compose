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
import com.russhwolf.settings.Settings
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.app.ToastKind
import es.dan15d.grimorio.presentation.app.errorMessage
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.GrimTextField
import es.dan15d.grimorio.presentation.components.NoticeBox
import es.dan15d.grimorio.presentation.components.NoticeKind
import es.dan15d.grimorio.presentation.components.Page
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.theme.GrimType
import kotlinx.coroutines.CancellationException

private const val NAME_KEY = "grimorio.playerName"

private fun savedName(): String = try {
    Settings().getStringOrNull(NAME_KEY) ?: ""
} catch (_: Throwable) {
    ""
}

/** Unirse a una partida con el código (o desde el QR, que ya lo trae puesto). */
@Composable
fun JoinPage(code: String?) {
    val state = LocalAppState.current
    val app = state.app ?: return
    val nav = LocalNavigator.current
    var joinCode by remember { mutableStateOf((code ?: "").uppercase()) }
    var name by remember { mutableStateOf(savedName()) }
    var busy by remember { mutableStateOf(false) }
    val codeFocus = remember { FocusRequester() }
    val nameFocus = remember { FocusRequester() }

    val player = app.player
    if (player == null) {
        Page(flow = true, maxWidth = 440.dp) {
            Text("Unirse", style = GrimType.title)
            NoticeBox("Esta instalación no tiene la nube configurada, así que no admite jugadores.", NoticeKind.Info)
        }
        return
    }

    // Con el código ya puesto (desde el QR) se empieza por el nombre.
    LaunchedEffect(Unit) { runCatching { if (joinCode.isEmpty()) codeFocus.requestFocus() else nameFocus.requestFocus() } }

    val ready = !busy && joinCode.length >= 5 && name.isNotBlank()
    val join: () -> Unit = join@{
        if (!ready) return@join
        busy = true
        state.run {
            try {
                val seat = player.join(joinCode, name)
                try {
                    Settings().putString(NAME_KEY, name.trim())
                } catch (_: Throwable) {
                    /* sin almacenamiento */
                }
                nav.navigate(Route.Play(seat.gameId, seat.seatId), replace = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                state.toast(errorMessage(e), ToastKind.Error)
                busy = false
            }
        }
    }

    Page(flow = true, maxWidth = 440.dp) {
        Text("Unirme a una partida", style = GrimType.title)
        GrimCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GrimTextField(
                    joinCode,
                    { joinCode = it.uppercase().filter { c -> c in 'A'..'Z' || c in '0'..'9' }.take(5) },
                    Modifier.focusRequester(codeFocus),
                    label = "Código de la partida",
                    placeholder = "ABCDE",
                    code = true,
                    onDone = { runCatching { nameFocus.requestFocus() } },
                )
                GrimTextField(
                    name,
                    { name = it.take(30) },
                    Modifier.focusRequester(nameFocus),
                    label = "Tu nombre",
                    placeholder = "Como te llaman en la mesa",
                    onDone = join,
                )
                GrimButton(
                    if (busy) "Entrando…" else "Entrar",
                    join,
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.Primary,
                    big = true,
                    enabled = ready,
                )
            }
        }
        GrimButton("Volver", { nav.home() }, kind = ButtonKind.Ghost)
    }
}

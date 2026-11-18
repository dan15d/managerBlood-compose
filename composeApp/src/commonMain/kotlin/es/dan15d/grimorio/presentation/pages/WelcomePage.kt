package es.dan15d.grimorio.presentation.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.dan15d.grimorio.application.ports.SeatRef
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.components.BrandHero
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.Page
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Route
import kotlinx.coroutines.CancellationException

/** Portada para quien no es Narrador: unirse a una partida o entrar como Narrador. */
@Composable
fun WelcomePage() {
    val state = LocalAppState.current
    val app = state.app ?: return
    val nav = LocalNavigator.current
    var current by remember { mutableStateOf<SeatRef?>(null) }

    LaunchedEffect(app) {
        current = try {
            app.player?.current()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    Page(flow = true, maxWidth = 560.dp) {
        BrandHero("Blood on the Clocktower en Ravenswood Bluff. Únete a la partida con el código que te dé el Narrador.") {
            current?.let { seat ->
                GrimButton(
                    "Volver a mi partida",
                    { nav.navigate(Route.Play(seat.gameId, seat.seatId)) },
                    Modifier.fillMaxWidth(),
                    kind = ButtonKind.Gold,
                    big = true,
                )
            }
            GrimButton(
                "Unirme a una partida",
                { nav.navigate(Route.Join()) },
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Primary,
                big = true,
            )
            GrimButton("Soy el Narrador", { nav.navigate(Route.AdminLogin) }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
        }
    }
}

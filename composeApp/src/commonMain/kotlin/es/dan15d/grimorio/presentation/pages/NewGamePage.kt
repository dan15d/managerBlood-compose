package es.dan15d.grimorio.presentation.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.dan15d.grimorio.domain.entities.GameMode
import es.dan15d.grimorio.domain.entities.Script
import es.dan15d.grimorio.domain.services.defaultFabled
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.app.ToastKind
import es.dan15d.grimorio.presentation.app.errorMessage
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.Page
import es.dan15d.grimorio.presentation.components.rememberAsync
import es.dan15d.grimorio.presentation.navigation.BackHandlerLayer
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.pages.newgame.CharactersStep
import es.dan15d.grimorio.presentation.pages.newgame.DealStep
import es.dan15d.grimorio.presentation.pages.newgame.LobbyStep
import es.dan15d.grimorio.presentation.pages.newgame.ModeStep
import es.dan15d.grimorio.presentation.pages.newgame.NewGameStart
import es.dan15d.grimorio.presentation.pages.newgame.NewGameState
import es.dan15d.grimorio.presentation.pages.newgame.PlayersStep
import es.dan15d.grimorio.presentation.pages.newgame.STEPS
import es.dan15d.grimorio.presentation.pages.newgame.ScriptStep
import es.dan15d.grimorio.presentation.pages.newgame.StepsHeader
import es.dan15d.grimorio.presentation.pages.newgame.WizardFooter
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Asistente de nueva partida: modo, guion, jugadores (o la sala, con la nube), personajes y
 * reparto. Para abrirlo con un guion ya elegido o retomando una sala: `nav.newGame(…)`.
 */
@Composable
fun NewGamePage() {
    val ui = LocalAppState.current
    val app = ui.app ?: return
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val request = remember { NewGameStart.take() }
    val state = remember { NewGameState(app, ui) }
    val scripts = rememberAsync(app) { app.listScripts.execute() }

    // Lo que en la web eran useEffect: se escuchan los cambios sin recomponer la página entera.
    LaunchedEffect(state) {
        // Al cambiar de guion: sus Fábulas y Lóricos, y el Djinn si tiene incompatibilidades.
        launch { snapshotFlow { state.pool }.collect { state.fabled = defaultFabled(it, app.catalog.jinxes()) } }
        // Si quedan menos jugadores, sobran Viajeros: la bolsa nunca baja de 5.
        launch { snapshotFlow { state.playerCount to state.travellers.size }.collect { state.trimTravellers() } }
        // Cada vez que cambia la bolsa, se completan los engaños (Borracho…) y los faroles.
        launch { snapshotFlow { state.script to state.selected }.collect { state.completeSelection() } }
        // En la sala, los asientos llegan en tiempo real.
        launch {
            snapshotFlow { state.lobby?.gameId }.collectLatest { gameId ->
                val manage = app.lobby
                if (gameId != null && manage != null) {
                    manage.watch(gameId)
                        .catch { e -> ui.toast(errorMessage(e), ToastKind.Error) }
                        .collect { state.onSeats(it) }
                }
            }
        }
    }

    // Retomar una sala abierta desde la portada.
    val loaded = scripts.data
    LaunchedEffect(loaded) {
        val id = request.lobbyId
        if (id != null && loaded != null && state.lobby == null) {
            try {
                state.resume(id, loaded)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                ui.toast(errorMessage(e), ToastKind.Error)
            }
        }
    }

    // Guion elegido desde su ficha: al escoger el modo se va directo a la sala.
    val preselected: Script? = loaded?.find { it.id == request.scriptId }
    val chooseMode: (GameMode) -> Unit = { m ->
        state.mode = m
        if (preselected != null && state.lobby == null) scope.launch { state.chooseScript(preselected) } else state.step = 1
    }

    val step = state.step
    // «Atrás» (el del móvil o el del navegador) vuelve al paso anterior antes de salir del asistente.
    if (step > 0) BackHandlerLayer { state.step = state.step - 1 }

    val create: () -> Unit = {
        scope.launch {
            val game = state.create()
            if (game != null) nav.navigate(Route.GameRoute(game.id), replace = true)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 640.dp
        val header: @Composable () -> Unit = { StepsHeader(step, wide) { state.step = it } }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                // Cada paso con su propio scroll: al cambiar de paso se empieza arriba.
                key(step) {
                    if (step == 3 && state.script != null) {
                        CharactersStep(state, header)
                    } else {
                        Page(flow = true) {
                            header()
                            when (step) {
                                0 -> ModeStep(state.mode, app.aiAvailable, wide, chooseMode) { nav.navigate(Route.Settings) }
                                1 -> ScriptStep(
                                    scripts.data,
                                    state.script,
                                    busy = state.openingLobby,
                                    wide = wide,
                                    onChoose = { s -> scope.launch { state.chooseScript(s) } },
                                    onCreateScript = { nav.navigate(Route.ScriptEditor()) },
                                )
                                2 -> if (state.lobby != null) LobbyStep(state) else PlayersStep(state, wide)
                                4 -> DealStep(state, wide)
                            }
                        }
                    }
                }
            }
            WizardFooter {
                GrimButton(
                    if (step > 0) "Atrás" else "Cancelar",
                    { if (step > 0) state.step = step - 1 else nav.navigate(Route.Start) },
                    kind = ButtonKind.Ghost,
                    big = true,
                )
                if (step > 0) {
                    val last = step == STEPS.lastIndex
                    // En el móvil, la acción principal ocupa el resto del ancho; en pantallas anchas, a la derecha.
                    if (wide) Spacer(Modifier.weight(1f))
                    GrimButton(
                        if (last) (if (state.creating) "Preparando…" else "Empezar partida") else "Siguiente",
                        { if (last) create() else state.step = step + 1 },
                        if (wide) Modifier else Modifier.weight(1f),
                        kind = ButtonKind.Primary,
                        enabled = state.canAdvance && !state.creating,
                        big = true,
                    )
                }
            }
        }
    }
}

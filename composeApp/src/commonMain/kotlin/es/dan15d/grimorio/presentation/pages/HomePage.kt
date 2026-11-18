package es.dan15d.grimorio.presentation.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.application.ports.GameSummary
import es.dan15d.grimorio.domain.entities.GameStatus
import es.dan15d.grimorio.domain.entities.Lobby
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.components.BrandHero
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.ConfirmDialog
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.GrimIconButton
import es.dan15d.grimorio.presentation.components.NoticeBox
import es.dan15d.grimorio.presentation.components.NoticeKind
import es.dan15d.grimorio.presentation.components.nowMillis
import es.dan15d.grimorio.presentation.components.parseMillis
import es.dan15d.grimorio.presentation.components.rememberAsync
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.pages.newgame.newGame
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType
import kotlin.math.roundToLong

private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")

/** «18 nov» a partir de los milisegundos (día en UTC: no hay zona horaria sin kotlinx-datetime). */
private fun shortDate(millis: Long): String {
    // Días desde 1970 → fecha civil (algoritmo de Howard Hinnant).
    val z = millis.floorDiv(86_400_000L) + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    return "$day ${MONTHS[(month - 1).toInt()]}"
}

private fun timeAgo(iso: String): String {
    val then = try {
        parseMillis(iso)
    } catch (_: Throwable) {
        return ""
    }
    val minutes = ((nowMillis() - then) / 60_000.0).roundToLong()
    if (minutes < 1) return "ahora mismo"
    if (minutes < 60) return "hace $minutes min"
    val hours = (minutes / 60.0).roundToLong()
    if (hours < 24) return "hace $hours h"
    return shortDate(then)
}

@Composable
fun HomePage() {
    val state = LocalAppState.current
    val app = state.app ?: return
    val nav = LocalNavigator.current
    val games = rememberAsync(app) { app.listGames.execute() }
    // Salas abiertas que aún no han empezado (se pueden retomar o cerrar).
    val lobbies = rememberAsync(app) { app.lobby?.listOpen() ?: emptyList() }
    var deleting by remember { mutableStateOf<GameSummary?>(null) }

    val data = games.data
    val inProgress = remember(data) { data?.filter { it.status == GameStatus.InProgress } ?: emptyList() }
    val finished = remember(data) { data?.filter { it.status == GameStatus.Finished } ?: emptyList() }
    val openLobbies = lobbies.data ?: emptyList()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                BrandHero("Narra Blood on the Clocktower sin perder el hilo: prepara la bolsa, sigue la noche paso a paso y cuenta los votos.") {
                    GrimButton("Nueva partida", { nav.newGame() }, kind = ButtonKind.Primary, big = true)
                }
            }

            games.error?.let { error -> item(key = "error") { NoticeBox(error, NoticeKind.Error) } }

            if (openLobbies.isNotEmpty()) {
                section("lobbies", "Salas esperando jugadores")
                items(openLobbies, key = { "lobby-" + it.gameId }) { lobby ->
                    LobbyRow(
                        lobby,
                        onResume = { nav.newGame(lobbyId = lobby.gameId) },
                        onClose = {
                            state.run {
                                app.lobby!!.close(lobby.gameId)
                                lobbies.reload()
                            }
                        },
                    )
                }
            }

            if (inProgress.isNotEmpty()) {
                section("in-progress", "En curso", top = openLobbies.isNotEmpty())
                items(inProgress, key = { "game-" + it.id }) { game ->
                    GameRow(game, onOpen = { nav.navigate(Route.GameRoute(game.id)) }, onDelete = { deleting = game })
                }
            }

            if (finished.isNotEmpty()) {
                section("finished", "Terminadas", top = openLobbies.isNotEmpty() || inProgress.isNotEmpty())
                items(finished, key = { "game-" + it.id }) { game ->
                    GameRow(game, onOpen = { nav.navigate(Route.GameRoute(game.id)) }, onDelete = { deleting = game })
                }
            }

            if (!games.loading && data.isNullOrEmpty() && games.error == null) {
                item(key = "empty") { EmptyBox("Todavía no hay partidas. Cuando crees una, aparecerá aquí para retomarla.") }
            }
        }
    }

    deleting?.let { game ->
        ConfirmDialog(
            title = "¿Borrar la partida?",
            message = "Se borrará «${game.name}» con todo su Grimorio. No se puede deshacer.",
            confirmLabel = "Borrar",
            danger = true,
            onConfirm = {
                deleting = null
                state.run {
                    app.deleteGame.execute(game.id)
                    state.toast("Partida borrada.")
                    games.reload()
                }
            },
            onCancel = { deleting = null },
        )
    }
}

/** Título de sección (h2), con más aire si va detrás de otra. */
private fun LazyListScope.section(key: String, title: String, top: Boolean = false) {
    item(key = "title-$key") {
        Text(
            title,
            Modifier.padding(top = if (top) 16.dp else 0.dp),
            style = GrimType.heading.copy(color = Grim.textStrong),
        )
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(
        text,
        style = GrimType.body.copy(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, color = Grim.textStrong),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun LobbyRow(lobby: Lobby, onResume: () -> Unit, onClose: () -> Unit) {
    GrimCard(padding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                RowTitle("Código ${lobby.joinCode}")
                Text(lobby.scriptName, style = GrimType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GrimButton("Seguir", onResume, small = true)
            GrimIconButton(Lucide.X, "Cerrar la sala ${lobby.joinCode}", onClose)
        }
    }
}

@Composable
private fun GameRow(game: GameSummary, onOpen: () -> Unit, onDelete: () -> Unit) {
    GrimCard(onClick = onOpen, padding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                RowTitle(game.name)
                Text(
                    "${game.scriptName} · ${game.playerCount} jugadores · ${game.phaseLabel} · ${timeAgo(game.updatedAt)}",
                    style = GrimType.small,
                )
            }
            GrimIconButton(Lucide.Trash2, "Borrar ${game.name}", onDelete)
        }
    }
}

/** Estado vacío con borde discontinuo (como .empty). */
@Composable
private fun EmptyBox(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .drawBehind {
                drawRoundRect(
                    Grim.lineStrong,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .padding(horizontal = 16.dp, vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = GrimType.body.copy(color = Grim.muted, fontSize = 17.sp, textAlign = TextAlign.Center))
    }
}

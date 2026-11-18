package es.dan15d.grimorio.presentation.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.application.ports.CharacterCatalog
import es.dan15d.grimorio.application.usecases.GuideAudience
import es.dan15d.grimorio.domain.entities.Alignment as Side
import es.dan15d.grimorio.domain.entities.PlayerView
import es.dan15d.grimorio.domain.entities.PublicPlayer
import es.dan15d.grimorio.domain.entities.TableStatus
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.ConfirmDialog
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.GrimIconButton
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.NoticeBox
import es.dan15d.grimorio.presentation.components.NoticeKind
import es.dan15d.grimorio.presentation.components.Page
import es.dan15d.grimorio.presentation.components.ScriptCharacterList
import es.dan15d.grimorio.presentation.components.SeatCircle
import es.dan15d.grimorio.presentation.components.SeatName
import es.dan15d.grimorio.presentation.components.Token
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.pages.player.Countdown
import es.dan15d.grimorio.presentation.pages.player.NoteSummary
import es.dan15d.grimorio.presentation.pages.player.NotesSheet
import es.dan15d.grimorio.presentation.pages.player.PageTab
import es.dan15d.grimorio.presentation.pages.player.PageTabs
import es.dan15d.grimorio.presentation.pages.player.PhasePill
import es.dan15d.grimorio.presentation.pages.player.PillKind
import es.dan15d.grimorio.presentation.pages.player.PlayerNotes
import es.dan15d.grimorio.presentation.pages.player.SeatNote
import es.dan15d.grimorio.presentation.pages.player.StatLine
import es.dan15d.grimorio.presentation.pages.player.WinnerBanner
import es.dan15d.grimorio.presentation.pages.player.isNight
import es.dan15d.grimorio.presentation.pages.player.rememberPlayerNotes
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

private enum class PlayerTab { Script, Players }

/** Margen lateral de la página (las pestañas van a todo lo ancho, como .tabs--page). */
private val Gutter = Modifier.padding(horizontal = 16.dp)

/**
 * El móvil de cada jugador: la mesa y el guion, al día sin recargar. Su personaje no: se lo
 * enseña el Narrador en persona, como con la ficha física.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerPage(gameId: String, seatId: String) {
    val state = LocalAppState.current
    val app = state.app ?: return
    val nav = LocalNavigator.current
    // Hasta la primera noticia de la mesa: «Conectando…»; después, null si la partida ya no existe.
    var loaded by remember(gameId, seatId) { mutableStateOf(false) }
    var view by remember(gameId, seatId) { mutableStateOf<PlayerView?>(null) }
    var tab by remember { mutableStateOf(PlayerTab.Players) }
    var leaving by remember { mutableStateOf(false) }
    var noting by remember { mutableStateOf<String?>(null) }
    val notes = rememberPlayerNotes(gameId)

    LaunchedEffect(app, gameId, seatId) {
        val player = app.player ?: return@LaunchedEffect
        player.watch(gameId, seatId).collect {
            view = it
            loaded = true
        }
    }

    val characterIds = view?.characterIds
    val fabledIds = view?.fabledIds
    val script = remember(app, characterIds) { characterIds.orEmpty().mapNotNull { app.catalog.get(it) } }
    val fabled = remember(app, fabledIds) { fabledIds.orEmpty().mapNotNull { app.catalog.get(it) } }

    val current = view
    if (!loaded) {
        Page(flow = true) { Muted("Conectando con la mesa…") }
        return
    }
    if (current == null) {
        Page(flow = true, maxWidth = 480.dp) {
            Text("Sin partida", style = GrimType.title)
            Muted("Esta partida ya no existe o el Narrador te ha quitado de la mesa.")
            GrimButton("Unirme a otra", { nav.navigate(Route.Join()) }, kind = ButtonKind.Primary)
        }
        return
    }

    val alive = current.players.count { it.alive }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        ) {
            item(key = "bar") { GameBar(current, Gutter.padding(bottom = 10.dp)) }

            if (current.status == TableStatus.Finished) {
                item(key = "winner") { WinnerBanner(current.winner, Gutter.padding(bottom = 16.dp)) }
            }

            // Sin pestaña del personaje: lo enseña el Narrador en persona, para que nadie pueda enseñar la pantalla como prueba.
            stickyHeader(key = "tabs") {
                PageTabs {
                    PageTab("Mesa", Lucide.Users, tab == PlayerTab.Players) { tab = PlayerTab.Players }
                    PageTab("Guion", Lucide.ScrollText, tab == PlayerTab.Script) { tab = PlayerTab.Script }
                }
            }
            item(key = "tabs-gap") { Spacer(Modifier.height(16.dp)) }

            when (tab) {
                PlayerTab.Script -> {
                    if (fabled.isNotEmpty()) {
                        item(key = "fabled") {
                            GrimCard(Gutter.padding(bottom = 16.dp)) {
                                Text("Anunciados por el Narrador", style = GrimType.heading.copy(color = Grim.textStrong))
                                ScriptCharacterList(fabled, GuideAudience.Player, Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                    item(key = "script") { ScriptCharacterList(script, GuideAudience.Player, Gutter) }
                }
                PlayerTab.Players -> playersTab(
                    view = current,
                    alive = alive,
                    notes = notes,
                    catalog = app.catalog,
                    onNote = { noting = it },
                    onLeave = { leaving = true },
                )
            }
        }
    }

    noting?.let { name ->
        NotesSheet(
            name = name,
            note = notes.notes[name] ?: SeatNote(),
            script = script,
            onChange = { notes.update(name, it) },
            onClose = { noting = null },
        )
    }

    if (leaving) {
        ConfirmDialog(
            title = "¿Salir de la sala?",
            message = "Dejarás tu asiento. Puedes volver a entrar con el código mientras no empiece la partida.",
            confirmLabel = "Salir",
            onConfirm = {
                leaving = false
                state.run {
                    app.player!!.leave(seatId)
                    nav.home()
                }
            },
            onCancel = { leaving = false },
        )
    }
}

/** Nombre de la partida, guion y asiento, con la fase a la derecha (como .game-bar). */
@Composable
private fun GameBar(view: PlayerView, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                view.name,
                style = GrimType.body.copy(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, color = Grim.textStrong),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${view.scriptName} · asiento ${view.seat + 1}", style = GrimType.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (view.status == TableStatus.Lobby) {
            PhasePill("Sala de espera", PillKind.Plain)
        } else {
            val night = isNight(view.phaseLabel)
            PhasePill(
                view.phaseLabel ?: "",
                if (night) PillKind.Night else PillKind.Day,
                if (night) Lucide.Moon else if (view.status == TableStatus.Finished) Lucide.Flag else Lucide.Sun,
            )
        }
    }
}

/** La pestaña «Mesa»: la sala de espera o el círculo de jugadores con mis notas. */
private fun LazyListScope.playersTab(
    view: PlayerView,
    alive: Int,
    notes: PlayerNotes,
    catalog: CharacterCatalog,
    onNote: (String) -> Unit,
    onLeave: () -> Unit,
) {
    when {
        view.status == TableStatus.Lobby -> item(key = "lobby") {
            Column(Gutter, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Esperando al Narrador…", style = GrimType.heading.copy(color = Grim.textStrong))
                Muted(
                    "Estás en la sala. Cuando empiece la partida, el Narrador te enseñará tu personaje en persona. Aquí verás la mesa: " +
                        "quién sigue vivo, los votos y tus notas.",
                )
                GrimButton("Salir de la sala", onLeave, kind = ButtonKind.Ghost)
            }
        }
        view.players.isEmpty() -> item(key = "no-players") {
            Muted("La lista aparecerá cuando empiece la partida.", Gutter)
        }
        else -> {
            item(key = "status") {
                NoticeBox(
                    when {
                        view.alive -> "Estás vivo."
                        view.ghostVoteUsed -> "Estás muerto y ya has gastado tu voto."
                        else -> "Estás muerto. Te queda un voto para el resto de la partida."
                    },
                    if (view.alive) NoticeKind.Ok else NoticeKind.Warning,
                    Gutter.padding(bottom = 12.dp),
                )
            }
            item(key = "circle") {
                SeatCircle(
                    view.players,
                    Gutter,
                    center = {
                        view.phaseLabel?.let { label ->
                            val night = isNight(label)
                            PhasePill(label, if (night) PillKind.Night else PillKind.Day, if (night) Lucide.Moon else Lucide.Sun)
                        }
                        view.timerEndsAt?.let { Countdown(it) }
                        StatLine(alive)
                    },
                ) { p, size, _ ->
                    TableSeat(p, size, view.seat, notes.notes[p.name], catalog, onNote)
                }
            }
            item(key = "help") {
                Muted(
                    "$alive vivos de ${view.players.size}. ● = muerto al que le queda el voto. Toca a un jugador para apuntar qué dice ser y " +
                        "qué crees que es: tus notas solo se guardan en este móvil.",
                    Gutter.padding(top = 8.dp, bottom = 8.dp),
                )
            }
            items(view.players, key = { "seat-" + it.seat }) { p ->
                val note = notes.notes[p.name]
                PlayerRow(
                    p,
                    me = p.seat == view.seat,
                    note = note,
                    claimName = note?.claim?.let { catalog.get(it)?.name },
                    onNote = { onNote(p.name) },
                )
            }
        }
    }
}

/** Un asiento del círculo: lo que dice ser según mis notas (los Viajeros enseñan su personaje real, que es público). */
@Composable
private fun TableSeat(
    p: PublicPlayer,
    size: Dp,
    mySeat: Int,
    note: SeatNote?,
    catalog: CharacterCatalog,
    onNote: (String) -> Unit,
) {
    val traveller = p.travellerId?.let { catalog.get(it) }
    val me = p.seat == mySeat
    val claimed = if (traveller == null) note?.claim?.let { catalog.get(it) } else null
    val label = buildString {
        append(p.name)
        if (traveller != null) append(", ${traveller.name}") else if (claimed != null) append(", dice ser ${claimed.name}")
        if (!p.alive) append(", muerto")
        if (me) append(" (tú)")
    }
    Token(
        traveller ?: claimed,
        alignment = note?.guess,
        size = size,
        dead = !p.alive,
        hidden = traveller == null && claimed == null,
        dim = claimed != null,
        glow = me,
        onClick = if (me) null else ({ onNote(p.name) }),
        label = label,
    )
    SeatName(
        (if (!p.alive && !p.ghostVoteUsed) "● " else "") + p.name + (if (me) " (tú)" else ""),
        evil = note?.guess == Side.Evil,
    )
}

/** Un renglón de la lista de jugadores (como .log li): número, nombre, notas y estado. */
@Composable
private fun PlayerRow(
    p: PublicPlayer,
    me: Boolean,
    note: SeatNote?,
    claimName: String?,
    onNote: () -> Unit,
) {
    Row(
        Gutter
            .fillMaxWidth()
            .drawBehind {
                drawLine(Grim.line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx())
            }
            .alpha(if (p.alive) 1f else 0.6f)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${p.seat + 1}", Modifier.width(24.dp), style = GrimType.small)
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    append(p.name)
                    if (me) withStyle(SpanStyle(color = Grim.muted, fontSize = 14.sp)) { append(" (tú)") }
                },
                style = GrimType.body.copy(fontSize = 16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note != null) NoteSummary(note, claimName)
        }
        Text(
            if (p.alive) "Vivo" else if (p.ghostVoteUsed) "Muerto, sin voto" else "Muerto, con voto",
            style = GrimType.small.copy(fontSize = 14.sp, color = Grim.text),
        )
        if (!me) {
            GrimIconButton(Lucide.NotebookPen, "Notas sobre ${p.name}", onNote, size = 40.dp)
        } else {
            // Mismo hueco que el botón, para que la columna del estado no baile.
            Box(Modifier.width(40.dp))
        }
    }
}

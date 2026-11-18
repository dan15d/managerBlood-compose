package es.dan15d.grimorio.presentation.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.domain.entities.Alignment as Side
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameLogEntry
import es.dan15d.grimorio.domain.entities.GameStatus
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.services.alivePlayers
import es.dan15d.grimorio.domain.services.declareWinner
import es.dan15d.grimorio.domain.services.phaseStart
import es.dan15d.grimorio.domain.services.reopen
import es.dan15d.grimorio.domain.services.setBluffs
import es.dan15d.grimorio.domain.services.succeedDemon
import es.dan15d.grimorio.domain.services.voteThreshold
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.CharacterPicker
import es.dan15d.grimorio.presentation.components.Divider
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimIconButton
import es.dan15d.grimorio.presentation.components.GrimMenu
import es.dan15d.grimorio.presentation.components.GrimSheet
import es.dan15d.grimorio.presentation.components.InfoTokenSheet
import es.dan15d.grimorio.presentation.components.MenuItem
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.Page
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.navigation.BackHandlerLayer
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.pages.game.ActionBar
import es.dan15d.grimorio.presentation.pages.game.AlertKind
import es.dan15d.grimorio.presentation.pages.game.CardTitle
import es.dan15d.grimorio.presentation.pages.game.ChronicleSheet
import es.dan15d.grimorio.presentation.pages.game.DayActionBar
import es.dan15d.grimorio.presentation.pages.game.DayPanel
import es.dan15d.grimorio.presentation.pages.game.DaySheets
import es.dan15d.grimorio.presentation.pages.game.FabledBar
import es.dan15d.grimorio.presentation.pages.game.FabledSheets
import es.dan15d.grimorio.presentation.pages.game.GameAlert
import es.dan15d.grimorio.presentation.pages.game.GameBanner
import es.dan15d.grimorio.presentation.pages.game.GameController
import es.dan15d.grimorio.presentation.pages.game.GameTab
import es.dan15d.grimorio.presentation.pages.game.Grimoire
import es.dan15d.grimorio.presentation.pages.game.NightActionBar
import es.dan15d.grimorio.presentation.pages.game.NightCard
import es.dan15d.grimorio.presentation.pages.game.NightSheets
import es.dan15d.grimorio.presentation.pages.game.NightStrip
import es.dan15d.grimorio.presentation.pages.game.PhaseClock
import es.dan15d.grimorio.presentation.pages.game.Pick
import es.dan15d.grimorio.presentation.pages.game.PlayerSheet
import es.dan15d.grimorio.presentation.pages.game.QuickSheet
import es.dan15d.grimorio.presentation.pages.game.ScrollTarget
import es.dan15d.grimorio.presentation.pages.game.TimerAlarm
import es.dan15d.grimorio.presentation.pages.game.actionButton
import es.dan15d.grimorio.presentation.pages.game.aiPanelItems
import es.dan15d.grimorio.presentation.pages.game.nightListItems
import es.dan15d.grimorio.presentation.platform.KeepScreenOn
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/** Posición de las pestañas del panel en la lista del móvil (para bajar a él desde la tira de la noche). */
private const val PANEL_INDEX = 5

/**
 * La partida: el Grimorio en círculo, la hoja de noche o el día con nominaciones y votos, la IA y el
 * registro. En el móvil va todo en una columna con la barra de la fase siempre a mano abajo; en
 * pantallas anchas, el panel a la derecha.
 */
@Composable
fun GamePage(id: String) {
    val state = LocalAppState.current
    val app = state.app ?: return
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val c = remember(id, app) { GameController(id, app, state, scope, onGone = { nav.home() }) }
    LaunchedEffect(c) { c.load() }
    LaunchedEffect(c) { c.watch() }
    LaunchedEffect(c) { c.retryLoop() }
    KeepScreenOn()

    val game = c.game
    if (game == null) {
        Page(flow = true) { Muted("Abriendo el Grimorio…") }
        return
    }
    TimerAlarm(game)

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 900.dp
        val gutter = if (wide) 24.dp else 12.dp
        Column(Modifier.widthIn(max = 1400.dp).fillMaxSize()) {
            GameBar(c, game, Modifier.padding(horizontal = gutter))
            PickBanner(c, Modifier.padding(horizontal = gutter))
            if (wide) WideLayout(c, game) else NarrowLayout(c, game)
        }
    }
    GameSheets(c, game)
}

// ---------- Barra y avisos ----------

/** Barra corta: lo de cada momento a mano (deshacer, ocultar) y el resto en el menú «…». */
@Composable
private fun GameBar(c: GameController, game: Game, modifier: Modifier) {
    val nav = LocalNavigator.current
    val seat = c.pick is Pick.Seat
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GrimIconButton(Lucide.ArrowLeft, "Volver a partidas", { nav.navigate(Route.Start) })
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                game.name,
                style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Grim.textStrong),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${game.scriptName} · ${game.players.size} jugadores", style = GrimType.small.copy(fontSize = 13.sp, lineHeight = 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GrimIconButton(Lucide.Undo2, "Deshacer", { c.undo() }, enabled = c.canUndo)
        GrimIconButton(
            if (c.hidden) Lucide.Eye else Lucide.EyeOff,
            if (c.hidden) "Mostrar personajes" else "Ocultar personajes",
            { c.hidden = !c.hidden },
            bordered = c.hidden,
            tint = if (c.hidden) Grim.goldBright else Grim.text,
        )
        GrimMenu(
            "Más acciones",
            buildList {
                add(MenuItem("Enseñar al jugador…", { c.composing = true }, Lucide.Eye))
                add(MenuItem(if (seat) "Dejar de cambiar sitios" else "Cambiar sitios", { c.pick = if (seat) null else Pick.Seat() }, Lucide.ArrowLeftRight, pressed = seat))
                add(MenuItem("Repartir personajes", { nav.navigate(Route.Reveal(game.id)) }, Lucide.Smartphone))
                add(MenuItem("Fábulas y Lóricos…", { c.addingFabled = true }, Lucide.WandSparkles))
                if (game.status != GameStatus.Finished) add(MenuItem("Terminar la partida", { c.ending = true }, Lucide.Flag, danger = true))
            },
        )
    }
}

/** Qué hay que tocar en el modo «toca una ficha», siempre a la vista encima del Grimorio. */
@Composable
private fun PickBanner(c: GameController, modifier: Modifier) {
    val pick = c.pick ?: return
    // «Atrás» sale del modo sin tocar a nadie.
    BackHandlerLayer { c.pick = null }
    val text = when (pick) {
        is Pick.Seat -> if (pick.fromId != null) "¿Con quién cambia de sitio ${c.name(pick.fromId)}?" else "Toca a un jugador y después a otro para cambiarlos de sitio"
        is Pick.Reminder -> "Toca al jugador para poner «${pick.label}»"
        Pick.Exile -> "Toca al Viajero que se quiere exiliar"
        Pick.Execute -> "Toca al jugador que se ejecuta sin votación"
        Pick.Shooter -> "Toca a quien dispara (dice ser el Exterminador)"
        is Pick.Shot -> "${c.name(pick.shooterId)} dispara a… toca al elegido"
        Pick.Nominator -> "Toca a quien nomina"
        is Pick.Nominee -> "${c.name(pick.nominatorId)} nomina a… toca al nominado"
    }
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.padding(bottom = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(10.dp, shape)
                .clip(shape)
                .background(Color(0xF52C2138))
                .border(1.dp, Grim.goldBright, shape)
                .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text, Modifier.weight(1f), style = TextStyle(fontFamily = GrimType.fonts.display, fontSize = 18.sp, lineHeight = 22.sp, color = Grim.goldBright))
            val seat = pick is Pick.Seat
            GrimButton(if (seat) "Listo" else "Cancelar", { c.pick = null }, kind = if (seat) ButtonKind.Gold else ButtonKind.Default, small = true)
        }
    }
}

/** Fin de partida, victoria posible o nuevo Demonio, encima del Grimorio. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Banners(c: GameController, game: Game) {
    val characters = c.characters
    val succession = c.succession
    val hint = c.hint
    val note = c.note
    val gap = Modifier.padding(bottom = 12.dp)
    when {
        game.status == GameStatus.Finished -> GameBanner(game.winner, gap) {
            CardTitle(if (game.winner == Side.Good) "Ha ganado el Bien" else "Ha ganado el Mal", Grim.textStrong)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GrimButton("Crónica", { c.chronicleOpen = true }, kind = ButtonKind.Gold, icon = Lucide.Feather, small = true)
                GrimButton("Reabrir", { c.change(::reopen) }, small = true)
            }
        }
        note != null -> GameAlert(AlertKind.Info, gap) { Text(note, style = GrimType.body.copy(fontSize = 16.sp)) }
        succession != null -> if (c.dismissedHint != c.successionKey) {
            GameBanner(Side.Evil, gap) {
                CardTitle("¿Nuevo Demonio?", Grim.textStrong)
                Text(succession.reason, style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp))
                succession.caveat?.let { Muted(it) }
                Muted("Esta noche, despiértalo y enséñale TÚ ERES y la ficha del Demonio.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (h in succession.heirs) {
                        key(h.id) {
                            GrimButton(
                                "${h.name} (${characters[h.characterId]?.name}) → ${characters[succession.demon.characterId]?.name}",
                                { c.change { g -> succeedDemon(g, succession, h.id, characters) } },
                                kind = ButtonKind.Gold,
                                small = true,
                            )
                        }
                    }
                    GrimButton("Ganan los buenos", { c.change { g -> declareWinner(g, Side.Good) } }, small = true)
                    GrimButton("Ahora no", { c.dismissedHint = c.successionKey }, kind = ButtonKind.Ghost, small = true)
                }
            }
        }
        hint != null && c.dismissedHint != c.hintKey -> GameBanner(hint.winner, gap) {
            CardTitle(if (hint.winner == Side.Good) "¿Gana el Bien?" else "¿Gana el Mal?", Grim.textStrong)
            Muted(hint.reason)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GrimButton("Declarar victoria", { c.change { g -> declareWinner(g, hint.winner) } }, kind = ButtonKind.Gold, small = true)
                GrimButton("La partida sigue", { c.dismissedHint = c.hintKey }, kind = ButtonKind.Ghost, small = true)
            }
        }
    }
}

// ---------- Disposición ----------

/** El círculo del Grimorio, centrado. */
@Composable
private fun GrimoireBlock(c: GameController, game: Game) {
    val step = c.step
    // Hueco abajo: el nombre de quien se sienta abajo del todo sobresale del círculo.
    Box(Modifier.fillMaxWidth().padding(bottom = 14.dp), contentAlignment = Alignment.TopCenter) {
        Grimoire(
            players = game.players,
            phase = game.phase,
            alive = alivePlayers(game).size,
            threshold = voteThreshold(game),
            characters = c.characters,
            highlighted = c.highlighted,
            hidden = c.hidden,
            picking = c.pick != null,
            centerNote = if (step != null) "${c.stepIndex + 1}/${c.steps.size} · ${step.title}" else null,
            onSelect = c.onTapPlayer,
            onRemoveReminder = c.onRemoveReminder,
        )
    }
}

/** Móvil: todo en una lista (el círculo arriba, el panel debajo) y la barra de la fase fija abajo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NarrowLayout(c: GameController, game: Game) {
    val listState = rememberLazyListState()
    LaunchedEffect(c, listState) {
        c.scrollRequests.collect { target -> listState.animateScrollToItem(if (target == ScrollTarget.Top) 0 else PANEL_INDEX) }
    }
    val night = game.phase.isNight
    val panel = Modifier.fillMaxWidth().background(Grim.surface).padding(horizontal = 16.dp)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            item(key = "banners") { Banners(c, game) }
            item(key = "grimoire") { GrimoireBlock(c, game) }
            // En el móvil, el paso de la noche y su acción principal justo bajo el círculo: sin bajar al panel.
            item(key = "strip") {
                val step = c.step
                if (step != null && game.status == GameStatus.InProgress) NightStrip(c, game, c.steps, c.stepIndex, step)
            }
            item(key = "fabled") { FabledBar(c, game.fabledIds, game) }
            item(key = "gap") { Spacer(Modifier.height(16.dp)) }
            stickyHeader(key = "tabs") { PanelTabs(c, night, rounded = true) }
            item(key = "panel-top") { Spacer(panel.height(16.dp)) }
            panelItems(c, game, panel)
            item(key = "panel-end") {
                Spacer(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).background(Grim.surface))
            }
        }
        if (c.tab == GameTab.Phase) PhaseBar(c, game, Modifier.padding(horizontal = 12.dp))
    }
}

/** Pantalla ancha: el Grimorio a la izquierda y el panel fijo a la derecha. */
@Composable
private fun WideLayout(c: GameController, game: Game) {
    val leftState = rememberLazyListState()
    LaunchedEffect(c, leftState) {
        c.scrollRequests.collect { target -> if (target == ScrollTarget.Top) leftState.animateScrollToItem(0) }
    }
    val night = game.phase.isNight
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = leftState, contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "banners") { Banners(c, game) }
            item(key = "grimoire") { GrimoireBlock(c, game) }
            item(key = "fabled") { FabledBar(c, game.fabledIds, game) }
        }
        Column(
            Modifier.width(420.dp).fillMaxHeight().padding(bottom = 16.dp).clip(shape).background(Grim.surface).border(1.dp, Grim.line, shape),
        ) {
            PanelTabs(c, night, rounded = false)
            val panel = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item(key = "panel-top") { Spacer(Modifier.height(16.dp)) }
                panelItems(c, game, panel)
                item(key = "panel-end") { Spacer(Modifier.height(16.dp)) }
            }
            if (c.tab == GameTab.Phase) PhaseBar(c, game, Modifier.padding(horizontal = 16.dp))
        }
    }
}

/** Pestañas del panel: Noche o Día, IA y Registro. */
@Composable
private fun PanelTabs(c: GameController, night: Boolean, rounded: Boolean) {
    val shape = if (rounded) RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp) else RoundedCornerShape(0.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Grim.surface2)) {
        Row(Modifier.fillMaxWidth()) {
            PanelTab(if (night) "Noche" else "Día", if (night) Lucide.Moon else Lucide.Sun, c.tab == GameTab.Phase) { c.tab = GameTab.Phase }
            PanelTab("IA", Lucide.Sparkles, c.tab == GameTab.Ai) { c.tab = GameTab.Ai }
            PanelTab("Registro", Lucide.ScrollText, c.tab == GameTab.Log) { c.tab = GameTab.Log }
        }
        Divider()
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PanelTab(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Grim.textStrong else Grim.muted
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Text(text.uppercase(), style = TextStyle(fontFamily = GrimType.fonts.ui, fontSize = 13.sp, letterSpacing = 1.sp, color = color))
        }
        if (selected) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(Grim.goldBright))
    }
}

/** Lo que va en el panel según la pestaña, como elementos de la lista (la hoja de noche y el registro, uno a uno). */
private fun LazyListScope.panelItems(c: GameController, game: Game, panel: Modifier) {
    when (c.tab) {
        GameTab.Phase -> {
            val night = game.phase.isNight
            if (game.status == GameStatus.InProgress) {
                item(key = "clock") {
                    Box(panel) { PhaseClock(phaseStart(game), game.timer?.endsAt, night, c.onChange) }
                }
            }
            if (night) {
                val step = c.step
                if (step != null) {
                    item(key = "night-card") { Box(panel) { NightCard(c, game, c.steps, c.stepIndex, step) } }
                    nightListItems(c, game, c.steps, c.stepIndex, panel)
                }
            } else {
                item(key = "day") { Box(panel) { DayPanel(c, game) } }
            }
        }
        GameTab.Ai -> aiPanelItems(c, game.aiNotes, panel)
        GameTab.Log -> logItems(game.log, panel)
    }
}

/** Registro de la partida, lo último arriba y agrupado por fase; cada línea es un elemento de la lista. */
private fun LazyListScope.logItems(log: List<GameLogEntry>, panel: Modifier) {
    if (log.isEmpty()) {
        item(key = "log-empty") { Box(panel) { Muted("Todavía no ha pasado nada.") } }
        return
    }
    val n = log.size
    items(n, key = { i -> "log:${n - 1 - i}" }) { i ->
        val entry = log[n - 1 - i]
        val header = i == 0 || log[n - i].phase != entry.phase
        Column(panel) {
            if (header) {
                Text(entry.phase.uppercase(), Modifier.padding(top = if (i == 0) 0.dp else 14.dp), style = GrimType.caps.copy(color = Grim.goldBright))
            }
            Text(entry.text, Modifier.padding(vertical = 6.dp), style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp))
            Divider()
        }
    }
}

/** La barra de la fase al pie: paso anterior/siguiente de noche, ejecutar y anochecer de día. */
@Composable
private fun PhaseBar(c: GameController, game: Game, modifier: Modifier) {
    if (game.phase.isNight) {
        if (c.steps.isNotEmpty()) NightActionBar(c, c.stepIndex, c.stepIndex == c.steps.size - 1, modifier)
    } else {
        DayActionBar(c, game, modifier)
    }
}

// ---------- Hojas ----------

/** Todas las hojas que suben desde abajo, fuera de la lista para que no se cierren al desplazarla. */
@Composable
private fun GameSheets(c: GameController, game: Game) {
    val characters = c.characters
    fun name(pid: String) = game.players.find { it.id == pid }?.name ?: ""

    if (c.ending) {
        val heretic = game.players.any { it.characterId == "heretic" }
        GrimSheet("Terminar la partida", { c.ending = false }) {
            Text(
                buildAnnotatedString {
                    if (heretic) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Grim.textStrong)) { append("Hay Hereje en juego: elige ya el ganador final (invertido). ") }
                    append("Para las victorias que la app no detecta sola: el Patoso, la Damisela, el Goblin… Se puede reabrir si te equivocas.")
                },
                style = GrimType.small,
            )
            ActionBar(sheet = true) {
                GrimButton("Gana el Mal", {
                    c.ending = false
                    c.change { g -> declareWinner(g, Side.Evil) }
                }, actionButton())
                GrimButton("Gana el Bien", {
                    c.ending = false
                    c.change { g -> declareWinner(g, Side.Good) }
                }, actionButton(), kind = ButtonKind.Gold)
            }
        }
    }

    if (c.chronicleOpen) {
        ChronicleSheet(c, game, onSave = { text -> c.change { g -> g.copy(chronicle = text) } }, onClose = { c.chronicleOpen = false })
    }

    c.quick?.let { pid ->
        game.players.find { it.id == pid }?.let { p ->
            key(p.id) {
                QuickSheet(
                    c,
                    p,
                    onOpenSheet = {
                        c.quick = null
                        c.selected = p.id
                    },
                    onClose = { c.quick = null },
                )
            }
        }
    }

    c.selected?.let { pid ->
        game.players.find { it.id == pid }?.let { p ->
            key(p.id) { PlayerSheet(c, game, p, onClose = { c.selected = null }) }
        }
    }

    if (c.editingBluffs) {
        GrimSheet("Faroles del Demonio", { c.editingBluffs = false }) {
            Muted("Elige 3 personajes buenos que no estén en juego (${game.demonBluffs.size}/3).")
            CharacterPicker(
                characters = c.scriptCharacters.filter { ch ->
                    (ch.team == Team.Townsfolk || ch.team == Team.Outsider) && game.players.none { it.characterId == ch.id || it.shownCharacterId == ch.id }
                },
                selected = game.demonBluffs,
                onToggle = { cid ->
                    c.change { g -> setBluffs(g, if (cid in g.demonBluffs) g.demonBluffs - cid else (g.demonBluffs + cid).takeLast(3)) }
                },
            )
        }
    }

    if (c.composing) {
        InfoTokenSheet(
            scriptCharacters = c.scriptCharacters,
            suggested = listOfNotNull(c.step?.character),
            bluffs = game.demonBluffs.mapNotNull { characters[it] },
            onClose = { c.composing = false },
        )
    }

    c.pitHagTarget?.let { targetId ->
        GrimSheet("¿En qué se convierte ${name(targetId)}?", { c.pitHagTarget = null }) {
            Muted("Solo cambia si el personaje no está en juego. Conserva su bando.")
            CharacterPicker(
                characters = c.scriptCharacters.filter { ch -> ch.team != Team.Traveller && game.players.none { it.characterId == ch.id } },
                selected = emptyList(),
                searchable = true,
                onToggle = { cid -> c.pitHagPick(targetId, cid) },
            )
        }
    }

    NightSheets(c, game)
    DaySheets(c, game)
    FabledSheets(c, game)
}

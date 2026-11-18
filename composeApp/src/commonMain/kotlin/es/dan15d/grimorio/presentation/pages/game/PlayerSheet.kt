package es.dan15d.grimorio.presentation.pages.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.application.usecases.GuideAudience
import es.dan15d.grimorio.domain.entities.Alignment as Side
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.ReminderToken
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.entities.label
import es.dan15d.grimorio.domain.services.DECOYS
import es.dan15d.grimorio.domain.services.addReminder
import es.dan15d.grimorio.domain.services.gameCharacterIds
import es.dan15d.grimorio.domain.services.killPlayer
import es.dan15d.grimorio.domain.services.removeReminder
import es.dan15d.grimorio.domain.services.renamePlayer
import es.dan15d.grimorio.domain.services.revivePlayer
import es.dan15d.grimorio.domain.services.seatSecret
import es.dan15d.grimorio.domain.services.setAlignment
import es.dan15d.grimorio.domain.services.setCharacter
import es.dan15d.grimorio.domain.services.setGhostVoteUsed
import es.dan15d.grimorio.domain.services.setNotes
import es.dan15d.grimorio.domain.services.setShownCharacter
import es.dan15d.grimorio.domain.services.swapCharacters
import es.dan15d.grimorio.domain.services.swapSeats
import es.dan15d.grimorio.presentation.components.Badge
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.CharacterGuide
import es.dan15d.grimorio.presentation.components.CharacterPicker
import es.dan15d.grimorio.presentation.components.Collapsible
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimSheet
import es.dan15d.grimorio.presentation.components.GrimTextField
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.SectionLabel
import es.dan15d.grimorio.presentation.components.ShowCard
import es.dan15d.grimorio.presentation.components.SpecialCases
import es.dan15d.grimorio.presentation.components.Token
import es.dan15d.grimorio.presentation.components.color
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.navigation.BackHandlerLayer
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

private val GENERIC_REMINDERS = listOf("Envenenado", "Borracho", "Protegido", "Muerto esta noche", "Sin habilidad", "Usado")

// Primero los recordatorios del Mal (Envenenado, Muerto…), que son los que más se ponen.
private val REMINDER_TEAM_ORDER = listOf(Team.Demon, Team.Minion, Team.Townsfolk, Team.Outsider, Team.Traveller, Team.Fabled, Team.Loric)

/** «3 · Ana» como título de las hojas de un jugador, con el número apagado. */
@Composable
internal fun RowScope.PlayerTitle(player: Player) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Grim.muted)) { append("${player.seat + 1} ·") }
            append(" ${player.name}")
        },
        Modifier.weight(1f),
        style = GrimType.title.copy(fontSize = 22.sp, lineHeight = 26.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Todo lo de un jugador en una hoja: lo que más se usa arriba, lo raro al final. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerSheet(c: GameController, game: Game, player: Player, onClose: () -> Unit) {
    val characters = c.characters
    var changing by remember { mutableStateOf(false) }
    var card by remember { mutableStateOf<ShowSpec?>(null) }

    GrimSheet("Jugador ${player.name}", onClose, titleContent = { PlayerTitle(player) }) {
        if (changing) {
            BackHandlerLayer { changing = false }
            Muted("Para el Filósofo, el Diablillo que pasa su Demonio, la Mujer Escarlata…")
            GrimButton("Volver", { changing = false }, kind = ButtonKind.Ghost, icon = Lucide.ArrowLeft, small = true)
            CharacterPicker(
                characters = c.scriptCharacters,
                selected = listOf(player.characterId),
                searchable = true,
                onToggle = { id ->
                    characters[id]?.let { ch -> c.change { g -> setCharacter(g, player.id, ch) } }
                    changing = false
                },
            )
        } else {
            PlayerSheetBody(c, game, player, onClose, onChangeCharacter = { changing = true }, onShow = { card = it })
        }
    }
    card?.let { spec -> ShowCard(spec.characters, onClose = { card = null }, heading = spec.heading) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.PlayerSheetBody(
    c: GameController,
    game: Game,
    player: Player,
    onClose: () -> Unit,
    onChangeCharacter: () -> Unit,
    onShow: (ShowSpec) -> Unit,
) {
    val characters = c.characters
    var custom by remember { mutableStateOf("") }
    // Lo que cree ser (el Borracho ve su personaje falso), para enseñárselo.
    val believed = seatSecret(player, characters).characterId?.let { characters[it] }
    val bluffs = game.demonBluffs.mapNotNull { characters[it] }
    val character = characters[player.characterId]
    val shown = player.shownCharacterId?.let { characters[it] }
    // Borracho, Lunático y Marioneta: qué tipo de personaje creen ser.
    val decoyTeam = DECOYS[player.characterId]

    val inPlay = remember(game.players, game.fabledIds) {
        (game.players.flatMap { listOfNotNull(it.characterId, it.shownCharacterId) } + game.fabledIds)
            .distinct()
            .mapNotNull { characters[it] }
            .filter { it.reminders.isNotEmpty() }
            .sortedBy { REMINDER_TEAM_ORDER.indexOf(it.team) }
    }
    // A mano, los recordatorios de su propio personaje (y del que cree ser); los de los demás, plegados.
    val own = inPlay.filter { it.id == player.characterId || it.id == player.shownCharacterId }
    val others = inPlay.filter { it !in own }

    fun add(sourceCharacterId: String, label: String) =
        c.change { g -> addReminder(g, player.id, ReminderToken(id = c.app.newId(), sourceCharacterId = sourceCharacterId, label = label)) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Token(character, alignment = player.alignment, size = 76.dp, dead = !player.alive)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(character?.name ?: "", style = GrimType.heading.copy(color = character?.team?.color ?: Grim.goldBright))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (character != null) Badge(character.team.label.singular, character.team.color)
                Badge(if (player.alignment == Side.Evil) "Malvado" else "Bueno", if (player.alignment == Side.Evil) Grim.demon else Grim.townsfolk)
                if (player.userId != null) Badge("Conectado", Grim.loric)
            }
        }
    }
    if (character != null) Text(character.ability, style = GrimType.body)
    SpecialCases(player.characterId, gameCharacterIds(game))
    if (shown != null) {
        GameAlert(AlertKind.Warning) {
            Text(
                buildAnnotatedString {
                    append("Cree ser ")
                    withStyle(SpanStyle(color = Grim.textStrong)) { append(shown.name) }
                    append(". Despiértalo como tal y dale información falsa si te conviene.")
                },
                style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp),
            )
        }
    }
    if (decoyTeam != null) {
        SelectField(
            label = "Cree ser (es lo que le enseñas al repartir)",
            placeholder = "— Sin asignar: no verá ningún personaje —",
            options = listOf(SelectOption("", "— Sin asignar: no verá ningún personaje —")) +
                c.scriptCharacters.filter { it.team == decoyTeam }.map { ch ->
                    SelectOption(ch.id, ch.name + if (game.players.any { it.characterId == ch.id }) " (en juego)" else "")
                },
            selected = player.shownCharacterId ?: "",
            onSelect = { value -> c.change { g -> setShownCharacter(g, player.id, characters[value]) } },
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GrimButton(
            if (player.alive) "Matar" else "Revivir",
            { c.change { g -> if (player.alive) killPlayer(g, player.id) else revivePlayer(g, player.id) } },
            Modifier.weight(1f),
            kind = if (player.alive) ButtonKind.Danger else ButtonKind.Gold,
            icon = if (player.alive) Lucide.Skull else Lucide.HeartPulse,
        )
        GrimButton(
            "Pasar a ${if (player.alignment == Side.Good) "Malvado" else "Bueno"}",
            { c.change { g -> setAlignment(g, player.id, if (player.alignment == Side.Good) Side.Evil else Side.Good) } },
            Modifier.weight(1f),
        )
    }
    if (!player.alive) {
        GrimButton(
            if (player.ghostVoteUsed) "Voto fantasma gastado · devolver" else "Le queda el voto fantasma · gastar",
            { c.change { g -> setGhostVoteUsed(g, player.id, !player.ghostVoteUsed) } },
            Modifier.fillMaxWidth(),
            kind = ButtonKind.Ghost,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GrimButton(
            "Enseñarle su personaje",
            { if (believed != null) onShow(ShowSpec("Tú eres", listOf(believed))) },
            Modifier.weight(1f),
            kind = ButtonKind.Ghost,
            icon = Lucide.Eye,
            enabled = believed != null,
        )
        if (character?.team == Team.Demon) {
            GrimButton(
                "Faroles",
                { onShow(ShowSpec("Estos personajes no están en juego", bluffs)) },
                Modifier.weight(1f),
                kind = ButtonKind.Ghost,
                icon = Lucide.Eye,
                enabled = bluffs.isNotEmpty(),
            )
        }
    }

    SectionLabel("Recordatorios")
    if (player.reminders.isNotEmpty()) {
        Chips {
            for (r in player.reminders) {
                key(r.id) { ReminderChip(r.label, onRemove = { c.change { g -> removeReminder(g, player.id, r.id) } }) }
            }
        }
    } else {
        Muted("Ninguno. Añade uno tocándolo abajo.")
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Chips {
            for (ch in own) {
                for (label in ch.reminders.distinct()) key("${ch.id}:$label") { ChipButton("+ $label", { add(ch.id, label) }, reminder = true) }
            }
            for (label in GENERIC_REMINDERS) key(label) { ChipButton("+ $label", { add("custom", label) }) }
        }
        if (others.isNotEmpty()) {
            Collapsible("De otros personajes (${others.size})") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (ch in others) {
                        key(ch.id) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(ch.name, style = GrimType.small.copy(color = ch.team.color))
                                Chips {
                                    for (label in ch.reminders.distinct()) key(label) { ChipButton("+ $label", { add(ch.id, label) }) }
                                }
                            }
                        }
                    }
                }
            }
        }
        val submit = {
            if (custom.isNotBlank()) add("custom", custom.trim())
            custom = ""
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GrimTextField(custom, { custom = it }, Modifier.weight(1f), placeholder = "Otro recordatorio…", onDone = submit)
            GrimButton("Añadir", submit, enabled = custom.isNotBlank())
        }
    }

    SectionLabel("Notas")
    // Como en la web, se guarda al salir del campo (o al cerrar la hoja).
    BlurField(
        initial = player.notes ?: "",
        placeholder = "Qué dice ser, a quién ha señalado…",
        singleLine = false,
        onCommit = { text -> if (text != (player.notes ?: "")) c.change { g -> setNotes(g, player.id, text) } },
    )

    if (character != null) {
        SectionLabel("Guía de ${character.name}")
        CharacterGuide(character.id, GuideAudience.Storyteller)
    }

    Collapsible("Más opciones") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GrimButton("Cambiar personaje", onChangeCharacter, Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Nombre")
                BlurField(
                    initial = player.name,
                    placeholder = null,
                    singleLine = true,
                    onCommit = { text -> if (text != player.name) c.change { g -> renamePlayer(g, player.id, text) } },
                )
            }
            val others2 = game.players.filter { it.id != player.id }.map { SelectOption(it.id, "${it.seat + 1}. ${it.name}") }
            SelectField(
                label = "Cambiar de sitio con",
                placeholder = "— Elige jugador —",
                options = others2,
                selected = null,
                onSelect = { other -> c.change { g -> swapSeats(g, player.id, other) } },
            )
            SelectField(
                label = "Intercambiar personaje con (Barbero: cada uno conserva su bando)",
                placeholder = "— Elige jugador —",
                options = others2,
                selected = null,
                onSelect = { other -> c.change { g -> swapCharacters(g, player.id, other, characters) } },
            )
        }
    }

    ActionBar(sheet = true) {
        GrimButton("Listo", onClose, actionButton(), kind = ButtonKind.Gold)
    }
}

/**
 * Campo que guarda al perder el foco (como onBlur en la web) y, si se cierra la hoja sin salir
 * del campo, al cerrarla.
 */
@Composable
private fun BlurField(initial: String, placeholder: String?, singleLine: Boolean, onCommit: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var focused by remember { mutableStateOf(false) }
    val commit by rememberUpdatedState(onCommit)
    val latest by rememberUpdatedState(text)
    DisposableEffect(Unit) { onDispose { commit(latest) } }
    Box(
        Modifier.fillMaxWidth().onFocusChanged { state ->
            if (focused && !state.hasFocus) commit(text)
            focused = state.hasFocus
        },
    ) {
        GrimTextField(
            text,
            { text = it },
            placeholder = placeholder,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            onDone = if (singleLine) ({ commit(text) }) else null,
        )
    }
}

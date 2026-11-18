package es.dan15d.grimorio.presentation.pages.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.application.usecases.GuideAudience
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.entities.isStorytellerTeam
import es.dan15d.grimorio.domain.entities.label
import es.dan15d.grimorio.domain.services.CHOICE_WITHOUT_REMINDER
import es.dan15d.grimorio.domain.services.InfoTokenId
import es.dan15d.grimorio.domain.services.NightStep
import es.dan15d.grimorio.domain.services.NightStepKind
import es.dan15d.grimorio.domain.services.choiceReminder
import es.dan15d.grimorio.domain.services.demonBlocked
import es.dan15d.grimorio.domain.services.disablesAbility
import es.dan15d.grimorio.domain.services.gameCharacterIds
import es.dan15d.grimorio.domain.services.infoHints
import es.dan15d.grimorio.domain.services.infoToken
import es.dan15d.grimorio.domain.services.killPlayer
import es.dan15d.grimorio.domain.services.noteInfoGiven
import es.dan15d.grimorio.domain.services.phaseLabel
import es.dan15d.grimorio.domain.services.philosopherChoose
import es.dan15d.grimorio.domain.services.revivePlayer
import es.dan15d.grimorio.domain.services.setNightStep
import es.dan15d.grimorio.domain.services.startDay
import es.dan15d.grimorio.domain.services.stepInfoToken
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.CharacterGuide
import es.dan15d.grimorio.presentation.components.CharacterPicker
import es.dan15d.grimorio.presentation.components.Divider
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimIconButton
import es.dan15d.grimorio.presentation.components.GrimImage
import es.dan15d.grimorio.presentation.components.GrimSheet
import es.dan15d.grimorio.presentation.components.InfoTokenDraft
import es.dan15d.grimorio.presentation.components.InfoTokenSheet
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.NightIcons
import es.dan15d.grimorio.presentation.components.ShowCard
import es.dan15d.grimorio.presentation.components.SpecialCases
import es.dan15d.grimorio.presentation.components.Token
import es.dan15d.grimorio.presentation.components.color
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/** Icono del paso: la ficha del personaje o el de la hoja de noche (crepúsculo, Esbirros…). */
@Composable
fun StepIcon(step: NightStep, size: Dp) {
    val character = step.character
    if (character != null) {
        Token(character, size = size)
        return
    }
    val src = when (step.kind) {
        NightStepKind.Dusk -> NightIcons.dusk
        NightStepKind.Dawn -> NightIcons.dawn
        NightStepKind.MinionInfo -> NightIcons.minionInfo
        NightStepKind.DemonInfo -> NightIcons.demonInfo
        NightStepKind.Character -> null
    }
    if (src != null) GrimImage(src, Modifier.size(size))
}

/** Quien tiene el paso: los del equipo malvado que conoce el Demonio o los Esbirros («2. Ana, 5. Luis»). */
private fun seatsOf(game: Game, characters: Map<String, Character>, team: Team): String =
    game.players.filter { characters[it.characterId]?.team == team }.joinToString(", ") { "${it.seat + 1}. ${it.name}" }.ifEmpty { "—" }

/** Tarjeta de la noche: el paso actual con lo que hay que hacer en él. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NightCard(c: GameController, game: Game, steps: List<NightStep>, index: Int, step: NightStep) {
    val characters = c.characters
    fun player(id: String) = game.players.first { it.id == id }
    val last = index == steps.size - 1
    val character = step.character
    val firstNight = game.phase.number == 1
    // Jugadores de este paso con un recordatorio que les quita la habilidad (envenenado, borracho…).
    val disabled = step.playerIds.mapNotNull { id -> game.players.find { it.id == id } }.filter { p -> p.reminders.any { disablesAbility(it) } }
    val hints = remember(game, step) { if (character != null) infoHints(game, character, characters) else emptyList() }
    // El Exorcista o la Princesa impiden que el Demonio mate esta noche.
    val demonBlock = if (character?.team == Team.Demon) demonBlocked(game, character.id) else null
    // Recordatorio de quien elige jugador cada noche (Envenenador, Monje, Demonio…).
    val chooser = if (character != null && step.playerIds.isNotEmpty()) choiceReminder(character.id, firstNight) else null
    val otherReminders = character?.reminders?.distinct()?.filter { it != chooser } ?: emptyList()
    // Ficha de información que acompaña a la del personaje en este paso («ESTE PERSONAJE TE HA ELEGIDO»…).
    val stepToken = if (character != null && step.playerIds.isNotEmpty()) stepInfoToken(character.id, firstNight) else null
    val bluffs = game.demonBluffs.mapNotNull { characters[it] }

    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(shape)
            .background(Grim.surface)
            .background(Brush.verticalGradient(listOf(Color(0x387C48C4), Color.Transparent)))
            .border(1.dp, Grim.gold, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 2.dp)) {
            if (character != null) {
                Box(Modifier.clickable(role = Role.Button, onClickLabel = "Enseñar ${step.title}") { c.card = ShowSpec(null, listOf(character)) }) {
                    StepIcon(step, 64.dp)
                }
            } else {
                StepIcon(step, 64.dp)
            }
            Column(Modifier.weight(1f)) {
                Text("Paso ${index + 1} de ${steps.size}", style = GrimType.small.copy(fontSize = 14.sp))
                Text(step.title, style = GrimType.heading.copy(color = character?.team?.color ?: Grim.goldBright))
            }
        }

        if (step.playerIds.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                Divider()
                for (id in step.playerIds) {
                    val p = game.players.find { it.id == id } ?: continue
                    key(id) {
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { c.selected = id }.padding(horizontal = 2.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (id in step.deceivedPlayerIds) Icon(Lucide.VenetianMask, "Cree serlo", tint = Grim.goldBright, modifier = Modifier.size(18.dp))
                                Text("${p.seat + 1}. ${p.name}", style = TextStyle(fontFamily = GrimType.fonts.display, fontSize = 18.sp, color = Grim.textStrong))
                                if (!p.alive) Icon(Lucide.Skull, "Muerto", tint = Grim.muted, modifier = Modifier.size(18.dp))
                            }
                            if (p.reminders.isNotEmpty() || p.shownCharacterId != null) {
                                Chips {
                                    p.shownCharacterId?.let { ReminderChip("Cree ser ${characters[it]?.name ?: ""}", tiny = true) }
                                    for (r in p.reminders) key(r.id) { ReminderChip(r.label, danger = disablesAbility(r), tiny = true) }
                                }
                            }
                        }
                        Divider()
                    }
                }
            }
        }
        if (disabled.isNotEmpty()) {
            GameNote(
                disabled.joinToString("; ") { p -> "${p.name} (${p.reminders.filter { disablesAbility(it) }.joinToString(", ") { it.label.lowercase() }})" } +
                    ": sin habilidad esta noche. Despiértalo igual y dale información falsa si te conviene.",
                NoteKind.Warning,
                Lucide.TriangleAlert,
            )
        }
        if (step.kind == NightStepKind.Character && step.playerIds.isEmpty()) {
            Muted(
                if (character != null && isStorytellerTeam(character.team)) "${character.team.label.singular} en juego: no es un jugador, lo resuelves tú."
                else "Nadie en juego.",
            )
        }

        Text(step.instruction, Modifier.padding(vertical = 2.dp), style = GrimType.body)
        // La habilidad tal como está en la ficha, por si hay dudas (si no hay texto de noche, ya es la instrucción).
        if (character != null && character.ability != step.instruction) {
            Text(character.ability, style = GrimType.small.copy(fontStyle = FontStyle.Italic))
        }

        if (demonBlock != null) GameNote(demonBlock, NoteKind.Warning, Lucide.TriangleAlert)

        if (hints.isNotEmpty() && character != null) {
            val hintShape = RoundedCornerShape(8.dp)
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(hintShape).background(Grim.surface2).border(1.dp, Grim.fabled, hintShape).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Lucide.Lightbulb, null, tint = Grim.fabled, modifier = Modifier.size(16.dp))
                    Text("Según el Grimorio", style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Grim.fabled))
                }
                for (h in hints) {
                    Text(
                        buildAnnotatedString {
                            if (hints.size > 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${player(h.playerId).name}: ") }
                            append(h.text)
                        },
                        style = GrimType.body,
                    )
                }
                for (caveat in hints[0].caveats) Muted(caveat)
                // Dada la información, se apunta en el registro y la hoja pasa al siguiente paso.
                GrimButton(
                    if (last) "Dado" else "Dado · Siguiente",
                    {
                        c.change { g ->
                            val noted = noteInfoGiven(g, hints.map { h -> "${character.name} (${player(h.playerId).name}): ${h.text}" })
                            if (last) noted else setNightStep(noted, index + 1)
                        }
                    },
                    Modifier.padding(top = 6.dp),
                    kind = ButtonKind.Primary,
                    icon = Lucide.Check,
                    small = true,
                )
            }
        }

        // Quien elige a alguien (Envenenador, Monje, Demonio…): un toque y se pone su recordatorio, y la hoja pasa sola si no queda nada que hacer.
        if (chooser != null && character != null) {
            GrimButton(
                "Toca a quién elige" + if (character.id in CHOICE_WITHOUT_REMINDER) "" else " · «$chooser»",
                { c.startPick(Pick.Reminder(character.id, chooser, advance = true)) },
                Modifier.fillMaxWidth().padding(top = 4.dp),
                kind = ButtonKind.Primary,
                icon = Lucide.Pointer,
            )
        }

        if (character != null && otherReminders.isNotEmpty()) {
            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Muted(if (chooser != null) "Otros recordatorios:" else "Poner recordatorio:")
                Chips {
                    for (label in otherReminders) {
                        key(label) { ChipButton(label, { c.startPick(Pick.Reminder(character.id, label)) }, reminder = true) }
                    }
                }
            }
        }

        if (character != null) key(step.key) { SpecialCases(character.id, gameCharacterIds(game)) }

        val deathNote = step.deathNote
        if (deathNote != null) {
            GameNote(deathNote.text, if (deathNote.wake) NoteKind.Info else NoteKind.Warning)
        } else if (step.dead) {
            GameNote("Está muerto: sáltalo, salvo que su habilidad diga que actúa al morir.", NoteKind.Warning, Lucide.Skull)
        }
        if (step.deceivedPlayerIds.isNotEmpty()) {
            GameNote(
                "${step.deceivedPlayerIds.joinToString(", ") { player(it).name }} solo cree ser ${step.title}: no tiene esta habilidad. Dale información falsa (o verdadera, si confunde más).",
                NoteKind.Warning,
                Lucide.VenetianMask,
            )
        }

        if (step.kind == NightStepKind.MinionInfo) {
            GrimButton(
                "Enseñar «${infoToken(InfoTokenId.Demon).text}»",
                { c.card = ShowSpec(infoToken(InfoTokenId.Demon).text, emptyList()) },
                Modifier.fillMaxWidth(),
                icon = Lucide.Eye,
            )
            LabeledLine("Demonio: ", seatsOf(game, characters, Team.Demon))
        }
        if (step.kind == NightStepKind.DemonInfo) {
            LabeledLine("Esbirros: ", seatsOf(game, characters, Team.Minion))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (id in game.demonBluffs) key(id) { Token(characters[id], size = 70.dp) }
            }
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GrimButton("«Tus Esbirros»", { c.card = ShowSpec(infoToken(InfoTokenId.Minions).text, emptyList()) }, kind = ButtonKind.Primary, icon = Lucide.Eye)
                GrimButton(
                    "Enseñar faroles",
                    { c.card = ShowSpec(infoToken(InfoTokenId.NotInPlay).text, bluffs) },
                    kind = ButtonKind.Primary,
                    icon = Lucide.Eye,
                    enabled = game.demonBluffs.isNotEmpty(),
                )
                GrimButton("Cambiar faroles", { c.editingBluffs = true }, kind = ButtonKind.Ghost, small = true, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        if (stepToken != null && character != null) {
            WrapButton(
                "Enseñar «${infoToken(stepToken.token).text}»",
                {
                    if (stepToken.more != null) {
                        c.nightComposing = InfoTokenDraft(token = stepToken.token, characters = listOf(character), note = stepToken.more)
                    } else {
                        c.card = ShowSpec(infoToken(stepToken.token).text, listOf(character))
                    }
                },
                icon = Lucide.Eye,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // El Filósofo elige un personaje bueno: pasa a tener su ficha.
        if (character?.id == "philosopher" && step.playerIds.isNotEmpty()) {
            GrimButton("Elegir personaje…", { c.philosophising = true }, Modifier.fillMaxWidth().padding(top = 4.dp), kind = ButtonKind.Primary, icon = Lucide.Pointer)
        }
        // Lo secundario, en una fila al final: la guía de la wiki y enseñar cualquier personaje.
        if (step.kind == NightStepKind.Character) {
            Column(Modifier.padding(top = 6.dp)) {
                Divider()
                FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                    if (character != null) GrimButton("Guía", { c.guideOpen = true }, kind = ButtonKind.Ghost, icon = Lucide.BookOpen, small = true)
                    GrimButton("Enseñar al jugador…", { c.nightComposing = InfoTokenDraft() }, kind = ButtonKind.Ghost, icon = Lucide.Eye, small = true)
                }
            }
        }

        if (step.kind == NightStepKind.Dawn) DawnSection(c, game)
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Text(
        buildAnnotatedString {
            append(label)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Grim.textStrong)) { append(value) }
        },
        style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp),
    )
}

/** Amanecer: lo apuntado esta noche y quién ha muerto, para anunciarlo. */
@Composable
private fun DawnSection(c: GameController, game: Game) {
    // Lo que el registro ha apuntado esta noche (sin la línea de «Cae la noche»), para el amanecer.
    val nightLog = remember(game.log, game.phase) {
        val phase = phaseLabel(game.phase)
        game.log.filter { it.phase == phase && !it.text.startsWith("Cae la noche") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Lo apuntado esta noche, para anunciar el amanecer sin abrir el registro.
        if (nightLog.isNotEmpty()) {
            Column {
                Muted("Esta noche")
                for (l in nightLog) {
                    Text(l.text, Modifier.padding(vertical = 4.dp), style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp))
                    Divider()
                }
            }
        }
        Muted(
            "¿Quién ha muerto esta noche? Los que ha matado el Demonio ya están marcados; toca para cambiarlo. Los móviles de los jugadores lo verán al empezar el día.",
        )
        Chips {
            for (p in game.players) {
                key(p.id) {
                    // Sugerencia: quien tenga un recordatorio de muerte puesto esta noche.
                    val marked = p.reminders.any { it.label.contains("muert", ignoreCase = true) }
                    ChipButton(
                        p.name,
                        { c.change { g -> if (p.alive) killPlayer(g, p.id) else revivePlayer(g, p.id) } },
                        pressed = !p.alive,
                        danger = marked && p.alive,
                        icon = if (!p.alive) Lucide.Skull else null,
                    )
                }
            }
        }
    }
}

/** La lista de pasos de la noche, plegada (anterior, actual y siguiente) salvo que se pida entera. */
fun LazyListScope.nightListItems(c: GameController, game: Game, steps: List<NightStep>, index: Int, modifier: Modifier) {
    steps.forEachIndexed { i, s ->
        if (c.allSteps || kotlin.math.abs(i - index) <= 1) {
            item(key = "step:$i") {
                NightItem(s, i, current = i == index, done = i < index, names = s.playerIds.mapNotNull { id -> game.players.find { it.id == id }?.name }.joinToString(", "), onClick = { c.change { g -> setNightStep(g, i) } }, modifier = modifier)
            }
        }
    }
    if (steps.size > 3) {
        item(key = "steps-toggle") {
            Box(modifier) {
                GrimButton(
                    if (c.allSteps) "Solo el paso actual" else "Toda la noche (${steps.size} pasos)",
                    { c.allSteps = !c.allSteps },
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    kind = ButtonKind.Ghost,
                    icon = if (c.allSteps) Lucide.ChevronUp else Lucide.ChevronDown,
                    small = true,
                )
            }
        }
    }
}

@Composable
private fun NightItem(s: NightStep, i: Int, current: Boolean, done: Boolean, names: String, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.padding(vertical = 2.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (s.dead) 0.5f else 1f)
                .clip(shape)
                .then(if (current) Modifier.background(Grim.surface3).border(1.dp, Grim.gold, shape) else Modifier)
                .clickable(onClickLabel = "Ir al paso ${i + 1}", onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StepIcon(s, 30.dp)
            Text(
                s.title,
                Modifier.weight(1f),
                style = GrimType.body.copy(
                    fontSize = 16.sp,
                    color = if (done) Grim.muted else Grim.text,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (names.isNotEmpty()) {
                Text(names, Modifier.weight(1f, fill = false), style = GrimType.small.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Botones de la noche al pie: paso anterior y siguiente (o empezar el día en el último). */
@Composable
fun NightActionBar(c: GameController, index: Int, last: Boolean, modifier: Modifier = Modifier) {
    ActionBar(modifier) {
        GrimIconButton(Lucide.ChevronLeft, "Paso anterior", { c.change { g -> setNightStep(g, index - 1) } }, enabled = index > 0, size = 52.dp)
        if (last) {
            GrimButton("Empezar el día", { c.change(::startDay) }, actionButton(), kind = ButtonKind.Gold, icon = Lucide.Sun)
        } else {
            GrimButton("Siguiente", { c.change { g -> setNightStep(g, index + 1) } }, actionButton(), kind = ButtonKind.Primary, icon = Lucide.ChevronRight)
        }
    }
}

/** Hojas y tarjetas que se abren desde la noche (guía, enseñar, Filósofo). */
@Composable
fun NightSheets(c: GameController, game: Game) {
    val step = c.step
    val character = step?.character
    if (c.guideOpen && character != null) {
        GrimSheet("Guía de ${character.name}", { c.guideOpen = false }) {
            CharacterGuide(character.id, GuideAudience.Storyteller)
        }
    }
    c.nightComposing?.let { draft ->
        InfoTokenSheet(
            scriptCharacters = c.scriptCharacters,
            suggested = listOfNotNull(character),
            bluffs = game.demonBluffs.mapNotNull { c.characters[it] },
            initial = draft,
            onClose = { c.nightComposing = null },
        )
    }
    if (c.philosophising && step != null) {
        GrimSheet("¿Qué personaje elige el Filósofo?", { c.philosophising = false }) {
            CharacterPicker(
                characters = c.scriptCharacters.filter { it.team == Team.Townsfolk || it.team == Team.Outsider },
                selected = emptyList(),
                searchable = true,
                onToggle = { id ->
                    val philosopherId = step.playerIds.firstOrNull() ?: return@CharacterPicker
                    var message = ""
                    c.change { g ->
                        val result = philosopherChoose(g, philosopherId, id, c.characters)
                        message = result.message
                        result.game
                    }
                    c.philosophising = false
                    if (message.isNotEmpty()) c.toast(message)
                },
            )
        }
    }
    c.card?.let { spec -> ShowCard(spec.characters, onClose = { c.card = null }, heading = spec.heading) }
}

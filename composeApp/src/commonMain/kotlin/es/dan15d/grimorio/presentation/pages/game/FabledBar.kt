package es.dan15d.grimorio.presentation.pages.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.application.usecases.GuideAudience
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.entities.isStorytellerTeam
import es.dan15d.grimorio.domain.entities.label
import es.dan15d.grimorio.domain.services.DJINN
import es.dan15d.grimorio.domain.services.gameCharacterIds
import es.dan15d.grimorio.domain.services.jinxesAmong
import es.dan15d.grimorio.domain.services.toggleFabled
import es.dan15d.grimorio.presentation.components.Badge
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.CharacterGuide
import es.dan15d.grimorio.presentation.components.CharacterPicker
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimIconButton
import es.dan15d.grimorio.presentation.components.GrimSheet
import es.dan15d.grimorio.presentation.components.JinxList
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.ShowCard
import es.dan15d.grimorio.presentation.components.SpecialCases
import es.dan15d.grimorio.presentation.components.Token
import es.dan15d.grimorio.presentation.components.color
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/**
 * Fábulas y Lóricos en juego, fuera del círculo como en el Grimorio físico. Si no hay ninguno
 * (ni incompatibilidades que avisar), no ocupa sitio: se añaden desde el menú de la partida.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FabledBar(c: GameController, fabledIds: List<String>, game: Game) {
    val characters = c.characters
    val inPlay = fabledIds.mapNotNull { characters[it] }
    val gameJinxes = remember(game.players, fabledIds, game.extraCharacterIds) { jinxesAmong(gameCharacterIds(game), c.app.catalog.jinxes()) }
    val jinxWarning = gameJinxes.isNotEmpty() && inPlay.none { it.id == DJINN }
    if (inPlay.isEmpty() && !jinxWarning) return

    FlowRow(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (ch in inPlay) {
            key(ch.id) {
                Column(
                    Modifier.widthIn(max = 76.dp).clickable(role = Role.Button, onClickLabel = ch.name) { c.fabledOpen = ch },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Token(ch, size = 52.dp)
                    Text(ch.name, style = GrimType.small.copy(fontSize = 12.sp, lineHeight = 14.sp, color = ch.team.color, textAlign = TextAlign.Center))
                }
            }
        }
        val label = "${Team.Fabled.label.plural} y ${Team.Loric.label.plural}"
        if (inPlay.isNotEmpty()) {
            GrimIconButton(Lucide.Plus, label, { c.addingFabled = true }, modifier = Modifier.align(Alignment.CenterVertically))
        } else {
            GrimButton(label, { c.addingFabled = true }, Modifier.align(Alignment.CenterVertically), kind = ButtonKind.Ghost, icon = Lucide.Plus, small = true)
        }
        if (jinxWarning) {
            Text(
                "${gameJinxes.size} ${if (gameJinxes.size == 1) "incompatibilidad" else "incompatibilidades"}",
                Modifier
                    .align(Alignment.CenterVertically)
                    .clickable(role = Role.Button) { c.fabledOpen = characters[DJINN] }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = GrimType.label.copy(color = Grim.fabled),
            )
        }
    }
}

/** Hojas de las Fábulas y Lóricos: ponerlos o quitarlos, y la ficha de uno. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FabledSheets(c: GameController, game: Game) {
    val characters = c.characters
    if (c.addingFabled) {
        GrimSheet("Fábulas y Lóricos", { c.addingFabled = false }) {
            Muted("Anúncialos a la mesa al ponerlos o quitarlos. Los jugadores los ven en su móvil.")
            CharacterPicker(
                characters = remember { characters.values.filter { isStorytellerTeam(it.team) } },
                teams = listOf(Team.Fabled, Team.Loric),
                selected = game.fabledIds,
                onToggle = { id -> characters[id]?.let { ch -> c.change { g -> toggleFabled(g, ch) } } },
            )
        }
    }

    val open = c.fabledOpen
    if (open != null) {
        val gameJinxes = remember(game.players, game.fabledIds) { jinxesAmong(gameCharacterIds(game), c.app.catalog.jinxes()) }
        GrimSheet(open.name, { c.fabledOpen = null }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Token(open, size = 76.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(open.team.label.singular, open.team.color)
                    Text(open.ability, style = GrimType.body)
                }
            }
            if (open.reminders.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muted("Poner recordatorio:")
                    Chips {
                        for (label in open.reminders.distinct()) {
                            key(label) {
                                ChipButton(label, {
                                    c.startPick(Pick.Reminder(open.id, label))
                                    c.fabledOpen = null
                                }, reminder = true)
                            }
                        }
                    }
                }
            }
            if (open.id == DJINN) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Incompatibilidades en juego", style = GrimType.heading.copy(fontSize = 19.sp))
                    if (gameJinxes.isNotEmpty()) JinxList(gameJinxes, characters) else Muted("Ninguna entre los personajes en juego.")
                }
            }
            SpecialCases(open.id, gameCharacterIds(game))
            CharacterGuide(open.id, GuideAudience.Storyteller)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GrimButton("Enseñar", { c.fabledShowing = true }, icon = Lucide.Eye)
                if (open.id in game.fabledIds) {
                    GrimButton("Quitar de la partida", {
                        c.change { g -> toggleFabled(g, open) }
                        c.fabledOpen = null
                    }, kind = ButtonKind.Ghost, icon = Lucide.Trash2)
                } else {
                    GrimButton("Poner en juego", {
                        c.change { g -> toggleFabled(g, open) }
                        c.fabledOpen = null
                    }, kind = ButtonKind.Ghost, icon = Lucide.Plus)
                }
            }
        }
        if (c.fabledShowing) ShowCard(listOf(open), onClose = { c.fabledShowing = false }, showAbility = true)
    }
}

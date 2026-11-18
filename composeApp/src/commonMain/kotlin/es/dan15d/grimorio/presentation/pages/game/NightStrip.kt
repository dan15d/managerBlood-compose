package es.dan15d.grimorio.presentation.pages.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.services.NightStep
import es.dan15d.grimorio.domain.services.NightStepKind
import es.dan15d.grimorio.domain.services.choiceReminder
import es.dan15d.grimorio.domain.services.infoHints
import es.dan15d.grimorio.domain.services.noteInfoGiven
import es.dan15d.grimorio.domain.services.setNightStep
import es.dan15d.grimorio.domain.services.startDay
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.color
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/**
 * Resumen del paso de la noche justo bajo el círculo, solo en el móvil: a quién despertar y el
 * botón que toca (elegir, dar la información o pasar), para no bajar al panel y volver a subir.
 */
@Composable
fun NightStrip(c: GameController, game: Game, steps: List<NightStep>, index: Int, step: NightStep) {
    val last = index == steps.size - 1
    val character = step.character
    fun player(id: String) = game.players.find { it.id == id }
    val names = step.playerIds.mapNotNull { player(it)?.name }.joinToString(", ")
    val chooser = if (character != null && step.playerIds.isNotEmpty()) choiceReminder(character.id, game.phase.number == 1) else null
    val hints = remember(game, step) { if (character != null) infoHints(game, character, c.characters) else emptyList() }
    val next = { c.change { g -> if (last) startDay(g) else setNightStep(g, index + 1) } }
    val shape = RoundedCornerShape(12.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(shape)
            .background(Grim.surface)
            .border(1.dp, Grim.lineStrong, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Ver el paso ${step.title}") { c.scrollTo(ScrollTarget.Panel) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StepIcon(step, 40.dp)
            Column {
                Text(
                    step.title,
                    style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = character?.team?.color ?: Grim.textStrong),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    names.ifEmpty { if (step.kind == NightStepKind.Character) "Nadie en juego" else "Paso ${index + 1} de ${steps.size}" },
                    style = GrimType.small.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val button = Modifier.widthIn(max = 200.dp)
        when {
            chooser != null && character != null -> GrimButton(
                "Toca a quién elige",
                { c.startPick(Pick.Reminder(character.id, chooser, advance = true)) },
                button,
                kind = ButtonKind.Primary,
                icon = Lucide.Pointer,
                small = true,
            )
            hints.isNotEmpty() && character != null -> GrimButton(
                if (last) "Dado" else "Dado · Siguiente",
                {
                    c.change { g ->
                        val noted = noteInfoGiven(g, hints.map { h -> "${character.name} (${player(h.playerId)?.name ?: "?"}): ${h.text}" })
                        if (last) noted else setNightStep(noted, index + 1)
                    }
                },
                button,
                kind = ButtonKind.Primary,
                icon = Lucide.Check,
                small = true,
            )
            last -> GrimButton("Empezar el día", next, button, kind = ButtonKind.Gold, icon = Lucide.Sun, small = true)
            else -> GrimButton("Siguiente", next, button, icon = Lucide.ChevronRight, small = true)
        }
    }
}

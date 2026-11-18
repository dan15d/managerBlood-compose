package es.dan15d.grimorio.presentation.pages.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.domain.entities.Alignment as Side
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.ReminderToken
import es.dan15d.grimorio.domain.entities.label
import es.dan15d.grimorio.domain.services.addReminder
import es.dan15d.grimorio.domain.services.killPlayer
import es.dan15d.grimorio.domain.services.removeReminder
import es.dan15d.grimorio.domain.services.revivePlayer
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimSheet
import es.dan15d.grimorio.presentation.components.Token
import es.dan15d.grimorio.presentation.components.color
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/** Los recordatorios genéricos que más se ponen a mano. */
private val QUICK_GENERIC = listOf("Envenenado", "Borracho")

private data class QuickOption(val source: Character?, val label: String)

/**
 * Mini-hoja al tocar una ficha del círculo: lo que se hace cada dos por tres (matar, poner o quitar
 * un recordatorio) en un toque, y la ficha entera solo si hace falta. Cada acción cierra la hoja.
 */
@Composable
fun QuickSheet(c: GameController, player: Player, onOpenSheet: () -> Unit, onClose: () -> Unit) {
    val character = c.characters[player.characterId]
    val shown = player.shownCharacterId?.let { c.characters[it] }
    // Los recordatorios de su personaje (y del que cree ser), sin repetir, y después los genéricos.
    val options = run {
        val seen = mutableSetOf<String>()
        (listOfNotNull(character, shown).flatMap { ch -> ch.reminders.map { QuickOption(ch, it) } } + QUICK_GENERIC.map { QuickOption(null, it) })
            .filter { seen.add(it.label) }
    }

    fun act(mutate: (Game) -> Game) {
        c.change(mutate)
        onClose()
    }

    GrimSheet("Acciones de ${player.name}", onClose, titleContent = { PlayerTitle(player) }) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Ver la ficha de ${player.name}", onClick = onOpenSheet),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Token(character, alignment = player.alignment, size = 56.dp, dead = !player.alive)
            Column(Modifier.weight(1f)) {
                Text(
                    character?.name ?: "Sin personaje",
                    style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = character?.team?.color ?: Grim.textStrong),
                )
                Text(
                    (character?.team?.label?.singular ?: "") +
                        (if (player.alignment == Side.Evil) " · malvado" else "") +
                        (if (!player.alive) " · muerto" else "") +
                        (if (shown != null) " · cree ser ${shown.name}" else ""),
                    style = GrimType.small.copy(fontSize = 14.sp, lineHeight = 19.sp),
                )
            }
            Icon(Lucide.ChevronRight, null, tint = Grim.muted, modifier = Modifier.size(22.dp))
        }

        if (player.reminders.isNotEmpty()) {
            Chips {
                for (r in player.reminders) key(r.id) { ReminderChip(r.label, onRemove = { act { g -> removeReminder(g, player.id, r.id) } }) }
            }
        }

        Chips {
            for (option in options) {
                key(option.label) {
                    ChipButton(
                        "+ ${option.label}",
                        {
                            act { g ->
                                addReminder(g, player.id, ReminderToken(id = c.app.newId(), sourceCharacterId = option.source?.id ?: "custom", label = option.label))
                            }
                        },
                        reminder = option.source != null,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GrimButton(
                if (player.alive) "Matar" else "Revivir",
                { act { g -> if (player.alive) killPlayer(g, player.id) else revivePlayer(g, player.id) } },
                Modifier.weight(1f),
                kind = if (player.alive) ButtonKind.Danger else ButtonKind.Gold,
                icon = if (player.alive) Lucide.Skull else Lucide.HeartPulse,
            )
            GrimButton("Ver ficha", onOpenSheet, Modifier.weight(1f))
        }
    }
}

package es.dan15d.grimorio.presentation.pages.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.Chip
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimSheet
import es.dan15d.grimorio.presentation.components.GrimTextField
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.SectionLabel
import es.dan15d.grimorio.presentation.components.clock
import es.dan15d.grimorio.presentation.components.color
import es.dan15d.grimorio.presentation.components.Alarm
import es.dan15d.grimorio.presentation.components.parseMillis
import es.dan15d.grimorio.presentation.components.rememberNow
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

// Piezas de la pantalla del jugador (PlayerPage).

// ---------- Fase ----------

internal enum class PillKind { Night, Day, Plain }

/** Píldora de la fase (como .phase-pill): azul de noche, dorada de día. */
@Composable
internal fun PhasePill(text: String, kind: PillKind, icon: ImageVector? = null) {
    val shape = CircleShape
    val (brush, border, color) = when (kind) {
        PillKind.Night -> Triple(Brush.verticalGradient(listOf(Color(0xFF1B2240), Color(0xFF10142A))), Color(0xFF3A4A8A), Color(0xFFC9D4FF))
        PillKind.Day -> Triple(Brush.verticalGradient(listOf(Color(0xFF4A3618), Color(0xFF2B1F0E))), Grim.gold, Grim.goldBright)
        PillKind.Plain -> Triple(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)), Grim.lineStrong, Grim.text)
    }
    Row(
        Modifier.clip(shape).background(brush).border(1.dp, border, shape).padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            text.uppercase(),
            style = TextStyle(fontFamily = GrimType.fonts.ui, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp, color = color),
            maxLines = 1,
        )
    }
}

internal fun isNight(phaseLabel: String?): Boolean = phaseLabel?.startsWith("Noche") == true

// ---------- Ganador ----------

/** Franja de final de partida (como .banner--good / --evil). */
@Composable
internal fun WinnerBanner(winner: Side?, modifier: Modifier = Modifier) {
    val color = if (winner == Side.Good) Grim.good else Grim.evil
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = if (winner == Side.Good) 0.2f else 0.25f), Color.Transparent)))
            .border(1.dp, color, shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(if (winner == Side.Good) "Ha ganado el Bien" else "Ha ganado el Mal", style = GrimType.heading.copy(color = Grim.textStrong))
    }
}

// ---------- Pestañas ----------

/** Pestañas a todo lo ancho (como .tabs--page), con la marcada subrayada en oro. */
@Composable
internal fun PageTabs(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Grim.surface2)
            .drawBehind {
                drawLine(Grim.line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx())
            },
        content = content,
    )
}

@Composable
internal fun RowScope.PageTab(text: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Grim.textStrong else Grim.muted
    Row(
        Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .drawBehind {
                if (selected) {
                    val y = size.height - 1.dp.toPx()
                    drawLine(Grim.goldBright, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                }
            }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(text.uppercase(), style = TextStyle(fontFamily = GrimType.fonts.ui, fontSize = 13.sp, letterSpacing = 1.sp, color = color))
    }
}

// ---------- Cuenta atrás ----------

/** La cuenta atrás del Narrador (debate, nominaciones). Al acabar vibra; se va sola al minuto. */
@Composable
internal fun Countdown(endsAt: String) {
    val now = rememberNow()
    val end = remember(endsAt) { runCatching { parseMillis(endsAt) }.getOrNull() } ?: return
    val left = end - now
    Alarm(endsAt, now, sound = false)
    if (left < -60_000) return
    val over = left <= 0
    val alpha = if (over) {
        val transition = rememberInfiniteTransition(label = "tiempo")
        transition.animateFloat(1f, 0.35f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "parpadeo").value
    } else {
        1f
    }
    Row(
        Modifier.border(BorderStroke(1.dp, Grim.lineStrong), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Lucide.Hourglass, null, tint = Grim.goldBright, modifier = Modifier.size(16.dp).alpha(alpha))
        Text(
            if (over) "¡Tiempo!" else clock(left),
            Modifier.alpha(alpha),
            style = TextStyle(fontFamily = GrimType.fonts.ui, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Grim.goldBright),
        )
    }
}

/** «7 vivos · 4 votos» en el centro de la mesa (como .stat-line). */
@Composable
internal fun StatLine(alive: Int) {
    val strong = SpanStyle(fontFamily = GrimType.fonts.display, fontSize = 19.sp, color = Grim.textStrong)
    Text(
        buildAnnotatedString {
            withStyle(strong) { append("$alive") }
            append(" vivos · ")
            withStyle(strong) { append("${(alive + 1) / 2}") }
            append(" votos")
        },
        style = TextStyle(fontFamily = GrimType.fonts.body, fontSize = 14.sp, color = Grim.muted),
        maxLines = 1,
    )
}

// ---------- Notas ----------

/** Resumen de mis notas sobre un jugador, debajo de su nombre en la lista. */
@Composable
internal fun NoteSummary(note: SeatNote, claimName: String?) {
    val parts = listOfNotNull(
        claimName?.let { "dice $it" },
        note.guess?.let { if (it == Side.Good) "creo bueno" else "creo malo" },
        note.text?.trim()?.takeIf { it.isNotEmpty() },
    )
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        style = GrimType.small.copy(fontSize = 14.sp, lineHeight = 19.sp, color = if (note.guess == Side.Evil) Grim.demon else Grim.muted),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Mis notas sobre otro jugador: qué dice ser, qué creo que es y lo que quiera apuntar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotesSheet(name: String, note: SeatNote, script: List<Character>, onChange: (SeatNote) -> Unit, onClose: () -> Unit) {
    val claimable = remember(script) { script.filter { it.team != Team.Fabled && it.team != Team.Loric } }
    GrimSheet(title = "Notas sobre $name", onDismiss = onClose) {
        Muted("Solo las ves tú: se guardan en este móvil y no llegan al Narrador.")

        ClaimSelect(note.claim, claimable) { onChange(note.copy(claim = it)) }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("Creo que es")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((guess, label) in listOf(null to "No sé", Side.Good to "Bueno", Side.Evil to "Malo")) {
                    key(label) { Chip(label, pressed = note.guess == guess) { onChange(note.copy(guess = guess)) } }
                }
            }
        }

        GrimTextField(
            note.text ?: "",
            { onChange(note.copy(text = it)) },
            label = "Notas",
            placeholder = "Qué información dijo, a quién nominó, a quién votó…",
            singleLine = false,
            minLines = 3,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GrimButton("Listo", onClose, kind = ButtonKind.Gold)
        }
    }
}

/** «Dice ser»: desplegable con los personajes del guion (el select de la web). */
@Composable
private fun ClaimSelect(claim: String?, characters: List<Character>, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = characters.firstOrNull { it.id == claim }
    val shape = RoundedCornerShape(8.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("Dice ser")
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(Grim.bg2)
                    .border(1.dp, if (open) Grim.gold else Grim.lineStrong, shape)
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    current?.name ?: "— No lo ha dicho —",
                    Modifier.weight(1f),
                    style = GrimType.body.copy(color = current?.team?.color ?: Grim.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Lucide.ChevronDown, null, tint = Grim.muted, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                containerColor = Grim.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Grim.lineStrong),
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                DropdownMenuItem(
                    text = { Text("— No lo ha dicho —", style = GrimType.body) },
                    onClick = {
                        open = false
                        onSelect(null)
                    },
                )
                for (c in characters) {
                    key(c.id) {
                        DropdownMenuItem(
                            text = { Text(c.name, style = GrimType.body.copy(color = c.team.color)) },
                            modifier = if (c.id == claim) Modifier.background(Grim.surface2) else Modifier,
                            onClick = {
                                open = false
                                onSelect(c.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

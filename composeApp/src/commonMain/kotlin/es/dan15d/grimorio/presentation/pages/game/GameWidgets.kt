package es.dan15d.grimorio.presentation.pages.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.domain.entities.Alignment as Side
import es.dan15d.grimorio.presentation.components.SectionLabel
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

// Piezas pequeñas de la pantalla de la partida (las clases .note, .alert, .banner, .chip--reminder,
// .action-bar… de app.css). Solo las usa la partida; las comunes están en components/.

private val radiusSm = RoundedCornerShape(8.dp)
private val radius = RoundedCornerShape(12.dp)

enum class NoteKind { Plain, Warning, Info }

/** Aviso dentro de la tarjeta: una línea con filete a la izquierda, sin caja (como .note). */
@Composable
fun GameNote(text: String, kind: NoteKind = NoteKind.Plain, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val line = when (kind) {
        NoteKind.Warning -> Grim.gold
        NoteKind.Info -> Color(0xCC5AA2FF)
        NoteKind.Plain -> Grim.lineStrong
    }
    val color = if (kind == NoteKind.Warning) Grim.goldBright else Grim.text
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 4.dp)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(line))
        Row(Modifier.padding(start = 10.dp, top = 2.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.padding(top = 3.dp).size(16.dp))
            Text(text, style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp, color = color))
        }
    }
}

enum class AlertKind { Info, Warning, Error }

/** Caja de aviso con borde de color (como .alert). */
@Composable
fun GameAlert(kind: AlertKind, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val (border, background) = when (kind) {
        AlertKind.Info -> Color(0x735AA2FF) to Color(0x145AA2FF)
        AlertKind.Warning -> Color(0x80E8B84A) to Color(0x14E8B84A)
        AlertKind.Error -> Color(0x99E0242F) to Color(0x269B111E)
    }
    Column(
        modifier.fillMaxWidth().clip(radiusSm).background(background).border(1.dp, border, radiusSm).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** Aviso grande de fin de partida o de victoria posible (como .banner), del color del bando. */
@Composable
fun GameBanner(winner: Side?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val (border, brush) = when (winner) {
        Side.Good -> Grim.good to Brush.horizontalGradient(listOf(Color(0x335AA2FF), Color.Transparent))
        Side.Evil -> Grim.evil to Brush.horizontalGradient(listOf(Color(0x40E0242F), Color.Transparent))
        null -> Grim.gold to Brush.horizontalGradient(listOf(Color(0x2EC9A35B), Color(0x337C48C4)))
    }
    Column(
        modifier.fillMaxWidth().clip(radius).background(brush).border(1.dp, border, radius).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Fila de píldoras que salta de línea (como .chips). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

private val parchmentBorder = Color(0xFFC9B48A)

/**
 * Recordatorio puesto (como .chip--static.chip--reminder): pergamino, o rojo si quita la habilidad.
 * Con [onRemove], lleva la × para quitarlo.
 */
@Composable
fun ReminderChip(label: String, danger: Boolean = false, tiny: Boolean = false, onRemove: (() -> Unit)? = null) {
    val background = if (danger) Color(0xFFF3C9C9) else Grim.parchment
    val border = if (danger) Grim.bloodBright else parchmentBorder
    val color = if (danger) Color(0xFF5A0D14) else Grim.ink
    Row(
        Modifier
            .heightIn(min = if (tiny) 26.dp else 36.dp)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .padding(start = if (tiny) 10.dp else 14.dp, end = if (onRemove != null) 2.dp else if (tiny) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = GrimType.label.copy(fontSize = if (tiny) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold, color = color))
        if (onRemove != null) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Quitar $label", onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.X, "Quitar $label", tint = color, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Píldora pulsable (como .chip). [reminder]: de pergamino (recordatorio de un personaje).
 * [pressed]: marcada. [danger]: sugerida con borde rojo (muerto esta noche).
 */
@Composable
fun ChipButton(
    text: String,
    onClick: () -> Unit,
    reminder: Boolean = false,
    pressed: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    wrap: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = if (wrap) RoundedCornerShape(18.dp) else CircleShape
    val background = when {
        reminder -> Grim.parchment
        pressed -> Grim.surface3
        else -> Grim.surface
    }
    val border = when {
        danger -> BorderStroke(2.dp, Grim.bloodBright)
        pressed -> BorderStroke(1.dp, Grim.goldBright)
        reminder -> BorderStroke(1.dp, parchmentBorder)
        else -> BorderStroke(1.dp, Grim.lineStrong)
    }
    val color = when {
        reminder -> Grim.ink
        pressed -> Grim.textStrong
        else -> Grim.text
    }
    Row(
        modifier
            .heightIn(min = 36.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(background)
            .border(border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (pressed) Grim.bloodBright else color, modifier = Modifier.size(16.dp))
        Text(
            text,
            style = GrimType.label.copy(fontSize = 15.sp, fontWeight = if (reminder) FontWeight.SemiBold else FontWeight.Medium, color = color),
            maxLines = if (wrap) 4 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Barra de botones grandes al pie (como .action-bar): todos a partes iguales. [sheet]: dentro de una
 * hoja, sin fondo propio.
 */
@Composable
fun ActionBar(modifier: Modifier = Modifier, sheet: Boolean = false, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (sheet) Modifier.padding(top = 8.dp) else Modifier.background(Brush.verticalGradient(0f to Color.Transparent, 0.25f to Grim.surface)))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Modificador de los botones de la barra: a partes iguales y altos para el pulgar. */
fun RowScope.actionButton(): Modifier = Modifier.weight(1f).heightIn(min = 52.dp)

/**
 * Botón de varias líneas (los textos largos, como «Enseñar "Este personaje te ha elegido"»),
 * con el aspecto del .btn normal.
 */
@Composable
fun WrapButton(text: String, onClick: () -> Unit, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(Grim.surface2)
            .border(1.dp, Grim.lineStrong, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = Grim.text, modifier = Modifier.size(20.dp))
        Text(
            text.uppercase(),
            style = TextStyle(fontFamily = GrimType.fonts.ui, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 17.sp, letterSpacing = 1.1.sp, color = Grim.text),
        )
    }
}

/** Opción de un desplegable: valor y texto. */
data class SelectOption(val value: String, val label: String)

/**
 * Desplegable con su etiqueta (como <label class="field"><select>). Con [selected] null enseña
 * [placeholder]; elegir una opción llama a [onSelect] con su valor.
 */
@Composable
fun SelectField(label: String, placeholder: String, options: List<SelectOption>, selected: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val current = options.find { it.value == selected }?.label ?: placeholder
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(label)
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(Grim.bg2)
                    .border(1.dp, Grim.lineStrong, shape)
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(current, Modifier.weight(1f), style = GrimType.body.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Lucide.ChevronDown, null, tint = Grim.muted, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                containerColor = Grim.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Grim.lineStrong),
            ) {
                for (option in options) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                style = GrimType.body.copy(fontSize = 16.sp, color = if (option.value == selected) Grim.goldBright else Grim.text),
                            )
                        },
                        onClick = {
                            open = false
                            onSelect(option.value)
                        },
                    )
                }
            }
        }
    }
}

/** Texto de una línea en el estilo de los títulos de la tarjeta (h2 de la web). */
@Composable
fun CardTitle(text: String, color: Color = Grim.goldBright, modifier: Modifier = Modifier) {
    Text(text, modifier, style = GrimType.heading.copy(color = color))
}

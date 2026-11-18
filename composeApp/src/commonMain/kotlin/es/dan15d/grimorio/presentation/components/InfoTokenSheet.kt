package es.dan15d.grimorio.presentation.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.domain.entities.Alignment as Side
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.services.INFO_TOKENS
import es.dan15d.grimorio.domain.services.InfoTokenId
import es.dan15d.grimorio.domain.services.infoToken
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

data class InfoTokenDraft(
    val token: InfoTokenId? = null,
    val characters: List<Character> = emptyList(),
    val alignment: Side? = null,
    /** Aviso de lo que falta (el personaje del que debe estar loco…): abre ya la lista de personajes. */
    val note: String? = null,
)

/** Elección de mensaje: una ficha del juego, «Otro mensaje…» (custom) o nada. */
private sealed interface Choice {
    data class Token(val id: InfoTokenId) : Choice
    data object Custom : Choice
}

/**
 * Enseñar al jugador una ficha de información («ESTE PERSONAJE TE HA ELEGIDO»…), con los
 * personajes que la acompañan y, para «TÚ ERES», el pulgar del bando. Al cerrar la tarjeta se
 * vuelve aquí con todo puesto, para enseñar la siguiente parte sin empezar de cero.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoTokenSheet(
    scriptCharacters: List<Character>,
    onClose: () -> Unit,
    suggested: List<Character> = emptyList(),
    bluffs: List<Character> = emptyList(),
    initial: InfoTokenDraft? = null,
) {
    var choice by remember { mutableStateOf<Choice?>(initial?.token?.let { Choice.Token(it) }) }
    var custom by remember { mutableStateOf("") }
    var chars by remember { mutableStateOf(initial?.characters ?: emptyList()) }
    var alignment by remember { mutableStateOf(initial?.alignment) }
    var picking by remember { mutableStateOf(initial?.note != null) }
    // Con un mensaje elegido, la lista se pliega a ese: lo siguiente (personajes, bando) queda a la vista.
    var listOpen by remember { mutableStateOf(initial?.token == null) }
    var showing by remember { mutableStateOf(false) }

    val heading = when (val c = choice) {
        is Choice.Custom -> custom.trim().ifEmpty { null }
        is Choice.Token -> infoToken(c.id).text
        null -> null
    }
    val thumb = if (choice == Choice.Token(InfoTokenId.YouAre)) alignment else null
    val empty = heading == null && chars.isEmpty() && thumb == null
    fun has(c: Character) = chars.any { it.id == c.id }
    fun add(c: Character) {
        if (!has(c)) chars = chars + c
    }
    fun remove(id: String) {
        chars = chars.filter { it.id != id }
    }
    val missingBluffs = bluffs.filter { !has(it) }

    fun choose(next: Choice) {
        if (!listOpen) {
            listOpen = true
            return
        }
        if (next == choice) {
            choice = null
            return
        }
        choice = next
        listOpen = false
        // Los faroles son lo que casi siempre acompaña a este mensaje: se ponen solos (se pueden quitar).
        if (next == Choice.Token(InfoTokenId.NotInPlay) && chars.isEmpty()) chars = bluffs
    }

    GrimSheet("Enseñar al jugador", onClose) {
        // Así lo verá el jugador; tocarla también la enseña.
        val shape = RoundedCornerShape(12.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 110.dp)
                .clip(shape)
                .background(Grim.bg2)
                .border(1.dp, Grim.lineStrong, shape)
                .clickable(enabled = !empty) { showing = true }
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            if (empty) {
                Muted("Elige un mensaje, un personaje o los dos", center = true)
            } else {
                heading?.let { Text(it, style = GrimType.heading.copy(color = Grim.textStrong, textAlign = TextAlign.Center)) }
                if (chars.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        for (c in chars) Token(c, size = if (chars.size > 3) 34.dp else 44.dp)
                    }
                }
                thumb?.let { Icon(if (it == Side.Good) Lucide.ThumbsUp else Lucide.ThumbsDown, null, tint = it.color, modifier = Modifier.size(34.dp)) }
            }
        }

        initial?.note?.let { Text(it, style = GrimType.small.copy(color = Grim.fabled)) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Mensaje", Modifier.weight(1f))
            if (!listOpen) GrimButton("Cambiar", { listOpen = true }, kind = ButtonKind.Ghost, small = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in INFO_TOKENS) {
                val mine = Choice.Token(t.id)
                if (listOpen || choice == mine) InfoChoiceRow(t.text, t.use, choice == mine) { choose(mine) }
            }
            if (listOpen || choice == Choice.Custom) {
                InfoChoiceRow("Otro mensaje…", "Escribe tú lo que quieras enseñar", choice == Choice.Custom) { choose(Choice.Custom) }
            }
            if (choice == Choice.Custom) {
                GrimTextField(custom, { custom = it.take(80) }, placeholder = "Por ejemplo: ¿Usas tu habilidad?")
            }
        }

        if (choice == Choice.Token(InfoTokenId.YouAre)) {
            SectionLabel("Bando")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Sin pulgar", alignment == null) { alignment = null }
                Chip("Bueno", alignment == Side.Good, Lucide.ThumbsUp) { alignment = Side.Good }
                Chip("Malo", alignment == Side.Evil, Lucide.ThumbsDown) { alignment = Side.Evil }
            }
        }

        SectionLabel("Personajes")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in chars) {
                Row(
                    Modifier.clip(CircleShape).background(Grim.surface2).border(1.dp, Grim.lineStrong, CircleShape).padding(start = 4.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Token(c, size = 26.dp)
                    Text(c.name, style = GrimType.label)
                    GrimIconButton(Lucide.X, "Quitar ${c.name}", { remove(c.id) }, size = 32.dp)
                }
            }
            for (c in suggested.filter { !has(it) }) Chip(c.name, false, Lucide.Plus, suggest = true) { add(c) }
            if (missingBluffs.isNotEmpty() && choice == Choice.Token(InfoTokenId.NotInPlay)) {
                Chip("Faroles del Demonio", false, Lucide.Plus, suggest = true) { missingBluffs.forEach(::add) }
            }
            Chip(if (picking) "Listo" else "Otro personaje…", false, if (picking) Lucide.Check else Lucide.Plus) { picking = !picking }
        }
        if (picking) {
            CharacterPicker(
                characters = scriptCharacters,
                selected = chars.map { it.id },
                searchable = true,
                onToggle = { id ->
                    scriptCharacters.find { it.id == id }?.let { c -> if (has(c)) remove(id) else add(c) }
                },
            )
        }

        GrimButton("Enseñar", { showing = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Primary, icon = Lucide.Eye, enabled = !empty)
    }
    if (showing) ShowCard(chars, onClose = { showing = false }, heading = heading, alignment = thumb)
}

@Composable
private fun InfoChoiceRow(text: String, use: String, pressed: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (pressed) Grim.surface3 else Grim.surface2)
            .border(1.dp, if (pressed) Grim.gold else Grim.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text.uppercase(), style = GrimType.label.copy(color = Grim.textStrong))
            Text(use, style = GrimType.small.copy(fontSize = 13.sp, lineHeight = 17.sp))
        }
        if (pressed) Icon(Lucide.Check, null, tint = Grim.goldBright, modifier = Modifier.size(20.dp))
    }
}

/** Píldora pulsable (como .chip): marcada con borde dorado. */
@Composable
fun Chip(
    text: String,
    pressed: Boolean,
    icon: ImageVector? = null,
    suggest: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val border = when {
        pressed -> Grim.goldBright
        suggest -> Grim.accentBright.copy(alpha = 0.6f)
        else -> Grim.lineStrong
    }
    Row(
        Modifier
            .heightIn(min = 36.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(if (pressed) Grim.surface3 else if (suggest) Grim.accentDeep.copy(alpha = 0.25f) else Grim.surface2)
            .border(1.dp, border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (pressed) Grim.goldBright else Grim.text, modifier = Modifier.size(16.dp))
        Text(text, style = GrimType.label.copy(color = if (pressed) Grim.textStrong else Grim.text))
    }
}

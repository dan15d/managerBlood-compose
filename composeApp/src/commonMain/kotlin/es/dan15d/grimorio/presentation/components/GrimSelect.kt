package es.dan15d.grimorio.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/** Opción de un [GrimSelect]: las desactivadas se ven pero no se pueden elegir (como <option disabled>). */
data class GrimOption<T>(val value: T, val label: String, val enabled: Boolean = true)

/**
 * Desplegable con aspecto de campo (como <select class="input">): enseña la opción elegida y,
 * al tocarlo, la lista. Sin nada elegido enseña [placeholder].
 */
@Composable
fun <T> GrimSelect(
    value: T?,
    options: List<GrimOption<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "— Elige —",
    labelContent: (@Composable () -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val current = options.firstOrNull { it.value == value }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (labelContent != null) labelContent() else if (label != null) SectionLabel(label)
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(Grim.bg2)
                    .border(1.dp, if (open) Grim.gold else Grim.lineStrong, shape)
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(start = 13.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    current?.label ?: placeholder,
                    Modifier.weight(1f),
                    style = GrimType.body.copy(color = if (current != null) Grim.text else Grim.muted),
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
            ) {
                for (option in options) {
                    val selected = option.value == value
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                style = GrimType.body.copy(color = if (selected) Grim.goldBright else if (option.enabled) Grim.text else Grim.muted.copy(alpha = 0.5f)),
                            )
                        },
                        trailingIcon = if (selected) ({ Icon(Lucide.Check, null, tint = Grim.goldBright, modifier = Modifier.size(18.dp)) }) else null,
                        enabled = option.enabled,
                        modifier = if (selected) Modifier.background(Grim.surface2) else Modifier,
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

package es.dan15d.grimorio.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.application.usecases.GuideAudience
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.label
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/**
 * Personajes de un guion por tipo, con su habilidad completa (como la hoja de personajes).
 * Al tocar uno se abre su guía para quien la lee: el Narrador o un jugador.
 */
@Composable
fun ScriptCharacterList(characters: List<Character>, audience: GuideAudience, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf<Character?>(null) }
    var big by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        for (team in TEAM_ORDER) {
            val list = characters.filter { it.team == team }
            if (list.isEmpty()) continue
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(team.label.plural, style = GrimType.heading.copy(color = team.color, fontSize = 19.sp))
                    Text("${list.size}", style = GrimType.label.copy(fontWeight = FontWeight.Bold))
                }
                Divider()
                for (c in list) {
                    val shape = RoundedCornerShape(8.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Grim.surface)
                            .border(1.dp, Grim.line, shape)
                            .clickable(onClickLabel = "Ver la guía de ${c.name}") { open = c }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Token(c, size = 48.dp)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Grim.textStrong))
                            Text(c.ability, style = GrimType.small.copy(fontSize = 14.sp, lineHeight = 19.sp))
                        }
                    }
                }
            }
        }
    }
    open?.let { c ->
        GrimSheet(c.name, onDismiss = { open = null }) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Token(c, size = 76.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(c.team.label.singular, c.team.color)
                    Text(c.ability, style = GrimType.body)
                }
            }
            GrimButton("Ver en grande", { big = true }, kind = ButtonKind.Ghost, small = true, icon = Lucide.Maximize2)
            key(c.id) { CharacterGuide(c.id, audience, openSummary = true) }
        }
        if (big) ShowCard(listOf(c), onClose = { big = false }, showAbility = true)
    }
}

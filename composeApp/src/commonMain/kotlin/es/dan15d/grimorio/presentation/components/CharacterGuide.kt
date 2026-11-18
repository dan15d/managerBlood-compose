package es.dan15d.grimorio.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.application.ports.GuideBlock
import es.dan15d.grimorio.application.ports.GuideSection
import es.dan15d.grimorio.application.usecases.GuideAudience
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/** Párrafos, listas y ejemplos de la guía. */
@Composable
private fun Blocks(blocks: List<GuideBlock>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in blocks) {
            when (b.kind) {
                "li" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = GrimType.body.copy(color = Grim.gold))
                    Text(b.text, style = GrimType.body.copy(fontSize = 16.sp, lineHeight = 23.sp))
                }
                "quote" -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Grim.lineStrong))
                    Text(b.text, Modifier.padding(start = 10.dp), style = GrimType.body.copy(fontSize = 16.sp, lineHeight = 23.sp, fontStyle = FontStyle.Italic, color = Grim.muted))
                }
                else -> Text(b.text, style = GrimType.body.copy(fontSize = 16.sp, lineHeight = 23.sp))
            }
        }
    }
}

/** Apartado plegable (como <details>). */
@Composable
fun Collapsible(title: String, initiallyOpen: Boolean = false, icon: ImageVector? = null, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(initiallyOpen) }
    val shape = RoundedCornerShape(8.dp)
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, Grim.line, shape).background(Grim.surface2.copy(alpha = 0.5f))) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(icon, null, tint = Grim.goldBright, modifier = Modifier.size(18.dp))
            Text(title, Modifier.weight(1f), style = GrimType.label.copy(color = Grim.goldBright))
            Icon(if (open) Lucide.ChevronUp else Lucide.ChevronDown, if (open) "Plegar" else "Desplegar", tint = Grim.muted, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(open) {
            Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) { content() }
        }
    }
}

/**
 * Guía de la wiki de un personaje, con solo los apartados de quien la lee.
 * Los apartados van plegados; con [openSummary] el resumen sale abierto.
 */
@Composable
fun CharacterGuide(characterId: String, audience: GuideAudience, openSummary: Boolean = false) {
    val app = LocalAppState.current.app ?: return
    val guide = rememberAsync(app, characterId, audience) { app.readGuide.execute(characterId, audience) }
    when {
        guide.loading -> Muted("Cargando la guía…")
        guide.error != null -> Muted("No se ha podido cargar la guía.")
        else -> {
            val data = guide.data ?: return
            if (data.sections.isEmpty()) return
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in data.sections) {
                    Collapsible(
                        s.title,
                        initiallyOpen = openSummary && s.section == GuideSection.Summary,
                        icon = if (s.section == GuideSection.HowToRun) Lucide.BookOpen else null,
                    ) { Blocks(s.blocks) }
                }
                data.quote?.let { Text(it, style = GrimType.body.copy(fontStyle = FontStyle.Italic, color = Grim.muted)) }
                Muted("Guía de la wiki de Villacuervos.")
            }
        }
    }
}

/**
 * Incompatibilidades e interacciones de la wiki de un personaje con los demás en juego.
 * Solo para el Narrador: dicen qué personajes hay en la partida.
 */
@Composable
fun SpecialCases(characterId: String, inPlayIds: List<String>) {
    val app = LocalAppState.current.app ?: return
    val cases = rememberAsync(app, characterId, inPlayIds.joinToString()) { app.listSpecialCases.execute(characterId, inPlayIds) }
    val data = cases.data ?: return
    if (data.jinxes.isEmpty() && data.interactions.isEmpty()) return
    val characters = app.catalog.map()
    Collapsible("Casos especiales en esta partida (${data.jinxes.size + data.interactions.size})", initiallyOpen = true, icon = Lucide.Puzzle) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            JinxList(data.jinxes, characters, label = { "Incompatibilidad" })
            JinxList(data.interactions, characters, label = { if (it.official == true) "Oficial" else null })
        }
    }
}

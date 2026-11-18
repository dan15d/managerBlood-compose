package es.dan15d.grimorio.presentation.pages.newgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.domain.entities.GameMode
import es.dan15d.grimorio.domain.entities.Script
import es.dan15d.grimorio.presentation.components.EditionBanner
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/** Rejilla de tarjetas: una columna en el móvil y dos (o más) en pantallas anchas (como .grid). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardGrid(wide: Boolean, content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = if (wide) 2 else 1,
        content = content,
    )
}

/** Paso 1: narrar a mano o con la IA. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModeStep(
    mode: GameMode?,
    aiAvailable: Boolean,
    wide: Boolean,
    onMode: (GameMode) -> Unit,
    onSettings: () -> Unit,
) {
    StepTitle("¿Cómo quieres narrar?")
    CardGrid(wide) {
        ModeCard(
            Lucide.BookOpen,
            "Manual",
            "Tú eliges los personajes de la bolsa (o pides un reparto al azar). Control total.",
            selected = mode == GameMode.Manual,
            onClick = { onMode(GameMode.Manual) },
            modifier = Modifier.weight(1f),
        )
        ModeCard(
            Lucide.Sparkles,
            "Con IA",
            "La IA prepara un reparto equilibrado según el estilo que pidas, te explica por qué y te aconseja durante la partida.",
            selected = mode == GameMode.Ai,
            enabled = aiAvailable,
            onClick = { onMode(GameMode.Ai) },
            modifier = Modifier.weight(1f),
        )
    }
    if (!aiAvailable) {
        Column {
            Muted("El modo IA necesita conexión con DeepSeek.")
            TextLink("Configúralo en Ajustes", onSettings, icon = Lucide.Settings)
        }
    }
}

/** Paso 2: el guion (al elegirlo, con la nube, se abre la sala). */
@Composable
internal fun ScriptStep(
    scripts: List<Script>?,
    current: Script?,
    busy: Boolean,
    wide: Boolean,
    onChoose: (Script) -> Unit,
    onCreateScript: () -> Unit,
) {
    StepTitle("Elige el guion")
    if (scripts == null) {
        Muted("Cargando…")
        return
    }
    CardGrid(wide) {
        for (s in scripts) {
            key(s.id) {
                ScriptCard(s, selected = current?.id == s.id, enabled = !busy, onClick = { onChoose(s) }, modifier = Modifier.weight(1f))
            }
        }
    }
    TextLink("Crear un guion personalizado", onCreateScript, icon = Lucide.Plus)
}

@Composable
private fun ScriptCard(s: Script, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val summary = s.description?.let { if (it.length > 120) it.take(120) + "…" else it }
        ?: s.author.ifEmpty { "Guion personalizado" }
    GrimCard(
        modifier.alpha(if (enabled) 1f else 0.6f),
        selected = selected,
        onClick = if (enabled) onClick else null,
        padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        EditionBanner(s.id, s.name, s.image, Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)) {
            Text(summary, style = GrimType.small)
            val min = s.recommendedMinPlayers
            if (min != null && min > 5) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recomendado con $min o más jugadores", style = GrimType.small.copy(color = Grim.fabled, fontSize = 14.sp))
                }
            }
        }
    }
}

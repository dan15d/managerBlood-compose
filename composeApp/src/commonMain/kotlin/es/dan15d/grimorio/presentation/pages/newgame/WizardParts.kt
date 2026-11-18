package es.dan15d.grimorio.presentation.pages.newgame

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.TEAM_ORDER
import es.dan15d.grimorio.presentation.components.Token
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

// Piezas del asistente de nueva partida (las clases .steps, .wizard-footer, .mode-card… de app.css).

/** Título de cada paso (el h1 de la web). */
@Composable
internal fun StepTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = GrimType.title)
}

/** Subtítulo dentro de un paso o una tarjeta (el h2 de la web). */
@Composable
internal fun CardTitle(text: String, modifier: Modifier = Modifier, color: Color = Grim.goldBright) {
    Text(text, modifier, style = GrimType.heading.copy(color = color))
}

/**
 * Cabecera de pasos. En el móvil, solo el paso en el que se está y una barra de progreso (la
 * tira entera no cabe); a partir de 640 de ancho, la tira con los cinco pasos, que deja volver.
 */
@Composable
internal fun StepsHeader(step: Int, wide: Boolean, onStep: (Int) -> Unit) {
    if (!wide) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                buildAnnotatedString {
                    append("PASO ${step + 1} DE ${STEPS.size} · ")
                    withStyle(SpanStyle(color = Grim.textStrong, fontWeight = FontWeight.Bold)) { append(STEPS[step].uppercase()) }
                },
                style = GrimType.caps,
            )
            val progress by animateFloatAsState((step + 1f) / STEPS.size, label = "progreso")
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Grim.surface2)) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Grim.accent, Grim.gold))),
                )
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        STEPS.forEachIndexed { i, label ->
            val current = i == step
            val done = i < step
            Row(
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, if (current) Grim.gold else Grim.line, CircleShape)
                    .clickable(enabled = i <= step, role = Role.Tab) { onStep(i) }
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (current) Grim.accent else if (done) Grim.gold else Grim.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(Lucide.Check, null, tint = Grim.ink, modifier = Modifier.size(15.dp))
                    } else {
                        Text("${i + 1}", style = GrimType.caps.copy(fontWeight = FontWeight.Bold, color = if (current) Color.White else Grim.muted))
                    }
                }
                Text(label.uppercase(), style = GrimType.caps.copy(color = if (current) Grim.textStrong else Grim.muted))
            }
        }
    }
}

/** Barra de abajo con «Atrás» y la acción principal, al alcance del pulgar (como .wizard-footer). */
@Composable
internal fun WizardFooter(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to Grim.bg))
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Tarjeta grande para escoger una opción (modo de narrar, forma de repartir). */
@Composable
internal fun ModeCard(
    icon: ImageVector,
    title: String,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GrimCard(
        modifier.alpha(if (enabled) 1f else 0.45f).heightIn(min = 150.dp),
        selected = selected,
        onClick = if (enabled) onClick else null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = if (selected) Grim.goldBright else Grim.accentBright, modifier = Modifier.size(34.dp))
            CardTitle(title)
            Text(text, style = GrimType.small)
        }
    }
}

/** Tarjeta plegable (como <details class="card travellers-card">). */
@Composable
internal fun Disclosure(
    title: String,
    titleColor: Color,
    hint: String,
    count: Int,
    initiallyOpen: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Guardado aunque la tarjeta salga de la lista perezosa al bajar.
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Grim.surface2, Grim.surface)))
            .border(1.dp, Grim.line, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) "Plegar" else "Desplegar") { open = !open }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Lucide.ChevronRight, null, tint = Grim.muted, modifier = Modifier.size(18.dp).rotate(if (open) 90f else 0f))
            Text(title, style = GrimType.heading.copy(color = titleColor, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold))
            Text(hint, Modifier.weight(1f), style = GrimType.small.copy(fontSize = 14.sp))
            if (count > 0) Text("$count", style = GrimType.label.copy(fontWeight = FontWeight.Bold))
        }
        if (open) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** Enlace en línea (como <Link>), en morado claro. */
@Composable
internal fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = onClick).heightIn(min = 40.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = Grim.accentBright, modifier = Modifier.size(18.dp))
        Text(text, style = GrimType.body.copy(color = Grim.accentBright, fontWeight = FontWeight.SemiBold))
    }
}

/** Las fichas de la propuesta de la IA, ordenadas por tipo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BagPreview(characters: List<Character>) {
    val sorted = remember(characters) { characters.sortedBy { TEAM_ORDER.indexOf(it.team) } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sorted.forEachIndexed { i, c ->
            androidx.compose.runtime.key(c.id, i) { Token(c, size = 64.dp) }
        }
    }
}

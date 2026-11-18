package es.dan15d.grimorio.presentation.pages.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import es.dan15d.grimorio.domain.entities.Alignment as Side
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimIconButton
import es.dan15d.grimorio.presentation.components.SeatName
import es.dan15d.grimorio.presentation.components.Token
import es.dan15d.grimorio.presentation.components.color
import es.dan15d.grimorio.presentation.components.imageUri
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Un recordatorio dibujado junto a la ficha: el de un personaje, uno propio o lo que cree ser el Borracho. */
private data class Bubble(
    val key: String,
    val label: String,
    /** Personaje que pone el recordatorio (su icono va en la burbuja). */
    val source: Character?,
    val reminderId: String? = null,
    val shown: Boolean = false,
)

private fun bubblesOf(p: Player, characters: Map<String, Character>): List<Bubble> {
    val shown = p.shownCharacterId?.let { characters[it] }
    return buildList {
        if (shown != null) add(Bubble("shown", "Cree ser ${shown.name}", shown, shown = true))
        for (r in p.reminders) add(Bubble(r.id, r.label, characters[r.sourceCharacterId], reminderId = r.id))
    }
}

/**
 * Ángulos de las burbujas alrededor de la ficha: empiezan mirando al centro de la mesa (como los
 * recordatorios físicos) y se reparten a los lados. Debajo va el nombre, así que ahí no se ponen.
 */
private fun bubbleAngles(seatAngle: Double, count: Int, step: Double): List<Double> {
    val toCenter = seatAngle + PI
    val angles = mutableListOf<Double>()
    var k = 0
    while (angles.size < count && k < 80) {
        val angle = toCenter + ceil(k / 2.0) * step * (if (k % 2 == 1) 1 else -1)
        k++
        if (sin(angle) > 0.8) continue
        angles.add(angle)
    }
    return angles
}

private fun initials(label: String) = label.trim().take(1).uppercase()

/** Burbuja abierta: su texto se lee en el centro de la mesa, donde siempre cabe. */
private data class OpenBubble(val playerId: String, val key: String)

/**
 * Los jugadores en círculo, como en la mesa, empezando arriba y en el sentido de las agujas del reloj.
 * Cada asiento es su propia pieza con parámetros estables: al cambiar un jugador solo se vuelve a
 * dibujar su ficha.
 */
@Composable
fun Grimoire(
    players: List<Player>,
    phase: Phase,
    alive: Int,
    threshold: Int,
    characters: Map<String, Character>,
    highlighted: Set<String>,
    hidden: Boolean,
    /** Modo «toca una ficha»: se apagan las que no se pueden elegir. */
    picking: Boolean,
    /** Línea bajo la fase (el paso de la noche en el que se va). */
    centerNote: String?,
    onSelect: (String) -> Unit,
    onRemoveReminder: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf<OpenBubble?>(null) }
    val onBubble = remember { { pid: String, key: String -> open = if (open == OpenBubble(pid, key)) null else OpenBubble(pid, key) } }
    val openPlayer = open?.let { o -> players.find { it.id == o.playerId } }
    val openBubble = if (openPlayer != null && !hidden) bubblesOf(openPlayer, characters).find { it.key == open?.key } else null

    BoxWithConstraints(modifier.widthIn(max = 760.dp).fillMaxWidth().aspectRatio(1f)) {
        val width = maxWidth.value
        val n = max(players.size, 1)
        val size = max(38.0, min(96.0, (PI * 2 * 0.4 * width / n) * 0.62)).roundToInt()
        val radius = min(0.4, 0.5 - (size / 2.0 + 6) / width)
        // Mesa: círculo discontinuo con un resplandor morado.
        Box(
            Modifier.fillMaxSize().padding(maxWidth * 0.09f).drawBehind {
                drawCircle(Brush.radialGradient(listOf(Color(0x297C48C4), Color.Transparent)))
                drawCircle(Grim.lineStrong, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            },
        )
        Box(Modifier.fillMaxSize().padding(maxWidth * 0.26f), contentAlignment = Alignment.Center) {
            if (openPlayer != null && openBubble != null) {
                ReminderCard(
                    bubble = openBubble,
                    playerName = openPlayer.name,
                    onRemove = openBubble.reminderId?.let { rid ->
                        {
                            onRemoveReminder(openPlayer.id, rid)
                            open = null
                        }
                    },
                    onClose = { open = null },
                )
            } else {
                CircleCenter(phase, centerNote, alive, threshold)
            }
        }
        val seatWidth = (size + 12).dp
        players.forEachIndexed { i, p ->
            key(p.id) {
                val angle = i.toDouble() / n * PI * 2 - PI / 2
                val x = width * (0.5 + cos(angle) * radius)
                val y = width * (0.5 + sin(angle) * radius)
                Seat(
                    player = p,
                    character = characters[p.characterId],
                    characters = characters,
                    size = size.dp,
                    angle = angle,
                    hidden = hidden,
                    dim = picking && p.id !in highlighted,
                    glow = p.id in highlighted,
                    openKey = open?.takeIf { it.playerId == p.id }?.key,
                    onSelect = onSelect,
                    onBubble = onBubble,
                    modifier = Modifier.width(seatWidth).offset(x = x.dp - seatWidth / 2, y = y.dp - (size / 2).dp),
                )
            }
        }
    }
}

/** La fase, el paso de la noche y «7 vivos · 4 votos», en el centro de la mesa. */
@Composable
private fun CircleCenter(phase: Phase, centerNote: String?, alive: Int, threshold: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PhasePill(phase)
        if (centerNote != null) {
            Text(centerNote, style = GrimType.small.copy(fontSize = 14.sp, color = Grim.goldBright), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val strong = SpanStyle(fontFamily = GrimType.fonts.display, fontSize = 18.sp, color = Grim.textStrong)
        Text(
            buildAnnotatedString {
                withStyle(strong) { append("$alive") }
                append(" vivos · ")
                withStyle(strong) { append("$threshold") }
                append(" votos")
            },
            style = GrimType.small.copy(fontSize = 14.sp),
            maxLines = 1,
        )
    }
}

/** Píldora de la fase: azul de noche, dorada de día (como .phase-pill). */
@Composable
fun PhasePill(phase: Phase) {
    val shape = CircleShape
    val night = phase.isNight
    val brush = if (night) Brush.verticalGradient(listOf(Color(0xFF1B2240), Color(0xFF10142A))) else Brush.verticalGradient(listOf(Color(0xFF4A3618), Color(0xFF2B1F0E)))
    val border = if (night) Color(0xFF3A4A8A) else Grim.gold
    val color = if (night) Color(0xFFC9D4FF) else Grim.goldBright
    Row(
        Modifier.clip(shape).background(brush).border(1.dp, border, shape).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(if (night) Lucide.Moon else Lucide.Sun, null, tint = color, modifier = Modifier.size(18.dp))
        Text(
            es.dan15d.grimorio.domain.services.phaseLabel(phase).uppercase(),
            style = TextStyle(fontFamily = GrimType.fonts.ui, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.1.sp, color = color),
        )
    }
}

/** El recordatorio abierto, leído en grande en el centro de la mesa (como .reminder-card). */
@Composable
private fun ReminderCard(bubble: Bubble, playerName: String, onRemove: (() -> Unit)?, onClose: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.shadow(10.dp, shape).clip(shape).background(Color(0xE00B0809)).border(1.dp, Grim.gold, shape).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (bubble.source != null) {
            Token(bubble.source, size = 52.dp)
        } else {
            BubbleFace(bubble, 44.dp)
        }
        Text(
            bubble.label,
            style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Grim.textStrong, textAlign = TextAlign.Center),
        )
        val from = if (bubble.shown) "" else if (bubble.source != null) "de ${bubble.source.name} · " else ""
        Text("${from}en $playerName", style = GrimType.small.copy(fontSize = 13.sp, lineHeight = 17.sp, textAlign = TextAlign.Center))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onRemove != null) GrimButton("Quitar", onRemove, kind = ButtonKind.Ghost, icon = Lucide.Trash2, small = true)
            GrimIconButton(Lucide.X, "Cerrar", onClose, bordered = true, size = 34.dp)
        }
    }
}

/** Una ficha del círculo con su nombre y sus recordatorios alrededor. */
@Composable
private fun Seat(
    player: Player,
    character: Character?,
    characters: Map<String, Character>,
    size: Dp,
    angle: Double,
    hidden: Boolean,
    dim: Boolean,
    glow: Boolean,
    openKey: String?,
    onSelect: (String) -> Unit,
    onBubble: (String, String) -> Unit,
    modifier: Modifier,
) {
    val bubbles = remember(player, hidden) { if (hidden) emptyList() else bubblesOf(player, characters) }
    val b = min(32f, max(18f, size.value * 0.36f)).roundToInt()
    val radius = size.value / 2 + b * 0.2f
    val angles = remember(bubbles.size, angle, b) { bubbleAngles(angle, bubbles.size, (b * 1.05) / radius) }
    val label = player.name + (if (hidden) "" else ", ${character?.name}") + (if (player.alive) "" else ", muerto")
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Token(
                character,
                alignment = player.alignment,
                size = size,
                dead = !player.alive,
                dim = dim,
                hidden = hidden,
                glow = glow,
                onClick = { onSelect(player.id) },
                label = label,
            )
            // El punto: muerto al que aún le queda el voto.
            SeatName((if (!player.alive && !player.ghostVoteUsed) "● " else "") + player.name, evil = !hidden && player.alignment == Side.Evil)
        }
        bubbles.forEachIndexed { i, bubble ->
            val a = angles.getOrNull(i) ?: return@forEachIndexed
            val half = b / 2f
            // Posición respecto al asiento: el centro de la ficha está en (ancho/2, tamaño/2).
            val left = (size.value + 12) / 2 + cos(a) * radius - half
            val top = size.value / 2 + sin(a) * radius - half
            key(bubble.key) {
                BubbleButton(
                    bubble = bubble,
                    sizeDp = b.dp,
                    open = openKey == bubble.key,
                    onClick = { onBubble(player.id, bubble.key) },
                    modifier = Modifier.align(Alignment.TopStart).offset(left.toFloat().dp, top.toFloat().dp),
                    description = "${bubble.label} en ${player.name}",
                )
            }
        }
    }
}

/** Burbuja de un recordatorio: el icono de quien lo pone (o la inicial), o la máscara de «Cree ser». */
@Composable
private fun BubbleButton(bubble: Bubble, sizeDp: Dp, open: Boolean, onClick: () -> Unit, modifier: Modifier, description: String) {
    Box(
        modifier
            .requiredSize(sizeDp)
            .scale(if (open) 1.25f else 1f)
            .then(if (open) Modifier.border(2.dp, Grim.goldBright, CircleShape).padding(3.dp) else Modifier)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
    ) {
        BubbleFace(bubble, sizeDp)
    }
}

private val bubbleBrush = Brush.radialGradient(0f to Color(0xFFFBF2DC), 0.55f to Color(0xFFECE0C2), 1f to Color(0xFFC9B48A))

@Composable
private fun BubbleFace(bubble: Bubble, sizeDp: Dp) {
    val border = if (bubble.shown) Grim.gold else bubble.source?.team?.color ?: Grim.lineStrong
    Box(
        Modifier
            .size(sizeDp)
            .clip(CircleShape)
            .then(if (bubble.shown) Modifier.background(Grim.surface3) else Modifier.background(bubbleBrush))
            .border(2.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bubble.shown) {
            Icon(Lucide.VenetianMask, null, tint = Grim.goldBright, modifier = Modifier.size(sizeDp * 0.62f))
        } else {
            // La inicial queda debajo: si el icono no carga, se ve ella.
            Text(
                initials(bubble.label),
                style = TextStyle(fontFamily = GrimType.fonts.ui, fontWeight = FontWeight.Bold, fontSize = (sizeDp.value * 0.45f).sp, color = Grim.ink),
            )
            val image = bubble.source?.image
            if (image != null) {
                AsyncImage(
                    model = imageUri(image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().scale(1.18f),
                )
            }
        }
    }
}

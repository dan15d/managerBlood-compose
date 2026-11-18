package es.dan15d.grimorio.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import es.dan15d.grimorio.domain.entities.Alignment as Side
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.entities.defaultAlignment
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/** Color de cada tipo de personaje (las variables --townsfolk, --demon… de la web). */
val Team.color: Color
    get() = when (this) {
        Team.Townsfolk -> Grim.townsfolk
        Team.Outsider -> Grim.outsider
        Team.Minion -> Grim.minion
        Team.Demon -> Grim.demon
        Team.Traveller -> Grim.traveller
        Team.Fabled -> Grim.fabled
        Team.Loric -> Grim.loric
    }

val Side.color: Color get() = if (this == Side.Good) Grim.good else Grim.evil

/** Iniciales para el centro de la ficha si no hay icono. */
private fun monogram(name: String): String {
    val words = name.split(Regex("\\s+")).filter { it.length > 2 || Regex("^[A-ZÁÉÍÓÚÑ]").containsMatchIn(it) }
    val letters = if (words.size > 1) words.take(2).joinToString("") { it.take(1) } else name.take(1)
    return letters.uppercase()
}

/** Icono de la wiki; el del bando contrario si el jugador ha cambiado de bando. */
private fun iconFor(character: Character, alignment: Side?): String? =
    if (alignment != null && alignment != defaultAlignment(character.team) && character.imageAlt != null) character.imageAlt else character.image

private val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private val dimFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.15f) })

/**
 * Ficha de personaje redonda, de pergamino y con el borde del color de su tipo, como las fichas
 * físicas del juego. Sin personaje u oculta enseña «?». Los muertos llevan el sudario.
 */
@Composable
fun Token(
    character: Character?,
    modifier: Modifier = Modifier,
    alignment: Side? = null,
    size: Dp = 92.dp,
    dead: Boolean = false,
    dim: Boolean = false,
    selected: Boolean = false,
    hidden: Boolean = false,
    glow: Boolean = false,
    onClick: (() -> Unit)? = null,
    label: String? = null,
) {
    val isHidden = hidden || character == null
    val team = character?.team
    val borderColor = if (isHidden || team == null) Grim.lineStrong else team.color
    val icon = if (character != null && !hidden) iconFor(character, alignment) else null
    val showName = size >= 56.dp
    val description = label ?: if (isHidden) "Personaje oculto" else character?.name ?: ""
    val background = if (isHidden) {
        Brush.radialGradient(listOf(Color(0xFF3A2C2E), Color(0xFF1E1517)))
    } else {
        Brush.radialGradient(0f to Color(0xFFFBF2DC), 0.45f to Grim.parchment, 1f to Color(0xFFC9B48A))
    }
    val filter = when {
        dead -> grayscale
        dim -> dimFilter
        else -> null
    }

    Box(modifier.requiredSize(size), contentAlignment = Alignment.Center) {
        if (selected) {
            Box(Modifier.requiredSize(size + 12.dp).border(3.dp, Grim.goldBright, CircleShape))
        }
        if (glow) Glow(size)
        Box(
            Modifier
                .size(size)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(background)
                .border(max(1.dp, size * 0.045f), borderColor, CircleShape)
                .then(
                    if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
                    else Modifier.semantics { contentDescription = description },
                )
                .padding(size * 0.1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isHidden -> Text("?", style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.3f).sp, color = Grim.muted))
                icon != null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    AsyncImage(
                        model = imageUri(icon),
                        contentDescription = null,
                        colorFilter = filter,
                        modifier = if (showName) Modifier.fillMaxWidth(0.9f).weight(1f) else Modifier.fillMaxSize(),
                    )
                    if (showName) TokenName(character!!.name, size)
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        monogram(character!!.name),
                        style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.3f).sp, color = borderColor.copy(red = borderColor.red * 0.62f, green = borderColor.green * 0.62f, blue = borderColor.blue * 0.62f)),
                    )
                    if (showName) TokenName(character.name, size)
                }
            }
        }
        // Velo oscuro para muertos y apagados (el filtro de la web oscurecía toda la ficha).
        if (dead || dim) Box(Modifier.size(size).clip(CircleShape).background(Color.Black.copy(alpha = if (dead) 0.5f else 0.4f)))
        if (dead) Shroud(size)
    }
}

@Composable
private fun TokenName(name: String, size: Dp) {
    Text(
        name,
        style = TextStyle(
            fontFamily = GrimType.fonts.display,
            fontWeight = FontWeight.Bold,
            fontSize = maxOf(8f, size.value * 0.105f).sp,
            lineHeight = maxOf(8.5f, size.value * 0.11f).sp,
            color = Grim.ink,
            textAlign = TextAlign.Center,
        ),
        maxLines = 2,
    )
}

/** El sudario de los muertos: una banda oscura que cae desde arriba con el borde dorado. */
@Composable
private fun Shroud(size: Dp) {
    Canvas(Modifier.size(size * 0.44f, size * 0.52f).offset(y = -(size * 0.24f + size * 0.08f))) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h * 0.78f); lineTo(w / 2, h); lineTo(0f, h * 0.78f); close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color(0xFF0D0A0B), Color(0xFF2A2124))))
        drawLine(Grim.gold, Offset(0f, 1f), Offset(w, 1f), strokeWidth = 2.dp.toPx())
    }
}

/** Halo dorado que late (el personaje al que le toca despertarse). */
@Composable
private fun Glow(size: Dp) {
    val transition = rememberInfiniteTransition(label = "halo")
    val alpha by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alfa")
    Canvas(Modifier.requiredSize(size + 20.dp)) {
        drawCircle(Grim.goldBright.copy(alpha = alpha), style = Stroke(3.dp.toPx()))
    }
}

/** Etiqueta redonda con borde del color del texto (como .badge). */
@Composable
fun Badge(text: String, color: Color = Grim.muted, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier
            .border(1.dp, color, CircleShape)
            .padding(horizontal = 10.dp, vertical = 1.dp),
        style = TextStyle(fontFamily = GrimType.fonts.ui, fontSize = 11.sp, letterSpacing = 1.sp, color = color),
    )
}

package es.dan15d.grimorio.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import es.dan15d.grimorio.domain.entities.Alignment as Side
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.CharacterPair
import es.dan15d.grimorio.domain.entities.GameLogEntry
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.entities.label
import es.dan15d.grimorio.domain.services.DECOYS
import es.dan15d.grimorio.domain.services.Distribution
import es.dan15d.grimorio.domain.services.SETUP_MODIFIERS
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.navigation.BackHandlerLayer
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------- Carga asíncrona (useAsync) ----------

@Stable
class AsyncState<T> {
    var data by mutableStateOf<T?>(null)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var loading by mutableStateOf(true)
        internal set
    internal var version by mutableIntStateOf(0)

    /** Vuelve a cargar (tras guardar o borrar algo). */
    fun reload() {
        version++
    }
}

/** Carga algo asíncrono y deja recargarlo (listas de partidas, guiones…). Se repite si cambian [keys]. */
@Composable
fun <T> rememberAsync(vararg keys: Any?, load: suspend () -> T): AsyncState<T> {
    val state = remember(*keys) { AsyncState<T>() }
    LaunchedEffect(state, state.version) {
        state.loading = true
        try {
            state.data = load()
            state.error = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            state.error = e.message ?: e.toString()
        } finally {
            state.loading = false
        }
    }
    return state
}

// ---------- Asientos en círculo ----------

/**
 * Asientos en círculo, como en la mesa, empezando arriba y en el sentido de las agujas del reloj.
 * Las fichas crecen hasta donde caben sin pisarse; [seat] recibe el tamaño de ficha y el ángulo.
 */
@Composable
fun <T> SeatCircle(
    items: List<T>,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit = {},
    seat: @Composable (item: T, size: Dp, angle: Double) -> Unit,
) {
    BoxWithConstraints(modifier.widthIn(max = 760.dp).fillMaxWidth().aspectRatio(1f)) {
        val width = maxWidth.value
        val n = max(items.size, 1)
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { center() }
        }
        val seatWidth = (size + 12).dp
        items.forEachIndexed { i, item ->
            val angle = i.toDouble() / n * PI * 2 - PI / 2
            val x = width * (0.5 + cos(angle) * radius)
            val y = width * (0.5 + sin(angle) * radius)
            Box(
                Modifier.width(seatWidth).offset(x = x.dp - seatWidth / 2, y = y.dp - (size / 2).dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    seat(item, size.dp, angle)
                }
            }
        }
    }
}

/** Nombre bajo una ficha del círculo (como .seat__name); con borde rojo si es malvado. */
@Composable
fun SeatName(name: String, evil: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        name,
        modifier
            .clip(CircleShape)
            .background(Color(0xD90B0809))
            .border(1.dp, if (evil) Grim.evil.copy(alpha = 0.6f) else Grim.line, CircleShape)
            .padding(horizontal = 6.dp),
        style = TextStyle(fontFamily = GrimType.fonts.body, fontSize = 11.sp, color = Grim.textStrong),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------- Tarjeta a pantalla completa ----------

/**
 * Tarjeta a pantalla completa para enseñar el móvil a un jugador por la noche: los faroles al
 * Demonio, el personaje que ve la Lavandera… Se cierra tocando en cualquier sitio.
 */
@Composable
fun ShowCard(
    characters: List<Character>,
    onClose: () -> Unit,
    heading: String? = null,
    showAbility: Boolean = false,
    alignment: Side? = null,
) {
    BackHandlerLayer(onClose)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val size = if (characters.size > 1) 120.dp else if (heading != null) 200.dp else 220.dp
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF1D1230), Grim.bg, Color(0xFF0F0A15))))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            if (heading != null) {
                Text(heading, style = GrimType.title.copy(fontSize = 34.sp, lineHeight = 40.sp, textAlign = TextAlign.Center))
            }
            if (characters.isNotEmpty()) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (c in characters) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 320.dp)) {
                            Token(c, size = size)
                            Text(c.name, style = GrimType.heading.copy(color = c.team.color, textAlign = TextAlign.Center))
                            if (characters.size == 1) Badge(c.team.label.singular, c.team.color)
                            if (showAbility) Text(c.ability, style = GrimType.body.copy(textAlign = TextAlign.Center))
                        }
                    }
                }
            }
            if (alignment != null) {
                Box(
                    Modifier.size(140.dp).clip(CircleShape).background(alignment.color.copy(alpha = 0.18f)).border(3.dp, alignment.color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (alignment == Side.Good) Lucide.ThumbsUp else Lucide.ThumbsDown, if (alignment == Side.Good) "Bueno" else "Malo", tint = alignment.color, modifier = Modifier.size(72.dp))
                }
            }
            Text("Toca para cerrar", style = GrimType.small)
        }
    }
}

// ---------- Selector de personajes ----------

val TEAM_ORDER: List<Team> = listOf(Team.Townsfolk, Team.Outsider, Team.Minion, Team.Demon, Team.Traveller, Team.Fabled, Team.Loric)

/** Cuántos de un tipo pide un reparto (los tipos fuera de la bolsa no tienen objetivo). */
fun Distribution.countFor(team: Team): Int? = when (team) {
    Team.Townsfolk -> townsfolk
    Team.Outsider -> outsider
    Team.Minion -> minion
    Team.Demon -> demon
    else -> null
}

/**
 * Rejilla de personajes agrupados por tipo, con la habilidad visible para elegir sin dudas.
 * [repeatable] + [onAdd]: personajes que pueden ir repetidos (botón «+1»; tocar la ficha quita una copia).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterPicker(
    characters: List<Character>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    target: Distribution? = null,
    teams: List<Team> = TEAM_ORDER,
    searchable: Boolean = false,
    repeatable: ((Character) -> Boolean)? = null,
    onAdd: ((String) -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val visible = if (q.isEmpty()) characters else characters.filter {
        it.name.lowercase().contains(q) || it.nameEn?.lowercase()?.contains(q) == true || it.ability.lowercase().contains(q)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        if (searchable) GrimTextField(query, { query = it }, placeholder = "Buscar personaje o habilidad…")
        for (team in teams) {
            val list = visible.filter { it.team == team }
            if (list.isEmpty()) continue
            // Lo que no va a la bolsa (Lil' Monsta) no cuenta para el reparto.
            val count = selected.count { id -> SETUP_MODIFIERS[id]?.outOfBag != true && characters.any { it.id == id && it.team == team } }
            val want = target?.countFor(team)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(team.label.plural, style = GrimType.heading.copy(color = team.color, fontSize = 19.sp))
                    if (want != null) {
                        val color = if (count == want) Grim.loric else if (count > want) Grim.bloodBright else Grim.text
                        Text("$count/$want", style = GrimType.label.copy(fontWeight = FontWeight.Bold, color = color))
                    } else if (count > 0) {
                        Text("$count", style = GrimType.label.copy(fontWeight = FontWeight.Bold))
                    }
                }
                Divider()
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2,
                ) {
                    for (c in list) {
                        val copies = selected.count { it == c.id }
                        PickCard(
                            c,
                            copies,
                            onToggle = { onToggle(c.id) },
                            onAdd = if (copies > 0 && onAdd != null && repeatable?.invoke(c) == true) ({ onAdd(c.id) }) else null,
                            modifier = Modifier.weight(1f).widthIn(min = 150.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickCard(c: Character, copies: Int, onToggle: () -> Unit, onAdd: (() -> Unit)?, modifier: Modifier) {
    val on = copies > 0
    val shape = RoundedCornerShape(8.dp)
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (on) Grim.surface2 else Grim.surface)
                .border(1.dp, if (on) Grim.gold else Grim.line, shape)
                .clickable(onClick = onToggle)
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = if (onAdd != null) 40.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Token(c, size = 44.dp, dim = !on)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    c.name + if (copies > 1) " ×$copies" else "",
                    style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Grim.textStrong),
                )
                Text(c.ability, style = GrimType.small.copy(fontSize = 13.sp, lineHeight = 17.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                SETUP_MODIFIERS[c.id]?.let { Flag(Lucide.Settings2, it.note) }
                if (DECOYS.containsKey(c.id)) Flag(Lucide.VenetianMask, "No sabe quién es")
            }
        }
        if (onAdd != null) {
            GrimButton("+1", onAdd, Modifier.align(Alignment.BottomEnd).padding(6.dp), small = true)
        }
    }
}

@Composable
private fun Flag(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = Grim.fabled, modifier = Modifier.size(13.dp))
        Text(text, style = GrimType.small.copy(fontSize = 12.sp, lineHeight = 15.sp, color = Grim.fabled))
    }
}

/** Reparto de la bolsa: «5 Aldeanos · 1 Forastero…» en píldoras. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DistributionBar(distribution: Distribution, travellers: Int = 0, modifier: Modifier = Modifier) {
    val items = buildList {
        add(Team.Townsfolk to distribution.townsfolk)
        add(Team.Outsider to distribution.outsider)
        add(Team.Minion to distribution.minion)
        add(Team.Demon to distribution.demon)
        if (travellers > 0) add(Team.Traveller to travellers)
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((team, n) in items) {
            Row(
                Modifier.clip(CircleShape).background(Grim.bg2).border(1.dp, Grim.line, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("$n", style = GrimType.label.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = team.color))
                Text(if (n == 1) team.label.singular else team.label.plural, style = GrimType.label.copy(fontSize = 13.sp, color = team.color))
            }
        }
    }
}

// ---------- Guiones, incompatibilidades y registro ----------

private val EDITION_LOGOS = mapOf(
    "trouble-brewing" to "img/logo-trouble-brewing.webp",
    "bad-moon-rising" to "img/logo-bad-moon-rising.webp",
    "sects-and-violets" to "img/logo-sects-and-violets.webp",
)

/** Logo oficial de la edición de un guion (null si es un guion personalizado). */
fun editionLogo(scriptId: String): String? = EDITION_LOGOS[scriptId]

/** Iconos de la hoja de noche para los pasos que no son personajes. */
object NightIcons {
    const val dusk = "img/dusk.webp"
    const val dawn = "img/dawn.webp"
    const val minionInfo = "img/minion.webp"
    const val demonInfo = "img/demon.webp"
}

/** Cabecera de un guion: el logo oficial de su edición, la imagen que le hayan puesto o, si no, su nombre. */
@Composable
fun EditionBanner(scriptId: String, name: String, image: String? = null, modifier: Modifier = Modifier) {
    val logo = image ?: editionLogo(scriptId)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (logo != null) {
            GrimImage(logo, Modifier.fillMaxWidth().height(if (image != null) 120.dp else 96.dp), contentDescription = name, contentScale = ContentScale.Fit)
            if (image != null) Text(name, style = GrimType.heading, textAlign = TextAlign.Center)
        } else {
            Text(name, style = GrimType.heading, textAlign = TextAlign.Center)
        }
    }
}

/** Incompatibilidades (jinxes) o interacciones, con los dos personajes que las provocan. */
@Composable
fun <T : CharacterPair> JinxList(jinxes: List<T>, characters: Map<String, Character>, label: ((T) -> String?)? = null) {
    if (jinxes.isEmpty()) return
    val name = { id: String -> characters[id]?.name ?: id }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (j in jinxes) {
            val shape = RoundedCornerShape(8.dp)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(shape).background(Grim.surface2)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(Grim.fabled))
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${name(j.characterIds[0])} + ${name(j.characterIds[1])}",
                            style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Grim.fabled),
                        )
                        label?.invoke(j)?.let { Badge(it) }
                    }
                    Text(j.text, style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp))
                }
            }
        }
    }
}

/** Registro de la partida, lo último arriba, agrupado por fase para no repetir «Noche 2 ·». */
@Composable
fun LogList(log: List<GameLogEntry>) {
    data class Group(val phase: String, val entries: MutableList<GameLogEntry>)
    val groups = mutableListOf<Group>()
    for (entry in log.asReversed()) {
        val last = groups.lastOrNull()
        if (last != null && last.phase == entry.phase) last.entries.add(entry) else groups.add(Group(entry.phase, mutableListOf(entry)))
    }
    if (groups.isEmpty()) {
        Muted("Todavía no ha pasado nada.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (group in groups) {
            Column {
                Text(group.phase.uppercase(), style = GrimType.caps.copy(color = Grim.goldBright))
                for (entry in group.entries) {
                    Text(entry.text, Modifier.padding(vertical = 6.dp), style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp))
                    Divider()
                }
            }
        }
    }
}

package es.dan15d.grimorio.presentation.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.domain.entities.Alignment as Side
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.entities.label
import es.dan15d.grimorio.domain.services.TOR
import es.dan15d.grimorio.domain.services.hasFabled
import es.dan15d.grimorio.domain.services.publicAlive
import es.dan15d.grimorio.domain.services.seatSecret
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.app.ToastKind
import es.dan15d.grimorio.presentation.components.Badge
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.Page
import es.dan15d.grimorio.presentation.components.PageHeader
import es.dan15d.grimorio.presentation.components.Token
import es.dan15d.grimorio.presentation.components.color
import es.dan15d.grimorio.presentation.components.rememberAsync
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.navigation.BackHandlerLayer
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.pages.newgame.WizardFooter
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/**
 * Pasar el móvil: cada jugador ve en secreto el personaje que cree ser (el Borracho ve su
 * Aldeano falso). Cuando haya app de jugadores, esto lo verá cada uno en su teléfono.
 */
@Composable
fun RevealPage(id: String) {
    val ui = LocalAppState.current
    val app = ui.app ?: return
    val nav = LocalNavigator.current
    val loaded = rememberAsync(app, id) { app.loadGame.execute(id) }
    var current by remember { mutableStateOf<Player?>(null) }
    var revealed by remember { mutableStateOf(false) }
    var seen by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(loaded.error) { loaded.error?.let { ui.toast(it, ToastKind.Error) } }

    val game = loaded.data
    if (game == null) {
        Page(flow = true) { Muted("Cargando…") }
        return
    }

    val player = current
    if (player != null) {
        // «Atrás» oculta el personaje y vuelve a la lista: que nadie lo vea por descuido.
        BackHandlerLayer {
            current = null
            revealed = false
        }
        // Al devolver el móvil se pasa directo al siguiente que falte, en orden de asiento; sin volver a la lista.
        val seenNow = (seen + player.id).distinct()
        val pending = game.players.filter { it.id !in seenNow }
        val following = pending.firstOrNull { it.seat > player.seat } ?: pending.firstOrNull()
        Reveal(
            game = game,
            player = player,
            revealed = revealed,
            following = following,
            seenCount = seenNow.size,
            onReveal = { revealed = true },
            onNotMe = { current = null },
            onDone = {
                seen = seenNow
                current = following
                revealed = false
            },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Page(flow = true, maxWidth = 640.dp) {
                PageHeader("Repartir personajes")
                Muted("Toca el nombre de cada jugador y pásale el móvil. Al terminar, vuelve al Grimorio.")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in game.players) {
                        key(p.id) {
                            GrimCard(onClick = { current = p }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("${p.seat + 1}", Modifier.width(22.dp), style = GrimType.label.copy(color = Grim.muted, textAlign = TextAlign.End))
                                    Text(p.name, Modifier.weight(1f), style = GrimType.body.copy(color = Grim.textStrong, fontSize = 18.sp))
                                    if (p.id in seen) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Lucide.Check, null, tint = Grim.loric, modifier = Modifier.size(18.dp))
                                            Text("Visto", style = GrimType.label.copy(color = Grim.loric))
                                        }
                                    } else {
                                        Text("Pendiente", style = GrimType.small.copy(fontSize = 14.sp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        WizardFooter {
            val all = seen.size == game.players.size
            GrimButton(
                if (all) "Ir al Grimorio" else "Volver al Grimorio (${seen.size}/${game.players.size})",
                { nav.navigate(Route.GameRoute(game.id)) },
                Modifier.weight(1f),
                kind = ButtonKind.Primary,
                big = true,
            )
        }
    }
}

@Composable
private fun Reveal(
    game: Game,
    player: Player,
    revealed: Boolean,
    following: Player?,
    seenCount: Int,
    onReveal: () -> Unit,
    onNotMe: () -> Unit,
    onDone: () -> Unit,
) {
    val app = LocalAppState.current.app ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tokenSize = min(200.dp, maxWidth * 0.56f)
        val minHeight = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        ) {
            if (!revealed) {
                Muted("Pasa el móvil a", center = true)
                Text(player.name, style = GrimType.title.copy(fontSize = 38.sp, lineHeight = 44.sp, textAlign = TextAlign.Center))
                Muted("Que nadie más mire la pantalla.", center = true)
                GrimButton(
                    "Soy ${player.name}: ver mi personaje",
                    onReveal,
                    Modifier.heightIn(min = 64.dp).widthIn(max = 420.dp).fillMaxWidth(),
                    kind = ButtonKind.Primary,
                    icon = Lucide.Eye,
                    big = true,
                )
                GrimButton("No soy yo", onNotMe, kind = ButtonKind.Ghost)
            } else {
                val secret = remember(game, player) { seatSecret(player, app.catalog.map(), game.fabledIds, publicAlive(game, player)) }
                val tor = hasFabled(game, TOR) && publicAlive(game, player)
                val character = secret.characterId?.let { app.catalog.get(it) }
                Muted("${player.name}, eres…", center = true)
                Token(character, alignment = secret.alignment, size = tokenSize)
                if (character == null) {
                    Muted(
                        if (tor) "Con Tor nadie conoce su personaje ni su bando: lo descubrirás al morir." else "El Narrador todavía no te ha asignado personaje.",
                        center = true,
                    )
                } else {
                    Text(character.name, style = GrimType.title.copy(color = character.team.color, textAlign = TextAlign.Center))
                    Badge(
                        if (character.team == Team.Traveller) "Viajero · ${if (secret.alignment == Side.Good) "Bueno" else "Malvado"}" else character.team.label.singular,
                        character.team.color,
                    )
                    Text(character.ability, Modifier.widthIn(max = 480.dp), style = GrimType.body)
                }
                GrimButton(
                    if (following != null) "Ocultar y pasar a ${following.name}" else "Ocultar y devolver el móvil",
                    onDone,
                    Modifier.heightIn(min = 56.dp).widthIn(max = 420.dp).fillMaxWidth(),
                    kind = ButtonKind.Gold,
                    icon = Lucide.EyeOff,
                    big = true,
                )
                if (following != null) {
                    Muted("$seenCount de ${game.players.size} ya han visto el suyo.", center = true)
                }
            }
        }
    }
}

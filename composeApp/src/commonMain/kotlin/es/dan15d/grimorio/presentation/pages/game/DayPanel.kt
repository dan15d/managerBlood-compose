package es.dan15d.grimorio.presentation.pages.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.domain.entities.Alignment as Side
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Nomination
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.services.butcherCanNominate
import es.dan15d.grimorio.domain.services.canVote
import es.dan15d.grimorio.domain.services.closeNomination
import es.dan15d.grimorio.domain.services.declareWinner
import es.dan15d.grimorio.domain.services.execute
import es.dan15d.grimorio.domain.services.exileThreshold
import es.dan15d.grimorio.domain.services.expiringReminders
import es.dan15d.grimorio.domain.services.mustVote
import es.dan15d.grimorio.domain.services.onTheBlock
import es.dan15d.grimorio.domain.services.removeReminders
import es.dan15d.grimorio.domain.services.startNight
import es.dan15d.grimorio.domain.services.toggleVote
import es.dan15d.grimorio.domain.services.voteCount
import es.dan15d.grimorio.domain.services.voteIgnored
import es.dan15d.grimorio.domain.services.voteThreshold
import es.dan15d.grimorio.domain.services.voteWeight
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.ConfirmDialog
import es.dan15d.grimorio.presentation.components.Divider
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimSheet
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.SectionLabel
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/** Nominaciones, votos y ejecución. El orden de voto empieza a la izquierda del nominado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayPanel(c: GameController, game: Game) {
    val characters = c.characters
    fun name(id: String) = game.players.find { it.id == id }?.name ?: "?"
    val threshold = voteThreshold(game)
    val open = game.nominations.find { !it.closed }
    val block = onTheBlock(game)
    // Tras la ejecución, el Carnicero aún puede nominar; los Viajeros vivos se pueden exiliar.
    val butcherTurn = butcherCanNominate(game)
    val travellers = game.players.any { it.alive && characters[it.characterId]?.team == Team.Traveller }
    fun needed(n: Nomination) = if (n.exile == true) exileThreshold(game) else threshold

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (open != null) {
            OpenNomination(c, game, open, ::name, needed(open))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val executed = game.executedToday
                if (executed != null) {
                    GameAlert(AlertKind.Info) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Grim.textStrong)) { append(name(executed)) }
                                append(" ha sido ejecutado hoy. ")
                                append(if (butcherTurn) "El Carnicero puede nominar a otro jugador." else "Ya no hay más nominaciones.")
                            },
                            style = GrimType.body.copy(fontSize = 16.sp),
                        )
                    }
                }
                if (executed == null || butcherTurn) {
                    GrimButton(
                        if (butcherTurn) "Nominación del Carnicero" else "Nueva nominación",
                        { c.startPick(Pick.Nominator) },
                        Modifier.fillMaxWidth(),
                        kind = ButtonKind.Primary,
                        icon = Lucide.Gavel,
                        big = true,
                    )
                    Muted("Después toca en el Grimorio a quien nomina y a quien es nominado.", Modifier.fillMaxWidth(), center = true)
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (executed == null) GrimButton("Ejecutar sin votación…", { c.startPick(Pick.Execute) }, small = true)
                    if (travellers) GrimButton("Exiliar a un Viajero…", { c.startPick(Pick.Exile) }, small = true)
                    if (game.players.any { it.characterId == "slayer" || it.shownCharacterId == "slayer" }) {
                        GrimButton("Disparo del Exterminador…", { c.startPick(Pick.Shooter) }, small = true)
                    }
                }
            }
        }

        val closed = game.nominations.filter { it.closed }
        if (closed.isNotEmpty()) {
            Column {
                SectionLabel("Votaciones de hoy", Modifier.padding(bottom = 4.dp))
                for (n in closed) {
                    key(n.id) {
                        val votes = voteCount(game, n)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                buildAnnotatedString {
                                    append(name(n.nomineeId))
                                    withStyle(SpanStyle(color = Grim.muted, fontSize = 13.sp)) {
                                        append(if (n.exile == true) " (exilio)" else " (por ${name(n.nominatorId)})")
                                    }
                                },
                                Modifier.weight(1f),
                                style = GrimType.body.copy(fontSize = 16.sp),
                            )
                            Text(
                                "$votes votos",
                                style = GrimType.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (votes >= needed(n)) Grim.fabled else Grim.muted),
                            )
                        }
                        Divider()
                    }
                }
            }
        }

        if (game.executedToday == null || game.butcherNominationId != null) {
            if (block != null) {
                GameNote("En la picota: ${name(block.playerId)} con ${block.votes} votos.", NoteKind.Info)
            } else {
                Muted(
                    if (game.nominations.any { it.closed && it.exile != true }) "Nadie está en la picota: sin votos suficientes o empate."
                    else "$threshold votos para ejecutar. Pide nominaciones cuando hayan hablado unos minutos.",
                    Modifier.fillMaxWidth(),
                    center = true,
                )
            }
        }
    }
}

@Composable
private fun OpenNomination(c: GameController, game: Game, open: Nomination, name: (String) -> String, needed: Int) {
    val votes = voteCount(game, open)
    val shape = RoundedCornerShape(12.dp)
    // El orden de voto empieza a la izquierda del nominado.
    val order = run {
        val seat = game.players.find { it.id == open.nomineeId }?.seat ?: 0
        val n = game.players.size
        game.players.sortedBy { ((it.seat - seat - 1 + n) % n) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Grim.surface)
            .background(Brush.verticalGradient(listOf(Color(0x387C48C4), Color.Transparent)))
            .border(1.dp, Grim.gold, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Muted(if (open.exile == true) "Exilio del Viajero" else "${name(open.nominatorId)} nomina a")
        CardTitle(name(open.nomineeId))
        Muted(
            if (open.exile == true) "Votan todos, vivos y muertos, sin gastar su voto. Hace falta la mitad de todos los jugadores."
            else "Deja que se defienda. Después cuenta en el sentido del reloj, empezando por quien está a su izquierda.",
        )
        // Rejilla de botones de voto: tantas columnas de 120 como quepan.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val columns = maxOf(1, ((maxWidth.value + 6) / 126).toInt())
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in order.chunked(columns)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (p in row) key(p.id) { VoteButton(c, game, open, p, Modifier.weight(1f)) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$votes", style = TextStyle(fontFamily = GrimType.fonts.display, fontSize = 24.sp, color = Grim.textStrong))
            Text("/ $needed necesarios", style = GrimType.small)
        }
        // Barra de votos: se llena hasta los necesarios.
        val fraction = (votes.toFloat() / maxOf(needed, 1)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Grim.bg2).border(1.dp, Grim.line, CircleShape)) {
            if (fraction > 0f) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Brush.horizontalGradient(listOf(Grim.accentBright, Grim.goldBright))))
            }
        }
        GrimButton(
            "Cerrar votación",
            { c.change { g -> closeNomination(g, open.id) } },
            Modifier.fillMaxWidth().padding(top = 12.dp),
            kind = ButtonKind.Primary,
        )
    }
}

@Composable
private fun VoteButton(c: GameController, game: Game, open: Nomination, p: Player, modifier: Modifier) {
    val on = p.id in open.voterIds
    // Burócrata (×3), Ladrón (−1) y Banshee (×2): su voto no vale 1.
    val weight = voteWeight(p)
    // Mayordomo sin su señor (no cuenta) y Fanático que no vota (debe hacerlo).
    val ignored = if (on) voteIgnored(game, open, p) else null
    val missing = !on && open.exile != true && mustVote(game, p)
    val enabled = on || canVote(p, game, open)
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .then(
                if (on) Modifier.background(Brush.verticalGradient(listOf(Color(0xFF3A2A10), Color(0xFF24190A)))).border(1.dp, Grim.goldBright, shape)
                else Modifier.background(Grim.bg2).border(1.dp, Grim.lineStrong, shape),
            )
            .clickable(enabled = enabled, role = Role.Checkbox, onClickLabel = if (on) "Quitar el voto de ${p.name}" else "Vota ${p.name}") {
                c.change { g -> toggleVote(g, open.id, p.id) }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (on) Icon(Lucide.Hand, "Vota", tint = Grim.goldBright, modifier = Modifier.size(18.dp))
            else if (!p.alive) Icon(Lucide.Ghost, "Muerto", tint = Grim.muted, modifier = Modifier.size(18.dp))
        }
        Text(
            p.name,
            Modifier.weight(1f),
            style = GrimType.body.copy(fontSize = 15.sp, color = if (on) Grim.textStrong else Grim.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val tag = when {
            ignored != null -> "no cuenta"
            missing -> "debe votar"
            weight != 1 -> if (weight > 0) "×$weight" else "−1"
            else -> null
        }
        if (tag != null) {
            Text(tag, style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Grim.fabled), maxLines = 1)
        }
    }
}

/** Botones del día al pie: ejecutar a quien está en la picota y anochecer. */
@Composable
fun DayActionBar(c: GameController, game: Game, modifier: Modifier = Modifier) {
    val open = game.nominations.any { !it.closed }
    val block = onTheBlock(game)
    ActionBar(modifier) {
        if (block != null && !open) {
            val name = game.players.find { it.id == block.playerId }?.name ?: "?"
            GrimButton("Ejecutar a $name", { c.change { g -> execute(g, block.playerId, c.characters) } }, actionButton(), kind = ButtonKind.Primary)
        }
        GrimButton("Anochecer", { c.endDay() }, actionButton(), icon = Lucide.Moon, enabled = !open)
    }
}

/** Confirmaciones y hojas del anochecer. */
@Composable
fun DaySheets(c: GameController, game: Game) {
    fun name(id: String) = game.players.find { it.id == id }?.name ?: "?"
    val block = onTheBlock(game)
    if (c.confirmDusk && block != null) {
        ConfirmDialog(
            title = "¿Terminar el día sin ejecutar?",
            message = "${name(block.playerId)} está en la picota con ${block.votes} votos. Si anochece ahora, no se le ejecuta.",
            confirmLabel = "Anochecer sin ejecutar",
            onConfirm = {
                c.confirmDusk = false
                c.checkDuskVictory()
            },
            onCancel = { c.confirmDusk = false },
        )
    }
    c.duskWin?.let { win ->
        GrimSheet(if (win.winner == Side.Good) "¿Gana el Bien?" else "¿Gana el Mal?", { c.duskWin = null }) {
            Text(win.reason, style = GrimType.body)
            Muted("Si su habilidad no funciona hoy (está borracho o envenenado), la partida sigue.")
            ActionBar(sheet = true) {
                GrimButton("La partida sigue", {
                    c.duskWin = null
                    c.goToNight()
                }, actionButton(), icon = Lucide.Moon)
                GrimButton(if (win.winner == Side.Good) "Gana el Bien" else "Gana el Mal", {
                    c.duskWin = null
                    c.change { g -> declareWinner(g, win.winner) }
                }, actionButton(), kind = ButtonKind.Gold)
            }
        }
    }
    c.cleaning?.let { marked ->
        val expiring = expiringReminders(game)
        GrimSheet("Antes de anochecer", { c.cleaning = null }) {
            Muted("Estos recordatorios solo valían anoche y hoy. Marcados, se quitan; toca los que quieras dejar.")
            Chips {
                for ((playerId, reminder) in expiring) {
                    key(reminder.id) {
                        val on = reminder.id in marked
                        val source = c.characters[reminder.sourceCharacterId]
                        ChipButton(
                            reminder.label + (if (source != null) " (${source.name})" else "") + " · ${name(playerId)}",
                            { c.cleaning = if (on) marked - reminder.id else marked + reminder.id },
                            pressed = on,
                        )
                    }
                }
            }
            ActionBar(sheet = true) {
                GrimButton("No quitar nada", {
                    c.cleaning = null
                    c.change(::startNight)
                }, actionButton())
                GrimButton("Quitar ${marked.size} y anochecer", {
                    c.cleaning = null
                    c.change { g -> startNight(removeReminders(g, marked)) }
                }, actionButton(), kind = ButtonKind.Primary, icon = Lucide.Moon)
            }
        }
    }
}

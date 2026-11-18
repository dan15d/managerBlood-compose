package es.dan15d.grimorio.domain.services

import es.dan15d.grimorio.domain.RuleViolation
import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameLogEntry
import es.dan15d.grimorio.domain.entities.GameStatus
import es.dan15d.grimorio.domain.entities.GameTimer
import es.dan15d.grimorio.domain.entities.Nomination
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.ReminderToken
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.isoString
import es.dan15d.grimorio.domain.nowIso
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// Reglas del Grimorio: funciones puras que devuelven una partida nueva. No guardan nada
// ni conocen la interfaz; los casos de uso se encargan de persistir el resultado.

/** Partida nueva y el aviso para el Narrador. */
data class GameMessage(val game: Game, val message: String)

fun phaseLabel(phase: Phase): String = "${if (phase.isNight) "Noche" else "Día"} ${phase.number}"

private fun touch(game: Game, text: String? = null): Game {
    val now = nowIso()
    return game.copy(
        updatedAt = now,
        log = if (!text.isNullOrEmpty()) game.log + GameLogEntry(at = now, phase = phaseLabel(game.phase), text = text) else game.log,
    )
}

private fun updatePlayer(game: Game, playerId: String, change: (Player) -> Player): Game {
    if (game.players.none { it.id == playerId }) throw RuleViolation("Ese jugador no está en la partida.")
    return game.copy(players = game.players.map { if (it.id == playerId) change(it) else it })
}

fun findPlayer(game: Game, playerId: String?): Player? = game.players.find { it.id == playerId }

fun alivePlayers(game: Game): List<Player> = game.players.filter { it.alive }

/** Mitad redondeando hacia arriba (Math.ceil(n / 2)). */
private fun half(n: Int) = (n + 1) / 2

// ---------- Vida y muerte ----------

/**
 * Apunta la muerte: de noche no se anuncia hasta el amanecer; de día cuenta para el Zombuul. Si
 * había resucitado esta misma noche, para la mesa nunca dejó de estar muerto.
 */
private fun recordDeath(game: Game, playerId: String): Game {
    if (!game.phase.isNight) return game.copy(dayDeaths = game.dayDeaths + playerId)
    if (playerId in game.nightRevivals) return game.copy(nightRevivals = game.nightRevivals.filter { it != playerId })
    return game.copy(nightDeaths = game.nightDeaths + playerId)
}

private fun withReminder(game: Game, playerId: String, source: String, label: String): Game =
    updatePlayer(game, playerId) { p -> p.copy(reminders = p.reminders + reminderFrom(source, label, "$playerId:$source:$label")) }

/**
 * Un jugador muere. Casos de Demonios:
 * - Zombuul: la primera vez parece muerto (para la mesa lo está) pero sigue vivo; la segunda, muere.
 * - Lleech: no muere mientras su huésped siga vivo; si muere el huésped, el Lleech muere con él.
 */
fun killPlayer(game: Game, playerId: String, cause: String = "ha muerto"): Game {
    val player = findPlayer(game, playerId) ?: return game
    val zombuul = player.characterId == "zombuul"
    val seemedDead = zombuul && player.reminders.any { it.sourceCharacterId == "zombuul" && it.label == ZOMBUUL_SEEMS_DEAD }
    val reallyDead = player.reminders.any { it.sourceCharacterId == "zombuul" && it.label == ZOMBUUL_REALLY_DEAD }
    if (seemedDead && !reallyDead) {
        val dead = withReminder(updatePlayer(game, playerId) { it.copy(alive = false) }, playerId, "zombuul", ZOMBUUL_REALLY_DEAD)
        return touch(if (player.alive) recordDeath(dead, playerId) else dead, "${player.name} $cause: el Zombuul muere de verdad.")
    }
    if (!player.alive) return game

    val host = if (player.characterId == "lleech") lleechHost(game) else null
    if (host != null && host.alive && host.id != playerId) {
        return touch(game, "${player.name} no muere: el Lleech vive mientras su huésped (${host.name}) siga vivo.")
    }

    var next = recordDeath(updatePlayer(game, playerId) { it.copy(alive = false) }, playerId)
    next = if (zombuul) {
        touch(withReminder(next, playerId, "zombuul", ZOMBUUL_SEEMS_DEAD), "${player.name} $cause… pero es el Zombuul: parece muerto y sigue vivo.")
    } else {
        touch(next, "${player.name} $cause.")
    }

    // Si era el huésped del Lleech, el Lleech muere con él.
    if (lleechHost(next)?.id == playerId) {
        for (lleech in next.players.filter { it.characterId == "lleech" && it.alive }) {
            next = killPlayer(next, lleech.id, "muere con su huésped")
        }
    }
    return next
}

/**
 * Un jugador vuelve a la vida. De noche (Profesor, Shabaloth…) la mesa no lo ve hasta el amanecer,
 * salvo que hubiera muerto esa misma noche: entonces, para la mesa, no ha pasado nada.
 */
fun revivePlayer(game: Game, playerId: String): Game {
    val player = findPlayer(game, playerId)
    if (player == null || player.alive) return game
    val night = game.phase.isNight
    val diedTonight = playerId in game.nightDeaths
    val hidden = night && !diedTonight
    val alive = updatePlayer(game, playerId) { p ->
        p.copy(
            alive = true,
            // Hasta el amanecer la mesa sigue viendo su voto fantasma como estaba.
            ghostVoteUsed = if (hidden) p.ghostVoteUsed else false,
            reminders = p.reminders.filter { it.label != ZOMBUUL_REALLY_DEAD },
        )
    }
    return touch(
        alive.copy(
            nightDeaths = game.nightDeaths.filter { it != playerId },
            dayDeaths = game.dayDeaths.filter { it != playerId },
            nightRevivals = if (hidden) game.nightRevivals + playerId else game.nightRevivals,
        ),
        "${player.name} vuelve a la vida${if (hidden) " (la mesa lo sabrá al amanecer)" else ""}.",
    )
}

fun setCharacter(game: Game, playerId: String, character: Character, keepAlignment: Boolean = false): Game {
    val player = findPlayer(game, playerId) ?: throw RuleViolation("Ese jugador no está en la partida.")
    val alignment = when {
        keepAlignment -> player.alignment
        character.team == Team.Minion || character.team == Team.Demon -> Alignment.Evil
        else -> Alignment.Good
    }
    return touch(
        updatePlayer(game, playerId) { it.copy(characterId = character.id, shownCharacterId = null, alignment = alignment) },
        "${player.name} pasa a ser ${character.name}.",
    )
}

/**
 * El Filósofo elige un personaje bueno y gana su habilidad: se le cambia la ficha por ese
 * personaje (así despierta como él) con el recordatorio «Es el Filósofo». Si el personaje está en
 * juego, quien lo tiene queda borracho mientras tanto.
 */
fun philosopherChoose(game: Game, philosopherId: String, characterId: String, characters: Map<String, Character>): GameMessage {
    val philosopher = findPlayer(game, philosopherId)
    val chosen = characters[characterId]
    if (philosopher == null || philosopher.characterId != "philosopher") throw RuleViolation("Ese jugador no es el Filósofo.")
    if (chosen == null || (chosen.team != Team.Townsfolk && chosen.team != Team.Outsider)) throw RuleViolation("El Filósofo elige un personaje bueno.")
    val works = healthy(philosopher)
    val holders = game.players.filter { it.id != philosopherId && it.characterId == characterId }
    var next = withReminder(setCharacter(game, philosopherId, chosen, true), philosopherId, "philosopher", "Es el Filósofo")
    if (works) for (h in holders) next = withReminder(next, h.id, "philosopher", REMINDER.drunk)
    val parts = mutableListOf("${philosopher.name} gana la habilidad de ${chosen.name}: desde ahora despierta como ${chosen.name}.")
    if (!works) {
        parts.add("Está borracho o envenenado: en realidad no la gana, dale información falsa.")
    } else if (holders.isNotEmpty()) {
        parts.add("${holders.joinToString(", ") { it.name }} (${chosen.name}) queda borracho.")
    }
    return GameMessage(next, parts.joinToString(" "))
}

/**
 * La Pit-Hag convierte a un jugador en un personaje que no está en juego. Conserva su bando. Si
 * crea un Demonio, las muertes de esa noche las decide el Narrador.
 */
fun pitHagChoose(game: Game, targetId: String, characterId: String, characters: Map<String, Character>): GameMessage {
    val target = findPlayer(game, targetId)
    val character = characters[characterId]
    if (target == null || character == null) throw RuleViolation("Ese jugador o personaje no existe.")
    if (game.players.any { it.characterId == characterId }) return GameMessage(game, "${character.name} ya está en juego: no pasa nada.")
    val hag = game.players.find { it.characterId == "pithag" && (it.alive || has(it, "vigormortis", REMINDER.hasAbility)) }
    if (hag == null || !healthy(hag)) {
        return GameMessage(game, "La Pit-Hag no tiene habilidad esta noche (borracha o envenenada): ${target.name} no cambia.")
    }
    val demon = if (character.team == Team.Demon) " Has creado un Demonio: las muertes de esta noche las decides tú." else ""
    return GameMessage(
        setCharacter(game, targetId, character, true),
        "${target.name} pasa a ser ${character.name}: despiértale y enséñale TÚ ERES y su nuevo personaje.$demon",
    )
}

/**
 * Dos jugadores intercambian personajes (con lo que cree ser cada uno). El Encantador de
 * serpientes intercambia también el bando; el Barbero, no.
 */
fun swapCharacters(game: Game, aId: String, bId: String, characters: Map<String, Character>, swapAlignment: Boolean = false): Game {
    val a = findPlayer(game, aId)
    val b = findPlayer(game, bId)
    if (a == null || b == null) throw RuleViolation("Ese jugador no está en la partida.")
    if (a.id == b.id) throw RuleViolation("Elige a otro jugador.")
    fun take(p: Player, from: Player): Player = p.copy(
        characterId = from.characterId,
        shownCharacterId = from.shownCharacterId,
        alignment = if (swapAlignment) from.alignment else p.alignment,
    )
    fun name(p: Player) = characters[p.characterId]?.name ?: p.characterId
    return touch(
        game.copy(players = game.players.map { p -> if (p.id == a.id) take(p, b) else if (p.id == b.id) take(p, a) else p }),
        "${a.name} y ${b.name} intercambian personajes${if (swapAlignment) " y bandos" else ""}: ${a.name} pasa a ser ${name(b)} y ${b.name}, ${name(a)}.",
    )
}

/**
 * El Encantador de serpientes elige a alguien: si es el Demonio (y el Encantador está en juego
 * y sano), intercambian personajes y bandos, y el antiguo Demonio queda envenenado para siempre.
 */
fun snakeCharmerChoose(game: Game, targetId: String, characters: Map<String, Character>): GameMessage {
    val target = findPlayer(game, targetId) ?: throw RuleViolation("Ese jugador no está en la partida.")
    val charmer = game.players.find { it.characterId == "snakecharmer" && it.alive && healthy(it) }
        ?: return GameMessage(game, "El Encantador de serpientes no tiene habilidad esta noche (no está en juego, está borracho o envenenado): no hay intercambio.")
    if (characters[target.characterId]?.team != Team.Demon || !reallyAlive(target)) {
        return GameMessage(game, "${target.name} no es el Demonio: no pasa nada.")
    }
    val swapped = swapCharacters(game, charmer.id, target.id, characters, true)
    return GameMessage(
        withReminder(swapped, target.id, "snakecharmer", REMINDER.poisoned),
        "${charmer.name} ha elegido al Demonio: ahora ${charmer.name} es el Demonio (malo) y ${target.name} el Encantador (bueno y envenenado). Despiértalos y enséñales TÚ ERES y su nuevo personaje.",
    )
}

fun setAlignment(game: Game, playerId: String, alignment: Alignment): Game =
    touch(updatePlayer(game, playerId) { it.copy(alignment = alignment) })

fun renamePlayer(game: Game, playerId: String, name: String): Game =
    touch(updatePlayer(game, playerId) { p -> p.copy(name = name.trim().ifEmpty { p.name }) })

/** Qué personaje cree ser quien no sabe el suyo (Borracho, Lunático, Marioneta). */
fun setShownCharacter(game: Game, playerId: String, shown: Character?): Game {
    val player = findPlayer(game, playerId) ?: throw RuleViolation("Ese jugador no está en la partida.")
    return touch(
        updatePlayer(game, playerId) { it.copy(shownCharacterId = shown?.id) },
        if (shown != null) "${player.name} cree ser ${shown.name}." else null,
    )
}

fun setGhostVoteUsed(game: Game, playerId: String, used: Boolean): Game =
    touch(updatePlayer(game, playerId) { it.copy(ghostVoteUsed = used) })

fun setNotes(game: Game, playerId: String, notes: String): Game =
    touch(updatePlayer(game, playerId) { it.copy(notes = notes) })

// ---------- Recordatorios ----------

fun addReminder(game: Game, playerId: String, reminder: ReminderToken): Game =
    touch(updatePlayer(game, playerId) { it.copy(reminders = it.reminders + reminder) })

/**
 * Pone un recordatorio como en el Grimorio físico: cada personaje tiene un número de fichas de
 * cada recordatorio. Si ya están todas puestas, se mueve la primera (el Envenenador cambia de víctima).
 */
fun placeReminder(game: Game, playerId: String, reminder: ReminderToken, copies: Int): Game {
    fun same(r: ReminderToken) = r.sourceCharacterId == reminder.sourceCharacterId && r.label == reminder.label
    val placed = game.players.flatMap { p -> p.reminders.filter(::same).map { p.id to it.id } }
    val moved = if (copies > 0 && placed.size >= copies) placed[0] else null
    val without = if (moved != null) removeReminder(game, moved.first, moved.second) else game
    return addReminder(without, playerId, reminder)
}

fun removeReminder(game: Game, playerId: String, reminderId: String): Game =
    touch(updatePlayer(game, playerId) { p -> p.copy(reminders = p.reminders.filter { it.id != reminderId }) })

/**
 * Recordatorios que solo valen una noche y el día siguiente: protecciones, envenenamientos del
 * Envenenador, muertes de esta noche… Al anochecer se proponen para quitarlos.
 * Clave: personaje que lo pone + etiqueta («custom» para los genéricos).
 */
val DUSK_REMINDERS: Set<String> = setOf(
    "poisoner:Envenenado",
    "monk:A salvo",
    "innkeeper:A salvo",
    "innkeeper:Borracho",
    "sailor:Borracho",
    "goon:Borracho",
    "minstrel:Borracho",
    "undertaker:Muerto hoy",
    "exorcist:Elegido",
    "devilsadvocate:Sobrevive ejecución",
    "witch:Maldito",
    "cerenovus:Loco",
    "harpy:Loco",
    "harpy:Segundo jugador",
    "lunatic:Elegido",
    "acrobat:Elegido",
    "flowergirl:Demonio ha votado",
    "flowergirl:Demonio no ha votado",
    "towncrier:Esbirro ha nominado",
    "towncrier:Esbirro no ha nominado",
    "zombuul:Muere hoy",
    "godfather:Muere hoy",
    "shabaloth:Vivo",
    "legion:A punto de morir",
    "organgrinder:A punto de morir",
    "custom:Protegido",
    "custom:Muerto esta noche",
    "bureaucrat:3 votos",
    "thief:Voto negativo",
)

/** La borrachera del Juglar: el día de la ejecución aún no caduca; al amanecer pasa a «Borracho». */
const val MINSTREL_TOMORROW = APP_REMINDER.minstrelTomorrow

/**
 * Las muertes de una noche («Muerto» de cualquier Demonio o personaje que mata) también caducan,
 * salvo las del Shabaloth: marcan a quién puede regurgitar la noche siguiente (y se mueven solas).
 */
private fun isDuskReminder(r: ReminderToken) =
    "${r.sourceCharacterId}:${r.label}" in DUSK_REMINDERS || (r.label == REMINDER.dead && r.sourceCharacterId != "shabaloth")

/** Un recordatorio que caduca y el jugador que lo tiene. */
data class ExpiringReminder(val playerId: String, val reminder: ReminderToken)

fun expiringReminders(game: Game): List<ExpiringReminder> =
    game.players.flatMap { p -> p.reminders.filter(::isDuskReminder).map { ExpiringReminder(p.id, it) } }

/** Quita varios recordatorios de una vez (los que caducan al anochecer). */
fun removeReminders(game: Game, reminderIds: List<String>): Game {
    if (reminderIds.isEmpty()) return game
    val ids = reminderIds.toSet()
    return touch(
        game.copy(players = game.players.map { p -> p.copy(reminders = p.reminders.filter { it.id !in ids }) }),
        "Se quitan ${ids.size} recordatorios que ya no valen.",
    )
}

/** Cambia el sitio de dos jugadores (por si se sientan distinto a como se apuntaron). */
fun swapSeats(game: Game, a: String, b: String): Game {
    val pa = findPlayer(game, a)
    val pb = findPlayer(game, b)
    if (pa == null || pb == null) throw RuleViolation("Ese jugador no está en la partida.")
    val players = game.players
        .map { p -> if (p.id == a) p.copy(seat = pb.seat) else if (p.id == b) p.copy(seat = pa.seat) else p }
        .sortedBy { it.seat }
    return touch(game.copy(players = players))
}

// ---------- Fases ----------

/** Amanece: se anuncian las muertes de la noche (desde ahora la mesa las ve). */
fun startDay(game: Game): Game {
    if (!game.phase.isNight) throw RuleViolation("Ya es de día.")
    val died = game.nightDeaths.mapNotNull { id -> findPlayer(game, id)?.name?.ifEmpty { null } }
    val revived = game.nightRevivals
    val next = game.copy(
        phase = Phase.day(game.phase.number),
        phaseStartedAt = nowIso(),
        timer = null,
        nominations = emptyList(),
        executedToday = null,
        nightDeaths = emptyList(),
        nightRevivals = emptyList(),
        dayDeaths = emptyList(),
        butcherNominationId = null,
        // El «No mata» de la Princesa solo valía la noche que acaba, y al nuevo Demonio ya se le ha dicho.
        // La borrachera del Juglar caduca en el crepúsculo de este día.
        players = game.players.map { p ->
            p.copy(
                ghostVoteUsed = if (p.id in revived) false else p.ghostVoteUsed,
                reminders = p.reminders
                    .filter { r -> !(r.sourceCharacterId == "princess" && r.label == "No mata") && r.label != NEW_DEMON }
                    .map { r -> if (r.sourceCharacterId == "minstrel" && r.label == MINSTREL_TOMORROW) r.copy(label = REMINDER.drunk) else r },
            )
        },
    )
    val back = revived.mapNotNull { id -> findPlayer(game, id)?.name?.ifEmpty { null } }
    val text = if (died.isNotEmpty()) "Amanece. Han muerto esta noche: ${died.joinToString(", ")}." else "Amanece. Nadie ha muerto esta noche."
    return touch(next, if (back.isNotEmpty()) "$text Vuelve a la vida: ${back.joinToString(", ")}." else text)
}

fun startNight(game: Game): Game {
    if (!game.phase.isDay) throw RuleViolation("Ya es de noche.")
    val next = game.copy(
        phase = Phase.night(game.phase.number + 1),
        nightStep = 0,
        phaseStartedAt = nowIso(),
        timer = null,
    )
    return touch(next, "Cae la noche.")
}

private val PHASE_CHANGE = Regex("^(Cae la noche|Amanece)")

/** Cuándo empezó la fase actual. En partidas de antes de guardarlo, lo dice el registro. */
fun phaseStart(game: Game): String {
    if (!game.phaseStartedAt.isNullOrEmpty()) return game.phaseStartedAt
    val change = game.log.lastOrNull { PHASE_CHANGE.containsMatchIn(it.text) }
    return change?.at ?: game.createdAt
}

/** Cuenta atrás para el debate o las nominaciones. No pasa por el registro ni por «Deshacer». */
fun setTimer(game: Game, seconds: Int, now: Instant = Clock.System.now()): Game =
    game.copy(timer = GameTimer(endsAt = isoString(now + seconds.seconds), seconds = seconds))

fun clearTimer(game: Game): Game = if (game.timer != null) game.copy(timer = null) else game

/** La información que el Narrador ha dado esta noche, apuntada en el registro. */
fun noteInfoGiven(game: Game, lines: List<String>): Game = lines.fold(game) { g, text -> touch(g, text) }

fun setNightStep(game: Game, step: Int): Game = game.copy(nightStep = maxOf(0, step))

fun setBluffs(game: Game, bluffs: List<String>): Game = touch(game.copy(demonBluffs = bluffs.take(3)))

/** El Narrador pone o quita una Fábula o un Lórico (y lo anuncia a la mesa). */
fun toggleFabled(game: Game, character: Character): Game {
    if (character.team != Team.Fabled && character.team != Team.Loric) throw RuleViolation("${character.name} no es una Fábula ni un Lórico.")
    val current = game.fabledIds
    val on = character.id in current
    return touch(
        game.copy(fabledIds = if (on) current.filter { it != character.id } else current + character.id),
        "${character.name} ${if (on) "sale de" else "entra en"} juego.",
    )
}

// ---------- Nominaciones y votos ----------

/** La Banshee que ha muerto por el Demonio: nomina dos veces al día y vota dos veces, aunque esté muerta. */
private fun isBanshee(p: Player) = p.characterId == "banshee" && has(p, "banshee", REMINDER.hasAbility)

/** Con el Voudon vivo solo votan él y los muertos (sin gastar su voto) y basta un voto. */
private fun voudonActive(game: Game) = game.players.any { it.characterId == "voudon" && it.alive && healthy(it) }

/** Nominaciones para ejecutar (los exilios de Viajeros van aparte). */
private fun executionNominations(game: Game) = game.nominations.filter { it.exile != true }

/** Votos necesarios: la mitad de los vivos, redondeando hacia arriba (con el Voudon, basta 1). */
fun voteThreshold(game: Game): Int = if (voudonActive(game)) 1 else half(alivePlayers(game).size)

/** Para exiliar a un Viajero hace falta la mitad de todos los jugadores, vivos o muertos. */
fun exileThreshold(game: Game): Int = half(game.players.size)

/** El Carnicero vivo y sano puede nominar una vez más después de la ejecución de hoy. */
fun butcherCanNominate(game: Game): Boolean =
    !game.executedToday.isNullOrEmpty() && game.butcherNominationId.isNullOrEmpty() &&
        game.players.any { it.characterId == "butcher" && it.alive && healthy(it) }

fun canNominate(game: Game, nominatorId: String, nomineeId: String): String? {
    if (!game.phase.isDay) return "Solo se nomina de día."
    if (game.nominations.any { !it.closed }) return "Primero hay que cerrar la votación en curso."
    val nominator = findPlayer(game, nominatorId) ?: return "Ese jugador no está en la partida."
    val executed = !game.executedToday.isNullOrEmpty()
    val butcherAgain = executed && nominator.characterId == "butcher" && butcherCanNominate(game)
    if (executed && !butcherAgain) return "Ya ha habido una ejecución hoy."
    if (!nominator.alive && !isBanshee(nominator)) return "Solo los jugadores vivos pueden nominar."
    val made = executionNominations(game).count { it.nominatorId == nominatorId }
    if (!butcherAgain && made >= (if (isBanshee(nominator)) 2 else 1)) return "${nominator.name} ya ha nominado hoy."
    if (nominator.characterId == "golem" && has(nominator, "golem", "No puede nominar")) return "${nominator.name} es el Gólem y ya nominó una vez."
    if (nomineeId.isNotEmpty() && executionNominations(game).any { it.nomineeId == nomineeId }) return "Ese jugador ya ha sido nominado hoy."
    return null
}

/** El maldito de la Bruja (viva, sana y con más de 3 vivos) muere si nomina. */
fun witchCursed(game: Game, nominatorId: String): Boolean {
    val nominator = findPlayer(game, nominatorId)
    val witch = game.players.any { it.characterId == "witch" && it.alive && healthy(it) }
    return nominator != null && witch && has(nominator, "witch", "Maldito") && alivePlayers(game).size > 3
}

/**
 * Nominar. La nominación se hace igual, pero:
 * - El maldito de la Bruja muere al nominar.
 * - El Gólem solo nomina una vez y, si el nominado no es el Demonio, muere.
 */
fun nominate(game: Game, nominatorId: String, nomineeId: String, id: String, characters: Map<String, Character>? = null): Game {
    val problem = canNominate(game, nominatorId, nomineeId)
    if (problem != null) throw RuleViolation(problem)
    val nomination = Nomination(id = id, nominatorId = nominatorId, nomineeId = nomineeId, voterIds = emptyList(), closed = false)
    val nominator = findPlayer(game, nominatorId)!!
    val nominee = findPlayer(game, nomineeId) ?: throw RuleViolation("Ese jugador no está en la partida.")
    // Tras la ejecución, solo el Carnicero puede nominar: se recuerda cuál es su nominación.
    val butcher = if (!game.executedToday.isNullOrEmpty()) game.copy(butcherNominationId = id) else game
    var next = touch(butcher.copy(nominations = game.nominations + nomination), "${nominator.name} nomina a ${nominee.name}.")
    if (witchCursed(game, nominatorId)) next = killPlayer(next, nominatorId, "muere por la maldición de la Bruja")
    if (nominator.characterId == "golem") {
        next = withReminder(next, nominatorId, "golem", "No puede nominar")
        val demon = characters != null && characters[nominee.characterId]?.team == Team.Demon
        if (characters != null && healthy(nominator) && !demon) next = killPlayer(next, nomineeId, "muere por el Gólem")
    }
    return next
}

/** Exiliar a un Viajero vivo: cualquiera puede pedirlo, una vez por Viajero y día. */
fun canExile(game: Game, travellerId: String, characters: Map<String, Character>): String? {
    if (!game.phase.isDay) return "Solo se exilia de día."
    if (game.nominations.any { !it.closed }) return "Primero hay que cerrar la votación en curso."
    val traveller = findPlayer(game, travellerId)
    if (traveller == null || characters[traveller.characterId]?.team != Team.Traveller) return "Solo se puede exiliar a los Viajeros."
    if (!traveller.alive) return "${traveller.name} ya está muerto."
    if (game.nominations.any { it.exile == true && it.nomineeId == travellerId }) return "Hoy ya se ha pedido el exilio de ${traveller.name}."
    return null
}

fun callExile(game: Game, travellerId: String, id: String, characters: Map<String, Character>): Game {
    val problem = canExile(game, travellerId, characters)
    if (problem != null) throw RuleViolation(problem)
    val nomination = Nomination(id = id, nominatorId = "", nomineeId = travellerId, voterIds = emptyList(), closed = false, exile = true)
    return touch(game.copy(nominations = game.nominations + nomination), "Se pide el exilio de ${findPlayer(game, travellerId)!!.name}.")
}

/**
 * Si un jugador puede votar: los vivos, y los muertos con su voto fantasma. En un exilio vota
 * todo el mundo; con el Voudon, solo él y los muertos; la Banshee con habilidad, siempre.
 */
fun canVote(player: Player, game: Game? = null, nomination: Nomination? = null): Boolean {
    if (nomination?.exile == true) return true
    if (game != null && voudonActive(game)) return player.characterId == "voudon" || !player.alive
    if (isBanshee(player)) return true
    return player.alive || !player.ghostVoteUsed
}

/**
 * Cuánto vale el voto de un jugador hoy: el elegido anoche por el Burócrata vota ×3, el
 * elegido por el Ladrón vota en negativo y la Banshee con habilidad vota dos veces.
 */
fun voteWeight(player: Player): Int {
    if (has(player, "thief", "Voto negativo")) return -1
    if (has(player, "bureaucrat", "3 votos")) return 3
    if (isBanshee(player)) return 2
    return 1
}

/** El señor del Mayordomo (el jugador con su recordatorio «Señor»). */
private fun butlerMaster(game: Game) = game.players.find { has(it, "butler", "Señor") }

/**
 * El voto del Mayordomo sano no cuenta si su señor no vota en esa nominación. Devuelve el motivo,
 * o null si el voto cuenta.
 */
fun voteIgnored(game: Game, nomination: Nomination, player: Player): String? {
    if (player.characterId != "butler" || !healthy(player) || nomination.exile == true) return null
    val master = butlerMaster(game)
    return if (master != null && master.id !in nomination.voterIds) {
        "${player.name} es el Mayordomo y su señor (${master.name}) no vota: su voto no cuenta."
    } else {
        null
    }
}

/** El Fanático sano debe votar en todas las nominaciones si hay 5 o más vivos. */
fun mustVote(game: Game, player: Player): Boolean =
    player.characterId == "zealot" && player.alive && healthy(player) && alivePlayers(game).size >= 5

/** Votos de una nominación, con el peso de cada voto (Burócrata, Ladrón, Banshee) y sin el Mayordomo sin su señor. */
fun voteCount(game: Game, nomination: Nomination): Int =
    nomination.voterIds.fold(0) { sum, id ->
        val voter = findPlayer(game, id) ?: return@fold sum + 1
        if (voteIgnored(game, nomination, voter) != null) sum else sum + voteWeight(voter)
    }

fun toggleVote(game: Game, nominationId: String, voterId: String): Game {
    val nomination = game.nominations.find { it.id == nominationId }
    if (nomination == null || nomination.closed) throw RuleViolation("Esa votación ya está cerrada.")
    val voter = findPlayer(game, voterId) ?: throw RuleViolation("Ese jugador no está en la partida.")
    val voting = voterId in nomination.voterIds
    if (!voting && !canVote(voter, game, nomination)) {
        throw RuleViolation(
            if (voudonActive(game) && voter.alive) "Con el Voudon, ${voter.name} no puede votar." else "${voter.name} está muerto y ya gastó su voto.",
        )
    }
    val voterIds = if (voting) nomination.voterIds.filter { it != voterId } else nomination.voterIds + voterId
    return game.copy(nominations = game.nominations.map { if (it.id == nominationId) it.copy(voterIds = voterIds) else it })
}

/**
 * Cierra la votación: los muertos que han votado gastan su voto (salvo en un exilio, con el
 * Voudon o la Banshee). Un exilio con votos suficientes mata al Viajero en el acto.
 */
fun closeNomination(game: Game, nominationId: String): Game {
    val nomination = game.nominations.find { it.id == nominationId }
    if (nomination == null || nomination.closed) return game
    val exile = nomination.exile == true
    val freeVotes = exile || voudonActive(game)
    val players = game.players.map { p ->
        if (!p.alive && !freeVotes && !isBanshee(p) && p.id in nomination.voterIds) p.copy(ghostVoteUsed = true) else p
    }
    val nominee = findPlayer(game, nomination.nomineeId) ?: throw RuleViolation("Ese jugador no está en la partida.")
    val votes = voteCount(game, nomination)
    var next = touch(
        game.copy(
            players = players,
            nominations = game.nominations.map { if (it.id == nominationId) it.copy(closed = true) else it },
        ),
        "${nominee.name} recibe $votes votos${if (exile) " para el exilio" else ""}.",
    )
    if (exile) {
        next = if (votes >= exileThreshold(game)) killPlayer(next, nominee.id, "es exiliado") else touch(next, "${nominee.name} no es exiliado.")
    }
    return next
}

/** Quién está «a punto de morir» y con cuántos votos. */
data class OnTheBlock(val playerId: String, val votes: Int)

/**
 * Quién está «a punto de morir»: el nominado con más votos si llega al umbral. Si empata con
 * otro, no se ejecuta a nadie (con el Voudon, sigue el primero hasta que otro le supere).
 * Tras la ejecución, solo cuenta la nominación del Carnicero.
 */
fun onTheBlock(game: Game): OnTheBlock? {
    val threshold = voteThreshold(game)
    if (!game.executedToday.isNullOrEmpty()) {
        // Ya hubo ejecución: solo queda la nominación del Carnicero (si la hay y aún no se ha ejecutado).
        val n = game.nominations.find { it.id == game.butcherNominationId && it.closed }
        val votes = if (n != null) voteCount(game, n) else 0
        return if (n != null && votes >= threshold && game.executedToday != n.nomineeId) OnTheBlock(n.nomineeId, votes) else null
    }
    val keepFirst = voudonActive(game)
    var best: OnTheBlock? = null
    var tied = false
    for (n in executionNominations(game).filter { it.closed }) {
        val votes = voteCount(game, n)
        if (votes < threshold) continue
        if (best == null || votes > best.votes) {
            best = OnTheBlock(n.nomineeId, votes)
            tied = false
        } else if (votes == best.votes && !keepFirst) {
            tied = true
        }
    }
    return if (tied) null else best
}

/**
 * Quien sobrevive a ser ejecutado: el elegido por el Abogado del diablo (sano), el Marinero sano,
 * el Bufón (una vez) y los vecinos buenos de la Dama del té.
 */
private fun survivesExecution(game: Game, player: Player): String? {
    if (has(player, "devilsadvocate", "Sobrevive ejecución") && protectionWorks(game, "devilsadvocate")) return "el Abogado del diablo le protege"
    if (player.characterId == "sailor" && healthy(player)) return "el Marinero no puede morir"
    if (player.characterId == "fool" && healthy(player)) return "el Bufón no muere la primera vez"
    if (teaLadyProtects(game, player)) return "la Dama del té protege a sus vecinos buenos"
    return null
}

/**
 * Ejecución. El Abogado del diablo, el Marinero y el Bufón sobreviven (pero cuenta como
 * ejecución). Con `characters`, si se ejecuta al Demonio y la Mente maestra está viva, se
 * juega un día más.
 */
fun execute(game: Game, playerId: String, characters: Map<String, Character>? = null): Game {
    if (!game.phase.isDay) throw RuleViolation("Solo se ejecuta de día.")
    val executed = !game.executedToday.isNullOrEmpty()
    val butcherNomination = game.nominations.find { it.id == game.butcherNominationId }
    val butcherExecution = executed && butcherNomination?.nomineeId == playerId && game.executedToday != playerId
    if (executed && !butcherExecution) throw RuleViolation("Solo hay una ejecución al día.")
    val player = findPlayer(game, playerId) ?: throw RuleViolation("Ese jugador no está en la partida.")

    val survives = if (player.alive) survivesExecution(game, player) else null
    var after: Game
    if (survives != null) {
        after = touch(game, "${player.name} es ejecutado, pero sigue vivo ($survives).")
        if (player.characterId == "fool") after = withReminder(after, playerId, "fool", REMINDER.noAbility)
    } else {
        // El Zombuul que solo parecía muerto sí muere al ser ejecutado.
        after = if (player.alive || zombuulSeemsDead(player)) {
            killPlayer(game, playerId, "es ejecutado")
        } else {
            touch(game, "${player.name} es ejecutado (ya estaba muerto).")
        }
    }
    // Juglar: si muere un Esbirro ejecutado, todos los demás (salvo Viajeros) están borrachos hasta
    // el crepúsculo de mañana: cada uno lleva su «Borracho» y así lo tienen en cuenta todas las reglas.
    val minstrel = after.players.find { it.characterId == "minstrel" && it.alive && healthy(it) }
    val minionDied = characters?.get(player.characterId)?.team == Team.Minion && player.alive && findPlayer(after, playerId)?.alive != true
    if (characters != null && minstrel != null && minionDied) {
        for (p in after.players) {
            if (p.id != minstrel.id && characters[p.characterId]?.team != Team.Traveller) after = withReminder(after, p.id, "minstrel", MINSTREL_TOMORROW)
        }
        after = touch(after, "Ha muerto un Esbirro ejecutado: por el Juglar, todos están borrachos hasta el crepúsculo de mañana.")
    }
    // El Leviatán cuenta los buenos ejecutados: con el segundo, gana el Mal.
    val leviathan = after.players.any { it.characterId == "leviathan" && reallyAlive(it) }
    if (leviathan && player.alignment == Alignment.Good) after = withReminder(after, playerId, "leviathan", LEVIATHAN_GOOD_EXECUTED)
    // Princesa: si en su primer día nomina y se ejecuta a ese jugador, el Demonio no mata esta noche.
    val princessNomination = game.nominations.find {
        it.exile != true && it.nomineeId == playerId && findPlayer(game, it.nominatorId)?.characterId == "princess"
    }
    val princess = princessNomination?.let { findPlayer(game, it.nominatorId) }
    if (princess != null && healthy(princess) && game.phase.number == 1) after = withReminder(after, princess.id, "princess", "No mata")
    // Mente maestra: el Demonio ejecutado no acaba la partida; se juega un día más.
    if (characters != null && (game.mastermindDay ?: 0) == 0) {
        val mastermind = after.players.find { it.characterId == "mastermind" && it.alive && healthy(it) }
        val demons = demonPlayers(after, characters)
        if (mastermind != null && demons.any { it.id == playerId } && demons.none(::reallyAlive) && demonSuccession(after, characters) == null) {
            after = touch(
                after.copy(mastermindDay = game.phase.number + 1),
                "La Mente maestra sigue viva: se juega un día más (no anuncies que ha muerto el Demonio).",
            )
        }
    }
    return after.copy(executedToday = playerId)
}

/**
 * Ejecución que no sale de una votación (la Virgen, el Juez, el Visir, la locura del
 * Cerenovus…). Si había una votación abierta, se cierra.
 */
fun executeNow(game: Game, playerId: String, reason: String? = null, characters: Map<String, Character>? = null): Game {
    val open = game.nominations.find { !it.closed }
    val closed = if (open != null) game.copy(nominations = game.nominations.map { if (it.id == open.id) it.copy(closed = true) else it }) else game
    return execute(if (!reason.isNullOrEmpty()) touch(closed, reason) else closed, playerId, characters)
}

/**
 * El Exterminador dispara de día, en público. Solo pasa algo si quien dispara es de verdad el
 * Exterminador, vivo y sin haber disparado: gasta el disparo y, si está sano y el elegido es el
 * Demonio, el Demonio muere (no es una ejecución). El Recluso puede registrarse como Demonio:
 * eso lo decide el Narrador.
 */
fun slayerShot(game: Game, shooterId: String, targetId: String, characters: Map<String, Character>): GameMessage {
    val shooter = findPlayer(game, shooterId)
    val target = findPlayer(game, targetId)
    if (shooter == null || target == null) throw RuleViolation("Ese jugador no está en la partida.")
    val shot = "${shooter.name} dispara a ${target.name}"
    fun nothing(why: String) = GameMessage(touch(game, "$shot: $why"), "$shot: $why")
    if (shooter.characterId != "slayer") return nothing("no es el Exterminador, no pasa nada.")
    if (!shooter.alive) return nothing("está muerto y no tiene habilidad, no pasa nada.")
    if (has(shooter, "slayer", REMINDER.noAbility)) return nothing("ya había disparado, no pasa nada.")
    val used = withReminder(game, shooterId, "slayer", REMINDER.noAbility)
    fun done(why: String, next: Game = used) = GameMessage(touch(next, "$shot: $why"), "$shot: $why")
    if (!healthy(shooter)) return done("está borracho o envenenado, no pasa nada (y gasta su disparo).")
    if (!target.alive) return done("ya estaba muerto, no pasa nada.")
    if (characters[target.characterId]?.team != Team.Demon) {
        val recluse = if (target.characterId == "recluse") " El Recluso puede registrarse como Demonio: si lo decides, márcalo muerto." else ""
        return done("no es el Demonio, no pasa nada.$recluse")
    }
    return done("es el Demonio y muere.", killPlayer(used, targetId, "muere por el disparo del Exterminador"))
}

private const val VIRGIN_USED = REMINDER.noAbility

/** Lo que pasa al nominar a la Virgen: la partida, si hay que ejecutar a quien nomina y si es el Espía. */
data class VirginResult(val game: Game, val executes: Boolean, val spy: Boolean)

/**
 * La Virgen nominada por primera vez: pierde su habilidad y, si quien nomina es un Aldeano
 * (y la Virgen está sana), ese Aldeano es ejecutado en el acto. Devuelve si hay que ejecutar.
 * El Espía puede registrarse como Aldeano: eso lo decide el Narrador.
 */
fun virginNomination(game: Game, nominatorId: String, nomineeId: String, characters: Map<String, Character>): VirginResult {
    val virgin = findPlayer(game, nomineeId)
    val nominator = findPlayer(game, nominatorId)
    if (virgin == null || nominator == null || virgin.characterId != "virgin" ||
        virgin.reminders.any { it.sourceCharacterId == "virgin" && it.label == VIRGIN_USED }
    ) {
        return VirginResult(game, executes = false, spy = false)
    }
    val used = withReminder(game, nomineeId, "virgin", VIRGIN_USED)
    val working = healthy(virgin)
    val townsfolk = characters[nominator.characterId]?.team == Team.Townsfolk
    return VirginResult(used, executes = working && townsfolk, spy = working && nominator.characterId == "spy")
}

// ---------- Fin de partida ----------

data class VictoryHint(val winner: Alignment, val reason: String)

/** Quién pasa a ser el Demonio cuando muere: la Mujer escarlata o un Esbirro (pase del Diablillo). */
data class DemonSuccession(
    /** El Demonio que ha muerto: el heredero pasa a ser ese mismo personaje. */
    val demon: Player,
    /** Uno solo si es obligatorio (la Mujer escarlata); varios si el Narrador elige. */
    val heirs: List<Player>,
    val reason: String,
    /** Algo que puede impedirlo (la Mujer escarlata está envenenada o borracha). */
    val caveat: String? = null,
    /** El «Demonio» muerto era quien cuidaba a Lil' Monsta: el heredero pasa a cuidarlo. */
    val babysitter: Boolean = false,
)

/**
 * Si el Demonio ha muerto pero la partida sigue porque otro jugador pasa a serlo:
 * - Mujer escarlata viva y 5 o más jugadores vivos justo antes de morir el Demonio (4 o más
 *   después; los Viajeros no cuentan): se convierte en ese Demonio, de día o de noche.
 * - Si no, el Diablillo muerto de noche (se ha matado a sí mismo): un Esbirro vivo, a elegir.
 */
fun demonSuccession(game: Game, characters: Map<String, Character>): DemonSuccession? {
    if (game.status == GameStatus.Finished) return null
    fun team(p: Player) = characters[p.characterId]?.team
    val demons = demonPlayers(game, characters)
    if (demons.isEmpty() || demons.any(::reallyAlive)) return null
    val demon = demons[0]
    // Si el muerto cuidaba a Lil' Monsta, lo que se hereda es cuidarlo.
    val babysitter = team(demon) != Team.Demon
    val demonName = if (babysitter) "Lil' Monsta" else (characters[demon.characterId]?.name ?: "el Demonio")
    val aliveAfter = game.players.count { reallyAlive(it) && team(it) != Team.Traveller }

    val scarlet = game.players.find { it.characterId == "scarletwoman" && it.alive }
    if (scarlet != null && aliveAfter >= 4) {
        val disabled = scarlet.reminders.find { disablesAbility(it) }
        return DemonSuccession(
            demon = demon,
            heirs = listOf(scarlet),
            reason = if (babysitter) {
                "Ha muerto quien cuidaba a Lil' Monsta con ${aliveAfter + 1} jugadores vivos: la Mujer escarlata cuida a Lil' Monsta el resto de la partida."
            } else {
                "Ha muerto el Demonio ($demonName) con ${aliveAfter + 1} jugadores vivos: la Mujer escarlata se convierte en el nuevo Demonio y la partida sigue."
            },
            caveat = disabled?.let { "La Mujer escarlata tiene «${it.label}»: si de verdad no tiene habilidad, ganan los buenos." },
            babysitter = babysitter,
        )
    }

    if (demon.characterId == "imp" && game.phase.isNight) {
        val minions = game.players.filter { it.alive && team(it) == Team.Minion }
        if (minions.isNotEmpty()) {
            return DemonSuccession(
                demon = demon,
                heirs = minions,
                reason = "Si el Demonio ($demonName) se ha matado a sí mismo esta noche, elige un Esbirro vivo: se convierte en el nuevo Demonio. Si ha muerto por otra cosa, ganan los buenos.",
            )
        }
    }
    return null
}

// ---------- Ataque del Demonio ----------

/** Protecciones que impiden morir por el Demonio esta noche (Monje, Posadero, Dama del té…). */
private val PROTECTION = Regex("^(a salvo|protegido|no puede morir)$", RegexOption.IGNORE_CASE)

/** El recordatorio «Muerto» de un Demonio: el jugador elegido muere esta noche. */
fun isDemonKill(reminder: ReminderToken, characters: Map<String, Character>): Boolean =
    isDemonKill(reminder.sourceCharacterId, reminder.label, characters)

fun isDemonKill(sourceCharacterId: String, label: String, characters: Map<String, Character>): Boolean =
    label == REMINDER.dead && characters[sourceCharacterId]?.team == Team.Demon

enum class DemonKillOutcome(val key: String) {
    Killed("killed"),
    Protected("protected"),
    SelfKill("selfKill"),
    Mayor("mayor"),
    Jump("jump"),
    Fool("fool"),
    Banshee("banshee"),
    Blocked("blocked"),
    AlreadyDead("alreadyDead"),
    Drunk("drunk"),
}

data class DemonKillResult(val game: Game, val outcome: DemonKillOutcome)

/**
 * Un recordatorio de protección solo vale si quien lo pone está de verdad en juego y sano: el
 * «A salvo» de un Monje que es el Borracho, o de un Monje envenenado, no protege. Los propios del
 * Narrador («custom») siempre valen.
 */
private fun protectionWorks(game: Game, source: String) =
    source == "custom" || game.players.any { it.characterId == source && healthy(it) }

/** Vecino vivo de una Dama del té viva y sana cuyos dos vecinos vivos son buenos. */
private fun teaLadyProtects(game: Game, target: Player) =
    game.players.any { lady ->
        if (lady.characterId != "tealady" || !lady.alive || !healthy(lady)) return@any false
        val neighbours = aliveNeighbours(game, lady)
        neighbours.any { it.id == target.id } && neighbours.all { it.alignment == Alignment.Good }
    }

/**
 * No muere: Soldado o Marinero sanos, vecinos buenos de la Dama del té, o con «A salvo»,
 * «Protegido» o «No puede morir» de alguien cuya habilidad funciona.
 */
private fun isProtected(game: Game, target: Player) =
    ((target.characterId == "soldier" || target.characterId == "sailor") && healthy(target)) ||
        teaLadyProtects(game, target) ||
        target.reminders.any { PROTECTION.matches(it.label) && protectionWorks(game, it.sourceCharacterId) }

/**
 * Por qué el Demonio no mata esta noche: el Exorcista le ha elegido o la Princesa ejecutó en su
 * primer día. Devuelve el aviso, o null si mata con normalidad.
 */
fun demonBlocked(game: Game, demonCharacterId: String): String? {
    val exorcist = game.players.any { it.characterId == "exorcist" && healthy(it) }
    val chosen = game.players.any { it.characterId == demonCharacterId && has(it, "exorcist", "Elegido") }
    if (exorcist && chosen) return "El Exorcista ha elegido al Demonio: esta noche no se despierta ni mata."
    if (game.players.any { has(it, "princess", "No mata") }) return "La Princesa ejecutó en su primer día: el Demonio no mata esta noche."
    return null
}

/** El Bufón sano que aún no ha usado su habilidad sobrevive a su primera muerte. */
private fun foolSurvives(p: Player) = p.characterId == "fool" && healthy(p)

/**
 * El Demonio ataca: el jugador muere en ese momento (se anuncia al amanecer), salvo que esté
 * protegido (Soldado sano, «A salvo», «Protegido», «No puede morir»). Si el Demonio se elige a sí
 * mismo, muere y demonSuccession ofrece el heredero. Al Alcalde se le avisa: puede morir otro.
 * Fang Gu: el primer Forastero que elige se convierte en Fang Gu malo y el Fang Gu muere en su lugar.
 */
fun demonKill(
    game: Game,
    targetId: String,
    reminder: ReminderToken,
    copies: Int,
    characters: Map<String, Character>,
): DemonKillResult {
    val target = findPlayer(game, targetId) ?: throw RuleViolation("Ese jugador no está en la partida.")
    if (!target.alive && !zombuulSeemsDead(target)) return DemonKillResult(placeReminder(game, targetId, reminder, copies), DemonKillOutcome.AlreadyDead)
    if (demonBlocked(game, reminder.sourceCharacterId) != null) return DemonKillResult(game, DemonKillOutcome.Blocked)
    // Un Demonio borracho o envenenado (Marinero, Matón, Cortesano…) no mata.
    val attacker = game.players.find { it.characterId == reminder.sourceCharacterId && reallyAlive(it) }
    if (attacker != null && !healthy(attacker)) return DemonKillResult(game, DemonKillOutcome.Drunk)
    if (isProtected(game, target)) return DemonKillResult(game, DemonKillOutcome.Protected)
    val demonCharacter = characters[reminder.sourceCharacterId]
    val demon = demonCharacter?.name ?: "el Demonio"

    if (reminder.sourceCharacterId == "fanggu" && characters[target.characterId]?.team == Team.Outsider && target.alive) {
        val jumped = game.players.any { p -> p.reminders.any { it.sourceCharacterId == "fanggu" && it.label == "Primera vez" } }
        val fangGu = game.players.find { it.characterId == "fanggu" && it.alive }
        // Un Fang Gu protegido no salta: el Forastero muere sin más.
        if (!jumped && fangGu != null && !isProtected(game, fangGu) && demonCharacter != null) {
            var next = killPlayer(game, fangGu.id, "muere: salta a ${target.name}")
            next = setCharacter(next, targetId, demonCharacter)
            next = withReminder(next, targetId, "fanggu", "Primera vez")
            return DemonKillResult(next, DemonKillOutcome.Jump)
        }
    }

    if (foolSurvives(target)) {
        return DemonKillResult(withReminder(game, targetId, "fool", REMINDER.noAbility), DemonKillOutcome.Fool)
    }

    val self = target.characterId == reminder.sourceCharacterId
    var next = killPlayer(placeReminder(game, targetId, reminder, copies), targetId, if (self) "se mata a sí mismo ($demon)" else "muere por $demon")
    // Banshee sana muerta por el Demonio: todos lo saben y gana su habilidad.
    if (target.characterId == "banshee" && healthy(target)) next = withReminder(next, targetId, "banshee", REMINDER.hasAbility)
    // Si el Demonio mata al nieto, la Abuela muere también.
    if (has(target, "grandmother", "Nieto")) {
        for (grandmother in next.players.filter { it.characterId == "grandmother" && it.alive && healthy(it) }) {
            next = killPlayer(next, grandmother.id, "muere con su nieto")
        }
    }
    val outcome = when {
        self -> DemonKillOutcome.SelfKill
        target.characterId == "mayor" -> DemonKillOutcome.Mayor
        target.characterId == "banshee" && healthy(target) -> DemonKillOutcome.Banshee
        else -> DemonKillOutcome.Killed
    }
    return DemonKillResult(next, outcome)
}

/** Lo que deja la elección del Pukka: quién muere por el veneno anterior y quién se salva. */
data class PukkaResult(val game: Game, val died: List<Player>, val saved: List<Player>)

/**
 * Pukka: el elegido queda envenenado y el envenenado anterior muere (salvo protección) y queda
 * sano. Solo hay una víctima envenenada viva a la vez.
 */
fun pukkaChoose(game: Game, targetId: String, reminder: ReminderToken): PukkaResult {
    fun isPukkaPoison(r: ReminderToken) = r.sourceCharacterId == "pukka" && r.label == REMINDER.poisoned
    val previous = game.players.filter { p -> p.reminders.any(::isPukkaPoison) }
    // Primero envenena al elegido: si es quien protegía al anterior (la Dama del té, el Monje…), ya no le protege.
    var next = addReminder(game, targetId, reminder)
    val died = mutableListOf<Player>()
    val saved = mutableListOf<Player>()
    for (p in previous) {
        // Muere (si no está protegido) todavía envenenado, y después está sano.
        val protectedNow = p.alive && isProtected(next, findPlayer(next, p.id)!!)
        next = updatePlayer(next, p.id) { x -> x.copy(reminders = x.reminders.filter { it.id == reminder.id || !isPukkaPoison(it) }) }
        if (!p.alive) continue
        if (protectedNow) {
            saved.add(p)
        } else {
            next = killPlayer(next, p.id, "muere por el veneno del Pukka")
            died.add(p)
        }
    }
    return PukkaResult(next, died, saved)
}

/**
 * No puede morir esta noche por nada (no solo por el Demonio): Marinero sano, vecino bueno de la
 * Dama del té o «A salvo» de un Posadero sano. El Monje y el Soldado solo protegen del Demonio.
 */
private fun cannotDie(game: Game, target: Player) =
    (target.characterId == "sailor" && healthy(target)) ||
        teaLadyProtects(game, target) ||
        target.reminders.any {
            (it.label == "No puede morir" || (it.sourceCharacterId == "innkeeper" && it.label == REMINDER.safe)) &&
                protectionWorks(game, it.sourceCharacterId)
        }

private const val ASSASSIN_USED = REMINDER.noAbility

/**
 * Un Esbirro que mata de noche. El Asesino (una vez por partida) mata aunque el elegido no pudiera
 * morir; el Padrino respeta lo que impide morir (Marinero, Dama del té, Posadero, Bufón).
 */
fun minionKill(game: Game, targetId: String, reminder: ReminderToken, characters: Map<String, Character>): GameMessage {
    val target = findPlayer(game, targetId) ?: throw RuleViolation("Ese jugador no está en la partida.")
    val source = reminder.sourceCharacterId
    // El Esbirro muerto por el Vigormortis conserva su habilidad.
    val killer = game.players.find { it.characterId == source && (it.alive || has(it, "vigormortis", REMINDER.hasAbility)) }
    val name = characters[source]?.name ?: source
    val copies = reminderCopies(reminder, characters)
    if (source == "assassin" && killer != null && has(killer, "assassin", ASSASSIN_USED)) {
        return GameMessage(game, "El $name ya usó su habilidad: ${target.name} no muere.")
    }
    var next = placeReminder(game, targetId, reminder, copies)
    if (source == "assassin" && killer != null) next = withReminder(next, killer.id, "assassin", ASSASSIN_USED)
    if (killer == null || !healthy(killer)) return GameMessage(next, "El $name no tiene habilidad esta noche (borracho o envenenado): ${target.name} no muere.")
    if (!target.alive) return GameMessage(next, "${target.name} ya estaba muerto.")
    if (source != "assassin") {
        if (cannotDie(next, target)) return GameMessage(next, "${target.name} no puede morir esta noche y no muere.")
        if (foolSurvives(target)) {
            return GameMessage(withReminder(next, targetId, "fool", REMINDER.noAbility), "${target.name} es el Bufón: esta vez no muere y pierde su habilidad.")
        }
    }
    next = killPlayer(next, targetId, "muere por el $name")
    return GameMessage(next, "${target.name} muere esta noche${if (source == "assassin") ", aunque estuviera protegido" else ""}. Se anunciará al amanecer.")
}

/** Tantas fichas de recordatorio como tiene el personaje en su ficha (0: sin límite). */
private fun reminderCopies(reminder: ReminderToken, characters: Map<String, Character>) =
    characters[reminder.sourceCharacterId]?.reminders?.count { it == reminder.label } ?: 0

/**
 * Matón: el primer jugador que le elige cada noche por su habilidad queda borracho hasta el
 * crepúsculo, y el Matón pasa a su bando. Null si no es el caso.
 */
private fun goonChosen(game: Game, targetId: String, sourceId: String, characters: Map<String, Character>): GameMessage? {
    val goon = findPlayer(game, targetId)
    if (!game.phase.isNight || goon?.characterId != "goon" || !goon.alive || !healthy(goon)) return null
    if (game.players.any { has(it, "goon", REMINDER.drunk) }) return null
    val chooser = game.players.find {
        it.characterId == sourceId && it.id != targetId && (it.alive || has(it, "vigormortis", REMINDER.hasAbility))
    }
    if (chooser == null || characters[sourceId]?.team !in listOf(Team.Townsfolk, Team.Outsider, Team.Minion, Team.Demon)) return null
    val next = updatePlayer(withReminder(game, chooser.id, "goon", REMINDER.drunk), targetId) { it.copy(alignment = chooser.alignment) }
    val side = if (chooser.alignment == Alignment.Good) "bueno" else "malo"
    return GameMessage(
        touch(next, "${goon.name} es el Matón: ${chooser.name} queda borracho y el Matón pasa a ser $side."),
        "${goon.name} es el Matón: ${chooser.name} queda borracho hasta el crepúsculo (su elección no hace efecto) y el Matón pasa a ser $side.",
    )
}

/**
 * Poner el recordatorio de lo que elige un personaje por la noche y aplicar lo que provoca:
 * el ataque del Demonio, el veneno del Pukka, el «Vivo» del Shabaloth… Devuelve también el
 * aviso para el Narrador. Si elige al Matón, primero se aplica el Matón.
 */
fun applyReminderChoice(game: Game, targetId: String, reminder: ReminderToken, characters: Map<String, Character>): GameMessage {
    val goon = goonChosen(game, targetId, reminder.sourceCharacterId, characters)
        ?: return applyChoice(game, targetId, reminder, characters)
    // Quien elige queda borracho: su elección no hace efecto (la información sí se da, y puede ser falsa).
    if (!isInfoChoice(reminder.sourceCharacterId)) return goon
    val result = applyChoice(goon.game, targetId, reminder, characters)
    return GameMessage(result.game, "${goon.message} ${result.message}")
}

private fun applyChoice(game: Game, targetId: String, reminder: ReminderToken, characters: Map<String, Character>): GameMessage {
    val target = findPlayer(game, targetId) ?: throw RuleViolation("Ese jugador no está en la partida.")
    val copies = reminderCopies(reminder, characters)
    val night = game.phase.isNight
    val source = reminder.sourceCharacterId

    // El Encantador de serpientes no pone recordatorio: si elige al Demonio, intercambian.
    if (night && source == "snakecharmer") return snakeCharmerChoose(game, targetId, characters)
    // La Pit-Hag: el personaje nuevo se elige después (pitHagChoose).
    if (night && source == "pithag") return GameMessage(game, "Elige el nuevo personaje de ${target.name}.")
    // Soñador, Guardián de cuervos, Tonto del pueblo: no cambia nada, solo se da la respuesta.
    if (night && isInfoChoice(source)) return GameMessage(game, infoChoiceAnswer(game, source, target, characters))

    if (night && source == "pukka" && reminder.label == REMINDER.poisoned) {
        val r = pukkaChoose(game, targetId, reminder)
        val parts = mutableListOf("${target.name} está envenenado por el Pukka.")
        if (r.died.isNotEmpty()) parts.add("${r.died.joinToString(", ") { it.name }} muere por el veneno (se anunciará al amanecer).")
        if (r.saved.isNotEmpty()) parts.add("${r.saved.joinToString(", ") { it.name }} estaba protegido: no muere y queda sano.")
        return GameMessage(r.game, parts.joinToString(" "))
    }

    if (source == "shabaloth" && reminder.label == REMINDER.alive) {
        val regurgitated = updatePlayer(revivePlayer(game, targetId), targetId) { p ->
            p.copy(reminders = p.reminders.filter { !(it.sourceCharacterId == "shabaloth" && it.label == REMINDER.dead) })
        }
        return GameMessage(placeReminder(regurgitated, targetId, reminder, copies), "El Shabaloth regurgita a ${target.name}: vuelve a la vida.")
    }

    if (night && reminder.label == REMINDER.dead && (source == "assassin" || source == "godfather")) {
        return minionKill(game, targetId, reminder, characters)
    }

    if (night && isDemonKill(reminder, characters)) {
        val (next, outcome) = demonKill(game, targetId, reminder, copies, characters)
        val demon = characters[source]?.name ?: "el Demonio"
        val message = when (outcome) {
            DemonKillOutcome.AlreadyDead -> "${target.name} ya estaba muerto: el ataque no cambia nada."
            DemonKillOutcome.Blocked -> demonBlocked(game, source) ?: "El Demonio no mata esta noche."
            DemonKillOutcome.Protected -> "${target.name} está a salvo y no muere. Si la protección no funciona (borracho, envenenado…), márcalo muerto en el Amanecer."
            DemonKillOutcome.SelfKill -> "${target.name} se ha elegido a sí mismo y muere: elige arriba quién es el nuevo Demonio."
            DemonKillOutcome.Mayor -> "${target.name} es el Alcalde: puede morir otro en su lugar. Si lo decides, deshaz y elige a ese jugador."
            DemonKillOutcome.Jump -> "$demon salta: ${target.name} es el nuevo $demon (malo) y el anterior muere. Despiértalo y enséñale TÚ ERES, $demon y el pulgar hacia abajo."
            DemonKillOutcome.Fool -> "${target.name} es el Bufón: esta vez no muere y pierde su habilidad."
            DemonKillOutcome.Banshee -> "${target.name} es la Banshee: muere y lo sabe toda la mesa. Desde ahora nomina y vota dos veces."
            DemonKillOutcome.Killed -> if (next.players.find { it.id == targetId }?.alive == true) {
                "${target.name} no muere esta vez."
            } else {
                "${target.name} muere esta noche. Se anunciará al amanecer."
            }
            DemonKillOutcome.Drunk -> "El $demon está borracho o envenenado: ${target.name} no muere."
        }
        // Vigormortis: el Esbirro que mata conserva su habilidad y envenena a 1 Aldeano vecino.
        if (outcome == DemonKillOutcome.Killed && source == "vigormortis" && characters[target.characterId]?.team == Team.Minion) {
            val neighbours = nearestTownsfolk(next.players, target, characters).map { it.name }
            return GameMessage(
                withReminder(next, targetId, "vigormortis", REMINDER.hasAbility),
                "$message Es Esbirro: conserva su habilidad. Pon «Envenenado» del Vigormortis en 1 de sus Aldeanos vecinos (${neighbours.joinToString(" o ")}).",
            )
        }
        return GameMessage(next, message)
    }

    // Profesor: una vez por partida resucita a un Aldeano muerto.
    if (night && source == "professor" && reminder.label == REMINDER.alive) {
        val professor = game.players.find { it.characterId == "professor" && it.alive }
        if (target.alive) return GameMessage(game, "${target.name} está vivo: el Profesor elige a un jugador muerto.")
        if (professor != null && has(professor, "professor", REMINDER.noAbility)) return GameMessage(game, "El Profesor ya usó su habilidad.")
        val used = if (professor != null) withReminder(game, professor.id, "professor", REMINDER.noAbility) else game
        if (professor == null || !healthy(professor)) {
            return GameMessage(used, "El Profesor no tiene habilidad (borracho o envenenado): ${target.name} sigue muerto.")
        }
        if (characters[target.characterId]?.team != Team.Townsfolk) return GameMessage(used, "${target.name} no es Aldeano: sigue muerto.")
        return GameMessage(placeReminder(revivePlayer(used, targetId), targetId, reminder, copies), "${target.name} es Aldeano y resucita.")
    }

    // Cortesano: una vez por partida, el elegido está borracho 3 días y 3 noches.
    if (night && source == "courtier" && reminder.label == REMINDER.drunk1) {
        val courtier = game.players.find { it.characterId == "courtier" && it.alive }
        if (courtier != null && has(courtier, "courtier", REMINDER.noAbility)) return GameMessage(game, "El Cortesano ya usó su habilidad.")
        val used = if (courtier != null) withReminder(game, courtier.id, "courtier", REMINDER.noAbility) else game
        if (courtier == null || !healthy(courtier)) {
            return GameMessage(used, "El Cortesano no tiene habilidad (borracho o envenenado): nadie queda borracho.")
        }
        return GameMessage(
            placeReminder(used, targetId, reminder, copies),
            "${target.name} está borracho 3 días y 3 noches: cada noche cambia su «Borracho 1» por «Borracho 2» y luego «Borracho 3».",
        )
    }

    // Niña de la Luna: el jugador que eligió al morir muere esta noche si es bueno.
    if (night && source == "moonchild" && reminder.label == REMINDER.dead) {
        val moonchild = game.players.find { it.characterId == "moonchild" && !it.alive }
        var next = placeReminder(game, targetId, reminder, copies)
        if (moonchild != null) next = withReminder(next, moonchild.id, "moonchild", REMINDER.noAbility)
        if (moonchild == null || !healthy(moonchild)) {
            return GameMessage(next, "La Niña de la Luna no tiene habilidad (borracha o envenenada): ${target.name} no muere.")
        }
        if (target.alignment != Alignment.Good) return GameMessage(next, "${target.name} es del bando malo: no muere.")
        if (!target.alive) return GameMessage(next, "${target.name} ya estaba muerto.")
        if (cannotDie(next, target)) return GameMessage(next, "${target.name} es del bando bueno, pero no puede morir esta noche.")
        return GameMessage(killPlayer(next, targetId, "muere por la Niña de la Luna"), "${target.name} es del bando bueno y muere esta noche. Se anunciará al amanecer.")
    }

    val placed = game.players.filter { p -> p.reminders.any { it.sourceCharacterId == source && it.label == reminder.label } }
    val from = if (copies > 0 && placed.size >= copies) placed[0] else null
    return GameMessage(
        placeReminder(game, targetId, reminder, copies),
        if (from != null && from.id != targetId) "«${reminder.label}» pasa de ${from.name} a ${target.name}." else "«${reminder.label}» en ${target.name}.",
    )
}

/** El heredero pasa a ser el Demonio que ha muerto (o a cuidar a Lil' Monsta). */
fun succeedDemon(game: Game, succession: DemonSuccession, heirId: String, characters: Map<String, Character>): Game {
    if (succession.heirs.none { it.id == heirId }) throw RuleViolation("Ese jugador no puede pasar a ser el Demonio.")
    if (succession.babysitter) {
        val heir = findPlayer(game, heirId) ?: throw RuleViolation("Ese jugador no está en la partida.")
        val moved = placeReminder(game, heirId, reminderFrom("lilmonsta", LILMONSTA_BABYSITTER, "$heirId:lilmonsta"), 1)
        return touch(moved, "${heir.name} pasa a cuidar a Lil' Monsta.")
    }
    val demon = characters[succession.demon.characterId] ?: throw RuleViolation("No encuentro el personaje del Demonio.")
    val next = setCharacter(game, heirId, demon)
    // De día no se le puede despertar: se le dice en su paso de esta noche.
    return if (game.phase.isDay) withReminder(next, heirId, demon.id, NEW_DEMON) else next
}

/** El Hereje (vivo o muerto, sano) invierte quién gana. */
private fun withHeretic(game: Game, hint: VictoryHint?): VictoryHint? {
    if (hint == null || game.players.none { it.characterId == "heretic" && healthy(it) }) return hint
    val winner = if (hint.winner == Alignment.Good) Alignment.Evil else Alignment.Good
    return VictoryHint(winner, "${hint.reason} Con el Hereje en juego se invierte: gana ${if (winner == Alignment.Good) "el Bien" else "el Mal"}.")
}

/** El gemelo bueno de la Gemela malvada (lleva su recordatorio «Gemelo»). */
private fun goodTwin(game: Game) = game.players.find { has(it, "eviltwin", "Gemelo") }

private fun evilTwinAlive(game: Game) = game.players.any { it.characterId == "eviltwin" && reallyAlive(it) && healthy(it) }

/**
 * Sugerencia de final según las reglas básicas. El Narrador decide: hay personajes
 * (Patoso, Goblin…) que cambian quién gana. Si hay un heredero del Demonio, la partida sigue.
 * El Zombuul que solo parece muerto sigue vivo; quien cuida a Lil' Monsta es el Demonio.
 * La Gemela malvada y la Mente maestra pueden impedir o retrasar la victoria del Bien; el
 * Hereje invierte el resultado.
 */
fun victoryHint(game: Game, characters: Map<String, Character>): VictoryHint? {
    if (game.status == GameStatus.Finished) return null
    return withHeretic(game, baseVictory(game, characters))
}

private fun baseVictory(game: Game, characters: Map<String, Character>): VictoryHint? {
    val executed = if (!game.executedToday.isNullOrEmpty()) findPlayer(game, game.executedToday) else null

    // Día extra de la Mente maestra: si se ejecuta a alguien, pierde su equipo.
    val mastermindDay = game.mastermindDay ?: 0
    if (mastermindDay != 0) {
        if (game.phase.isDay && game.phase.number == mastermindDay && executed != null) {
            return if (executed.alignment == Alignment.Good) {
                VictoryHint(Alignment.Evil, "Día de la Mente maestra: se ha ejecutado a un jugador bueno.")
            } else {
                VictoryHint(Alignment.Good, "Día de la Mente maestra: se ha ejecutado a un jugador malo.")
            }
        }
        return null
    }

    // El Santo ejecutado hoy (y sano): su equipo pierde.
    if (executed?.characterId == "saint" && !executed.alive && healthy(executed)) {
        return VictoryHint(if (executed.alignment == Alignment.Good) Alignment.Evil else Alignment.Good, "Han ejecutado al Santo.")
    }
    // Fearmonger: si nomina a su elegido («Terror») y se le ejecuta, el equipo del ejecutado pierde.
    val fearmonger = game.players.find { it.characterId == "fearmonger" && it.alive && healthy(it) }
    val byFearmonger = fearmonger != null && game.nominations.any { it.exile != true && it.nominatorId == fearmonger.id && it.nomineeId == executed?.id }
    if (executed != null && byFearmonger && has(executed, "fearmonger", "Terror")) {
        return VictoryHint(
            if (executed.alignment == Alignment.Good) Alignment.Evil else Alignment.Good,
            "El Fearmonger ha nominado y se ha ejecutado a su elegido.",
        )
    }
    // Gemela malvada: si se ejecuta a su gemelo bueno, gana el Mal.
    val twin = goodTwin(game)
    if (executed != null && twin?.id == executed.id && executed.alignment == Alignment.Good && evilTwinAlive(game)) {
        return VictoryHint(Alignment.Evil, "Han ejecutado al gemelo bueno de la Gemela malvada.")
    }

    val demons = demonPlayers(game, characters)
    if (demons.isNotEmpty() && demons.none(::reallyAlive)) {
        if (demonSuccession(game, characters) != null) return null
        // Mientras vivan las dos gemelas, los buenos no pueden ganar.
        if (twin != null && reallyAlive(twin) && evilTwinAlive(game)) return null
        return VictoryHint(Alignment.Good, "El Demonio ha muerto.")
    }
    val leviathan = game.players.any { it.characterId == "leviathan" && reallyAlive(it) }
    val goodExecuted = game.players.count { p -> p.reminders.any { it.label == LEVIATHAN_GOOD_EXECUTED } }
    if (leviathan && goodExecuted >= 2) return VictoryHint(Alignment.Evil, "Con Leviatán en juego se ha ejecutado a un segundo jugador bueno.")
    val alive = game.players.filter { reallyAlive(it) && characters[it.characterId]?.team != Team.Traveller }
    if (alive.size <= 2) return VictoryHint(Alignment.Evil, "Solo quedan dos jugadores vivos.")
    return null
}

/**
 * Por qué la partida sigue aunque haya muerto el Demonio (para avisar al Narrador): el día extra
 * de la Mente maestra o la Gemela malvada con su gemelo vivos.
 */
fun victoryNote(game: Game, characters: Map<String, Character>): String? {
    if (game.status == GameStatus.Finished) return null
    val mastermindDay = game.mastermindDay ?: 0
    if (mastermindDay != 0 && !(game.phase.isDay && game.phase.number == mastermindDay && !game.executedToday.isNullOrEmpty())) {
        return "Ha muerto el Demonio, pero la Mente maestra sigue viva: el día $mastermindDay se juega igual. Si ese día se ejecuta a un bueno, gana el Mal; si se ejecuta a un malo o no hay ejecución, gana el Bien."
    }
    val demons = demonPlayers(game, characters)
    val twin = goodTwin(game)
    if (demons.isNotEmpty() && demons.none(::reallyAlive) && demonSuccession(game, characters) == null &&
        twin != null && reallyAlive(twin) && evilTwinAlive(game)
    ) {
        return "Ha muerto el Demonio, pero los buenos no pueden ganar mientras vivan la Gemela malvada y su gemelo."
    }
    return null
}

/**
 * Victorias que se deciden al terminar el día:
 * - Día extra de la Mente maestra sin ejecución: gana el Bien.
 * - Vortox vivo y nadie ejecutado hoy: gana el Mal.
 * - Alcalde vivo y sano, 3 jugadores vivos y nadie ejecutado hoy: gana su equipo.
 * - Leviatán vivo al terminar el día 5 (o más): gana el Mal.
 */
fun duskVictory(game: Game, characters: Map<String, Character>): VictoryHint? {
    if (game.status == GameStatus.Finished || !game.phase.isDay) return null
    return withHeretic(game, baseDuskVictory(game, characters))
}

private fun baseDuskVictory(game: Game, characters: Map<String, Character>): VictoryHint? {
    val noExecution = game.executedToday.isNullOrEmpty()
    if (game.mastermindDay == game.phase.number && noExecution) {
        return VictoryHint(Alignment.Good, "Termina el día de la Mente maestra sin ejecución.")
    }
    fun alive(id: String) = game.players.any { it.characterId == id && reallyAlive(it) }
    if (alive("vortox") && noExecution) return VictoryHint(Alignment.Evil, "Con Vortox vivo, un día sin ejecución hace ganar al Mal.")
    val mayor = game.players.find { it.characterId == "mayor" && it.alive && healthy(it) }
    val aliveCount = game.players.count { reallyAlive(it) && characters[it.characterId]?.team != Team.Traveller }
    if (mayor != null && aliveCount == 3 && noExecution) {
        return VictoryHint(mayor.alignment, "Quedan 3 jugadores vivos, no ha habido ejecución y el Alcalde sigue vivo.")
    }
    if (alive("leviathan") && game.phase.number >= 5) return VictoryHint(Alignment.Evil, "Termina el día ${game.phase.number} y Leviatán sigue vivo.")
    return null
}

fun declareWinner(game: Game, winner: Alignment): Game =
    touch(game.copy(status = GameStatus.Finished, winner = winner), if (winner == Alignment.Good) "Gana el Bien." else "Gana el Mal.")

fun reopen(game: Game): Game = touch(game.copy(status = GameStatus.InProgress, winner = null), "La partida sigue.")

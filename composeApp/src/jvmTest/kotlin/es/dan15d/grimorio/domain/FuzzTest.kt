package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameStatus
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.ReminderToken
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.services.RandomFn
import es.dan15d.grimorio.domain.services.applyReminderChoice
import es.dan15d.grimorio.domain.services.applySetupEffects
import es.dan15d.grimorio.domain.services.buildNightOrder
import es.dan15d.grimorio.domain.services.callExile
import es.dan15d.grimorio.domain.services.canNominate
import es.dan15d.grimorio.domain.services.choiceReminder
import es.dan15d.grimorio.domain.services.closeNomination
import es.dan15d.grimorio.domain.services.demonPlayers
import es.dan15d.grimorio.domain.services.demonSuccession
import es.dan15d.grimorio.domain.services.duskVictory
import es.dan15d.grimorio.domain.services.execute
import es.dan15d.grimorio.domain.services.expiringReminders
import es.dan15d.grimorio.domain.services.infoHints
import es.dan15d.grimorio.domain.services.isDealt
import es.dan15d.grimorio.domain.services.keepNightStep
import es.dan15d.grimorio.domain.services.nominate
import es.dan15d.grimorio.domain.services.onTheBlock
import es.dan15d.grimorio.domain.services.phaseLabel
import es.dan15d.grimorio.domain.services.pick
import es.dan15d.grimorio.domain.services.pickBluffs
import es.dan15d.grimorio.domain.services.pickDecoys
import es.dan15d.grimorio.domain.services.publicPlayers
import es.dan15d.grimorio.domain.services.randomSelection
import es.dan15d.grimorio.domain.services.reallyAlive
import es.dan15d.grimorio.domain.services.removeReminders
import es.dan15d.grimorio.domain.services.seatMarionette
import es.dan15d.grimorio.domain.services.seatSecret
import es.dan15d.grimorio.domain.services.seededRandom
import es.dan15d.grimorio.domain.services.setNightStep
import es.dan15d.grimorio.domain.services.shuffle
import es.dan15d.grimorio.domain.services.startDay
import es.dan15d.grimorio.domain.services.startNight
import es.dan15d.grimorio.domain.services.succeedDemon
import es.dan15d.grimorio.domain.services.toggleVote
import es.dan15d.grimorio.domain.services.victoryHint
import es.dan15d.grimorio.domain.services.victoryNote
import es.dan15d.grimorio.domain.services.voteCount
import kotlin.test.Test
import kotlin.test.assertEquals

// Partidas al azar de principio a fin: el Narrador sigue la hoja de noche, los jugadores nominan
// y votan al azar. En cada paso se comprueba que las reglas no se contradicen ni se rompen.

private val map = TestCatalog.map

// Un guion con todo lo que se puede preparar (sin los Demonios de preparación pendiente).
private val UNSUPPORTED = setOf("legion", "riot", "kazali", "lordoftyphon", "summoner", "atheist")
private val BAG_TEAMS = setOf(Team.Townsfolk, Team.Outsider, Team.Minion, Team.Demon)
private val everything = TestCatalog.all.filter { it.team in BAG_TEAMS && it.id !in UNSUPPORTED }
private val travellers = TestCatalog.all.filter { it.team == Team.Traveller }
private val FABLED = listOf("toymaker", "tor", "sentinel", "angel", "djinn")

private fun scriptCharacters(id: String) = TestCatalog.scriptPool(id).filter { it.team != Team.Traveller }

private val NOT_A_NUMBER = Regex("undefined|NaN|null")

private fun setUp(pool: List<Character>, playerCount: Int, random: RandomFn): Game? {
    val bag = try {
        randomSelection(playerCount, pool, random)
    } catch (e: Exception) {
        return null
    }
    val decoys = pickDecoys(bag, pool, random)
    val dealt = bag.filter(::isDealt)
    // En la web se baraja con sort(() => random() - 0.5); aquí, con shuffle (no hace falta la misma partida).
    val order = shuffle(dealt, random)
    var players: List<Player> = order.mapIndexed { seat, c ->
        Player(
            id = "p$seat",
            seat = seat,
            name = "J${seat + 1}",
            characterId = c.id,
            shownCharacterId = decoys[c.id],
            alignment = if (c.team == Team.Minion || c.team == Team.Demon) Alignment.Evil else Alignment.Good,
        )
    }
    // A veces, un Viajero.
    if (random() < 0.3) {
        val t = pick(travellers, random)!!
        val n = players.size
        players = players + Player(
            id = "p$n",
            seat = n,
            name = "J${n + 1}",
            characterId = t.id,
            alignment = if (random() < 0.5) Alignment.Good else Alignment.Evil,
        )
    }
    players = seatMarionette(players, map, random)
    var n = 0
    players = applySetupEffects(players, map, random) { "s${n++}" }.players
    val fabledIds = if (random() < 0.3) listOf(pick(FABLED, random)!!) else emptyList()
    val extra = bag.filter { !isDealt(it) }.map { it.id }
    val bluffs = pickBluffs(bag.map { it.id } + decoys.values, pool, random)
    return newGame(players, Phase.night(1), scriptId = "", scriptName = "").copy(
        name = "Fuzz",
        fabledIds = fabledIds,
        extraCharacterIds = extra,
        demonBluffs = bluffs,
        joinCode = "FUZZ",
    )
}

/** Lo que nunca debería pasar, con el estado en el que pasó. */
private fun invariants(g: Game, where: String): List<String> {
    val problems = mutableListOf<String>()
    val steps = buildNightOrder(g, map)
    val keys = steps.map { it.key }
    if (keys.toSet().size != keys.size) problems.add("$where: pasos de noche repetidos ${keys.joinToString(",")}")
    for (p in g.players) {
        if (map[p.characterId] == null) problems.add("$where: personaje desconocido ${p.characterId}")
    }
    val deaths = g.nightDeaths + g.dayDeaths
    if (g.nightDeaths.toSet().size != g.nightDeaths.size) problems.add("$where: muerte de noche repetida")
    for (id in deaths) if (g.players.none { it.id == id }) problems.add("$where: muerte de un jugador que no existe")
    if (g.phase.isDay && g.nightDeaths.isNotEmpty()) problems.add("$where: de día quedan muertes de noche sin anunciar")
    // La mesa nunca ve muerto a alguien vivo (salvo quien ha resucitado esta noche, hasta el amanecer).
    val pub = publicPlayers(g, map)
    if (g.phase.isDay && g.nightRevivals.isNotEmpty()) problems.add("$where: de día quedan resurrecciones sin anunciar")
    g.players.forEachIndexed { i, p ->
        if (p.alive && !pub[i].alive && p.id !in g.nightRevivals) problems.add("$where: la mesa ve muerto a ${p.name}, que está vivo")
        seatSecret(p, map, g.fabledIds, pub[i].alive)
    }
    // Las ayudas de la noche no fallan ni dicen «null».
    for (s in steps) {
        val character = s.character ?: continue
        for (h in infoHints(g, character, map)) {
            if (NOT_A_NUMBER.containsMatchIn(h.text)) problems.add("$where: ayuda rara para ${character.id}: ${h.text}")
        }
    }
    // No puede sugerir a la vez una victoria y un nuevo Demonio.
    if (victoryHint(g, map) != null && demonSuccession(g, map) != null) problems.add("$where: victoria y heredero a la vez")
    // Si no queda ningún Demonio vivo ni heredero, o hay que sugerir una victoria o explicar por qué sigue.
    val demons = demonPlayers(g, map)
    if (demons.isNotEmpty() && demons.none(::reallyAlive) && demonSuccession(g, map) == null &&
        victoryHint(g, map) == null && victoryNote(g, map) == null
    ) {
        problems.add("$where: el Demonio ha muerto y la app no dice nada")
    }
    for (n in g.nominations) voteCount(g, n)
    return problems
}

/** Ejecuta una acción; una RuleViolation es un «no se puede» legítimo, cualquier otro error es un fallo. */
private fun attempt(g: Game, where: String, problems: MutableList<String>, action: (Game) -> Game): Game =
    try {
        action(g)
    } catch (e: RuleViolation) {
        g
    } catch (e: Exception) {
        problems.add("$where: $e | ${e.stackTrace.take(3).joinToString(" | ")}")
        g
    }

private fun play(pool: List<Character>, playerCount: Int, seed: Int): List<String> {
    val random = seededRandom(seed)
    var g = setUp(pool, playerCount, random) ?: return emptyList()
    val problems = mutableListOf<String>()
    fun label() = "[$playerCount jug., semilla $seed, ${phaseLabel(g.phase)}]"
    var ids = 0

    var round = 0
    while (round < 10 && g.status == GameStatus.InProgress) {
        round++
        // ---- Noche: se recorre la hoja; quien elige, elige al azar.
        val night = g.phase.number
        for (i in 0 until 40) {
            val steps = buildNightOrder(g, map)
            if (g.nightStep >= steps.size - 1) break
            val step = steps[g.nightStep]
            val character = step.character
            val label2 = choiceReminder(character?.id ?: "", night == 1)
            if (character != null && label2 != null && step.playerIds.isNotEmpty() && !step.dead) {
                val target = pick(g.players, random)!!
                val before = g
                g = attempt(g, "${label()} ${character.id}", problems) { x ->
                    val result = applyReminderChoice(x, target.id, ReminderToken("c${ids++}", character.id, label2), map)
                    if (NOT_A_NUMBER.containsMatchIn(result.message)) problems.add("${label()} aviso raro: ${result.message}")
                    result.game
                }
                g = keepNightStep(before, g, map)
                // Si aparece un heredero del Demonio, se acepta el primero.
                val s = demonSuccession(g, map)
                if (s != null) g = attempt(g, "${label()} heredero", problems) { x -> succeedDemon(x, s, s.heirs[0].id, map) }
            }
            problems.addAll(invariants(g, "${label()} paso ${step.key}"))
            g = setNightStep(g, g.nightStep + 1)
        }
        if (victoryHint(g, map) != null) break
        g = attempt(g, "${label()} amanecer", problems, ::startDay)
        problems.addAll(invariants(g, "${label()} amanecer"))

        // ---- Día: algún exilio, unas nominaciones al azar y, si hay picota, ejecución.
        val traveller = g.players.find { it.alive && map[it.characterId]?.team == Team.Traveller }
        if (traveller != null && random() < 0.3) {
            val id = "e${ids++}"
            g = attempt(g, "${label()} exilio", problems) { x -> callExile(x, traveller.id, id, map) }
            for (p in g.players) if (random() < 0.6) g = attempt(g, "${label()} voto exilio", problems) { x -> toggleVote(x, id, p.id) }
            g = attempt(g, "${label()} cierre exilio", problems) { x -> closeNomination(x, id) }
        }
        for (k in 0 until 3) {
            val nominator = pick(g.players, random)!!
            val nominee = pick(g.players, random)!!
            if (canNominate(g, nominator.id, nominee.id) != null) continue
            val id = "n${ids++}"
            g = attempt(g, "${label()} nominación", problems) { x -> nominate(x, nominator.id, nominee.id, id, map) }
            if (g.nominations.none { it.id == id }) continue
            for (p in g.players) if (random() < 0.55) g = attempt(g, "${label()} voto", problems) { x -> toggleVote(x, id, p.id) }
            g = attempt(g, "${label()} cierre", problems) { x -> closeNomination(x, id) }
            problems.addAll(invariants(g, "${label()} tras votar"))
        }
        val block = onTheBlock(g)
        if (block != null) g = attempt(g, "${label()} ejecución", problems) { x -> execute(x, block.playerId, map) }
        problems.addAll(invariants(g, "${label()} tras ejecutar"))
        val s = demonSuccession(g, map)
        if (s != null) g = attempt(g, "${label()} heredero de día", problems) { x -> succeedDemon(x, s, s.heirs[0].id, map) }
        if (victoryHint(g, map) != null || duskVictory(g, map) != null) break
        val expiring = expiringReminders(g).map { it.reminder.id }
        g = attempt(g, "${label()} anochecer", problems) { x -> startNight(removeReminders(x, expiring)) }
        problems.addAll(invariants(g, "${label()} anochecer"))
    }
    // Una partida sin vivos o con todos muertos sería un fallo de las reglas de victoria.
    if (g.players.none(::reallyAlive)) problems.add("${label()}: han muerto todos sin que nadie gane")
    return problems
}

private fun fuzz(pool: List<Character>) {
    val problems = mutableListOf<String>()
    for (players in 5..15) {
        for (seed in 1..25) problems.addAll(play(pool, players, seed * 101 + players))
    }
    val unique = problems.map { it.replaceFirst(Regex("\\[[^\\]]*\\]"), "").trim() }.distinct()
    assertEquals(emptyList(), unique.take(15))
}

class FuzzTest {
    @Test
    fun troubleBrewingDe5A15JugadoresSinErrores() = fuzz(scriptCharacters("trouble-brewing"))

    @Test
    fun badMoonRisingDe5A15JugadoresSinErrores() = fuzz(scriptCharacters("bad-moon-rising"))

    @Test
    fun sectsAndVioletsDe5A15JugadoresSinErrores() = fuzz(scriptCharacters("sects-and-violets"))

    @Test
    fun todosDe5A15JugadoresSinErrores() = fuzz(everything)
}

package es.dan15d.grimorio.domain.services

import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.ReminderToken
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.entities.isGoodTeam

private fun byTeam(characters: List<Character>, team: Team) = characters.filter { it.team == team }

/**
 * Elige al azar una bolsa válida de personajes del guion. Se usa en el modo manual
 * («Al azar») y como plan B si la IA no responde.
 */
fun randomSelection(playerCount: Int, scriptCharacters: List<Character>, random: RandomFn): List<Character> {
    val bagSize = minOf(playerCount, MAX_BAG_PLAYERS)
    repeat(200) {
        val base = baseDistribution(playerCount)
        val demons = shuffle(byTeam(scriptCharacters, Team.Demon), random).take(base.demon)
        var minions = shuffle(byTeam(scriptCharacters, Team.Minion), random).take(base.minion)

        // La distribución final depende del Demonio y los Esbirros (Barón, Fang Gu…).
        val target = pick(allowedDistributions(playerCount, demons + minions), random)!!
        if (target.minion > minions.size) {
            val extra = byTeam(scriptCharacters, Team.Minion).filter { m -> minions.none { it === m } }
            minions = minions + shuffle(extra, random).take(target.minion - minions.size)
        }

        val outsiders = shuffle(byTeam(scriptCharacters, Team.Outsider), random).take(target.outsider)
        val townsfolk = shuffle(byTeam(scriptCharacters, Team.Townsfolk), random).take(target.townsfolk)
        var bag = demons + minions + outsiders + townsfolk

        // Personajes que obligan a meter otro (Cazador → Damisela, Niño del coro → Rey).
        for (c in bag.toList()) {
            for (req in SETUP_MODIFIERS[c.id]?.requires ?: emptyList()) {
                if (bag.any { it.id == req }) continue
                val needed = scriptCharacters.find { it.id == req }
                val victim = if (needed != null) bag.lastOrNull { it.team == needed.team && it.id != c.id } else null
                if (needed != null && victim != null) bag = bag.map { if (it === victim) needed else it }
            }
        }

        if (bag.filter(::isDealt).size == bagSize && validateSelection(playerCount, bag).none { it.level == SetupIssueLevel.Error }) {
            return bag
        }
    }
    throw IllegalStateException("No he encontrado un reparto válido con este guion y este número de jugadores.")
}

/**
 * Para el Borracho, el Lunático… elige qué ficha creerán tener (un personaje que no está en juego).
 * Devuelve id real → id mostrado.
 */
fun pickDecoys(
    inPlay: List<Character>,
    scriptCharacters: List<Character>,
    random: RandomFn,
    preferred: Map<String, String> = emptyMap(),
): Map<String, String> {
    val used = inPlay.map { it.id }.toMutableSet()
    val result = mutableMapOf<String, String>()
    for (c in inPlay) {
        val team = DECOYS[c.id] ?: continue
        val wanted = preferred[c.id]
        // La Marioneta no puede creer ser el Rey si está el Niño del coro (wiki: interacción Marioneta-Rey).
        val forbidden = if (c.id == "marionette" && "choirboy" in used) "king" else null
        val candidates = scriptCharacters.filter { it.team == team && it.id !in used && it.id != forbidden }
        val chosen = candidates.find { it.id == wanted } ?: pick(candidates, random)
        if (chosen != null) {
            result[c.id] = chosen.id
            used.add(chosen.id)
        }
    }
    return result
}

/** Tres personajes buenos que no están en juego para que el Demonio farolee (2 Aldeanos y 1 Forastero si se puede). */
fun pickBluffs(excludedIds: Iterable<String>, scriptCharacters: List<Character>, random: RandomFn): List<String> {
    val excluded = excludedIds.toSet()
    fun free(team: Team) = shuffle(scriptCharacters.filter { it.team == team && it.id !in excluded }, random)
    val townsfolk = free(Team.Townsfolk)
    val outsiders = free(Team.Outsider)
    val bluffs = (townsfolk.take(2) + outsiders.take(1)).toMutableList()
    for (c in townsfolk.drop(2)) if (bluffs.size < 3) bluffs.add(c)
    return bluffs.map { it.id }
}

/**
 * La Marioneta empieza junto al Demonio. Si al sacar de la bolsa ha caído lejos, cambia la
 * ficha con un vecino del Demonio (nunca con un Viajero).
 */
fun seatMarionette(players: List<Player>, characters: Map<String, Character>, random: RandomFn): List<Player> {
    fun team(p: Player) = characters[p.characterId]?.team
    val marionette = players.find { it.characterId == "marionette" } ?: return players
    val demon = players.find { team(it) == Team.Demon } ?: return players
    val n = players.size
    val neighbours = players.filter { it.seat == (demon.seat + 1) % n || it.seat == (demon.seat - 1 + n) % n }
    if (neighbours.any { it === marionette }) return players
    val other = pick(neighbours.filter { team(it) != Team.Traveller && it !== demon }, random) ?: return players
    fun swap(p: Player, from: Player) = p.copy(
        characterId = from.characterId,
        shownCharacterId = from.shownCharacterId,
        alignment = from.alignment,
    )
    return players.map { if (it === marionette) swap(it, other) else if (it === other) swap(it, marionette) else it }
}

/** Lo que devuelve applySetupEffects: los jugadores y qué se ha hecho. */
data class SetupEffects(val players: List<Player>, val log: List<String>)

private data class SetupMark(val source: String, val label: String, val text: String, val self: Boolean)

/**
 * Lo que la preparación hace después de repartir: el Cazarrecompensas vuelve malo a un Aldeano
 * y, si hay varios Tontos del pueblo, uno está borracho. Devuelve los jugadores y qué se ha hecho.
 */
fun applySetupEffects(
    players: List<Player>,
    characters: Map<String, Character>,
    random: RandomFn,
    newId: () -> String,
): SetupEffects {
    val log = mutableListOf<String>()
    var result = players
    if (players.any { it.characterId == "bountyhunter" }) {
        val target = pick(
            players.filter { characters[it.characterId]?.team == Team.Townsfolk && it.characterId != "bountyhunter" },
            random,
        )
        if (target != null) {
            result = result.map { if (it.id == target.id) it.copy(alignment = Alignment.Evil) else it }
            log.add("${target.name} es el Aldeano malo del Cazarrecompensas: díselo cuando le enseñes su personaje.")
        }
    }
    val idiots = result.filter { it.characterId == "villageidiot" }
    if (idiots.size > 1) {
        val drunk = pick(idiots, random)!!
        result = result.map {
            if (it.id == drunk.id) it.copy(reminders = it.reminders + ReminderToken(newId(), "villageidiot", REMINDER.drunk)) else it
        }
        log.add("${drunk.name} es el Tonto del pueblo borracho.")
    }
    // No Dashii: los Aldeanos más cercanos a cada lado están envenenados desde el principio.
    for (demon in result.filter { it.characterId == "nodashii" }) {
        val poisoned = nearestTownsfolk(result, demon, characters)
        val ids = poisoned.map { it.id }.toSet()
        result = result.map {
            if (it.id in ids) it.copy(reminders = it.reminders + ReminderToken(newId(), "nodashii", REMINDER.poisoned)) else it
        }
        if (poisoned.isNotEmpty()) log.add("${poisoned.joinToString(" y ") { it.name }}: envenenados por el No Dashii (Aldeanos vecinos).")
    }
    // Gemela malvada, Abuela y Pitonisa: un jugador bueno al azar con su recordatorio (se puede
    // cambiar a mano). El falso positivo de la Pitonisa puede ser ella misma.
    val marks = listOf(
        SetupMark("eviltwin", "Gemelo", "es el gemelo bueno de la Gemela malvada", false),
        SetupMark("grandmother", "Nieto", "es el nieto de la Abuela", false),
        SetupMark("fortuneteller", "Falso positivo", "es el falso positivo de la Pitonisa", true),
    )
    for ((source, label, text, self) in marks) {
        if (result.none { it.characterId == source }) continue
        val good = result.filter {
            it.alignment == Alignment.Good && (self || it.characterId != source) &&
                isGoodTeam(characters[it.characterId]?.team ?: Team.Traveller)
        }
        val chosen = pick(good, random) ?: continue
        result = result.map {
            if (it.id == chosen.id) it.copy(reminders = it.reminders + ReminderToken(newId(), source, label)) else it
        }
        log.add("${chosen.name} $text.")
    }
    return SetupEffects(result, log)
}

/** El Aldeano más cercano a cada lado de un jugador (saltando a los que no son Aldeanos). */
fun nearestTownsfolk(players: List<Player>, from: Player, characters: Map<String, Character>): List<Player> {
    val seated = players.sortedBy { it.seat }
    val n = seated.size
    val at = seated.indexOfFirst { it.id == from.id }
    fun walk(dir: Int): Player? {
        for (k in 1 until n) {
            val p = seated[((at + dir * k) % n + n) % n]
            if (characters[p.characterId]?.team == Team.Townsfolk) return p
        }
        return null
    }
    return listOfNotNull(walk(-1), walk(1)).distinct()
}

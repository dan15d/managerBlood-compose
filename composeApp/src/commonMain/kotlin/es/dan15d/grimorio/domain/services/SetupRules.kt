package es.dan15d.grimorio.domain.services

import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Edition
import es.dan15d.grimorio.domain.entities.TEAMS_IN_BAG
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.entities.isGoodTeam
import es.dan15d.grimorio.domain.entities.label

data class Distribution(val townsfolk: Int, val outsider: Int, val minion: Int, val demon: Int) {
    /** Cuántos de ese equipo (0 para los que no van en la bolsa). */
    operator fun get(team: Team): Int = when (team) {
        Team.Townsfolk -> townsfolk
        Team.Outsider -> outsider
        Team.Minion -> minion
        Team.Demon -> demon
        else -> 0
    }

    /** La misma distribución con otro número para ese equipo (los que no van en la bolsa no cambian nada). */
    fun copy(team: Team, count: Int): Distribution = when (team) {
        Team.Townsfolk -> copy(townsfolk = count)
        Team.Outsider -> copy(outsider = count)
        Team.Minion -> copy(minion = count)
        Team.Demon -> copy(demon = count)
        else -> this
    }
}

const val MIN_PLAYERS = 5
/** A partir de 16 jugadores, los que sobran juegan como Viajeros. */
const val MAX_BAG_PLAYERS = 15
const val MAX_PLAYERS = 20

// Hoja de preparación del reglamento.
private val BASE: Map<Int, Distribution> = mapOf(
    5 to Distribution(townsfolk = 3, outsider = 0, minion = 1, demon = 1),
    6 to Distribution(townsfolk = 3, outsider = 1, minion = 1, demon = 1),
    7 to Distribution(townsfolk = 5, outsider = 0, minion = 1, demon = 1),
    8 to Distribution(townsfolk = 5, outsider = 1, minion = 1, demon = 1),
    9 to Distribution(townsfolk = 5, outsider = 2, minion = 1, demon = 1),
    10 to Distribution(townsfolk = 7, outsider = 0, minion = 2, demon = 1),
    11 to Distribution(townsfolk = 7, outsider = 1, minion = 2, demon = 1),
    12 to Distribution(townsfolk = 7, outsider = 2, minion = 2, demon = 1),
    13 to Distribution(townsfolk = 9, outsider = 0, minion = 3, demon = 1),
    14 to Distribution(townsfolk = 9, outsider = 1, minion = 3, demon = 1),
    15 to Distribution(townsfolk = 9, outsider = 2, minion = 3, demon = 1),
)

fun baseDistribution(playerCount: Int): Distribution {
    val bag = minOf(maxOf(playerCount, MIN_PLAYERS), MAX_BAG_PLAYERS)
    return BASE.getValue(bag)
}

fun travellerCount(playerCount: Int): Int = maxOf(0, playerCount - MAX_BAG_PLAYERS)

/** Los Viajeros son opcionales, pero en la bolsa siempre tiene que haber al menos 5 jugadores. */
fun maxTravellers(playerCount: Int): Int = maxOf(0, playerCount - MIN_PLAYERS)

/**
 * Asiento de cada Viajero. Se respeta el que haya elegido el Narrador (si es válido y no está
 * repetido); el resto ocupa los últimos asientos libres. Devuelve asiento → personaje, ordenado
 * por asiento (como las claves numéricas de un objeto en la web).
 */
fun travellerSeats(playerCount: Int, travellerIds: List<String>, chosen: Map<String, Int> = emptyMap()): Map<Int, String> {
    val taken = mutableSetOf<Int>()
    val result = mutableMapOf<Int, String>()
    for (id in travellerIds) {
        val seat = chosen[id]
        if (seat != null && seat >= 0 && seat < playerCount && seat !in taken) {
            taken.add(seat)
            result[seat] = id
        }
    }
    var free = playerCount - 1
    for (id in travellerIds) {
        if (id in result.values) continue
        while (free in taken) free--
        if (free < 0) break
        taken.add(free)
        result[free] = id
    }
    return result.entries.sortedBy { it.key }.associate { it.key to it.value }
}

/**
 * Personajes con corchetes en su ficha. Cada opción de `outsiders` es un ajuste válido
 * (el Padrino puede ser +1 o −1; el Globo, +0 o +1). `requires` añade personajes a la bolsa.
 */
data class SetupModifier(
    val outsiders: List<Int>? = null,
    val minions: List<Int>? = null,
    val requires: List<String>? = null,
    /** Sin Demonio en la bolsa: su sitio lo ocupa un Aldeano (Invocador). */
    val noDemon: Boolean = false,
    /** Sin Esbirros ni Demonio: todos sus sitios los ocupan Aldeanos (Ateo). */
    val noEvil: Boolean = false,
    /** Cuántas fichas iguales puede haber en la bolsa (Tonto del pueblo). */
    val copies: Int? = null,
    /** El hueco del Demonio lo ocupa un Esbirro más (Lil' Monsta). */
    val demonToMinion: Boolean = false,
    /** Está en juego pero su ficha no va a la bolsa: no lo tiene ningún jugador (Lil' Monsta). */
    val outOfBag: Boolean = false,
    val note: String,
)

val SETUP_MODIFIERS: Map<String, SetupModifier> = mapOf(
    "baron" to SetupModifier(outsiders = listOf(2), note = "+2 Forasteros"),
    "godfather" to SetupModifier(outsiders = listOf(-1, 1), note = "−1 o +1 Forastero"),
    "fanggu" to SetupModifier(outsiders = listOf(1), note = "+1 Forastero"),
    "vigormortis" to SetupModifier(outsiders = listOf(-1), note = "−1 Forastero"),
    "balloonist" to SetupModifier(outsiders = listOf(0, 1), note = "+0 o +1 Forastero"),
    "hermit" to SetupModifier(outsiders = listOf(0, -1), note = "−0 o −1 Forastero"),
    "sentinel" to SetupModifier(outsiders = listOf(-1, 0, 1), note = "−1, +0 o +1 Forastero"),
    "xaan" to SetupModifier(outsiders = listOf(1, 2, 3), note = "Forasteros = X"),
    "lilmonsta" to SetupModifier(demonToMinion = true, outOfBag = true, note = "No va en la bolsa: +1 Esbirro"),
    "lordoftyphon" to SetupModifier(minions = listOf(1), note = "+1 Esbirro"),
    "huntsman" to SetupModifier(requires = listOf("damsel"), note = "+la Damisela"),
    "choirboy" to SetupModifier(requires = listOf("king"), note = "+el Rey"),
    "atheist" to SetupModifier(noEvil = true, note = "No hay malos"),
    "summoner" to SetupModifier(noDemon = true, note = "No hay Demonio"),
    "villageidiot" to SetupModifier(copies = 3, note = "+0 a +2 Tontos del pueblo"),
    "bountyhunter" to SetupModifier(note = "1 Aldeano es malo"),
    "marionette" to SetupModifier(note = "Junto al Demonio"),
)

/** Personajes cuya ficha va a la bolsa y se reparte a un jugador. */
fun isDealt(c: Character): Boolean = c.team in TEAMS_IN_BAG && SETUP_MODIFIERS[c.id]?.outOfBag != true

/** Incompatibilidades que prohíben que dos personajes empiecen a la vez en juego. */
val INCOMPATIBLE: List<Pair<String, String>> = listOf(
    "heretic" to "baron",
    "heretic" to "godfather",
    "heretic" to "pithag",
    "heretic" to "spy",
    "heretic" to "widow",
    "heretic" to "lleech",
    "legion" to "engineer",
)

/** Personajes que no saben quiénes son: se les enseña otra ficha. */
val DECOYS: Map<String, Team> = mapOf(
    "drunk" to Team.Townsfolk,
    "lunatic" to Team.Demon,
    "marionette" to Team.Townsfolk,
)

enum class SetupIssueLevel(val key: String) {
    Error("error"),
    Warning("warning"),
}

data class SetupIssue(val level: SetupIssueLevel, val message: String)

fun countByTeam(characters: List<Character>): Distribution {
    var d = Distribution(townsfolk = 0, outsider = 0, minion = 0, demon = 0)
    for (c in characters) if (c.team in TEAMS_IN_BAG) d = d.copy(c.team, d[c.team] + 1)
    return d
}

/**
 * Todas las distribuciones válidas para los personajes elegidos, combinando los ajustes
 * de cada personaje con corchetes. Los Aldeanos rellenan lo que falte.
 */
fun allowedDistributions(playerCount: Int, characters: List<Character>): List<Distribution> {
    val base = baseDistribution(playerCount)
    var options: List<Distribution> = listOf(base)
    val mods = characters.map { it.id }.distinct().mapNotNull { SETUP_MODIFIERS[it] }
    for (mod in mods) {
        if (mod.demonToMinion) {
            options = options.map { it.copy(minion = it.minion + it.demon, demon = 0) }
            continue
        }
        if (mod.noEvil || mod.noDemon) {
            options = options.map { d ->
                val removed = if (mod.noEvil) d.minion + d.demon else d.demon
                d.copy(townsfolk = d.townsfolk + removed, minion = if (mod.noEvil) 0 else d.minion, demon = 0)
            }
            continue
        }
        val next = mutableListOf<Distribution>()
        for (d in options) {
            for (o in mod.outsiders ?: listOf(0)) {
                for (m in mod.minions ?: listOf(0)) {
                    val outsider = maxOf(0, d.outsider + o)
                    val minion = d.minion + m
                    val townsfolk = d.townsfolk - (outsider - d.outsider) - (minion - d.minion)
                    if (townsfolk >= 0) next.add(d.copy(outsider = outsider, minion = minion, townsfolk = townsfolk))
                }
            }
        }
        options = next
    }
    // Sin repetidas, en el orden en que aparecen (como un Map de la web: la última pisa a la primera,
    // pero son iguales).
    return options.distinct()
}

fun sameDistribution(a: Distribution, b: Distribution): Boolean =
    a.townsfolk == b.townsfolk && a.outsider == b.outsider && a.minion == b.minion && a.demon == b.demon

/**
 * Comprueba una selección de personajes para la bolsa y explica qué falla. Puede incluir las
 * Fábulas y Lóricos en juego: no van a la bolsa, pero algunos cambian la preparación
 * (el Centinela, un Forastero más o menos; el Papa, buenos repetidos).
 */
fun validateSelection(playerCount: Int, characters: List<Character>): List<SetupIssue> {
    val issues = mutableListOf<SetupIssue>()
    val bagSize = minOf(playerCount, MAX_BAG_PLAYERS)
    val inBag = characters.filter(::isDealt)
    val counts = countByTeam(inBag)
    val ids = inBag.map { it.id }.toSet()

    if (inBag.size != bagSize) {
        issues.add(SetupIssue(SetupIssueLevel.Error, "Hay ${inBag.size} personajes en la bolsa y deben ser $bagSize."))
    }
    val withoutDemon = characters.any {
        val mod = SETUP_MODIFIERS[it.id]
        mod != null && (mod.noDemon || mod.noEvil || mod.demonToMinion)
    }
    if (counts.demon < 1 && !withoutDemon) issues.add(SetupIssue(SetupIssueLevel.Error, "Falta el Demonio."))

    val options = allowedDistributions(playerCount, characters)
    if (inBag.size == bagSize && options.none { sameDistribution(it, counts) }) {
        val expected = options.joinToString(" o ") { d ->
            "${d.townsfolk} ${Team.Townsfolk.label.plural}, ${d.outsider} ${Team.Outsider.label.plural}, " +
                "${d.minion} ${Team.Minion.label.plural}, ${d.demon} ${Team.Demon.label.singular}"
        }
        issues.add(SetupIssue(SetupIssueLevel.Error, "El reparto no cuadra. Se espera: $expected."))
    }

    for (c in inBag.distinct()) {
        val mod = SETUP_MODIFIERS[c.id]
        if (mod?.requires?.any { it !in ids } == true) {
            issues.add(SetupIssue(SetupIssueLevel.Error, "${c.name} necesita ${mod.note.replaceFirst("+", "")} en la bolsa."))
        }
    }
    // Con el Papa puede haber buenos repetidos; el Tonto del pueblo trae sus copias. El Mal nunca.
    val pope = characters.any { it.id == POPE }
    for (id in ids) {
        val copies = inBag.filter { it.id == id }
        val allowed = SETUP_MODIFIERS[id]?.copies ?: if (pope && isGoodTeam(copies[0].team)) bagSize else 1
        if (copies.size > allowed) {
            issues.add(
                SetupIssue(
                    SetupIssueLevel.Error,
                    if (allowed > 1) "Puede haber hasta $allowed ${copies[0].name}."
                    else "${copies[0].name} está repetido.${if (pope) " Solo los buenos pueden repetirse." else ""}",
                ),
            )
        }
    }
    for ((a, b) in INCOMPATIBLE) {
        val first = inBag.find { it.id == a }
        val second = inBag.find { it.id == b }
        if (first != null && second != null) {
            issues.add(SetupIssue(SetupIssueLevel.Error, "${first.name} y ${second.name} no pueden estar los dos en juego."))
        }
    }
    if (playerCount < 7 && inBag.any { it.edition == Edition.Bmr || it.edition == Edition.Snv }) {
        issues.add(
            SetupIssue(
                SetupIssueLevel.Warning,
                "Con menos de 7 jugadores, Bad Moon Rising y Sects & Violets pueden quedar desequilibrados.",
            ),
        )
    }
    return issues
}

/** Lo que se espera para cada equipo con lo que ya se ha elegido (para los contadores de la UI). */
fun targetDistribution(playerCount: Int, characters: List<Character>): Distribution {
    val options = allowedDistributions(playerCount, characters)
    val counts = countByTeam(characters.filter(::isDealt))
    // Sin ninguna válida (no debería pasar), la de la hoja de preparación.
    return options.find { sameDistribution(it, counts) } ?: options.firstOrNull() ?: baseDistribution(playerCount)
}

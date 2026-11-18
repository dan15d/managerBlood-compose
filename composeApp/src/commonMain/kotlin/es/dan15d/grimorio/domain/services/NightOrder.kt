package es.dan15d.grimorio.domain.services

import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.Team

enum class NightStepKind(val key: String) {
    Dusk("dusk"),
    MinionInfo("minion_info"),
    DemonInfo("demon_info"),
    Character("character"),
    Dawn("dawn"),
}

/** Para los que actúan al morir: si toca despertarlo esta noche o no, y por qué. */
data class DeathNote(val wake: Boolean, val text: String)

data class NightStep(
    val key: String,
    val kind: NightStepKind,
    val title: String,
    val instruction: String,
    /** Jugadores a los que afecta el paso (a quién despertar). */
    val playerIds: List<String>,
    val character: Character? = null,
    /** Jugadores que creen ser este personaje pero no lo son (Borracho, Lunático…): dales información falsa. */
    val deceivedPlayerIds: List<String> = emptyList(),
    /** El jugador está muerto: normalmente se salta, salvo habilidades que actúan al morir. */
    val dead: Boolean = false,
    /** Para los que actúan al morir: si toca despertarlo esta noche o no, y por qué. */
    val deathNote: DeathNote? = null,
)

/** Actúan si han muerto esta noche (Guardián de cuervos, Sabio, Granjero, Banshee). */
private val ACT_IF_DIED_TONIGHT = setOf("ravenkeeper", "sage", "farmer", "banshee")
/** Actúan una vez si han muerto hoy o esta noche (Adorable, Barbero, Doctor de plaga). */
private val ACT_IF_DIED_RECENTLY = setOf("sweetheart", "barber", "plaguedoctor")

private fun deathNote(game: Game, characterId: String, players: List<Player>): DeathNote? {
    val tonight = players.any { it.id in game.nightDeaths }
    if (characterId in ACT_IF_DIED_TONIGHT) {
        return if (tonight) DeathNote(true, "Ha muerto esta noche: despiértalo para que use su habilidad.")
        else DeathNote(false, "Solo actúa si muere esta noche. Ahora no le despiertes.")
    }
    // La Niña de la Luna elige al morir (de día, en público) y esa noche muere su elegido si es bueno.
    if (characterId == "moonchild") {
        val dead = players.filter { !it.alive }
        if (dead.isEmpty()) return DeathNote(false, "Solo actúa cuando muere. Ahora no hay nada que hacer.")
        return if (dead.any { !has(it, "moonchild", REMINDER.noAbility) }) {
            DeathNote(true, "Ha muerto: si ya ha elegido a alguien en público, toca a quién (si es bueno, muere).")
        } else {
            DeathNote(false, "Ya usó su habilidad.")
        }
    }
    if (characterId in ACT_IF_DIED_RECENTLY) {
        val recently = tonight || players.any { it.id in game.dayDeaths }
        return if (recently) DeathNote(true, "Ha muerto hoy o esta noche: su habilidad se activa ahora (si no lo has hecho ya).")
        else DeathNote(false, "Solo actúa cuando muere. Ahora no hay nada que hacer.")
    }
    return null
}

/**
 * Posiciones de los pasos especiales en la hoja de la primera noche, con la misma numeración
 * que los personajes del catálogo: algunos (Filósofo, Kazali…) van antes de la info de los Esbirros.
 */
const val FIRST_NIGHT_MINION_INFO = 20
const val FIRST_NIGHT_DEMON_INFO = 24

/**
 * Con menos de 7 jugadores los Esbirros y el Demonio no se conocen la primera noche, salvo con
 * el Juguetero. Con Tor nadie conoce su personaje, así que tampoco hay información del Mal.
 */
fun hasEvilInfo(playerCount: Int, fabledIds: List<String> = emptyList()): Boolean =
    TOR !in fabledIds && (playerCount >= 7 || TOYMAKER in fabledIds)

private fun nightNumber(game: Game) = if (game.phase.isNight) game.phase.number else game.phase.number + 1

/**
 * Si la hoja cambia en mitad de la noche (alguien cambia de personaje, aparece la información
 * del Cultivador de opio…), el Narrador sigue en el mismo paso: se busca por su clave y no por
 * su posición. Si el cambio era moverse por la hoja, se respeta.
 */
fun keepNightStep(before: Game, after: Game, characters: Map<String, Character>): Game {
    if (!before.phase.isNight || !after.phase.isNight || before.phase.number != after.phase.number) return after
    if (after.nightStep != before.nightStep) return after
    val oldSteps = buildNightOrder(before, characters)
    val key = oldSteps.getOrNull(minOf(before.nightStep, oldSteps.size - 1))?.key
    val newSteps = buildNightOrder(after, characters)
    if (key == null || newSteps.getOrNull(after.nightStep)?.key == key) return after
    val index = newSteps.indexOfFirst { it.key == key }
    return if (index >= 0) after.copy(nightStep = index) else after
}

private class OrderedStep(val order: Double, val step: NightStep)

fun buildNightOrder(game: Game, characters: Map<String, Character>): List<NightStep> {
    val first = nightNumber(game) == 1
    val steps = mutableListOf<OrderedStep>()
    fun team(p: Player) = characters[p.characterId]?.team
    val seated = game.players.count { team(it) != Team.Traveller }

    steps.add(
        OrderedStep(
            0.0,
            NightStep(
                key = "dusk",
                kind = NightStepKind.Dusk,
                title = "Crepúsculo",
                instruction = "Comprueba que todos los jugadores tienen los ojos cerrados. Algunos Viajeros y Fábulas actúan ahora.",
                playerIds = emptyList(),
            ),
        ),
    )

    // Cultivador de opio: mientras viva sano, los malos no se conocen; la noche en que muere, sí.
    val poppies = game.players.filter { it.characterId == "poppygrower" }
    val poppyBlocks = poppies.any { it.alive && healthy(it) }
    val poppyDied = poppies.any { it.id in game.nightDeaths || it.id in game.dayDeaths }
    val poppyOrder = (characters["poppygrower"]?.otherNight ?: 0).toDouble()

    // Con Lil' Monsta no hay información de Esbirros ni de Demonio: se conocen en su paso.
    val evilInfo = if (first) hasEvilInfo(seated, game.fabledIds) && !hasLilMonsta(game) && !poppyBlocks
    else poppyDied && hasEvilInfo(seated, game.fabledIds)
    if (evilInfo) {
        val minions = game.players.filter { team(it) == Team.Minion }
        val demons = game.players.filter { team(it) == Team.Demon }
        // La noche en que muere el Cultivador de opio, justo después de su paso (sin faroles: ya los tuvo).
        val later = !first
        steps.add(
            OrderedStep(
                if (later) poppyOrder + 0.1 else FIRST_NIGHT_MINION_INFO.toDouble(),
                NightStep(
                    key = "minion_info",
                    kind = NightStepKind.MinionInfo,
                    title = "Información de Esbirros",
                    instruction = "Despierta a todos los Esbirros para que se reconozcan. Enséñales la ficha ESTE ES EL DEMONIO y señala al Demonio. Mándalos a dormir.",
                    playerIds = minions.map { it.id },
                ),
            ),
        )
        steps.add(
            OrderedStep(
                if (later) poppyOrder + 0.2 else FIRST_NIGHT_DEMON_INFO.toDouble(),
                NightStep(
                    key = "demon_info",
                    kind = NightStepKind.DemonInfo,
                    title = "Información del Demonio",
                    instruction = if (later) {
                        "Ha muerto el Cultivador de opio: despierta al Demonio y enséñale la ficha ESTOS SON TUS ESBIRROS señalando a los Esbirros."
                    } else {
                        "Despierta al Demonio. Enséñale la ficha ESTOS SON TUS ESBIRROS y señala a los Esbirros. Después enséñale ESTOS PERSONAJES NO ESTÁN EN JUEGO y los 3 faroles."
                    },
                    playerIds = demons.map { it.id },
                ),
            ),
        )
    }

    // Un paso por personaje en juego. El Borracho (o quien crea ser otro) se despierta como el
    // personaje que cree ser, y el Narrador le da información falsa. El Lunático no: su propio
    // paso ya le hace actuar como el Demonio que cree ser.
    val byCharacter = LinkedHashMap<String, List<Player>>()
    fun add(id: String, p: Player) {
        byCharacter[id] = (byCharacter[id] ?: emptyList()) + p
    }
    for (p in game.players) {
        add(p.characterId, p)
        val shown = p.shownCharacterId
        if (!shown.isNullOrEmpty() && p.characterId != "lunatic") add(shown, p)
    }
    // Las Fábulas y Lóricos no son jugadores: su paso lo hace el Narrador (anunciar, poner recordatorios…).
    for (id in game.fabledIds) if (id !in byCharacter) byCharacter[id] = emptyList()
    // Lil' Monsta no lo tiene nadie: en su paso despiertan los Esbirros para elegir quién lo cuida.
    if (hasLilMonsta(game)) byCharacter["lilmonsta"] = game.players.filter { team(it) == Team.Minion }
    for ((id, players) in byCharacter) {
        val c = characters[id] ?: continue
        val order = if (first) c.firstNight else c.otherNight
        if (order == 0) continue
        val deceived = players.filter { it.shownCharacterId == id }.map { it.id }
        val note = if (players.isNotEmpty()) deathNote(game, id, players) else null
        steps.add(
            OrderedStep(
                order.toDouble(),
                NightStep(
                    key = "char:$id",
                    kind = NightStepKind.Character,
                    title = c.name,
                    instruction = (if (first) c.firstNightReminder else c.otherNightReminder).ifEmpty { c.ability },
                    playerIds = players.map { it.id },
                    deceivedPlayerIds = deceived,
                    character = c,
                    // Los que actúan al morir se saltan si no les toca, aunque estén vivos.
                    // El Esbirro muerto por el Vigormortis conserva su habilidad.
                    dead = if (note != null) !note.wake
                    else players.isNotEmpty() && players.all { !reallyAlive(it) && !has(it, "vigormortis", REMINDER.hasAbility) },
                    deathNote = note,
                ),
            ),
        )
    }

    steps.add(
        OrderedStep(
            Double.MAX_VALUE,
            NightStep(
                key = "dawn",
                kind = NightStepKind.Dawn,
                title = "Amanecer",
                instruction = "Espera unos segundos. Despierta a todos los jugadores y anuncia quién ha muerto (nunca su personaje ni cómo).",
                playerIds = emptyList(),
            ),
        ),
    )

    return steps.sortedBy { it.order }.map { it.step }
}

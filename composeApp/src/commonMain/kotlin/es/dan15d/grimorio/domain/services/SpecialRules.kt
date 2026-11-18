package es.dan15d.grimorio.domain.services

import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.CharacterPair
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Jinx
import es.dan15d.grimorio.domain.entities.Team

// Fábulas, Lóricos e incompatibilidades (jinxes): lo que cambia la preparación o la noche.
// El resto de sus efectos los resuelve el Narrador con los recordatorios.

/** Con incompatibilidades en el guion, el Djinn está en juego para que todos conozcan la regla. */
const val DJINN = "djinn"
/** Los malos reciben su información inicial aunque haya menos de 7 jugadores. */
const val TOYMAKER = "toymaker"
/** Nadie conoce su personaje hasta morir y no hay información de Esbirros ni Demonio. */
const val TOR = "tor"
/** Puede haber personajes buenos repetidos. */
const val POPE = "pope"
/** El Narrador puede asignar personajes a jugadores concretos. */
const val GARDENER = "gardener"

/** Incompatibilidades (o interacciones) cuyos dos personajes están entre los dados. */
fun <T : CharacterPair> jinxesAmong(characterIds: Iterable<String>, pairs: List<T>): List<T> {
    val ids = characterIds.toSet()
    return pairs.filter { it.characterIds[0] in ids && it.characterIds[1] in ids }
}

/** Incompatibilidades (o interacciones) de un personaje con los demás dados. */
fun <T : CharacterPair> jinxesOf(characterId: String, others: Iterable<String>, pairs: List<T>): List<T> =
    jinxesAmong(listOf(characterId) + others, pairs).filter { characterId in it.characterIds }

/**
 * Fábulas y Lóricos con los que empieza una partida de este guion: los que trae el guion
 * y el Djinn si alguno de sus personajes tiene una incompatibilidad con otro.
 */
fun defaultFabled(scriptCharacters: List<Character>, jinxes: List<Jinx>): List<String> {
    val own = scriptCharacters.filter { it.team == Team.Fabled || it.team == Team.Loric }.map { it.id }
    val djinn = if (jinxesAmong(scriptCharacters.map { it.id }, jinxes).isNotEmpty()) listOf(DJINN) else emptyList()
    return (own + djinn).distinct()
}

fun hasFabled(fabledIds: List<String>, id: String): Boolean = id in fabledIds

fun hasFabled(game: Game, id: String): Boolean = hasFabled(game.fabledIds, id)

/** Personajes del Grimorio (de jugadores, los que creen ser y las Fábulas), para buscar incompatibilidades. */
fun gameCharacterIds(game: Game): List<String> =
    (game.players.flatMap { listOfNotNull(it.characterId.ifEmpty { null }, it.shownCharacterId?.ifEmpty { null }) } +
        game.fabledIds +
        game.extraCharacterIds).distinct()

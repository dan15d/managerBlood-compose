package es.dan15d.grimorio.domain.services

import es.dan15d.grimorio.domain.entities.Game

// Las partidas se guardan enteras (en el dispositivo o en Supabase) y duran más que la versión de
// la app que las creó. Al cargarlas se ponen al día aquí, así el resto del código siempre trabaja
// con una partida completa.
//
// Para cambiar el formato: sube GAME_SCHEMA_VERSION y añade a UPGRADES lo que hay que hacer para
// pasar de la versión anterior a la nueva.

const val GAME_SCHEMA_VERSION = 1

/** La app es más antigua que la partida (p. ej. un móvil con la versión anterior). */
class GameFromNewerAppError :
    Exception("Esta partida se guardó con una versión más nueva de la app. Recarga la página para actualizarla.")

private typealias Upgrade = (Game) -> Game

/** UPGRADES[n] pasa una partida de la versión n a la n + 1. */
private val UPGRADES: List<Upgrade> = listOf(
    // 0 → 1: las partidas sin versión pueden no tener fabledIds, nightDeaths, nightRevivals,
    // dayDeaths ni extraCharacterIds. Al leer el JSON, Game ya los deja como listas vacías.
    { game -> game },
)

/** Pone al día una partida guardada con cualquier versión anterior. */
fun upgradeGame(stored: Game): Game {
    val from = stored.schemaVersion
    if (from > GAME_SCHEMA_VERSION) throw GameFromNewerAppError()
    var game = stored
    for (version in from until GAME_SCHEMA_VERSION) game = UPGRADES[version](game)
    return game.copy(schemaVersion = GAME_SCHEMA_VERSION)
}

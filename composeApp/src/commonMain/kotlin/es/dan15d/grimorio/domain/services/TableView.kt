package es.dan15d.grimorio.domain.services

import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameStatus
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.PublicPlayer
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.entities.defaultAlignment

/** Lo que un jugador sabe de sí mismo. Vacío (los dos nulos) si no se le enseña nada. */
data class SeatSecret(val characterId: String? = null, val alignment: Alignment? = null)

/**
 * Lo que se enseña a cada jugador de sí mismo: el personaje que cree ser y su bando aparente.
 * El Borracho, el Lunático y la Marioneta nunca ven su personaje real: si aún no tienen uno
 * falso asignado, no se les enseña nada. Con Tor nadie sabe quién es hasta que muere, y una
 * muerte de noche no se sabe hasta el amanecer (`publiclyAlive`).
 */
fun seatSecret(
    player: Player,
    characters: Map<String, Character>,
    fabledIds: List<String> = emptyList(),
    publiclyAlive: Boolean = player.alive,
): SeatSecret {
    if (TOR in fabledIds && publiclyAlive) return SeatSecret()
    val shown = player.shownCharacterId?.ifEmpty { null }?.let { characters[it] }
    if (shown != null) return SeatSecret(shown.id, defaultAlignment(shown.team))
    if (DECOYS.containsKey(player.characterId)) return SeatSecret()
    return SeatSecret(player.characterId, player.alignment)
}

/**
 * Vivo para la mesa: quien ha muerto esta noche sigue vivo hasta que se anuncie al amanecer, y
 * quien ha resucitado esta noche sigue muerto hasta entonces.
 */
fun publicAlive(game: Game, player: Player): Boolean =
    (player.alive && player.id !in game.nightRevivals) || player.id in game.nightDeaths

fun publicPlayers(game: Game, characters: Map<String, Character>): List<PublicPlayer> =
    game.players.map { p ->
        PublicPlayer(
            seat = p.seat,
            name = p.name,
            alive = publicAlive(game, p),
            ghostVoteUsed = p.ghostVoteUsed,
            travellerId = if (characters[p.characterId]?.team == Team.Traveller) p.characterId else null,
        )
    }

fun publicPhase(game: Game): String =
    if (game.status == GameStatus.Finished) {
        if (game.winner == Alignment.Good) "Ha ganado el Bien" else "Ha ganado el Mal"
    } else {
        phaseLabel(game.phase)
    }

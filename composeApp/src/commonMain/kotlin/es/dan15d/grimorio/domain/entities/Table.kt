package es.dan15d.grimorio.domain.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Lo que se comparte con los jugadores: nunca el Grimorio, solo lo que se sabe en la mesa
// y lo que cada uno sabe de sí mismo.

/** Un asiento de la sala o de la partida, tal como lo ve el Narrador al montar la mesa. */
@Serializable
data class Seat(
    val id: String,
    val seat: Int,
    val name: String,
    /** Sesión del jugador que se unió desde su móvil; nula si el Narrador lo añadió a mano. */
    val userId: String? = null,
)

/** Información pública de un jugador (la que hay en la Plaza del Pueblo). */
@Serializable
data class PublicPlayer(
    val seat: Int,
    val name: String,
    val alive: Boolean,
    val ghostVoteUsed: Boolean,
    /** Solo los Viajeros: su personaje es público y toda la mesa lo conoce. */
    val travellerId: String? = null,
)

@Serializable
enum class TableStatus {
    @SerialName("lobby") Lobby,
    @SerialName("in_progress") InProgress,
    @SerialName("finished") Finished,
}

/** Sala abierta por el Narrador, con su código para unirse. */
data class Lobby(
    val gameId: String,
    val joinCode: String,
    val scriptId: String,
    val scriptName: String,
    val characterIds: List<String>,
    val status: TableStatus,
)

/** Lo que ve un jugador en su móvil. */
data class PlayerView(
    val gameId: String,
    val seatId: String,
    val seat: Int,
    val name: String,
    val scriptName: String,
    val characterIds: List<String>,
    val status: TableStatus,
    val phaseLabel: String? = null,
    /** Cuenta atrás del Narrador (debate, nominaciones): cuándo acaba. */
    val timerEndsAt: String? = null,
    val winner: Alignment? = null,
    /** Personaje que cree ser (el Borracho ve su Aldeano falso). Sin repartir aún: null. */
    val characterId: String? = null,
    val alignment: Alignment? = null,
    val alive: Boolean,
    val ghostVoteUsed: Boolean,
    val players: List<PublicPlayer>,
    /** Fábulas y Lóricos en juego: el Narrador los anuncia a toda la mesa. */
    val fabledIds: List<String>,
)

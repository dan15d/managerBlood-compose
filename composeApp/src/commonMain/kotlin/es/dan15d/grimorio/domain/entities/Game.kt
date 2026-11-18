package es.dan15d.grimorio.domain.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// La partida se guarda entera como JSON (en Supabase, columna games.data, o en el almacenamiento
// local) con EXACTAMENTE el mismo formato que la web en React: las dos apps comparten la base de
// datos. Por eso los nombres de los campos y los valores de los enums no se tocan.

@Serializable
enum class GameMode {
    @SerialName("manual") Manual,
    @SerialName("ai") Ai,
}

@Serializable
enum class GameStatus {
    @SerialName("in_progress") InProgress,
    @SerialName("finished") Finished,
}

@Serializable
data class ReminderToken(
    val id: String,
    /** Personaje que pone el recordatorio (p. ej. "poisoner"), o "custom". */
    val sourceCharacterId: String,
    val label: String,
)

@Serializable
data class Player(
    val id: String,
    val seat: Int,
    val name: String,
    /** Personaje real (lo que ve el Narrador en el Grimorio). */
    val characterId: String,
    /** Lo que cree ser el jugador si difiere (Borracho, Lunático…). */
    val shownCharacterId: String? = null,
    val alignment: Alignment,
    val alive: Boolean = true,
    /** Los muertos tienen un único voto para el resto de la partida. */
    val ghostVoteUsed: Boolean = false,
    val reminders: List<ReminderToken> = emptyList(),
    val notes: String? = null,
    /** Sesión del jugador que se unió desde su móvil. */
    val userId: String? = null,
)

@Serializable
enum class PhaseKind {
    @SerialName("night") Night,
    @SerialName("day") Day,
}

@Serializable
data class Phase(val kind: PhaseKind, val number: Int) {
    val isNight: Boolean get() = kind == PhaseKind.Night
    val isDay: Boolean get() = kind == PhaseKind.Day

    companion object {
        fun night(number: Int) = Phase(PhaseKind.Night, number)
        fun day(number: Int) = Phase(PhaseKind.Day, number)
    }
}

@Serializable
data class Nomination(
    val id: String,
    val nominatorId: String,
    val nomineeId: String,
    val voterIds: List<String> = emptyList(),
    val closed: Boolean = false,
    /** Exilio de un Viajero: vota todo el mundo y no es una ejecución. */
    val exile: Boolean? = null,
)

@Serializable
data class GameLogEntry(val at: String, val phase: String, val text: String)

/** Cuenta atrás puesta por el Narrador (debate, nominaciones): cuándo acaba y cuánto duraba. */
@Serializable
data class GameTimer(val endsAt: String, val seconds: Int)

@Serializable
data class Game(
    /** Versión del formato con que está guardada (ver GameSchema.kt). Las antiguas no la tienen: 0. */
    val schemaVersion: Int = 0,
    val id: String,
    val name: String,
    val mode: GameMode,
    val scriptId: String,
    val scriptName: String,
    /** Personajes del guion, para que los jugadores vean la lista aunque el guion sea personalizado. */
    val scriptCharacterIds: List<String>? = null,
    val players: List<Player>,
    /** Fábulas y Lóricos en juego: los usa el Narrador, no son jugadores y toda la mesa los conoce. */
    val fabledIds: List<String> = emptyList(),
    val demonBluffs: List<String> = emptyList(),
    val phase: Phase,
    /** Paso de la hoja de noche en el que va el Narrador. */
    val nightStep: Int = 0,
    /** Cuándo empezó la fase actual, para el cronómetro. */
    val phaseStartedAt: String? = null,
    val timer: GameTimer? = null,
    val nominations: List<Nomination> = emptyList(),
    val executedToday: String? = null,
    /** La nominación extra del Carnicero hoy, después de la ejecución. */
    val butcherNominationId: String? = null,
    /** Día extra de la Mente maestra (tras ejecutar al Demonio). */
    val mastermindDay: Int? = null,
    /** Muertos esta noche: ya lo están en el Grimorio, pero la mesa no lo sabe hasta el amanecer. */
    val nightDeaths: List<String> = emptyList(),
    /** Resucitados esta noche (Profesor, Shabaloth…): la mesa los sigue viendo muertos hasta el amanecer. */
    val nightRevivals: List<String> = emptyList(),
    /** Muertos durante el día de hoy (el Zombuul solo ataca si no ha muerto nadie). */
    val dayDeaths: List<String> = emptyList(),
    /** Personajes en juego que no tiene ningún jugador (Lil' Monsta, que cuida un Esbirro). */
    val extraCharacterIds: List<String> = emptyList(),
    val status: GameStatus = GameStatus.InProgress,
    val winner: Alignment? = null,
    val log: List<GameLogEntry> = emptyList(),
    /** Explicación y consejos que dio la IA al preparar la partida. */
    val aiNotes: String? = null,
    /** Crónica de la partida que escribió la IA al terminar, si el Narrador la pidió. */
    val chronicle: String? = null,
    /** Código corto para que los jugadores se unan. */
    val joinCode: String,
    val createdAt: String,
    val updatedAt: String,
)

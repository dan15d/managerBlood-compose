package es.dan15d.grimorio.application.ports

import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameStatus
import es.dan15d.grimorio.domain.entities.Interaction
import es.dan15d.grimorio.domain.entities.Jinx
import es.dan15d.grimorio.domain.entities.Lobby
import es.dan15d.grimorio.domain.entities.PlayerView
import es.dan15d.grimorio.domain.entities.Script
import es.dan15d.grimorio.domain.entities.Seat
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Puertos de la capa de aplicación (los mismos que src/application/ports en la web).
// Las promesas son funciones suspend y las suscripciones (watch) son Flow: se cancelan al
// cancelar la corrutina que las recoge.

// ---------- Catálogo ----------

/** Fuente de personajes (oficiales y, en el futuro, homebrew). */
interface CharacterCatalog {
    fun all(): List<Character>
    fun get(id: String): Character?
    /** Mapa id → personaje, para las reglas del dominio. */
    fun map(): Map<String, Character>
    /** Incompatibilidades oficiales entre personajes. */
    fun jinxes(): List<Jinx>
}

/** Apartados de la guía de un personaje en la wiki. */
enum class GuideSection(val key: String) { Summary("summary"), HowToRun("howToRun"), Examples("examples"), Tips("tips"), Bluffing("bluffing"), More("more") }

/** Un párrafo, un punto de lista o un ejemplo (cita) de la guía, en texto plano. */
@Serializable
data class GuideBlock(val kind: String, val text: String) {
    // kind: "p" | "li" | "quote"
}

data class CharacterGuide(val sections: Map<GuideSection, List<GuideBlock>>, val quote: String? = null)

/** Guías e interacciones de la wiki. Son grandes: se cargan cuando se piden. */
interface CharacterGuideSource {
    suspend fun guide(characterId: String): CharacterGuide?
    suspend fun interactions(): List<Interaction>
}

// ---------- Partidas ----------

data class GameSummary(
    val id: String,
    val name: String,
    val scriptName: String,
    val playerCount: Int,
    val phaseLabel: String,
    val status: GameStatus,
    val updatedAt: String,
)

/** Otro dispositivo guardó la partida después de que este la cargara: no se pisa su versión. */
class GameConflictError : Exception("La partida ha cambiado en otro dispositivo.")

/** No hay ninguna partida con ese id (o se ha borrado). */
class GameNotFoundError : Exception("No encuentro esa partida.")

interface GameRepository {
    suspend fun list(): List<GameSummary>
    suspend fun get(id: String): Game?
    /**
     * Guarda la partida. Con [expectUpdatedAt] solo la guarda si la versión guardada sigue siendo esa
     * (la última que vio este dispositivo); si otro la ha cambiado entretanto, lanza GameConflictError.
     */
    suspend fun save(game: Game, expectUpdatedAt: String? = null)
    suspend fun delete(id: String)
    /** Cambios en la partida desde otro sitio (otro dispositivo, otra pestaña); null si se borra. */
    fun watch(id: String): Flow<Game?>
}

interface IdGenerator {
    fun uuid(): String
    /** Código corto y legible para unirse a una partida (p. ej. «K7QP»). */
    fun joinCode(): String
}

// ---------- Guiones ----------

/** Guiones oficiales: solo lectura. */
interface OfficialScriptSource {
    fun list(): List<Script>
}

/** Guiones creados por el Narrador. */
interface CustomScriptRepository {
    suspend fun list(): List<Script>
    suspend fun save(script: Script)
    suspend fun delete(id: String)
}

// ---------- Ajustes ----------

@Serializable
enum class AiTransport {
    /** La Edge Function de Supabase. */
    @SerialName("cloud") Cloud,
    /** DeepSeek directo con la clave guardada en este dispositivo. */
    @SerialName("browser") Browser,
}

@Serializable
data class AppSettings(
    /** Clave de DeepSeek guardada solo en este dispositivo (si no hay función en la nube). */
    val deepseekKey: String = "",
    val aiModel: String = "deepseek-flash",
    val aiTransport: AiTransport = AiTransport.Browser,
)

interface SettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}

// ---------- Mesa (Narrador y jugadores) ----------

/** Acceso de Narrador con la contraseña común. */
interface AdminAuth {
    /** false si este despliegue no admite jugadores (sin nube): entonces todo el mundo es Narrador. */
    val required: Boolean
    suspend fun isAdmin(): Boolean
    suspend fun login(password: String): Boolean
    suspend fun logout()
}

data class OpenLobbyInput(val gameId: String, val joinCode: String, val scriptId: String, val scriptName: String, val characterIds: List<String>)

data class LobbyScript(val id: String, val name: String, val characterIds: List<String>)

/** La sala donde se unen los jugadores, vista desde el Narrador. */
interface LobbyGateway {
    suspend fun open(input: OpenLobbyInput): Lobby
    suspend fun get(gameId: String): Lobby?
    suspend fun listOpen(): List<Lobby>
    suspend fun changeScript(gameId: String, script: LobbyScript)
    suspend fun seats(gameId: String): List<Seat>
    fun watchSeats(gameId: String): Flow<List<Seat>>
    suspend fun addSeat(gameId: String, name: String)
    suspend fun removeSeat(seatId: String)
    /** Guarda el orden de los asientos (como estén sentados en la mesa). */
    suspend fun reorder(gameId: String, seatIds: List<String>)
    suspend fun close(gameId: String)
}

/** Publica en la mesa lo que pueden ver los jugadores cada vez que cambia la partida. */
interface TablePublisher {
    suspend fun publish(game: Game, characters: Map<String, Character>)
    suspend fun remove(gameId: String)
}

data class SeatRef(val gameId: String, val seatId: String)

/** El móvil del jugador. */
interface PlayerGateway {
    suspend fun join(code: String, name: String): SeatRef
    /** Partidas en las que esta sesión tiene asiento, la más reciente primero. */
    suspend fun mySeats(): List<SeatRef>
    suspend fun view(gameId: String, seatId: String): PlayerView?
    fun watch(gameId: String, seatId: String): Flow<PlayerView?>
    suspend fun leave(seatId: String)
}

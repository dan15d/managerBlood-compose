package es.dan15d.grimorio.application.usecases

import es.dan15d.grimorio.application.ports.AiAdviceRequest
import es.dan15d.grimorio.application.ports.AiAdviceTurn
import es.dan15d.grimorio.application.ports.AiChronicleRequest
import es.dan15d.grimorio.application.ports.AiStoryteller
import es.dan15d.grimorio.application.ports.CharacterCatalog
import es.dan15d.grimorio.application.ports.CharacterGuideSource
import es.dan15d.grimorio.application.ports.GameNotFoundError
import es.dan15d.grimorio.application.ports.GameRepository
import es.dan15d.grimorio.application.ports.GameSummary
import es.dan15d.grimorio.application.ports.IdGenerator
import es.dan15d.grimorio.application.ports.TablePublisher
import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameLogEntry
import es.dan15d.grimorio.domain.entities.GameMode
import es.dan15d.grimorio.domain.entities.GameStatus
import es.dan15d.grimorio.domain.entities.Interaction
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.Script
import es.dan15d.grimorio.domain.entities.Seat
import es.dan15d.grimorio.domain.entities.defaultAlignment
import es.dan15d.grimorio.domain.entities.isStorytellerTeam
import es.dan15d.grimorio.domain.nowIso
import es.dan15d.grimorio.domain.services.GAME_SCHEMA_VERSION
import es.dan15d.grimorio.domain.services.RandomFn
import es.dan15d.grimorio.domain.services.SetupIssueLevel
import es.dan15d.grimorio.domain.services.applySetupEffects
import es.dan15d.grimorio.domain.services.gameCharacterIds
import es.dan15d.grimorio.domain.services.isDealt
import es.dan15d.grimorio.domain.services.jinxesAmong
import es.dan15d.grimorio.domain.services.phaseLabel
import es.dan15d.grimorio.domain.services.seatMarionette
import es.dan15d.grimorio.domain.services.shuffle
import es.dan15d.grimorio.domain.services.validateSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/** Sala de la que sale la partida: mismo id y código, y los asientos de quienes se unieron (en orden). */
data class CreateGameTable(val gameId: String, val joinCode: String, val seats: List<Seat>)

data class CreateGameInput(
    val name: String,
    val mode: GameMode,
    val script: Script,
    val playerNames: List<String>,
    val characterIds: List<String>,
    val bluffIds: List<String>,
    val decoys: Map<String, String>,
    /** Asiento → personaje. Si falta, los personajes se reparten al azar (como sacarlos de la bolsa). */
    val assignment: Map<Int, String>? = null,
    /** Viajeros elegidos por los jugadores que sobran (más de 15). Asiento → personaje. */
    val travellers: Map<Int, String>? = null,
    /** Fábulas y Lóricos en juego (no son jugadores). */
    val fabledIds: List<String>? = null,
    val aiNotes: String? = null,
    val table: CreateGameTable? = null,
)

class CreateGame(
    private val repo: GameRepository,
    private val catalog: CharacterCatalog,
    private val ids: IdGenerator,
    private val random: RandomFn,
    private val publisher: TablePublisher,
) {
    suspend fun execute(input: CreateGameInput): Game {
        if (input.table != null && input.table.seats.size != input.playerNames.size) {
            throw Exception("Los asientos de la sala no coinciden con los jugadores.")
        }
        val names = input.playerNames.mapIndexed { i, n -> n.trim().ifEmpty { "Jugador ${i + 1}" } }
        val bag = input.characterIds.map { id -> catalog.get(id) ?: throw Exception("Personaje desconocido: $id") }
        val fabled = (input.fabledIds ?: emptyList()).mapNotNull { catalog.get(it) }.filter { isStorytellerTeam(it.team) }
        val travellerSeats = (input.travellers ?: emptyMap()).keys
        val errors = validateSelection(names.size - travellerSeats.size, bag + fabled).filter { it.level == SetupIssueLevel.Error }
        if (errors.isNotEmpty()) throw Exception(errors.joinToString(" ") { it.message })

        val seatsForBag = names.indices.filter { it !in travellerSeats }
        val assignment = input.assignment ?: emptyMap()
        // Lo que no se reparte (Lil' Monsta) queda en juego sin jugador.
        val extraCharacterIds = bag.filter { !isDealt(it) }.map { it.id }
        // Lo que queda en la bolsa tras quitar lo asignado a mano (puede haber fichas repetidas con el Papa).
        val left = bag.filter { isDealt(it) }.map { it.id }.toMutableList()
        for (id in assignment.values) {
            val i = left.indexOf(id)
            if (i >= 0) left.removeAt(i)
        }
        val remaining = ArrayDeque(shuffle(left, random))

        var players: List<Player> = names.mapIndexed { seat, name ->
            val characterId = input.travellers?.get(seat) ?: assignment[seat]
                ?: (if (seat in seatsForBag) remaining.removeFirstOrNull() else null)
                ?: throw Exception("Falta el personaje del asiento ${seat + 1}.")
            val character = catalog.get(characterId)!!
            val seatInfo = input.table?.seats?.getOrNull(seat)
            Player(
                id = seatInfo?.id ?: ids.uuid(),
                userId = seatInfo?.userId,
                seat = seat,
                name = name,
                characterId = characterId,
                shownCharacterId = input.decoys[characterId],
                alignment = defaultAlignment(character.team),
                alive = true,
                ghostVoteUsed = false,
                reminders = emptyList(),
            )
        }

        // Al sacarlos de la bolsa, la Marioneta se sienta junto al Demonio (a mano, lo decide el Narrador).
        if (input.assignment == null) players = seatMarionette(players, catalog.map(), random)
        val setup = applySetupEffects(players, catalog.map(), random) { ids.uuid() }
        players = setup.players
        val log = setup.log

        val now = nowIso()
        val game = Game(
            schemaVersion = GAME_SCHEMA_VERSION,
            id = input.table?.gameId ?: ids.uuid(),
            name = input.name.trim().ifEmpty { "${input.script.name} · ${todayEs()}" },
            mode = input.mode,
            scriptId = input.script.id,
            scriptName = input.script.name,
            scriptCharacterIds = input.script.characterIds,
            players = players,
            fabledIds = fabled.map { it.id },
            extraCharacterIds = extraCharacterIds,
            nightDeaths = emptyList(),
            nightRevivals = emptyList(),
            dayDeaths = emptyList(),
            demonBluffs = input.bluffIds.take(3),
            phase = Phase.night(1),
            nightStep = 0,
            nominations = emptyList(),
            status = GameStatus.InProgress,
            log = (listOf("Partida creada con ${players.size} jugadores.") + log).map { GameLogEntry(at = now, phase = "Preparación", text = it) },
            aiNotes = input.aiNotes,
            joinCode = input.table?.joinCode ?: ids.joinCode(),
            createdAt = now,
            updatedAt = now,
        )
        repo.save(game)
        publisher.publish(game, catalog.map())
        return game
    }
}

/** La fecha de hoy como la escribe la web en español («18/11/2026»). */
private fun todayEs(): String {
    // Días desde 1970 → fecha civil (algoritmo de Howard Hinnant), sin depender de otra librería.
    val days = Clock.System.now().epochSeconds.floorDiv(86_400L)
    val z = days + 719_468
    val era = z.floorDiv(146_097)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + (if (month <= 2) 1 else 0)
    return "$day/$month/$year"
}

class ListGames(private val repo: GameRepository) {
    suspend fun execute(): List<GameSummary> = repo.list().sortedByDescending { it.updatedAt }
}

class LoadGame(private val repo: GameRepository) {
    suspend fun execute(id: String): Game = repo.get(id) ?: throw GameNotFoundError()
}

/** Guarda el Grimorio y publica en la mesa lo que pueden ver los jugadores. */
class SaveGame(
    private val repo: GameRepository,
    private val publisher: TablePublisher,
    private val catalog: CharacterCatalog,
) {
    /** Con [expectUpdatedAt], solo si nadie la ha guardado desde esa versión (ver GameRepository). */
    suspend fun execute(game: Game, expectUpdatedAt: String? = null) {
        repo.save(game, expectUpdatedAt)
        publisher.publish(game, catalog.map())
    }
}

/** Sigue la partida mientras está abierta: si cambia desde otro dispositivo, llega la versión nueva. */
class WatchGame(private val repo: GameRepository) {
    fun execute(id: String): Flow<Game?> = repo.watch(id)
}

class DeleteGame(
    private val repo: GameRepository,
    private val publisher: TablePublisher,
) {
    suspend fun execute(id: String) {
        repo.delete(id)
        publisher.remove(id)
    }
}

/** Resumen para la lista de partidas (vale también para una guardada con una versión antigua). */
fun summarize(game: Game): GameSummary = GameSummary(
    id = game.id,
    name = game.name,
    scriptName = game.scriptName,
    playerCount = game.players.size,
    phaseLabel = if (game.status == GameStatus.Finished) {
        if (game.winner == Alignment.Good) "Ganó el Bien" else "Ganó el Mal"
    } else {
        phaseLabel(game.phase)
    },
    status = game.status,
    updatedAt = game.updatedAt,
)

/** Consulta al narrador de IA sobre la partida en curso (información falsa, dudas de reglas…). */
class AskAiAdvice(
    private val ai: AiStoryteller,
    private val catalog: CharacterCatalog,
    private val guides: CharacterGuideSource,
) {
    /** [history]: lo ya hablado en esta conversación, para que se pueda repreguntar. */
    suspend fun execute(game: Game, question: String, history: List<AiAdviceTurn> = emptyList()): String {
        if (question.isBlank()) throw Exception("Escribe la pregunta.")
        val inPlay = gameCharacterIds(game)
        val jinxes = jinxesAmong(inPlay, catalog.jinxes())
        // Si no cargan las interacciones (sin conexión), se pregunta igual sin ellas.
        val interactions: List<Interaction> = try {
            jinxesAmong(inPlay, guides.interactions())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        return ai.advise(AiAdviceRequest(game, catalog.map(), jinxes, interactions, question.trim(), history))
    }
}

/** Crónica de una partida terminada. Solo se escribe cuando el Narrador la pide: cada una cuesta una consulta. */
class WriteAiChronicle(
    private val ai: AiStoryteller,
    private val catalog: CharacterCatalog,
) {
    suspend fun execute(game: Game): String {
        if (game.status != GameStatus.Finished) throw Exception("La crónica se escribe cuando termina la partida.")
        val text = ai.chronicle(AiChronicleRequest(game, catalog.map())).trim()
        if (text.isEmpty()) throw Exception("La IA no ha escrito nada. Prueba otra vez.")
        return text
    }
}

class TestAiConnection(private val ai: AiStoryteller) {
    suspend fun execute() = ai.checkConnection()
}

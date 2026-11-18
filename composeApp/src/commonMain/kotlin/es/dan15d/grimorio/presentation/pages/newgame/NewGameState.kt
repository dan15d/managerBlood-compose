package es.dan15d.grimorio.presentation.pages.newgame

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import es.dan15d.grimorio.application.ports.SetupStyle
import es.dan15d.grimorio.application.usecases.CreateGameInput
import es.dan15d.grimorio.application.usecases.CreateGameTable
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameMode
import es.dan15d.grimorio.domain.entities.Lobby
import es.dan15d.grimorio.domain.entities.Script
import es.dan15d.grimorio.domain.entities.Seat
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.entities.isGoodTeam
import es.dan15d.grimorio.domain.entities.isStorytellerTeam
import es.dan15d.grimorio.domain.services.MIN_PLAYERS
import es.dan15d.grimorio.domain.services.POPE
import es.dan15d.grimorio.domain.services.SETUP_MODIFIERS
import es.dan15d.grimorio.domain.services.SetupIssueLevel
import es.dan15d.grimorio.domain.services.isDealt
import es.dan15d.grimorio.domain.services.jinxesAmong
import es.dan15d.grimorio.domain.services.maxTravellers
import es.dan15d.grimorio.domain.services.targetDistribution
import es.dan15d.grimorio.domain.services.travellerCount
import es.dan15d.grimorio.domain.services.travellerSeats
import es.dan15d.grimorio.domain.services.validateSelection
import es.dan15d.grimorio.infrastructure.persistence.SettingsKeyValueStore
import es.dan15d.grimorio.presentation.app.AppState
import es.dan15d.grimorio.presentation.app.Container
import es.dan15d.grimorio.presentation.app.ToastKind
import es.dan15d.grimorio.presentation.app.errorMessage
import kotlinx.serialization.json.Json
import kotlin.math.max

/** Los pasos del asistente, en orden. */
internal val STEPS = listOf("Modo", "Guion", "Jugadores", "Personajes", "Reparto")

/** Explicación y consejos de la IA sobre su propuesta. */
internal data class AiNotes(val explanation: String?, val tips: List<String>)

// Los nombres de la última partida se recuerdan para la siguiente (misma clave que la web).
private const val NAMES_KEY = "grimorio.lastNames"

private fun loadNames(): List<String> = try {
    SettingsKeyValueStore().get(NAMES_KEY)?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
} catch (_: Throwable) {
    emptyList()
}

private fun saveNames(names: List<String>) {
    try {
        SettingsKeyValueStore().put(NAMES_KEY, Json.encodeToString(names))
    } catch (_: Throwable) {
        // sin almacenamiento
    }
}

/**
 * Estado del asistente de nueva partida (lo que en la web eran los useState de NewGamePage).
 * Lo que se deriva (bolsa, reparto esperado, avisos…) se recalcula solo cuando cambia lo suyo.
 */
@Stable
internal class NewGameState(val app: Container, private val ui: AppState) {
    private val initialNames = loadNames()

    var step by mutableIntStateOf(0)
    var mode by mutableStateOf<GameMode?>(null)
    var script by mutableStateOf<Script?>(null)
    var playerCount by mutableIntStateOf(max(MIN_PLAYERS, initialNames.size.takeIf { it > 0 } ?: 8))
    var names by mutableStateOf(initialNames)
    var selected by mutableStateOf<List<String>>(emptyList())
    var decoys by mutableStateOf<Map<String, String>>(emptyMap())
    var bluffs by mutableStateOf<List<String>>(emptyList())
    var travellers by mutableStateOf<List<String>>(emptyList())
    var fabled by mutableStateOf<List<String>>(emptyList())
    /** Viajero → asiento que ha elegido el Narrador (si no, ocupan los últimos asientos). */
    var chosenTravellerSeats by mutableStateOf<Map<String, Int>>(emptyMap())
    var style by mutableStateOf(SetupStyle.Equilibrada)
    var extra by mutableStateOf("")
    var aiNotes by mutableStateOf<AiNotes?>(null)
    var aiLoading by mutableStateOf(false)
    var editingAi by mutableStateOf(false)
    var manualSeats by mutableStateOf(false)
    var assignment by mutableStateOf<Map<Int, String>>(emptyMap())
    var gameName by mutableStateOf("")
    var creating by mutableStateOf(false)
    // Con la nube, los jugadores se unen a una sala y sus nombres forman los asientos.
    var lobby by mutableStateOf<Lobby?>(null)
    var seats by mutableStateOf<List<Seat>>(emptyList())
    var openingLobby by mutableStateOf(false)

    val pool: List<Character> by derivedStateOf { script?.characterIds?.mapNotNull { app.catalog.get(it) } ?: emptyList() }
    val travellerPool: List<Character> by derivedStateOf {
        val own = pool.filter { it.team == Team.Traveller }
        own.ifEmpty { app.catalog.all().filter { it.team == Team.Traveller } }
    }
    val fabledPool: List<Character> = app.catalog.all().filter { isStorytellerTeam(it.team) }

    val forcedTravellers: Int get() = travellerCount(playerCount)
    val bagCount: Int get() = playerCount - max(travellers.size, forcedTravellers)
    val travellerBySeat: Map<Int, String> by derivedStateOf { travellerSeats(playerCount, travellers, chosenTravellerSeats) }
    val bagSeats: List<Int> by derivedStateOf { (0 until playerCount).filter { it !in travellerBySeat } }
    val bagAssignment: Map<Int, String> by derivedStateOf { assignment.filterKeys { it in bagSeats } }
    val chosen: List<Character> by derivedStateOf { selected.mapNotNull { app.catalog.get(it) } }
    /** Las fichas que se reparten (Lil' Monsta está en juego pero no va a la bolsa). */
    val dealtCount: Int by derivedStateOf { chosen.count(::isDealt) }
    private val fabledChosen: List<Character> by derivedStateOf { fabled.mapNotNull { app.catalog.get(it) } }
    val issues by derivedStateOf { validateSelection(bagCount, chosen + fabledChosen) }
    val errors by derivedStateOf { issues.filter { it.level == SetupIssueLevel.Error } }
    val target by derivedStateOf { targetDistribution(bagCount, chosen + fabledChosen) }
    val jinxes by derivedStateOf { jinxesAmong(selected + decoys.values + fabled + travellers, app.catalog.jinxes()) }
    val playerNames: List<String> by derivedStateOf {
        List(playerCount) { i -> names.getOrNull(i)?.trim()?.ifEmpty { null } ?: "Jugador ${i + 1}" }
    }

    /** Con el Papa, los buenos pueden ir repetidos; el Tonto del pueblo, siempre. */
    fun repeatable(c: Character): Boolean =
        (POPE in fabled && isGoodTeam(c.team)) || (SETUP_MODIFIERS[c.id]?.copies ?: 1) > 1

    val canAdvance: Boolean
        get() = when (step) {
            0 -> mode != null
            1 -> script != null
            2 -> if (lobby != null) seats.size >= MIN_PLAYERS else playerCount >= MIN_PLAYERS
            3 -> selected.isNotEmpty() && errors.isEmpty() && travellers.size >= forcedTravellers
            else -> !manualSeats || bagAssignment.size == bagCount
        }

    fun changePlayerCount(delta: Int) {
        playerCount += delta
        resetSelection()
    }

    /** Si quedan menos jugadores, sobran Viajeros: la bolsa nunca baja de 5. */
    fun trimTravellers() {
        val limit = maxTravellers(playerCount)
        if (travellers.size > limit) travellers = travellers.take(limit)
    }

    fun toggleTraveller(id: String) {
        travellers = when {
            id in travellers -> travellers - id
            travellers.size < maxTravellers(playerCount) -> travellers + id
            else -> travellers
        }
    }

    fun toggleFabled(id: String) {
        fabled = if (id in fabled) fabled - id else fabled + id
    }

    /** Tocar una ficha quita una copia (la última) o la añade. */
    fun toggleCharacter(id: String) {
        val i = selected.lastIndexOf(id)
        selected = if (i >= 0) selected.filterIndexed { index, _ -> index != i } else selected + id
    }

    fun addCopy(id: String) {
        selected = selected + id
    }

    fun resetSelection() {
        selected = emptyList()
        decoys = emptyMap()
        bluffs = emptyList()
        aiNotes = null
        editingAi = false
        assignment = emptyMap()
    }

    /** En la sala, el número de jugadores y sus nombres salen de quién se ha unido. */
    fun onSeats(next: List<Seat>) {
        seats = next
        if (lobby == null) return
        names = next.map { it.name }
        if (playerCount != next.size) {
            // Si entra o sale alguien, la bolsa elegida ya no cuadra.
            playerCount = next.size
            selected = emptyList()
            assignment = emptyMap()
        }
    }

    /** Cada vez que cambia la bolsa, se completan los engaños (Borracho…) y los faroles. */
    fun completeSelection() {
        val s = script ?: return
        if (selected.isEmpty()) return
        val done = app.completeManualSetup.execute(s, selected, decoys, bluffs)
        if (done.decoys != decoys) decoys = done.decoys
        if (done.bluffIds != bluffs) bluffs = done.bluffIds
    }

    fun randomize() {
        val s = script ?: return
        try {
            val p = app.suggestRandomSetup.execute(s, bagCount)
            selected = p.characterIds
            decoys = p.decoys
            bluffs = p.bluffIds
            assignment = emptyMap()
        } catch (e: Throwable) {
            ui.toast(errorMessage(e), ToastKind.Error)
        }
    }

    suspend fun askAi() {
        val s = script ?: return
        aiLoading = true
        try {
            val p = app.generateAiSetup.execute(s, playerNames.take(bagCount), style, extra)
            selected = p.characterIds
            decoys = p.decoys
            bluffs = p.bluffIds
            aiNotes = AiNotes(p.explanation, p.tips ?: emptyList())
            assignment = emptyMap()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            ui.toast(errorMessage(e), ToastKind.Error)
        } finally {
            aiLoading = false
        }
    }

    suspend fun chooseScript(s: Script) {
        if (script?.id != s.id) resetSelection()
        val manage = app.lobby
        if (manage != null) {
            openingLobby = true
            try {
                val current = lobby
                if (current == null) {
                    lobby = manage.open(s)
                } else if (current.scriptId != s.id) {
                    manage.changeScript(current.gameId, s)
                    lobby = current.copy(scriptId = s.id, scriptName = s.name, characterIds = s.characterIds)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                ui.toast(errorMessage(e), ToastKind.Error)
                return
            } finally {
                openingLobby = false
            }
        }
        script = s
        step = 2
    }

    /** Retomar una sala abierta desde la portada. */
    suspend fun resume(lobbyId: String, scripts: List<Script>) {
        val manage = app.lobby ?: return
        val l = manage.get(lobbyId) ?: return
        lobby = l
        script = scripts.find { it.id == l.scriptId }
        if (mode == null) mode = GameMode.Manual
        step = 2
    }

    /** Crea la partida; devuelve null si falla (el error ya se ha avisado). */
    suspend fun create(): Game? {
        val s = script ?: return null
        val m = mode ?: return null
        creating = true
        saveNames(playerNames)
        return try {
            val notes = aiNotes?.let { n -> (listOfNotNull(n.explanation) + n.tips.map { "• $it" }).filter { it.isNotEmpty() }.joinToString("\n") }
            val l = lobby
            app.createGame.execute(
                CreateGameInput(
                    name = gameName,
                    mode = m,
                    script = s,
                    playerNames = playerNames,
                    characterIds = selected,
                    bluffIds = bluffs,
                    decoys = decoys,
                    assignment = if (manualSeats) bagAssignment else null,
                    travellers = travellerBySeat,
                    fabledIds = fabled,
                    aiNotes = notes,
                    table = l?.let { CreateGameTable(it.gameId, it.joinCode, seats) },
                ),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            creating = false
            throw e
        } catch (e: Throwable) {
            ui.toast(errorMessage(e), ToastKind.Error)
            creating = false
            null
        }
    }
}

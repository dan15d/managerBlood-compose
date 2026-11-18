package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameMode
import es.dan15d.grimorio.domain.entities.GameStatus
import es.dan15d.grimorio.domain.entities.Jinx
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.ReminderToken
import es.dan15d.grimorio.domain.entities.Script
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.services.GAME_SCHEMA_VERSION
import kotlinx.serialization.json.Json
import java.io.File

// Lo que comparten los tests de dominio: el catálogo (leído de los JSON de composeResources sin
// pasar por la infraestructura) y una partida de prueba como la de gameFixture.ts.

private val json = Json { ignoreUnknownKeys = true }

/** Fichero de datos del catálogo. Los tests se ejecutan con el módulo composeApp como directorio. */
private fun dataFile(name: String): File {
    val candidates = listOf(
        File("src/commonMain/composeResources/files/data/$name"),
        File("composeApp/src/commonMain/composeResources/files/data/$name"),
    )
    return candidates.firstOrNull { it.exists() } ?: error("No encuentro $name (directorio: ${File(".").absolutePath})")
}

/** El catálogo estático, como StaticCharacterCatalog y StaticOfficialScripts en la web. */
object TestCatalog {
    val all: List<Character> by lazy { json.decodeFromString<List<Character>>(dataFile("characters.json").readText()) }
    val jinxes: List<Jinx> by lazy { json.decodeFromString<List<Jinx>>(dataFile("jinxes.json").readText()) }
    val scripts: List<Script> by lazy { json.decodeFromString<List<Script>>(dataFile("scripts.json").readText()) }
    val map: Map<String, Character> by lazy { all.associateBy { it.id } }

    fun get(id: String): Character? = map[id]

    fun chars(ids: List<String>): List<Character> = ids.map { map.getValue(it) }

    fun script(id: String): Script = scripts.first { it.id == id }

    /** Los personajes de un guion oficial. */
    fun scriptPool(id: String): List<Character> = chars(script(id).characterIds)
}

/** Jugadores de prueba: p0, p1… sentados en orden, llamados J1, J2…, con el bando de su personaje. */
fun testPlayers(ids: List<String>): List<Player> = ids.mapIndexed { seat, characterId ->
    val team = TestCatalog.get(characterId)?.team ?: error("Personaje desconocido: $characterId")
    Player(
        id = "p$seat",
        seat = seat,
        name = "J${seat + 1}",
        characterId = characterId,
        alignment = if (team == Team.Minion || team == Team.Demon) Alignment.Evil else Alignment.Good,
        alive = true,
        ghostVoteUsed = false,
        reminders = emptyList(),
    )
}

/** Lo que tiene toda partida nueva (NEW_GAME de gameFixture.ts) con los valores de los tests. */
fun newGame(
    players: List<Player> = emptyList(),
    phase: Phase = Phase.day(1),
    scriptId: String = "trouble-brewing",
    scriptName: String = "Trouble Brewing",
    createdAt: String = "",
): Game = Game(
    schemaVersion = GAME_SCHEMA_VERSION,
    id = "g",
    name = "Prueba",
    mode = GameMode.Manual,
    scriptId = scriptId,
    scriptName = scriptName,
    players = players,
    fabledIds = emptyList(),
    demonBluffs = emptyList(),
    phase = phase,
    nightStep = 0,
    nominations = emptyList(),
    nightDeaths = emptyList(),
    nightRevivals = emptyList(),
    dayDeaths = emptyList(),
    extraCharacterIds = emptyList(),
    status = GameStatus.InProgress,
    log = emptyList(),
    joinCode = "ABCDE",
    createdAt = createdAt,
    updatedAt = "",
)

/** La partida de prueba con un jugador por personaje (el `game(ids)` de cada test de la web). */
fun testGame(ids: List<String>, phase: Phase): Game = newGame(testPlayers(ids), phase)

fun reminderOf(id: String, source: String, label: String) = ReminderToken(id = id, sourceCharacterId = source, label = label)

fun Game.player(id: String): Player = players.first { it.id == id }

fun Game.night(number: Int): Game = copy(phase = Phase.night(number))

fun Game.withPlayer(id: String, change: (Player) -> Player): Game =
    copy(players = players.map { if (it.id == id) change(it) else it })

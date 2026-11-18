package es.dan15d.grimorio.infrastructure.persistence

import es.dan15d.grimorio.application.ports.AdminAuth
import es.dan15d.grimorio.application.ports.LobbyGateway
import es.dan15d.grimorio.application.ports.LobbyScript
import es.dan15d.grimorio.application.ports.OpenLobbyInput
import es.dan15d.grimorio.application.ports.PlayerGateway
import es.dan15d.grimorio.application.ports.SeatRef
import es.dan15d.grimorio.application.ports.TablePublisher
import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameStatus
import es.dan15d.grimorio.domain.entities.Lobby
import es.dan15d.grimorio.domain.entities.PlayerView
import es.dan15d.grimorio.domain.entities.PublicPlayer
import es.dan15d.grimorio.domain.entities.Seat
import es.dan15d.grimorio.domain.entities.TableStatus
import es.dan15d.grimorio.domain.nowIso
import es.dan15d.grimorio.domain.services.publicAlive
import es.dan15d.grimorio.domain.services.publicPhase
import es.dan15d.grimorio.domain.services.publicPlayers
import es.dan15d.grimorio.domain.services.seatSecret
import es.dan15d.grimorio.infrastructure.GrimJson
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// La mesa compartida en Supabase: game_public (lo que sabe toda la mesa) y game_seats (lo que
// sabe cada jugador de sí mismo). Las políticas RLS impiden que un jugador lea otro asiento.

@Serializable
private data class PublicRow(
    @SerialName("game_id") val gameId: String,
    @SerialName("join_code") val joinCode: String,
    @SerialName("script_id") val scriptId: String,
    @SerialName("script_name") val scriptName: String,
    @SerialName("character_ids") val characterIds: List<String> = emptyList(),
    val status: TableStatus,
    @SerialName("phase_label") val phaseLabel: String? = null,
    val winner: String? = null,
    val players: List<PublicPlayer>? = null,
    @SerialName("fabled_ids") val fabledIds: List<String>? = null,
    @SerialName("timer_ends_at") val timerEndsAt: String? = null,
)

@Serializable
private data class SeatRow(
    val id: String,
    @SerialName("game_id") val gameId: String,
    val seat: Int,
    @SerialName("player_name") val playerName: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("shown_character_id") val shownCharacterId: String? = null,
    @SerialName("shown_alignment") val shownAlignment: Alignment? = null,
    val alive: Boolean = true,
    @SerialName("ghost_vote_used") val ghostVoteUsed: Boolean = false,
)

@Serializable
private data class GameIdRow(@SerialName("game_id") val gameId: String)

@Serializable
private data class MySeatRow(val id: String, @SerialName("game_id") val gameId: String)

private fun PublicRow.toLobby() = Lobby(
    gameId = gameId,
    joinCode = joinCode,
    scriptId = scriptId,
    scriptName = scriptName,
    characterIds = characterIds,
    status = status,
)

private fun SeatRow.toSeat() = Seat(id = id, seat = seat, name = playerName, userId = userId)

/** Los mensajes de join_game/claim_admin ya están escritos para el usuario. */
private suspend fun <T> rpcError(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw Exception(e.supabaseMessage().ifBlank { "Algo ha fallado." }, e)
}

private val NO_ARGS = JsonObject(emptyMap())

class SupabaseAdminAuth(private val client: SupabaseClient) : AdminAuth {
    override val required: Boolean = true

    override suspend fun isAdmin(): Boolean {
        ownerId(client)
        val data = fail("comprobar el acceso") { client.postgrest.rpc("is_admin", NO_ARGS).decodeAs<JsonElement>() }
        return data == JsonPrimitive(true)
    }

    override suspend fun login(password: String): Boolean {
        ownerId(client)
        val data = rpcError {
            client.postgrest.rpc("claim_admin", buildJsonObject { put("password", password) }).decodeAs<JsonElement>()
        }
        return data == JsonPrimitive(true)
    }

    override suspend fun logout() {
        fail("salir del modo Narrador") { client.postgrest.rpc("leave_admin", NO_ARGS) }
    }
}

class SupabaseLobbyGateway(private val client: SupabaseClient) : LobbyGateway {
    override suspend fun open(input: OpenLobbyInput): Lobby {
        ownerId(client)
        val row = buildJsonObject {
            put("game_id", input.gameId)
            put("join_code", input.joinCode)
            put("script_id", input.scriptId)
            put("script_name", input.scriptName)
            putJsonArray("character_ids") { input.characterIds.forEach { add(JsonPrimitive(it)) } }
            put("status", "lobby")
        }
        return fail("abrir la sala") { client.from("game_public").insert(row) { select() }.decodeSingle<PublicRow>() }.toLobby()
    }

    override suspend fun get(gameId: String): Lobby? {
        ownerId(client)
        return fail("leer la sala") {
            client.from("game_public").select { filter { eq("game_id", gameId) } }.decodeList<PublicRow>()
        }.firstOrNull()?.toLobby()
    }

    override suspend fun listOpen(): List<Lobby> {
        ownerId(client)
        return fail("leer las salas") {
            client.from("game_public").select {
                filter { eq("status", "lobby") }
                order("updated_at", Order.DESCENDING)
            }.decodeList<PublicRow>()
        }.map { it.toLobby() }
    }

    override suspend fun changeScript(gameId: String, script: LobbyScript) {
        val row = buildJsonObject {
            put("script_id", script.id)
            put("script_name", script.name)
            putJsonArray("character_ids") { script.characterIds.forEach { add(JsonPrimitive(it)) } }
            put("updated_at", nowIso())
        }
        fail("cambiar el guion de la sala") { client.from("game_public").update(row) { filter { eq("game_id", gameId) } } }
    }

    override suspend fun seats(gameId: String): List<Seat> {
        ownerId(client)
        return fail("leer los asientos") {
            client.from("game_seats").select {
                filter { eq("game_id", gameId) }
                order("seat", Order.ASCENDING)
            }.decodeList<SeatRow>()
        }.map { it.toSeat() }
    }

    override fun watchSeats(gameId: String): Flow<List<Seat>> = client.watchTables(
        topic = "lobby:$gameId",
        refreshNow = true,
        changes = { listOf(it.changesOf("game_seats", "game_id", gameId)) },
    ) { refresh { seats(gameId) } }

    override suspend fun addSeat(gameId: String, name: String) {
        val current = seats(gameId)
        val row = buildJsonObject {
            put("game_id", gameId)
            put("seat", current.size)
            put("player_name", name.take(30))
        }
        fail("añadir el jugador") { client.from("game_seats").insert(row) }
    }

    override suspend fun removeSeat(seatId: String) {
        val removed = fail("quitar el jugador") {
            client.from("game_seats").delete {
                select(Columns.list("game_id"))
                filter { eq("id", seatId) }
            }.decodeSingle<GameIdRow>()
        }
        reorder(removed.gameId, seats(removed.gameId).map { it.id })
    }

    override suspend fun reorder(gameId: String, seatIds: List<String>) {
        val current = seats(gameId)
        val rows = seatIds.mapIndexedNotNull { seat, id ->
            val s = current.find { it.id == id } ?: return@mapIndexedNotNull null
            buildJsonObject {
                put("id", id)
                put("game_id", gameId)
                put("seat", seat)
                put("player_name", s.name)
                put("user_id", s.userId)
            }
        }
        fail("ordenar los asientos") { client.from("game_seats").upsert(JsonArray(rows)) }
    }

    override suspend fun close(gameId: String) {
        fail("cerrar la sala") { client.from("game_public").delete { filter { eq("game_id", gameId) } } }
    }
}

class SupabaseTablePublisher(private val client: SupabaseClient) : TablePublisher {
    // Lo último publicado de cada partida: si no cambia nada que vean los jugadores, no se escribe.
    private val last = mutableMapOf<String, String>()

    override suspend fun publish(game: Game, characters: Map<String, Character>) {
        val table = buildJsonObject {
            put("game_id", game.id)
            put("join_code", game.joinCode)
            put("script_id", game.scriptId)
            put("script_name", game.scriptName)
            putJsonArray("character_ids") { (game.scriptCharacterIds ?: emptyList()).forEach { add(JsonPrimitive(it)) } }
            put("status", GrimJson.encodeToJsonElement(GameStatus.serializer(), game.status))
            put("phase_label", publicPhase(game))
            put("winner", game.winner?.key)
            put("players", GrimJson.encodeToJsonElement(ListSerializer(PublicPlayer.serializer()), publicPlayers(game, characters)))
            putJsonArray("fabled_ids") { game.fabledIds.forEach { add(JsonPrimitive(it)) } }
            put("timer_ends_at", game.timer?.endsAt)
        }
        val seats = game.players.map { p ->
            val alive = publicAlive(game, p)
            val secret = seatSecret(p, characters, game.fabledIds, alive)
            buildJsonObject {
                put("id", p.id)
                put("game_id", game.id)
                put("seat", p.seat)
                put("player_name", p.name)
                put("user_id", p.userId)
                put("shown_character_id", secret.characterId)
                put("shown_alignment", secret.alignment?.key)
                put("alive", alive)
                put("ghost_vote_used", p.ghostVoteUsed)
            }
        }
        val signature = JsonArray(listOf(table, JsonArray(seats))).toString()
        if (last[game.id] == signature) return

        ownerId(client)
        fail("publicar la partida para los jugadores") {
            client.from("game_public").upsert(JsonObject(table + ("updated_at" to JsonPrimitive(nowIso()))))
        }
        fail("publicar los personajes de los jugadores") { client.from("game_seats").upsert(JsonArray(seats)) }
        last[game.id] = signature
    }

    override suspend fun remove(gameId: String) {
        last.remove(gameId)
        fail("quitar la partida de la mesa") { client.from("game_public").delete { filter { eq("game_id", gameId) } } }
    }
}

class SupabasePlayerGateway(private val client: SupabaseClient) : PlayerGateway {
    override suspend fun join(code: String, name: String): SeatRef {
        ownerId(client)
        val seatId = rpcError {
            client.postgrest.rpc("join_game", buildJsonObject {
                put("code", code)
                put("name", name)
            }).decodeAs<String>()
        }
        val seat = fail("entrar en la partida") {
            client.from("game_seats").select(Columns.list("game_id")) { filter { eq("id", seatId) } }.decodeSingle<GameIdRow>()
        }
        return SeatRef(gameId = seat.gameId, seatId = seatId)
    }

    override suspend fun mySeats(): List<SeatRef> {
        val me = ownerId(client)
        return fail("buscar tu partida") {
            client.from("game_seats").select(Columns.list("id", "game_id")) {
                filter { eq("user_id", me) }
                order("joined_at", Order.DESCENDING)
            }.decodeList<MySeatRow>()
        }.map { SeatRef(gameId = it.gameId, seatId = it.id) }
    }

    override suspend fun view(gameId: String, seatId: String): PlayerView? {
        ownerId(client)
        val (seat, table) = coroutineScope {
            val seatRes = async {
                fail("leer tu asiento") {
                    client.from("game_seats").select { filter { eq("id", seatId) } }.decodeList<SeatRow>()
                }.firstOrNull()
            }
            val tableRes = async {
                fail("leer la partida") {
                    client.from("game_public").select { filter { eq("game_id", gameId) } }.decodeList<PublicRow>()
                }.firstOrNull()
            }
            seatRes.await() to tableRes.await()
        }
        if (seat == null || table == null) return null
        return PlayerView(
            gameId = gameId,
            seatId = seatId,
            seat = seat.seat,
            name = seat.playerName,
            scriptName = table.scriptName,
            characterIds = table.characterIds,
            status = table.status,
            phaseLabel = table.phaseLabel,
            timerEndsAt = table.timerEndsAt,
            winner = Alignment.entries.find { it.key == table.winner },
            characterId = seat.shownCharacterId,
            alignment = seat.shownAlignment,
            alive = seat.alive,
            ghostVoteUsed = seat.ghostVoteUsed,
            players = table.players ?: emptyList(),
            fabledIds = table.fabledIds ?: emptyList(),
        )
    }

    /**
     * Lo que ve el jugador, al día. En la web también se volvía a leer al desbloquear el móvil
     * (visibilitychange): aquí basta con volver a recoger el Flow al volver a primer plano.
     */
    override fun watch(gameId: String, seatId: String): Flow<PlayerView?> = client.watchTables(
        topic = "seat:$seatId",
        refreshNow = true,
        changes = {
            listOf(
                it.changesOf("game_seats", "id", seatId),
                it.changesOf("game_public", "game_id", gameId),
            )
        },
    ) { refresh { view(gameId, seatId) } }

    override suspend fun leave(seatId: String) {
        rpcError { client.postgrest.rpc("leave_game", buildJsonObject { put("seat_id", seatId) }) }
    }
}

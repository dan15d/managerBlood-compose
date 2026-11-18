package es.dan15d.grimorio.infrastructure.persistence

import es.dan15d.grimorio.application.ports.CustomScriptRepository
import es.dan15d.grimorio.application.ports.GameConflictError
import es.dan15d.grimorio.application.ports.GameRepository
import es.dan15d.grimorio.application.ports.GameSummary
import es.dan15d.grimorio.application.usecases.summarize
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameStatus
import es.dan15d.grimorio.domain.entities.Script
import es.dan15d.grimorio.domain.nowIso
import es.dan15d.grimorio.domain.services.upgradeGame
import es.dan15d.grimorio.infrastructure.GrimJson
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

// Persistencia en Supabase (plan gratuito). Cada dispositivo entra con una sesión anónima y las
// políticas RLS (supabase/migrations) dejan las partidas y guiones solo a los Narradores.
// La partida se guarda entera como JSON (es un agregado) con el mismo formato que la web; las
// columnas sueltas sirven para listar sin descargar el Grimorio.

/** JSON de las filas de Supabase: aquí los null sí se escriben (para vaciar una columna). */
internal val RowJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Cliente de Supabase con lo que usa la app: sesión, tablas, tiempo real y la función de IA. */
fun createGrimorioSupabase(url: String, anonKey: String): SupabaseClient = createSupabaseClient(url, anonKey) {
    defaultSerializer = KotlinXSerializer(RowJson)
    // La función de IA puede tardar en responder: el tope por defecto (10 s) se queda corto.
    requestTimeout = 120.seconds
    install(Auth)
    install(Postgrest)
    install(Realtime)
    install(Functions)
}

private val signingIn = Mutex()

/** Id de la sesión anónima de este dispositivo (la crea la primera vez, una sola aunque se pida a la vez). */
suspend fun ownerId(client: SupabaseClient): String {
    // La sesión guardada se carga al arrancar: hay que esperarla para no crear otra.
    client.auth.awaitInitialization()
    client.auth.currentUserOrNull()?.let { return it.id }
    return signingIn.withLock {
        client.auth.currentUserOrNull()?.let { return@withLock it.id }
        try {
            client.auth.signInAnonymously()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw Exception("No he podido conectar con Supabase. Revisa que el inicio de sesión anónimo esté activado.")
        }
        client.auth.currentUserOrNull()?.id
            ?: throw Exception("No he podido conectar con Supabase. Revisa que el inicio de sesión anónimo esté activado.")
    }
}

/** Lo que dice Supabase cuando algo falla (el mensaje de Postgres, o el de la red). */
internal fun Throwable.supabaseMessage(): String =
    ((this as? RestException)?.error ?: message).orEmpty()

/** Ejecuta una petición y, si falla, lanza el error explicado para el Narrador. */
internal suspend fun <T> fail(what: String, block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    throw Exception("Supabase: no he podido $what (${e.supabaseMessage()}).", e)
}

/**
 * Sigue los cambios de unas tablas en tiempo real (postgres_changes) mientras se recoge el Flow.
 * [onEvent] recibe cada cambio, y null al quedar suscrito (para volver a leer por si cambió algo
 * entre cargar y suscribirse) y, con [refreshNow], también al empezar.
 */
internal fun <T> SupabaseClient.watchTables(
    topic: String,
    refreshNow: Boolean,
    changes: (RealtimeChannel) -> List<Flow<PostgresAction>>,
    onEvent: suspend ProducerScope<T>.(PostgresAction?) -> Unit,
): Flow<T> = channelFlow {
    // Un canal por suscripción: dos pantallas pueden seguir lo mismo a la vez.
    val channel = channel("$topic:${Random.nextLong().toULong().toString(36)}")
    try {
        for (flow in changes(channel)) {
            launch(start = CoroutineStart.UNDISPATCHED) { flow.collect { onEvent(it) } }
        }
        launch(start = CoroutineStart.UNDISPATCHED) {
            channel.status.first { it == RealtimeChannel.Status.SUBSCRIBED }
            onEvent(null)
        }
        // La primera conexión en tiempo real puede tardar: mientras, se enseña lo que hay ya.
        if (refreshNow) launch { onEvent(null) }
        try {
            channel.subscribe()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            /* sin tiempo real: se queda con lo leído */
        }
        awaitCancellation()
    } finally {
        withContext(NonCancellable) { runCatching { realtime.removeChannel(channel) } }
    }
}

/** Vuelve a leer y lo manda; si falla (sin conexión…), se ignora y sigue lo que había. */
internal suspend fun <T> ProducerScope<T>.refresh(read: suspend () -> T) {
    val value = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        return
    }
    send(value)
}

/** Cambios de una tabla filtrada por una columna (`columna=eq.valor`). */
internal fun RealtimeChannel.changesOf(table: String, column: String, value: String): Flow<PostgresAction> =
    postgresChangeFlow<PostgresAction>(schema = "public") {
        this.table = table
        filter(column, FilterOperator.EQ, value)
    }

@Serializable
private data class GameListRow(
    val id: String,
    val name: String,
    @SerialName("script_name") val scriptName: String,
    @SerialName("player_count") val playerCount: Int,
    @SerialName("phase_label") val phaseLabel: String,
    val status: GameStatus,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class DataRow(val data: JsonElement? = null)

@Serializable
internal data class IdRow(val id: String)

class SupabaseGameRepository(private val client: SupabaseClient) : GameRepository {
    override suspend fun list(): List<GameSummary> {
        ownerId(client)
        val rows = fail("leer las partidas") {
            client.from("games")
                .select(Columns.list("id", "name", "script_name", "player_count", "phase_label", "status", "updated_at"))
                .decodeList<GameListRow>()
        }
        return rows.map { GameSummary(it.id, it.name, it.scriptName, it.playerCount, it.phaseLabel, it.status, it.updatedAt) }
    }

    override suspend fun get(id: String): Game? {
        ownerId(client)
        val stored = fail("leer la partida") {
            client.from("games").select(Columns.list("data")) { filter { eq("id", id) } }.decodeList<DataRow>()
        }.firstOrNull()?.data?.takeIf { it != JsonNull } ?: return null
        return upgradeGame(GrimJson.decodeFromJsonElement(Game.serializer(), stored))
    }

    override suspend fun save(game: Game, expectUpdatedAt: String?) {
        val owner = ownerId(client)
        val s = summarize(game)
        val row = buildJsonObject {
            put("name", s.name)
            put("script_name", s.scriptName)
            put("player_count", s.playerCount)
            put("phase_label", s.phaseLabel)
            put("status", GrimJson.encodeToJsonElement(GameStatus.serializer(), s.status))
            put("join_code", game.joinCode)
            put("data", GrimJson.encodeToJsonElement(Game.serializer(), game))
            put("updated_at", game.updatedAt)
        }
        if (expectUpdatedAt == null) {
            val full = JsonObject(mapOf("id" to JsonPrimitive(game.id), "owner_id" to JsonPrimitive(owner)) + row)
            fail("guardar la partida") { client.from("games").upsert(full) }
            return
        }
        // Solo si nadie la ha guardado desde que este dispositivo la vio: así no se pisa a otro Narrador.
        val saved = fail("guardar la partida") {
            client.from("games").update(row) {
                select(Columns.list("id"))
                filter {
                    eq("id", game.id)
                    eq("data->>updatedAt", expectUpdatedAt)
                }
            }.decodeList<IdRow>()
        }
        if (saved.isEmpty()) throw GameConflictError()
    }

    /** Cambios en la partida desde otro dispositivo (la tabla games está en la publicación de Realtime). */
    override fun watch(id: String): Flow<Game?> = client.watchTables(
        topic = "game:$id",
        refreshNow = false,
        changes = { listOf(it.changesOf("games", "id", id)) },
    ) { action ->
        when (action) {
            is PostgresAction.Delete -> send(null)
            null, is PostgresAction.Update -> refresh { get(id) }
            else -> Unit
        }
    }

    override suspend fun delete(id: String) {
        ownerId(client)
        fail("borrar la partida") { client.from("games").delete { filter { eq("id", id) } } }
    }
}

@Serializable
private data class ScriptRow(
    val id: String,
    val name: String,
    val author: String? = null,
    val description: String? = null,
    val image: String? = null,
    @SerialName("character_ids") val characterIds: List<String>? = null,
)

class SupabaseScriptRepository(private val client: SupabaseClient) : CustomScriptRepository {
    override suspend fun list(): List<Script> {
        ownerId(client)
        val rows = fail("leer los guiones") {
            client.from("scripts")
                .select(Columns.list("id", "name", "author", "description", "image", "character_ids"))
                .decodeList<ScriptRow>()
        }
        return rows.map {
            Script(
                id = it.id,
                name = it.name,
                author = it.author ?: "",
                description = it.description,
                image = it.image,
                characterIds = it.characterIds ?: emptyList(),
                official = false,
            )
        }
    }

    override suspend fun save(script: Script) {
        val owner = ownerId(client)
        val row = buildJsonObject {
            put("id", script.id)
            put("owner_id", owner)
            put("name", script.name)
            put("author", script.author)
            put("description", script.description)
            put("image", script.image)
            putJsonArray("character_ids") { script.characterIds.forEach { add(JsonPrimitive(it)) } }
            put("updated_at", nowIso())
        }
        fail("guardar el guion") { client.from("scripts").upsert(row) }
    }

    override suspend fun delete(id: String) {
        ownerId(client)
        fail("borrar el guion") { client.from("scripts").delete { filter { eq("id", id) } } }
    }
}

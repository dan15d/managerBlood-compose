package es.dan15d.grimorio.infrastructure.persistence

import com.russhwolf.settings.Settings
import es.dan15d.grimorio.application.ports.AdminAuth
import es.dan15d.grimorio.application.ports.CustomScriptRepository
import es.dan15d.grimorio.application.ports.GameConflictError
import es.dan15d.grimorio.application.ports.GameRepository
import es.dan15d.grimorio.application.ports.GameSummary
import es.dan15d.grimorio.application.ports.TablePublisher
import es.dan15d.grimorio.application.usecases.summarize
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Script
import es.dan15d.grimorio.domain.services.upgradeGame
import es.dan15d.grimorio.infrastructure.GrimJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Guardado en el dispositivo: la app funciona sin cuenta ni conexión. Si no se puede guardar,
// se trabaja en memoria y se pierde al cerrar. En la web se usan las mismas claves del
// localStorage que la app en React («grimorio.games», «grimorio.scripts»), así que se ven las
// partidas que ya hubiera en ese navegador.

/** Lo mínimo que hace falta de un almacén clave → texto (en los tests, uno en memoria). */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** El almacenamiento de cada plataforma: localStorage en la web, SharedPreferences en Android, Preferences en escritorio. */
class SettingsKeyValueStore(private val settings: Settings = Settings()) : KeyValueStore {
    override fun get(key: String): String? = settings.getStringOrNull(key)
    override fun put(key: String, value: String) = settings.putString(key, value)
    override fun remove(key: String) = settings.remove(key)
}

class MemoryKeyValueStore : KeyValueStore {
    private val data = mutableMapOf<String, String>()
    override fun get(key: String): String? = data[key]
    override fun put(key: String, value: String) {
        data[key] = value
    }
    override fun remove(key: String) {
        data.remove(key)
    }
}

/** Trozo máximo cuando el almacén no admite textos largos (Preferences de Java: 8 KB por valor). */
private const val CHUNK = 8_000

/** Un objeto JSON id → elemento guardado en una sola clave (como en la web). */
internal class JsonStore(private val storage: KeyValueStore, val key: String) {
    private var memory: Map<String, JsonElement> = emptyMap()

    fun read(): Map<String, JsonElement> = try {
        val raw = readRaw()
        if (raw != null) GrimJson.parseToJsonElement(raw).jsonObject else memory
    } catch (_: Exception) {
        memory
    }

    fun write(data: Map<String, JsonElement>) {
        memory = data
        try {
            writeRaw(GrimJson.encodeToString(JsonObject.serializer(), JsonObject(data)))
        } catch (_: Exception) {
            /* sin almacenamiento: queda en memoria */
        }
    }

    private fun readRaw(): String? {
        storage.get(key)?.let { return it }
        val parts = storage.get("$key#parts")?.toIntOrNull() ?: return null
        return (0 until parts).joinToString("") { storage.get("$key#$it") ?: "" }
    }

    private fun writeRaw(raw: String) {
        try {
            storage.put(key, raw)
            clearParts()
        } catch (_: IllegalArgumentException) {
            // El almacén no admite un valor tan largo (escritorio): se guarda en trozos.
            storage.remove(key)
            clearParts()
            val chunks = raw.chunked(CHUNK)
            chunks.forEachIndexed { i, chunk -> storage.put("$key#$i", chunk) }
            storage.put("$key#parts", chunks.size.toString())
        }
    }

    private fun clearParts() {
        val parts = storage.get("$key#parts")?.toIntOrNull() ?: return
        for (i in 0 until parts) storage.remove("$key#$i")
        storage.remove("$key#parts")
    }
}

class LocalStorageGameRepository(storage: KeyValueStore = SettingsKeyValueStore()) : GameRepository {
    private val store = JsonStore(storage, "grimorio.games")

    private fun decode(stored: JsonElement): Game = upgradeGame(GrimJson.decodeFromJsonElement(Game.serializer(), stored))

    // Si alguna no se puede leer (guardada por una versión más nueva de la app), no sale en la lista.
    override suspend fun list(): List<GameSummary> = store.read().values.mapNotNull { runCatching { summarize(decode(it)) }.getOrNull() }

    override suspend fun get(id: String): Game? = store.read()[id]?.let(::decode)

    override suspend fun save(game: Game, expectUpdatedAt: String?) {
        val all = store.read()
        val current = all[game.id]
        if (expectUpdatedAt != null && current != null && current.updatedAt() != expectUpdatedAt) throw GameConflictError()
        store.write(all + (game.id to GrimJson.encodeToJsonElement(Game.serializer(), game)))
    }

    override suspend fun delete(id: String) {
        store.write(store.read() - id)
    }

    /**
     * En la web avisaba cuando otra pestaña del mismo navegador cambiaba lo guardado. Aquí no hay
     * forma común de enterarse: no llega nada (al guardar, si otro la cambió, sale el conflicto).
     */
    override fun watch(id: String): Flow<Game?> = emptyFlow()
}

private fun JsonElement.updatedAt(): String? =
    runCatching { (jsonObject["updatedAt"] as? JsonPrimitive)?.jsonPrimitive?.content }.getOrNull()

class LocalStorageScriptRepository(storage: KeyValueStore = SettingsKeyValueStore()) : CustomScriptRepository {
    private val store = JsonStore(storage, "grimorio.scripts")

    override suspend fun list(): List<Script> =
        store.read().values.mapNotNull { runCatching { GrimJson.decodeFromJsonElement(Script.serializer(), it) }.getOrNull() }

    override suspend fun save(script: Script) {
        store.write(store.read() + (script.id to GrimJson.encodeToJsonElement(Script.serializer(), script)))
    }

    override suspend fun delete(id: String) {
        store.write(store.read() - id)
    }
}

// Sin nube no hay jugadores conectados: quien abre la app es el Narrador y no se publica nada.
class LocalAdminAuth : AdminAuth {
    override val required: Boolean = false
    override suspend fun isAdmin(): Boolean = true
    override suspend fun login(password: String): Boolean = true
    override suspend fun logout() {}
}

class NoTablePublisher : TablePublisher {
    override suspend fun publish(game: Game, characters: Map<String, Character>) {}
    override suspend fun remove(gameId: String) {}
}

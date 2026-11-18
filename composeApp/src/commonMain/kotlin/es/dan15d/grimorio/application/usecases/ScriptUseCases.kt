package es.dan15d.grimorio.application.usecases

import es.dan15d.grimorio.application.ports.CharacterCatalog
import es.dan15d.grimorio.application.ports.CustomScriptRepository
import es.dan15d.grimorio.application.ports.IdGenerator
import es.dan15d.grimorio.application.ports.OfficialScriptSource
import es.dan15d.grimorio.domain.entities.Script
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ListScripts(
    private val official: OfficialScriptSource,
    private val custom: CustomScriptRepository,
) {
    suspend fun execute(): List<Script> {
        val mine = custom.list()
        return official.list() + mine.sortedWith(compareBy<Script> { spanishSortKey(it.name) }.thenBy { it.name })
    }
}

/** Lo que el Narrador escribe en el editor de guiones (sin id si es nuevo). */
data class SaveScriptInput(
    val id: String? = null,
    val name: String,
    val author: String = "",
    val characterIds: List<String>,
    val description: String? = null,
    val image: String? = null,
    val recommendedMinPlayers: Int? = null,
)

class SaveCustomScript(
    private val repo: CustomScriptRepository,
    private val ids: IdGenerator,
) {
    suspend fun execute(input: SaveScriptInput): Script {
        val name = input.name.trim()
        if (name.isEmpty()) throw Exception("Ponle un nombre al guion.")
        if (input.characterIds.size < 5) throw Exception("Un guion necesita al menos 5 personajes.")
        val script = Script(
            id = input.id ?: ids.uuid(),
            name = name,
            author = input.author.trim(),
            characterIds = input.characterIds,
            official = false,
            description = input.description,
            image = input.image,
            recommendedMinPlayers = input.recommendedMinPlayers,
        )
        repo.save(script)
        return script
    }
}

class DeleteCustomScript(private val repo: CustomScriptRepository) {
    suspend fun execute(id: String) = repo.delete(id)
}

/** Quita tildes y diéresis (como `normalize("NFD")` y quitar las marcas en la web). */
private fun foldAccents(c: Char): Char = when (c) {
    'á', 'à', 'ä', 'â', 'ã', 'å' -> 'a'
    'é', 'è', 'ë', 'ê' -> 'e'
    'í', 'ì', 'ï', 'î' -> 'i'
    'ó', 'ò', 'ö', 'ô', 'õ' -> 'o'
    'ú', 'ù', 'ü', 'û' -> 'u'
    'ñ' -> 'n'
    'ç' -> 'c'
    'ý', 'ÿ' -> 'y'
    else -> c
}

private fun normalize(s: String): String =
    s.lowercase().map(::foldAccents).filter { it in 'a'..'z' || it in '0'..'9' }.joinToString("")

/** Orden alfabético en español (como `localeCompare(…, "es")`): sin mayúsculas ni tildes, y la ñ tras la n. */
internal fun spanishSortKey(s: String): String = buildString {
    for (c in s.lowercase()) {
        if (c == 'ñ') append('n').append('￿') else append(foldAccents(c))
    }
}

data class ParsedScript(
    val name: String,
    val author: String,
    val logo: String? = null,
    val characterIds: List<String>,
    val unknown: List<String>,
)

/**
 * Lee el JSON de la herramienta oficial de guiones (script.bloodontheclocktower.com):
 * una lista de ids o de objetos { id }, con un objeto opcional { id: "_meta", name, author }.
 */
class ParseScriptJson(private val catalog: CharacterCatalog) {
    fun execute(text: String): ParsedScript {
        val data: JsonElement = try {
            Json.parseToJsonElement(text)
        } catch (_: Exception) {
            throw Exception("No es un JSON válido.")
        }
        if (data !is JsonArray) throw Exception("El JSON de un guion debe ser una lista.")
        val index = mutableMapOf<String, String>()
        for (c in catalog.all()) {
            index[normalize(c.name)] = c.id
            c.nameEn?.let { index[normalize(it)] = c.id }
            index[normalize(c.id)] = c.id
        }
        var name = ""
        var author = ""
        var logo: String? = null
        val characterIds = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        for (item in data) {
            val raw = when (item) {
                is JsonPrimitive -> if (item.isString) item.content else null
                is JsonObject -> (item["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                else -> null
            }
            if (raw.isNullOrEmpty()) continue
            if (raw == "_meta") {
                val meta = item as JsonObject
                name = meta["name"].asText()
                author = meta["author"].asText()
                val url = meta["logo"] as? JsonPrimitive
                if (url != null && url.isString && url.content.startsWith("https://")) logo = url.content
                continue
            }
            val id = index[normalize(raw)]
            if (id == null) unknown += raw
            else if (id !in characterIds) characterIds += id
        }
        return ParsedScript(name, author, logo, characterIds, unknown)
    }
}

/** `String(x ?? "")` de la web: el texto tal cual, o el JSON si no es un texto. */
private fun JsonElement?.asText(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> content
    else -> toString()
}

@OptIn(ExperimentalSerializationApi::class)
private val PrettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/** Exporta un guion en el formato de la herramienta oficial. */
fun scriptToJson(script: Script): String = PrettyJson.encodeToString(
    JsonArray.serializer(),
    buildJsonArray {
        add(
            buildJsonObject {
                put("id", "_meta")
                put("name", script.name)
                put("author", script.author)
                // El logo solo viaja si es una URL: una imagen subida no cabe en el JSON de la herramienta oficial.
                if (script.image?.startsWith("https://") == true) put("logo", script.image)
            },
        )
        for (id in script.characterIds) add(JsonPrimitive(id))
    },
)

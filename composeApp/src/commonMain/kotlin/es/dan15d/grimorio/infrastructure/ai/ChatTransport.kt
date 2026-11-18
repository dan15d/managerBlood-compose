package es.dan15d.grimorio.infrastructure.ai

import es.dan15d.grimorio.infrastructure.GrimJson
import es.dan15d.grimorio.infrastructure.persistence.ownerId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Serializable
data class ChatMessage(
    /** "system" | "user" | "assistant" */
    val role: String,
    val content: String,
)

data class ChatOptions(val json: Boolean = false, val temperature: Double? = null)

/** Cómo llega la conversación al modelo. Separado del narrador para poder cambiar de proveedor. */
fun interface ChatTransport {
    suspend fun complete(messages: List<ChatMessage>, options: ChatOptions): String
}

/** Cliente HTTP compartido. El motor lo pone cada plataforma (OkHttp en Android/escritorio, fetch en la web). */
val sharedHttpClient: HttpClient by lazy {
    HttpClient {
        install(ContentNegotiation) { json(GrimJson) }
        // Las respuestas de la IA pueden tardar bastante (razona antes de contestar).
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
    }
}

private const val DEEPSEEK_URL = "https://api.deepseek.com/chat/completions"

private fun messagesJson(messages: List<ChatMessage>): JsonArray =
    JsonArray(messages.map { GrimJson.encodeToJsonElement(ChatMessage.serializer(), it) })

/**
 * Cuerpo de la petición a la API directa de DeepSeek (igual que en rolAI). De fábrica razona en
 * alto; para esto basta con razonamiento bajo, que es más rápido y gasta menos. El tope de tokens
 * cuenta también lo que razona.
 */
fun deepseekBody(model: String, messages: List<ChatMessage>, options: ChatOptions = ChatOptions()): JsonObject = buildJsonObject {
    put("model", model)
    put("messages", messagesJson(messages))
    put("temperature", options.temperature ?: 0.8)
    put("max_tokens", 4000)
    put("reasoning_effort", "low")
    if (options.json) putJsonObject("response_format") { put("type", "json_object") }
}

private val THINK = Regex("<think>[\\s\\S]*?</think>")

private fun extract(data: JsonObject): String {
    val error = data["error"]
    if (error != null && error !is kotlinx.serialization.json.JsonNull) {
        val message = ((error as? JsonObject)?.get("message") as? JsonPrimitive)?.content
        throw Exception("DeepSeek: ${message ?: "error desconocido"}")
    }
    val text = runCatching {
        val choice = (data["choices"] as JsonArray)[0].jsonObject
        ((choice["message"] as JsonObject)["content"] as JsonPrimitive).content.trim()
    }.getOrNull()
    if (text.isNullOrEmpty()) throw Exception("La IA no ha respondido nada. Prueba otra vez.")
    return text.replace(THINK, "").trim()
}

/** Llama a DeepSeek desde el dispositivo con la clave guardada en Ajustes (solo para uso personal). */
class BrowserDeepSeekTransport(
    private val apiKey: String,
    private val model: String,
    private val http: HttpClient = sharedHttpClient,
) : ChatTransport {
    override suspend fun complete(messages: List<ChatMessage>, options: ChatOptions): String {
        val res = http.post(DEEPSEEK_URL) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(deepseekBody(model, messages, options))
        }
        when (res.status.value) {
            401 -> throw Exception("DeepSeek no acepta la clave. Revísala en Ajustes.")
            402 -> throw Exception("No queda saldo en DeepSeek.")
            429 -> throw Exception("DeepSeek está saturado ahora mismo. Prueba en un momento.")
        }
        if (!res.status.isSuccess()) throw Exception("DeepSeek ha respondido con un error (${res.status.value}).")
        return extract(GrimJson.parseToJsonElement(res.bodyAsText()).jsonObject)
    }
}

/**
 * Llama a la Edge Function «ai-storyteller» de Supabase, que guarda la clave de DeepSeek como
 * secreto. Es lo recomendable si la app es pública: la clave nunca llega al dispositivo.
 */
class SupabaseFunctionTransport(
    private val client: SupabaseClient,
    private val model: String,
) : ChatTransport {
    override suspend fun complete(messages: List<ChatMessage>, options: ChatOptions): String {
        ownerId(client)
        val body = buildJsonObject {
            put("model", model)
            put("messages", messagesJson(messages))
            put("json", options.json)
            options.temperature?.let { put("temperature", it) }
        }
        val text = try {
            client.functions.invoke("ai-storyteller", body).bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw Exception("La función de IA ha fallado: ${functionErrorMessage(e)}", e)
        }
        return extract(GrimJson.parseToJsonElement(text).jsonObject)
    }
}

/**
 * Si la función responde con error, el motivo real (p. ej. «Solo el Narrador puede usar la IA.»)
 * viene en el cuerpo de la respuesta: { "error": { "message": … } }.
 */
private fun functionErrorMessage(error: Exception): String {
    val body = (error as? RestException)?.error ?: return error.message.orEmpty()
    val parsed: JsonElement? = runCatching { GrimJson.parseToJsonElement(body) }.getOrNull()
    val message = runCatching {
        ((parsed as JsonObject)["error"] as JsonObject)["message"] as JsonPrimitive
    }.getOrNull()?.content
    return message ?: error.message.orEmpty()
}

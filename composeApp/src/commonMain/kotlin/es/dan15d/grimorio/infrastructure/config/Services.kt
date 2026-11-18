package es.dan15d.grimorio.infrastructure.config

import com.russhwolf.settings.Settings
import es.dan15d.grimorio.AppConfig
import es.dan15d.grimorio.application.ports.AiTransport
import es.dan15d.grimorio.application.ports.AppSettings
import es.dan15d.grimorio.application.ports.IdGenerator
import es.dan15d.grimorio.application.ports.SettingsStore
import es.dan15d.grimorio.infrastructure.GrimJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Los modelos de OpenRouter llevan «proveedor/modelo» y DeepSeek directo no los entiende: puede
 * quedar alguno guardado en un dispositivo o en la variable VITE_AI_MODEL de GitHub.
 */
private fun isOpenRouterModel(model: String?): Boolean = model.isNullOrEmpty() || model.contains("/")

/** Configuración del despliegue (Supabase y modelo de IA), generada por Gradle en AppConfig. */
object Env {
    val supabaseUrl: String = AppConfig.SUPABASE_URL
    val supabaseAnonKey: String = AppConfig.SUPABASE_ANON_KEY
    val aiModel: String = if (isOpenRouterModel(AppConfig.AI_MODEL)) "deepseek-flash" else AppConfig.AI_MODEL
}

fun hasSupabase(): Boolean = Env.supabaseUrl.isNotEmpty() && Env.supabaseAnonKey.isNotEmpty()

class RandomIdGenerator : IdGenerator {
    @OptIn(ExperimentalUuidApi::class)
    override fun uuid(): String = Uuid.random().toString()

    override fun joinCode(): String {
        // Sin letras que se confundan al dictarlas (0/O, 1/I/L).
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        return (1..5).map { alphabet[Random.nextInt(256) % alphabet.length] }.joinToString("")
    }
}

private const val SETTINGS_KEY = "grimorio.settings"

/** Ajustes en el almacenamiento del dispositivo (en la web, el mismo localStorage que la app en React). */
class LocalSettingsStore(private val settings: Settings = Settings()) : SettingsStore {
    override fun load(): AppSettings {
        val defaults = AppSettings(
            deepseekKey = "",
            aiModel = Env.aiModel,
            // La función en la nube hay que desplegarla aparte (README); hasta entonces, la clave del dispositivo.
            aiTransport = AiTransport.Browser,
        )
        return try {
            // Ajustes de cuando se usaba OpenRouter: su clave no vale para DeepSeek y su modelo tampoco.
            val saved: JsonObject = settings.getStringOrNull(SETTINGS_KEY)
                ?.let { GrimJson.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())
            fun text(key: String) = (saved[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val model = text("aiModel") ?: defaults.aiModel
            AppSettings(
                deepseekKey = text("deepseekKey") ?: defaults.deepseekKey,
                aiModel = if (isOpenRouterModel(model)) Env.aiModel else model,
                aiTransport = when (text("aiTransport")) {
                    "cloud" -> AiTransport.Cloud
                    "browser" -> AiTransport.Browser
                    else -> defaults.aiTransport
                },
            )
        } catch (_: Exception) {
            defaults
        }
    }

    override fun save(settings: AppSettings) {
        try {
            this.settings.putString(SETTINGS_KEY, GrimJson.encodeToString(AppSettings.serializer(), settings))
        } catch (_: Exception) {
            /* sin almacenamiento */
        }
    }
}

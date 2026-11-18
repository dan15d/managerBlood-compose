package es.dan15d.grimorio.infrastructure.catalog

import es.dan15d.grimorio.application.ports.CharacterGuide
import es.dan15d.grimorio.application.ports.CharacterGuideSource
import es.dan15d.grimorio.application.ports.GuideBlock
import es.dan15d.grimorio.application.ports.GuideSection
import es.dan15d.grimorio.domain.entities.Interaction
import es.dan15d.grimorio.infrastructure.GrimJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// Guías (una por personaje) e interacciones de la wiki de Villacuervos, en texto plano.
// Cada guía es un fichero aparte que solo se lee al abrirla. Se generan con un script aparte;
// no editar a mano.

class StaticCharacterGuides : CharacterGuideSource {
    private val lock = Mutex()
    private var interactionList: List<Interaction>? = null

    override suspend fun guide(characterId: String): CharacterGuide? {
        // Solo ids de personaje: nada de rutas raras.
        if (characterId.isEmpty() || !characterId.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return null
        val text = try {
            readDataFile("guides/$characterId.json")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null // No hay guía de ese personaje.
        }
        return parseGuide(GrimJson.parseToJsonElement(text).jsonObject)
    }

    override suspend fun interactions(): List<Interaction> = lock.withLock {
        interactionList ?: GrimJson.decodeFromString<List<Interaction>>(readDataFile("interactions.json"))
            .also { interactionList = it }
    }
}

private fun parseGuide(json: JsonObject): CharacterGuide {
    val sections = GuideSection.entries.mapNotNull { section ->
        json[section.key]?.let { section to GrimJson.decodeFromJsonElement(ListSerializer(GuideBlock.serializer()), it) }
    }.toMap()
    val quote = (json["quote"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    return CharacterGuide(sections, quote)
}

package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.services.GAME_SCHEMA_VERSION
import es.dan15d.grimorio.domain.services.GameFromNewerAppError
import es.dan15d.grimorio.domain.services.upgradeGame
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Las partes de dominio de game-schema.test.ts. Las del repositorio del navegador y LoadGame son
// de la aplicación y la infraestructura: van en sus propios tests.

private val schemaJson = Json { ignoreUnknownKeys = true }

private const val LEGACY = """{
  "id": "g",
  "name": "Antigua",
  "mode": "manual",
  "scriptId": "trouble-brewing",
  "scriptName": "Trouble Brewing",
  "players": [],
  "demonBluffs": [],
  "phase": { "kind": "night", "number": 2 },
  "nightStep": 3,
  "nominations": [],
  "status": "in_progress",
  "log": [],
  "joinCode": "ABCDE",
  "createdAt": "2026-10-28T20:00:00.000Z",
  "updatedAt": "2026-10-28T21:00:00.000Z"
}"""

private fun strings(vararg values: String) = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

/** Una partida como las que guardaba la app antes de tener versión: sin las listas añadidas después. */
private fun legacy(extra: Map<String, JsonElement> = emptyMap()): Game {
    val base = schemaJson.parseToJsonElement(LEGACY).jsonObject
    return schemaJson.decodeFromJsonElement(Game.serializer(), JsonObject(base + extra))
}

class GameSchemaTest {
    @Test
    fun unaPartidaSinVersionSePoneAlDiaConLasListasVacias() {
        val game = upgradeGame(legacy())
        assertEquals(GAME_SCHEMA_VERSION, game.schemaVersion)
        assertEquals(emptyList(), game.fabledIds)
        assertEquals(emptyList(), game.nightDeaths)
        assertEquals(emptyList(), game.nightRevivals)
        assertEquals(emptyList(), game.dayDeaths)
        assertEquals(emptyList(), game.extraCharacterIds)
        // El resto no cambia.
        assertEquals("Antigua", game.name)
        assertEquals(Phase.night(2), game.phase)
        assertEquals(3, game.nightStep)
        assertEquals("2026-10-28T21:00:00.000Z", game.updatedAt)
    }

    @Test
    fun conservaLoQueYaTenia() {
        val game = upgradeGame(legacy(mapOf("nightDeaths" to strings("p1"), "fabledIds" to strings("sentinel"))))
        assertEquals(listOf("p1"), game.nightDeaths)
        assertEquals(listOf("sentinel"), game.fabledIds)
    }

    @Test
    fun unaPartidaAlDiaQuedaIgual() {
        val current = upgradeGame(legacy(mapOf("dayDeaths" to strings("p2"))))
        assertEquals(current, upgradeGame(current))
    }

    @Test
    fun noAbreUnaPartidaGuardadaPorUnaVersionMasNuevaDeLaApp() {
        assertFailsWith<GameFromNewerAppError> {
            upgradeGame(legacy(mapOf("schemaVersion" to JsonPrimitive(GAME_SCHEMA_VERSION + 1))))
        }
    }
}

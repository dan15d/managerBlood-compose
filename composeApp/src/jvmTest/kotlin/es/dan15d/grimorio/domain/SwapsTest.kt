package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.services.GameMessage
import es.dan15d.grimorio.domain.services.addReminder
import es.dan15d.grimorio.domain.services.applyReminderChoice
import es.dan15d.grimorio.domain.services.buildNightOrder
import es.dan15d.grimorio.domain.services.expiringReminders
import es.dan15d.grimorio.domain.services.swapCharacters
import es.dan15d.grimorio.domain.services.victoryHint
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val map = TestCatalog.map

private fun swapGame(ids: List<String>): Game = testGame(ids, Phase.night(2))

private val swapIds = listOf("vortox", "cerenovus", "snakecharmer", "clockmaker", "dreamer", "oracle", "flowergirl")

private fun charm(g: Game, target: String): GameMessage =
    applyReminderChoice(g, target, reminderOf("c", "snakecharmer", "Envenenado"), map)

class SwapsTest {
    // ---------- Encantador de serpientes ----------

    @Test
    fun siEligeAlDemonioIntercambianPersonajesYBandosYElAntiguoDemonioQuedaEnvenenado() {
        val (g, message) = charm(swapGame(swapIds), "p0")
        assertEquals("vortox", g.players[2].characterId)
        assertEquals(Alignment.Evil, g.players[2].alignment)
        assertEquals("snakecharmer", g.players[0].characterId)
        assertEquals(Alignment.Good, g.players[0].alignment)
        assertEquals(listOf("snakecharmer/Envenenado"), g.players[0].reminders.map { "${it.sourceCharacterId}/${it.label}" })
        assertContains(message, "ha elegido al Demonio")
        // El veneno es para siempre: no se propone quitarlo al anochecer.
        assertEquals(emptyList(), expiringReminders(g))
        // La partida sigue: hay un Demonio vivo y la hoja de noche despierta al nuevo.
        assertNull(victoryHint(g, map))
        assertEquals(listOf("p2"), buildNightOrder(g, map).find { it.key == "char:vortox" }?.playerIds)
    }

    @Test
    fun siEligeAOtroNoPasaNada() {
        val (g, message) = charm(swapGame(swapIds), "p3")
        assertEquals(swapIds, g.players.map { it.characterId })
        assertContains(message, "no es el Demonio")
    }

    @Test
    fun siEstaEnvenenadoOEsElBorrachoNoHayIntercambio() {
        val poisoned = addReminder(swapGame(swapIds), "p2", reminderOf("e", "cerenovus", "Envenenado"))
        assertEquals("vortox", charm(poisoned, "p0").game.players[0].characterId)
        val drunk = swapGame(listOf("vortox", "cerenovus", "drunk", "clockmaker", "dreamer"))
            .withPlayer("p2") { it.copy(shownCharacterId = "snakecharmer") }
        assertContains(charm(drunk, "p0").message, "no tiene habilidad")
    }

    // ---------- Barbero ----------

    @Test
    fun elBarberoIntercambiaPersonajesPeroCadaUnoConservaSuBando() {
        val g = swapCharacters(swapGame(listOf("imp", "poisoner", "barber", "chef", "empath")), "p1", "p3", map)
        assertEquals("chef", g.players[1].characterId)
        assertEquals(Alignment.Evil, g.players[1].alignment)
        assertEquals("poisoner", g.players[3].characterId)
        assertEquals(Alignment.Good, g.players[3].alignment)
    }

    @Test
    fun loQueCreeSerElBorrachoViajaConSuPersonaje() {
        val g0 = swapGame(listOf("imp", "drunk", "chef", "empath", "monk")).withPlayer("p1") { it.copy(shownCharacterId = "empath") }
        val g = swapCharacters(g0, "p1", "p2", map)
        assertEquals("drunk", g.players[2].characterId)
        assertEquals("empath", g.players[2].shownCharacterId)
        assertNull(g.players[1].shownCharacterId)
    }
}

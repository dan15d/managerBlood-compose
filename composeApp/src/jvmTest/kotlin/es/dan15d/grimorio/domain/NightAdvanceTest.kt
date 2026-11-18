package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.services.advanceAfterChoice
import es.dan15d.grimorio.domain.services.applyReminderChoice
import es.dan15d.grimorio.domain.services.buildNightOrder
import es.dan15d.grimorio.domain.services.placeReminder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// La hoja de noche pasa sola al siguiente paso cuando elegir jugador era la acción principal.

private val map = TestCatalog.map

private fun game(ids: List<String>, night: Int = 2): Game =
    testGame(ids, Phase.night(night)).copy(scriptId = "", scriptName = "")

/** La partida en el paso de noche de ese personaje. */
private fun atStep(g: Game, characterId: String): Game {
    val index = buildNightOrder(g, map).indexOfFirst { it.character?.id == characterId }
    assertTrue(index > 0, "paso de $characterId")
    return g.copy(nightStep = index)
}

private fun r(source: String, label: String) = reminderOf("$source-$label", source, label)

private data class Chosen(val advanced: Boolean, val game: Game)

/** Toca al jugador con el botón principal del paso y dice si la hoja ha avanzado. */
private fun choose(g: Game, source: String, label: String, target: String): Chosen {
    val before = atStep(g, source)
    val after = applyReminderChoice(before, target, r(source, label), map).game
    val next = advanceAfterChoice(before, after, target, source, label, map)
    return Chosen(next.nightStep == before.nightStep + 1, next)
}

class NightAdvanceTest {
    @Test
    fun elDiablilloMataPasaAlSiguientePaso() {
        val g = game(listOf("imp", "poisoner", "empath", "monk", "soldier"))
        val (advanced, next) = choose(g, "imp", "Muerto", "p2")
        assertTrue(advanced)
        assertFalse(next.players[2].alive)
    }

    @Test
    fun elEnvenenadorYElMonjeTambien() {
        val g = game(listOf("imp", "poisoner", "empath", "monk", "soldier"))
        assertTrue(choose(g, "poisoner", "Envenenado", "p2").advanced)
        assertTrue(choose(g, "monk", "A salvo", "p4").advanced)
    }

    @Test
    fun elDiablilloQueSeMataASiMismoSeQuedaHayQueEnsenarTuEres() {
        val g = game(listOf("imp", "poisoner", "empath", "monk", "soldier"))
        val (advanced, next) = choose(g, "imp", "Muerto", "p0")
        assertFalse(next.players[0].alive)
        assertFalse(advanced)
    }

    @Test
    fun elFangGuQueSaltaAUnForasteroSeQueda() {
        val g = game(listOf("fanggu", "poisoner", "empath", "butler", "soldier"))
        val (advanced, next) = choose(g, "fanggu", "Muerto", "p3")
        assertEquals("fanggu", next.players[3].characterId)
        assertFalse(advanced)
    }

    @Test
    fun quienEligeParaRecibirInformacionSeQuedaHayQueDarla() {
        val g = game(listOf("imp", "poisoner", "ravenkeeper", "dreamer", "soldier"))
        assertFalse(choose(g, "dreamer", "Elegido", "p0").advanced)
    }

    @Test
    fun laPitHagSeQuedaDespuesSeEligeElPersonaje() {
        val g = game(listOf("imp", "pithag", "empath", "monk", "soldier"))
        assertFalse(choose(g, "pithag", "Elegido", "p2").advanced)
    }

    @Test
    fun elPoYElAlHadikhiaEligenAVariosSeQuedan() {
        assertFalse(choose(game(listOf("po", "poisoner", "empath", "monk", "soldier")), "po", "Muerto", "p2").advanced)
        assertFalse(choose(game(listOf("alhadikhia", "poisoner", "empath", "monk", "soldier")), "alhadikhia", "1", "p2").advanced)
    }

    @Test
    fun unRecordatorioQueNoEsLaAccionPrincipalNoMueveLaHoja() {
        val g = atStep(game(listOf("imp", "poisoner", "empath", "monk", "soldier")), "monk")
        val after = placeReminder(g, "p2", r("imp", "Muerto"), 1)
        assertEquals(g.nightStep, advanceAfterChoice(g, after, "p2", "imp", "Muerto", map).nightStep)
    }

    @Test
    fun deDiaNoSeMueveNada() {
        val g = game(listOf("imp", "poisoner", "empath", "monk", "soldier")).copy(phase = Phase.day(2))
        val after = placeReminder(g, "p2", r("poisoner", "Envenenado"), 1)
        assertEquals(0, advanceAfterChoice(g, after, "p2", "poisoner", "Envenenado", map).nightStep)
    }
}

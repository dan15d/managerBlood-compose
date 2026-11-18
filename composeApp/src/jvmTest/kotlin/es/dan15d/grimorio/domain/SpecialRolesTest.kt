package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.services.VictoryHint
import es.dan15d.grimorio.domain.services.addReminder
import es.dan15d.grimorio.domain.services.buildNightOrder
import es.dan15d.grimorio.domain.services.closeNomination
import es.dan15d.grimorio.domain.services.duskVictory
import es.dan15d.grimorio.domain.services.execute
import es.dan15d.grimorio.domain.services.executeNow
import es.dan15d.grimorio.domain.services.expiringReminders
import es.dan15d.grimorio.domain.services.killPlayer
import es.dan15d.grimorio.domain.services.nominate
import es.dan15d.grimorio.domain.services.onTheBlock
import es.dan15d.grimorio.domain.services.startDay
import es.dan15d.grimorio.domain.services.startNight
import es.dan15d.grimorio.domain.services.toggleVote
import es.dan15d.grimorio.domain.services.victoryHint
import es.dan15d.grimorio.domain.services.virginNomination
import es.dan15d.grimorio.domain.services.voteCount
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val map = TestCatalog.map

private fun game(ids: List<String>, phase: Phase = Phase.day(2)): Game =
    testGame(ids, phase).copy(scriptId = "", scriptName = "")

private fun reminder(playerId: String, source: String, label: String) = reminderOf("$playerId-$label", source, label)

private val tb = listOf("imp", "poisoner", "saint", "mayor", "virgin", "chef", "empath", "ravenkeeper")

class SpecialRolesTest {
    @Test
    fun santoSiLoEjecutanGanaElMalEnvenenadoNo() {
        assertEquals(VictoryHint(Alignment.Evil, "Han ejecutado al Santo."), victoryHint(execute(game(tb), "p2"), map))
        val poisoned = addReminder(game(tb), "p2", reminder("p2", "poisoner", "Envenenado"))
        assertNull(victoryHint(execute(poisoned, "p2"), map))
    }

    @Test
    fun santoSiMuereDeOtraFormaNoPasaNada() {
        assertNull(victoryHint(killPlayer(game(tb), "p2"), map))
    }

    private fun threeAlive(g: Game) = listOf("p2", "p4", "p5", "p6", "p7").fold(g) { acc, id -> killPlayer(acc, id) }

    @Test
    fun alcaldeCon3VivosYSinEjecucionSuEquipoGanaAlAnochecer() {
        assertEquals(Alignment.Good, duskVictory(threeAlive(game(tb)), map)?.winner)
    }

    @Test
    fun alcaldeNoGanaSiHuboEjecucionSiQuedanMasDe3OSiEstaEnvenenado() {
        val g = threeAlive(game(tb))
        assertNull(duskVictory(g.copy(executedToday = "p6"), map))
        assertNull(duskVictory(game(tb), map))
        assertNull(duskVictory(addReminder(g, "p3", reminder("p3", "poisoner", "Envenenado")), map))
    }

    @Test
    fun virgenSiLaNominaUnAldeanoPorPrimeraVezEseAldeanoEsEjecutadoEnElActo() {
        val g = nominate(game(tb), "p5", "p4", "n1")
        val result = virginNomination(g, "p5", "p4", map)
        assertTrue(result.executes)
        val after = executeNow(result.game, "p5")
        assertFalse(after.players[5].alive)
        assertEquals("p5", after.executedToday)
        assertTrue(after.nominations.all { it.closed })
    }

    @Test
    fun virgenSoloLaPrimeraVezYNoSiNominaAlguienQueNoEsAldeano() {
        val first = virginNomination(nominate(game(tb), "p1", "p4", "n1"), "p1", "p4", map)
        assertFalse(first.executes)
        // Ya ha perdido su habilidad: aunque luego la nomine un Aldeano, no pasa nada.
        val again = virginNomination(first.game, "p5", "p4", map)
        assertFalse(again.executes)
    }

    @Test
    fun virgenSiNominaElEspiaDecideElNarrador() {
        val g = game(listOf("imp", "spy", "virgin", "chef", "empath"))
        val result = virginNomination(nominate(g, "p1", "p2", "n1"), "p1", "p2", map)
        assertFalse(result.executes)
        assertTrue(result.spy)
    }

    @Test
    fun ejecutarSinVotacionEjecutaAunqueNadieHayaVotadoYCierraLaVotacionAbierta() {
        val g = executeNow(nominate(game(tb), "p5", "p6", "n1"), "p6", "El Juez fuerza la ejecución.")
        assertFalse(g.players[6].alive)
        assertTrue(g.nominations[0].closed)
        assertTrue(g.log.any { it.text.contains("Juez") })
        val error = assertFailsWith<RuleViolation> { executeNow(g, "p7") }
        assertContains(error.message!!, "Solo hay una ejecución")
    }

    @Test
    fun elGuardianDeCuervosSeDespiertaSoloSiMuereEstaNoche() {
        var g = game(tb, Phase.night(2))
        fun step() = buildNightOrder(g, map).find { it.key == "char:ravenkeeper" }!!
        assertTrue(step().dead)
        assertEquals(false, step().deathNote?.wake)
        g = killPlayer(g, "p7", "muere por el Diablillo")
        assertFalse(step().dead)
        assertEquals(true, step().deathNote?.wake)
        // La noche siguiente ya no se despierta.
        g = startNight(startDay(g))
        assertEquals(false, step().deathNote?.wake)
    }

    @Test
    fun elVotoDelElegidoPorElBurocrataVale3YElDelLadronResta1() {
        var g = game(tb + listOf("bureaucrat", "thief"))
        g = addReminder(g, "p5", reminder("p5", "bureaucrat", "3 votos"))
        g = addReminder(g, "p6", reminder("p6", "thief", "Voto negativo"))
        g = nominate(g, "p3", "p1", "n1")
        for (v in listOf("p5", "p6", "p7")) g = toggleVote(g, "n1", v)
        assertEquals(3 - 1 + 1, voteCount(g, g.nominations[0]))
        g = closeNomination(g, "n1")
        // 10 vivos: hacen falta 5 votos, y 3 no llegan.
        assertNull(onTheBlock(g))
    }

    @Test
    fun losRecordatoriosDelBurocrataYElLadronCaducanAlAnochecer() {
        val g = addReminder(game(tb), "p5", reminder("p5", "bureaucrat", "3 votos"))
        assertEquals(listOf("3 votos"), expiringReminders(g).map { it.reminder.label })
    }
}

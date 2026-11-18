package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.services.GameMessage
import es.dan15d.grimorio.domain.services.SeatSecret
import es.dan15d.grimorio.domain.services.addReminder
import es.dan15d.grimorio.domain.services.applyReminderChoice
import es.dan15d.grimorio.domain.services.buildNightOrder
import es.dan15d.grimorio.domain.services.closeNomination
import es.dan15d.grimorio.domain.services.demonBlocked
import es.dan15d.grimorio.domain.services.demonSuccession
import es.dan15d.grimorio.domain.services.execute
import es.dan15d.grimorio.domain.services.expiringReminders
import es.dan15d.grimorio.domain.services.infoHints
import es.dan15d.grimorio.domain.services.keepNightStep
import es.dan15d.grimorio.domain.services.killPlayer
import es.dan15d.grimorio.domain.services.nominate
import es.dan15d.grimorio.domain.services.publicPlayers
import es.dan15d.grimorio.domain.services.seatSecret
import es.dan15d.grimorio.domain.services.setNightStep
import es.dan15d.grimorio.domain.services.startDay
import es.dan15d.grimorio.domain.services.startNight
import es.dan15d.grimorio.domain.services.succeedDemon
import es.dan15d.grimorio.domain.services.toggleVote
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Fallos de la narración: protecciones que no deberían valer, la hoja de noche que salta de paso,
// lo que ve el móvil de noche y avisos que no cuadraban.

private val map = TestCatalog.map

private fun game(ids: List<String>, phase: Phase = Phase.night(2)): Game =
    testGame(ids, phase).copy(scriptId = "", scriptName = "")

private var next = 0
private fun reminder(source: String, label: String) = reminderOf("r${next++}", source, label)
private fun attack(g: Game, target: String, demon: String = "imp"): GameMessage =
    applyReminderChoice(g, target, reminder(demon, "Muerto"), map)

class NarrationTest {
    // ---------- Protecciones ----------

    @Test
    fun elASalvoDeUnMonjeQueEsElBorrachoNoProtege() {
        var g = game(listOf("imp", "poisoner", "drunk", "chef", "empath", "soldier", "washerwoman"))
            .withPlayer("p2") { it.copy(shownCharacterId = "monk") }
        g = addReminder(g, "p3", reminder("monk", "A salvo"))
        assertFalse(attack(g, "p3").game.players[3].alive)
    }

    @Test
    fun elDeUnMonjeEnvenenadoTampocoElDeUnMonjeSanoSi() {
        var g = game(listOf("imp", "poisoner", "monk", "chef", "empath", "soldier", "washerwoman"))
        g = addReminder(g, "p3", reminder("monk", "A salvo"))
        assertTrue(attack(g, "p3").game.players[3].alive)
        g = addReminder(g, "p2", reminder("poisoner", "Envenenado"))
        assertFalse(attack(g, "p3").game.players[3].alive)
    }

    @Test
    fun laDamaDelTeProtegeASusVecinosBuenosDelDemonioYDeLaEjecucion() {
        val g = game(listOf("imp", "poisoner", "chef", "tealady", "empath", "soldier", "washerwoman"))
        assertTrue(attack(g, "p2").game.players[2].alive)
        val executed = execute(g.copy(phase = Phase.day(2)), "p4")
        assertTrue(executed.players[4].alive)
        // Si un vecino es malo, no protege a nadie.
        val evilNeighbour = game(listOf("imp", "poisoner", "chef", "tealady", "spy", "soldier", "washerwoman"))
        assertFalse(attack(evilNeighbour, "p2").game.players[2].alive)
    }

    // ---------- El Demonio que no mata esta noche ----------

    @Test
    fun siElExorcistaLeHaElegidoElAtaqueNoMataYAvisa() {
        val g = addReminder(game(listOf("imp", "poisoner", "exorcist", "chef", "empath")), "p0", reminder("exorcist", "Elegido"))
        val result = attack(g, "p3")
        assertTrue(result.game.players[3].alive)
        assertContains(result.message, "Exorcista")
        assertContains(demonBlocked(g, "imp")!!, "Exorcista")
    }

    @Test
    fun siLaPrincesaEjecutaEnSuPrimerDiaEsaNocheElDemonioNoMataLaSiguienteSi() {
        var g = game(listOf("imp", "poisoner", "princess", "chef", "empath", "monk", "soldier"), Phase.day(1))
        g = nominate(g, "p2", "p3", "n1")
        for (v in listOf("p0", "p1", "p2", "p4")) g = toggleVote(g, "n1", v)
        g = execute(closeNomination(g, "n1"), "p3")
        // El aviso sobrevive al anochecer: es justo para esa noche.
        assertFalse(expiringReminders(g).any { it.reminder.label == "No mata" })
        g = startNight(g)
        assertTrue(attack(g, "p4").game.players[4].alive)
        g = startNight(startDay(g))
        assertFalse(attack(g, "p4").game.players[4].alive)
    }

    @Test
    fun atacarAUnMuertoNoDiceQueMuereEstaNoche() {
        val g = killPlayer(game(listOf("imp", "poisoner", "chef", "empath", "monk")), "p2")
        assertContains(attack(g, "p2").message, "ya estaba muerto")
    }

    // ---------- Hoja de noche ----------

    @Test
    fun siCambiaEnMitadDeLaNocheElNarradorSigueEnElMismoPaso() {
        var g = game(listOf("imp", "poisoner", "chef", "empath", "monk", "soldier", "washerwoman"))
        val impIndex = buildNightOrder(g, map).indexOfFirst { it.key == "char:imp" }
        g = g.copy(nightStep = impIndex)
        val before = attack(g, "p0").game
        val after = keepNightStep(before, succeedDemon(before, demonSuccession(before, map)!!, "p1", map), map)
        assertEquals("char:imp", buildNightOrder(after, map)[after.nightStep].key)
    }

    @Test
    fun moversePorLaHojaSigueFuncionando() {
        val g = game(listOf("imp", "poisoner", "chef", "empath", "monk"))
        assertEquals(2, keepNightStep(g, setNightStep(g, 2), map).nightStep)
    }

    // ---------- Lo que ve el jugador ----------

    @Test
    fun conTorQuienMuereDeNocheNoVeSuPersonajeHastaElAmanecer() {
        var g = killPlayer(game(listOf("imp", "poisoner", "chef", "empath", "monk")).copy(fabledIds = listOf("tor")), "p2")
        fun seen() = seatSecret(g.players[2], map, g.fabledIds, publicPlayers(g, map)[2].alive)
        assertEquals(SeatSecret(), seen())
        g = startDay(g)
        assertEquals("chef", seen().characterId)
    }

    // ---------- Información de la noche ----------

    @Test
    fun elEnterradorNoAprendeNadaSiElEjecutadoSobrevive() {
        val g = startNight(execute(game(listOf("imp", "poisoner", "sailor", "undertaker", "monk"), Phase.day(1)), "p2"))
        assertContains(infoHints(g, TestCatalog.get("undertaker")!!, map)[0].text, "no murió")
    }

    @Test
    fun paraLaPitonisaQuienCuidaALilMonstaEsElDemonio() {
        var g = game(listOf("poisoner", "spy", "fortuneteller", "chef", "empath")).copy(extraCharacterIds = listOf("lilmonsta"))
        g = addReminder(g, "p1", reminder("lilmonsta", "Es el Demonio"))
        assertContains(infoHints(g, TestCatalog.get("fortuneteller")!!, map)[0].text, "2. J2")
    }
}

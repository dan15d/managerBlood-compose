package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.services.NightStepKind
import es.dan15d.grimorio.domain.services.addReminder
import es.dan15d.grimorio.domain.services.applyReminderChoice
import es.dan15d.grimorio.domain.services.applySetupEffects
import es.dan15d.grimorio.domain.services.buildNightOrder
import es.dan15d.grimorio.domain.services.butcherCanNominate
import es.dan15d.grimorio.domain.services.callExile
import es.dan15d.grimorio.domain.services.canExile
import es.dan15d.grimorio.domain.services.canNominate
import es.dan15d.grimorio.domain.services.closeNomination
import es.dan15d.grimorio.domain.services.duskVictory
import es.dan15d.grimorio.domain.services.execute
import es.dan15d.grimorio.domain.services.killPlayer
import es.dan15d.grimorio.domain.services.nominate
import es.dan15d.grimorio.domain.services.onTheBlock
import es.dan15d.grimorio.domain.services.seededRandom
import es.dan15d.grimorio.domain.services.startDay
import es.dan15d.grimorio.domain.services.startNight
import es.dan15d.grimorio.domain.services.toggleVote
import es.dan15d.grimorio.domain.services.victoryHint
import es.dan15d.grimorio.domain.services.victoryNote
import es.dan15d.grimorio.domain.services.voteCount
import es.dan15d.grimorio.domain.services.voteThreshold
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val map = TestCatalog.map

private fun game(ids: List<String>): Game = testGame(ids, Phase.day(2))

private var next = 0
private fun reminder(source: String, label: String) = reminderOf("r${next++}", source, label)
private fun day(g: Game, number: Int): Game = g.copy(phase = Phase.day(number), executedToday = null, nominations = emptyList())
private fun kill(g: Game, target: String, demon: String = "imp"): Game =
    applyReminderChoice(g.night(2), target, reminder(demon, "Muerto"), map).game

/** Nomina, vota con los dados, cierra y devuelve la partida. */
private fun vote(g: Game, nominator: String, nominee: String, voters: List<String>): Game {
    val id = "n${next++}"
    var after = nominate(g, nominator, nominee, id)
    for (v in voters) after = toggleVote(after, id, v)
    return closeNomination(after, id)
}

class OtherEditionsTest {
    private val twinIds = listOf("imp", "eviltwin", "chef", "empath", "monk", "soldier", "washerwoman")
    private fun withTwin(g: Game) = addReminder(g, "p2", reminder("eviltwin", "Gemelo"))

    @Test
    fun gemelaMalvadaAlEmpezarSeMarcaAUnGemeloBueno() {
        var n = 0
        val players = applySetupEffects(game(twinIds).players, map, seededRandom(2)) { "t${n++}" }.players
        val twins = players.filter { p -> p.reminders.any { it.label == "Gemelo" } }
        assertEquals(1, twins.size)
        assertEquals(Alignment.Good, twins[0].alignment)
    }

    @Test
    fun gemelaMalvadaSiEjecutanAlGemeloBuenoGanaElMal() {
        assertEquals(Alignment.Evil, victoryHint(execute(withTwin(game(twinIds)), "p2"), map)?.winner)
    }

    @Test
    fun gemelaMalvadaMuertoElDemonioLosBuenosNoGananMientrasVivanLasDos() {
        val g = execute(withTwin(game(twinIds)), "p0")
        assertNull(victoryHint(g, map))
        assertContains(victoryNote(g, map)!!, "Gemela malvada")
        assertEquals(Alignment.Good, victoryHint(killPlayer(g, "p1"), map)?.winner)
    }

    private val mastermindIds = listOf("imp", "mastermind", "chef", "empath", "monk", "soldier", "washerwoman")

    @Test
    fun menteMaestraEjecutadoElDemonioSeJuegaUnDiaMasYSiSeEjecutaAUnBuenoGanaElMal() {
        var g = execute(game(mastermindIds), "p0", map)
        assertEquals(3, g.mastermindDay)
        assertNull(victoryHint(g, map))
        assertContains(victoryNote(g, map)!!, "Mente maestra")
        g = execute(day(g, 3), "p2", map)
        assertEquals(Alignment.Evil, victoryHint(g, map)?.winner)
    }

    @Test
    fun menteMaestraSiEseDiaSeEjecutaAUnMaloONoHayEjecucionGanaElBien() {
        val g = day(execute(game(mastermindIds), "p0", map), 3)
        assertEquals(Alignment.Good, victoryHint(execute(g, "p1", map), map)?.winner)
        assertEquals(Alignment.Good, duskVictory(g, map)?.winner)
    }

    @Test
    fun herejeInvierteElGanador() {
        val g = execute(game(listOf("imp", "poisoner", "heretic", "chef", "empath", "monk", "soldier")), "p0")
        assertEquals(Alignment.Evil, victoryHint(g, map)?.winner)
        assertContains(victoryHint(g, map)!!.reason, "Hereje")
    }

    private val sailorIds = listOf("imp", "devilsadvocate", "sailor", "fool", "chef", "empath", "monk")

    @Test
    fun elMarineroSanoNoMuerePorElDemonioNiEjecutadoBorrachoSi() {
        assertTrue(kill(game(sailorIds), "p2").players[2].alive)
        val executed = execute(game(sailorIds), "p2")
        assertTrue(executed.players[2].alive)
        assertEquals("p2", executed.executedToday)
        val drunk = addReminder(game(sailorIds), "p2", reminder("sailor", "Borracho"))
        assertFalse(kill(drunk, "p2").players[2].alive)
    }

    @Test
    fun elBufonSeSalvaLaPrimeraVezYPierdeSuHabilidad() {
        var g = kill(game(sailorIds), "p3")
        assertTrue(g.players[3].alive)
        assertContains(g.players[3].reminders.map { it.label }, "Sin habilidad")
        g = execute(day(g, 3), "p3")
        assertFalse(g.players[3].alive)
    }

    @Test
    fun elElegidoPorElAbogadoDelDiabloSobreviveALaEjecucion() {
        val g = addReminder(game(sailorIds), "p4", reminder("devilsadvocate", "Sobrevive ejecución"))
        assertTrue(execute(g, "p4").players[4].alive)
    }

    @Test
    fun abuelaSiElDemonioMataASuNietoEllaTambienMuere() {
        var g = game(listOf("imp", "poisoner", "grandmother", "chef", "empath", "monk", "soldier"))
        g = addReminder(g, "p3", reminder("grandmother", "Nieto"))
        g = kill(g, "p3")
        assertFalse(g.players[2].alive)
    }

    private val poppyIds = listOf("imp", "poisoner", "poppygrower", "chef", "empath", "monk", "soldier")
    private fun kinds(g: Game) = buildNightOrder(g, map).map { it.kind }

    @Test
    fun cultivadorDeOpioMientrasViveNoHayInformacionDelMalYLaNocheEnQueMuereSi() {
        assertFalse(NightStepKind.MinionInfo in kinds(game(poppyIds).night(1)))
        var g = killPlayer(day(game(poppyIds), 2), "p2")
        g = startNight(g)
        val steps = buildNightOrder(g, map)
        val poppy = steps.indexOfFirst { it.key == "char:poppygrower" }
        assertEquals(poppy + 1, steps.indexOfFirst { it.kind == NightStepKind.MinionInfo })
        // La noche siguiente ya no.
        assertFalse(NightStepKind.MinionInfo in kinds(startNight(startDay(g))))
    }

    @Test
    fun cultivadorDeOpioBorrachoOEnvenenadoNoImpideLaInformacionDeLaPrimeraNoche() {
        val g = addReminder(game(poppyIds).night(1), "p2", reminder("poisoner", "Envenenado"))
        assertTrue(NightStepKind.MinionInfo in kinds(g))
    }

    @Test
    fun carniceroTrasLaEjecucionPuedeNominarOtraVezYPuedeHaberUnaSegundaEjecucion() {
        val ids = listOf("imp", "poisoner", "butcher", "chef", "empath", "monk", "soldier")
        var g = execute(vote(game(ids), "p3", "p4", listOf("p0", "p1", "p2", "p3")), "p4")
        assertEquals("Ya ha habido una ejecución hoy.", canNominate(g, "p5", "p6"))
        assertTrue(butcherCanNominate(g))
        g = vote(g, "p2", "p1", listOf("p0", "p2", "p3"))
        assertEquals("p1", onTheBlock(g)?.playerId)
        g = execute(g, "p1")
        assertFalse(g.players[1].alive)
        assertNotNull(canNominate(g, "p2", "p6"))
    }

    @Test
    fun bansheeMuertaPorElDemonioNominaYVotaDosVecesAunqueEsteMuerta() {
        val ids = listOf("imp", "poisoner", "banshee", "chef", "empath", "monk", "soldier")
        var g = day(kill(game(ids), "p2"), 3)
        assertContains(g.players[2].reminders.map { it.label }, "Tiene habilidad")
        assertNull(canNominate(g, "p2", "p0"))
        g = vote(g, "p2", "p0", listOf("p2"))
        assertEquals(2, voteCount(g, g.nominations[0]))
        assertFalse(g.players[2].ghostVoteUsed)
        assertNull(canNominate(g, "p2", "p1"))
        g = vote(g, "p2", "p1", emptyList())
        assertContains(canNominate(g, "p2", "p3")!!, "ya ha nominado")
    }

    @Test
    fun voudonSoloVotanElYLosMuertosSinGastarSuVotoYBastaUnVoto() {
        var g = game(listOf("imp", "poisoner", "voudon", "chef", "empath", "monk", "soldier"))
        g = killPlayer(g, "p6")
        assertEquals(1, voteThreshold(g))
        val before = g
        val error = assertFailsWith<RuleViolation> { vote(before, "p3", "p1", listOf("p4")) }
        assertContains(error.message!!, "Voudon")
        g = vote(g, "p3", "p1", listOf("p6"))
        assertFalse(g.players[6].ghostVoteUsed)
        assertEquals("p1", onTheBlock(g)?.playerId)
        // Un empate no salva al primero: sigue marcado hasta que alguien tenga más votos.
        g = vote(g, "p4", "p0", listOf("p2"))
        assertEquals("p1", onTheBlock(g)?.playerId)
    }

    private val exileIds = listOf("imp", "poisoner", "chef", "empath", "monk", "soldier", "thief")

    @Test
    fun exilioVotanTodosHaceFaltaLaMitadDeTodosYNoEsUnaEjecucion() {
        var g = killPlayer(game(exileIds), "p5")
        g = callExile(g, "p6", "e1", map)
        for (v in listOf("p0", "p1", "p2", "p5")) g = toggleVote(g, "e1", v)
        g = closeNomination(g, "e1")
        assertFalse(g.players[6].alive)
        assertFalse(g.players[5].ghostVoteUsed)
        assertNull(g.executedToday)
        assertNull(canNominate(g, "p2", "p3"))
    }

    @Test
    fun exilioConPocosVotosNoSeExiliaYSoloSePuedePedirUnaVezAlDia() {
        var g = callExile(game(exileIds), "p6", "e1", map)
        g = closeNomination(toggleVote(g, "e1", "p0"), "e1")
        assertTrue(g.players[6].alive)
        assertContains(canExile(g, "p6", map)!!, "ya se ha pedido")
        assertContains(canExile(g, "p2", map)!!, "Viajeros")
    }

    @Test
    fun despuesDeEjecutarYaNoHayNadieEnLaPicota() {
        val ids = listOf("imp", "poisoner", "chef", "empath", "monk", "soldier", "washerwoman")
        val g = execute(vote(game(ids), "p2", "p3", listOf("p0", "p1", "p2", "p4")), "p3")
        assertNull(onTheBlock(g))
    }
}

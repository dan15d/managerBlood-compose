package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.services.addReminder
import es.dan15d.grimorio.domain.services.canNominate
import es.dan15d.grimorio.domain.services.closeNomination
import es.dan15d.grimorio.domain.services.execute
import es.dan15d.grimorio.domain.services.infoHints
import es.dan15d.grimorio.domain.services.killPlayer
import es.dan15d.grimorio.domain.services.mustVote
import es.dan15d.grimorio.domain.services.nominate
import es.dan15d.grimorio.domain.services.startNight
import es.dan15d.grimorio.domain.services.toggleVote
import es.dan15d.grimorio.domain.services.victoryHint
import es.dan15d.grimorio.domain.services.voteCount
import es.dan15d.grimorio.domain.services.voteIgnored
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val map = TestCatalog.map

private fun voteGame(ids: List<String>): Game = testGame(ids, Phase.day(1))

private var next = 0
private fun reminder(source: String, label: String) = reminderOf("r${next++}", source, label)

private fun vote(g: Game, nominator: String, nominee: String, voters: List<String>): Game {
    val id = "n${next++}"
    var after = nominate(g, nominator, nominee, id, map)
    for (v in voters) after = toggleVote(after, id, v)
    return closeNomination(after, id)
}

private fun hintAtNight(g: Game, id: String): String = infoHints(startNight(g), TestCatalog.get(id)!!, map)[0].text

class VotesTest {
    private val bmrSnv = listOf("vortox", "witch", "towncrier", "flowergirl", "dreamer", "oracle", "clockmaker")

    @Test
    fun elPregoneroSabeSiHaNominadoUnEsbirroHoy() {
        assertContains(hintAtNight(vote(voteGame(bmrSnv), "p1", "p4", emptyList()), "towncrier"), "Asiente")
        assertContains(hintAtNight(vote(voteGame(bmrSnv), "p3", "p4", emptyList()), "towncrier"), "Niega")
        assertContains(hintAtNight(voteGame(bmrSnv), "towncrier"), "Niega")
    }

    @Test
    fun laFloristaSabeSiElDemonioHaVotadoHoy() {
        assertContains(hintAtNight(vote(voteGame(bmrSnv), "p3", "p4", listOf("p0", "p2")), "flowergirl"), "Asiente")
        assertContains(hintAtNight(vote(voteGame(bmrSnv), "p3", "p4", listOf("p1", "p2")), "flowergirl"), "Niega")
    }

    // ---------- Mayordomo ----------

    private val butlerIds = listOf("imp", "poisoner", "butler", "chef", "empath", "monk", "soldier")
    private fun withMaster(g: Game) = addReminder(g, "p3", reminder("butler", "Señor"))

    @Test
    fun elVotoDelMayordomoNoCuentaSiSuSenorNoVotaSiVotaSi() {
        val alone = vote(withMaster(voteGame(butlerIds)), "p4", "p1", listOf("p2", "p5"))
        assertEquals(1, voteCount(alone, alone.nominations[0]))
        assertContains(voteIgnored(alone, alone.nominations[0], alone.players[2])!!, "Mayordomo")
        val together = vote(withMaster(voteGame(butlerIds)), "p4", "p1", listOf("p2", "p3"))
        assertEquals(2, voteCount(together, together.nominations[0]))
    }

    @Test
    fun elMayordomoBorrachoOEnvenenadoVotaLibre() {
        val g = addReminder(withMaster(voteGame(butlerIds)), "p2", reminder("poisoner", "Envenenado"))
        val voted = vote(g, "p4", "p1", listOf("p2"))
        assertEquals(1, voteCount(voted, voted.nominations[0]))
    }

    // ---------- Fanático ----------

    @Test
    fun elFanaticoDebeVotarSiHay5OMasVivos() {
        val g = voteGame(listOf("imp", "poisoner", "zealot", "chef", "empath", "monk"))
        assertTrue(mustVote(g, g.players[2]))
        val fewer = killPlayer(killPlayer(g, "p4"), "p5")
        assertFalse(mustVote(fewer, fewer.players[2]))
    }

    // ---------- Bruja ----------

    private val witchIds = listOf("imp", "witch", "chef", "empath", "monk", "soldier", "washerwoman")

    @Test
    fun elMalditoDeLaBrujaMuereAlNominar() {
        val g = nominate(addReminder(voteGame(witchIds), "p2", reminder("witch", "Maldito")), "p2", "p3", "n1", map)
        assertFalse(g.players[2].alive)
        assertEquals(1, g.nominations.size)
    }

    @Test
    fun laBrujaCon3VivosPierdeLaHabilidad() {
        var g = addReminder(voteGame(witchIds), "p2", reminder("witch", "Maldito"))
        for (id in listOf("p4", "p5", "p6", "p3")) g = killPlayer(g, id)
        assertTrue(nominate(g, "p2", "p0", "n1", map).players[2].alive)
    }

    // ---------- Gólem ----------

    private val golemIds = listOf("imp", "poisoner", "golem", "chef", "empath", "monk", "soldier")

    @Test
    fun siElNominadoPorElGolemNoEsElDemonioMuereYSoloNominaUnaVez() {
        var g = nominate(voteGame(golemIds), "p2", "p3", "n1", map)
        assertFalse(g.players[3].alive)
        g = g.copy(nominations = emptyList(), phase = Phase.day(2))
        assertContains(canNominate(g, "p2", "p4")!!, "Gólem")
    }

    @Test
    fun siElGolemNominaAlDemonioNoPasaNada() {
        assertTrue(nominate(voteGame(golemIds), "p2", "p0", "n1", map).players[0].alive)
    }

    // ---------- Fearmonger ----------

    private val fearIds = listOf("imp", "fearmonger", "chef", "empath", "monk", "soldier", "washerwoman")

    @Test
    fun siElFearmongerNominaASuElegidoYSeLeEjecutaSuEquipoPierde() {
        var g = addReminder(voteGame(fearIds), "p2", reminder("fearmonger", "Terror"))
        g = execute(vote(g, "p1", "p2", listOf("p0", "p1", "p3", "p4")), "p2")
        assertEquals(Alignment.Evil, victoryHint(g, map)?.winner)
    }

    @Test
    fun siNominaOtroNoGanaNadie() {
        var g = addReminder(voteGame(fearIds), "p2", reminder("fearmonger", "Terror"))
        g = execute(vote(g, "p3", "p2", listOf("p0", "p1", "p3", "p4")), "p2")
        assertNull(victoryHint(g, map))
    }
}

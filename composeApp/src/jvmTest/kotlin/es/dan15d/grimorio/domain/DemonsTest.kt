package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.services.Distribution
import es.dan15d.grimorio.domain.services.GameMessage
import es.dan15d.grimorio.domain.services.NightStepKind
import es.dan15d.grimorio.domain.services.addReminder
import es.dan15d.grimorio.domain.services.allowedDistributions
import es.dan15d.grimorio.domain.services.applyReminderChoice
import es.dan15d.grimorio.domain.services.applySetupEffects
import es.dan15d.grimorio.domain.services.buildNightOrder
import es.dan15d.grimorio.domain.services.choiceReminder
import es.dan15d.grimorio.domain.services.demonSuccession
import es.dan15d.grimorio.domain.services.duskVictory
import es.dan15d.grimorio.domain.services.execute
import es.dan15d.grimorio.domain.services.expiringReminders
import es.dan15d.grimorio.domain.services.infoHints
import es.dan15d.grimorio.domain.services.killPlayer
import es.dan15d.grimorio.domain.services.publicPlayers
import es.dan15d.grimorio.domain.services.randomSelection
import es.dan15d.grimorio.domain.services.seededRandom
import es.dan15d.grimorio.domain.services.startDay
import es.dan15d.grimorio.domain.services.startNight
import es.dan15d.grimorio.domain.services.succeedDemon
import es.dan15d.grimorio.domain.services.validateSelection
import es.dan15d.grimorio.domain.services.victoryHint
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Demonios cuyas reglas cambian quién muere, quién gana o cómo se prepara la partida.

private val map = TestCatalog.map

private fun game(ids: List<String>, phase: Phase = Phase.night(2)): Game =
    testGame(ids, phase).copy(scriptId = "", scriptName = "")

private var next = 0
private fun reminder(source: String, label: String) = reminderOf("r${next++}", source, label)
private fun choose(g: Game, target: String, source: String, label: String): GameMessage =
    applyReminderChoice(g, target, reminder(source, label), map)
private fun day(g: Game, number: Int): Game = g.copy(phase = Phase.day(number), executedToday = null)

class DemonsTest {
    private val pukkaIds = listOf("pukka", "poisoner", "chef", "empath", "monk", "soldier", "washerwoman")

    @Test
    fun pukkaLaVictimaAnteriorMuereYQuedaSanaSoloHayUnEnvenenadoVivo() {
        var g = choose(game(pukkaIds).night(1), "p2", "pukka", "Envenenado").game
        assertTrue(g.players[2].alive)
        g = choose(g.night(2), "p3", "pukka", "Envenenado").game
        assertFalse(g.players[2].alive)
        assertEquals(emptyList(), g.players[2].reminders)
        assertEquals(listOf("Envenenado"), g.players[3].reminders.map { it.label })
        g = choose(g.night(3), "p6", "pukka", "Envenenado").game
        assertFalse(g.players[3].alive)
        assertEquals(listOf("p6"), g.players.filter { p -> p.reminders.any { it.sourceCharacterId == "pukka" } }.map { it.id })
    }

    @Test
    fun pukkaSiLaVictimaAnteriorEstaProtegidaNoMuereYQuedaSana() {
        var g = choose(game(pukkaIds).night(1), "p2", "pukka", "Envenenado").game
        // El Monje está en juego y sano: su «A salvo» protege.
        g = addReminder(g.night(2), "p2", reminder("monk", "A salvo"))
        val result = choose(g, "p3", "pukka", "Envenenado")
        assertTrue(result.game.players[2].alive)
        assertFalse(result.game.players[2].reminders.any { it.sourceCharacterId == "pukka" })
        assertContains(result.message, "protegido")
    }

    @Test
    fun fangGuSaltaAlPrimerForasteroQueEligeElSiguienteMuereSinMas() {
        val ids = listOf("fanggu", "butler", "recluse", "chef", "poisoner", "empath", "monk")
        var g = choose(game(ids), "p1", "fanggu", "Muerto").game
        assertFalse(g.players[0].alive)
        assertEquals("fanggu", g.players[1].characterId)
        assertEquals(Alignment.Evil, g.players[1].alignment)
        assertTrue(g.players[1].alive)
        assertNull(victoryHint(g, map))
        g = choose(g.night(3), "p2", "fanggu", "Muerto").game
        assertEquals("recluse", g.players[2].characterId)
        assertFalse(g.players[2].alive)
    }

    @Test
    fun zombuulLaPrimeraVezPareceMuertoYLaSegundaMuereDeVerdad() {
        val ids = listOf("zombuul", "poisoner", "chef", "empath", "monk", "soldier", "washerwoman")
        var g = execute(day(game(ids), 2), "p0")
        assertFalse(g.players[0].alive)
        assertFalse(publicPlayers(g, map)[0].alive)
        assertNull(victoryHint(g, map))
        assertNull(demonSuccession(g, map))
        // Esa noche no ataca: hoy ha muerto alguien (él mismo, en apariencia).
        val n = startNight(g)
        assertContains(infoHints(n, TestCatalog.get("zombuul")!!, map)[0].text, "no despiertes")
        assertEquals(false, buildNightOrder(n, map).find { it.key == "char:zombuul" }?.dead)
        g = execute(startDay(n), "p0")
        assertEquals(Alignment.Good, victoryHint(g, map)?.winner)
    }

    @Test
    fun zombuulUnDiaSinMuertesDespiertaParaAtacar() {
        val n = startNight(day(game(listOf("zombuul", "poisoner", "chef", "empath", "monk")), 1))
        assertContains(infoHints(n, TestCatalog.get("zombuul")!!, map)[0].text, "despierta al Zombuul")
    }

    @Test
    fun lleechEnvenenaASuHuespedNoMuereMientrasVivaYMuereConEl() {
        assertEquals("Envenenado", choiceReminder("lleech", true))
        assertEquals("Muerto", choiceReminder("lleech", false))
        val ids = listOf("lleech", "poisoner", "chef", "empath", "monk", "soldier", "washerwoman")
        var g = choose(game(ids).night(1), "p2", "lleech", "Envenenado").game
        assertTrue(g.players[2].alive)
        g = execute(day(g, 1), "p0")
        assertTrue(g.players[0].alive)
        assertNull(victoryHint(g, map))
        g = killPlayer(g, "p2")
        assertFalse(g.players[0].alive)
        assertEquals(Alignment.Good, victoryHint(g, map)?.winner)
    }

    @Test
    fun lilMonstaNoVaEnLaBolsaYSuHuecoLoOcupaUnEsbirro() {
        assertEquals(listOf(Distribution(5, 0, 2, 0)), allowedDistributions(7, TestCatalog.chars(listOf("lilmonsta"))))
        assertEquals(
            emptyList(),
            validateSelection(7, TestCatalog.chars(listOf("lilmonsta", "poisoner", "spy", "chef", "empath", "monk", "soldier", "washerwoman"))),
        )
        val tb = TestCatalog.script("trouble-brewing")
        val pool = TestCatalog.chars(tb.characterIds).filter { it.team != Team.Demon && it.team != Team.Traveller } + TestCatalog.get("lilmonsta")!!
        val bag = randomSelection(8, pool, seededRandom(4))
        assertEquals(8, bag.filter { it.id != "lilmonsta" }.size)
    }

    @Test
    fun lilMonstaDespiertaALosEsbirrosEligenQuienLoCuidaYSiMuereGananLosBuenos() {
        val ids = listOf("poisoner", "spy", "chef", "empath", "monk", "soldier", "washerwoman")
        var g = game(ids, Phase.night(1)).copy(extraCharacterIds = listOf("lilmonsta"))
        val steps = buildNightOrder(g, map)
        assertEquals(listOf("p0", "p1"), steps.find { it.key == "char:lilmonsta" }?.playerIds)
        assertFalse(steps.any { it.kind == NightStepKind.MinionInfo })
        assertEquals("Es el Demonio", choiceReminder("lilmonsta", true))
        g = choose(g, "p1", "lilmonsta", "Es el Demonio").game
        g = execute(day(g, 1), "p1")
        assertEquals(Alignment.Good, victoryHint(g, map)?.winner)
    }

    @Test
    fun lilMonstaConLaMujerEscarlataVivaPasaACuidarloElla() {
        val ids = listOf("scarletwoman", "spy", "chef", "empath", "monk", "soldier", "washerwoman")
        var g = game(ids, Phase.night(1)).copy(extraCharacterIds = listOf("lilmonsta"))
        g = choose(g, "p1", "lilmonsta", "Es el Demonio").game
        g = execute(day(g, 1), "p1")
        val s = demonSuccession(g, map)!!
        assertTrue(s.babysitter)
        g = succeedDemon(g, s, "p0", map)
        assertEquals("scarletwoman", g.players[0].characterId)
        assertContains(g.players[0].reminders.map { it.label }, "Es el Demonio")
        assertNull(victoryHint(g, map))
    }

    @Test
    fun conVortoxUnDiaSinEjecucionGanaElMal() {
        val g = day(game(listOf("vortox", "poisoner", "chef", "empath", "monk")), 2)
        assertEquals(Alignment.Evil, duskVictory(g, map)?.winner)
        assertNull(duskVictory(execute(g, "p2"), map))
    }

    @Test
    fun leviatanGanaAlTerminarElDia5OConElSegundoBuenoEjecutado() {
        val ids = listOf("leviathan", "poisoner", "chef", "empath", "monk", "soldier", "washerwoman")
        assertNull(duskVictory(day(game(ids), 4), map))
        assertEquals(Alignment.Evil, duskVictory(day(game(ids), 5), map)?.winner)
        var g = execute(day(game(ids), 1), "p2")
        assertNull(victoryHint(g, map))
        g = execute(day(g, 2), "p3")
        assertEquals(Alignment.Evil, victoryHint(g, map)?.winner)
    }

    @Test
    fun shabalothSuMuertoNoCaducaAlAnochecerYVivoRegurgitaAlJugador() {
        var g = choose(game(listOf("shabaloth", "poisoner", "chef", "empath", "monk", "soldier", "washerwoman")), "p2", "shabaloth", "Muerto").game
        assertFalse(g.players[2].alive)
        assertEquals(emptyList(), expiringReminders(g))
        g = choose(g.night(3), "p2", "shabaloth", "Vivo").game
        assertTrue(g.players[2].alive)
        assertEquals(listOf("Vivo"), g.players[2].reminders.map { it.label })
    }

    @Test
    fun noDashiiEnvenenaDesdeElPrincipioAlAldeanoMasCercanoACadaLado() {
        // Asientos: Chef, Mayordomo, No Dashii, Envenenador, Recluso, Empático, Monje.
        val g = game(listOf("chef", "butler", "nodashii", "poisoner", "recluse", "empath", "monk"))
        var n = 0
        val players = applySetupEffects(g.players, map, seededRandom(1)) { "n${n++}" }.players
        val poisoned = players.filter { p -> p.reminders.any { it.sourceCharacterId == "nodashii" } }.map { it.characterId }
        assertEquals(listOf("chef", "empath"), poisoned.sorted())
    }
}

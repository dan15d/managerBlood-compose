package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.services.GameMessage
import es.dan15d.grimorio.domain.services.addReminder
import es.dan15d.grimorio.domain.services.applyReminderChoice
import es.dan15d.grimorio.domain.services.applySetupEffects
import es.dan15d.grimorio.domain.services.buildNightOrder
import es.dan15d.grimorio.domain.services.choiceReminder
import es.dan15d.grimorio.domain.services.demonSuccession
import es.dan15d.grimorio.domain.services.execute
import es.dan15d.grimorio.domain.services.expiringReminders
import es.dan15d.grimorio.domain.services.infoHints
import es.dan15d.grimorio.domain.services.killPlayer
import es.dan15d.grimorio.domain.services.philosopherChoose
import es.dan15d.grimorio.domain.services.pitHagChoose
import es.dan15d.grimorio.domain.services.publicAlive
import es.dan15d.grimorio.domain.services.publicPlayers
import es.dan15d.grimorio.domain.services.revivePlayer
import es.dan15d.grimorio.domain.services.seededRandom
import es.dan15d.grimorio.domain.services.slayerShot
import es.dan15d.grimorio.domain.services.startDay
import es.dan15d.grimorio.domain.services.startNight
import es.dan15d.grimorio.domain.services.succeedDemon
import es.dan15d.grimorio.domain.services.victoryHint
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val map = TestCatalog.map

private fun game(ids: List<String>, phase: Phase = Phase.night(2)): Game =
    testGame(ids, phase).copy(scriptId = "", scriptName = "")

private var ids = 0
private fun r(source: String, label: String) = reminderOf("$source-$label-${ids++}", source, label)
private fun choose(g: Game, target: String, source: String, label: String): GameMessage =
    applyReminderChoice(g, target, r(source, label), map)
private fun alive(g: Game, id: String) = g.player(id).alive

// Asientos: Lunático, Pukka, Abuela, Asesino, Marinero, Posadero, Exorcista, Dama del té.
private fun bmr() = game(listOf("lunatic", "pukka", "grandmother", "assassin", "sailor", "innkeeper", "exorcist", "tealady"))

// Asientos: Exterminador, Diablillo, Mujer escarlata, Recluso, Chef, Empático.
private fun tb() = game(listOf("slayer", "imp", "scarletwoman", "recluse", "chef", "empath"), Phase.day(1))

class ScriptsPlaytestTest {
    // ---------- Bad Moon Rising ----------

    @Test
    fun pukkaEnvenenaPrimeroAsiQueLaDamaDelTeQueEligeYaNoProtegeASuVecino() {
        var g = addReminder(bmr(), "p6", r("pukka", "Envenenado"))
        // Si envenena a otro, la Dama del té sana protege a su vecino (J7).
        val saved = choose(g, "p5", "pukka", "Envenenado")
        assertTrue(alive(saved.game, "p6"))
        assertContains(saved.message, "protegido")
        // Si envenena a la Dama del té, el vecino muere.
        g = choose(g, "p7", "pukka", "Envenenado").game
        assertFalse(alive(g, "p6"))
        assertTrue(g.players[7].reminders.any { it.sourceCharacterId == "pukka" })
        assertFalse(g.players[6].reminders.any { it.sourceCharacterId == "pukka" })
    }

    @Test
    fun pukkaQueEligeOtraVezAlMismoMuereYSigueEnvenenado() {
        var g = addReminder(bmr(), "p2", r("pukka", "Envenenado"))
        g = choose(g, "p2", "pukka", "Envenenado").game
        assertFalse(alive(g, "p2"))
        assertEquals(1, g.players[2].reminders.count { it.sourceCharacterId == "pukka" })
    }

    @Test
    fun asesinoMataAunqueEsteProtegidoUnaVezPorPartida() {
        var g = choose(bmr(), "p2", "innkeeper", "A salvo").game
        val first = choose(g, "p2", "assassin", "Muerto")
        assertFalse(alive(first.game, "p2"))
        assertContains(first.message, "aunque estuviera protegido")
        // El Marinero tampoco se salva, y la segunda vez ya no tiene habilidad.
        g = choose(first.game, "p4", "assassin", "Muerto").game
        assertTrue(alive(g, "p4"))
    }

    @Test
    fun asesinoBorrachoOEnvenenadoNoMataPeroGastaLaHabilidad() {
        val g = addReminder(bmr(), "p3", r("sailor", "Borracho"))
        val result = choose(g, "p2", "assassin", "Muerto")
        assertTrue(alive(result.game, "p2"))
        assertContains(result.message, "no tiene habilidad")
    }

    @Test
    fun padrinoLeParanElPosaderoYLaDamaDelTeNoElMonje() {
        val g = game(listOf("godfather", "imp", "monk", "innkeeper", "fool", "chef", "tealady", "soldier"))
        assertFalse(alive(choose(choose(g, "p3", "monk", "A salvo").game, "p3", "godfather", "Muerto").game, "p3"))
        assertTrue(alive(choose(choose(g, "p2", "innkeeper", "A salvo").game, "p2", "godfather", "Muerto").game, "p2"))
        // J6 (Chef) y J8 (Soldado) son los vecinos buenos de la Dama del té.
        assertTrue(alive(choose(g, "p5", "godfather", "Muerto").game, "p5"))
        assertTrue(alive(choose(g, "p7", "godfather", "Muerto").game, "p7"))
        val fool = choose(g, "p4", "godfather", "Muerto")
        assertTrue(alive(fool.game, "p4"))
        assertContains(fool.message, "Bufón")
    }

    @Test
    fun elLunaticoSoloSeDespiertaEnSuPasoNoTambienEnElDelDemonioQueCreeSer() {
        val g = bmr().withPlayer("p0") { it.copy(shownCharacterId = "zombuul") }
        val keys = buildNightOrder(g, map).map { it.key }
        assertContains(keys, "char:lunatic")
        assertFalse("char:zombuul" in keys)
        // El Borracho, en cambio, sí despierta como el que cree ser.
        val drunk = game(listOf("drunk", "imp", "poisoner", "chef", "empath")).withPlayer("p0") { it.copy(shownCharacterId = "monk") }
        assertContains(buildNightOrder(drunk, map).map { it.key }, "char:monk")
    }

    // ---------- Habilidades de Bad Moon Rising y Sects & Violets ----------

    @Test
    fun unDemonioBorrachoOEnvenenadoNoMata() {
        val g = addReminder(game(listOf("imp", "sailor", "poisoner", "chef", "empath")), "p0", r("sailor", "Borracho"))
        val result = choose(g, "p3", "imp", "Muerto")
        assertTrue(alive(result.game, "p3"))
        assertContains(result.message, "borracho o envenenado")
    }

    @Test
    fun matonElPrimeroQueLeEligeQuedaBorrachoYElMatonPasaASuBandoElSegundoNo() {
        val g = game(listOf("imp", "goon", "poisoner", "chef", "empath"))
        val first = choose(g, "p1", "imp", "Muerto")
        assertTrue(alive(first.game, "p1"))
        assertEquals(Alignment.Evil, first.game.players[1].alignment)
        assertTrue(first.game.players[0].reminders.any { it.sourceCharacterId == "goon" && it.label == "Borracho" })
        assertContains(first.message, "es el Matón")
        // Ya ha elegido alguien esta noche: el Envenenador le envenena sin más.
        val second = choose(first.game, "p1", "poisoner", "Envenenado")
        assertFalse(second.game.players[2].reminders.any { it.sourceCharacterId == "goon" })
    }

    @Test
    fun profesorResucitaAUnAldeanoMuertoUnaVez() {
        var g = killPlayer(killPlayer(game(listOf("professor", "imp", "poisoner", "chef", "empath")), "p3"), "p2")
        assertContains(choose(g, "p2", "professor", "Vivo").message, "no es Aldeano")
        val revived = choose(g, "p3", "professor", "Vivo")
        assertTrue(alive(revived.game, "p3"))
        g = killPlayer(revived.game, "p4")
        assertFalse(alive(choose(g, "p4", "professor", "Vivo").game, "p4"))
    }

    @Test
    fun unaResurreccionDeNocheNoLaVenLosMovilesHastaElAmanecer() {
        var g = killPlayer(game(listOf("professor", "imp", "poisoner", "chef", "empath"), Phase.day(1)), "p3")
        g = g.withPlayer("p3") { it.copy(ghostVoteUsed = true) }
        g = startNight(g)
        g = choose(g, "p3", "professor", "Vivo").game
        assertTrue(alive(g, "p3"))
        assertFalse(publicAlive(g, g.players[3]))
        assertTrue(publicPlayers(g, map)[3].ghostVoteUsed)
        // Si vuelve a morir esa noche, para la mesa no ha pasado nada.
        val again = killPlayer(g, "p3")
        assertFalse("p3" in again.nightDeaths)
        assertFalse(publicAlive(again, again.players[3]))
        // Al amanecer ya se ve vivo, con su voto.
        g = startDay(g)
        assertTrue(publicAlive(g, g.players[3]))
        assertFalse(g.players[3].ghostVoteUsed)
        assertContains(g.log.last().text, "Vuelve a la vida: J4")
        // Quien muere y revive en la misma noche (el Narrador lo corrige) no cambia para la mesa.
        var h = startNight(startDay(startNight(game(listOf("imp", "poisoner", "chef", "empath", "monk")).copy(phase = Phase.day(1)))))
        h = revivePlayer(killPlayer(h, "p2"), "p2")
        assertEquals(emptyList(), h.nightRevivals)
        assertTrue(publicAlive(h, h.players[2]))
    }

    @Test
    fun ninaDeLaLunaSuElegidoMuereSiEsBuenoYSuPasoNoSeSaltaPorEstarMuerta() {
        var g = killPlayer(game(listOf("moonchild", "imp", "poisoner", "chef", "empath")), "p0")
        assertEquals(false, buildNightOrder(g, map).find { it.key == "char:moonchild" }?.dead)
        assertTrue(alive(choose(g, "p2", "moonchild", "Muerto").game, "p2"))
        g = choose(g, "p3", "moonchild", "Muerto").game
        assertFalse(alive(g, "p3"))
        assertEquals(true, buildNightOrder(g, map).find { it.key == "char:moonchild" }?.dead)
    }

    @Test
    fun juglarSiSeEjecutaAUnEsbirroTodosLosDemasEstanBorrachosHastaElCrepusculoDeManana() {
        var g = game(listOf("minstrel", "imp", "poisoner", "chef", "empath"), Phase.day(1))
        g = execute(g, "p2", map)
        fun drunk(x: Game) = x.players.filter { p -> p.reminders.any { it.sourceCharacterId == "minstrel" } }.map { it.id }
        assertEquals(listOf("p1", "p2", "p3", "p4"), drunk(g))
        // Ese crepúsculo aún no caduca.
        assertFalse(expiringReminders(g).any { it.reminder.sourceCharacterId == "minstrel" })
        g = startNight(g)
        assertContains(infoHints(g, TestCatalog.get("empath")!!, map)[0].caveats.joinToString(" "), "Juglar")
        // El Demonio borracho no mata.
        assertTrue(alive(choose(g, "p3", "imp", "Muerto").game, "p3"))
        // Al día siguiente caduca en el crepúsculo.
        g = startDay(g)
        assertEquals(4, expiringReminders(g).count { it.reminder.sourceCharacterId == "minstrel" })
    }

    @Test
    fun cortesanoUnaSolaVezElPadrinoNoMataLaPrimeraNoche() {
        val g = choose(game(listOf("courtier", "imp", "godfather", "chef", "empath")), "p1", "courtier", "Borracho 1").game
        assertTrue(alive(choose(g, "p3", "imp", "Muerto").game, "p3"))
        assertContains(choose(g, "p4", "courtier", "Borracho 1").message, "ya usó")
        assertNull(choiceReminder("godfather", true))
        assertEquals("Muerto", choiceReminder("godfather", false))
    }

    @Test
    fun filosofoGanaLaHabilidadElegidaDespiertaComoEsePersonajeYEmborrachaAQuienLoTiene() {
        val g = game(listOf("philosopher", "imp", "witch", "dreamer", "empath"), Phase.night(1))
        val result = philosopherChoose(g, "p0", "dreamer", map)
        assertEquals("dreamer", result.game.players[0].characterId)
        assertEquals(Alignment.Good, result.game.players[0].alignment)
        assertTrue(result.game.players[0].reminders.any { it.label == "Es el Filósofo" })
        assertTrue(result.game.players[3].reminders.any { it.sourceCharacterId == "philosopher" && it.label == "Borracho" })
        assertEquals(listOf("p0", "p3"), buildNightOrder(result.game, map).find { it.key == "char:dreamer" }?.playerIds)
        assertFailsWith<Exception> { philosopherChoose(g, "p0", "witch", map) }
    }

    @Test
    fun pitHagConvierteAUnJugadorConservaSuBandoSalvoEnUnPersonajeEnJuego() {
        val g = game(listOf("pithag", "vortox", "dreamer", "empath", "oracle"))
        assertSame(g, choose(g, "p2", "pithag", "Elegido").game)
        assertContains(pitHagChoose(g, "p2", "empath", map).message, "ya está en juego")
        val demon = pitHagChoose(g, "p2", "nodashii", map)
        assertEquals("nodashii", demon.game.players[2].characterId)
        assertEquals(Alignment.Good, demon.game.players[2].alignment)
        assertContains(demon.message, "las muertes de esta noche las decides tú")
        val poisoned = addReminder(g, "p0", r("custom", "Envenenado"))
        assertEquals("dreamer", pitHagChoose(poisoned, "p2", "sage", map).game.players[2].characterId)
        assertNull(choiceReminder("pithag", true))
    }

    @Test
    fun sirvientaQuienHaDespertadoEstaNochePorSuHabilidad() {
        val g = game(listOf("chambermaid", "imp", "poisoner", "chef", "drunk", "saint"))
            .withPlayer("p4") { it.copy(shownCharacterId = "monk") }
        val hint = infoHints(g, TestCatalog.get("chambermaid")!!, map)[0].text
        assertContains(hint, "J2, J3")
        assertFalse(hint.contains("J5"))
        assertFalse(hint.contains("J4"))
    }

    @Test
    fun vigormortisElEsbirroQueMataConservaSuHabilidadYSigueDespertandose() {
        val g = game(listOf("vigormortis", "chef", "witch", "empath", "oracle"))
        val result = choose(g, "p2", "vigormortis", "Muerto")
        assertFalse(alive(result.game, "p2"))
        assertContains(result.message, "conserva su habilidad")
        assertContains(result.message, "J2 o J4")
        assertEquals(false, buildNightOrder(result.game, map).find { it.key == "char:witch" }?.dead)
    }

    @Test
    fun tahurAlTocarAQuienEligeDiceSuPersonaje() {
        val g = game(listOf("gambler", "imp", "poisoner", "chef", "empath"))
        val result = choose(g, "p3", "gambler", "Elegido")
        assertContains(result.message, "J4 es Chef. Si el Tahúr no ha acertado, muere")
        assertSame(g, result.game)
    }

    // ---------- Sects & Violets ----------

    @Test
    fun gemelaMalvadaLaPrimeraNocheDiceQuienEsSuGemelo() {
        var g = game(listOf("eviltwin", "fanggu", "klutz", "clockmaker", "oracle"), Phase.night(1))
        fun hint() = infoHints(g, TestCatalog.get("eviltwin")!!, map).firstOrNull()?.text
        assertContains(hint()!!, "Falta")
        g = addReminder(g, "p4", r("eviltwin", "Gemelo"))
        assertContains(hint()!!, "El gemelo bueno es J5 (Oráculo)")
    }

    // ---------- Trouble Brewing ----------

    @Test
    fun exterminadorSiDisparaAlDemonioMuereYNoEsUnaEjecucion() {
        val g = game(listOf("slayer", "imp", "poisoner", "chef", "empath"), Phase.day(1))
        val shot = slayerShot(g, "p0", "p1", map)
        assertFalse(alive(shot.game, "p1"))
        assertContains(shot.message, "es el Demonio y muere")
        assertNull(shot.game.executedToday)
        assertEquals(Alignment.Good, victoryHint(shot.game, map)?.winner)
    }

    @Test
    fun exterminadorConLaMujerEscarlataY5VivosEllaPasaASerElDemonio() {
        val shot = slayerShot(tb(), "p0", "p1", map)
        assertEquals(listOf("p2"), demonSuccession(shot.game, map)?.heirs?.map { it.id })
    }

    @Test
    fun laMujerEscarlataQuePasaASerElDemonioDeDiaEsaNocheSeAvisaDeDespertarla() {
        val shot = slayerShot(tb(), "p0", "p1", map)
        val s = demonSuccession(shot.game, map)!!
        var g = startNight(succeedDemon(shot.game, s, "p2", map))
        val hint = infoHints(g, TestCatalog.get("imp")!!, map).find { it.playerId == "p2" }?.text
        assertContains(hint!!, "J3 se ha convertido hoy en Diablillo: despiértale y enséñale TÚ ERES")
        // Al amanecer ya se le ha dicho.
        g = startDay(g)
        assertFalse(g.players[2].reminders.any { it.label == "Nuevo Demonio" })
    }

    @Test
    fun losAvisosDeEspiaYReclusoConcuerdan() {
        val g = game(listOf("washerwoman", "imp", "spy", "recluse", "chef"), Phase.night(1))
        assertEquals("Espía y Recluso pueden aparecer distintos: decides tú.", infoHints(g, TestCatalog.get("washerwoman")!!, map)[0].caveats[0])
    }

    @Test
    fun exterminadorFallaConQuienNoEsDemonioAvisaDelReclusoYSoloDisparaUnaVez() {
        var g = slayerShot(tb(), "p0", "p3", map)
        assertContains(g.message, "Recluso puede registrarse como Demonio")
        assertTrue(alive(g.game, "p3"))
        g = slayerShot(g.game, "p0", "p1", map)
        assertContains(g.message, "ya había disparado")
        assertTrue(alive(g.game, "p1"))
    }

    @Test
    fun exterminadorQuienNoLoEsOLoEsEnvenenadoNoMata() {
        assertTrue(alive(slayerShot(tb(), "p4", "p1", map).game, "p1"))
        val poisoned = addReminder(tb(), "p0", r("poisoner", "Envenenado"))
        val shot = slayerShot(poisoned, "p0", "p1", map)
        assertTrue(alive(shot.game, "p1"))
        assertContains(slayerShot(shot.game, "p0", "p1", map).message, "ya había disparado")
    }

    @Test
    fun pitonisaElFalsoPositivoSePoneSoloEnUnJugadorBueno() {
        for (seed in 1..20) {
            val players = game(listOf("fortuneteller", "imp", "poisoner", "chef", "empath")).players
            val (placed, log) = applySetupEffects(players, map, seededRandom(seed)) { "id$seed" }
            val herring = placed.filter { p -> p.reminders.any { it.sourceCharacterId == "fortuneteller" && it.label == "Falso positivo" } }
            assertEquals(1, herring.size)
            assertEquals(Alignment.Good, herring[0].alignment)
            assertContains(log.joinToString(" "), "falso positivo de la Pitonisa")
        }
    }

    // ---------- Textos del catálogo ----------

    @Test
    fun losTextosDelCatalogoNoTienenHtml() {
        val html = Regex("</?[a-z][^>]*>", RegexOption.IGNORE_CASE)
        for (c in TestCatalog.all) {
            for (text in listOf(c.ability, c.firstNightReminder, c.otherNightReminder)) assertFalse(html.containsMatchIn(text), c.id)
        }
    }
}

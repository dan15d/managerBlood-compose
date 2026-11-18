package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.services.DemonKillOutcome
import es.dan15d.grimorio.domain.services.Distribution
import es.dan15d.grimorio.domain.services.NightStepKind
import es.dan15d.grimorio.domain.services.OnTheBlock
import es.dan15d.grimorio.domain.services.SeatSecret
import es.dan15d.grimorio.domain.services.SetupIssue
import es.dan15d.grimorio.domain.services.SetupIssueLevel
import es.dan15d.grimorio.domain.services.addReminder
import es.dan15d.grimorio.domain.services.aliveNeighbours
import es.dan15d.grimorio.domain.services.allowedDistributions
import es.dan15d.grimorio.domain.services.applySetupEffects
import es.dan15d.grimorio.domain.services.baseDistribution
import es.dan15d.grimorio.domain.services.buildNightOrder
import es.dan15d.grimorio.domain.services.closeNomination
import es.dan15d.grimorio.domain.services.defaultFabled
import es.dan15d.grimorio.domain.services.demonKill
import es.dan15d.grimorio.domain.services.demonSuccession
import es.dan15d.grimorio.domain.services.execute
import es.dan15d.grimorio.domain.services.expiringReminders
import es.dan15d.grimorio.domain.services.infoHints
import es.dan15d.grimorio.domain.services.isDemonKill
import es.dan15d.grimorio.domain.services.jinxesAmong
import es.dan15d.grimorio.domain.services.killPlayer
import es.dan15d.grimorio.domain.services.maxTravellers
import es.dan15d.grimorio.domain.services.nominate
import es.dan15d.grimorio.domain.services.onTheBlock
import es.dan15d.grimorio.domain.services.pickBluffs
import es.dan15d.grimorio.domain.services.pickDecoys
import es.dan15d.grimorio.domain.services.placeReminder
import es.dan15d.grimorio.domain.services.publicPlayers
import es.dan15d.grimorio.domain.services.randomSelection
import es.dan15d.grimorio.domain.services.removeReminders
import es.dan15d.grimorio.domain.services.seatMarionette
import es.dan15d.grimorio.domain.services.seatSecret
import es.dan15d.grimorio.domain.services.seededRandom
import es.dan15d.grimorio.domain.services.startDay
import es.dan15d.grimorio.domain.services.startNight
import es.dan15d.grimorio.domain.services.succeedDemon
import es.dan15d.grimorio.domain.services.toggleFabled
import es.dan15d.grimorio.domain.services.toggleVote
import es.dan15d.grimorio.domain.services.travellerSeats
import es.dan15d.grimorio.domain.services.validateSelection
import es.dan15d.grimorio.domain.services.victoryHint
import es.dan15d.grimorio.domain.services.voteThreshold
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val catalog = TestCatalog
private val map = TestCatalog.map
private fun chars(ids: List<String>) = TestCatalog.chars(ids)
private fun scriptPool(id: String) = TestCatalog.scriptPool(id)

private fun game(ids: List<String>, phase: Phase = Phase.day(1)): Game = testGame(ids, phase)

private fun hasError(issues: List<SetupIssue>) = issues.any { it.level == SetupIssueLevel.Error }

class DomainTest {
    // ---------- catálogo ----------

    @Test
    fun tieneLosTresGuionesOficialesConPersonajesConocidos() {
        assertEquals(listOf("trouble-brewing", "bad-moon-rising", "sects-and-violets"), catalog.scripts.map { it.id })
        for (s in catalog.scripts) for (id in s.characterIds) assertNotNull(catalog.get(id), id)
    }

    // ---------- preparación ----------

    @Test
    fun usaLaTablaOficial() {
        assertEquals(Distribution(townsfolk = 3, outsider = 0, minion = 1, demon = 1), baseDistribution(5))
        assertEquals(Distribution(townsfolk = 7, outsider = 2, minion = 2, demon = 1), baseDistribution(12))
        assertEquals(baseDistribution(15), baseDistribution(18))
    }

    @Test
    fun aplicaElBaronYElPadrino() {
        assertEquals(listOf(Distribution(3, 2, 1, 1)), allowedDistributions(7, chars(listOf("baron"))))
        assertEquals(listOf(0, 2), allowedDistributions(8, chars(listOf("godfather"))).map { it.outsider }.sorted())
    }

    @Test
    fun detectaRepartosQueNoCuadran() {
        val ok = chars(listOf("washerwoman", "librarian", "investigator", "chef", "empath", "poisoner", "imp"))
        assertEquals(emptyList(), validateSelection(7, ok))
        val bad = chars(listOf("washerwoman", "librarian", "investigator", "chef", "drunk", "poisoner", "imp"))
        assertTrue(hasError(validateSelection(7, bad)))
    }

    @Test
    fun generaBolsasValidasAlAzarParaTodosLosGuionesYTamanos() {
        val random = seededRandom(42)
        for (s in catalog.scripts) {
            for (n in 5..15) {
                val bag = randomSelection(n, scriptPool(s.id), random)
                assertEquals(emptyList(), validateSelection(n, bag).filter { it.level == SetupIssueLevel.Error }, "${s.id} $n")
            }
        }
    }

    @Test
    fun eligeFarolesBuenosFueraDeJuegoYUnAldeanoParaElBorracho() {
        val pool = scriptPool("trouble-brewing")
        val bag = chars(listOf("drunk", "chef", "empath", "poisoner", "imp"))
        val decoys = pickDecoys(bag, pool, seededRandom(1))
        val drunk = decoys.getValue("drunk")
        assertEquals(Team.Townsfolk, catalog.get(drunk)!!.team)
        val bluffs = pickBluffs(bag.map { it.id } + drunk, pool, seededRandom(2))
        assertEquals(3, bluffs.size)
        for (b in bluffs) assertFalse(bag.any { it.id == b } || b == drunk)
    }

    // ---------- votaciones ----------

    private val tb7 = listOf("washerwoman", "librarian", "investigator", "chef", "empath", "poisoner", "imp")

    @Test
    fun pideLaMitadDeLosVivosYGastaElVotoDeLosMuertos() {
        var g = killPlayer(game(tb7), "p0")
        assertEquals(3, voteThreshold(g))
        g = nominate(g, "p1", "p6", "n1")
        for (v in listOf("p0", "p2", "p3")) g = toggleVote(g, "n1", v)
        g = closeNomination(g, "n1")
        assertEquals(OnTheBlock("p6", 3), onTheBlock(g))
        assertTrue(g.players[0].ghostVoteUsed)
        val closed = g
        val error = assertFailsWith<RuleViolation> { nominate(closed, "p0", "p5", "n2") }
        assertContains(error.message!!, "vivos")
    }

    @Test
    fun unEmpateDejaATodosLibres() {
        var g = game(tb7)
        g = nominate(g, "p1", "p6", "n1")
        for (v in listOf("p0", "p2", "p3", "p4")) g = toggleVote(g, "n1", v)
        g = closeNomination(g, "n1")
        g = nominate(g, "p2", "p5", "n2")
        for (v in listOf("p0", "p1", "p3", "p4")) g = toggleVote(g, "n2", v)
        g = closeNomination(g, "n2")
        assertNull(onTheBlock(g))
    }

    @Test
    fun sugiereLaVictoriaDelBienAlMorirElDemonio() {
        val g = execute(game(tb7), "p6")
        assertEquals(Alignment.Good, victoryHint(g, map)?.winner)
    }

    // ---------- orden de la noche ----------

    @Test
    fun laPrimeraNocheCon7OMasJugadoresIncluyeLaInformacionDelMalEnSuSitio() {
        val g = game(listOf("washerwoman", "librarian", "investigator", "chef", "drunk", "poisoner", "imp"), Phase.night(1))
            .withPlayer("p4") { it.copy(shownCharacterId = "empath") }
        val keys = buildNightOrder(g, map).map { it.key }
        assertEquals("dusk", keys.first())
        assertEquals("dawn", keys.last())
        assertTrue(keys.indexOf("minion_info") < keys.indexOf("char:poisoner"))
        assertTrue(keys.indexOf("demon_info") < keys.indexOf("char:washerwoman"))
        assertContains(keys, "char:empath")
    }

    @Test
    fun con5JugadoresNoHayInformacionDelMal() {
        val g = game(listOf("washerwoman", "chef", "empath", "poisoner", "imp"), Phase.night(1))
        assertFalse(buildNightOrder(g, map).any { it.kind == NightStepKind.MinionInfo })
    }

    // ---------- lo que ve cada jugador ----------

    private fun seat(characterId: String, shownCharacterId: String? = null): Player {
        val team = catalog.get(characterId)!!.team
        return Player(
            id = "p",
            seat = 0,
            name = "J",
            characterId = characterId,
            shownCharacterId = shownCharacterId,
            alignment = if (team == Team.Minion || team == Team.Demon) Alignment.Evil else Alignment.Good,
            alive = true,
            ghostVoteUsed = false,
            reminders = emptyList(),
        )
    }

    @Test
    fun elBorrachoVeElAldeanoQueCreeSerComoBuenoNuncaBorracho() {
        assertEquals(SeatSecret("empath", Alignment.Good), seatSecret(seat("drunk", "empath"), map))
    }

    @Test
    fun elLunaticoVeElDemonioQueCreeSerComoMalvado() {
        assertEquals(SeatSecret("imp", Alignment.Evil), seatSecret(seat("lunatic", "imp"), map))
    }

    @Test
    fun siAlBorrachoNoSeLeHaDadoPersonajeFalsoNoVeNada() {
        assertEquals(SeatSecret(), seatSecret(seat("drunk"), map))
        assertEquals(SeatSecret(), seatSecret(seat("marionette"), map))
    }

    @Test
    fun elRestoVeSuPersonajeYSuBandoTambienSiHaCambiadoDeBando() {
        assertEquals(SeatSecret("chef", Alignment.Good), seatSecret(seat("chef"), map))
        assertEquals(SeatSecret("chef", Alignment.Evil), seatSecret(seat("chef").copy(alignment = Alignment.Evil), map))
    }

    @Test
    fun alRepartirElBorrachoRecibeSuAldeanoFalsoYQuedaEnElGrimorioComoBorracho() {
        val pool = scriptPool("trouble-brewing")
        val bag = chars(listOf("drunk", "chef", "empath", "poisoner", "imp"))
        val decoys = pickDecoys(bag, pool, seededRandom(3))
        val shown = catalog.get(decoys.getValue("drunk"))!!
        assertEquals(Team.Townsfolk, shown.team)
        assertFalse(bag.any { it.id == shown.id })
        assertEquals(shown.id, seatSecret(seat("drunk", shown.id), map).characterId)
    }

    // ---------- Viajeros ----------

    @Test
    fun losViajerosSonOpcionalesPeroLaBolsaNuncaBajaDe5() {
        assertEquals(3, maxTravellers(8))
        assertEquals(0, maxTravellers(5))
    }

    @Test
    fun losViajerosOcupanLosUltimosAsientosSalvoQueElNarradorElijaOtro() {
        assertEquals(mapOf(7 to "thief", 6 to "beggar"), travellerSeats(8, listOf("thief", "beggar")))
        assertEquals(mapOf(0 to "beggar", 7 to "thief"), travellerSeats(8, listOf("thief", "beggar"), mapOf("beggar" to 0)))
    }

    @Test
    fun siDosViajerosEligenElMismoAsientoElSegundoVaAUnAsientoLibre() {
        assertEquals(mapOf(2 to "thief", 5 to "beggar"), travellerSeats(6, listOf("thief", "beggar"), mapOf("thief" to 2, "beggar" to 2)))
    }

    @Test
    fun laMesaPublicaEnsenaElPersonajeDeLosViajerosYDeNadieMas() {
        val g = game(listOf("chef", "empath", "poisoner", "imp", "washerwoman", "thief"))
        val players = publicPlayers(g, map)
        assertEquals("thief", players[5].travellerId)
        assertTrue(players.take(5).all { it.travellerId == null })
    }

    // ---------- Fábulas, Lóricos y casos especiales ----------

    @Test
    fun elCentinelaPermiteUnForasteroMasOMenos() {
        val withOutsider = chars(listOf("washerwoman", "librarian", "investigator", "chef", "butler", "poisoner", "imp"))
        assertTrue(hasError(validateSelection(7, withOutsider)))
        assertEquals(emptyList(), validateSelection(7, withOutsider + chars(listOf("sentinel"))))
    }

    @Test
    fun conElPapaSeRepitenBuenosPeroNuncaMalos() {
        val twoChefs = chars(listOf("chef", "chef", "investigator", "empath", "washerwoman", "poisoner", "imp"))
        assertTrue(hasError(validateSelection(7, twoChefs)))
        assertEquals(emptyList(), validateSelection(7, twoChefs + chars(listOf("pope"))))
        val twoImps = chars(listOf("chef", "investigator", "empath", "washerwoman", "librarian", "imp", "imp"))
        assertTrue(hasError(validateSelection(7, twoImps + chars(listOf("pope")))))
    }

    @Test
    fun hasta3TontosDelPuebloSinPapa() {
        val idiots = chars(listOf("villageidiot", "villageidiot", "villageidiot", "chef", "empath", "poisoner", "imp"))
        assertEquals(emptyList(), validateSelection(7, idiots))
    }

    @Test
    fun elHerejeNoPuedeEstarConElBaron() {
        val bag = chars(listOf("heretic", "butler", "chef", "empath", "investigator", "baron", "imp"))
        assertContains(validateSelection(7, bag).joinToString(" ") { it.message }, "no pueden estar los dos")
    }

    @Test
    fun conElAteoNoHayMalosConElInvocadorNoHayDemonio() {
        val atheist = chars(listOf("atheist", "chef", "empath", "investigator", "washerwoman", "librarian", "monk"))
        assertEquals(emptyList(), validateSelection(7, atheist))
        val summoner = chars(listOf("chef", "empath", "investigator", "washerwoman", "librarian", "monk", "summoner"))
        assertEquals(emptyList(), validateSelection(7, summoner))
    }

    @Test
    fun elJugueteroDaInformacionAlMalConMenosDe7TorLaQuita() {
        val five = listOf("chef", "empath", "investigator", "poisoner", "imp")
        fun kinds(g: Game) = buildNightOrder(g, map).map { it.kind }
        assertFalse(NightStepKind.MinionInfo in kinds(game(five, Phase.night(1))))
        assertTrue(NightStepKind.MinionInfo in kinds(game(five, Phase.night(1)).copy(fabledIds = listOf("toymaker"))))
        assertFalse(NightStepKind.DemonInfo in kinds(game(tb7, Phase.night(1)).copy(fabledIds = listOf("tor"))))
    }

    @Test
    fun lasFabulasConPasoDeNocheSalenEnLaHojaSinJugadoresYSinMarcarseMuertas() {
        val steps = buildNightOrder(game(tb7, Phase.night(1)).copy(fabledIds = listOf("angel")), map)
        val angel = steps.first { it.key == "char:angel" }
        assertEquals(emptyList(), angel.playerIds)
        assertFalse(angel.dead)
    }

    @Test
    fun conTorLosVivosNoVenSuPersonaje() {
        val g = game(tb7).copy(fabledIds = listOf("tor"))
        assertEquals(SeatSecret(), seatSecret(g.players[0], map, g.fabledIds))
        assertEquals("washerwoman", seatSecret(g.players[0].copy(alive = false), map, g.fabledIds).characterId)
    }

    @Test
    fun elDjinnEntraSiElGuionTieneIncompatibilidades() {
        val jinxes = catalog.jinxes
        assertEquals(listOf("sentinel"), defaultFabled(chars(listOf("chef", "imp", "sentinel")), jinxes))
        assertEquals(listOf("djinn"), defaultFabled(chars(listOf("heretic", "baron", "imp")), jinxes))
        assertEquals(1, jinxesAmong(listOf("spy", "magician", "chef"), jinxes).size)
    }

    @Test
    fun sePonenYQuitanFabulasDuranteLaPartidaPeroNoOtrosPersonajes() {
        var g = toggleFabled(game(tb7), catalog.get("ferryman")!!)
        assertEquals(listOf("ferryman"), g.fabledIds)
        g = toggleFabled(g, catalog.get("ferryman")!!)
        assertEquals(emptyList(), g.fabledIds)
        val last = g
        assertFailsWith<RuleViolation> { toggleFabled(last, catalog.get("chef")!!) }
    }

    @Test
    fun laMarionetaSeSientaJuntoAlDemonio() {
        val g = game(listOf("marionette", "chef", "empath", "imp", "investigator", "washerwoman", "librarian"))
        val players = seatMarionette(g.players, map, seededRandom(5))
        val m = players.first { it.characterId == "marionette" }
        assertContains(listOf(2, 4), m.seat)
        assertEquals(1, players.count { it.characterId == "marionette" })
    }

    @Test
    fun elCazarrecompensasVuelveMaloAUnAldeanoUnTontoDelPuebloExtraEstaBorracho() {
        val g = game(listOf("bountyhunter", "chef", "villageidiot", "villageidiot", "empath", "poisoner", "imp"))
        var n = 0
        val (players, log) = applySetupEffects(g.players, map, seededRandom(9)) { "r${n++}" }
        val evilTownsfolk = players.filter { it.alignment == Alignment.Evil && catalog.get(it.characterId)!!.team == Team.Townsfolk }
        assertEquals(1, evilTownsfolk.size)
        assertTrue(evilTownsfolk[0].characterId != "bountyhunter")
        assertEquals(1, players.count { p -> p.reminders.any { it.label == "Borracho" } })
        assertEquals(2, log.size)
    }

    @Test
    fun laMarionetaNoCreeSerElReySiEstaElNinoDelCoro() {
        val pool = chars(listOf("king", "chef"))
        for (seed in 0 until 10) {
            assertEquals("chef", pickDecoys(chars(listOf("marionette", "choirboy")), pool, seededRandom(seed))["marionette"])
        }
    }

    // ---------- ayudas del Narrador ----------

    // Asientos 0..6: Lavandera, Bibliotecario, Envenenador, Diablillo, Empático, Chef, Investigador.
    private val helpIds = listOf("washerwoman", "librarian", "poisoner", "imp", "empath", "chef", "investigator")
    private fun helpNight(phase: Phase = Phase.night(1)) = game(helpIds, phase)
    private fun hint(g: Game, id: String) = infoHints(g, catalog.get(id)!!, map)

    @Test
    fun elEmpaticoCuentaSusVecinosVivosMalosSaltandoALosMuertos() {
        assertContains(hint(helpNight(), "empath")[0].text, "1")
        val g = killPlayer(helpNight(), "p3")
        // Muerto el Diablillo, su vecino vivo de ese lado es el Envenenador.
        assertContains(hint(g, "empath")[0].text, "1")
        assertEquals(listOf("poisoner", "chef"), aliveNeighbours(g, g.players[4]).map { it.characterId })
    }

    @Test
    fun elChefCuentaParejasDeMalosYElRelojeroLosPasosHastaElEsbirro() {
        assertContains(hint(helpNight(), "chef")[0].text, "1")
        assertContains(hint(game(listOf("clockmaker", "imp", "chef", "poisoner", "empath")), "clockmaker")[0].text, "2")
    }

    @Test
    fun elBibliotecarioSinForasterosRecibeUn0ElEnterradorVeAlEjecutado() {
        assertContains(hint(helpNight(), "librarian")[0].text, "0")
        val g = helpNight(Phase.night(2)).copy(executedToday = "p5")
        assertEquals(emptyList(), hint(g, "undertaker"))
        val withUndertaker = startNight(execute(game(listOf("undertaker", "chef", "poisoner", "imp", "empath")), "p1"))
        assertContains(hint(withUndertaker, "undertaker")[0].text, "Chef")
    }

    @Test
    fun laPitonisaDiceSiConElDemonioYConLaPistaFalsa() {
        var g = game(listOf("fortuneteller", "chef", "poisoner", "imp", "empath"))
        assertContains(hint(g, "fortuneteller")[0].text, "Falta poner la pista falsa")
        g = addReminder(g, "p1", reminderOf("r", "fortuneteller", "Falso positivo"))
        assertContains(hint(g, "fortuneteller")[0].text, Regex("4\\. J4 o 2\\. J2"))
    }

    @Test
    fun avisaSiElEspiaOElReclusoPuedenCambiarLaRespuesta() {
        val g = game(listOf("empath", "recluse", "spy", "imp", "chef"))
        assertContains(hint(g, "empath")[0].caveats.joinToString(" "), "Espía y Recluso pueden aparecer distintos")
    }

    @Test
    fun elRecordatorioDelEnvenenadorSeMueveEnVezDeDuplicarse() {
        fun poison(id: String) = reminderOf(id, "poisoner", "Envenenado")
        var g = placeReminder(helpNight(), "p0", poison("a"), 1)
        g = placeReminder(g, "p4", poison("b"), 1)
        assertEquals(emptyList(), g.players[0].reminders)
        assertEquals(listOf("b"), g.players[4].reminders.map { it.id })
        // Sin límite (recordatorios propios) se acumulan.
        g = placeReminder(g, "p5", reminderOf("c", "custom", "Ojo"), 0)
        g = placeReminder(g, "p6", reminderOf("d", "custom", "Ojo"), 0)
        assertEquals(2, g.players.count { p -> p.reminders.any { it.label == "Ojo" } })
    }

    @Test
    fun alAnochecerSeProponenLosRecordatoriosDeUnaNocheYUnDia() {
        var g = helpNight()
        g = addReminder(g, "p0", reminderOf("a", "poisoner", "Envenenado"))
        g = addReminder(g, "p1", reminderOf("b", "imp", "Muerto"))
        g = addReminder(g, "p2", reminderOf("c", "drunk", "Es el Borracho"))
        assertEquals(listOf("a", "b"), expiringReminders(g).map { it.reminder.id })
        g = removeReminders(g, listOf("a", "b"))
        assertEquals(listOf("c"), g.players.flatMap { it.reminders }.map { it.id })
    }

    // ---------- nuevo Demonio: Mujer escarlata y pase del Diablillo ----------

    // Asientos: Diablillo, Mujer escarlata, Envenenador, Chef, Empático, Lavandera, Monje.
    private val scarletIds = listOf("imp", "scarletwoman", "poisoner", "chef", "empath", "washerwoman", "monk")

    @Test
    fun con5OMasVivosAntesDeMorirLaMujerEscarlataPasaASerElDiablilloYLaPartidaSigue() {
        val g = execute(game(scarletIds), "p0")
        val s = demonSuccession(g, map)!!
        assertEquals(listOf("scarletwoman"), s.heirs.map { it.characterId })
        assertNull(victoryHint(g, map))
        val after = succeedDemon(g, s, "p1", map)
        assertEquals("imp", after.players[1].characterId)
        assertEquals(Alignment.Evil, after.players[1].alignment)
        assertNull(demonSuccession(after, map))
    }

    @Test
    fun con4VivosAntesDeMorirElDemonioGananLosBuenos() {
        var g = game(scarletIds)
        for (id in listOf("p3", "p4", "p5")) g = killPlayer(g, id)
        g = execute(g, "p0")
        assertNull(demonSuccession(g, map))
        assertEquals(Alignment.Good, victoryHint(g, map)?.winner)
    }

    @Test
    fun losViajerosNoCuentanParaLaMujerEscarlata() {
        var g = game(scarletIds.take(5) + listOf("thief", "beggar"))
        g = execute(g, "p0")
        // Quedan 4 jugadores vivos sin contar los Viajeros: sí llega.
        assertEquals("scarletwoman", demonSuccession(g, map)?.heirs?.get(0)?.characterId)
        g = killPlayer(g, "p4")
        assertNull(demonSuccession(g, map))
    }

    @Test
    fun avisaSiLaMujerEscarlataEstaEnvenenada() {
        var g = addReminder(game(scarletIds), "p1", reminderOf("r", "poisoner", "Envenenado"))
        g = execute(g, "p0")
        assertContains(demonSuccession(g, map)?.caveat!!, "Envenenado")
    }

    @Test
    fun sinMujerEscarlataElDiablilloMuertoDeNochePasaAUnEsbirroVivoAElegir() {
        val noScarlet = listOf("imp", "baron", "poisoner", "chef", "empath", "washerwoman", "monk")
        val night = killPlayer(game(noScarlet, Phase.night(2)), "p0")
        val s = demonSuccession(night, map)!!
        assertEquals(listOf("baron", "poisoner"), s.heirs.map { it.characterId }.sorted())
        assertFailsWith<RuleViolation> { succeedDemon(night, s, "p3", map) }
        // De día (ejecutado) no hay pase: ganan los buenos.
        val day = execute(game(noScarlet), "p0")
        assertNull(demonSuccession(day, map))
        assertEquals(Alignment.Good, victoryHint(day, map)?.winner)
    }

    // ---------- ataque del Demonio de noche ----------

    // Asientos: Diablillo, Barón, Soldado, Monje, Empático, Mayordomo, Chef.
    private val attackIds = listOf("imp", "baron", "soldier", "monk", "empath", "butler", "chef")
    private fun attackNight() = game(attackIds, Phase.night(2))
    private fun kill(id: String) = reminderOf(id, "imp", "Muerto")

    @Test
    fun elElegidoMuereEnElActoPeroLaMesaNoLoVeHastaElAmanecer() {
        val (g, outcome) = demonKill(attackNight(), "p4", kill("k"), 1, map)
        assertEquals(DemonKillOutcome.Killed, outcome)
        assertFalse(g.players[4].alive)
        assertTrue(publicPlayers(g, map)[4].alive)
        val day = startDay(g)
        assertFalse(publicPlayers(day, map)[4].alive)
        assertContains(day.log.last().text, "Han muerto esta noche: J5")
    }

    @Test
    fun elSoldadoYLosProtegidosNoMuerenUnSoldadoEnvenenadoSi() {
        assertEquals(DemonKillOutcome.Protected, demonKill(attackNight(), "p2", kill("k"), 1, map).outcome)
        val safe = addReminder(attackNight(), "p6", reminderOf("s", "monk", "A salvo"))
        assertEquals(DemonKillOutcome.Protected, demonKill(safe, "p6", kill("k"), 1, map).outcome)
        val poisoned = addReminder(attackNight(), "p2", reminderOf("e", "poisoner", "Envenenado"))
        assertFalse(demonKill(poisoned, "p2", kill("k"), 1, map).game.players[2].alive)
    }

    @Test
    fun siElDiablilloSeEligeASiMismoMuereYSeOfreceElPaseAlEsbirro() {
        val (g, outcome) = demonKill(attackNight(), "p0", kill("k"), 1, map)
        assertEquals(DemonKillOutcome.SelfKill, outcome)
        assertEquals(listOf("baron"), demonSuccession(g, map)?.heirs?.map { it.characterId })
    }

    @Test
    fun soloLosMuertoDeUnDemonioSonAtaques() {
        assertTrue(isDemonKill("imp", "Muerto", map))
        assertFalse(isDemonKill("gambler", "Muerto", map))
        assertFalse(isDemonKill("poisoner", "Envenenado", map))
    }
}

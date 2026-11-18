package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.services.EvilSide
import es.dan15d.grimorio.domain.services.addReminder
import es.dan15d.grimorio.domain.services.applyReminderChoice
import es.dan15d.grimorio.domain.services.infoHints
import es.dan15d.grimorio.domain.services.killPlayer
import es.dan15d.grimorio.domain.services.nearestEvilSide
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

// Respuestas de la noche que la app calcula con el Grimorio.

private val map = TestCatalog.map

private fun game(ids: List<String>, phase: Phase = Phase.night(1)): Game =
    testGame(ids, phase).copy(scriptId = "", scriptName = "")

private fun hint(g: Game, id: String): String? = infoHints(g, TestCatalog.get(id)!!, map).firstOrNull()?.text

private fun r(source: String, label: String) = reminderOf("$source-$label", source, label)

class InfoTest {
    @Test
    fun oraculoCuantosMuertosSonMalos() {
        var g = game(listOf("imp", "poisoner", "oracle", "chef", "empath"), Phase.night(2))
        assertContains(hint(g, "oracle")!!, "0")
        g = killPlayer(killPlayer(g, "p1"), "p3")
        assertContains(hint(g, "oracle")!!, "1")
    }

    @Test
    fun shugenjaHaciaQueLadoEstaElMaloMasCercano() {
        // Asientos: Shugenja, Chef, Diablillo, Empático, Envenenador, Monje, Soldado.
        // Hacia la derecha el Diablillo está a 2; hacia la izquierda, el Envenenador a 3.
        val g = game(listOf("shugenja", "chef", "imp", "empath", "poisoner", "monk", "soldier"))
        assertEquals(EvilSide.Clockwise, nearestEvilSide(g, g.players[0]))
        assertContains(hint(g, "shugenja")!!, "agujas del reloj")
        val tie = game(listOf("shugenja", "imp", "chef", "empath", "poisoner"))
        assertEquals(EvilSide.Both, nearestEvilSide(tie, tie.players[0]))
    }

    @Test
    fun caballeroNobleYAdministradorAQuienPuedeSenalar() {
        val g = game(listOf("knight", "noble", "steward", "imp", "poisoner", "chef"))
        assertFalse(hint(g, "knight")!!.contains("J4 (Diablillo)"))
        assertContains(hint(g, "noble")!!, "1 malo (4. J4 (Diablillo), 5. J5 (Envenenador))")
        assertFalse(hint(g, "steward")!!.contains("Envenenador"))
    }

    @Test
    fun abuelaSenalaASuNietoLaPrimeraNoche() {
        val g = addReminder(game(listOf("grandmother", "chef", "imp", "poisoner", "empath")), "p1", r("grandmother", "Nieto"))
        assertContains(hint(g, "grandmother")!!, "J2 y enséñale Chef")
        assertContains(hint(game(listOf("grandmother", "chef", "imp", "poisoner", "empath")), "grandmother")!!, "Falta")
    }

    @Test
    fun sabioSiLeMataElDemonioSenalaAlDemonioYAOtro() {
        var g = game(listOf("imp", "poisoner", "sage", "chef", "empath"), Phase.night(2))
        g = applyReminderChoice(g, "p2", r("imp", "Muerto"), map).game
        assertContains(hint(g, "sage")!!, "Señala al Demonio (J1)")
    }

    @Test
    fun reyYNinoDelCoro() {
        var g = game(listOf("imp", "poisoner", "king", "choirboy", "chef", "empath"), Phase.night(2))
        assertContains(hint(g, "king")!!, "más vivos que muertos")
        assertContains(hint(g, "choirboy")!!, "no le despiertes")
        g = applyReminderChoice(g, "p2", r("imp", "Muerto"), map).game
        assertContains(hint(g, "choirboy")!!, "señala al Demonio (J1)")
    }

    @Test
    fun cazarrecompensasSenalaASuConocidoYAOtroCuandoMuere() {
        var g = game(listOf("bountyhunter", "imp", "poisoner", "chef", "empath"))
        assertContains(hint(g, "bountyhunter")!!, "Falta poner")
        g = addReminder(g, "p2", r("bountyhunter", "Conocido"))
        assertContains(hint(g, "bountyhunter")!!, "Señala a J3")
        g = killPlayer(g.copy(phase = Phase.night(2)), "p2")
        assertContains(hint(g, "bountyhunter")!!, "Ha muerto J3")
    }

    @Test
    fun sonadorGuardianDeCuervosYTontoDelPuebloAlTocarAlElegidoLaRespuesta() {
        val g = game(listOf("dreamer", "ravenkeeper", "villageidiot", "imp", "poisoner", "chef"), Phase.night(2))
        fun choose(source: String, target: String) = applyReminderChoice(g, target, r(source, "Elegido"), map)
        assertContains(choose("dreamer", "p3").message, "J4 es Diablillo (malo): enséñale Diablillo y un personaje bueno")
        assertContains(choose("ravenkeeper", "p5").message, "Enséñale Chef")
        assertContains(choose("villageidiot", "p4").message, "malo: pulgar hacia abajo")
        // No cambia nada en el Grimorio.
        assertSame(g, choose("dreamer", "p3").game)
    }

    @Test
    fun siQuienEligeNoTieneHabilidadOHayReclusoLoAvisa() {
        val g = game(listOf("drunk", "recluse", "imp", "poisoner", "chef"), Phase.night(2))
            .withPlayer("p0") { it.copy(shownCharacterId = "dreamer") }
        val message = applyReminderChoice(g, "p1", r("dreamer", "Elegido"), map).message
        assertContains(message, "no tiene habilidad")
        assertContains(message, "Recluso puede aparecer distinto")
    }

    @Test
    fun padrinoYMarioneta() {
        assertContains(hint(game(listOf("godfather", "imp", "butler", "chef", "empath")), "godfather")!!, "Mayordomo")
        assertContains(hint(game(listOf("marionette", "imp", "chef", "empath", "monk")), "marionette")!!, "Marioneta: J1")
    }
}

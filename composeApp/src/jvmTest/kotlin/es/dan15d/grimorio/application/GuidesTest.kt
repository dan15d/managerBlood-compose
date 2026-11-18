package es.dan15d.grimorio.application

import es.dan15d.grimorio.application.ports.CharacterGuide
import es.dan15d.grimorio.application.ports.CharacterGuideSource
import es.dan15d.grimorio.application.ports.GuideBlock
import es.dan15d.grimorio.application.ports.GuideSection
import es.dan15d.grimorio.application.usecases.GUIDE_SECTIONS
import es.dan15d.grimorio.application.usecases.GuideAudience
import es.dan15d.grimorio.application.usecases.ListSpecialCases
import es.dan15d.grimorio.application.usecases.ReadCharacterGuide
import es.dan15d.grimorio.domain.entities.Interaction
import es.dan15d.grimorio.infrastructure.catalog.StaticCharacterGuides
import es.dan15d.grimorio.infrastructure.catalog.loadCatalog
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuidesTest {
    private val catalog = runBlocking { loadCatalog() }
    private val guides = StaticCharacterGuides()

    @Test
    fun hayGuiaDeTodosLosPersonajes() = runTest {
        for (c in catalog.all()) assertNotNull(guides.guide(c.id), c.id)
    }

    @Test
    fun elJugadorNuncaVeComoNarrarloNiLosDetallesDelNarrador() = runTest {
        val read = ReadCharacterGuide(catalog, guides)
        for (id in listOf("drunk", "imp", "spy", "washerwoman", "toymaker")) {
            val player = read.execute(id, GuideAudience.Player)!!.sections.map { it.section }
            assertTrue(player.all { it in GUIDE_SECTIONS.getValue(GuideAudience.Player) }, id)
            assertFalse(GuideSection.HowToRun in player, id)
            assertFalse(GuideSection.More in player, id)
        }
        val storyteller = read.execute("drunk", GuideAudience.Storyteller)!!.sections.map { it.section }
        assertTrue(GuideSection.HowToRun in storyteller)
    }

    @Test
    fun losApartadosSalenEnTextoPlanoSinHtmlNiMarcasDeLaWiki() = runTest {
        val marks = Regex("<[a-z]|\\[role|\\[/r")
        for (c in catalog.all()) {
            val guide = guides.guide(c.id)!!
            val text = (guide.sections.values.flatten().map(GuideBlock::text) + listOfNotNull(guide.quote)).joinToString("\n")
            assertFalse(marks.containsMatchIn(text), c.id)
        }
    }

    @Test
    fun losCasosEspecialesSoloCuentanPersonajesEnJuego() = runTest {
        val cases = ListSpecialCases(catalog, guides)
        val withSpy = cases.execute("magician", listOf("spy", "imp"))
        assertTrue(withSpy.jinxes.map { it.characterIds }.contains(listOf("magician", "spy")))
        val alone = cases.execute("magician", listOf("chef", "imp"))
        assertEquals(emptyList(), alone.jinxes)

        val first = guides.interactions().first()
        val (a, b) = first.characterIds
        assertTrue(cases.execute(a, listOf(b)).interactions.contains(first))
        assertEquals(emptyList<Interaction>(), cases.execute(a, emptyList()).interactions)
    }

    @Test
    fun siUnaGuiaNoExisteNoSeEnsenaNada() = runTest {
        val empty = object : CharacterGuideSource {
            override suspend fun guide(characterId: String): CharacterGuide? = null
            override suspend fun interactions(): List<Interaction> = emptyList()
        }
        assertNull(ReadCharacterGuide(catalog, empty).execute("chef", GuideAudience.Player))
    }
}

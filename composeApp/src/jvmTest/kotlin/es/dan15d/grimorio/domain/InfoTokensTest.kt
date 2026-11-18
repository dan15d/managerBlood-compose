package es.dan15d.grimorio.domain

import es.dan15d.grimorio.domain.services.INFO_TOKENS
import es.dan15d.grimorio.domain.services.InfoTokenId
import es.dan15d.grimorio.domain.services.STEP_INFO_TOKENS
import es.dan15d.grimorio.domain.services.infoToken
import es.dan15d.grimorio.domain.services.stepInfoToken
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val spanish = Locale.forLanguageTag("es")
private fun upper(s: String) = s.uppercase(spanish)

/** La guía de la wiki de un personaje, como texto JSON (igual que JSON.stringify en la web). */
private fun guideText(id: String): String {
    val file = listOf(
        File("src/commonMain/composeResources/files/data/guides/$id.json"),
        File("composeApp/src/commonMain/composeResources/files/data/guides/$id.json"),
    ).firstOrNull { it.exists() } ?: return "null"
    return Json.parseToJsonElement(file.readText()).toString()
}

class InfoTokensTest {
    @Test
    fun noSeRepiten() {
        assertEquals(INFO_TOKENS.size, INFO_TOKENS.map { it.id }.toSet().size)
        assertEquals(INFO_TOKENS.size, INFO_TOKENS.map { it.text }.toSet().size)
    }

    @Test
    fun losTextosSonLosQueCitaLaWiki() {
        val all = TestCatalog.all.joinToString("\n") { guideText(it.id) }
        for (t in INFO_TOKENS) assertContains(all, upper(t.text), message = t.text)
    }

    @Test
    fun elBotonDirectoDeCadaPasoEsDeUnPersonajeQueLaUsaSegunSuGuia() {
        for ((id, step) in STEP_INFO_TOKENS) {
            assertTrue(TestCatalog.all.any { it.id == id }, id)
            assertContains(guideText(id), upper(infoToken(step.token).text), message = id)
        }
    }

    @Test
    fun elReySoloLaEnsenaLaPrimeraNoche() {
        assertEquals(InfoTokenId.CharacterIs, stepInfoToken("king", true)?.token)
        assertNull(stepInfoToken("king", false))
        assertNotNull(stepInfoToken("cerenovus", false)?.more)
        assertNull(stepInfoToken("imp", true))
    }
}

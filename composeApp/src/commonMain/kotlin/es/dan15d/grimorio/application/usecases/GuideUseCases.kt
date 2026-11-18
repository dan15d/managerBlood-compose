package es.dan15d.grimorio.application.usecases

import es.dan15d.grimorio.application.ports.CharacterCatalog
import es.dan15d.grimorio.application.ports.CharacterGuideSource
import es.dan15d.grimorio.application.ports.GuideBlock
import es.dan15d.grimorio.application.ports.GuideSection
import es.dan15d.grimorio.domain.entities.Interaction
import es.dan15d.grimorio.domain.entities.Jinx
import es.dan15d.grimorio.domain.services.jinxesOf

/**
 * Quién lee la guía. El Narrador ve cómo narrarlo y los detalles de reglas; el jugador, lo que le
 * sirve para jugar (consejos y cómo farolear). Al jugador nunca se le enseña nada que dependa de
 * la partida: solo la guía del personaje que cree ser, igual que la de cualquier personaje del guion.
 */
enum class GuideAudience(val key: String) { Storyteller("storyteller"), Player("player") }

val GUIDE_SECTIONS: Map<GuideAudience, List<GuideSection>> = mapOf(
    GuideAudience.Storyteller to listOf(GuideSection.Summary, GuideSection.HowToRun, GuideSection.Examples, GuideSection.More),
    GuideAudience.Player to listOf(GuideSection.Summary, GuideSection.Examples, GuideSection.Tips, GuideSection.Bluffing),
)

private fun title(section: GuideSection, name: String): String = when (section) {
    GuideSection.Summary -> "Resumen"
    GuideSection.HowToRun -> "Cómo narrarlo"
    GuideSection.Examples -> "Ejemplos"
    GuideSection.Tips -> "Consejos"
    GuideSection.Bluffing -> "Farolear como $name"
    GuideSection.More -> "Más detalles de reglas"
}

data class GuideViewSection(val section: GuideSection, val title: String, val blocks: List<GuideBlock>)

data class GuideView(val sections: List<GuideViewSection>, val quote: String? = null)

/** La guía de un personaje con solo los apartados de quien la lee. */
class ReadCharacterGuide(
    private val catalog: CharacterCatalog,
    private val guides: CharacterGuideSource,
) {
    suspend fun execute(characterId: String, audience: GuideAudience): GuideView? {
        val guide = guides.guide(characterId) ?: return null
        val name = catalog.get(characterId)?.name ?: characterId
        return GuideView(
            sections = GUIDE_SECTIONS.getValue(audience)
                .filter { !guide.sections[it].isNullOrEmpty() }
                .map { GuideViewSection(it, title(it, name), guide.sections.getValue(it)) },
            quote = guide.quote,
        )
    }
}

data class SpecialCases(val jinxes: List<Jinx>, val interactions: List<Interaction>)

/**
 * Casos especiales de un personaje con los demás de la partida: incompatibilidades oficiales e
 * interacciones de la wiki. Solo para el Narrador: dependen de qué hay en juego.
 */
class ListSpecialCases(
    private val catalog: CharacterCatalog,
    private val guides: CharacterGuideSource,
) {
    suspend fun execute(characterId: String, inPlayIds: Iterable<String>): SpecialCases {
        val others = inPlayIds.filter { it != characterId }
        return SpecialCases(
            jinxes = jinxesOf(characterId, others, catalog.jinxes()),
            interactions = jinxesOf(characterId, others, guides.interactions()),
        )
    }
}

package es.dan15d.grimorio.application.usecases

import es.dan15d.grimorio.application.ports.AiSetupProposal
import es.dan15d.grimorio.application.ports.AiSetupRequest
import es.dan15d.grimorio.application.ports.AiStoryteller
import es.dan15d.grimorio.application.ports.CharacterCatalog
import es.dan15d.grimorio.application.ports.SetupStyle
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Script
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.services.RandomFn
import es.dan15d.grimorio.domain.services.SETUP_MODIFIERS
import es.dan15d.grimorio.domain.services.SetupIssueLevel
import es.dan15d.grimorio.domain.services.baseDistribution
import es.dan15d.grimorio.domain.services.pickBluffs
import es.dan15d.grimorio.domain.services.pickDecoys
import es.dan15d.grimorio.domain.services.randomSelection
import es.dan15d.grimorio.domain.services.validateSelection

data class SetupProposal(
    val characterIds: List<String>,
    val bluffIds: List<String>,
    val decoys: Map<String, String>,
    val explanation: String? = null,
    val tips: List<String>? = null,
)

fun scriptCharacters(catalog: CharacterCatalog, script: Script): List<Character> =
    script.characterIds.mapNotNull { catalog.get(it) }

/** Reparto al azar respetando la hoja de preparación (botón «Al azar» del modo manual). */
class SuggestRandomSetup(
    private val catalog: CharacterCatalog,
    private val random: RandomFn,
) {
    fun execute(script: Script, playerCount: Int): SetupProposal {
        val pool = scriptCharacters(catalog, script)
        val bag = randomSelection(playerCount, pool, random)
        val decoys = pickDecoys(bag, pool, random)
        val bluffIds = pickBluffs(bag.map { it.id } + decoys.values, pool, random)
        return SetupProposal(characterIds = bag.map { it.id }, bluffIds = bluffIds, decoys = decoys)
    }
}

/** Completa engaños (Borracho…) y faroles para una selección hecha a mano. */
class CompleteManualSetup(
    private val catalog: CharacterCatalog,
    private val random: RandomFn,
) {
    fun execute(
        script: Script,
        characterIds: List<String>,
        decoys: Map<String, String>? = null,
        bluffIds: List<String>? = null,
    ): SetupProposal {
        val pool = scriptCharacters(catalog, script)
        val bag = characterIds.mapNotNull { catalog.get(it) }
        val chosenDecoys = pickDecoys(bag, pool, random, decoys ?: emptyMap())
        val taken = (characterIds + chosenDecoys.values).toSet()
        val kept = (bluffIds ?: emptyList()).filter { it !in taken }.take(3)
        val bluffs = if (kept.size == 3) kept else (kept + pickBluffs(taken + kept, pool, random)).take(3)
        return SetupProposal(characterIds = characterIds, bluffIds = bluffs, decoys = chosenDecoys)
    }
}

/**
 * Pide el reparto a la IA y lo valida con las reglas del dominio. Si la IA se equivoca,
 * se le reenvían los errores una vez; si vuelve a fallar, se avisa al Narrador.
 */
class GenerateAiSetup(
    private val catalog: CharacterCatalog,
    private val ai: AiStoryteller,
    private val random: RandomFn,
) {
    suspend fun execute(
        script: Script,
        playerNames: List<String>,
        style: SetupStyle,
        extraInstructions: String? = null,
    ): SetupProposal {
        val pool = scriptCharacters(catalog, script)
        val playerCount = playerNames.size
        val modifiers = pool.mapNotNull { c -> SETUP_MODIFIERS[c.id]?.let { c.id to it.note } }.toMap()
        var previousErrors: List<String>? = null
        for (attempt in 0 until 2) {
            val proposal = ai.proposeSetup(
                AiSetupRequest(
                    playerCount = playerCount,
                    playerNames = playerNames,
                    scriptName = script.name,
                    scriptCharacters = pool.filter { it.team in listOf(Team.Townsfolk, Team.Outsider, Team.Minion, Team.Demon) },
                    baseDistribution = baseDistribution(playerCount),
                    setupModifiers = modifiers,
                    style = style,
                    extraInstructions = extraInstructions,
                    previousErrors = previousErrors,
                ),
            )
            val errors = check(proposal, pool, playerCount)
            if (errors.isEmpty()) return complete(proposal, pool)
            previousErrors = errors
        }
        throw Exception("La IA no ha dado un reparto válido: ${previousErrors?.joinToString(" ")}")
    }

    private fun check(p: AiSetupProposal, pool: List<Character>, playerCount: Int): List<String> {
        val ids = pool.map { it.id }.toSet()
        val unknown = p.characterIds.filter { it !in ids }
        if (unknown.isNotEmpty()) return listOf("Estos personajes no están en el guion: ${unknown.joinToString(", ")}.")
        val chosen = p.characterIds.map { id -> pool.first { it.id == id } }
        return validateSelection(playerCount, chosen)
            .filter { it.level == SetupIssueLevel.Error }
            .map { it.message }
    }

    /** Rellena faroles y engaños que falten o no sean válidos. */
    private fun complete(p: AiSetupProposal, pool: List<Character>): SetupProposal {
        val bag = p.characterIds.map { id -> pool.first { it.id == id } }
        val decoys = pickDecoys(bag, pool, random, p.decoys)
        val taken = (p.characterIds + decoys.values).toSet()
        fun goodFree(id: String): Boolean {
            val c = pool.find { it.id == id }
            return c != null && id !in taken && (c.team == Team.Townsfolk || c.team == Team.Outsider)
        }
        var bluffIds = p.bluffIds.distinct().filter(::goodFree).take(3)
        if (bluffIds.size < 3) bluffIds = (bluffIds + pickBluffs(taken + bluffIds, pool, random)).take(3)
        return SetupProposal(p.characterIds, bluffIds, decoys, p.explanation, p.tips)
    }
}

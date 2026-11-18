package es.dan15d.grimorio.application.usecases

import es.dan15d.grimorio.application.ports.AiScriptProposal
import es.dan15d.grimorio.application.ports.AiScriptRequest
import es.dan15d.grimorio.application.ports.AiScriptReviewRequest
import es.dan15d.grimorio.application.ports.AiScriptSwap
import es.dan15d.grimorio.application.ports.AiStoryteller
import es.dan15d.grimorio.application.ports.CharacterCatalog
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.services.Distribution
import es.dan15d.grimorio.domain.services.jinxesAmong

enum class ScriptSize(val key: String) { Classic("classic"), Teensy("teensy") }

/** Reparto de un guion completo: el clásico de 25 personajes o uno pequeño para 5-6 jugadores. */
val SCRIPT_TARGETS: Map<ScriptSize, Distribution> = mapOf(
    ScriptSize.Classic to Distribution(townsfolk = 13, outsider = 4, minion = 4, demon = 4),
    ScriptSize.Teensy to Distribution(townsfolk = 6, outsider = 2, minion = 2, demon = 1),
)

private val BAG_TEAMS: List<Team> = listOf(Team.Townsfolk, Team.Outsider, Team.Minion, Team.Demon)

data class GeneratedScript(
    val name: String,
    val description: String,
    val characterIds: List<String>,
    val explanation: String,
    /** Avisos si la IA no ha clavado el reparto (el Narrador lo puede retocar en el editor). */
    val warnings: List<String>,
)

/** Con menos personajes no hay guion que valorar. */
private const val MIN_TO_REVIEW = 5

data class ScriptReview(
    val verdict: String,
    val strengths: List<String>,
    val problems: List<String>,
    val swaps: List<AiScriptSwap>,
    /** Personajes del guion tal como se revisó, para saber si ha cambiado desde entonces. */
    val reviewedIds: List<String>,
)

/** Pide a la IA una opinión sobre un guion y se queda solo con los cambios que se pueden aplicar. */
class ReviewAiScript(
    private val catalog: CharacterCatalog,
    private val ai: AiStoryteller,
) {
    suspend fun execute(name: String, characterIds: List<String>): ScriptReview {
        val ids = characterIds.distinct().filter { catalog.get(it) != null }
        if (ids.size < MIN_TO_REVIEW) throw Exception("Añade más personajes al guion antes de revisarlo.")
        val inScript = ids.toSet()
        val review = ai.reviewScript(
            AiScriptReviewRequest(
                name = name.trim(),
                characters = ids.map { catalog.get(it)!! },
                jinxes = jinxesAmong(ids, catalog.jinxes()),
                alternatives = catalog.all().filter { it.team in BAG_TEAMS && it.id !in inScript },
            ),
        )

        // Cada personaje sale o entra una sola vez; lo que no exista o no cuadre con el guion se descarta.
        val used = mutableSetOf<String>()
        val swaps = review.swaps.filter { s ->
            val add = catalog.get(s.addId)
            val ok = s.removeId in inScript && add != null && add.team in BAG_TEAMS && s.addId !in inScript &&
                s.removeId !in used && s.addId !in used
            if (ok) {
                used += s.removeId
                used += s.addId
            }
            ok
        }
        if (review.verdict.isBlank() && review.problems.isEmpty() && swaps.isEmpty()) {
            throw Exception("La IA no ha sabido valorar el guion. Prueba otra vez.")
        }
        return ScriptReview(review.verdict, review.strengths, review.problems, swaps, reviewedIds = ids)
    }
}

/** Diseña un guion con IA a partir de una idea y comprueba que los personajes existen y cuadran por tipo. */
class GenerateAiScript(
    private val catalog: CharacterCatalog,
    private val ai: AiStoryteller,
) {
    suspend fun execute(idea: String, size: ScriptSize): GeneratedScript {
        if (idea.isBlank()) throw Exception("Describe qué guion quieres.")
        val target = SCRIPT_TARGETS.getValue(size)
        val characters = catalog.all().filter { it.team in BAG_TEAMS }
        var previousErrors: List<String>? = null
        var last: Triple<AiScriptProposal, List<String>, List<String>>? = null

        for (attempt in 0 until 2) {
            val proposal = ai.proposeScript(AiScriptRequest(idea.trim(), target, characters, previousErrors))
            val ids = proposal.characterIds.distinct().filter { id -> characters.any { it.id == id } }
            val problems = check(ids, proposal.characterIds, target)
            last = Triple(proposal, ids, problems)
            if (problems.isEmpty()) break
            previousErrors = problems
        }
        val (proposal, ids, problems) = last!!
        if (ids.isEmpty()) throw Exception("La IA no ha propuesto personajes válidos. Prueba con otra idea.")
        return GeneratedScript(
            name = proposal.name.trim().ifEmpty { "Guion sin nombre" },
            description = proposal.description,
            characterIds = ids,
            explanation = proposal.explanation,
            warnings = problems,
        )
    }

    private fun check(ids: List<String>, raw: List<String>, target: Distribution): List<String> {
        val problems = mutableListOf<String>()
        val unknown = raw.filter { catalog.get(it) == null }
        if (unknown.isNotEmpty()) problems += "No existen estos personajes: ${unknown.joinToString(", ")}."
        fun byTeam(team: Team) = ids.count { catalog.get(it)?.team == team }
        val labels = mapOf(Team.Townsfolk to "Aldeanos", Team.Outsider to "Forasteros", Team.Minion to "Esbirros", Team.Demon to "Demonios")
        for (team in BAG_TEAMS) {
            val got = byTeam(team)
            val wanted = target[team]
            if (got != wanted) problems += "Hay $got ${labels.getValue(team)} y deben ser $wanted."
        }
        return problems
    }
}


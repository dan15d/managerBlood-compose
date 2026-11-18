package es.dan15d.grimorio.application.ports

import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Interaction
import es.dan15d.grimorio.domain.entities.Jinx
import es.dan15d.grimorio.domain.services.Distribution
import kotlinx.serialization.Serializable

enum class SetupStyle(val key: String) {
    Equilibrada("equilibrada"),
    Principiantes("principiantes"),
    Caotica("caotica"),
    MuchaInfo("mucha_info"),
    Maligna("maligna"),
}

data class AiSetupRequest(
    val playerCount: Int,
    val playerNames: List<String>,
    val scriptName: String,
    val scriptCharacters: List<Character>,
    val baseDistribution: Distribution,
    /** Notas de los personajes que cambian la preparación (Barón: +2 Forasteros…). */
    val setupModifiers: Map<String, String>,
    val style: SetupStyle,
    val extraInstructions: String? = null,
    /** Si la propuesta anterior no era válida, por qué (para que la IA lo corrija). */
    val previousErrors: List<String>? = null,
)

data class AiSetupProposal(
    val characterIds: List<String>,
    val bluffIds: List<String>,
    /** Personaje que cree ser cada personaje engañado (drunk → washerwoman). */
    val decoys: Map<String, String>,
    val explanation: String,
    val tips: List<String>,
)

data class AiAdviceRequest(
    val game: Game,
    val characters: Map<String, Character>,
    /** Incompatibilidades entre los personajes en juego. */
    val jinxes: List<Jinx>,
    /** Interacciones de la wiki entre los personajes en juego. */
    val interactions: List<Interaction>,
    val question: String,
    /** Lo ya hablado en esta conversación, de más antiguo a más reciente, para poder repreguntar. */
    val history: List<AiAdviceTurn> = emptyList(),
)

@Serializable
data class AiAdviceTurn(val question: String, val answer: String)

data class AiScriptReviewRequest(
    val name: String,
    /** Personajes del guion que se revisa. */
    val characters: List<Character>,
    /** Incompatibilidades entre los personajes del guion. */
    val jinxes: List<Jinx>,
    /** Personajes que no están en el guion, entre los que proponer cambios. */
    val alternatives: List<Character>,
)

/** Cambio concreto: quitar un personaje del guion y poner otro en su lugar. */
data class AiScriptSwap(val removeId: String, val addId: String, val reason: String)

data class AiScriptReview(
    val verdict: String,
    val strengths: List<String>,
    val problems: List<String>,
    val swaps: List<AiScriptSwap>,
)

data class AiChronicleRequest(val game: Game, val characters: Map<String, Character>)

data class AiScriptRequest(
    val idea: String,
    /** Cuántos personajes de cada tipo debe tener el guion. */
    val target: Distribution,
    /** Personajes entre los que elegir. */
    val characters: List<Character>,
    val previousErrors: List<String>? = null,
)

data class AiScriptProposal(
    val name: String,
    val description: String,
    val characterIds: List<String>,
    val explanation: String,
)

/** Narrador con IA. Las implementaciones deciden el proveedor (DeepSeek, función en la nube…). */
interface AiStoryteller {
    fun isAvailable(): Boolean
    /** Pregunta algo trivial para comprobar clave, saldo y modelo. */
    suspend fun checkConnection()
    suspend fun proposeSetup(request: AiSetupRequest): AiSetupProposal
    suspend fun advise(request: AiAdviceRequest): String
    suspend fun proposeScript(request: AiScriptRequest): AiScriptProposal
    /** Opina sobre un guion ya hecho: qué funciona, qué cojea y qué cambiar. */
    suspend fun reviewScript(request: AiScriptReviewRequest): AiScriptReview
    /** Relato de una partida terminada, para leer o compartir con la mesa. */
    suspend fun chronicle(request: AiChronicleRequest): String
}

package es.dan15d.grimorio.application

import es.dan15d.grimorio.application.ports.AiAdviceRequest
import es.dan15d.grimorio.application.ports.AiAdviceTurn
import es.dan15d.grimorio.application.ports.AiChronicleRequest
import es.dan15d.grimorio.application.ports.AiScriptProposal
import es.dan15d.grimorio.application.ports.AiScriptRequest
import es.dan15d.grimorio.application.ports.AiScriptReview
import es.dan15d.grimorio.application.ports.AiScriptReviewRequest
import es.dan15d.grimorio.application.ports.AiScriptSwap
import es.dan15d.grimorio.application.ports.AiSetupProposal
import es.dan15d.grimorio.application.ports.AiSetupRequest
import es.dan15d.grimorio.application.ports.AiStoryteller
import es.dan15d.grimorio.application.usecases.GenerateAiScript
import es.dan15d.grimorio.application.usecases.ReviewAiScript
import es.dan15d.grimorio.application.usecases.SCRIPT_TARGETS
import es.dan15d.grimorio.application.usecases.ScriptSize
import es.dan15d.grimorio.application.usecases.WriteAiChronicle
import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameLogEntry
import es.dan15d.grimorio.domain.entities.GameMode
import es.dan15d.grimorio.domain.entities.GameStatus
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.services.GAME_SCHEMA_VERSION
import es.dan15d.grimorio.infrastructure.ai.ChatMessage
import es.dan15d.grimorio.infrastructure.ai.ChatTransport
import es.dan15d.grimorio.infrastructure.ai.LlmStoryteller
import es.dan15d.grimorio.infrastructure.catalog.loadCatalog
import es.dan15d.grimorio.infrastructure.catalog.loadOfficialScripts
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val catalog = runBlocking { loadCatalog() }
private val tb = runBlocking { loadOfficialScripts() }.list().first { it.id == "trouble-brewing" }
private val tbBag = tb.characterIds.filter { catalog.get(it)?.team != Team.Traveller }

/** IA de mentira: devuelve las respuestas en orden y guarda lo que se le pidió. */
private class FakeAi(
    private val answers: List<AiScriptProposal>,
    private val review: AiScriptReview? = null,
    private val chronicle: String = "",
) : AiStoryteller {
    val requests = mutableListOf<AiScriptRequest>()
    val reviews = mutableListOf<AiScriptReviewRequest>()

    override fun isAvailable() = true
    override suspend fun checkConnection() {}
    override suspend fun proposeSetup(request: AiSetupRequest): AiSetupProposal = throw Exception("no se usa")
    override suspend fun advise(request: AiAdviceRequest) = ""
    override suspend fun reviewScript(request: AiScriptReviewRequest): AiScriptReview {
        reviews += request
        return review ?: AiScriptReview("", emptyList(), emptyList(), emptyList())
    }
    override suspend fun chronicle(request: AiChronicleRequest) = chronicle
    override suspend fun proposeScript(request: AiScriptRequest): AiScriptProposal {
        requests += request
        return answers[minOf(requests.size - 1, answers.size - 1)]
    }
}

private fun proposal(characterIds: List<String>) = AiScriptProposal(
    name = "Prueba",
    description = "Un pueblo inquieto.",
    characterIds = characterIds,
    explanation = "Porque sí.",
)

private fun otherDemons() = catalog.all().filter { it.team == Team.Demon && it.id != "imp" }.take(3).map { it.id }

class CrearGuionConIaTest {
    @Test
    fun aceptaUnGuionClasicoQueCuadraALaPrimera() = runTest {
        // Trouble Brewing tiene 13/4/4/1: le faltan 3 Demonios para ser «clásico» con 4.
        val ai = FakeAi(listOf(proposal(tbBag + otherDemons())))
        val result = GenerateAiScript(catalog, ai).execute("algo clásico", ScriptSize.Classic)
        assertEquals(emptyList(), result.warnings)
        assertEquals(25, result.characterIds.size)
        assertEquals(1, ai.requests.size)
        assertEquals(SCRIPT_TARGETS[ScriptSize.Classic], ai.requests[0].target)
        assertFalse(ai.requests[0].characters.any { it.team == Team.Traveller })
    }

    @Test
    fun reintentaConLosErroresYQuitaPersonajesInventadosORepetidos() = runTest {
        val bad = proposal(tbBag + listOf("inventado", "imp"))
        val ai = FakeAi(listOf(bad, proposal(tbBag + otherDemons())))
        val result = GenerateAiScript(catalog, ai).execute("x", ScriptSize.Classic)
        assertEquals(2, ai.requests.size)
        assertContains(ai.requests[1].previousErrors!!.joinToString(" "), "inventado")
        assertEquals(emptyList(), result.warnings)
    }

    @Test
    fun siSigueSinCuadrarDevuelveLaPropuestaConAvisosParaRetocarla() = runTest {
        val ai = FakeAi(listOf(proposal(tbBag)))
        val result = GenerateAiScript(catalog, ai).execute("x", ScriptSize.Classic)
        assertEquals(22, result.characterIds.size)
        assertContains(result.warnings.joinToString(" "), "Demonios")
    }
}

class RevisarGuionConIaTest {
    private fun review(swaps: List<AiScriptSwap>) = AiScriptReview(
        verdict = "Un guion sólido.",
        strengths = listOf("Mucha información."),
        problems = listOf("Poca desinformación."),
        swaps = swaps,
    )

    @Test
    fun mandaElGuionSusJinxesYSoloAlternativasDeFuera() = runTest {
        val ai = FakeAi(emptyList(), review(emptyList()))
        val result = ReviewAiScript(catalog, ai).execute(" Mi guion ", tb.characterIds)
        assertEquals("Mi guion", ai.reviews[0].name)
        assertEquals(tb.characterIds, ai.reviews[0].characters.map { it.id })
        assertFalse(ai.reviews[0].alternatives.any { it.id in tb.characterIds })
        assertFalse(ai.reviews[0].alternatives.any { it.team == Team.Traveller || it.team == Team.Fabled })
        assertEquals(tb.characterIds, result.reviewedIds)
        assertEquals("Un guion sólido.", result.verdict)
    }

    @Test
    fun descartaLosCambiosQueNoSePuedenAplicar() = runTest {
        val outside = catalog.all().filter { it.team == Team.Townsfolk && it.id !in tb.characterIds }
        val good = AiScriptSwap(removeId = "washerwoman", addId = outside[0].id, reason = "Más variedad.")
        val ai = FakeAi(
            emptyList(),
            review(
                listOf(
                    good,
                    AiScriptSwap("inventado", outside[1].id, "No está en el guion."),
                    AiScriptSwap("chef", "imp", "Ya está en el guion."),
                    AiScriptSwap("empath", "nadie", "No existe."),
                    AiScriptSwap("washerwoman", outside[2].id, "Repite al que sale."),
                    AiScriptSwap("monk", outside[0].id, "Repite al que entra."),
                ),
            ),
        )
        val result = ReviewAiScript(catalog, ai).execute("x", tb.characterIds)
        assertEquals(listOf(good), result.swaps)
    }

    @Test
    fun noRevisaUnGuionCasiVacioNiAceptaUnaRevisionEnBlanco() = runTest {
        val ai = FakeAi(emptyList())
        val few = assertFailsWith<Exception> { ReviewAiScript(catalog, ai).execute("x", listOf("imp", "chef")) }
        assertContains(few.message!!, "Añade más personajes")
        assertEquals(0, ai.reviews.size)
        val blank = assertFailsWith<Exception> { ReviewAiScript(catalog, ai).execute("x", tb.characterIds) }
        assertContains(blank.message!!, "no ha sabido")
    }
}

private fun finishedGame(status: GameStatus = GameStatus.Finished, winner: Alignment? = Alignment.Good) = Game(
    schemaVersion = GAME_SCHEMA_VERSION,
    id = "g",
    name = "Prueba",
    mode = GameMode.Manual,
    scriptId = "trouble-brewing",
    scriptName = "Trouble Brewing",
    players = listOf("imp", "poisoner", "chef", "empath", "monk").mapIndexed { seat, characterId ->
        Player(
            id = "p$seat",
            seat = seat,
            name = "J${seat + 1}",
            characterId = characterId,
            alignment = if (seat < 2) Alignment.Evil else Alignment.Good,
            alive = seat != 0,
            ghostVoteUsed = false,
            reminders = emptyList(),
        )
    },
    demonBluffs = listOf("soldier"),
    phase = Phase.day(3),
    nightStep = 0,
    nominations = emptyList(),
    status = status,
    winner = winner,
    log = listOf(GameLogEntry(at = "", phase = "Día 3", text = "Ejecutan a J1.")),
    joinCode = "",
    createdAt = "",
    updatedAt = "",
)

/** Transporte de mentira: guarda lo que se le manda y responde siempre lo mismo. */
private class FakeTransport(private val answer: String) : ChatTransport {
    val calls = mutableListOf<List<ChatMessage>>()
    override suspend fun complete(messages: List<ChatMessage>, options: es.dan15d.grimorio.infrastructure.ai.ChatOptions): String {
        calls += messages
        return answer
    }
}

class CronicaDeLaPartidaTest {
    @Test
    fun soloSeEscribeConLaPartidaTerminada() = runTest {
        val ai = FakeAi(emptyList(), chronicle = "  Titular\nLo que pasó.  ")
        val write = WriteAiChronicle(ai, catalog)
        val error = assertFailsWith<Exception> { write.execute(finishedGame(status = GameStatus.InProgress, winner = null)) }
        assertContains(error.message!!, "cuando termina")
        assertEquals("Titular\nLo que pasó.", write.execute(finishedGame()))
    }

    @Test
    fun cuentaQuienEraQuienElResultadoYElRegistro() = runTest {
        val transport = FakeTransport("Titular\nTexto.")
        LlmStoryteller(transport).chronicle(AiChronicleRequest(finishedGame(), catalog.map()))
        val prompt = transport.calls[0][1].content
        assertContains(prompt, "ganó el Bien")
        assertContains(prompt, "J1: ${catalog.get("imp")!!.name}")
        assertContains(prompt, "[Día 3] Ejecutan a J1.")
    }
}

class ConsejeroConMemoriaTest {
    @Test
    fun recuerdaLaConversacionEnOrdenYSoloMandaElGrimorioDeAhora() = runTest {
        val transport = FakeTransport("Respuesta")
        LlmStoryteller(transport).advise(
            AiAdviceRequest(
                game = finishedGame(),
                characters = catalog.map(),
                jinxes = emptyList(),
                interactions = emptyList(),
                question = "¿Y si protege a otro?",
                history = listOf(
                    AiAdviceTurn("¿A quién protege el Monje?", "A J4."),
                    AiAdviceTurn("¿Por qué?", "Porque es la Empática."),
                ),
            ),
        )
        val messages = transport.calls[0]
        assertEquals(listOf("system", "user", "assistant", "user", "assistant", "user"), messages.map { it.role })
        assertEquals("Pregunta del Narrador: ¿A quién protege el Monje?", messages[1].content)
        assertEquals("Porque es la Empática.", messages[4].content)
        assertEquals(1, messages.count { it.content.contains("Grimorio (en orden de asiento") })
        assertTrue(Regex("Grimorio[\\s\\S]*¿Y si protege a otro\\?$").containsMatchIn(messages[5].content))
    }

    @Test
    fun deUnaConversacionLargaSoloMandaLosUltimosTurnos() = runTest {
        val transport = FakeTransport("Respuesta")
        val history = (0 until 10).map { AiAdviceTurn("P$it", "R$it") }
        LlmStoryteller(transport).advise(
            AiAdviceRequest(finishedGame(), catalog.map(), emptyList(), emptyList(), "Última", history),
        )
        assertEquals(1 + 6 * 2 + 1, transport.calls[0].size)
        assertEquals("Pregunta del Narrador: P4", transport.calls[0][1].content)
    }
}

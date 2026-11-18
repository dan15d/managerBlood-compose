package es.dan15d.grimorio.infrastructure.ai

import es.dan15d.grimorio.application.ports.AiAdviceRequest
import es.dan15d.grimorio.application.ports.AiChronicleRequest
import es.dan15d.grimorio.application.ports.AiScriptProposal
import es.dan15d.grimorio.application.ports.AiScriptRequest
import es.dan15d.grimorio.application.ports.AiScriptReview
import es.dan15d.grimorio.application.ports.AiScriptReviewRequest
import es.dan15d.grimorio.application.ports.AiScriptSwap
import es.dan15d.grimorio.application.ports.AiSetupProposal
import es.dan15d.grimorio.application.ports.AiSetupRequest
import es.dan15d.grimorio.application.ports.AiStoryteller
import es.dan15d.grimorio.application.ports.SetupStyle
import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Edition
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.entities.label
import es.dan15d.grimorio.domain.services.phaseLabel
import es.dan15d.grimorio.infrastructure.GrimJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Turnos anteriores que se le recuerdan al consejero: los últimos, para que el coste no crezca sin fin. */
private const val ADVICE_HISTORY_LIMIT = 6

/** Entradas del registro que se mandan para la crónica. */
private const val CHRONICLE_LOG_LIMIT = 300

private val STYLE: Map<SetupStyle, String> = mapOf(
    SetupStyle.Equilibrada to "Una partida equilibrada en la que ambos equipos tengan opciones reales.",
    SetupStyle.Principiantes to "Para jugadores nuevos: personajes sencillos, mucha información clara para el Bien y Esbirros fáciles de jugar.",
    SetupStyle.Caotica to "Caótica y divertida: combinaciones que generen confusión, Borrachos/Envenenados y giros inesperados.",
    SetupStyle.MuchaInfo to "Con mucha información para el equipo Bueno, para que se hable mucho desde el principio.",
    SetupStyle.Maligna to "Que favorezca un poco al Mal: personajes buenos con información frágil y Esbirros potentes.",
)

private const val BASE_RULES = """Eres un Narrador experto de Blood on the Clocktower y ayudas a otro Narrador en una partida presencial.
Hablas en español de España, claro y directo. Conoces las reglas oficiales: el Bien gana si muere el Demonio; el Mal gana si quedan 2 jugadores vivos;
con 7 o más jugadores, la primera noche los Esbirros conocen al Demonio y el Demonio a sus Esbirros y recibe 3 faroles (personajes buenos que no están en juego).
Los jugadores borrachos o envenenados no tienen habilidad y pueden recibir información falsa. El Narrador tiene la última palabra."""

private fun parseJson(text: String): JsonObject {
    val start = text.indexOf("{")
    val end = text.lastIndexOf("}")
    if (start < 0 || end <= start) throw Exception("La IA no ha devuelto un JSON.")
    return GrimJson.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject
        ?: throw Exception("La IA no ha devuelto un JSON.")
}

private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun asStringArray(v: JsonElement?): List<String> =
    (v as? JsonArray)?.mapNotNull { it.asString() } ?: emptyList()

private fun editionLabel(edition: Edition): String = when (edition) {
    Edition.Tb -> "TB"
    Edition.Bmr -> "BMR"
    Edition.Snv -> "S&V"
    Edition.Exp -> "Exp"
    Edition.Custom -> "custom"
}

private fun alignmentLabel(alignment: Alignment) = if (alignment == Alignment.Good) "Bueno" else "Malvado"

/** Narrador con un modelo de lenguaje. El transporte decide si va por la nube o desde el dispositivo. */
class LlmStoryteller(private val transport: ChatTransport?) : AiStoryteller {
    override fun isAvailable(): Boolean = transport != null

    private val chat: ChatTransport
        get() = transport ?: throw Exception("El modo IA no está configurado. Ve a Ajustes para conectarlo.")

    override suspend fun checkConnection() {
        chat.complete(listOf(ChatMessage("user", "Responde solo: OK")), ChatOptions(temperature = 0.0))
    }

    override suspend fun proposeSetup(request: AiSetupRequest): AiSetupProposal {
        val r = request
        val roster = r.scriptCharacters.joinToString("\n") { c -> "- ${c.id} | ${c.name} | ${c.team.label.singular} | ${c.ability}" }
        val mods = r.setupModifiers.entries.joinToString("\n") { (id, note) -> "- $id: $note" }
        val d = r.baseDistribution
        val extra = if (!r.extraInstructions.isNullOrEmpty()) "Indicaciones del Narrador: ${r.extraInstructions}\n" else ""
        val previous = if (!r.previousErrors.isNullOrEmpty()) {
            "\nTu propuesta anterior no era válida: ${r.previousErrors.joinToString(" ")} Corrígelo.\n"
        } else {
            ""
        }
        val prompt = """Prepara la bolsa de personajes para una partida del guion «${r.scriptName}» con ${r.playerCount} jugadores.

Distribución base: ${d.townsfolk} Aldeanos, ${d.outsider} Forasteros, ${d.minion} Esbirros, ${d.demon} Demonio.
Personajes que cambian la distribución si los eliges (ajusta Forasteros/Esbirros quitando o añadiendo Aldeanos):
${mods.ifEmpty { "- ninguno" }}

Estilo pedido: ${STYLE.getValue(r.style)}
$extra
Personajes disponibles (id | nombre | tipo | habilidad):
$roster

Reglas de la respuesta:
- "characterIds": exactamente ${if (r.playerCount > 15) 15 else r.playerCount} ids distintos de la lista, cumpliendo la distribución (con sus ajustes).
- "bluffIds": 3 ids de Aldeanos o Forasteros que NO estén en characterIds (mejor 2 Aldeanos y 1 Forastero).
- "decoys": si eliges Borracho (drunk) o Marioneta (marionette), qué Aldeano creen ser (que no esté en juego); si eliges Lunático (lunatic), qué Demonio cree ser. Formato {"drunk": "id"}.
- "explanation": 2-4 frases explicando por qué este reparto es interesante y qué interacciones vigilar.
- "tips": 3-5 consejos concretos para el Narrador (a quién dar información falsa, qué recordar por la noche…).
$previous
Responde SOLO con un objeto JSON: {"characterIds": [], "bluffIds": [], "decoys": {}, "explanation": "", "tips": []}"""

        val text = chat.complete(
            listOf(ChatMessage("system", BASE_RULES), ChatMessage("user", prompt)),
            ChatOptions(json = true, temperature = 0.9),
        )
        val data = parseJson(text)
        return AiSetupProposal(
            characterIds = asStringArray(data["characterIds"]),
            bluffIds = asStringArray(data["bluffIds"]),
            decoys = (data["decoys"] as? JsonObject)?.mapNotNull { (k, v) -> v.asString()?.let { k to it } }?.toMap() ?: emptyMap(),
            explanation = data["explanation"].asString() ?: "",
            tips = asStringArray(data["tips"]),
        )
    }

    override suspend fun proposeScript(request: AiScriptRequest): AiScriptProposal {
        val r = request
        val roster = r.characters.joinToString("\n") { c ->
            "- ${c.id} | ${c.name} | ${c.team.label.singular} | ${editionLabel(c.edition)} | ${c.ability}"
        }
        val t = r.target
        val previous = if (!r.previousErrors.isNullOrEmpty()) {
            "\nTu propuesta anterior no era válida: ${r.previousErrors.joinToString(" ")} Corrígelo.\n"
        } else {
            ""
        }
        val prompt = """Diseña un guion personalizado de Blood on the Clocktower a partir de esta idea del Narrador:
«${r.idea}»

Debe tener exactamente ${t.townsfolk} Aldeanos, ${t.outsider} Forasteros, ${t.minion} Esbirros y ${t.demon} Demonios (${t.townsfolk + t.outsider + t.minion + t.demon} personajes).
Criterios de un buen guion: suficiente información para el Bien, Esbirros y Demonios que puedan farolear con los Aldeanos del guion,
interacciones interesantes (envenenamiento, borrachera, locura…) y que el tema de la idea se note. No repitas personajes.

Personajes disponibles (id | nombre | tipo | edición | habilidad):
$roster
$previous
Responde SOLO con un objeto JSON: {"name": "nombre evocador en español", "description": "2-3 frases de ambientación", "characterIds": ["id", ...], "explanation": "2-4 frases sobre por qué funciona y qué interacciones destacan"}"""

        val text = chat.complete(
            listOf(ChatMessage("system", BASE_RULES), ChatMessage("user", prompt)),
            ChatOptions(json = true, temperature = 0.9),
        )
        val data = parseJson(text)
        return AiScriptProposal(
            name = data["name"].asString() ?: "",
            description = data["description"].asString() ?: "",
            characterIds = asStringArray(data["characterIds"]),
            explanation = data["explanation"].asString() ?: "",
        )
    }

    override suspend fun reviewScript(request: AiScriptReviewRequest): AiScriptReview {
        val r = request
        fun line(c: Character) = "- ${c.id} | ${c.name} | ${c.team.label.singular} | ${c.ability}"
        fun nameOf(id: String) = r.characters.find { it.id == id }?.name ?: id
        fun count(team: Team) = r.characters.count { it.team == team }
        val jinxes = r.jinxes.joinToString(" | ") { j -> "${nameOf(j.characterIds[0])} + ${nameOf(j.characterIds[1])}: ${j.text}" }
        val prompt = """Revisa este guion personalizado de Blood on the Clocktower como lo haría un diseñador de guiones con experiencia.

Guion «${r.name.ifEmpty { "Sin nombre" }}»: ${count(Team.Townsfolk)} Aldeanos, ${count(Team.Outsider)} Forasteros, ${count(Team.Minion)} Esbirros, ${count(Team.Demon)} Demonios.
Un guion clásico tiene 13 / 4 / 4 / 4 y uno pequeño (5-6 jugadores) 6 / 2 / 2 / 1; si el reparto se aparta de eso, dilo.

Personajes del guion (id | nombre | tipo | habilidad):
${r.characters.joinToString("\n") { line(it) }}

Incompatibilidades (jinxes) entre ellos: ${jinxes.ifEmpty { "ninguna" }}

Qué mirar:
- Información del Bien: si hay suficiente y de tipos distintos (primera noche, cada noche, al morir, de día), y cuánta puede falsear el Mal.
- Desinformación: fuentes de borrachera, envenenamiento o locura, y si son demasiadas o demasiado pocas para la información que hay.
- Protección y muertes extra: si el Demonio puede quedarse sin matar o si el pueblo se vacía demasiado rápido.
- Faroles: si Esbirros y Demonios tienen Aldeanos y Forasteros creíbles que fingir, y si algún personaje se confirma solo con demasiada facilidad.
- Cada Demonio y cada Esbirro: si alguno queda flojo, demasiado fuerte o sin apoyo en este guion.
- Forasteros: si molestan de verdad al Bien y si hay algo que cambie cuántos hay.
- Combinaciones concretas que rompen la partida, la alargan o son difíciles de arbitrar.

Personajes que NO están en el guion y puedes proponer a cambio (id | nombre | tipo | habilidad):
${r.alternatives.joinToString("\n") { line(it) }}

Reglas de la respuesta:
- "verdict": 2-3 frases con la valoración general y para qué tipo de mesa sirve.
- "strengths": 2-4 puntos fuertes, cada uno nombrando personajes concretos.
- "problems": de 0 a 5 problemas reales, de más grave a menos, cada uno nombrando personajes concretos. No inventes problemas si el guion está bien.
- "swaps": de 0 a 4 cambios, cada uno {"removeId": id del guion, "addId": id de la lista de fuera, "reason": una frase}. Mejor del mismo tipo, salvo que quieras corregir el reparto.
Escribe los nombres de los personajes como aparecen en las listas, nunca los ids, salvo en removeId y addId.

Responde SOLO con un objeto JSON: {"verdict": "", "strengths": [], "problems": [], "swaps": [{"removeId": "", "addId": "", "reason": ""}]}"""

        val text = chat.complete(
            listOf(ChatMessage("system", BASE_RULES), ChatMessage("user", prompt)),
            ChatOptions(json = true, temperature = 0.5),
        )
        val data = parseJson(text)
        val swaps = (data["swaps"] as? JsonArray ?: JsonArray(emptyList()))
            .filterIsInstance<JsonObject>()
            .map { s ->
                AiScriptSwap(
                    removeId = s["removeId"].asString() ?: "",
                    addId = s["addId"].asString() ?: "",
                    reason = s["reason"].asString() ?: "",
                )
            }
        return AiScriptReview(
            verdict = data["verdict"].asString() ?: "",
            strengths = asStringArray(data["strengths"]),
            problems = asStringArray(data["problems"]),
            swaps = swaps,
        )
    }

    override suspend fun chronicle(request: AiChronicleRequest): String {
        val game = request.game
        val characters = request.characters
        fun name(id: String?) = if (!id.isNullOrEmpty()) characters[id]?.name ?: id else ""
        val cast = game.players.joinToString("\n") { p ->
            val c = characters[p.characterId]
            val parts = mutableListOf(
                "${p.name}: ${c?.name ?: p.characterId} (${c?.team?.label?.singular ?: "?"}, ${alignmentLabel(p.alignment)})",
                if (p.alive) "acabó vivo" else "acabó muerto",
            )
            if (!p.shownCharacterId.isNullOrEmpty()) parts += "creía ser ${name(p.shownCharacterId)}"
            parts.joinToString(" · ")
        }
        // Con las partidas más largas basta el final del registro para no disparar el coste.
        val log = game.log.takeLast(CHRONICLE_LOG_LIMIT).joinToString("\n") { "[${it.phase}] ${it.text}" }
        val result = when (game.winner) {
            Alignment.Good -> "ganó el Bien"
            Alignment.Evil -> "ganó el Mal"
            null -> "sin ganador declarado"
        }
        val bluffs = game.demonBluffs.joinToString(", ") { name(it) }
        val prompt = """Escribe la crónica de esta partida ya terminada, para leerla en voz alta o mandarla al grupo.

Partida «${game.name}», guion ${game.scriptName}. Resultado: $result.

Quién era quién (ya se puede contar todo, la partida ha acabado):
$cast
Faroles del Demonio: ${bluffs.ifEmpty { "ninguno" }}

Registro de la partida, en orden:
${log.ifEmpty { "(vacío)" }}

Cómo escribirla:
- Primera línea: un titular corto. Después, 3 o 4 párrafos breves (unas 220 palabras en total).
- Cuenta lo que pasó de verdad, en orden, usando los nombres de los jugadores y sus personajes: las muertes, las ejecuciones, las votaciones ajustadas y el momento que decidió la partida.
- Destaca la mejor jugada y el engaño que mejor funcionó, si el registro los deja ver.
- Tono de cronista de pueblo, con algo de humor negro, sin burlarte de nadie.
- No inventes hechos que no estén en el registro. Si el registro es pobre, haz la crónica más corta.
- Texto plano, sin Markdown, sin asteriscos y sin listas."""

        return chat.complete(
            listOf(ChatMessage("system", BASE_RULES), ChatMessage("user", prompt)),
            ChatOptions(temperature = 0.9),
        )
    }

    override suspend fun advise(request: AiAdviceRequest): String {
        val game = request.game
        val characters = request.characters
        fun name(id: String?) = if (!id.isNullOrEmpty()) characters[id]?.name ?: id else ""
        val grimoire = game.players.joinToString("\n") { p ->
            val c = characters[p.characterId]
            val parts = mutableListOf(
                "${p.seat + 1}. ${p.name}: ${c?.name ?: p.characterId} (${c?.team?.label?.singular ?: "?"}, ${alignmentLabel(p.alignment)})",
                if (p.alive) "vivo" else "muerto${if (p.ghostVoteUsed) ", sin voto" else ", con voto"}",
            )
            if (!p.shownCharacterId.isNullOrEmpty()) parts += "cree ser ${name(p.shownCharacterId)}"
            if (p.reminders.isNotEmpty()) parts += "recordatorios: ${p.reminders.joinToString(", ") { it.label }}"
            if (!p.notes.isNullOrEmpty()) parts += "notas: ${p.notes}"
            parts.joinToString(" · ")
        }
        val recent = game.log.takeLast(25).joinToString("\n") { "[${it.phase}] ${it.text}" }
        val bluffs = game.demonBluffs.joinToString(", ") { name(it) }
        val fabled = game.fabledIds.joinToString("; ") { id -> "${name(id)} (${characters[id]?.ability ?: ""})" }
        val jinxes = request.jinxes.joinToString(" | ") { j -> "${name(j.characterIds[0])} + ${name(j.characterIds[1])}: ${j.text}" }
        val interactions = request.interactions.joinToString(" | ") { j -> "${name(j.characterIds[0])} + ${name(j.characterIds[1])}: ${j.text}" }
        val context = """Partida «${game.name}» (guion ${game.scriptName}), ${phaseLabel(game.phase)}.
Grimorio (en orden de asiento, en sentido horario):
$grimoire
Faroles del Demonio: ${bluffs.ifEmpty { "ninguno" }}
Fábulas y Lóricos en juego: ${fabled.ifEmpty { "ninguno" }}
Incompatibilidades (jinxes) en juego: ${jinxes.ifEmpty { "ninguna" }}
Casos especiales de la wiki entre personajes en juego: ${interactions.ifEmpty { "ninguno" }}
Últimos sucesos:
$recent"""

        val system = """$BASE_RULES
Responde en pocas líneas y de forma práctica. Si propones información falsa, que sea creíble y útil para la partida.
El Narrador puede repreguntar sobre lo ya hablado: el estado de la partida que vale es siempre el del último mensaje."""

        return chat.complete(
            buildList {
                add(ChatMessage("system", system))
                // Lo ya hablado va sin su Grimorio de entonces: solo cuenta el de ahora, que viaja con la pregunta nueva.
                for (turn in request.history.takeLast(ADVICE_HISTORY_LIMIT)) {
                    add(ChatMessage("user", "Pregunta del Narrador: ${turn.question}"))
                    add(ChatMessage("assistant", turn.answer))
                }
                add(ChatMessage("user", "$context\n\nPregunta del Narrador: ${request.question}"))
            },
            ChatOptions(temperature = 0.7),
        )
    }
}

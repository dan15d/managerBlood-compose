package es.dan15d.grimorio.presentation.pages.game

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import es.dan15d.grimorio.application.ports.GameConflictError
import es.dan15d.grimorio.application.ports.GameNotFoundError
import es.dan15d.grimorio.application.usecases.SaveQueue
import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameStatus
import es.dan15d.grimorio.domain.entities.ReminderToken
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.nowIso
import es.dan15d.grimorio.domain.services.NightStep
import es.dan15d.grimorio.domain.services.VictoryHint
import es.dan15d.grimorio.domain.services.advanceAfterChoice
import es.dan15d.grimorio.domain.services.applyReminderChoice
import es.dan15d.grimorio.domain.services.buildNightOrder
import es.dan15d.grimorio.domain.services.callExile
import es.dan15d.grimorio.domain.services.canExile
import es.dan15d.grimorio.domain.services.canNominate
import es.dan15d.grimorio.domain.services.demonSuccession
import es.dan15d.grimorio.domain.services.duskVictory
import es.dan15d.grimorio.domain.services.executeNow
import es.dan15d.grimorio.domain.services.expiringReminders
import es.dan15d.grimorio.domain.services.keepNightStep
import es.dan15d.grimorio.domain.services.nominate
import es.dan15d.grimorio.domain.services.onTheBlock
import es.dan15d.grimorio.domain.services.pitHagChoose
import es.dan15d.grimorio.domain.services.removeReminder
import es.dan15d.grimorio.domain.services.slayerShot
import es.dan15d.grimorio.domain.services.startNight
import es.dan15d.grimorio.domain.services.swapSeats
import es.dan15d.grimorio.domain.services.victoryHint
import es.dan15d.grimorio.domain.services.victoryNote
import es.dan15d.grimorio.domain.services.virginNomination
import es.dan15d.grimorio.domain.services.witchCursed
import es.dan15d.grimorio.presentation.app.AppState
import es.dan15d.grimorio.presentation.app.Container
import es.dan15d.grimorio.presentation.app.ToastAction
import es.dan15d.grimorio.presentation.app.ToastKind
import es.dan15d.grimorio.presentation.app.errorMessage
import es.dan15d.grimorio.presentation.components.InfoTokenDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** Pestañas del panel: la fase (noche o día), el consejero de IA y el registro. */
enum class GameTab { Phase, Ai, Log }

/** Adónde mover la pantalla: arriba del todo (al Grimorio) o al panel. */
enum class ScrollTarget { Top, Panel }

/** Modo «toca una ficha»: poner un recordatorio o nominar sin abrir la hoja del jugador. */
sealed interface Pick {
    /** `advance`: es la acción principal del paso de noche; al tocar, la hoja pasa sola si no queda nada por hacer. */
    data class Reminder(val sourceId: String, val label: String, val advance: Boolean = false) : Pick
    data object Nominator : Pick
    data class Nominee(val nominatorId: String) : Pick
    /** Cambiar sitios: se toca a uno y luego a otro, y se intercambian. Sigue activo hasta «Listo». */
    data class Seat(val fromId: String? = null) : Pick
    /** Ejecutar sin votación (Virgen, Juez, Visir, locura…). */
    data object Execute : Pick
    /** Pedir el exilio de un Viajero. */
    data object Exile : Pick
    /** Disparo del Exterminador: primero quién dispara y después a quién. */
    data object Shooter : Pick
    data class Shot(val shooterId: String) : Pick
}

/** Una pregunta al consejero y lo que respondió (o el error, que no cuenta como conversación). */
data class AiExchange(val q: String, val a: String, val error: Boolean = false)

/** Tarjeta a pantalla completa para enseñar al jugador (faroles, «Este es el Demonio»…). */
data class ShowSpec(val heading: String?, val characters: List<Character>)

private const val UNDO_LIMIT = 30

/** Solo ha cambiado el paso de la noche, la cuenta atrás o la hora de guardado: nada del Grimorio. */
internal fun onlyNavigation(a: Game, b: Game): Boolean =
    a.copy(nightStep = 0, timer = null, updatedAt = "") == b.copy(nightStep = 0, timer = null, updatedAt = "")

/**
 * Estado de la partida abierta y lo que se puede hacer con ella. Las piezas de la pantalla solo
 * leen de aquí y llaman a sus acciones; cada acción aplica una regla del dominio, guarda (en cola,
 * con reintento) y deja apuntado cómo deshacerla.
 */
@Stable
class GameController(
    val id: String,
    val app: Container,
    private val appState: AppState,
    /** Ámbito de la pantalla: guardados, vigilancia y consultas a la IA. */
    val scope: CoroutineScope,
    /** La partida ya no existe o no se puede abrir: vuelta a la portada. */
    private val onGone: () -> Unit,
) {
    val characters: Map<String, Character> = app.catalog.map()

    var game by mutableStateOf<Game?>(null)
        private set

    /** Personajes del guion (y todos los Viajeros), para los selectores. */
    var scriptCharacters by mutableStateOf<List<Character>>(emptyList())
        private set

    var canUndo by mutableStateOf(false)
        private set

    // Estados anteriores para «Deshacer» (solo en esta pantalla; al recargar se pierden).
    private val history = ArrayDeque<Game>()

    // Versión que tiene la nube (la cargada, la recibida o la última guardada): al guardar se exige que
    // siga siendo esa, para no pisar lo que haya hecho otro dispositivo entretanto.
    private var syncedAt: String? = null

    // Versiones guardadas desde aquí: cuando la nube las devuelve por el canal, no son de otro.
    private val ownVersions = ArrayDeque<String>()

    // ---------- Estado de la interfaz ----------

    var tab by mutableStateOf(GameTab.Phase)
    var hidden by mutableStateOf(false)
    var pick by mutableStateOf<Pick?>(null)

    /** Jugador tocado en el círculo: mini-hoja de acciones rápidas (la ficha entera se abre desde ella). */
    var quick by mutableStateOf<String?>(null)

    /** Jugador con la ficha entera abierta. */
    var selected by mutableStateOf<String?>(null)
    var ending by mutableStateOf(false)
    var addingFabled by mutableStateOf(false)
    var composing by mutableStateOf(false)
    var pitHagTarget by mutableStateOf<String?>(null)
    var chronicleOpen by mutableStateOf(false)
    var editingBluffs by mutableStateOf(false)
    var dismissedHint by mutableStateOf("")

    // Noche: tarjeta para enseñar, ficha de información, Filósofo, guía y lista de pasos desplegada.
    var card by mutableStateOf<ShowSpec?>(null)
    var nightComposing by mutableStateOf<InfoTokenDraft?>(null)
    var philosophising by mutableStateOf(false)
    var guideOpen by mutableStateOf(false)
    var allSteps by mutableStateOf(false)

    // Día: anochecer sin ejecutar, recordatorios que caducan y victoria al anochecer.
    var confirmDusk by mutableStateOf(false)

    /** Recordatorios marcados para quitar al anochecer (null: la hoja está cerrada). */
    var cleaning by mutableStateOf<List<String>?>(null)

    /** Victoria que se decide al anochecer (Vortox, Alcalde, Leviatán…). */
    var duskWin by mutableStateOf<VictoryHint?>(null)

    // Fábulas y Lóricos: la ficha abierta y si se está enseñando.
    var fabledOpen by mutableStateOf<Character?>(null)
    var fabledShowing by mutableStateOf(false)

    // La conversación con el consejero vive aquí para que no se pierda al cambiar de pestaña.
    var aiThread by mutableStateOf<List<AiExchange>>(emptyList())
    var aiLoading by mutableStateOf(false)
        private set

    private val scrolls = MutableSharedFlow<ScrollTarget>(extraBufferCapacity = 4)
    val scrollRequests: SharedFlow<ScrollTarget> get() = scrolls

    fun scrollTo(target: ScrollTarget) {
        scrolls.tryEmit(target)
    }

    // ---------- Lo que se deriva de la partida (se calcula solo cuando cambia) ----------

    val steps: List<NightStep> by derivedStateOf { game?.let { buildNightOrder(it, characters) } ?: emptyList() }

    val stepIndex: Int by derivedStateOf { minOf(game?.nightStep ?: 0, steps.size - 1).coerceAtLeast(0) }

    /** El paso de la noche en el que se va (null de día). */
    val step: NightStep? by derivedStateOf { if (game?.phase?.isNight == true) steps.getOrNull(stepIndex) else null }

    val hint: VictoryHint? by derivedStateOf { game?.let { victoryHint(it, characters) } }
    val succession by derivedStateOf { game?.let { demonSuccession(it, characters) } }

    /** Por qué la partida sigue aunque haya muerto el Demonio (Mente maestra, Gemela malvada). */
    val note: String? by derivedStateOf { game?.let { victoryNote(it, characters) } }

    /** Quién vive y quién no, para que «La partida sigue» valga hasta la próxima muerte. */
    private val aliveKey: String by derivedStateOf { game?.players?.joinToString("") { if (it.alive) "1" else "0" } ?: "" }
    val hintKey: String by derivedStateOf { hint?.let { "${it.winner.key}:$aliveKey" } ?: "" }
    val successionKey: String by derivedStateOf { if (succession != null) "succ:${game?.phase?.kind}:$aliveKey" else "" }

    /** Durante la nominación se resaltan quienes pueden nominar o ser nominados. */
    val highlighted: Set<String> by derivedStateOf {
        val g = game ?: return@derivedStateOf emptySet()
        when (val p = pick) {
            Pick.Nominator -> g.players.filter { pl -> pl.alive && g.nominations.none { it.nominatorId == pl.id } }.map { it.id }.toSet()
            is Pick.Nominee -> g.players.filter { pl -> g.nominations.none { it.nomineeId == pl.id } }.map { it.id }.toSet()
            is Pick.Seat -> if (p.fromId != null) setOf(p.fromId) else g.players.map { it.id }.toSet()
            Pick.Exile -> g.players.filter { it.alive && characters[it.characterId]?.team == Team.Traveller }.map { it.id }.toSet()
            Pick.Execute, is Pick.Shot -> g.players.map { it.id }.toSet()
            Pick.Shooter -> g.players.filter { it.alive }.map { it.id }.toSet()
            else -> step?.playerIds?.toSet() ?: emptySet()
        }
    }

    fun name(playerId: String): String = game?.players?.find { it.id == playerId }?.name ?: ""

    // ---------- Carga, vigilancia y guardado ----------

    /**
     * Ámbito de lo que debe terminar aunque se salga de la pantalla (el último guardado, la crónica):
     * el mismo hilo que la interfaz, pero no se cancela con ella.
     */
    val workScope: CoroutineScope = CoroutineScope(scope.coroutineContext.minusKey(Job) + SupervisorJob())

    // Los guardados van en cola; si uno falla (sin cobertura…) se reintenta solo hasta que entre.
    private val saves: SaveQueue<Game> = SaveQueue(
        workScope,
        saveFn = { save(it) },
        onError = { e -> appState.toast("${errorMessage(e)} Se volverá a intentar solo.", ToastKind.Error) },
        onRecovered = { appState.toast("Guardado: los móviles ya están al día.") },
    )

    private suspend fun save(g: Game) {
        try {
            app.saveGame.execute(g, syncedAt)
            syncedAt = g.updatedAt
        } catch (e: GameConflictError) {
            // Otro dispositivo guardó antes: manda su versión y lo que había en cola aquí se descarta.
            // Solo si ya no existe se da por borrada; si no se puede leer (sin cobertura…), se avisa y se reintenta.
            val remote = try {
                app.loadGame.execute(g.id)
            } catch (_: GameNotFoundError) {
                null
            }
            saves.discard()
            adopt(remote)
        }
    }

    /** Abre la partida y los personajes de su guion. Si no se puede, se avisa y se vuelve a la portada. */
    suspend fun load() {
        try {
            val g = app.loadGame.execute(id)
            syncedAt = g.updatedAt
            ownVersions.clear()
            ownVersions.addLast(g.updatedAt)
            game = g
            val script = app.listScripts.execute().find { it.id == g.scriptId }
            val ids = script?.characterIds ?: app.catalog.all().map { it.id }
            val travellers = app.catalog.all().filter { it.team == Team.Traveller }.map { it.id }
            scriptCharacters = (ids + travellers).distinct().mapNotNull { app.catalog.get(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            appState.toast(errorMessage(e), ToastKind.Error)
            onGone()
        }
    }

    /** Si la misma partida está abierta en otro dispositivo (el PC y el móvil), lo que se hace allí llega aquí. */
    suspend fun watch() {
        try {
            app.watchGame.execute(id).collect { remote ->
                if (game == null) return@collect
                if (remote != null && remote.updatedAt in ownVersions) return@collect
                saves.discard()
                adopt(remote)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Sin canal de cambios (sin conexión): se sigue guardando igual.
        }
    }

    /** Reintento: cada 10 segundos mientras falte guardar. */
    suspend fun retryLoop() {
        while (true) {
            delay(10_000)
            if (saves.failed) saves.retry()
        }
    }

    /** La partida tal como está en la nube (la ha cambiado otro dispositivo): se enseña esa y se olvida lo de aquí. */
    private fun adopt(remote: Game?) {
        if (remote == null) {
            appState.toast("La partida ya no existe: se ha borrado desde otro dispositivo.", ToastKind.Error)
            onGone()
            return
        }
        // Si allí solo se han movido por la hoja de noche, se sigue sin avisar y sin perder el «Deshacer».
        val quiet = game?.let { onlyNavigation(it, remote) } == true
        game = remote
        syncedAt = remote.updatedAt
        ownVersions.clear()
        ownVersions.addLast(remote.updatedAt)
        if (quiet) return
        history.clear()
        canUndo = false
        appState.toast("El Grimorio ha cambiado desde otro dispositivo.")
    }

    private fun persist(next0: Game) {
        // Cada guardado lleva su hora, aunque la regla no la cambie (moverse por la hoja de noche):
        // así el otro dispositivo distingue cada versión.
        val next = if (next0.updatedAt == game?.updatedAt) next0.copy(updatedAt = nowIso()) else next0
        game = next
        ownVersions.addLast(next.updatedAt)
        while (ownVersions.size > 50) ownVersions.removeFirst()
        saves.save(next)
    }

    // ---------- Cambios y deshacer ----------

    fun undo() {
        val current = game ?: return
        val previous = history.removeLastOrNull() ?: return
        canUndo = history.isNotEmpty()
        val undone = if (current.log.size > previous.log.size) current.log.last().text else null
        // La cuenta atrás no se deshace: sigue la que hay.
        persist(previous.copy(timer = current.timer, updatedAt = nowIso()))
        appState.toast(if (undone != null) "Deshecho: $undone" else "Deshecho.", ToastKind.Info, key = "change")
    }

    /** Aviso de lo que acaba de pasar, con «Deshacer» dentro: sustituye al anterior para que no se apilen. */
    fun say(message: String) {
        appState.toast(message, ToastKind.Info, ToastAction("Deshacer") { undo() }, key = "change")
    }

    fun toast(message: String, kind: ToastKind = ToastKind.Info) = appState.toast(message, kind)

    /** Aplica una regla del dominio y guarda (en cola, para no pisar guardados anteriores). */
    fun change(mutate: (Game) -> Game) {
        val current = game ?: return
        var next = try {
            mutate(current)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            appState.toast(errorMessage(e), ToastKind.Error)
            return
        }
        if (next === current) return
        // Si la hoja de noche cambia (pase del Diablillo, Cultivador de opio…), se sigue en el mismo paso.
        next = keepNightStep(current, next, characters)
        // Moverse por la hoja de noche no se deshace: solo lo que cambia el Grimorio.
        if (!onlyNavigation(current, next)) {
            history.addLast(current)
            while (history.size > UNDO_LIMIT) history.removeFirst()
            canUndo = true
            // Lo que el registro apunta de este cambio se avisa con «Deshacer» a mano.
            if (next.log.size > current.log.size) say(next.log.last().text)
        }
        persist(next)
    }

    /** Referencias fijas para el círculo: así las fichas que no cambian no se vuelven a dibujar. */
    val onChange: ((Game) -> Game) -> Unit = { change(it) }
    val onTapPlayer: (String) -> Unit = { tapPlayer(it) }
    val onRemoveReminder: (String, String) -> Unit = { pid, rid -> change { g -> removeReminder(g, pid, rid) } }

    // ---------- Modo «toca una ficha» ----------

    /** Empieza un modo «toca una ficha» y sube al Grimorio. */
    fun startPick(next: Pick, scroll: Boolean = true) {
        pick = next
        if (scroll) scrollTo(ScrollTarget.Top)
    }

    fun tapPlayer(pid: String) {
        val g = game ?: return
        // Sin modo «toca»: la mini-hoja de acciones rápidas (matar, recordatorios) o la ficha entera desde ella.
        when (val p = pick) {
            null -> quick = pid
            is Pick.Seat -> {
                val from = p.fromId
                if (from == null) {
                    pick = Pick.Seat(pid)
                    return
                }
                if (from != pid) {
                    change { swapSeats(it, from, pid) }
                    say("${name(from)} y ${name(pid)} cambian de sitio.")
                }
                pick = Pick.Seat()
            }
            is Pick.Reminder -> {
                if (p.sourceId == "pithag") {
                    // La Pit-Hag: después de tocar al jugador se elige su nuevo personaje.
                    pitHagTarget = pid
                    pick = null
                    return
                }
                // Pone el recordatorio y aplica lo que provoca (ataque del Demonio, veneno del Pukka…).
                val reminder = ReminderToken(id = app.newId(), sourceCharacterId = p.sourceId, label = p.label)
                var message = ""
                change { before ->
                    val result = applyReminderChoice(before, pid, reminder, characters)
                    message = result.message
                    if (p.advance) advanceAfterChoice(before, result.game, pid, p.sourceId, p.label, characters) else result.game
                }
                if (message.isNotEmpty()) say(message)
                pick = null
            }
            Pick.Exile -> {
                val problem = canExile(g, pid, characters)
                if (problem != null) {
                    appState.toast(problem, ToastKind.Error)
                    return
                }
                change { callExile(it, pid, app.newId(), characters) }
                pick = null
                tab = GameTab.Phase
            }
            Pick.Execute -> {
                // El registro dice si sobrevive (Abogado del diablo, Marinero, Bufón…) y el aviso lo repite.
                change { executeNow(it, pid, null, characters) }
                pick = null
                tab = GameTab.Phase
            }
            Pick.Shooter -> pick = Pick.Shot(pid)
            is Pick.Shot -> {
                var message = ""
                change { before ->
                    val result = slayerShot(before, p.shooterId, pid, characters)
                    message = result.message
                    result.game
                }
                if (message.isNotEmpty()) say(message)
                pick = null
                tab = GameTab.Phase
            }
            Pick.Nominator -> {
                val problem = canNominate(g, pid, "")
                if (problem != null) {
                    appState.toast(problem, ToastKind.Error)
                    return
                }
                pick = Pick.Nominee(pid)
            }
            is Pick.Nominee -> {
                val nominatorId = p.nominatorId
                val problem = canNominate(g, nominatorId, pid)
                if (problem != null) {
                    appState.toast(problem, ToastKind.Error)
                    return
                }
                // La Virgen: si la nomina un Aldeano por primera vez, ese Aldeano es ejecutado en el acto.
                var executes = false
                var spy = false
                var died = emptyList<String>()
                change { before ->
                    val nominated = nominate(before, nominatorId, pid, app.newId(), characters)
                    // La Bruja o el Gólem pueden matar al nominar.
                    died = before.players.filter { pl -> pl.alive && nominated.players.find { it.id == pl.id }?.alive != true }.map { it.name }
                    val result = virginNomination(nominated, nominatorId, pid, characters)
                    executes = result.executes
                    spy = result.spy
                    if (result.executes) {
                        executeNow(result.game, nominatorId, "${name(nominatorId)} nomina a la Virgen siendo Aldeano: es ejecutado en el acto.", characters)
                    } else {
                        result.game
                    }
                }
                when {
                    executes -> say("${name(pid)} es la Virgen y ${name(nominatorId)} es Aldeano: ${name(nominatorId)} es ejecutado en el acto.")
                    spy -> say("${name(pid)} es la Virgen y la nomina el Espía: si decides que se registra como Aldeano, usa «Ejecutar sin votación».")
                    died.isNotEmpty() -> say("${died.joinToString(" y ")} muere al nominar (${if (witchCursed(g, nominatorId)) "maldición de la Bruja" else "Gólem"}).")
                }
                pick = null
                tab = GameTab.Phase
            }
        }
    }

    // ---------- Pit-Hag ----------

    fun pitHagPick(targetId: String, characterId: String) {
        var message = ""
        change { before ->
            val result = pitHagChoose(before, targetId, characterId, characters)
            message = result.message
            result.game
        }
        pitHagTarget = null
        if (message.isNotEmpty()) say(message)
    }

    // ---------- Anochecer ----------

    /** Al anochecer se proponen para quitar los recordatorios que solo valían una noche y un día. */
    fun goToNight() {
        val g = game ?: return
        val expiring = expiringReminders(g)
        if (expiring.isNotEmpty()) {
            cleaning = expiring.map { it.reminder.id }
            return
        }
        change(::startNight)
    }

    /** Victorias que se deciden al acabar el día (Vortox o Alcalde sin ejecución, Leviatán tras el día 5). */
    fun checkDuskVictory() {
        val g = game ?: return
        val win = duskVictory(g, characters)
        if (win != null) duskWin = win else goToNight()
    }

    fun endDay() {
        val g = game ?: return
        if (onTheBlock(g) != null && g.executedToday == null) confirmDusk = true else checkDuskVictory()
    }

    // ---------- Consejero de IA ----------

    fun askAi(question: String) {
        val g = game ?: return
        if (question.isBlank() || aiLoading) return
        aiLoading = true
        // Al consejero se le recuerda en orden, de lo más antiguo a lo más reciente, y sin los errores.
        val history = aiThread.filter { !it.error }.map { es.dan15d.grimorio.application.ports.AiAdviceTurn(it.q, it.a) }.reversed()
        scope.launch {
            try {
                val answer = app.askAi.execute(g, question, history)
                aiThread = listOf(AiExchange(question, answer)) + aiThread
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                aiThread = listOf(AiExchange(question, errorMessage(e), error = true)) + aiThread
            } finally {
                aiLoading = false
            }
        }
    }

    val finished: Boolean get() = game?.status == GameStatus.Finished

    fun declare(winner: Alignment) = change { es.dan15d.grimorio.domain.services.declareWinner(it, winner) }
}

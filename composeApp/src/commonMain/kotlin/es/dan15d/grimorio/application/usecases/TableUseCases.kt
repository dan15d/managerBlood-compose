package es.dan15d.grimorio.application.usecases

import es.dan15d.grimorio.application.ports.AdminAuth
import es.dan15d.grimorio.application.ports.IdGenerator
import es.dan15d.grimorio.application.ports.LobbyGateway
import es.dan15d.grimorio.application.ports.LobbyScript
import es.dan15d.grimorio.application.ports.OpenLobbyInput
import es.dan15d.grimorio.application.ports.PlayerGateway
import es.dan15d.grimorio.application.ports.SeatRef
import es.dan15d.grimorio.domain.entities.Lobby
import es.dan15d.grimorio.domain.entities.PlayerView
import es.dan15d.grimorio.domain.entities.Script
import es.dan15d.grimorio.domain.entities.Seat
import kotlinx.coroutines.flow.Flow

// Casos de uso de la mesa compartida: el acceso de Narrador, la sala donde se unen los
// jugadores y lo que ve cada jugador en su móvil.

class AdminSession(private val auth: AdminAuth) {
    val required: Boolean get() = auth.required

    suspend fun check(): Boolean = auth.isAdmin()

    /** Si la contraseña no vale, lanza el error con el mensaje para el Narrador; si vale, devuelve true. */
    suspend fun login(password: String): Boolean {
        if (password.isBlank()) throw Exception("Escribe la contraseña.")
        val ok = auth.login(password)
        if (!ok) throw Exception("Contraseña incorrecta.")
        return true
    }

    suspend fun logout() = auth.logout()
}

/** La sala del Narrador: se abre al elegir guion y se cierra al repartir (o al cancelar). */
class ManageLobby(
    private val gateway: LobbyGateway,
    private val ids: IdGenerator,
) {
    suspend fun open(script: Script): Lobby = gateway.open(
        OpenLobbyInput(
            gameId = ids.uuid(),
            joinCode = ids.joinCode(),
            scriptId = script.id,
            scriptName = script.name,
            characterIds = script.characterIds,
        ),
    )

    suspend fun get(gameId: String): Lobby? = gateway.get(gameId)

    suspend fun listOpen(): List<Lobby> = gateway.listOpen()

    suspend fun changeScript(gameId: String, script: Script) =
        gateway.changeScript(gameId, LobbyScript(id = script.id, name = script.name, characterIds = script.characterIds))

    suspend fun seats(gameId: String): List<Seat> = gateway.seats(gameId)

    fun watch(gameId: String): Flow<List<Seat>> = gateway.watchSeats(gameId)

    suspend fun addSeat(gameId: String, name: String) {
        if (name.isBlank()) throw Exception("Escribe el nombre.")
        gateway.addSeat(gameId, name.trim())
    }

    suspend fun removeSeat(seatId: String) = gateway.removeSeat(seatId)

    /** Mueve un asiento una posición (−1 o +1) para ordenarlos como estén sentados. */
    suspend fun move(gameId: String, seats: List<Seat>, seatId: String, delta: Int) {
        val order = seats.map { it.id }.toMutableList()
        val from = order.indexOf(seatId)
        val to = from + delta
        if (from < 0 || to < 0 || to >= order.size) return
        order[from] = order[to].also { order[to] = order[from] }
        gateway.reorder(gameId, order)
    }

    suspend fun close(gameId: String) = gateway.close(gameId)
}

/** El jugador: unirse con el código y seguir su partida en tiempo real. */
class PlayerTable(private val gateway: PlayerGateway) {
    suspend fun join(code: String, name: String): SeatRef {
        val clean = code.filterNot { it.isWhitespace() }.uppercase()
        if (clean.length < 4) throw Exception("El código tiene 5 letras.")
        if (name.isBlank()) throw Exception("Escribe tu nombre.")
        return gateway.join(clean, name.trim())
    }

    suspend fun current(): SeatRef? = gateway.mySeats().firstOrNull()

    suspend fun view(gameId: String, seatId: String): PlayerView? = gateway.view(gameId, seatId)

    fun watch(gameId: String, seatId: String): Flow<PlayerView?> = gateway.watch(gameId, seatId)

    suspend fun leave(seatId: String) = gateway.leave(seatId)
}

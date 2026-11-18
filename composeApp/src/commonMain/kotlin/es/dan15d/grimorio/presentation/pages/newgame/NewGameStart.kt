package es.dan15d.grimorio.presentation.pages.newgame

import es.dan15d.grimorio.presentation.navigation.Navigator
import es.dan15d.grimorio.presentation.navigation.Route

/**
 * Lo que en la web iba en la URL del asistente: «/nueva?guion=id» (guion elegido desde su
 * ficha) y «/nueva?sala=id» (retomar una sala abierta desde la portada). La ruta NewGame no
 * lleva parámetros, así que se deja aquí y el asistente lo recoge al abrirse.
 */
object NewGameStart {
    private var scriptId: String? = null
    private var lobbyId: String? = null

    internal data class Request(val scriptId: String?, val lobbyId: String?)

    /** Lo recoge el asistente una sola vez: si se vuelve a abrir, empieza de cero. */
    internal fun take(): Request {
        val request = Request(scriptId, lobbyId)
        scriptId = null
        lobbyId = null
        return request
    }

    internal fun set(scriptId: String?, lobbyId: String?) {
        this.scriptId = scriptId
        this.lobbyId = lobbyId
    }
}

/**
 * Abre el asistente de nueva partida. [scriptId]: con ese guion ya elegido (al escoger el modo
 * se va directo a la sala); [lobbyId]: retomando esa sala abierta.
 */
fun Navigator.newGame(scriptId: String? = null, lobbyId: String? = null) {
    NewGameStart.set(scriptId, lobbyId)
    navigate(Route.NewGame)
}

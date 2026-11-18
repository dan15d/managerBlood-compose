package es.dan15d.grimorio.application

import es.dan15d.grimorio.application.ports.GameConflictError
import es.dan15d.grimorio.application.usecases.SaveQueue
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameMode
import es.dan15d.grimorio.domain.entities.Phase
import es.dan15d.grimorio.infrastructure.persistence.LocalStorageGameRepository
import es.dan15d.grimorio.infrastructure.persistence.MemoryKeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// El Grimorio en dos dispositivos: nadie pisa lo que guardó el otro sin enterarse.

private fun game(updatedAt: String) = Game(
    id = "g1",
    name = "Prueba",
    mode = GameMode.Manual,
    scriptId = "",
    scriptName = "",
    players = emptyList(),
    phase = Phase.night(1),
    joinCode = "",
    createdAt = "",
    updatedAt = updatedAt,
)

private fun repo() = LocalStorageGameRepository(MemoryKeyValueStore())

class SyncTest {
    @Test
    fun guardaSiLaVersionGuardadaEsLaQueVioEsteDispositivo() = runTest {
        val repo = repo()
        repo.save(game("t1"))
        repo.save(game("t2"), "t1")
        assertEquals("t2", repo.get("g1")?.updatedAt)
    }

    @Test
    fun siOtroGuardoEntretantoNoLaPisaYLanzaElConflicto() = runTest {
        val repo = repo()
        repo.save(game("t1"))
        repo.save(game("t2"), "t1") // el otro dispositivo
        assertFailsWith<GameConflictError> { repo.save(game("t3"), "t1") }
        assertEquals("t2", repo.get("g1")?.updatedAt)
    }

    @Test
    fun unaPartidaNuevaSeGuardaSinVersionEsperada() = runTest {
        val repo = repo()
        repo.save(game("t1"), null)
        assertEquals("t1", repo.get("g1")?.updatedAt)
    }

    @Test
    fun loEncoladoAntesDeDescartarYaNoSeGuardaYLoDeDespuesSi() = runTest {
        val saved = mutableListOf<Int>()
        val first = CompletableDeferred<Unit>()
        val queue = SaveQueue<Int>(
            scope = this,
            saveFn = { n ->
                if (n == 1) first.await()
                saved += n
            },
            onError = {},
            onRecovered = {},
        )
        queue.save(1) // el 1 ya está guardándose (se queda esperando)
        queue.save(2)
        queue.discard()
        first.complete(Unit)
        queue.save(3).join()
        assertEquals(listOf(1, 3), saved)
        assertFalse(queue.failed)
    }

    @Test
    fun descartarQuitaElFalloPendienteYReintentarNoGuardaLoViejo() = runTest {
        val saved = mutableListOf<Int>()
        val queue = SaveQueue<Int>(
            scope = this,
            saveFn = { n ->
                if (n == 1) throw Exception("sin conexión")
                saved += n
            },
            onError = {},
            onRecovered = {},
        )
        queue.save(1).join()
        assertTrue(queue.failed)
        queue.discard()
        assertFalse(queue.failed)
        queue.retry().join()
        assertEquals(emptyList(), saved)
    }
}

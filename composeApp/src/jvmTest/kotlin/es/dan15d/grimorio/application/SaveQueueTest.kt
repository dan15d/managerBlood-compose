package es.dan15d.grimorio.application

import es.dan15d.grimorio.application.usecases.SaveQueue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// La cola de guardado del Narrador: si se pierde la conexión, no se pierde nada.

private class Setup(scope: TestScope) {
    val saved = mutableListOf<Int>()
    val events = mutableListOf<String>()
    var online = true
    val queue = SaveQueue<Int>(
        scope = scope,
        saveFn = { n ->
            if (!online) throw Exception("Sin conexión")
            saved += n
        },
        onError = { events += "error" },
        onRecovered = { events += "recuperado" },
    )
}

class SaveQueueTest {
    @Test
    fun guardaEnOrden() = runTest {
        val s = Setup(this)
        s.queue.save(1)
        s.queue.save(2).join()
        assertEquals(listOf(1, 2), s.saved)
        assertEquals(emptyList(), s.events)
        assertFalse(s.queue.failed)
    }

    @Test
    fun siFallaAvisaUnaVezYAlReintentarGuardaLoUltimo() = runTest {
        val s = Setup(this)
        s.online = false
        s.queue.save(1)
        s.queue.save(2).join()
        assertTrue(s.queue.failed)
        assertEquals(listOf("error"), s.events)
        // Sigue sin conexión: reintentar no vuelve a avisar.
        s.queue.retry().join()
        assertEquals(listOf("error"), s.events)
        s.online = true
        s.queue.retry().join()
        assertEquals(listOf(2), s.saved)
        assertEquals(listOf("error", "recuperado"), s.events)
        assertFalse(s.queue.failed)
        // Ya no falta nada: reintentar no guarda otra vez.
        s.queue.retry().join()
        assertEquals(listOf(2), s.saved)
    }

    @Test
    fun unCambioNuevoQueSiEntraTambienArreglaElFalloAnterior() = runTest {
        val s = Setup(this)
        s.online = false
        s.queue.save(1).join()
        s.online = true
        s.queue.save(2).join()
        assertEquals(listOf(2), s.saved)
        assertEquals(listOf("error", "recuperado"), s.events)
        assertFalse(s.queue.failed)
    }
}

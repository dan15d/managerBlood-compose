package es.dan15d.grimorio.application.usecases

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Cola de guardado: guarda en orden y, si falla (sin cobertura…), se puede reintentar con la
 * versión más reciente. Cada guardado lleva el estado entero, así que basta con guardar el último.
 *
 * Los guardados se lanzan en [scope] y se hacen de uno en uno (un Mutex, que atiende por orden de
 * llegada). `save` y `retry` devuelven el trabajo lanzado por si se quiere esperar (`join()`).
 */
class SaveQueue<T>(
    private val scope: CoroutineScope,
    private val saveFn: suspend (T) -> Unit,
    /** El primer fallo (los siguientes, mientras siga fallando, no se avisan). */
    private val onError: (Throwable) -> Unit,
    /** Se ha vuelto a guardar después de fallar. */
    private val onRecovered: () -> Unit,
) {
    /** Cada guardado pedido, para distinguir dos peticiones aunque lleven el mismo valor. */
    private class Request<T>(val item: T)

    private val mutex = Mutex()

    @Volatile private var pending: Request<T>? = null

    @Volatile private var failing = false

    /** Sube con cada `discard`: los guardados encolados antes ya no se hacen. */
    @Volatile private var generation = 0

    @Volatile private var last: Job = Job().apply { complete() }

    /** Hay algo sin guardar porque el último intento falló. */
    val failed: Boolean get() = failing

    fun save(item: T): Job {
        val request = Request(item)
        pending = request
        val generation = this.generation
        // Sin despachar: coge sitio en la cola ya, en el orden en que se llama a save.
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                try {
                    if (generation == this@SaveQueue.generation) saveFn(item)
                    if (generation != this@SaveQueue.generation) return@withLock
                    // Si entretanto llegó algo más nuevo, ese ya se guardará.
                    if (pending !== request) return@withLock
                    pending = null
                    if (failing) {
                        failing = false
                        onRecovered()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (!failing) onError(e)
                    failing = true
                }
            }
        }
        last = job
        return job
    }

    /** Olvida lo que faltaba por guardar: la partida ha cambiado en otro sitio y manda esa versión. */
    fun discard() {
        generation++
        pending = null
        failing = false
    }

    /** Vuelve a intentar lo último que falló. Si no falta nada, no hace nada. */
    fun retry(): Job {
        val request = pending
        return if (failing && request != null) save(request.item) else last
    }
}

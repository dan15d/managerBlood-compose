package es.dan15d.grimorio.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import es.dan15d.grimorio.presentation.platform.ring
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant

// Reloj compartido por el cronómetro de fase (Grimorio) y la cuenta atrás (Grimorio y móvil del jugador).

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun parseMillis(iso: String): Long = Instant.parse(iso).toEpochMilliseconds()

/** La hora actual en milisegundos, que avanza cada segundo mientras [active]. */
@Composable
fun rememberNow(active: Boolean = true): Long {
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            now = nowMillis()
            delay(1000 - now % 1000)
        }
    }
    return now
}

/** m:ss, o h:mm:ss a partir de una duración en milisegundos. */
fun clock(ms: Long): String {
    val total = maxOf(0L, ms / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val mm = if (h > 0) m.toString().padStart(2, '0') else m.toString()
    return (if (h > 0) "$h:" else "") + mm + ":" + s.toString().padStart(2, '0')
}

/**
 * Cuando la cuenta atrás termina, avisa una sola vez, y solo si acaba de pasar: al abrir una
 * partida con una cuenta atrás vieja no suena nada.
 */
@Composable
fun Alarm(endsAt: String?, now: Long, sound: Boolean = true) {
    val rang = remember { arrayOfNulls<String>(1) }
    LaunchedEffect(endsAt, now >= (endsAt?.let(::parseMillis) ?: Long.MAX_VALUE)) {
        if (endsAt == null) {
            rang[0] = null
            return@LaunchedEffect
        }
        val end = parseMillis(endsAt)
        val current = nowMillis()
        if (current < end || rang[0] == endsAt) return@LaunchedEffect
        rang[0] = endsAt
        if (current - end < 5000) ring(sound)
    }
}

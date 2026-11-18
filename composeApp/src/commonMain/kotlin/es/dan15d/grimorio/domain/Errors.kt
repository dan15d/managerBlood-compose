package es.dan15d.grimorio.domain

import kotlin.time.Clock
import kotlin.time.Instant

/** Error de reglas del juego: el mensaje se enseña tal cual al Narrador. */
class RuleViolation(message: String) : Exception(message)

/**
 * Una fecha en ISO-8601 (UTC) igual que `Date.toISOString()` en la web: siempre con milisegundos
 * («2026-11-05T12:03:00.000Z»). Las dos apps comparten las partidas, así que el texto es el mismo.
 */
fun isoString(instant: Instant): String {
    val text = Instant.fromEpochMilliseconds(instant.toEpochMilliseconds()).toString()
    return if ('.' in text) text else text.removeSuffix("Z") + ".000Z"
}

/** Hora actual en ISO-8601 (UTC), el formato con que se guardan las fechas de la partida. */
fun nowIso(): String = isoString(Clock.System.now())

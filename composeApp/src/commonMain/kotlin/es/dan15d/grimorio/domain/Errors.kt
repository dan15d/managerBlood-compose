package es.dan15d.grimorio.domain

/** Error de reglas del juego: el mensaje se enseña tal cual al Narrador. */
class RuleViolation(message: String) : Exception(message)

/** Hora actual en ISO-8601 (UTC), el formato con que se guardan las fechas de la partida. */
fun nowIso(): String = kotlin.time.Clock.System.now().toString()

package es.dan15d.grimorio.domain.services

import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.ReminderToken

// Recordatorios del Grimorio que leen las reglas. Como en el Grimorio físico, el estado de muchos
// personajes (envenenado, sin habilidad, a salvo…) está en los recordatorios: aquí está el único
// sitio que decide qué texto significa qué.

/**
 * Textos del catálogo (characters.json) que usan las reglas. Si se cambia uno allí, hay que
 * cambiarlo aquí: lo comprueba el test de recordatorios.
 */
object REMINDER {
    const val poisoned = "Envenenado"
    const val drunk = "Borracho"
    /** El Cortesano: primer día de borrachera (luego «Borracho 2» y «Borracho 3»). */
    const val drunk1 = "Borracho 1"
    const val noAbility = "Sin habilidad"
    const val hasAbility = "Tiene habilidad"
    const val dead = "Muerto"
    const val alive = "Vivo"
    const val safe = "A salvo"
    const val mad = "Loco"
    /** Quien cuida a Lil' Monsta (y «es el Demonio»). */
    const val isDemon = "Es el Demonio"
    /** El Leviatán ha visto ejecutar a este jugador bueno. */
    const val goodExecuted = "Bueno ejecutado"

    /** Todos, por nombre (como Object.entries en la web). */
    val all: Map<String, String> = mapOf(
        "poisoned" to poisoned,
        "drunk" to drunk,
        "drunk1" to drunk1,
        "noAbility" to noAbility,
        "hasAbility" to hasAbility,
        "dead" to dead,
        "alive" to alive,
        "safe" to safe,
        "mad" to mad,
        "isDemon" to isDemon,
        "goodExecuted" to goodExecuted,
    )
}

/** Recordatorios que pone la app y no están en el catálogo. */
object APP_REMINDER {
    /** El Zombuul ya ha «muerto» una vez: parece muerto, pero sigue vivo. */
    const val zombuulSeemsDead = "Parece muerto"
    /** El Zombuul ha muerto de verdad (la segunda vez). */
    const val zombuulReallyDead = "Muerto de verdad"
    /** Se ha convertido en el Demonio de día (Mujer escarlata): esta noche hay que decírselo. */
    const val newDemon = "Nuevo Demonio"
    /** La borrachera del Juglar: el día de la ejecución aún no caduca; al amanecer pasa a «Borracho». */
    const val minstrelTomorrow = "Borracho hasta mañana"

    val all: Map<String, String> = mapOf(
        "zombuulSeemsDead" to zombuulSeemsDead,
        "zombuulReallyDead" to zombuulReallyDead,
        "newDemon" to newDemon,
        "minstrelTomorrow" to minstrelTomorrow,
    )
}

/**
 * Le quita la habilidad: envenenado, borracho (también «Borracho 1», «Es el Borracho» o los que
 * el Narrador pone a mano) o sin habilidad. La locura no: un jugador loco conserva su habilidad.
 */
private val DISABLING = Regex("envenen|borrach|sin habilidad", RegexOption.IGNORE_CASE)

fun disablesAbility(label: String): Boolean = DISABLING.containsMatchIn(label)

fun disablesAbility(reminder: ReminderToken): Boolean = disablesAbility(reminder.label)

/** Sin recordatorios que le quiten la habilidad. */
fun healthy(p: Player): Boolean = p.reminders.none { disablesAbility(it) }

/** Tiene el recordatorio `label` puesto por `source`. */
fun has(p: Player, source: String, label: String): Boolean =
    p.reminders.any { it.sourceCharacterId == source && it.label == label }

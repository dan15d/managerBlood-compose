package es.dan15d.grimorio.domain.services

import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.ReminderToken
import es.dan15d.grimorio.domain.entities.Team

// Estado propio de algunos Demonios, guardado como recordatorios en el Grimorio (igual que en
// el físico). Solo son consultas: las reglas que lo cambian están en GameRules.
// (has y healthy están en Reminders.kt, en el mismo paquete.)

/** El Zombuul ya ha «muerto» una vez: parece muerto, pero sigue vivo. */
const val ZOMBUUL_SEEMS_DEAD = APP_REMINDER.zombuulSeemsDead
/** El Zombuul ha muerto de verdad (la segunda vez). */
const val ZOMBUUL_REALLY_DEAD = APP_REMINDER.zombuulReallyDead
/** Quien cuida a Lil' Monsta (y «es el Demonio»). */
const val LILMONSTA_BABYSITTER = REMINDER.isDemon
/** El Leviatán ha visto ejecutar a este jugador bueno. */
const val LEVIATHAN_GOOD_EXECUTED = REMINDER.goodExecuted
/** Se ha convertido en el Demonio de día (Mujer escarlata): esta noche hay que decírselo. */
const val NEW_DEMON = APP_REMINDER.newDemon

/** Zombuul que parece muerto pero sigue vivo (para las reglas cuenta como vivo). */
fun zombuulSeemsDead(p: Player): Boolean =
    p.characterId == "zombuul" && !p.alive && has(p, "zombuul", ZOMBUUL_SEEMS_DEAD) && !has(p, "zombuul", ZOMBUUL_REALLY_DEAD)

/** Vivo de verdad: los vivos y el Zombuul que solo parece muerto. */
fun reallyAlive(p: Player): Boolean = p.alive || zombuulSeemsDead(p)

/** El huésped del Lleech: el jugador que envenenó la primera noche. */
fun lleechHost(game: Game): Player? = game.players.find { has(it, "lleech", REMINDER.poisoned) }

/** Lil' Monsta está en juego: no lo tiene ningún jugador, lo cuida un Esbirro. */
fun hasLilMonsta(game: Game): Boolean = "lilmonsta" in game.extraCharacterIds

fun lilMonstaBabysitter(game: Game): Player? = game.players.find { has(it, "lilmonsta", LILMONSTA_BABYSITTER) }

/** Jugadores que son el Demonio: los de tipo Demonio y quien cuida a Lil' Monsta. */
fun demonPlayers(game: Game, characters: Map<String, Character>): List<Player> {
    val demons = game.players.filter { characters[it.characterId]?.team == Team.Demon }
    val babysitter = if (hasLilMonsta(game)) lilMonstaBabysitter(game) else null
    return if (babysitter != null && demons.none { it === babysitter }) demons + babysitter else demons
}

fun reminderFrom(source: String, label: String, id: String): ReminderToken =
    ReminderToken(id = id, sourceCharacterId = source, label = label)

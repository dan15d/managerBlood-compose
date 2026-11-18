package es.dan15d.grimorio.domain.services

import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.Player
import es.dan15d.grimorio.domain.entities.Team
import kotlin.math.abs

// La respuesta verdadera de los personajes de información, calculada con el Grimorio.
// Es una ayuda: el Narrador decide si la da (o da otra si está borracho, envenenado…).

data class InfoHint(
    val playerId: String,
    /** La respuesta verdadera, lista para dar. */
    val text: String,
    /** Lo que puede cambiarla (personajes que se registran distinto, Vortox…). */
    val caveats: List<String>,
)

/**
 * Recordatorio que se pone sobre el jugador que elige cada noche quien tiene esta habilidad
 * (Envenenador → «Envenenado», Monje → «A salvo», Demonios → «Muerto»…).
 */
val CHOICE_REMINDER: Map<String, String> = mapOf(
    "poisoner" to REMINDER.poisoned,
    "monk" to REMINDER.safe,
    "butler" to "Señor",
    "innkeeper" to REMINDER.safe,
    "sailor" to REMINDER.drunk,
    "exorcist" to "Elegido",
    "moonchild" to REMINDER.dead,
    "devilsadvocate" to "Sobrevive ejecución",
    "assassin" to REMINDER.dead,
    "godfather" to REMINDER.dead,
    "witch" to "Maldito",
    "cerenovus" to REMINDER.mad,
    "harpy" to REMINDER.mad,
    "widow" to REMINDER.poisoned,
    "fearmonger" to "Terror",
    "acrobat" to "Elegido",
    "preacher" to REMINDER.noAbility,
    "professor" to REMINDER.alive,
    "courtier" to REMINDER.drunk1,
    "ogre" to "Amigo",
    "lunatic" to "Elegido",
    "imp" to REMINDER.dead,
    "po" to REMINDER.dead,
    "pukka" to REMINDER.poisoned,
    "shabaloth" to REMINDER.dead,
    "zombuul" to REMINDER.dead,
    "fanggu" to REMINDER.dead,
    "nodashii" to REMINDER.dead,
    "vigormortis" to REMINDER.dead,
    "vortox" to REMINDER.dead,
    "lleech" to REMINDER.dead,
    "ojo" to REMINDER.dead,
    "kazali" to REMINDER.dead,
    "lordoftyphon" to REMINDER.dead,
    "legion" to REMINDER.dead,
    "lilmonsta" to REMINDER.isDemon,
    "yaggababble" to REMINDER.dead,
    "alhadikhia" to "1",
    "bureaucrat" to "3 votos",
    "thief" to "Voto negativo",
    "snakecharmer" to REMINDER.poisoned,
    // La Pit-Hag elige jugador y después el personaje en el que se convierte.
    "pithag" to "Elegido",
    // Eligen para recibir información: no ponen recordatorio, la app da la respuesta.
    "dreamer" to "Elegido",
    "ravenkeeper" to "Elegido",
    "villageidiot" to "Elegido",
    "gambler" to "Elegido",
)

/** Quien elige para recibir información sobre el elegido (la app da la respuesta). */
private val INFO_ON_CHOICE = setOf("dreamer", "ravenkeeper", "villageidiot", "gambler")

/**
 * Quien al elegir no pone recordatorio: el Encantador de serpientes (intercambia personajes) y
 * los que reciben información sobre el elegido. El botón no nombra ningún recordatorio.
 */
val CHOICE_WITHOUT_REMINDER: Set<String> = setOf("snakecharmer", "pithag") + INFO_ON_CHOICE

fun isInfoChoice(characterId: String): Boolean = characterId in INFO_ON_CHOICE

/** Eligen a varios jugadores en la misma noche: la hoja espera a que el Narrador termine. */
private val MULTI_CHOICE = setOf("po", "alhadikhia", "yaggababble")

/**
 * Después de tocar al jugador elegido, la hoja de noche pasa sola al paso siguiente si elegir era
 * la acción principal del paso y no queda nada por hacer en él. Se queda donde está cuando se
 * elige para recibir información (hay que darla), cuando faltan más elecciones (Po, Al-Hadikhia…),
 * cuando después se elige personaje (Pit-Hag) o cuando alguien cambia de personaje (el Demonio que
 * se mata, el Fang Gu que salta, el Encantador de serpientes): hay que enseñar «TÚ ERES».
 */
fun advanceAfterChoice(
    before: Game,
    after: Game,
    targetId: String,
    sourceCharacterId: String,
    label: String,
    characters: Map<String, Character>,
): Game {
    if (!before.phase.isNight || !after.phase.isNight) return after
    if (label != choiceReminder(sourceCharacterId, before.phase.number == 1)) return after
    if (sourceCharacterId in CHOICE_WITHOUT_REMINDER || sourceCharacterId in MULTI_CHOICE) return after
    // Se elige a sí mismo (el Demonio que se mata: toca elegir heredero) o alguien cambia de personaje.
    if (before.players.find { it.id == targetId }?.characterId == sourceCharacterId) return after
    val changed = after.players.any { p -> before.players.find { it.id == p.id }?.characterId != p.characterId }
    if (changed) return after
    val oldSteps = buildNightOrder(before, characters)
    val current = oldSteps.getOrNull(minOf(before.nightStep, oldSteps.size - 1))
    if (current?.character?.id != sourceCharacterId) return after
    val newSteps = buildNightOrder(after, characters)
    val index = newSteps.indexOfFirst { it.key == current.key }
    if (index < 0 || index >= newSteps.size - 1) return after
    return after.copy(nightStep = index + 1)
}

/**
 * La respuesta para quien elige a un jugador para saber de él (Soñador, Guardián de cuervos,
 * Tonto del pueblo), con los avisos de siempre: Espía o Recluso, Vortox y quien elige sin habilidad.
 */
fun infoChoiceAnswer(game: Game, chooserCharacterId: String, target: Player, characters: Map<String, Character>): String {
    val character = characters[target.characterId]
    val name = character?.name ?: target.characterId
    val good = target.alignment == Alignment.Good
    val answer = when (chooserCharacterId) {
        "dreamer" -> "${target.name} es $name (${if (good) "bueno" else "malo"}): enséñale $name y un personaje ${if (good) "malo" else "bueno"} que no esté en su sitio."
        "ravenkeeper" -> "Enséñale $name, el personaje de ${target.name}."
        "villageidiot" -> "${target.name} es ${if (good) "bueno: pulgar hacia arriba" else "malo: pulgar hacia abajo"}."
        "gambler" -> "${target.name} es $name. Si el Tahúr no ha acertado, muere: márcale muerto."
        else -> ""
    }
    val chooser = characters[chooserCharacterId]
    val notes = if (chooser != null) commonCaveats(game, characters, chooser).toMutableList() else mutableListOf()
    val working = game.players.any { it.characterId == chooserCharacterId && healthy(it) }
    if (!working) notes.add(0, "Quien elige no tiene habilidad (Borracho, envenenado…): puedes darle información falsa.")
    return (listOf(answer) + notes).joinToString(" ")
}

/** Lo que pone quien elige esta noche: el Lleech, la primera noche, envenena a su huésped. */
fun choiceReminder(characterId: String, firstNight: Boolean): String? = when {
    characterId == "lleech" && firstNight -> REMINDER.poisoned
    characterId in listOf("godfather", "pithag") && firstNight -> null
    else -> CHOICE_REMINDER[characterId]
}

private fun label(p: Player, characters: Map<String, Character>) =
    "${p.seat + 1}. ${p.name} (${characters[p.characterId]?.name ?: "?"})"

/** Los vecinos vivos más cercanos a cada lado, saltando a los muertos. */
fun aliveNeighbours(game: Game, player: Player): List<Player> {
    val seated = game.players.sortedBy { it.seat }
    val n = seated.size
    val at = seated.indexOfFirst { it.id == player.id }
    fun walk(dir: Int): Player? {
        for (k in 1 until n) {
            val p = seated[((at + dir * k) % n + n) % n]
            if (p.alive && p.id != player.id) return p
        }
        return null
    }
    return listOfNotNull(walk(-1), walk(1)).distinct()
}

/** Hacia qué lado está el jugador malo más cercano, como aprende el Shugenja. */
enum class EvilSide(val key: String) {
    /** En el sentido de las agujas del reloj. */
    Clockwise("clockwise"),
    Counter("counter"),
    /** A la misma distancia por los dos lados. */
    Both("both"),
}

/**
 * Hacia qué lado está el jugador malo más cercano (vivo o muerto), como aprende el Shugenja:
 * Clockwise (sentido de las agujas del reloj), Counter, Both si están a la misma distancia.
 */
fun nearestEvilSide(game: Game, from: Player): EvilSide? {
    val seated = game.players.sortedBy { it.seat }
    val n = seated.size
    val at = seated.indexOfFirst { it.id == from.id }
    for (k in 1 until n) {
        val cw = seated[(at + k) % n].alignment == Alignment.Evil
        val ccw = seated[((at - k) % n + n) % n].alignment == Alignment.Evil
        if (cw && ccw) return EvilSide.Both
        if (cw) return EvilSide.Clockwise
        if (ccw) return EvilSide.Counter
    }
    return null
}

/** Parejas de jugadores malos sentados juntos (vivos o muertos), como cuenta el Chef. */
fun evilPairs(game: Game): Int {
    val seated = game.players.sortedBy { it.seat }
    if (seated.size < 2) return 0
    return seated.withIndex().count { (i, p) ->
        p.alignment == Alignment.Evil && seated[(i + 1) % seated.size].alignment == Alignment.Evil
    }
}

/** Pasos desde el Demonio hasta el Esbirro más cercano, como cuenta el Relojero. */
fun demonToMinion(game: Game, characters: Map<String, Character>): Int? {
    val n = game.players.size
    fun team(p: Player) = characters[p.characterId]?.team
    val demons = game.players.filter { team(it) == Team.Demon }
    val minions = game.players.filter { team(it) == Team.Minion }
    val distances = demons.flatMap { d -> minions.map { m -> minOf(abs(d.seat - m.seat), n - abs(d.seat - m.seat)) } }
    return distances.minOrNull()
}

private fun commonCaveats(game: Game, characters: Map<String, Character>, character: Character): List<String> {
    val inPlay = game.players.map { it.characterId }.toSet()
    val caveats = mutableListOf<String>()
    val misregister = listOf("spy", "recluse").filter { it in inPlay }.map { characters.getValue(it).name }
    if (misregister.isNotEmpty()) {
        caveats.add(
            "${misregister.joinToString(" y ")} ${if (misregister.size > 1) "pueden aparecer distintos" else "puede aparecer distinto"}: decides tú.",
        )
    }
    if ("vortox" in inPlay && character.team == Team.Townsfolk) caveats.add("Con Vortox en juego, la información de los Aldeanos debe ser falsa.")
    if (game.players.any { p -> p.reminders.any { it.sourceCharacterId == "minstrel" } }) {
        caveats.add("Por el Juglar todos (salvo él) están borrachos: la información puede ser falsa.")
    }
    return caveats
}

/**
 * La respuesta verdadera para cada jugador de un paso de noche (también para quien solo cree ser
 * ese personaje, que la recibirá falsa si al Narrador le conviene). Vacío si el personaje no es
 * de información o la respuesta depende de lo que elija el jugador.
 */
fun infoHints(game: Game, character: Character, characters: Map<String, Character>): List<InfoHint> {
    val players = game.players.filter { it.characterId == character.id || it.shownCharacterId == character.id }
    fun team(p: Player) = characters[p.characterId]?.team
    val caveats = commonCaveats(game, characters, character)
    fun others(self: Player, t: Team) = game.players.filter { it.id != self.id && team(it) == t }
    fun list(ps: List<Player>) = ps.joinToString(", ") { label(it, characters) }
    val demons = demonPlayers(game, characters)
    val firstNight = game.phase.number == 1 && game.phase.isNight

    fun answer(self: Player): String? = when (character.id) {
        "empath" -> {
            val evil = aliveNeighbours(game, self).count { it.alignment == Alignment.Evil }
            "Enseña $evil con los dedos."
        }
        "chef" -> "Enseña ${evilPairs(game)} con los dedos."
        "clockmaker" -> demonToMinion(game, characters)?.let { "Enseña $it con los dedos." }
        "washerwoman" -> others(self, Team.Townsfolk).let { ts -> if (ts.isNotEmpty()) "Aldeanos que puedes enseñar: ${list(ts)}." else null }
        "librarian" -> others(self, Team.Outsider).let { os ->
            if (os.isNotEmpty()) "Forasteros que puedes enseñar: ${list(os)}." else "No hay Forasteros: enséñale el 0."
        }
        "investigator" -> others(self, Team.Minion).let { ms -> if (ms.isNotEmpty()) "Esbirros que puedes enseñar: ${list(ms)}." else null }
        "undertaker" -> {
            val executed = game.players.find { it.id == game.executedToday }
            when {
                executed == null -> "Hoy no ha habido ejecución: no le despiertes."
                // Solo aprende al que ha muerto por la ejecución (no al Marinero o al Bufón que sobreviven).
                executed.id !in game.dayDeaths -> "${executed.name} fue ejecutado pero no murió: no le despiertes."
                else -> "Enséñale ${characters[executed.characterId]?.name} (ejecutado: ${executed.name})."
            }
        }
        "fortuneteller" -> {
            // Quien cuida a Lil' Monsta también es el Demonio para la Pitonisa.
            val herring = game.players.find { p -> p.reminders.any { it.sourceCharacterId == "fortuneteller" } }
            val yes = (demonPlayers(game, characters) + listOfNotNull(herring)).map { "${it.seat + 1}. ${it.name}" }
            "Responde SÍ si elige a ${yes.joinToString(" o ")}${if (herring != null) " (pista falsa incluida)" else ""}." +
                if (herring != null) "" else " Falta poner la pista falsa en un jugador bueno."
        }
        "towncrier" -> {
            // Las nominaciones del día siguen en la partida hasta el amanecer.
            val minions = game.nominations
                .filter { it.exile != true }
                .mapNotNull { n -> game.players.find { it.id == n.nominatorId } }
                .filter { team(it) == Team.Minion }
            if (minions.isNotEmpty()) "Asiente: sí ha nominado un Esbirro (${minions.map { it.name }.distinct().joinToString(", ")})."
            else "Niega: hoy no ha nominado ningún Esbirro."
        }
        "flowergirl" -> {
            val demonIds = demonPlayers(game, characters).map { it.id }.toSet()
            val voted = game.nominations.any { n -> n.voterIds.any { it in demonIds } }
            if (voted) "Asiente: el Demonio ha votado hoy." else "Niega: hoy el Demonio no ha votado."
        }
        "oracle" -> {
            val evilDead = game.players.count { !it.alive && it.alignment == Alignment.Evil }
            "Enseña $evilDead con los dedos (muertos malos)."
        }
        "shugenja" -> when (nearestEvilSide(game, self)) {
            EvilSide.Both -> "Equidistante: señala hacia el lado que quieras."
            EvilSide.Clockwise -> "Señala en el sentido de las agujas del reloj."
            EvilSide.Counter -> "Señala en sentido contrario a las agujas del reloj."
            null -> null
        }
        "knight" -> {
            val notDemon = game.players.filter { p -> p.id != self.id && demons.none { it.id == p.id } }
            "Señala a 2 de estos (no son el Demonio): ${list(notDemon)}."
        }
        "noble" -> {
            val evil = game.players.filter { it.id != self.id && it.alignment == Alignment.Evil }
            val good = game.players.filter { it.id != self.id && it.alignment == Alignment.Good }
            "Señala a 1 malo (${list(evil)}) y 2 buenos (${list(good)})."
        }
        "steward" -> {
            val good = game.players.filter { it.id != self.id && it.alignment == Alignment.Good }
            "Señala a 1 jugador bueno: ${list(good)}."
        }
        "grandmother" -> {
            val grandchild = game.players.find { p -> p.reminders.any { it.sourceCharacterId == "grandmother" && it.label == "Nieto" } }
            when {
                grandchild == null -> "Falta poner el recordatorio «Nieto» en un jugador bueno."
                firstNight -> "Señala a ${grandchild.name} y enséñale ${characters[grandchild.characterId]?.name}."
                else -> null
            }
        }
        "chambermaid" -> {
            // Quienes tienen paso esta noche y no se saltan (el Borracho no despierta por su habilidad).
            val woke = buildNightOrder(game, characters)
                .filter { it.kind == NightStepKind.Character && !it.dead && it.character?.id != "chambermaid" }
                .flatMap { s -> s.playerIds.filter { it !in s.deceivedPlayerIds } }
                .toSet()
            val names = game.players.filter { it.id in woke && it.id != self.id }.map { it.name }
            if (names.isNotEmpty()) {
                "Despiertan por su habilidad esta noche: ${names.joinToString(", ")}. Enseña cuántos de los 2 que elige están en la lista (descuenta a quien no haya llegado a despertar)."
            } else {
                "Esta noche nadie más despierta por su habilidad: enseña 0."
            }
        }
        "eviltwin" -> {
            val twin = game.players.find { p -> p.reminders.any { it.sourceCharacterId == "eviltwin" && it.label == "Gemelo" } }
            when {
                twin == null -> "Falta poner el recordatorio «Gemelo» en un jugador bueno."
                firstNight -> "El gemelo bueno es ${twin.name} (${characters[twin.characterId]?.name}): despierta a ${self.name} y a ${twin.name}, y enséñale a cada uno el personaje del otro."
                else -> null
            }
        }
        // Solo si el Demonio le ha matado esta noche (el paso ya dice si toca despertarle).
        "sage" -> if (self.id !in game.nightDeaths) null
        else "Señala al Demonio (${demons.joinToString(" o ") { it.name }}) y a otro jugador."
        "king" -> if (game.phase.number == 1) {
            "El Rey es ${self.name}: señálaselo al Demonio."
        } else {
            val dead = game.players.count { !it.alive }
            val alive = game.players.filter { it.alive }
            if (dead >= alive.size) "Hay tantos muertos como vivos o más: enséñale el personaje de 1 vivo (${list(alive.filter { it.id != self.id })})."
            else "Hay más vivos que muertos: no le despiertes."
        }
        "choirboy" -> {
            val king = game.players.find { it.characterId == "king" }
            val killedByDemon = king != null && king.id in game.nightDeaths &&
                king.reminders.any { it.label == REMINDER.dead && characters[it.sourceCharacterId]?.team == Team.Demon }
            if (killedByDemon) "El Demonio ha matado al Rey: señala al Demonio (${demons.joinToString(", ") { it.name }})."
            else "El Demonio no ha matado al Rey esta noche: no le despiertes."
        }
        "bountyhunter" -> {
            val known = game.players.find { p -> p.reminders.any { it.sourceCharacterId == "bountyhunter" && it.label == "Conocido" } }
            val evil = game.players.filter { it.alive && it.alignment == Alignment.Evil && it.id != known?.id }
            when {
                known == null -> "Falta poner «Conocido» en un jugador malo: ${list(evil)}."
                firstNight -> "Señala a ${known.name}."
                known.id in game.nightDeaths || known.id in game.dayDeaths ->
                    "Ha muerto ${known.name}: mueve «Conocido» y señala a otro malo (${list(evil)})."
                else -> "Su jugador conocido sigue vivo: no le despiertes."
            }
        }
        "godfather" -> if (game.phase.number != 1) null
        else "Forasteros en juego: ${list(game.players.filter { team(it) == Team.Outsider }).ifEmpty { "ninguno" }}."
        "marionette" -> "Despierta al Demonio y señálale a la Marioneta: ${self.name}."
        "zombuul" -> {
            // Solo ataca si nadie ha muerto durante el día (también cuenta su propia muerte aparente).
            val died = game.dayDeaths.mapNotNull { id -> game.players.find { it.id == id }?.name?.ifEmpty { null } }
            if (died.isNotEmpty()) "Hoy ha muerto ${died.joinToString(", ")}: no despiertes al Zombuul."
            else "Hoy no ha muerto nadie: despierta al Zombuul para que elija."
        }
        else -> null
    }

    return players.mapNotNull { p ->
        // Quien se ha convertido en el Demonio de día aún no lo sabe.
        val fresh = if (p.alive && p.reminders.any { it.label == NEW_DEMON }) {
            "${p.name} se ha convertido hoy en ${character.name}: despiértale y enséñale TÚ ERES y la ficha de ${character.name}."
        } else {
            null
        }
        val text = listOfNotNull(fresh, answer(p)).filter { it.isNotEmpty() }.joinToString(" ")
        if (text.isNotEmpty()) InfoHint(p.id, text, caveats) else null
    }
}

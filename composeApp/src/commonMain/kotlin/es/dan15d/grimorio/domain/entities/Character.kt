package es.dan15d.grimorio.domain.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Team {
    @SerialName("townsfolk") Townsfolk,
    @SerialName("outsider") Outsider,
    @SerialName("minion") Minion,
    @SerialName("demon") Demon,
    @SerialName("traveller") Traveller,
    @SerialName("fabled") Fabled,
    @SerialName("loric") Loric;

    /** El mismo texto que en el JSON ("townsfolk"…), para claves y registros. */
    val key: String get() = name.lowercase()
}

@Serializable
enum class Alignment {
    @SerialName("good") Good,
    @SerialName("evil") Evil;

    val key: String get() = name.lowercase()
}

@Serializable
enum class Edition {
    @SerialName("tb") Tb,
    @SerialName("bmr") Bmr,
    @SerialName("snv") Snv,
    @SerialName("exp") Exp,
    @SerialName("custom") Custom,
}

val TEAMS_IN_BAG: List<Team> = listOf(Team.Townsfolk, Team.Outsider, Team.Minion, Team.Demon)

data class TeamLabel(val singular: String, val plural: String)

val TEAM_LABEL: Map<Team, TeamLabel> = mapOf(
    Team.Townsfolk to TeamLabel("Aldeano", "Aldeanos"),
    Team.Outsider to TeamLabel("Forastero", "Forasteros"),
    Team.Minion to TeamLabel("Esbirro", "Esbirros"),
    Team.Demon to TeamLabel("Demonio", "Demonios"),
    Team.Traveller to TeamLabel("Viajero", "Viajeros"),
    Team.Fabled to TeamLabel("Fábula", "Fábulas"),
    Team.Loric to TeamLabel("Lórico", "Lóricos"),
)

val Team.label: TeamLabel get() = TEAM_LABEL.getValue(this)

@Serializable
data class Character(
    val id: String,
    val name: String,
    /** Nombre original en inglés (para buscar y para importar guiones de la herramienta oficial). */
    val nameEn: String? = null,
    val team: Team,
    val edition: Edition,
    val ability: String,
    /** Posición en la hoja de la primera noche; 0 si no se despierta. */
    val firstNight: Int = 0,
    /** Posición en la hoja del resto de noches; 0 si no se despierta. */
    val otherNight: Int = 0,
    val firstNightReminder: String = "",
    val otherNightReminder: String = "",
    val reminders: List<String> = emptyList(),
    /** Cambia la preparación (texto entre corchetes de la ficha). */
    val setup: Boolean = false,
    /** Icono con los colores de su bando (ruta relativa, p. ej. "roles/mayor.webp", o URL). */
    val image: String? = null,
    /** Icono con los colores del bando contrario (Aldeano que se vuelve malvado…). */
    val imageAlt: String? = null,
)

fun defaultAlignment(team: Team): Alignment =
    if (team == Team.Minion || team == Team.Demon) Alignment.Evil else Alignment.Good

fun isGoodTeam(team: Team) = team == Team.Townsfolk || team == Team.Outsider

/** Fábulas y Lóricos: personajes del Narrador. No son jugadores ni salen de la bolsa. */
fun isStorytellerTeam(team: Team) = team == Team.Fabled || team == Team.Loric

/** Regla o aclaración que solo aplica cuando dos personajes coinciden en la partida. */
interface CharacterPair {
    /** Siempre dos ids. */
    val characterIds: List<String>
    val text: String
}

/** Incompatibilidad oficial (jinx): regla especial cuando dos personajes están en juego a la vez. */
@Serializable
data class Jinx(override val characterIds: List<String>, override val text: String) : CharacterPair

/** Interacción de la wiki: cómo se resuelven dos habilidades juntas (solo para el Narrador). */
@Serializable
data class Interaction(
    override val characterIds: List<String>,
    override val text: String,
    /** Resolución oficial de The Pandemonium Institute (el resto son de la comunidad). */
    val official: Boolean? = null,
) : CharacterPair

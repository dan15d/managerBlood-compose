package es.dan15d.grimorio.domain.services

// Las fichas de información del juego: los mensajes que el Narrador enseña por la noche
// («ESTE PERSONAJE TE HA ELEGIDO», «TÚ ERES»…). Los textos son los que usa la wiki de Villacuervos.

enum class InfoTokenId(val key: String) {
    Demon("demon"),
    Minions("minions"),
    NotInPlay("not_in_play"),
    YouAre("you_are"),
    PlayerIs("player_is"),
    SelectedYou("selected_you"),
    CharacterIs("character_is"),
    VotedToday("voted_today"),
    NominatedToday("nominated_today"),
}

data class InfoToken(
    val id: InfoTokenId,
    val text: String,
    /** Para qué se usa, en pocas palabras (para elegirla sin dudar). */
    val use: String,
)

val INFO_TOKENS: List<InfoToken> = listOf(
    InfoToken(InfoTokenId.SelectedYou, "Este personaje te ha elegido", "Cerenovus, Meretriz, Exorcista, Sereno, Predicador…"),
    InfoToken(InfoTokenId.YouAre, "Tú eres", "Quien cambia de personaje o de bando"),
    InfoToken(InfoTokenId.PlayerIs, "Este jugador es", "Lunático, Atrapa tormentas…"),
    InfoToken(InfoTokenId.CharacterIs, "Este personaje es", "El Rey, al Demonio"),
    InfoToken(InfoTokenId.Demon, "Este es el Demonio", "A los Esbirros, la primera noche"),
    InfoToken(InfoTokenId.Minions, "Estos son tus Esbirros", "Al Demonio, la primera noche"),
    InfoToken(InfoTokenId.NotInPlay, "Estos personajes no están en juego", "Faroles del Demonio, Chivato…"),
    InfoToken(InfoTokenId.VotedToday, "¿Has votado hoy?", "Florista, si no recuerdas si el Demonio votó"),
    InfoToken(InfoTokenId.NominatedToday, "¿Has nominado hoy?", "Pregonero, si no recuerdas si un Esbirro nominó"),
)

fun infoToken(id: InfoTokenId): InfoToken = INFO_TOKENS.first { it.id == id }

data class StepInfoToken(
    val token: InfoTokenId,
    /** Hace falta enseñar otra ficha además de la del personaje (la del que debe estar loco…). */
    val more: String? = null,
    val firstNightOnly: Boolean = false,
)

/**
 * Personajes cuyo paso de la noche termina enseñando una ficha de información junto a la suya
 * (según la guía de la wiki): la noche le pone un botón directo.
 */
val STEP_INFO_TOKENS: Map<String, StepInfoToken> = mapOf(
    "cerenovus" to StepInfoToken(InfoTokenId.SelectedYou, more = "Añade el personaje del que debe estar loco"),
    "boffin" to StepInfoToken(InfoTokenId.SelectedYou, more = "Añade el personaje cuya habilidad tiene"),
    "barista" to StepInfoToken(InfoTokenId.SelectedYou),
    "barber" to StepInfoToken(InfoTokenId.SelectedYou),
    "exorcist" to StepInfoToken(InfoTokenId.SelectedYou),
    "harlot" to StepInfoToken(InfoTokenId.SelectedYou),
    "harpy" to StepInfoToken(InfoTokenId.SelectedYou),
    "hatter" to StepInfoToken(InfoTokenId.SelectedYou),
    "nightwatchman" to StepInfoToken(InfoTokenId.SelectedYou),
    "preacher" to StepInfoToken(InfoTokenId.SelectedYou),
    "king" to StepInfoToken(InfoTokenId.CharacterIs, firstNightOnly = true),
)

fun stepInfoToken(characterId: String, firstNight: Boolean): StepInfoToken? {
    val entry = STEP_INFO_TOKENS[characterId]
    return if (entry != null && (!entry.firstNightOnly || firstNight)) entry else null
}

package es.dan15d.grimorio.domain.entities

import kotlinx.serialization.Serializable

@Serializable
data class Script(
    val id: String,
    val name: String,
    val author: String = "",
    /** Ids de personajes. Las Fábulas y Viajeros también pueden estar en el guion. */
    val characterIds: List<String>,
    val official: Boolean = false,
    val description: String? = null,
    /** Portada del guion personalizado: data URL ya reducida o URL de un logo. */
    val image: String? = null,
    /** Jugadores mínimos recomendados (BMR y S&V se recomiendan con 7 o más). */
    val recommendedMinPlayers: Int? = null,
)

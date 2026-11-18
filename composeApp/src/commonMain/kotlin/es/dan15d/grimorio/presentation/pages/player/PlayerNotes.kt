package es.dan15d.grimorio.presentation.pages.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import es.dan15d.grimorio.domain.entities.Alignment
import es.dan15d.grimorio.infrastructure.GrimJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Lo que un jugador apunta de otro: qué dice ser, qué cree que es y notas libres. */
@Serializable
data class SeatNote(
    /** Personaje que dice ser. */
    val claim: String? = null,
    val guess: Alignment? = null,
    val text: String? = null,
) {
    val isEmpty: Boolean get() = claim == null && guess == null && text.isNullOrBlank()
}

private fun key(gameId: String) = "grimorio.notas.$gameId"

private val notesSerializer = MapSerializer(String.serializer(), SeatNote.serializer())

/** El almacenamiento del dispositivo (en la web, el mismo localStorage que la app en React); null si no hay. */
private fun storage(): Settings? = try {
    Settings()
} catch (_: Throwable) {
    null
}

/**
 * Notas privadas del jugador sobre los demás, por nombre (los asientos pueden cambiar si el
 * Narrador los intercambia). Solo se guardan en este móvil: no llegan al Narrador ni a nadie.
 */
@Stable
class PlayerNotes(private val gameId: String) {
    private val settings = storage()

    var notes: Map<String, SeatNote> by mutableStateOf(load())
        private set

    private fun load(): Map<String, SeatNote> = try {
        settings?.getStringOrNull(key(gameId))?.let { GrimJson.decodeFromString(notesSerializer, it) } ?: emptyMap()
    } catch (_: Throwable) {
        emptyMap()
    }

    fun update(name: String, note: SeatNote) {
        val next = if (note.isEmpty) notes - name else notes + (name to note)
        notes = next
        try {
            settings?.putString(key(gameId), GrimJson.encodeToString(notesSerializer, next))
        } catch (_: Throwable) {
            /* sin almacenamiento: las notas duran mientras la página esté abierta */
        }
    }
}

@Composable
fun rememberPlayerNotes(gameId: String): PlayerNotes = remember(gameId) { PlayerNotes(gameId) }

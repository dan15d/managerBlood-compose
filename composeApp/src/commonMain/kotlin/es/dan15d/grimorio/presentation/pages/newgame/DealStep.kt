package es.dan15d.grimorio.presentation.pages.newgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.services.GARDENER
import es.dan15d.grimorio.domain.services.isDealt
import es.dan15d.grimorio.presentation.components.GrimAlert
import es.dan15d.grimorio.presentation.components.GrimAlertKind
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.GrimOption
import es.dan15d.grimorio.presentation.components.GrimSelect
import es.dan15d.grimorio.presentation.components.GrimTextField
import es.dan15d.grimorio.presentation.components.color
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType
import kotlin.time.Clock

/** Paso 5: nombre de la partida, cómo se reparten las fichas y quién es Viajero. */
@Composable
internal fun DealStep(state: NewGameState, wide: Boolean) {
    val script = state.script ?: return
    val today = remember { todayEs() }
    StepTitle("Reparto")
    GrimCard {
        GrimTextField(
            state.gameName,
            { state.gameName = it },
            label = "Nombre de la partida",
            placeholder = "${script.name} · $today",
        )
    }
    CardGrid(wide) {
        ModeCard(
            Lucide.Shuffle,
            "Sacar de la bolsa",
            "Los personajes se reparten al azar entre los asientos, como en la mesa.",
            selected = !state.manualSeats,
            onClick = { state.manualSeats = false },
            modifier = Modifier.weight(1f),
        )
        ModeCard(
            Lucide.PenLine,
            "Asignar a mano",
            "Ya habéis sacado las fichas físicas: indica qué le tocó a cada uno.",
            selected = state.manualSeats,
            onClick = { state.manualSeats = true },
            modifier = Modifier.weight(1f),
        )
    }
    if (GARDENER in state.fabled && !state.manualSeats) {
        GrimAlert("Con el Jardinero puedes asignar tú los personajes: elige «Asignar a mano».", GrimAlertKind.Info)
    }
    if (state.travellers.isNotEmpty()) TravellerSeatsCard(state)
    if (state.manualSeats) ManualSeatsCard(state)
    GrimAlert(
        "Al empezar irás al Grimorio. Desde allí puedes pasar el móvil para que cada jugador vea su personaje en secreto.",
        GrimAlertKind.Info,
    )
}

@Composable
private fun TravellerSeatsCard(state: NewGameState) {
    val bySeat = state.travellerBySeat
    val names = state.playerNames
    GrimCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardTitle("¿Quién es Viajero?")
            for (id in state.travellers) {
                key(id) {
                    val seat = bySeat.entries.firstOrNull { it.value == id }?.key
                    val options = names.mapIndexed { s, n -> GrimOption(s, "${s + 1}. $n", enabled = !(s in bySeat && bySeat[s] != id)) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            state.app.catalog.get(id)?.name ?: id,
                            Modifier.weight(0.4f),
                            style = GrimType.body.copy(color = Team.Traveller.color),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        GrimSelect(seat, options, onSelect = { state.chosenTravellerSeats = state.chosenTravellerSeats + (id to it) }, modifier = Modifier.weight(0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualSeatsCard(state: NewGameState) {
    val assignment = state.bagAssignment
    // Cada ficha una vez, salvo las repetidas (Papa, Tonto del pueblo), que salen tantas veces como estén.
    val dealt = remember(state.chosen) { state.chosen.filter(::isDealt).distinctBy { it.id } }
    GrimCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (seat in state.bagSeats) {
                key(seat) {
                    val current = assignment[seat]
                    val options = listOf(GrimOption<String?>(null, "— Elige —")) + dealt
                        .filter { c -> current == c.id || assignment.values.count { it == c.id } < state.selected.count { it == c.id } }
                        .map { GrimOption<String?>(it.id, it.name) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${seat + 1}", Modifier.width(26.dp), style = GrimType.label.copy(fontSize = 14.sp, color = Grim.muted, textAlign = TextAlign.End))
                        Text(state.playerNames[seat], Modifier.weight(0.4f), style = GrimType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        GrimSelect(
                            current,
                            options,
                            onSelect = { value ->
                                state.assignment = if (value != null) assignment + (seat to value) else assignment - seat
                            },
                            modifier = Modifier.weight(0.6f),
                        )
                    }
                }
            }
        }
    }
}

/** La fecha de hoy como la escribe la web en español («18/11/2026»). */
private fun todayEs(): String {
    // Días desde 1970 → fecha civil (algoritmo de Howard Hinnant), sin depender de otra librería.
    val days = Clock.System.now().epochSeconds.floorDiv(86_400L)
    val z = days + 719_468
    val era = z.floorDiv(146_097)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + (if (month <= 2) 1 else 0)
    return "$day/$month/$year"
}

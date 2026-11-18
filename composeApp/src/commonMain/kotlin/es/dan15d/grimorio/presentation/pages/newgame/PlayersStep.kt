package es.dan15d.grimorio.presentation.pages.newgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.domain.services.MAX_PLAYERS
import es.dan15d.grimorio.domain.services.MIN_PLAYERS
import es.dan15d.grimorio.domain.services.baseDistribution
import es.dan15d.grimorio.domain.services.travellerCount
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.DistributionBar
import es.dan15d.grimorio.presentation.components.GrimAlert
import es.dan15d.grimorio.presentation.components.GrimAlertKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.GrimIconButton
import es.dan15d.grimorio.presentation.components.GrimTextField
import es.dan15d.grimorio.presentation.components.LobbyPanel
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/** Paso 3 con la nube: la sala de espera, donde los jugadores se unen con el código o el QR. */
@Composable
internal fun LobbyStep(state: NewGameState) {
    val lobby = state.lobby ?: return
    StepTitle("Sala de espera")
    Muted("Que cada jugador escanee el QR o entre con el código. Necesitas al menos $MIN_PLAYERS.")
    LobbyPanel(lobby, state.seats)
    val n = state.seats.size
    if (n >= MIN_PLAYERS) DistributionBar(baseDistribution(n), travellerCount(n))
}

/** Paso 3 sin la nube: cuántos jugadores y sus nombres. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayersStep(state: NewGameState, wide: Boolean) {
    val count = state.playerCount
    StepTitle("¿Cuántos jugadores?")
    GrimCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                GrimIconButton(Lucide.Minus, "Menos jugadores", { state.changePlayerCount(-1) }, enabled = count > MIN_PLAYERS, bordered = true, size = 56.dp)
                Text(
                    "$count",
                    Modifier.widthIn(min = 72.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = TextStyle(
                        fontFamily = GrimType.fonts.display,
                        fontWeight = FontWeight.Black,
                        fontSize = 54.sp,
                        lineHeight = 60.sp,
                        color = Grim.textStrong,
                        textAlign = TextAlign.Center,
                    ),
                )
                GrimIconButton(Lucide.Plus, "Más jugadores", { state.changePlayerCount(1) }, enabled = count < MAX_PLAYERS, bordered = true, size = 56.dp)
            }
            DistributionBar(baseDistribution(count), travellerCount(count))
            val script = state.script
            val min = script?.recommendedMinPlayers
            if (script != null && min != null && count < min) {
                GrimAlert("${script.name} se recomienda con $min o más jugadores.", GrimAlertKind.Warning)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CardTitle("Nombres")
        Muted("En el orden en que están sentados, en el sentido de las agujas del reloj. Opcional.")
    }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = if (wide) 2 else 1,
    ) {
        for (i in 0 until count) {
            key(i) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}", Modifier.width(30.dp), style = GrimType.label.copy(fontSize = 14.sp, color = Grim.muted, textAlign = TextAlign.End))
                    GrimTextField(
                        state.names.getOrNull(i) ?: "",
                        { value ->
                            val copy = state.names.toMutableList()
                            while (copy.size <= i) copy.add("")
                            copy[i] = value
                            state.names = copy
                        },
                        Modifier.weight(1f),
                        placeholder = "Jugador ${i + 1}",
                    )
                }
            }
        }
    }
    if (state.names.any { it.isNotEmpty() }) {
        GrimButton("Borrar nombres", { state.names = emptyList() }, kind = ButtonKind.Ghost, small = true)
    }
}

package es.dan15d.grimorio.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.domain.entities.Lobby
import es.dan15d.grimorio.domain.entities.Seat
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.platform.shareText
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType
import kotlinx.coroutines.launch

/** Sala de espera: código y QR para unirse, y los asientos en el orden de la mesa. */
@Suppress("DEPRECATION")
@Composable
fun LobbyPanel(lobby: Lobby, seats: List<Seat>, modifier: Modifier = Modifier) {
    val state = LocalAppState.current
    val manage = state.app?.lobby ?: return
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var showQr by remember { mutableStateOf(true) }
    val url = remember(lobby.joinCode) { joinUrl(lobby.joinCode) }

    val share: () -> Unit = {
        scope.launch {
            val shared = try {
                shareText("Únete a la partida", "Código: ${lobby.joinCode}", url)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                true // compartir cancelado
            }
            if (!shared) {
                clipboard.setText(AnnotatedString(url))
                state.toast("Enlace copiado.")
            }
        }
    }
    val add: () -> Unit = {
        if (name.isNotBlank()) {
            state.run {
                manage.addSeat(lobby.gameId, name)
                name = ""
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GrimCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Muted("Código para unirse")
                Text(
                    lobby.joinCode,
                    Modifier
                        .padding(start = 10.dp)
                        .semantics { contentDescription = "Código ${lobby.joinCode.toList().joinToString(" ")}" },
                    style = TextStyle(
                        fontFamily = GrimType.fonts.display,
                        fontWeight = FontWeight.Black,
                        fontSize = 42.sp,
                        letterSpacing = 10.sp,
                        color = Grim.goldBright,
                        textAlign = TextAlign.Center,
                    ),
                )
                if (showQr) QrCode(url, size = 210.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    GrimButton("Compartir enlace", share, icon = Lucide.Share2, small = true)
                    GrimButton(if (showQr) "Ocultar QR" else "Ver QR", { showQr = !showQr }, kind = ButtonKind.Ghost, small = true)
                }
            }
        }

        GrimCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Jugadores", Modifier.weight(1f), style = GrimType.heading)
                    Text("${seats.size}", style = GrimType.label.copy(fontWeight = FontWeight.Bold))
                }
                Muted("Aparecen al unirse. Ordénalos como estén sentados, en el sentido de las agujas del reloj.")
                if (seats.isEmpty()) {
                    Muted("Esperando a que entre alguien…", Modifier.fillMaxWidth().padding(vertical = 12.dp), center = true)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    seats.forEachIndexed { i, s ->
                        key(s.id) {
                            SeatRow(
                                index = i,
                                seat = s,
                                last = i == seats.lastIndex,
                                onUp = { state.run { manage.move(lobby.gameId, seats, s.id, -1) } },
                                onDown = { state.run { manage.move(lobby.gameId, seats, s.id, 1) } },
                                onRemove = { state.run { manage.removeSeat(s.id) } },
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GrimTextField(name, { name = it }, Modifier.weight(1f), placeholder = "Añadir a alguien sin móvil", onDone = add)
                    GrimButton("Añadir", add, enabled = name.isNotBlank())
                }
            }
        }
    }
}

@Composable
private fun SeatRow(index: Int, seat: Seat, last: Boolean, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Grim.bg2)
            .border(1.dp, Grim.line, shape)
            .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "${index + 1}",
            Modifier.width(24.dp),
            style = GrimType.label.copy(fontSize = 13.sp, color = Grim.muted, textAlign = TextAlign.End),
        )
        Row(Modifier.weight(1f).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(seat.name, Modifier.weight(1f, fill = false), style = GrimType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(
                if (seat.userId != null) Lucide.Smartphone else Lucide.PenLine,
                if (seat.userId != null) "Con móvil" else "Añadido a mano",
                tint = Grim.muted,
                modifier = Modifier.size(14.dp),
            )
        }
        GrimIconButton(Lucide.ChevronUp, "Subir a ${seat.name}", onUp, enabled = index > 0, size = 40.dp)
        GrimIconButton(Lucide.ChevronDown, "Bajar a ${seat.name}", onDown, enabled = !last, size = 40.dp)
        GrimIconButton(Lucide.X, "Quitar a ${seat.name}", onRemove, size = 40.dp)
    }
}

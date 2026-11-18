package es.dan15d.grimorio.presentation.pages.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.domain.entities.GameStatus
import es.dan15d.grimorio.domain.services.clearTimer
import es.dan15d.grimorio.domain.services.setTimer
import es.dan15d.grimorio.presentation.components.Alarm
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.clock
import es.dan15d.grimorio.presentation.components.parseMillis
import es.dan15d.grimorio.presentation.components.rememberNow
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/** Duraciones de la cuenta atrás, en segundos (nominaciones cortas, debate largo). */
private val PRESETS = listOf(60, 180, 300)

/** Cuánto lleva la fase: a partir de una hora ya no interesa el segundo (la partida viene de otro día). */
fun elapsedLabel(ms: Long): String = if (ms >= 3_600_000) "+${ms / 3_600_000} h" else clock(ms)

/**
 * Una línea discreta: cuánto lleva la fase (la noche o el día) y una cuenta atrás para el debate
 * o las nominaciones, que también ven los jugadores en su móvil. Solo esta línea se vuelve a
 * dibujar cada segundo.
 */
@Composable
fun PhaseClock(phaseStartedAt: String, timerEndsAt: String?, night: Boolean, onChange: ((Game) -> Game) -> Unit) {
    val now = rememberNow(true)
    val elapsed = now - parseMillis(phaseStartedAt)
    val left = timerEndsAt?.let { parseMillis(it) - now }
    val over = left != null && left <= 0
    val style = TextStyle(fontFamily = GrimType.fonts.ui, fontSize = 14.sp, color = Grim.text, fontFeatureSettings = "tnum")

    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Lucide.Timer, "Tiempo que lleva ${if (night) "la noche" else "el día"}", tint = Grim.text, modifier = Modifier.size(16.dp))
            Text(elapsedLabel(elapsed), style = style)
            Text(if (night) "de noche" else "de día", style = style.copy(color = Grim.muted))
        }
        if (left != null) {
            val pulse = if (over) {
                val transition = rememberInfiniteTransition(label = "tiempo")
                val alpha by transition.animateFloat(1f, 0.35f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "alfa")
                alpha
            } else {
                1f
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.alpha(pulse), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Lucide.Hourglass, null, tint = Grim.goldBright, modifier = Modifier.size(16.dp))
                    Text(if (over) "¡Tiempo!" else clock(left), style = style.copy(fontWeight = FontWeight.Bold, color = Grim.goldBright))
                }
                GrimButton(if (over) "Quitar" else "Parar", { onChange(::clearTimer) }, kind = ButtonKind.Ghost, small = true)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (seconds in PRESETS) {
                    Box(
                        Modifier
                            .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Cuenta atrás de ${seconds / 60} ${if (seconds == 60) "minuto" else "minutos"}") {
                                onChange { g -> setTimer(g, seconds) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${seconds / 60}", style = style.copy(fontWeight = FontWeight.Bold))
                    }
                }
                Text("min", style = style.copy(color = Grim.muted), modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}

/**
 * Cuando la cuenta atrás termina, vibra y pita. Va aparte del reloj visible (que puede quedar fuera
 * de la pantalla al bajar por el panel) y solo se redibuja ella cada segundo.
 */
@Composable
fun TimerAlarm(game: Game) {
    val endsAt = game.timer?.endsAt
    if (endsAt == null || game.status != GameStatus.InProgress) return
    val now = rememberNow(true)
    Alarm(endsAt, now)
}

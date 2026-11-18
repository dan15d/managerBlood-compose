package es.dan15d.grimorio.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.presentation.theme.GrimType

enum class GrimAlertKind { Error, Warning, Info, Ok }

/** Aviso dentro de la página (como .alert): rojo para errores, ámbar para avisos, azul para información. */
@Composable
fun GrimAlert(text: String, kind: GrimAlertKind, modifier: Modifier = Modifier) {
    val (border, background) = when (kind) {
        GrimAlertKind.Error -> Color(0x99E0242F) to Color(0x269B111E)
        GrimAlertKind.Warning -> Color(0x80E8B84A) to Color(0x14E8B84A)
        GrimAlertKind.Info -> Color(0x735AA2FF) to Color(0x145AA2FF)
        GrimAlertKind.Ok -> Color(0x807CCF7F) to Color(0x147CCF7F)
    }
    val shape = RoundedCornerShape(8.dp)
    Text(
        text,
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp),
    )
}

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

/** Tipo de aviso dentro de la página (las clases .alert--error, --warning, --info y --ok). */
enum class NoticeKind(internal val border: Color, internal val background: Color) {
    Error(Color(0x99E0242F), Color(0x269B111E)),
    Warning(Color(0x80E8B84A), Color(0x14E8B84A)),
    Info(Color(0x735AA2FF), Color(0x145AA2FF)),
    Ok(Color(0x807CCF7F), Color(0x147CCF7F)),
}

/** Aviso con borde de color dentro de la página (como .alert). */
@Composable
fun NoticeBox(text: String, kind: NoticeKind, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text,
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(kind.background)
            .border(1.dp, kind.border, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        style = GrimType.body.copy(fontSize = 16.sp, lineHeight = 23.sp),
    )
}

package es.dan15d.grimorio.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.presentation.theme.GrimType

/** El aldabón de Blood on the Clocktower, con su halo, para la portada. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Box(modifier.size(210.dp), contentAlignment = Alignment.Center) {
        // El halo morado de .hero__logo (drop-shadow de 32 px) y un brillo claro pegado al logo.
        Box(
            Modifier.size(210.dp).drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0xBF7C48C4),
                        0.45f to Color(0x667C48C4),
                        1f to Color.Transparent,
                    ),
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x40E6D7FF), Color.Transparent)),
                    radius = size.minDimension * 0.32f,
                )
            },
        )
        GrimImage("logo.webp", Modifier.size(width = 150.dp, height = 163.dp))
    }
}

/**
 * Cabecera de la portada (como .hero): el logo con su halo, «El Grimorio», el texto de
 * presentación y debajo las acciones.
 */
@Composable
fun BrandHero(text: String, modifier: Modifier = Modifier, actions: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark()
        Text(
            "El Grimorio",
            Modifier.padding(bottom = 8.dp),
            style = GrimType.title.copy(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        )
        Text(
            text,
            Modifier.widthIn(max = 560.dp).padding(bottom = 22.dp),
            style = GrimType.body.copy(fontSize = 19.sp, lineHeight = 27.sp, color = GrimType.small.color, textAlign = TextAlign.Center),
        )
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = actions,
        )
    }
}

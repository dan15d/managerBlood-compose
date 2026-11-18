package es.dan15d.grimorio.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.dan15d.grimorio.presentation.platform.appUrl
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

/** QR dibujado en el propio dispositivo (sin servicios externos), en blanco para que lo lea cualquier cámara. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val painter = rememberQrCodePainter(text)
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(12.dp),
    ) {
        Image(painter, contentDescription = "Código QR para unirse", modifier = Modifier.fillMaxSize())
    }
}

/** Enlace directo para unirse con el código ya puesto. */
fun joinUrl(code: String): String = appUrl() + "#/unirse/" + code

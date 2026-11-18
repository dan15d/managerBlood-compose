package es.dan15d.grimorio.presentation.platform

import androidx.compose.runtime.Composable

/**
 * Selector de imagen (la portada de un guion): abre el selector del sistema, reduce la imagen a
 * [maxSide] píxeles por lado como mucho y la entrega como data URL (webp, o jpeg si no se puede).
 * Devuelve la función que abre el selector, o null si la plataforma no tiene (y no se enseña el botón).
 */
@Composable
expect fun rememberImagePicker(
    maxSide: Int = 800,
    onPicked: (dataUrl: String) -> Unit,
    onError: (message: String) -> Unit,
): (() -> Unit)?

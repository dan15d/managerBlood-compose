package es.dan15d.grimorio.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
actual fun rememberImagePicker(
    maxSide: Int,
    onPicked: (dataUrl: String) -> Unit,
    onError: (message: String) -> Unit,
): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val picked = rememberUpdatedState(onPicked)
    val error = rememberUpdatedState(onError)
    return remember(maxSide) {
        {
            // Diálogo nativo de abrir fichero (modal: espera a que se elija o se cancele).
            val dialog = FileDialog(null as Frame?, "Elegir imagen", FileDialog.LOAD)
            dialog.isVisible = true
            val file = dialog.file?.let { File(dialog.directory, it) }
            dialog.dispose()
            if (file != null) {
                scope.launch {
                    try {
                        picked.value(withContext(Dispatchers.IO) { shrinkImage(file, maxSide) })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error.value(e.message ?: "No he podido leer la imagen.")
                    }
                }
            }
        }
    }
}

/** Lee la imagen, la reduce a [maxSide] por lado y la devuelve como data URL en jpeg (ImageIO no escribe webp). */
private fun shrinkImage(file: File, maxSide: Int): String {
    val source = runCatching { ImageIO.read(file) }.getOrNull() ?: throw Exception("No he podido leer la imagen.")
    val scale = min(1.0, maxSide.toDouble() / max(source.width, source.height))
    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.drawImage(source, 0, 0, width, height, null)
    g.dispose()
    val out = ByteArrayOutputStream()
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    ImageIO.createImageOutputStream(out).use { stream ->
        writer.output = stream
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = 0.82f
        }
        writer.write(null, IIOImage(image, null, null), params)
        writer.dispose()
    }
    return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray())
}

package es.dan15d.grimorio.presentation.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
actual fun rememberImagePicker(
    maxSide: Int,
    onPicked: (dataUrl: String) -> Unit,
    onError: (message: String) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked = rememberUpdatedState(onPicked)
    val error = rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                picked.value(withContext(Dispatchers.IO) { shrinkImage(context, uri, maxSide) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value(e.message ?: "No he podido leer la imagen.")
            }
        }
    }
    return remember(launcher) { { launcher.launch("image/*") } }
}

/** Lee la imagen elegida, la reduce a [maxSide] por lado y la devuelve como data URL. */
private fun shrinkImage(context: Context, uri: Uri, maxSide: Int): String {
    val type = context.contentResolver.getType(uri)
    if (type != null && !type.startsWith("image/")) throw Exception("Eso no es una imagen.")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw Exception("No he podido leer la imagen.")
    // Se decodifica ya a menor tamaño para no llenar la memoria con fotos grandes.
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: throw Exception("No he podido leer la imagen.")
    val scale = min(1f, maxSide / max(decoded.width, decoded.height).toFloat())
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
    } else {
        decoded
    }
    val out = ByteArrayOutputStream()
    val mime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 82, out)
        "image/webp"
    } else {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        "image/jpeg"
    }
    return "data:$mime;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

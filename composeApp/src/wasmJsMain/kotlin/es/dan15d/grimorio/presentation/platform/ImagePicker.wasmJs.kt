@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package es.dan15d.grimorio.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

// Como imageFile.ts de la web: <input type="file"> y un canvas para reducir la imagen.
@JsFun("""(maxSide, onPicked, onError) => {
  const input = document.createElement("input");
  input.type = "file";
  input.accept = "image/*";
  input.style.display = "none";
  document.body.appendChild(input);
  const done = () => input.remove();
  input.addEventListener("cancel", done);
  input.addEventListener("change", async () => {
    const file = input.files && input.files[0];
    done();
    if (!file) return;
    if (!file.type.startsWith("image/")) { onError("Eso no es una imagen."); return; }
    let bitmap;
    try { bitmap = await createImageBitmap(file); } catch (e) { onError("No he podido leer la imagen."); return; }
    const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement("canvas");
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext("2d").drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    const webp = canvas.toDataURL("image/webp", 0.82);
    onPicked(webp.startsWith("data:image/webp") ? webp : canvas.toDataURL("image/jpeg", 0.82));
  });
  input.click();
}""")
private external fun jsPickImage(maxSide: Int, onPicked: (String) -> Unit, onError: (String) -> Unit)

@Composable
actual fun rememberImagePicker(
    maxSide: Int,
    onPicked: (dataUrl: String) -> Unit,
    onError: (message: String) -> Unit,
): (() -> Unit)? {
    val picked = rememberUpdatedState(onPicked)
    val error = rememberUpdatedState(onError)
    return remember(maxSide) { { jsPickImage(maxSide, { picked.value(it) }, { error.value(it) }) } }
}

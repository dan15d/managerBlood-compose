package es.dan15d.grimorio.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlin.js.Promise

@JsFun("""(sound) => {
  try { navigator.vibrate && navigator.vibrate([200, 100, 200, 100, 400]); } catch (e) {}
  if (!sound) return;
  try {
    const ctx = new AudioContext();
    for (const at of [0, 0.25, 0.5]) {
      const osc = ctx.createOscillator(); const gain = ctx.createGain();
      osc.type = "sine"; osc.frequency.value = 880; gain.gain.value = 0.2;
      osc.connect(gain); gain.connect(ctx.destination);
      osc.start(ctx.currentTime + at); osc.stop(ctx.currentTime + at + 0.18);
    }
    setTimeout(() => ctx.close(), 1500);
  } catch (e) {}
}""")
private external fun jsRing(sound: Boolean)

actual fun ring(sound: Boolean) = jsRing(sound)

/** Pide el bloqueo de pantalla encendida y lo repite al volver a la pestaña; devuelve cómo soltarlo. */
@JsFun("""() => {
  if (!("wakeLock" in navigator)) return () => {};
  let lock = null, active = true;
  const request = async () => {
    if (document.visibilityState !== "visible") return;
    try { lock = await navigator.wakeLock.request("screen"); if (!active) lock.release(); } catch (e) {}
  };
  request();
  document.addEventListener("visibilitychange", request);
  return () => { active = false; document.removeEventListener("visibilitychange", request); if (lock) lock.release().catch(() => {}); };
}""")
private external fun jsWakeLock(): JsAny

@JsFun("(release) => release()")
private external fun jsCall(release: JsAny)

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        val release = jsWakeLock()
        onDispose { jsCall(release) }
    }
}

actual fun appUrl(): String = window.location.origin + window.location.pathname

@JsFun("""(title, text, url) => {
  if (!navigator.share) return Promise.resolve(false);
  return navigator.share(url ? { title, text, url } : { title, text }).then(() => true, () => true);
}""")
private external fun jsShare(title: String, text: String, url: String?): Promise<JsBoolean>

actual suspend fun shareText(title: String, text: String, url: String?): Boolean =
    jsShare(title, text, url).await<JsBoolean>().toBoolean()

actual fun openUrl(url: String) {
    window.open(url, "_blank")
}

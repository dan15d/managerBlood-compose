package es.dan15d.grimorio.presentation.platform

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** El contexto de la app, para vibrar y abrir enlaces fuera de la composición (lo pone MainActivity). */
lateinit var appContext: Context

actual fun ring(sound: Boolean) {
    runCatching {
        @Suppress("DEPRECATION")
        val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 400), -1))
    }
    if (sound) runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_ALARM, 60)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 700)
    }
}

@Composable
actual fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

actual fun appUrl(): String = PUBLISHED_URL

actual suspend fun shareText(title: String, text: String, url: String?): Boolean {
    val body = if (url != null) "$text\n$url" else text
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    appContext.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}

actual fun openUrl(url: String) {
    appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

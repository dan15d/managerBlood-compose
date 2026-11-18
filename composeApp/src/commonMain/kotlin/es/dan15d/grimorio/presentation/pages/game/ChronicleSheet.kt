package es.dan15d.grimorio.presentation.pages.game

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import es.dan15d.grimorio.domain.entities.Game
import es.dan15d.grimorio.presentation.app.ToastKind
import es.dan15d.grimorio.presentation.app.errorMessage
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimSheet
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Crónica de la partida terminada. Solo se escribe al pulsar el botón: cada una es una consulta a la IA. */
@Suppress("DEPRECATION")
@Composable
fun ChronicleSheet(c: GameController, game: Game, onSave: (String) -> Unit, onClose: () -> Unit) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val chronicle = game.chronicle
    val title = chronicle?.substringBefore('\n') ?: ""
    val body = chronicle?.substringAfter('\n', "")?.trim() ?: ""

    val write: () -> Unit = {
        loading = true
        error = ""
        // Si se cierra la hoja (o se sale de la partida) mientras escribe, se guarda igual.
        c.workScope.launch {
            try {
                onSave(c.app.writeChronicle.execute(game))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = errorMessage(e)
            } finally {
                loading = false
            }
        }
    }
    val copy: () -> Unit = {
        try {
            clipboard.setText(AnnotatedString(chronicle ?: ""))
            c.toast("Crónica copiada.")
        } catch (_: Throwable) {
            c.toast("No he podido copiar al portapapeles.", ToastKind.Error)
        }
    }

    GrimSheet("Crónica de la partida", onClose) {
        if (chronicle != null) {
            Text(title, style = GrimType.heading)
            Text(body, style = GrimType.body)
        } else {
            Muted(
                "La IA cuenta la partida a partir del registro: quién era quién, las muertes, las votaciones y el momento que la decidió. Se guarda con la partida, así que solo se pide una vez.",
            )
        }

        if (error.isNotEmpty()) GameAlert(AlertKind.Error) { Text(error, style = GrimType.body.copy(fontStyle = FontStyle.Normal, color = Grim.text)) }

        if (!c.app.aiAvailable) {
            SettingsLinkAlert("Para escribirla necesitas conectar la IA. ", "Configúrala en Ajustes")
            if (chronicle != null) {
                ActionBar(sheet = true) {
                    GrimButton("Copiar", copy, actionButton(), kind = ButtonKind.Primary, icon = Lucide.Copy)
                }
            }
        } else {
            ActionBar(sheet = true) {
                if (chronicle != null) {
                    GrimButton(if (loading) "Escribiendo…" else "Otra versión", write, actionButton(), icon = Lucide.RefreshCw, enabled = !loading)
                    GrimButton("Copiar", copy, actionButton(), kind = ButtonKind.Primary, icon = Lucide.Copy)
                } else {
                    GrimButton(
                        if (loading) "Escribiendo la crónica…" else "Escribir la crónica",
                        write,
                        actionButton(),
                        kind = ButtonKind.Primary,
                        icon = Lucide.Feather,
                        enabled = !loading,
                    )
                }
            }
        }
    }
}

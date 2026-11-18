package es.dan15d.grimorio.presentation.pages.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.Collapsible
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimTextField
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

private val QUICK = listOf(
    "¿Qué información falsa le doy al Borracho o a los envenenados esta noche?",
    "Resume cómo va la partida y qué equipo lleva ventaja.",
    "¿Qué debería vigilar esta noche como Narrador?",
    "¿Cómo resuelvo la interacción entre los personajes en juego?",
)

/**
 * Consejero de IA: ve el Grimorio entero y responde dudas del Narrador. Recuerda la conversación
 * mientras la partida siga abierta, así que se puede repreguntar; no se guarda con la partida.
 * Las respuestas van como elementos de la lista, lo último primero.
 */
fun LazyListScope.aiPanelItems(c: GameController, aiNotes: String?, modifier: Modifier) {
    if (aiNotes != null) {
        item(key = "ai-notes") {
            Box(modifier.padding(bottom = 12.dp)) {
                Collapsible("Notas de la preparación") {
                    Text(aiNotes, style = GrimType.body)
                }
            }
        }
    }
    if (!c.app.aiAvailable) {
        item(key = "ai-off") { Box(modifier) { SettingsLinkAlert("El consejero de IA necesita conexión con DeepSeek. ", "Configúralo en Ajustes") } }
        return
    }
    item(key = "ai-form") { Box(modifier) { AiForm(c) } }
    itemsIndexed(c.aiThread, key = { i, _ -> "ai:${c.aiThread.size - i}" }) { _, x ->
        Box(modifier.padding(top = 12.dp)) {
            GameAlert(if (x.error) AlertKind.Error else AlertKind.Info) {
                Muted(x.q)
                Text(x.a, style = GrimType.body)
            }
        }
    }
}

@Composable
private fun AiForm(c: GameController) {
    var question by remember { mutableStateOf("") }
    val loading = c.aiLoading
    val ask = { q: String ->
        if (q.isNotBlank() && !loading) {
            question = ""
            c.askAi(q)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Chips {
            for (q in QUICK) key(q) { ChipButton(q, { ask(q) }, enabled = !loading, wrap = true) }
        }
        GrimTextField(
            question,
            { question = it },
            placeholder = if (c.aiThread.isNotEmpty()) "Sigue preguntando: recuerda lo que habéis hablado…" else "Pregunta lo que quieras sobre la partida…",
            singleLine = false,
            minLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GrimButton(
                if (loading) "Pensando…" else "Preguntar",
                { ask(question) },
                Modifier.weight(1f),
                kind = ButtonKind.Primary,
                enabled = question.isNotBlank() && !loading,
            )
            if (c.aiThread.isNotEmpty()) {
                GrimButton("Empezar de cero", { c.aiThread = emptyList() }, kind = ButtonKind.Ghost, enabled = !loading)
            }
        }
    }
}

/** Aviso con el enlace a Ajustes (para conectar la IA). */
@Composable
internal fun SettingsLinkAlert(text: String, link: String) {
    val nav = LocalNavigator.current
    GameAlert(AlertKind.Info) {
        Text(
            buildAnnotatedString {
                append(text)
                withLink(
                    LinkAnnotation.Clickable(
                        "ajustes",
                        TextLinkStyles(SpanStyle(color = Grim.goldBright, textDecoration = TextDecoration.Underline)),
                    ) { nav.navigate(Route.Settings) },
                ) { append(link) }
                append(".")
            },
            Modifier.fillMaxWidth(),
            style = GrimType.body.copy(fontSize = 16.sp),
        )
    }
}

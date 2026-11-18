package es.dan15d.grimorio.presentation.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.application.ports.AiTransport
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.app.ToastKind
import es.dan15d.grimorio.presentation.app.errorMessage
import es.dan15d.grimorio.presentation.app.supabaseConfigured
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.GrimTextField
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.NoticeBox
import es.dan15d.grimorio.presentation.components.NoticeKind
import es.dan15d.grimorio.presentation.components.Page
import es.dan15d.grimorio.presentation.components.PageHeader
import es.dan15d.grimorio.presentation.components.SectionLabel
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.platform.openUrl
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType
import kotlinx.coroutines.CancellationException

private data class ModelOption(val id: String, val label: String)

// Sugerencias de modelos de DeepSeek. La función en la nube solo acepta los de su lista (README).
private val MODELS = listOf(
    ModelOption("deepseek-flash", "DeepSeek Flash (barato y rápido)"),
    ModelOption("deepseek-v4-pro", "DeepSeek V4 Pro (más listo, unas 4 veces más caro)"),
)

private const val DEEPSEEK_KEYS_URL = "https://platform.deepseek.com/api_keys"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPage() {
    val state = LocalAppState.current
    val app = state.app ?: return
    val nav = LocalNavigator.current
    val settings = state.settings
    var form by remember { mutableStateOf(settings) }
    var testing by remember { mutableStateOf(false) }
    val dirty = form != settings

    val test: () -> Unit = {
        testing = true
        state.run {
            try {
                app.testAi.execute()
                state.toast("La IA responde correctamente.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                state.toast(errorMessage(e), ToastKind.Error)
            } finally {
                testing = false
            }
        }
    }

    Page(maxWidth = 720.dp) {
        PageHeader("Ajustes")

        SettingsCard {
            CardTitle("Dónde se guardan las partidas")
            if (app.storage == "supabase") {
                NoticeBox(
                    "En la nube (Supabase), ligadas a la sesión de este navegador. Desde otro móvil, o si borras los datos del " +
                        "navegador, no las verás: para eso hará falta iniciar sesión con una cuenta.",
                    NoticeKind.Ok,
                )
            } else {
                NoticeBox(
                    "En este navegador. Para guardarlas en la nube (y, más adelante, que los jugadores se unan desde su móvil), configura " +
                        "Supabase: mira el README del proyecto.",
                    NoticeKind.Info,
                )
            }
        }

        SettingsCard {
            CardTitle("Modo IA")
            Muted("La IA prepara repartos de personajes y te aconseja durante la partida. Usa DeepSeek: cuesta céntimos por partida.")

            if (supabaseConfigured) {
                Column {
                    SectionLabel("Conexión")
                    TransportOption(
                        selected = form.aiTransport == AiTransport.Cloud,
                        title = "Función en la nube",
                        note = "(recomendado: la clave queda en Supabase)",
                        onSelect = { form = form.copy(aiTransport = AiTransport.Cloud) },
                    )
                    TransportOption(
                        selected = form.aiTransport == AiTransport.Browser,
                        title = "Desde este navegador",
                        note = "(con tu clave de DeepSeek)",
                        onSelect = { form = form.copy(aiTransport = AiTransport.Browser) },
                    )
                }
            }

            if (form.aiTransport == AiTransport.Browser) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GrimTextField(
                        form.deepseekKey,
                        { form = form.copy(deepseekKey = it.trim()) },
                        label = "Clave de DeepSeek",
                        placeholder = "sk-…",
                        password = true,
                    )
                    KeyHelp()
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GrimTextField(form.aiModel, { form = form.copy(aiModel = it.trim()) }, label = "Modelo")
                // Las sugerencias (el datalist de la web): tocar una la escribe en el campo.
                for (model in MODELS) {
                    key(model.id) {
                        ModelSuggestion(model, selected = form.aiModel == model.id) { form = form.copy(aiModel = model.id) }
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GrimButton("Guardar", { state.updateSettings(form) }, kind = ButtonKind.Primary, enabled = dirty)
                GrimButton(
                    if (testing) "Probando…" else "Probar conexión",
                    test,
                    enabled = !dirty && app.aiAvailable && !testing,
                )
            }
            if (!app.aiAvailable && !dirty) {
                Muted("El modo IA está desactivado hasta que configures la conexión.")
            }
        }

        if (app.admin.required) {
            SettingsCard {
                CardTitle("Modo Narrador")
                Muted("Este dispositivo es de Narrador. Si lo vas a usar como jugador (o es un móvil prestado), sal del modo Narrador.")
                GrimButton(
                    "Salir del modo Narrador",
                    {
                        state.run {
                            app.admin.logout()
                            state.refreshRole()
                            nav.home()
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    GrimCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = GrimType.heading.copy(color = Grim.textStrong))
}

/** Una opción de conexión (radio de la web): todo el renglón es pulsable. */
@Composable
private fun TransportOption(selected: Boolean, title: String, note: String, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = Grim.accentBright, unselectedColor = Grim.muted),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Grim.textStrong)) { append(title) }
                append(" ")
                withStyle(SpanStyle(color = Grim.muted, fontSize = 15.sp)) { append(note) }
            },
            Modifier.weight(1f),
            style = GrimType.body,
        )
    }
}

/** Una sugerencia de modelo (las opciones del datalist de la web): al tocarla se escribe en el campo. */
@Composable
private fun ModelSuggestion(model: ModelOption, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) Grim.surface3 else Grim.surface2)
            .border(1.dp, if (selected) Grim.gold else Grim.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(model.id, style = GrimType.label.copy(color = Grim.textStrong))
            Text(model.label, style = GrimType.small.copy(fontSize = 13.sp, lineHeight = 17.sp))
        }
        if (selected) Icon(Lucide.Check, null, tint = Grim.goldBright, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun KeyHelp() {
    val linkStyle = TextLinkStyles(SpanStyle(color = Grim.goldBright, textDecoration = TextDecoration.Underline))
    Text(
        buildAnnotatedString {
            append("Se guarda solo en este navegador. Créala en ")
            withLink(LinkAnnotation.Clickable("deepseek", linkStyle) { openUrl(DEEPSEEK_KEYS_URL) }) {
                append("platform.deepseek.com/api_keys")
            }
            append(". DeepSeek es de prepago: solo gasta el saldo que cargues.")
        },
        style = GrimType.small.copy(fontSize = 14.sp, lineHeight = 20.sp),
    )
}

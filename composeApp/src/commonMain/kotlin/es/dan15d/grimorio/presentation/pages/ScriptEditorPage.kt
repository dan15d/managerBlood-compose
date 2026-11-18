package es.dan15d.grimorio.presentation.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.application.usecases.SaveScriptInput
import es.dan15d.grimorio.application.usecases.ScriptSize
import es.dan15d.grimorio.domain.entities.Edition
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.app.ToastKind
import es.dan15d.grimorio.presentation.app.errorMessage
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.CharacterPicker
import es.dan15d.grimorio.presentation.components.Chip
import es.dan15d.grimorio.presentation.components.GrimAlertKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.GrimSheet
import es.dan15d.grimorio.presentation.components.GrimTextField
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.ScriptReviewSheet
import es.dan15d.grimorio.presentation.components.SectionLabel
import es.dan15d.grimorio.presentation.components.color
import es.dan15d.grimorio.presentation.components.rememberScriptReview
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.pages.scripts.AiNeededNotice
import es.dan15d.grimorio.presentation.pages.scripts.AlertFrame
import es.dan15d.grimorio.presentation.pages.scripts.FooterBar
import es.dan15d.grimorio.presentation.pages.scripts.ScriptBanner
import es.dan15d.grimorio.presentation.pages.scripts.ScriptEditorStart
import es.dan15d.grimorio.presentation.platform.rememberImagePicker
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val EDITIONS: List<Pair<Edition?, String>> = listOf(
    null to "Todos",
    Edition.Tb to "Trouble Brewing",
    Edition.Bmr to "Bad Moon Rising",
    Edition.Snv to "Sects & Violets",
    Edition.Exp to "Experimentales",
)

/** Lo que la IA explica de su propuesta y los avisos si no ha clavado el reparto. */
private data class AiNote(val explanation: String, val warnings: List<String>)

/** Editor de guiones: nombre, portada y personajes; con importación JSON y ayuda de la IA. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ScriptEditorPage(id: String?) {
    val state = LocalAppState.current
    val app = state.app!!
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    // Lo que en la web venía en la URL (?desde=id, ?ia=1): se recoge una vez al abrir.
    val start = remember { ScriptEditorStart.take() }
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(listOf<String>()) }
    var edition by remember { mutableStateOf<Edition?>(null) }
    var importing by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    // Crear con IA (se abre solo si se llega desde «Con IA» en la lista de guiones).
    var aiOpen by remember { mutableStateOf(start.withAi) }
    var idea by remember { mutableStateOf("") }
    var size by remember { mutableStateOf(ScriptSize.Classic) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiNote by remember { mutableStateOf<AiNote?>(null) }
    val review = rememberScriptReview()
    val pickImage = rememberImagePicker(
        onPicked = { image = it },
        onError = { state.toast(it, ToastKind.Error) },
    )

    val generate: () -> Unit = {
        aiBusy = true
        scope.launch {
            try {
                val result = app.generateAiScript.execute(idea, size)
                name = result.name
                description = result.description
                if (author.isEmpty()) author = "Creado con IA"
                selected = result.characterIds
                edition = null
                aiNote = AiNote(result.explanation, result.warnings)
                aiOpen = false
                state.toast("Guion propuesto. Revísalo y guárdalo.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                state.toast(errorMessage(e), ToastKind.Error)
            } finally {
                aiBusy = false
            }
        }
    }

    // Cargar el guion que se edita o el oficial del que se parte.
    LaunchedEffect(app, id) {
        val source = id ?: start.from ?: return@LaunchedEffect
        val s = runCatching { app.listScripts.execute() }.getOrNull()?.find { it.id == source } ?: return@LaunchedEffect
        name = if (id != null) s.name else "${s.name} (mi versión)"
        author = if (id != null) s.author else ""
        description = s.description ?: ""
        image = s.image
        selected = s.characterIds
    }

    val characters = remember(app, edition, selected) {
        app.catalog.all().filter { edition == null || it.edition == edition || it.id in selected }
    }
    val counts = remember(app, selected) {
        selected.mapNotNull { app.catalog.get(it)?.team }.groupingBy { it }.eachCount()
    }
    val toggle: (String) -> Unit = { cid -> selected = if (cid in selected) selected - cid else selected + cid }

    val save: () -> Unit = {
        saving = true
        scope.launch {
            try {
                app.saveScript.execute(
                    SaveScriptInput(id = id, name = name, author = author, description = description, image = image, characterIds = selected),
                )
                state.toast("Guion guardado.")
                nav.navigate(Route.Scripts)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                state.toast(errorMessage(e), ToastKind.Error)
            } finally {
                saving = false
            }
        }
    }

    val doImport: () -> Unit = {
        try {
            val parsed = app.parseScriptJson.execute(importText)
            selected = parsed.characterIds
            if (parsed.name.isNotEmpty()) name = parsed.name
            if (parsed.author.isNotEmpty()) author = parsed.author
            parsed.logo?.let { image = it }
            importing = false
            if (parsed.unknown.isNotEmpty()) {
                state.toast("Importados ${parsed.characterIds.size}. No reconozco: ${parsed.unknown.joinToString(", ")}.", ToastKind.Error)
            } else {
                state.toast("Importados ${parsed.characterIds.size} personajes.")
            }
        } catch (e: Throwable) {
            state.toast(errorMessage(e), ToastKind.Error)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        LazyColumn(
            Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        ) {
            item(key = "cabecera") {
                FlowRow(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    itemVerticalAlignment = Alignment.Bottom,
                ) {
                    Text(if (id != null) "Editar guion" else "Nuevo guion", Modifier.weight(1f), style = GrimType.title)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GrimButton("Con IA", { aiOpen = true }, kind = ButtonKind.Primary, small = true, icon = Lucide.Sparkles)
                        GrimButton("Revisar", { review.start(name, selected) }, small = true, icon = Lucide.SearchCheck, enabled = selected.size >= 5)
                        GrimButton("JSON", { importing = true }, small = true, icon = Lucide.FileJson)
                    }
                }
            }

            aiNote?.let { note ->
                item(key = "nota-ia") {
                    AlertFrame(GrimAlertKind.Info, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Grim.textStrong)) { append("Propuesta de la IA.") }
                                append(" ${note.explanation}")
                            },
                            style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp),
                        )
                        for (w in note.warnings) {
                            key(w) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Lucide.TriangleAlert, null, tint = Grim.fabled, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                                    Text(w, Modifier.weight(1f), style = GrimType.small.copy(color = Grim.text))
                                }
                            }
                        }
                    }
                }
            }

            item(key = "datos") {
                GrimCard(Modifier.padding(horizontal = 16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GrimTextField(name, { name = it }, label = "Nombre", placeholder = "Ej.: Noche de difuntos")
                        GrimTextField(author, { author = it }, label = "Autor", placeholder = "Opcional")
                        GrimTextField(description, { description = it }, label = "Descripción", placeholder = "Opcional", singleLine = false, minLines = 3)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel("Imagen")
                            image?.let { img ->
                                ScriptBanner("", name.ifEmpty { "Sin nombre" }, img, Modifier.clip(RoundedCornerShape(12.dp)))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (pickImage != null) {
                                    GrimButton(if (image != null) "Cambiar imagen" else "Subir imagen", pickImage, small = true, icon = Lucide.ImagePlus)
                                }
                                if (image != null) {
                                    GrimButton("Quitar", { image = null }, kind = ButtonKind.Ghost, small = true, icon = Lucide.Trash2)
                                }
                            }
                            Muted("Opcional: un logo o ilustración para reconocer el guion. Se reduce antes de guardarla.")
                        }
                    }
                }
            }

            // Recuento y ediciones: se quedan arriba mientras se eligen personajes (como .sticky-bar).
            item(key = "hueco") { Spacer(Modifier.height(16.dp)) }
            stickyHeader(key = "recuento") {
                val shape = RoundedCornerShape(12.dp)
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Color(0xF0120D0F))
                            .border(1.dp, Grim.line, shape)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp)
                            Text("${selected.size} personajes", style = style.copy(fontWeight = FontWeight.Bold, color = Grim.textStrong))
                            Text("${counts[Team.Townsfolk] ?: 0} aldeanos", style = style.copy(color = Team.Townsfolk.color))
                            Text("${counts[Team.Outsider] ?: 0} forasteros", style = style.copy(color = Team.Outsider.color))
                            Text("${counts[Team.Minion] ?: 0} esbirros", style = style.copy(color = Team.Minion.color))
                            Text("${counts[Team.Demon] ?: 0} demonios", style = style.copy(color = Team.Demon.color))
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((value, label) in EDITIONS) {
                                key(label) { Chip(label, pressed = edition == value) { edition = value } }
                            }
                        }
                    }
                }
            }

            item(key = "consejo") {
                Muted(
                    "Consejo: un guion clásico tiene 13 Aldeanos, 4 Forasteros, 4 Esbirros y 4 Demonios.",
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                )
            }

            item(key = "personajes") {
                CharacterPicker(characters, selected, toggle, Modifier.padding(horizontal = 16.dp), searchable = true)
            }
        }

        FooterBar {
            GrimButton("Cancelar", { nav.navigate(Route.Scripts) }, Modifier.heightIn(min = 52.dp), kind = ButtonKind.Ghost)
            GrimButton("Guardar guion", save, Modifier.weight(1f).heightIn(min = 52.dp), kind = ButtonKind.Primary, enabled = !saving)
        }
    }

    if (aiOpen) {
        GrimSheet("Crear guion con IA", onDismiss = { aiOpen = false }) {
            if (!app.aiAvailable) {
                AiNeededNotice()
            } else {
                GrimTextField(
                    idea,
                    { idea = it },
                    label = "Tu idea",
                    placeholder = "Ej.: un pueblo de pescadores donde nadie se fía de nadie, con mucho envenenamiento y un Demonio que se esconde bien",
                    singleLine = false,
                    minLines = 5,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Tamaño")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Clásico · 13 / 4 / 4 / 4", pressed = size == ScriptSize.Classic) { size = ScriptSize.Classic }
                        Chip("Pequeño · 6 / 2 / 2 / 1", pressed = size == ScriptSize.Teensy) { size = ScriptSize.Teensy }
                    }
                    Muted("Aldeanos / Forasteros / Esbirros / Demonios. El pequeño es para 5 o 6 jugadores.")
                }
                GrimButton(
                    if (aiBusy) "Diseñando el guion…" else "Proponer guion",
                    generate,
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 4.dp),
                    kind = ButtonKind.Primary,
                    icon = Lucide.Sparkles,
                    enabled = !aiBusy && idea.isNotBlank(),
                )
            }
        }
    }

    if (review.open) {
        ScriptReviewSheet(
            review,
            name,
            selected,
            // El que entra ocupa el sitio del que sale, para no descolocar la lista.
            onApply = { swap -> selected = selected.map { if (it == swap.removeId) swap.addId else it } },
        )
    }

    if (importing) {
        GrimSheet("Importar guion", onDismiss = { importing = false }) {
            Muted("Pega el JSON de la herramienta oficial (script.bloodontheclocktower.com) o una lista de ids de personajes.")
            CodeArea(importText, { importText = it }, placeholder = """[{"id":"_meta","name":"Mi guion"},"washerwoman","imp"]""")
            GrimButton(
                "Importar",
                doImport,
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 4.dp),
                kind = ButtonKind.Primary,
                enabled = importText.isNotBlank(),
            )
        }
    }
}

/** Área de texto en letra de máquina para pegar el JSON del guion. */
@Composable
private fun CodeArea(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val shape = RoundedCornerShape(8.dp)
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp, color = Grim.text)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        cursorBrush = SolidColor(Grim.gold),
        minLines = 8,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .clip(shape)
            .background(Grim.bg2)
            .border(1.dp, Grim.lineStrong, shape)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = Grim.muted.copy(alpha = 0.7f)))
                inner()
            }
        },
    )
}

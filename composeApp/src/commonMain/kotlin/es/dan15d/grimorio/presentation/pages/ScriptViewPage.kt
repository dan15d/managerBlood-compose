package es.dan15d.grimorio.presentation.pages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.application.usecases.GuideAudience
import es.dan15d.grimorio.application.usecases.scriptCharacters
import es.dan15d.grimorio.application.usecases.scriptToJson
import es.dan15d.grimorio.domain.entities.Character
import es.dan15d.grimorio.domain.services.FIRST_NIGHT_DEMON_INFO
import es.dan15d.grimorio.domain.services.FIRST_NIGHT_MINION_INFO
import es.dan15d.grimorio.domain.services.jinxesAmong
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.app.ToastKind
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.GrimIconButton
import es.dan15d.grimorio.presentation.components.GrimImage
import es.dan15d.grimorio.presentation.components.JinxList
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.NightIcons
import es.dan15d.grimorio.presentation.components.Page
import es.dan15d.grimorio.presentation.components.ScriptCharacterList
import es.dan15d.grimorio.presentation.components.ScriptReviewSheet
import es.dan15d.grimorio.presentation.components.Divider
import es.dan15d.grimorio.presentation.components.Token
import es.dan15d.grimorio.presentation.components.rememberAsync
import es.dan15d.grimorio.presentation.components.rememberScriptReview
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.pages.newgame.newGame
import es.dan15d.grimorio.presentation.pages.scripts.FooterBar
import es.dan15d.grimorio.presentation.pages.scripts.ScriptBanner
import es.dan15d.grimorio.presentation.pages.scripts.newScript
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

private enum class ScriptTab { Characters, First, Other }

/** Un paso de la hoja de noche: un personaje o un paso fijo (crepúsculo, Esbirros, Demonio, amanecer). */
private data class NightRow(
    val order: Int,
    val key: String,
    val title: String,
    val text: String,
    val character: Character? = null,
    val icon: String? = null,
)

/** La hoja de noche del guion entero (útil para preparar la partida o repasar). */
private fun nightSheet(characters: List<Character>, first: Boolean): List<NightRow> {
    val rows = characters
        .filter { (if (first) it.firstNight else it.otherNight) > 0 }
        .map { c ->
            NightRow(
                order = if (first) c.firstNight else c.otherNight,
                key = c.id,
                title = c.name,
                text = (if (first) c.firstNightReminder else c.otherNightReminder).ifEmpty { c.ability },
                character = c,
            )
        }
        .toMutableList()
    rows += NightRow(0, "dusk", "Crepúsculo", "Todos cierran los ojos.", icon = NightIcons.dusk)
    if (first) {
        rows += NightRow(FIRST_NIGHT_MINION_INFO, "minion_info", "Información de Esbirros", "Con 7 o más jugadores: los Esbirros se reconocen y ven al Demonio.", icon = NightIcons.minionInfo)
        rows += NightRow(FIRST_NIGHT_DEMON_INFO, "demon_info", "Información del Demonio", "Con 7 o más jugadores: el Demonio ve a sus Esbirros y recibe 3 faroles.", icon = NightIcons.demonInfo)
    }
    rows += NightRow(Int.MAX_VALUE, "dawn", "Amanecer", "Todos abren los ojos; anuncia quién ha muerto.", icon = NightIcons.dawn)
    return rows.sortedBy { it.order }
}

/** Ficha de un guion: sus personajes, la hoja de noche y los botones para editarlo o jugarlo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScriptViewPage(id: String) {
    val state = LocalAppState.current
    val app = state.app!!
    val nav = LocalNavigator.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var tab by remember { mutableStateOf(ScriptTab.Characters) }
    var fullText by remember { mutableStateOf(false) }
    val review = rememberScriptReview()
    val scripts = rememberAsync(app) { app.listScripts.execute() }
    val script = remember(scripts.data, id) { scripts.data?.find { it.id == id } }
    val characters = remember(app, script) { if (script != null) scriptCharacters(app.catalog, script) else emptyList() }
    val jinxes = remember(app, script) { jinxesAmong(script?.characterIds.orEmpty(), app.catalog.jinxes()) }
    val characterMap = remember(app) { app.catalog.map() }
    val night = remember(characters, tab) { if (tab == ScriptTab.Characters) emptyList() else nightSheet(characters, tab == ScriptTab.First) }

    if (scripts.loading && scripts.data == null) {
        Page(flow = true) { Muted("Cargando…") }
        return
    }
    if (script == null) {
        Page(flow = true) {
            Muted("No encuentro ese guion.")
            GrimButton("Volver a los guiones", { nav.navigate(Route.Scripts) }, kind = ButtonKind.Ghost, icon = Lucide.ArrowLeft)
        }
        return
    }

    val copyJson = {
        try {
            clipboard.setText(AnnotatedString(scriptToJson(script)))
            state.toast("JSON copiado.")
        } catch (_: Throwable) {
            state.toast("No he podido copiar al portapapeles.", ToastKind.Error)
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        LazyColumn(
            Modifier.weight(1f).widthIn(max = 760.dp).fillMaxWidth(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        ) {
            item(key = "barra") {
                // Como .game-bar: volver a la izquierda y las acciones a la derecha.
                BoxWithConstraints(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                    val wide = maxWidth >= 480.dp
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GrimIconButton(Lucide.ArrowLeft, "Volver a los guiones", { nav.navigate(Route.Scripts) })
                        Spacer(Modifier.weight(1f))
                        if (wide) {
                            GrimButton("JSON", copyJson, kind = ButtonKind.Ghost, small = true, icon = Lucide.Copy)
                        } else {
                            GrimIconButton(Lucide.Copy, "Copiar JSON", copyJson, size = 40.dp)
                        }
                        GrimButton("Revisar", { review.start(script.name, script.characterIds) }, kind = ButtonKind.Ghost, small = true, icon = Lucide.Sparkles)
                        GrimButton(
                            if (script.official) "Duplicar" else "Editar",
                            { if (script.official) nav.newScript(from = script.id) else nav.navigate(Route.ScriptEditor(script.id)) },
                            small = true,
                            icon = Lucide.Pencil,
                        )
                    }
                }
            }

            item(key = "ficha") {
                GrimCard(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), padding = PaddingValues(0.dp)) {
                    ScriptBanner(script.id, script.name, script.image)
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                        Text(script.name, style = GrimType.title, modifier = Modifier.padding(bottom = 4.dp))
                        if (script.author.isNotEmpty()) Text(script.author, style = GrimType.small)
                        val description = script.description
                        if (!description.isNullOrEmpty()) {
                            Text(
                                description,
                                Modifier.padding(top = 10.dp),
                                style = GrimType.body,
                                maxLines = if (fullText) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (description.length > 180) {
                                Text(
                                    (if (fullText) "Leer menos" else "Leer más").uppercase(),
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(role = Role.Button) { fullText = !fullText }
                                        .padding(vertical = 8.dp),
                                    style = TextStyle(fontFamily = GrimType.fonts.ui, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.1.sp, color = Grim.text),
                                )
                            }
                        }
                    }
                }
            }

            stickyHeader(key = "pestañas") {
                PageTabs(tab) { tab = it }
            }

            if (tab == ScriptTab.Characters) {
                item(key = "personajes") {
                    ScriptCharacterList(characters, GuideAudience.Storyteller, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
                }
                if (jinxes.isNotEmpty()) {
                    item(key = "incompatibilidades") {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Incompatibilidades", style = GrimType.heading.copy(color = Grim.fabled, fontSize = 19.sp))
                                Text("${jinxes.size}", style = GrimType.label.copy(fontWeight = FontWeight.Bold))
                            }
                            Divider()
                            Muted("Reglas especiales cuando estos personajes están juntos. Al jugar, el Djinn las anuncia a la mesa.")
                            JinxList(jinxes, characterMap)
                        }
                    }
                }
            } else {
                item(key = "hueco") { Spacer(Modifier.height(16.dp)) }
                items(night, key = { "${tab.name}-${it.key}" }) { row ->
                    NightSheetRow(row, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp))
                }
            }
        }

        FooterBar(maxWidth = 760.dp) {
            GrimButton(
                "Nueva partida con este guion",
                { nav.newGame(scriptId = script.id) },
                Modifier.weight(1f).heightIn(min = 52.dp),
                kind = ButtonKind.Primary,
                icon = Lucide.Play,
                big = true,
            )
        }
    }

    if (review.open) ScriptReviewSheet(review, script.name, script.characterIds)
}

/** Pestañas a todo lo ancho que se quedan arriba al hacer scroll (como .tabs--page). */
@Composable
private fun PageTabs(selected: ScriptTab, onSelect: (ScriptTab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Grim.surface2)) {
        PageTab(Lucide.ScrollText, "Personajes", selected == ScriptTab.Characters) { onSelect(ScriptTab.Characters) }
        PageTab(Lucide.Moon, "1.ª noche", selected == ScriptTab.First) { onSelect(ScriptTab.First) }
        PageTab(Lucide.Sunrise, "Otras noches", selected == ScriptTab.Other) { onSelect(ScriptTab.Other) }
    }
    Divider()
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.PageTab(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Grim.textStrong else Grim.muted
    Column(
        Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Tab, onClick = onClick),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Text(
                label.uppercase(),
                style = TextStyle(fontFamily = GrimType.fonts.ui, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.sp, color = color),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) Grim.goldBright else Color.Transparent))
    }
}

/** Una línea de la hoja de noche (como .night-sheet li). */
@Composable
private fun NightSheetRow(row: NightRow, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Grim.surface.copy(alpha = 0.75f))
            .border(1.dp, Grim.line, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (row.character != null) {
            Token(row.character, size = 40.dp)
        } else {
            GrimImage(row.icon, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f)) {
            Text(row.title, style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Grim.textStrong))
            Text(row.text, style = GrimType.small.copy(fontSize = 14.sp, lineHeight = 19.sp))
        }
    }
}

package es.dan15d.grimorio.presentation.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.application.ports.CharacterCatalog
import es.dan15d.grimorio.application.usecases.scriptToJson
import es.dan15d.grimorio.domain.entities.Script
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.entities.label
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.app.ToastKind
import es.dan15d.grimorio.presentation.app.errorMessage
import es.dan15d.grimorio.presentation.components.Badge
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.ConfirmDialog
import es.dan15d.grimorio.presentation.components.GrimAlert
import es.dan15d.grimorio.presentation.components.GrimAlertKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.rememberAsync
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.pages.scripts.ScriptBanner
import es.dan15d.grimorio.presentation.pages.scripts.newScript
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

/** Lista de guiones: los oficiales y los del Narrador, para verlos, editarlos o crear uno nuevo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScriptsPage() {
    val state = LocalAppState.current
    val app = state.app!!
    val nav = LocalNavigator.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val scripts = rememberAsync(app) { app.listScripts.execute() }
    var deleting by remember { mutableStateOf<Script?>(null) }

    val exportScript = { s: Script ->
        try {
            clipboard.setText(AnnotatedString(scriptToJson(s)))
            state.toast("JSON copiado. Puedes pegarlo en la herramienta oficial de guiones.")
        } catch (_: Throwable) {
            state.toast("No he podido copiar al portapapeles.", ToastKind.Error)
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // Una columna en el móvil; en pantallas anchas, tantas de 260 como quepan (como .grid).
        LazyVerticalGrid(
            columns = GridCells.Adaptive(260.dp),
            modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "cabecera", span = { GridItemSpan(maxLineSpan) }) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    itemVerticalAlignment = Alignment.Bottom,
                ) {
                    Text("Guiones", Modifier.weight(1f), style = GrimType.title)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GrimButton("Con IA", { nav.newScript(withAi = true) }, kind = ButtonKind.Primary, icon = Lucide.Sparkles)
                        GrimButton("Crear guion", { nav.newScript() })
                    }
                }
            }
            scripts.error?.let { error ->
                item(key = "error", span = { GridItemSpan(maxLineSpan) }) { GrimAlert(error, GrimAlertKind.Error) }
            }
            items(scripts.data.orEmpty(), key = { it.id }) { s ->
                ScriptCard(
                    s,
                    app.catalog,
                    onView = { nav.navigate(Route.ScriptView(s.id)) },
                    onDuplicate = { nav.newScript(from = s.id) },
                    onEdit = { nav.navigate(Route.ScriptEditor(s.id)) },
                    onDelete = { deleting = s },
                    onExport = { exportScript(s) },
                )
            }
        }
    }

    deleting?.let { s ->
        ConfirmDialog(
            title = "¿Borrar el guion?",
            message = "Se borrará «${s.name}». Las partidas ya creadas con él no cambian.",
            confirmLabel = "Borrar",
            danger = true,
            onCancel = { deleting = null },
            onConfirm = {
                deleting = null
                state.run {
                    try {
                        app.deleteScript.execute(s.id)
                        state.toast("Guion borrado.")
                        scripts.reload()
                    } catch (e: Throwable) {
                        state.toast(errorMessage(e), ToastKind.Error)
                    }
                }
            },
        )
    }
}

/** «13 aldeanos · 4 forasteros · 4 esbirros · 4 demonios». */
private fun teamCounts(catalog: CharacterCatalog, s: Script): String {
    val byTeam = s.characterIds.mapNotNull { catalog.get(it)?.team }.groupingBy { it }.eachCount()
    return listOf(Team.Townsfolk, Team.Outsider, Team.Minion, Team.Demon)
        .joinToString(" · ") { "${byTeam[it] ?: 0} ${it.label.plural.lowercase()}" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScriptCard(
    s: Script,
    catalog: CharacterCatalog,
    onView: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    val counts = remember(s, catalog) { teamCounts(catalog, s) }
    GrimCard(padding = PaddingValues(0.dp)) {
        ScriptBanner(s.id, s.name, s.image, Modifier.clickable(onClickLabel = "Ver ${s.name}", onClick = onView))
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    s.name,
                    Modifier.weight(1f),
                    style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Grim.textStrong),
                )
                if (s.official) Badge("Oficial", Grim.fabled)
            }
            if (s.author.isNotEmpty()) Text(s.author, style = GrimType.small)
            Text(counts, style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp))
            s.description?.takeIf { it.isNotEmpty() }?.let { d ->
                Text(if (d.length > 160) d.take(160) + "…" else d, style = GrimType.small)
            }
            FlowRow(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GrimButton("Ver", onView, kind = ButtonKind.Primary, small = true)
                if (s.official) {
                    GrimButton("Duplicar y editar", onDuplicate, small = true)
                } else {
                    GrimButton("Editar", onEdit, small = true)
                    GrimButton("Borrar", onDelete, kind = ButtonKind.Ghost, small = true)
                }
                GrimButton("Copiar JSON", onExport, kind = ButtonKind.Ghost, small = true)
            }
        }
    }
}

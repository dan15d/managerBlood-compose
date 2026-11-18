package es.dan15d.grimorio.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.application.ports.AiScriptSwap
import es.dan15d.grimorio.application.usecases.ScriptReview
import es.dan15d.grimorio.presentation.app.Container
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.app.errorMessage
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.pages.scripts.AiNeededNotice
import es.dan15d.grimorio.presentation.pages.scripts.AlertFrame
import es.dan15d.grimorio.presentation.pages.scripts.GroupTitle
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Revisión de un guion con IA. La revisión vive en la página, no en la hoja: al cerrarla y volver a
 * abrirla se enseña la misma, y solo se gasta otra consulta si el Narrador pide revisar otra vez.
 */
@Stable
class ScriptReviewState internal constructor(private val app: Container, private val scope: CoroutineScope) {
    var open by mutableStateOf(false)
        private set
    var review by mutableStateOf<ScriptReview?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set

    fun run(name: String, characterIds: List<String>) {
        loading = true
        error = ""
        scope.launch {
            try {
                review = app.reviewAiScript.execute(name, characterIds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = errorMessage(e)
            } finally {
                loading = false
            }
        }
    }

    /** Abre la hoja; la primera vez (o si la anterior falló) lanza la revisión. */
    fun start(name: String, characterIds: List<String>) {
        open = true
        if (app.aiAvailable && review == null && !loading) run(name, characterIds)
    }

    fun close() {
        open = false
    }
}

/** Equivale a useScriptReview de la web. */
@Composable
fun rememberScriptReview(): ScriptReviewState {
    val app = LocalAppState.current.app!!
    val scope = rememberCoroutineScope()
    return remember(app, scope) { ScriptReviewState(app, scope) }
}

/**
 * Hoja con la revisión. Si se pasa [onApply], cada cambio propuesto lleva un botón para aplicarlo
 * al guion que se está editando.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScriptReviewSheet(
    state: ScriptReviewState,
    name: String,
    characterIds: List<String>,
    onApply: ((AiScriptSwap) -> Unit)? = null,
) {
    val app = LocalAppState.current.app!!
    val review = state.review
    val loading = state.loading
    val inScript = remember(characterIds) { characterIds.toSet() }
    fun isApplied(swap: AiScriptSwap) = swap.removeId !in inScript && swap.addId in inScript
    // Aplicar los cambios propuestos no cuenta como «el guion ha cambiado»: solo lo que se toque aparte.
    val changed = remember(review, inScript, characterIds.size) {
        if (review == null) {
            false
        } else {
            val expected = review.reviewedIds.map { id -> review.swaps.find { it.removeId == id && isApplied(it) }?.addId ?: id }
            expected.size != characterIds.size || expected.any { it !in inScript }
        }
    }

    GrimSheet("Revisión del guion", onDismiss = state::close) {
        if (!app.aiAvailable) {
            AiNeededNotice()
            return@GrimSheet
        }
        if (loading) Muted("Revisando el guion…")
        if (state.error.isNotEmpty() && !loading) GrimAlert(state.error, GrimAlertKind.Error)

        if (review != null && !loading) {
            if (changed) {
                AlertFrame(GrimAlertKind.Warning) {
                    Text("El guion ha cambiado desde esta revisión.", style = GrimType.small.copy(color = Grim.text))
                }
            }
            if (review.verdict.isNotEmpty()) Text(review.verdict, style = GrimType.body)

            if (review.strengths.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GroupTitle("Lo que funciona")
                    BulletList(review.strengths)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GroupTitle("A vigilar")
                if (review.problems.isNotEmpty()) BulletList(review.problems) else Muted("No ve problemas serios.")
            }

            if (review.swaps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GroupTitle("Cambios que propone")
                    for (swap in review.swaps) {
                        val out = app.catalog.get(swap.removeId) ?: continue
                        val into = app.catalog.get(swap.addId) ?: continue
                        val applied = isApplied(swap)
                        val possible = swap.removeId in inScript && swap.addId !in inScript
                        key(swap.removeId, swap.addId) {
                            val shape = RoundedCornerShape(8.dp)
                            Column(
                                Modifier.fillMaxWidth().border(1.dp, Grim.line, shape).padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    itemVerticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val pairStyle = TextStyle(fontFamily = GrimType.fonts.display, fontSize = 16.sp)
                                    Token(out, size = 36.dp)
                                    Text(out.name, style = pairStyle.copy(color = out.team.color))
                                    Icon(Lucide.ArrowRight, "se cambia por", tint = Grim.muted, modifier = Modifier.size(18.dp))
                                    Token(into, size = 36.dp)
                                    Text(into.name, style = pairStyle.copy(color = into.team.color))
                                }
                                Text(swap.reason, style = GrimType.small)
                                Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Grim.textStrong)) { append("${into.name}:") }
                                        append(" ${into.ability}")
                                    },
                                    style = GrimType.small.copy(color = Grim.text),
                                )
                                if (onApply != null) {
                                    if (applied) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Lucide.Check, null, tint = Grim.muted, modifier = Modifier.size(16.dp))
                                            Muted("Aplicado")
                                        }
                                    } else if (possible) {
                                        GrimButton("Aplicar el cambio", { onApply(swap) }, small = true)
                                    }
                                }
                            }
                        }
                    }
                    if (onApply == null) Muted("Para aplicarlos, abre el guion en el editor y revísalo desde allí.")
                }
            }
        }

        if (!loading) {
            GrimButton(
                if (review != null) "Revisar otra vez" else "Reintentar",
                { state.run(name, characterIds) },
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 4.dp),
                icon = Lucide.RefreshCw,
            )
        }
    }
}

/** Lista con viñetas (como .review-list). */
@Composable
private fun BulletList(items: List<String>) {
    Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (item in items) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp))
                Text(item, Modifier.weight(1f), style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp))
            }
        }
    }
}

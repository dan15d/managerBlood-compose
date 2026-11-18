package es.dan15d.grimorio.presentation.pages.newgame

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.application.ports.SetupStyle
import es.dan15d.grimorio.domain.entities.GameMode
import es.dan15d.grimorio.domain.entities.Team
import es.dan15d.grimorio.domain.entities.isStorytellerTeam
import es.dan15d.grimorio.domain.entities.label
import es.dan15d.grimorio.domain.services.DJINN
import es.dan15d.grimorio.domain.services.SetupIssueLevel
import es.dan15d.grimorio.domain.services.jinxesAmong
import es.dan15d.grimorio.presentation.components.ButtonKind
import es.dan15d.grimorio.presentation.components.CharacterPicker
import es.dan15d.grimorio.presentation.components.Chip
import es.dan15d.grimorio.presentation.components.DistributionBar
import es.dan15d.grimorio.presentation.components.GrimAlert
import es.dan15d.grimorio.presentation.components.GrimAlertKind
import es.dan15d.grimorio.presentation.components.GrimButton
import es.dan15d.grimorio.presentation.components.GrimCard
import es.dan15d.grimorio.presentation.components.GrimOption
import es.dan15d.grimorio.presentation.components.GrimSelect
import es.dan15d.grimorio.presentation.components.GrimTextField
import es.dan15d.grimorio.presentation.components.JinxList
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.SectionLabel
import es.dan15d.grimorio.presentation.components.color
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType
import kotlinx.coroutines.launch

private data class StyleOption(val style: SetupStyle, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val STYLES = listOf(
    StyleOption(SetupStyle.Equilibrada, "Equilibrada", Lucide.Scale),
    StyleOption(SetupStyle.Principiantes, "Para principiantes", Lucide.Sprout),
    StyleOption(SetupStyle.MuchaInfo, "Mucha información", Lucide.Search),
    StyleOption(SetupStyle.Caotica, "Caótica", Lucide.Tornado),
    StyleOption(SetupStyle.Maligna, "Ventaja al Mal", Lucide.Skull),
)

private val BAG_TEAMS = listOf(Team.Townsfolk, Team.Outsider, Team.Minion, Team.Demon)

/**
 * Paso 4: Viajeros, Fábulas, la bolsa (a mano, al azar o con la IA), los engaños y los faroles.
 * Es una lista perezosa para que la barra del reparto se quede pegada arriba al bajar por los
 * personajes (como .sticky-bar).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CharactersStep(state: NewGameState, header: @Composable () -> Unit) {
    val script = state.script ?: return
    val scope = rememberCoroutineScope()
    val manual = state.mode == GameMode.Manual || state.editingAi
    val ai = state.mode == GameMode.Ai && !state.editingAi

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") { header() }
            item(key = "title") { StepTitle("Personajes") }
            item(key = "travellers") { TravellersCard(state) }
            item(key = "fabled") { FabledCard(state, script.characterIds) }

            if (ai) {
                item(key = "ai-request") { AiRequestCard(state) { scope.launch { state.askAi() } } }
                if (state.aiNotes != null) item(key = "ai-proposal") { AiProposalCard(state) }
            }

            if (manual) {
                stickyHeader(key = "bar") { BagBar(state) }
                item(key = "picker") {
                    CharacterPicker(
                        characters = state.pool,
                        selected = state.selected,
                        onToggle = state::toggleCharacter,
                        target = state.target,
                        teams = BAG_TEAMS,
                        repeatable = state::repeatable,
                        onAdd = state::addCopy,
                    )
                }
            }

            if (state.selected.isNotEmpty()) issuesAndExtras(state)
        }
    }
}

private fun LazyListScope.issuesAndExtras(state: NewGameState) {
    items(state.issues) { issue ->
        GrimAlert(issue.message, if (issue.level == SetupIssueLevel.Error) GrimAlertKind.Error else GrimAlertKind.Warning)
    }
    if (state.jinxes.isNotEmpty()) {
        item(key = "jinxes") {
            GrimCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardTitle("Incompatibilidades en juego")
                    Muted("Reglas especiales de la wiki para estos personajes juntos." + if (DJINN in state.fabled) " El Djinn las anuncia a la mesa." else "")
                    JinxList(state.jinxes, state.app.catalog.map())
                }
            }
        }
    }
    item(key = "decoys") { DecoyAndBluffs(state) }
}

@Composable
private fun TravellersCard(state: NewGameState) {
    val forced = state.forcedTravellers
    Disclosure(
        title = Team.Traveller.label.plural,
        titleColor = Team.Traveller.color,
        hint = if (forced > 0) "al menos $forced" else "opcional",
        count = state.travellers.size,
        initiallyOpen = forced > 0 || state.travellers.isNotEmpty(),
    ) {
        Muted(
            (if (forced > 0) "Con más de 15 jugadores, al menos $forced juegan como Viajeros. " else "Para quien llega tarde o se va antes. ") +
                "No salen de la bolsa: cada Viajero es un jugador menos en ella (${state.bagCount} en la bolsa). En el último paso eliges " +
                "quién es cada uno; su bando lo decides tú desde su ficha en el Grimorio.",
        )
        CharacterPicker(
            characters = state.travellerPool,
            selected = state.travellers,
            onToggle = state::toggleTraveller,
            teams = listOf(Team.Traveller),
        )
    }
}

@Composable
private fun FabledCard(state: NewGameState, scriptCharacterIds: List<String>) {
    val text = remember(state.pool, scriptCharacterIds) {
        buildString {
            append("Personajes del Narrador: no son jugadores ni van en la bolsa, y se anuncian a toda la mesa.")
            if (state.pool.any { isStorytellerTeam(it.team) }) append(" Ya vienen marcados los del guion.")
            if (jinxesAmong(scriptCharacterIds, state.app.catalog.jinxes()).isNotEmpty()) {
                append(" El Djinn también, porque el guion tiene incompatibilidades.")
            }
            append(" Algunos cambian la preparación (Centinela, Papa) o la noche (Juguetero, Tor). También los puedes poner o quitar durante la partida.")
        }
    }
    Disclosure(
        title = "${Team.Fabled.label.plural} y ${Team.Loric.label.plural}",
        titleColor = Team.Fabled.color,
        hint = "opcional",
        count = state.fabled.size,
        initiallyOpen = state.fabled.isNotEmpty(),
    ) {
        Muted(text)
        CharacterPicker(
            characters = state.fabledPool,
            selected = state.fabled,
            onToggle = state::toggleFabled,
            teams = listOf(Team.Fabled, Team.Loric),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiRequestCard(state: NewGameState, onAsk: () -> Unit) {
    GrimCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardTitle("¿Qué partida quieres?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in STYLES) {
                    Chip(s.label, pressed = state.style == s.style, icon = s.icon) { state.style = s.style }
                }
            }
            GrimTextField(
                state.extra,
                { state.extra = it },
                placeholder = "Indicaciones opcionales: «que salga el Barón», «sin Borracho», «es la primera partida de Ana»…",
                singleLine = false,
                minLines = 3,
            )
            GrimButton(
                when {
                    state.aiLoading -> "La IA está pensando…"
                    state.selected.isNotEmpty() -> "Otra propuesta"
                    else -> "Preparar reparto con IA"
                },
                onAsk,
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Primary,
                icon = Lucide.Sparkles,
                enabled = !state.aiLoading,
                big = true,
            )
        }
    }
}

@Composable
private fun AiProposalCard(state: NewGameState) {
    val notes = state.aiNotes ?: return
    GrimCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardTitle("La propuesta")
            BagPreview(state.chosen)
            notes.explanation?.takeIf { it.isNotEmpty() }?.let { Text(it, style = GrimType.body) }
            if (notes.tips.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (tip in notes.tips) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", style = GrimType.body.copy(color = Grim.goldBright))
                            Text(tip, Modifier.weight(1f), style = GrimType.body)
                        }
                    }
                }
            }
            GrimButton("Retocar a mano", { state.editingAi = true }, icon = Lucide.Pencil, small = true)
        }
    }
}

/** Barra pegada arriba con el reparto que falta, «Al azar», «Vaciar» y el contador de la bolsa. */
@Composable
private fun BagBar(state: NewGameState) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Grim.bg.copy(alpha = 0.96f))
            .border(1.dp, Grim.line, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DistributionBar(state.target)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GrimButton("Al azar", state::randomize, kind = ButtonKind.Gold, icon = Lucide.Dices, small = true)
            GrimButton("Vaciar", state::resetSelection, kind = ButtonKind.Ghost, small = true, enabled = state.selected.isNotEmpty())
            Spacer(Modifier.weight(1f))
            val full = state.dealtCount == state.bagCount
            Text(
                "${state.dealtCount}/${state.bagCount}",
                style = GrimType.label.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (full) Grim.loric else Grim.text),
            )
        }
    }
}

/** Lo que cree ser el Borracho (y compañía) y los tres faroles del Demonio. */
@Composable
private fun DecoyAndBluffs(state: NewGameState) {
    val pool = state.pool
    val decoys = state.decoys
    val bluffs = state.bluffs
    val taken = remember(state.selected, decoys) { (state.selected + decoys.values).toSet() }
    val good = remember(pool, taken) {
        pool.filter { (it.team == Team.Townsfolk || it.team == Team.Outsider) && it.id !in taken }
    }
    fun name(id: String) = pool.find { it.id == id }?.name ?: id

    GrimCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            for ((id, shown) in decoys) {
                androidx.compose.runtime.key(id) {
                    val team = pool.find { it.id == shown }?.team
                    val options = pool.filter { it.team == team && (it.id == shown || it.id !in taken) }.map { GrimOption(it.id, it.name) }
                    GrimSelect(
                        shown,
                        options,
                        onSelect = { state.decoys = decoys + (id to it) },
                        labelContent = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Lucide.VenetianMask, null, tint = Grim.muted, modifier = Modifier.size(16.dp))
                                SectionLabel("${name(id)} cree ser")
                            }
                        },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Faroles del Demonio (no están en juego)")
                for (i in 0 until 3) {
                    androidx.compose.runtime.key(i) {
                        val current = bluffs.getOrNull(i)
                        val options = listOfNotNull(current?.let { GrimOption(it, name(it)) }) +
                            good.filter { it.id !in bluffs }.map { GrimOption(it.id, it.name) }
                        GrimSelect(current, options, onSelect = { value ->
                            val copy = MutableList(3) { bluffs.getOrNull(it) ?: "" }
                            copy[i] = value
                            state.bluffs = copy.filter { it.isNotEmpty() }
                        })
                    }
                }
            }
        }
    }
}

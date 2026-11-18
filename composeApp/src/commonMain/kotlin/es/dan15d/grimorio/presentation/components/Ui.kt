package es.dan15d.grimorio.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import es.dan15d.grimorio.presentation.app.AppState
import es.dan15d.grimorio.presentation.app.ToastKind
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.navigation.BackHandlerLayer
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType
import es.dan15d.grimorio.resources.Res
import kotlinx.coroutines.launch

// Piezas comunes de la interfaz: equivalen a las clases .btn, .card, .field, .sheet… de app.css.

// ---------- Botones ----------

enum class ButtonKind { Default, Primary, Ghost, Danger, Gold }

/** Botón con forma de píldora y texto en mayúsculas (como .btn). El principal es morado. */
@Composable
fun GrimButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Default,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    small: Boolean = false,
    big: Boolean = false,
) {
    val shape = RoundedCornerShape(999.dp)
    val (background, border, color) = when (kind) {
        ButtonKind.Primary -> Triple<Brush, Color, Color>(Brush.verticalGradient(listOf(Color(0xFF7F50C2), Grim.accentDeep)), Color(0xFF9A72D8), Grim.textStrong)
        ButtonKind.Danger -> Triple(SolidColor(Grim.blood.copy(alpha = 0.18f)), Grim.bloodBright.copy(alpha = 0.6f), Color(0xFFFFD9D9))
        ButtonKind.Gold -> Triple(Brush.verticalGradient(listOf(Grim.goldBright, Grim.gold)), Grim.goldBright, Grim.ink)
        ButtonKind.Ghost -> Triple(SolidColor(Color.Transparent), Color.Transparent, Grim.text)
        ButtonKind.Default -> Triple(SolidColor(Grim.surface2), Grim.lineStrong, Grim.text)
    }
    val minHeight = if (small) 34.dp else if (big) 56.dp else 44.dp
    Row(
        modifier
            .heightIn(min = minHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (kind == ButtonKind.Primary && enabled) Modifier.shadow(8.dp, shape, ambientColor = Grim.accentDeep, spotColor = Grim.accentDeep) else Modifier)
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 18.dp, vertical = if (small) 4.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(if (small) 16.dp else 20.dp))
        Text(
            text.uppercase(),
            style = TextStyle(
                fontFamily = GrimType.fonts.ui,
                fontWeight = FontWeight.Bold,
                fontSize = if (small) 12.sp else if (big) 15.sp else 14.sp,
                letterSpacing = 1.1.sp,
                color = color,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Botón redondo solo con icono (como .icon-btn); [label] es el nombre accesible. */
@Composable
fun GrimIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Grim.text,
    enabled: Boolean = true,
    bordered: Boolean = false,
    size: Dp = 44.dp,
) {
    Box(
        modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .then(if (bordered) Modifier.background(Grim.surface2).border(1.dp, Grim.lineStrong, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

// ---------- Tarjetas y páginas ----------

private val cardShape = RoundedCornerShape(12.dp)

/** Tarjeta con el degradado de superficie (como .card). Con [onClick] es pulsable. */
@Composable
fun GrimCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Brush.verticalGradient(listOf(Grim.surface2, Grim.surface)))
            .border(if (selected) 2.dp else 1.dp, if (selected) Grim.goldBright else Grim.line, cardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/**
 * Página con scroll, centrada y con el margen de 16 de la web. [flow] quita el hueco de la barra
 * inferior (pantallas de flujo).
 */
@Composable
fun Page(
    modifier: Modifier = Modifier,
    flow: Boolean = false,
    maxWidth: Dp = 720.dp,
    spacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (flow) 24.dp else 96.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** Cabecera de página: título grande y acciones a la derecha. */
@Composable
fun PageHeader(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = GrimType.title)
            if (subtitle != null) Text(subtitle, style = GrimType.small)
        }
        actions()
    }
}

/** Texto apagado para estados vacíos y «Cargando…». */
@Composable
fun Muted(text: String, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(text, modifier, style = GrimType.small, textAlign = if (center) TextAlign.Center else null)
}

/** Título de sección en mayúsculas pequeñas. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = GrimType.caps)
}

// ---------- Campos ----------

/** Campo de texto con su etiqueta encima (como .field + .input). */
@Composable
fun GrimTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: Boolean = false,
    code: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val textStyle = if (code) {
        TextStyle(fontFamily = GrimType.fonts.ui, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp, textAlign = TextAlign.Center, color = Grim.text)
    } else {
        GrimType.body
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) SectionLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle,
            cursorBrush = SolidColor(Grim.gold),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onGo = { onDone?.invoke() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChangedCompat { focused = it }
                .clip(shape)
                .background(Grim.bg2)
                .border(1.dp, if (focused) Grim.gold else Grim.lineStrong, shape)
                .heightIn(min = 44.dp)
                .padding(horizontal = 13.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Box(contentAlignment = if (code) Alignment.Center else Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle.copy(color = Grim.muted.copy(alpha = 0.7f)))
                    }
                    inner()
                }
            },
        )
    }
}

private fun Modifier.onFocusChangedCompat(onChange: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { onChange(it.isFocused) }

// ---------- Hojas y diálogos ----------

/**
 * Hoja que sube desde abajo (como Sheet): se cierra deslizando, tocando fuera, con la ✕ o con
 * «atrás». En pantallas anchas se queda centrada a 640 de ancho.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrimSheet(
    title: String,
    onDismiss: () -> Unit,
    titleContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
    }
    BackHandlerLayer(close)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Grim.surface,
        contentColor = Grim.text,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        sheetMaxWidth = 640.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(Modifier.padding(top = 6.dp, bottom = 2.dp).size(width = 44.dp, height = 5.dp).clip(CircleShape).background(Grim.lineStrong))
        },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (titleContent != null) {
                titleContent()
            } else {
                Text(
                    title,
                    Modifier.weight(1f),
                    style = GrimType.title.copy(fontSize = 22.sp, lineHeight = 26.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GrimIconButton(Lucide.X, "Cerrar", close, bordered = true)
        }
        Box(Modifier.fillMaxWidth().background(Grim.line).heightIn(min = 1.dp, max = 1.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Confirmación sencilla para acciones que no se pueden deshacer. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    danger: Boolean = false,
) {
    BackHandlerLayer(onCancel)
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Grim.surface,
        shape = RoundedCornerShape(12.dp),
        title = { Text(title, style = GrimType.heading.copy(color = Grim.textStrong)) },
        text = { Text(message, style = GrimType.small) },
        confirmButton = { GrimButton(confirmLabel, onConfirm, kind = if (danger) ButtonKind.Danger else ButtonKind.Primary) },
        dismissButton = { GrimButton("Cancelar", onCancel, kind = ButtonKind.Ghost) },
    )
}

// ---------- Menú «…» ----------

data class MenuItem(
    val label: String,
    val onSelect: () -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    /** Acción que se queda activa (cambiar sitios): se marca mientras lo está. */
    val pressed: Boolean = false,
    /** Lo peligroso (terminar la partida) va al final y en otro color. */
    val danger: Boolean = false,
)

/** Menú «…» con las acciones que no caben en la barra. */
@Composable
fun GrimMenu(label: String, items: List<MenuItem>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        GrimIconButton(Lucide.Ellipsis, label, { open = !open })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Grim.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Grim.lineStrong),
        ) {
            for (item in items) {
                val color = if (item.danger) Grim.bloodBright else if (item.pressed) Grim.textStrong else Grim.text
                DropdownMenuItem(
                    text = { Text(item.label, style = GrimType.body.copy(color = color)) },
                    leadingIcon = item.icon?.let { icon -> { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) } },
                    enabled = item.enabled,
                    modifier = if (item.pressed) Modifier.background(Grim.surface2) else Modifier,
                    onClick = {
                        open = false
                        item.onSelect()
                    },
                )
            }
        }
    }
}

// ---------- Avisos ----------

/** Avisos abajo de la pantalla (como .toasts): los de error con borde rojo. */
@Composable
fun ToastHost(state: AppState, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (toast in state.toasts) {
            val shape = RoundedCornerShape(8.dp)
            Row(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .shadow(10.dp, shape)
                    .clip(shape)
                    .background(Grim.surface3)
                    .border(1.dp, if (toast.kind == ToastKind.Error) Grim.bloodBright else Grim.lineStrong, shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.dismissToast(toast.id) }
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(toast.message, Modifier.weight(1f), style = GrimType.body.copy(fontSize = 16.sp))
                toast.action?.let { action ->
                    GrimButton(action.label, {
                        state.dismissToast(toast.id)
                        action.onClick()
                    }, kind = ButtonKind.Ghost, small = true)
                }
            }
        }
    }
}

// ---------- Imágenes ----------

/**
 * URI de una imagen de la app: las rutas relativas ("roles/mayor.webp", "img/dusk.webp") están en
 * los recursos de Compose; las que empiezan por http o data: se usan tal cual.
 */
fun imageUri(path: String): String =
    if (path.startsWith("http") || path.startsWith("data:")) path else Res.getUri("files/" + path.removePrefix("/"))

/** Imagen de los recursos o de internet, cargada con Coil (en la web se baja cuando hace falta). */
@Composable
fun GrimImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    if (path.isNullOrEmpty()) {
        Spacer(modifier)
        return
    }
    AsyncImage(model = imageUri(path), contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
}

/** Separador fino de 1 dp. */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(Grim.line))
}

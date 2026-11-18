package es.dan15d.grimorio.presentation.pages.scripts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.presentation.components.EditionBanner
import es.dan15d.grimorio.presentation.components.GrimAlertKind
import es.dan15d.grimorio.presentation.components.editionLogo
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Navigator
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType

// Piezas que comparten las pantallas de guiones (lista, ficha, editor y la revisión con IA).

/**
 * Lo que en la web iba en la URL del editor: «/guiones/nuevo?desde=id» (duplicar un guion
 * oficial) y «/guiones/nuevo?ia=1» (abrir directamente «Crear con IA»). La ruta del editor no
 * lleva parámetros, así que se deja aquí y el editor lo recoge al abrirse.
 */
internal object ScriptEditorStart {
    private var from: String? = null
    private var withAi = false

    data class Request(val from: String?, val withAi: Boolean)

    /** Lo recoge el editor una sola vez: si se vuelve a abrir, empieza de cero. */
    fun take(): Request {
        val request = Request(from, withAi)
        from = null
        withAi = false
        return request
    }

    fun set(from: String?, withAi: Boolean) {
        this.from = from
        this.withAi = withAi
    }
}

/** Abre el editor para un guion nuevo: copiando [from] (un oficial) o con «Crear con IA» abierto. */
internal fun Navigator.newScript(from: String? = null, withAi: Boolean = false) {
    ScriptEditorStart.set(from, withAi)
    navigate(Route.ScriptEditor())
}

/** Aviso con borde de color (como .alert) que admite contenido propio (negritas, listas, enlaces). */
@Composable
internal fun AlertFrame(kind: GrimAlertKind, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val (border, background) = when (kind) {
        GrimAlertKind.Error -> Color(0x99E0242F) to Color(0x269B111E)
        GrimAlertKind.Warning -> Color(0x80E8B84A) to Color(0x14E8B84A)
        GrimAlertKind.Info -> Color(0x735AA2FF) to Color(0x145AA2FF)
        GrimAlertKind.Ok -> Color(0x807CCF7F) to Color(0x147CCF7F)
    }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** «Necesitas conectar la IA. Configúrala en Ajustes.» con el enlace a Ajustes. */
@Composable
internal fun AiNeededNotice() {
    val nav = LocalNavigator.current
    val text = remember(nav) {
        buildAnnotatedString {
            append("Necesitas conectar la IA. ")
            withLink(
                LinkAnnotation.Clickable(
                    "ajustes",
                    TextLinkStyles(SpanStyle(color = Grim.goldBright, textDecoration = TextDecoration.Underline)),
                ) { nav.navigate(Route.Settings) },
            ) { append("Configúrala en Ajustes") }
            append(".")
        }
    }
    AlertFrame(GrimAlertKind.Info) {
        Text(text, style = GrimType.body.copy(fontSize = 15.sp, lineHeight = 21.sp))
    }
}

/** Título pequeño de grupo en mayúsculas doradas (como .log-group__title). */
@Composable
internal fun GroupTitle(text: String) {
    Text(text.uppercase(), style = GrimType.caps.copy(color = Grim.goldBright, letterSpacing = 1.2.sp))
}

/**
 * Cabecera de la tarjeta de un guion (como .edition-banner): el fondo morado con el logo oficial,
 * la imagen que le hayan puesto o, si no, el nombre.
 */
@Composable
internal fun ScriptBanner(scriptId: String, name: String, image: String?, modifier: Modifier = Modifier) {
    val hasLogo = image == null && editionLogo(scriptId) != null
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (hasLogo) 130.dp else 92.dp)
            .background(Brush.linearGradient(listOf(Color(0xFF3B2160), Color(0xFF1D1230))))
            .background(Brush.radialGradient(listOf(Color(0x8C7C48C4), Color.Transparent)))
            .padding(horizontal = if (image != null) 12.dp else 16.dp, vertical = 10.dp),
        contentAlignment = if (image == null && !hasLogo) Alignment.BottomStart else Alignment.Center,
    ) {
        if (image == null && !hasLogo) {
            Text(name, style = GrimType.heading.copy(color = Color.White, fontSize = 23.sp, lineHeight = 27.sp))
        } else {
            EditionBanner(scriptId, name, image)
        }
    }
}

/**
 * Barra de botones pegada abajo (como .wizard-footer): lo de detrás se funde con el fondo. El
 * contenido se centra a [maxWidth] como la página.
 */
@Composable
internal fun FooterBar(maxWidth: Dp = 720.dp, content: @Composable RowScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to Grim.bg))
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

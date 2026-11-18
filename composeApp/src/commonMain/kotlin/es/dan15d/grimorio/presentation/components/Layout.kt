package es.dan15d.grimorio.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimType
import es.dan15d.grimorio.presentation.app.Role as AppRole

/** Barra superior con el logo, la pantalla y, para el Narrador, la barra inferior de navegación. */
@Composable
fun Layout(content: @Composable BoxScope.() -> Unit) {
    val nav = LocalNavigator.current
    val state = LocalAppState.current
    val route = nav.current
    val showNav = state.role == AppRole.Admin && !route.isFlow && !route.isPublic

    Column(Modifier.fillMaxSize()) {
        TopBar()
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        if (showNav) BottomNav(route)
    }
}

@Composable
private fun TopBar() {
    val nav = LocalNavigator.current
    Column(Modifier.fillMaxWidth().background(Color(0xE00B0809))) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClickLabel = "Inicio") { nav.home() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GrimImage("logo.webp", Modifier.size(width = 30.dp, height = 32.dp))
                Text(
                    "GRIMORIO",
                    style = TextStyle(fontFamily = GrimType.fonts.display, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 2.sp, color = Grim.textStrong),
                )
            }
        }
        Divider()
    }
}

@Composable
private fun BottomNav(route: Route) {
    val nav = LocalNavigator.current
    Column(Modifier.fillMaxWidth().background(Color(0xF50B0809))) {
        Divider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(4.dp)) {
            NavItem("Partidas", Lucide.Clock, route == Route.Start) { nav.home() }
            NavItem("Guiones", Lucide.ScrollText, route == Route.Scripts) { nav.navigate(Route.Scripts) }
            NavItem("Ajustes", Lucide.Settings, route == Route.Settings) { nav.navigate(Route.Settings) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val color = if (active) Grim.goldBright else Grim.muted
    Column(
        Modifier
            .weight(1f)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(label.uppercase(), style = TextStyle(fontFamily = GrimType.fonts.ui, fontSize = 11.sp, letterSpacing = 1.sp, color = color))
    }
}

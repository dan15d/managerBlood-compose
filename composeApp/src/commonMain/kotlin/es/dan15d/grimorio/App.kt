package es.dan15d.grimorio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import es.dan15d.grimorio.presentation.app.AppState
import es.dan15d.grimorio.presentation.app.LocalAppState
import es.dan15d.grimorio.presentation.app.Role
import es.dan15d.grimorio.presentation.components.Layout
import es.dan15d.grimorio.presentation.components.Muted
import es.dan15d.grimorio.presentation.components.Page
import es.dan15d.grimorio.presentation.components.ToastHost
import es.dan15d.grimorio.presentation.navigation.LocalNavigator
import es.dan15d.grimorio.presentation.navigation.Navigator
import es.dan15d.grimorio.presentation.navigation.PlatformHistory
import es.dan15d.grimorio.presentation.navigation.Route
import es.dan15d.grimorio.presentation.navigation.initialRoute
import es.dan15d.grimorio.presentation.pages.AdminLoginPage
import es.dan15d.grimorio.presentation.pages.GamePage
import es.dan15d.grimorio.presentation.pages.HomePage
import es.dan15d.grimorio.presentation.pages.JoinPage
import es.dan15d.grimorio.presentation.pages.NewGamePage
import es.dan15d.grimorio.presentation.pages.PlayerPage
import es.dan15d.grimorio.presentation.pages.RevealPage
import es.dan15d.grimorio.presentation.pages.ScriptEditorPage
import es.dan15d.grimorio.presentation.pages.ScriptViewPage
import es.dan15d.grimorio.presentation.pages.ScriptsPage
import es.dan15d.grimorio.presentation.pages.SettingsPage
import es.dan15d.grimorio.presentation.pages.WelcomePage
import es.dan15d.grimorio.presentation.theme.GrimBackground
import es.dan15d.grimorio.presentation.theme.GrimorioTheme

@Composable
fun App() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory()) }.build()
    }
    val scope = rememberCoroutineScope()
    val state = remember { AppState(scope) }
    val nav = remember { Navigator(initialRoute()) }
    LaunchedEffect(Unit) { state.start() }

    GrimorioTheme {
        CompositionLocalProvider(LocalAppState provides state, LocalNavigator provides nav) {
            PlatformHistory(nav)
            GrimBackground {
                Layout {
                    AnimatedContent(
                        targetState = nav.current,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier.fillMaxSize(),
                        label = "pantalla",
                    ) { route -> Screen(route, state) }
                }
                ToastHost(state, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun Screen(route: Route, state: AppState) {
    // Hasta que no se sabe si es el Narrador (y está cargado el catálogo) no se enseña nada.
    if (state.app == null || state.role == Role.Loading) {
        Page(flow = true) { Muted("Cargando…") }
        return
    }
    if (route.needsAdmin && state.role != Role.Admin) {
        val nav = LocalNavigator.current
        LaunchedEffect(route) { nav.navigate(Route.Start, replace = true) }
        return
    }
    Box(Modifier.fillMaxSize()) {
        when (route) {
            Route.Start -> if (state.role == Role.Admin) HomePage() else WelcomePage()
            Route.AdminLogin -> AdminLoginPage()
            is Route.Join -> JoinPage(route.code)
            is Route.Play -> PlayerPage(route.gameId, route.seatId)
            Route.NewGame -> NewGamePage()
            is Route.GameRoute -> GamePage(route.id)
            is Route.Reveal -> RevealPage(route.id)
            Route.Scripts -> ScriptsPage()
            is Route.ScriptEditor -> ScriptEditorPage(route.id)
            is Route.ScriptView -> ScriptViewPage(route.id)
            Route.Settings -> SettingsPage()
        }
    }
}

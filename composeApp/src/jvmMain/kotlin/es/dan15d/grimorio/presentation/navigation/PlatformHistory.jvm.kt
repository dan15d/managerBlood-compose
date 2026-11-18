package es.dan15d.grimorio.presentation.navigation

import androidx.compose.runtime.Composable

actual fun initialRoute(): Route = Route.Start

/** En escritorio no hay «atrás» del sistema: se usa la flecha de la app (y Escape en las hojas). */
@Composable
actual fun PlatformHistory(nav: Navigator) = Unit

package es.dan15d.grimorio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.dan15d.grimorio.presentation.icons.Lucide
import es.dan15d.grimorio.presentation.theme.Grim
import es.dan15d.grimorio.presentation.theme.GrimBackground
import es.dan15d.grimorio.presentation.theme.GrimType
import es.dan15d.grimorio.presentation.theme.GrimorioTheme

@Composable
fun App() {
    GrimorioTheme {
        GrimBackground {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Lucide.Skull, null, tint = Grim.gold)
                Text("El Grimorio", style = GrimType.title)
                Text("Compose Multiplatform", style = GrimType.small)
            }
        }
    }
}

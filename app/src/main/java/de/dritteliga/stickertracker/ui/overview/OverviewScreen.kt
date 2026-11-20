package de.dritteliga.stickertracker.ui.overview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import de.dritteliga.stickertracker.ui.components.AppScaffold
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

@Composable
fun OverviewScreen(onOpenSettings: () -> Unit, onOpenAlbum: (String?, Int?) -> Unit) {
    AppScaffold(title = "Übersicht", actions = {
        IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Einstellungen") }
    }) { p -> Text("Übersicht folgt in Schritt 2.", Modifier.padding(p)) }
}

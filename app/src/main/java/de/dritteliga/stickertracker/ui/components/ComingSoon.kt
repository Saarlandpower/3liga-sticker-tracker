package de.dritteliga.stickertracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ComingSoon(title: String) {
    AppScaffold(title = title) { p ->
        Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) { Text("Folgt in einem späteren Schritt.") }
    }
}

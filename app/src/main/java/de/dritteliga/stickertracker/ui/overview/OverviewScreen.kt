package de.dritteliga.stickertracker.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dritteliga.stickertracker.ui.album.AlbumFilter
import de.dritteliga.stickertracker.ui.components.AppScaffold
import de.dritteliga.stickertracker.ui.components.ManualInputDialog
import de.dritteliga.stickertracker.ui.theme.StatusColors

@Composable
fun OverviewScreen(
    onOpenSettings: () -> Unit,
    onOpenAlbum: (filter: String?, clubId: Int?) -> Unit,
    vm: OverviewViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showInput by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }

    AppScaffold(
        title = "Übersicht",
        actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Einstellungen") } },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showInput = true },
                icon = { Icon(Icons.Filled.Dialpad, null) },
                text = { Text("Nummern eingeben") },
            )
        },
        snackbarHostState = snackbar,
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ProgressCard(state) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        "Fehlend", state.summary.missing.toString(), StatusColors.Missing,
                        Modifier.weight(1f),
                    ) { onOpenAlbum(AlbumFilter.FEHLEND.name, null) }
                    StatCard(
                        "Doppelte", state.summary.duplicates.toString(), StatusColors.Duplicate,
                        Modifier.weight(1f),
                    ) { onOpenAlbum(AlbumFilter.DOPPELT.name, null) }
                }
            }
            if (state.clubs.isNotEmpty()) {
                item { SectionHeader("Fortschritt pro Verein") }
                items(state.clubs, key = { "club-${it.id}" }) { g ->
                    GroupRow(g) { onOpenAlbum(null, g.id ?: -2) }
                }
            } else if (state.loaded) {
                item {
                    Text(
                        "Noch keine Sticker Vereinen zugeordnet. In den Einstellungen → „Vereine bearbeiten“ " +
                            "kannst du Nummernbereiche zuweisen oder die echte Checkliste importieren.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.categories.size > 1) {
                item { SectionHeader("Nach Kategorie") }
                items(state.categories, key = { "cat-${it.id}" }) { g -> GroupRow(g, null) }
            }
        }
    }

    if (showInput) {
        ManualInputDialog(
            knownNumbers = state.knownNumbers,
            onDismiss = { showInput = false },
            onConfirm = { showInput = false; vm.addNumbers(it) },
        )
    }
}

@Composable
private fun ProgressCard(state: OverviewState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            if (state.checklistName.isNotEmpty()) {
                Text(state.checklistName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${state.summary.owned}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    " von ${state.summary.total}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.summary.percent} %",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.summary.progress },
                modifier = Modifier.fillMaxWidth().height(12.dp),
                strokeCap = StrokeCap.Round,
                color = StatusColors.Owned,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).height(44.dp).background(color, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color.lighten())
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun Color.lighten(): Color = Color(
    red = red + (1 - red) * 0.25f, green = green + (1 - green) * 0.25f, blue = blue + (1 - blue) * 0.25f, alpha = alpha,
)

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun GroupRow(g: GroupProgress, onClick: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
            Text(
                "${g.summary.owned}/${g.summary.total}" + if (g.summary.duplicates > 0) " · ${g.summary.duplicates} dopp." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { g.summary.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            strokeCap = StrokeCap.Round,
            color = if (g.summary.missing == 0) StatusColors.Duplicate else StatusColors.Owned,
            drawStopIndicator = {},
        )
    }
}

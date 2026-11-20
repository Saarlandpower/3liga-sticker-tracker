package de.dritteliga.stickertracker.ui.album

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dritteliga.stickertracker.domain.Category
import de.dritteliga.stickertracker.ui.components.AppScaffold
import de.dritteliga.stickertracker.ui.components.ManualInputDialog
import kotlinx.coroutines.launch

@Composable
fun AlbumScreen(
    initialFilter: String?,
    initialClub: Int?,
    onOpenSettings: () -> Unit,
    vm: AlbumViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.init(initialFilter, initialClub) }
    val state by vm.state.collectAsStateWithLifecycle()
    val undo by vm.undo.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var detailNumber by rememberSaveable { mutableStateOf<String?>(null) }
    var showInput by rememberSaveable { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val q = state.query

    LaunchedEffect(undo) {
        val ev = undo ?: return@LaunchedEffect
        vm.consumeUndo()
        snackbar.currentSnackbarData?.dismiss()
        scope.launch {
            val r = snackbar.showSnackbar(ev.text, actionLabel = "Rückgängig", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.undo(ev)
        }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }

    AppScaffold(
        title = "Album",
        actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Einstellungen") } },
        floatingActionButton = {
            FloatingActionButton(onClick = { showInput = true }) { Icon(Icons.Filled.Dialpad, "Nummern eingeben") }
        },
        snackbarHostState = snackbar,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = q.search,
                onValueChange = vm::setSearch,
                placeholder = { Text("Nummer oder Spielername suchen") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (q.search.isNotEmpty()) IconButton(onClick = { vm.setSearch("") }) { Icon(Icons.Filled.Clear, "Suche leeren") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            PrimaryScrollableTabRow(selectedTabIndex = q.filter.ordinal, edgePadding = 8.dp) {
                AlbumFilter.entries.forEach { f ->
                    Tab(selected = q.filter == f, onClick = { vm.setFilter(f) }, text = { Text(f.label) })
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChipMenu(
                    label = when (q.clubId) {
                        null -> "Verein"
                        CLUB_NONE -> "Ohne Verein"
                        else -> state.data.clubById[q.clubId]?.name ?: "Verein"
                    },
                    selected = q.clubId != null,
                    options = listOf<Pair<String, Int?>>("Alle Vereine" to null) +
                        state.data.clubs.map { it.name to it.id } + ("Ohne Verein" to CLUB_NONE),
                    onSelect = vm::setClub,
                )
                ChipMenu(
                    label = q.category?.label ?: "Kategorie",
                    selected = q.category != null,
                    options = listOf<Pair<String, Category?>>("Alle Kategorien" to null) + Category.entries.map { it.label to it },
                    onSelect = vm::setCategory,
                )
                Text(
                    "${state.resultCount} Sticker",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp),
                )
            }

            if (state.resultCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            q.search.isNotEmpty() -> "Kein Sticker passt zu „${q.search}“."
                            q.filter == AlbumFilter.FEHLEND -> "Nichts fehlt – Album komplett! 🎉"
                            q.filter == AlbumFilter.DOPPELT -> "Noch keine Doppelten."
                            else -> "Keine Sticker."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(64.dp),
                    contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    state.sections.forEach { section ->
                        if (section.title != null) {
                            item(key = "h-${section.key}", span = { GridItemSpan(maxLineSpan) }) {
                                Row(Modifier.padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(section.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    section.subtitle?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        items(section.stickers, key = { "${section.key}-${it.number}" }) { s ->
                            StickerTile(s, dark, onTap = { vm.tap(s) }, onLongPress = { detailNumber = s.number })
                        }
                    }
                }
            }
        }
    }

    val detail = detailNumber?.let { n -> state.data.stickers.firstOrNull { it.number == n } }
    if (detail != null) {
        StickerDetailSheet(
            sticker = detail,
            clubs = state.data.clubs,
            onDismiss = { detailNumber = null },
            onChange = { vm.change(detail, it) },
            onSave = vm::save,
        )
    }

    if (showInput) {
        ManualInputDialog(
            knownNumbers = state.data.numbers,
            onDismiss = { showInput = false },
            onConfirm = { showInput = false; vm.addNumbers(it) },
        )
    }
}

@Composable
private fun <T> ChipMenu(label: String, selected: Boolean, options: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}

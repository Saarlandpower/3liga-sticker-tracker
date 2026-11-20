package de.dritteliga.stickertracker.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.dritteliga.stickertracker.domain.Category
import de.dritteliga.stickertracker.domain.Club
import de.dritteliga.stickertracker.domain.Sticker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerDetailSheet(
    sticker: Sticker,
    clubs: List<Club>,
    onDismiss: () -> Unit,
    onChange: (Int) -> Unit,
    onSave: (Sticker) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by rememberSaveable(sticker.number) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text("#${sticker.number}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(
                    sticker.name.ifEmpty { null },
                    sticker.clubId?.let { id -> clubs.firstOrNull { it.id == id }?.name },
                    sticker.category.label,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                FilledIconButton(onClick = { onChange(-1) }, enabled = sticker.count > 0, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Remove, contentDescription = "Eins weniger")
                }
                Column(Modifier.width(140.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${sticker.count}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            sticker.count == 0 -> "fehlt"
                            sticker.count == 1 -> "vorhanden"
                            else -> "${sticker.duplicates} doppelt"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledIconButton(onClick = { onChange(+1) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "Eins mehr")
                }
            }
            Spacer(Modifier.height(20.dp))

            if (!editing) {
                OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Bearbeiten")
                }
            } else {
                var name by rememberSaveable(sticker.number) { mutableStateOf(sticker.name) }
                var clubId by rememberSaveable(sticker.number) { mutableStateOf(sticker.clubId) }
                var category by rememberSaveable(sticker.number) { mutableStateOf(sticker.category) }

                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Dropdown(
                    label = "Verein",
                    selected = clubId?.let { id -> clubs.firstOrNull { it.id == id }?.name } ?: "Ohne Verein",
                    options = listOf<Pair<String, Int?>>("Ohne Verein" to null) + clubs.map { it.name to it.id },
                    onSelect = { clubId = it },
                )
                Spacer(Modifier.height(8.dp))
                Dropdown(
                    label = "Kategorie",
                    selected = category.label,
                    options = Category.entries.map { it.label to it },
                    onSelect = { category = it },
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        onSave(sticker.copy(name = name.trim(), clubId = clubId, category = category))
                        editing = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Speichern") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(label: String, selected: String, options: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

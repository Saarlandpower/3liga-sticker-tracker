package de.dritteliga.stickertracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.dritteliga.stickertracker.data.StickerRepository
import de.dritteliga.stickertracker.domain.Club
import de.dritteliga.stickertracker.domain.ManualInputParser
import de.dritteliga.stickertracker.domain.RangeFormatter
import de.dritteliga.stickertracker.ui.components.AppScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ClubRow(val club: Club, val numbers: List<String>)

@HiltViewModel
class ClubsViewModel @Inject constructor(private val repository: StickerRepository) : ViewModel() {
    val rows: StateFlow<List<ClubRow>> = combine(repository.clubs, repository.stickers) { clubs, stickers ->
        val byClub = stickers.groupBy { it.clubId }
        clubs.map { c -> ClubRow(c, byClub[c.id].orEmpty().map { it.number }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val knownNumbers: StateFlow<Set<String>> = combine(repository.stickers, repository.clubs) { s, _ ->
        s.mapTo(HashSet()) { it.number } as Set<String>
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val message = MutableStateFlow<String?>(null)

    fun save(row: ClubRow, newName: String, numbersInput: String) {
        viewModelScope.launch {
            if (newName.isNotBlank() && newName != row.club.name) repository.renameClub(row.club.id, newName)
            val original = RangeFormatter.format(row.numbers)
            if (numbersInput.trim() != original) {
                val parsed = ManualInputParser.parse(numbersInput, knownNumbers.value)
                val wanted = parsed.numbers.distinct()
                val removed = row.numbers.filter { it !in wanted }
                if (removed.isNotEmpty()) repository.assignClub(removed, null)
                if (wanted.isNotEmpty()) repository.assignClub(wanted, row.club.id)
                message.value = buildString {
                    append("${wanted.size} Nummern zugeordnet")
                    if (parsed.invalid.isNotEmpty()) append(" – unbekannt: ${parsed.invalid.take(8).joinToString(", ")}")
                }
            }
        }
    }
}

@Composable
fun ClubsScreen(onBack: () -> Unit, vm: ClubsViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<ClubRow?>(null) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }

    AppScaffold(title = "Vereine", onBack = onBack, snackbarHostState = snackbar) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Text(
                    "Tippe auf einen Verein, um den Namen zu ändern oder ihm Stickernummern zuzuordnen (z. B. „21-38, S3“).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(rows, key = { it.club.id }) { row ->
                ListItem(
                    headlineContent = { Text(row.club.name) },
                    supportingContent = {
                        Text(
                            if (row.numbers.isEmpty()) "Keine Nummern zugeordnet"
                            else "${row.numbers.size} Sticker: ${RangeFormatter.format(row.numbers)}",
                            maxLines = 2,
                        )
                    },
                    trailingContent = { Icon(Icons.Filled.Edit, contentDescription = "Bearbeiten") },
                    modifier = Modifier.clickable { editing = row },
                )
            }
        }
    }

    editing?.let { row ->
        var name by rememberSaveable(row.club.id) { mutableStateOf(row.club.name) }
        var numbers by rememberSaveable(row.club.id) { mutableStateOf(RangeFormatter.format(row.numbers)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Verein bearbeiten") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        numbers, { numbers = it },
                        label = { Text("Stickernummern") },
                        placeholder = { Text("z. B. 21-38, S3") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.save(row, name, numbers); editing = null }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Abbrechen") } },
        )
    }
}

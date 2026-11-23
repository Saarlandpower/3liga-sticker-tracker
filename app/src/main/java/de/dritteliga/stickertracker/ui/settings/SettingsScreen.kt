package de.dritteliga.stickertracker.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dritteliga.stickertracker.BuildConfig
import de.dritteliga.stickertracker.data.ScanMode
import de.dritteliga.stickertracker.data.ThemeMode
import de.dritteliga.stickertracker.ui.components.AppScaffold

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenClubs: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    backupVm: BackupViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val count by vm.stickerCount.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val backupMessage by backupVm.message.collectAsStateWithLifecycle()
    val pendingRestore by backupVm.pendingRestore.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var showPlaceholderDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    val pickChecklist = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadChecklistFile(uri)
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) backupVm.export(uri)
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backupVm.loadForRestore(uri)
    }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    LaunchedEffect(backupMessage) { backupMessage?.let { snackbar.showSnackbar(it); backupVm.consumeMessage() } }

    AppScaffold(title = "Einstellungen", onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle("Checkliste")
            ListItem(
                headlineContent = { Text(settings.checklistName.ifEmpty { "Checkliste" }) },
                supportingContent = { Text("$count Sticker") },
                leadingContent = { Icon(Icons.Filled.Inventory, null) },
            )
            SettingsAction(Icons.Filled.UploadFile, "Checkliste importieren", "JSON- oder CSV-Datei – vorhandene Anzahlen bleiben erhalten") {
                pickChecklist.launch(arrayOf("application/json", "text/*", "text/csv", "text/comma-separated-values", "application/octet-stream"))
            }
            SettingsAction(Icons.Filled.Numbers, "Platzhalter-Liste erzeugen", "Nummern 1 bis N, wenn die echte Liste noch fehlt") {
                showPlaceholderDialog = true
            }
            SettingsAction(Icons.Filled.Restore, "Mitgelieferte Checkliste laden", "assets/checklist.json erneut einlesen") {
                vm.loadBundled()
            }
            SettingsAction(Icons.Filled.Groups, "Vereine bearbeiten", "Namen ändern und Nummern zuordnen", onOpenClubs)

            HorizontalDivider()
            SectionTitle("Datensicherung")
            SettingsAction(Icons.Filled.Backup, "Bestand exportieren", "Kompletter Stand als JSON-Datei") {
                exportBackup.launch(backupVm.suggestedFileName())
            }
            SettingsAction(Icons.Filled.Restore, "Bestand importieren", "Stand aus einer Export-Datei wiederherstellen") {
                importBackup.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
            }
            Text(
                "Zusätzlich sichert Android die Daten automatisch (Auto Backup), sofern die Sicherung im Google-Konto aktiviert ist.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionTitle("Scannen")
            Row(Modifier.padding(horizontal = 16.dp)) {
                ScanMode.entries.forEach { m ->
                    FilterChip(
                        selected = settings.scanMode == m,
                        onClick = { vm.updateSettings { it.copy(scanMode = m) } },
                        label = { Text(m.label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Text(
                settings.scanMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SwitchRow("Ton bei Treffer", settings.sound) { v -> vm.updateSettings { it.copy(sound = v) } }
            SwitchRow("Vibration bei Treffer", settings.vibration) { v -> vm.updateSettings { it.copy(vibration = v) } }

            HorizontalDivider()
            SectionTitle("Darstellung")
            Row(Modifier.padding(horizontal = 16.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { vm.updateSettings { it.copy(themeMode = mode) } },
                        label = { Text(mode.label) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionTitle("Gefahrenzone")
            SettingsAction(Icons.Filled.RestartAlt, "Alle Anzahlen zurücksetzen", "Setzt jeden Sticker auf „fehlt“") {
                showResetDialog = true
            }
            Text(
                "Version ${BuildConfig.VERSION_NAME} · alle Daten bleiben auf diesem Gerät",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    preview?.let { p ->
        AlertDialog(
            onDismissRequest = vm::cancelImport,
            title = { Text("Checkliste übernehmen?") },
            text = {
                Column {
                    Text(p.title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("• ${p.result.stickers.size} Sticker")
                    Text("• ${p.result.clubs.size} Vereine" + if (!p.parsed.hasClubInfo) " (unverändert)" else "")
                    Text("• ${p.result.keptCounts} vorhandene Anzahlen bleiben erhalten")
                    if (p.result.droppedWithCount.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Achtung: ${p.result.droppedWithCount.size} Sticker mit Bestand sind nicht in der neuen Liste " +
                                "und werden entfernt: ${p.result.droppedWithCount.take(15).joinToString(", ")}" +
                                if (p.result.droppedWithCount.size > 15) " …" else "",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (p.parsed.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Hinweise:", style = MaterialTheme.typography.labelLarge)
                        p.parsed.warnings.take(5).forEach { Text("– $it", style = MaterialTheme.typography.bodySmall) }
                        if (p.parsed.warnings.size > 5) Text("… und ${p.parsed.warnings.size - 5} weitere", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::confirmImport) { Text("Übernehmen") } },
            dismissButton = { TextButton(onClick = vm::cancelImport) { Text("Abbrechen") } },
        )
    }

    pendingRestore?.let { r ->
        AlertDialog(
            onDismissRequest = backupVm::cancelRestore,
            title = { Text("Bestand wiederherstellen?") },
            text = {
                Text(
                    "Die Sicherung vom ${r.exportedAt} enthält ${r.stickerCount} Sticker " +
                        "(${r.ownedCount} vorhanden). Der aktuelle Stand auf diesem Gerät wird vollständig ersetzt."
                )
            },
            confirmButton = { TextButton(onClick = backupVm::confirmRestore) { Text("Ersetzen") } },
            dismissButton = { TextButton(onClick = backupVm::cancelRestore) { Text("Abbrechen") } },
        )
    }

    if (showPlaceholderDialog) {
        var text by rememberSaveable { mutableStateOf(BuildConfig.PLACEHOLDER_COUNT.toString()) }
        AlertDialog(
            onDismissRequest = { showPlaceholderDialog = false },
            title = { Text("Platzhalter-Liste") },
            text = {
                Column {
                    Text("Wie viele Sticker hat das Album? Es werden die Nummern 1 bis N angelegt. Vorhandene Anzahlen bleiben erhalten.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter(Char::isDigit).take(4) },
                        label = { Text("Anzahl N") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPlaceholderDialog = false
                    vm.generatePlaceholders(text.toIntOrNull() ?: 0)
                }) { Text("Weiter") }
            },
            dismissButton = { TextButton(onClick = { showPlaceholderDialog = false }) { Text("Abbrechen") } },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Alle Anzahlen zurücksetzen?") },
            text = { Text("Dein kompletter Bestand wird auf 0 gesetzt. Tipp: Vorher den Bestand exportieren.") },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; vm.resetCounts() }) {
                    Text("Zurücksetzen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onChange(!checked) },
    )
}

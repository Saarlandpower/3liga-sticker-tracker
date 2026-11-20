package de.dritteliga.stickertracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dritteliga.stickertracker.domain.ManualInputParser

/**
 * Nummernblock für die manuelle Eingabe. Mehrere Nummern auf einmal möglich: "12, 45, 45, 201", "45-48", "S1".
 */
@Composable
fun ManualInputDialog(
    knownNumbers: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val parsed = remember(value.text, knownNumbers) { ManualInputParser.parse(value.text, knownNumbers) }

    fun type(s: String) {
        val t = value.text
        val sel = value.selection
        val newText = t.substring(0, sel.start) + s + t.substring(sel.end)
        value = TextFieldValue(newText, TextRange(sel.start + s.length))
    }

    fun backspace() {
        val t = value.text
        val sel = value.selection
        value = if (sel.length > 0) {
            TextFieldValue(t.substring(0, sel.start) + t.substring(sel.end), TextRange(sel.start))
        } else if (sel.start > 0) {
            TextFieldValue(t.substring(0, sel.start - 1) + t.substring(sel.start), TextRange(sel.start - 1))
        } else value
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nummern eingeben") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text("z. B. 12, 45, 45, 201") },
                    textStyle = MaterialTheme.typography.titleLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(if (parsed.numbers.isEmpty()) "Noch keine gültige Nummer" else "${parsed.numbers.size} Sticker werden gezählt")
                        if (parsed.invalid.isNotEmpty()) append(" · unbekannt: ${parsed.invalid.take(6).joinToString(", ")}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (parsed.invalid.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(", ", "0", "⌫"),
                    listOf("-", "S", "✕"),
                )
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { key ->
                            FilledTonalButton(
                                onClick = {
                                    when (key) {
                                        "⌫" -> backspace()
                                        "✕" -> value = TextFieldValue("")
                                        else -> type(key)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(52.dp).padding(vertical = 3.dp),
                            ) {
                                if (key == "⌫") Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Löschen")
                                else Text(
                                    if (key == "✕") "Leeren" else key.trim(),
                                    fontSize = if (key == "✕") 14.sp else 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                Text(
                    "Bereiche mit „-“ (45-48), Sonderkürzel über die Tastatur (FW3).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parsed.numbers) }, enabled = parsed.numbers.isNotEmpty()) { Text("Zählen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Kurzer deutscher Text für das Ergebnis einer Zählung. */
fun countSummary(results: List<de.dritteliga.stickertracker.data.CountChange>, invalid: Int = 0): String {
    val new = results.count { it.isNew }
    val dup = results.size - new
    return buildString {
        append("${results.size} gezählt: $new neu, $dup doppelt")
        if (invalid > 0) append(" · $invalid unbekannt")
    }
}

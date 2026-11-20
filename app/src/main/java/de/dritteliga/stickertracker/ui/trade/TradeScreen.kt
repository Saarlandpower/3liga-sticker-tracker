package de.dritteliga.stickertracker.ui.trade

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dritteliga.stickertracker.domain.RangeFormatter
import de.dritteliga.stickertracker.domain.TradeMatch
import de.dritteliga.stickertracker.ui.components.AppScaffold
import de.dritteliga.stickertracker.ui.theme.StatusColors

@Composable
fun TradeScreen(vm: TradeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val match by vm.match.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showQr by rememberSaveable { mutableStateOf(false) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var confirmPairs by remember { mutableStateOf<List<Pair<String, String>>?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanning = true else vm.showMessage("Ohne Kamerazugriff kann kein QR-Code gescannt werden. Bitte in den App-Einstellungen erlauben.")
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    AppScaffold(title = "Tauschen", snackbarHostState = snackbar) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Meine Tauschliste", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LabeledList("Fehlen (${state.list.missing.size})", state.missingText.ifEmpty { "keine 🎉" }, StatusColors.Missing)
                        Spacer(Modifier.height(8.dp))
                        LabeledList(
                            "Doppelt (${state.list.duplicates.values.sum()})",
                            state.duplicatesText.ifEmpty { "keine" },
                            StatusColors.Duplicate,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Tauschliste 3. Liga Sticker")
                                    putExtra(Intent.EXTRA_TEXT, state.shareText)
                                }
                                context.startActivity(Intent.createChooser(send, "Tauschliste teilen"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Share, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Tauschliste teilen")
                        }
                    }
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("QR-Tausch", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Zeige deinen Code – dein Tauschpartner scannt ihn mit seiner App und sieht sofort passende Tauschpaare. " +
                                "Oder scanne seinen Code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { showQr = true }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.QrCode2, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Mein Code")
                            }
                            FilledTonalButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        scanning = true
                                    } else {
                                        permission.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.QrCodeScanner, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Code scannen")
                            }
                        }
                    }
                }
            }
            match?.let { m -> item { MatchCard(m, onApply = { confirmPairs = m.pairs }, onClear = vm::clearPartner) } }
        }
    }

    if (showQr) {
        Dialog(onDismissRequest = { showQr = false }) {
            Card {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Mein Tausch-Code", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    val qr = remember(state.qrPayload) { runCatching { QrCode.render(state.qrPayload) }.getOrNull() }
                    if (qr != null) {
                        Image(
                            bitmap = qr,
                            contentDescription = "QR-Code mit fehlenden und doppelten Stickern",
                            filterQuality = FilterQuality.None,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .background(Color.White, RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Text("Die Liste ist zu lang für einen QR-Code. Bitte „Tauschliste teilen“ nutzen.", textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${state.list.missing.size} fehlend · ${state.list.duplicates.values.sum()} doppelt",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { showQr = false }) { Text("Schließen") }
                }
            }
        }
    }

    if (scanning) {
        Dialog(onDismissRequest = { scanning = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                QrScanner(
                    onResult = { scanning = false; vm.onQrScanned(it) },
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    "Tausch-Code des Partners in die Mitte halten",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                OutlinedButton(
                    onClick = { scanning = false },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                ) { Text("Abbrechen", color = Color.White) }
            }
        }
    }

    confirmPairs?.let { pairs ->
        AlertDialog(
            onDismissRequest = { confirmPairs = null },
            title = { Text("Tausch buchen?") },
            text = {
                Text(
                    "${pairs.size} Paare: Deine Doppelten ${RangeFormatter.format(pairs.map { it.first })} werden um je 1 verringert, " +
                        "die Sticker ${RangeFormatter.format(pairs.map { it.second })} als erhalten gezählt.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.applyPairs(pairs); confirmPairs = null }) { Text("Buchen") } },
            dismissButton = { TextButton(onClick = { confirmPairs = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun LabeledList(label: String, text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).width(10.dp).height(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MatchCard(m: TradeMatch, onApply: () -> Unit, onClear: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Passende Tauschpaare", style = MaterialTheme.typography.titleMedium)
            if (m.checklistMismatch) {
                Text(
                    "Hinweis: Dein Partner nutzt eine andere Checkliste – Nummern könnten abweichen.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            LabeledList("Du gibst (${m.iGive.size})", RangeFormatter.format(m.iGive).ifEmpty { "nichts Passendes" }, StatusColors.Duplicate)
            Spacer(Modifier.height(8.dp))
            LabeledList("Du bekommst (${m.iGet.size})", RangeFormatter.format(m.iGet).ifEmpty { "nichts Passendes" }, StatusColors.Owned)
            if (m.pairs.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("1:1-Tausch (${m.pairs.size} Paare)", style = MaterialTheme.typography.labelLarge)
                m.pairs.take(30).forEach { (give, get) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("#$give", fontWeight = FontWeight.SemiBold, color = StatusColors.Duplicate, modifier = Modifier.width(64.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.padding(horizontal = 8.dp))
                        Text("#$get", fontWeight = FontWeight.SemiBold, color = StatusColors.Owned)
                    }
                }
                if (m.pairs.size > 30) Text("… und ${m.pairs.size - 30} weitere")
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (m.pairs.isNotEmpty()) Button(onClick = onApply) { Text("Tausch buchen") }
                OutlinedButton(onClick = onClear) { Text("Schließen") }
            }
        }
    }
}

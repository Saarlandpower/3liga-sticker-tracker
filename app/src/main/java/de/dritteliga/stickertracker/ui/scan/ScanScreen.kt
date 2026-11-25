package de.dritteliga.stickertracker.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.dritteliga.stickertracker.data.ScanMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.dritteliga.stickertracker.domain.OcrBox
import de.dritteliga.stickertracker.ui.components.AppScaffold
import de.dritteliga.stickertracker.ui.components.ManualInputDialog
import de.dritteliga.stickertracker.ui.theme.StatusColors

/** Scan-Rahmen in Vorschau-Koordinaten (Pixel): mittig, etwa im Seitenverhältnis eines Stickers. */
fun scanFrame(size: IntSize): OcrBox {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    var fw = w * 0.66f
    var fh = fw * 1.3f
    if (fh > h * 0.82f) { fh = h * 0.82f; fw = fh / 1.3f }
    val left = (w - fw) / 2f
    val top = (h - fh) / 2f
    return OcrBox(left, top, left + fw, top + fh)
}

@Composable
fun ScanScreen(vm: ScanViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var showInput by rememberSaveable { mutableStateOf(false) }

    val session by vm.session.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val capturing by vm.capturing.collectAsStateWithLifecycle()
    val hint by vm.hint.collectAsStateWithLifecycle()
    val known by vm.knownNumbers.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        askedOnce = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = context.hasCameraPermission()
        if (!hasPermission && !askedOnce) {
            askedOnce = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { torchOn = false }

    AppScaffold(
        title = "Scannen",
        actions = {
            IconButton(onClick = {
                vm.setMode(if (mode == ScanMode.MANUELL) ScanMode.AUTOMATISCH else ScanMode.MANUELL)
            }) {
                Icon(
                    if (mode == ScanMode.MANUELL) Icons.Filled.TouchApp else Icons.Filled.Autorenew,
                    contentDescription = if (mode == ScanMode.MANUELL) {
                        "Modus: Auslöser – auf Automatik umschalten"
                    } else {
                        "Modus: Automatik – auf Auslöser umschalten"
                    },
                )
            }
            if (hasPermission && torchAvailable) {
                IconButton(onClick = { torchOn = !torchOn }) {
                    Icon(
                        if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = if (torchOn) "Taschenlampe aus" else "Taschenlampe an",
                    )
                }
            }
            IconButton(onClick = { showInput = true }) { Icon(Icons.Filled.Dialpad, "Nummern manuell eingeben") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .background(Color.Black),
            ) {
                when {
                    hasPermission && cameraError == null -> CameraArea(
                        vm = vm,
                        mode = mode,
                        torchOn = torchOn,
                        paused = paused,
                        live = live,
                        capturing = capturing,
                        onTorchAvailable = { torchAvailable = it },
                        onError = { cameraError = it },
                    )
                    hasPermission -> CameraMessage(
                        title = "Kamera nicht verfügbar",
                        text = cameraError.orEmpty() + "\nDu kannst Nummern weiterhin manuell eingeben.",
                        action = "Nummern eingeben" to { showInput = true },
                    )
                    else -> PermissionMessage(
                        permanentlyDenied = askedOnce && context.findActivity()
                            ?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false,
                        onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        onManual = { showInput = true },
                    )
                }

                ResultOverlay(overlay, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
                HintOverlay(hint, Modifier.align(Alignment.TopCenter).padding(top = 16.dp, start = 16.dp, end = 16.dp))
            }

            SessionList(
                session = session,
                mode = mode,
                onUndo = vm::undo,
                onClear = vm::clearSession,
                modifier = Modifier.weight(0.4f),
            )
        }
    }

    if (showInput) {
        ManualInputDialog(
            knownNumbers = known,
            onDismiss = { showInput = false },
            onConfirm = { showInput = false; vm.addManual(it) },
        )
    }
}

/** Ergebnis-Overlay: "#125 – NEU" (grün) / "#125 – doppelt (jetzt 3x)" (orange) */
@Composable
private fun ResultOverlay(overlay: ScanEntry?, modifier: Modifier) {
    AnimatedVisibility(
        visible = overlay != null,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val e = overlay
        if (e != null) {
            Surface(
                color = if (e.isNew) StatusColors.Owned else StatusColors.Duplicate,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
            ) {
                Text(
                    e.label,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraArea(
    vm: ScanViewModel,
    mode: ScanMode,
    torchOn: Boolean,
    paused: Boolean,
    live: String?,
    capturing: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onError: (String) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val manual = mode == ScanMode.MANUELL
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it; vm.frame = scanFrame(it) },
    ) {
        CameraPreview(
            torchOn = torchOn,
            onTorchAvailable = onTorchAvailable,
            onTokens = vm::onTextFrame,
            onError = onError,
            modifier = Modifier.fillMaxSize(),
        )
        if (manual) {
            // Eigene Tipp-Fläche über der Vorschau: Tipp ins Kamerabild löst aus (große Trefferfläche)
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Auslösen",
                    ) { vm.trigger() },
            )
        }
        val frameColor = when {
            paused -> Color.Gray
            capturing -> StatusColors.Duplicate
            live != null -> StatusColors.Owned
            else -> Color.White
        }
        Canvas(Modifier.fillMaxSize()) {
            if (size == IntSize.Zero) return@Canvas
            val f = scanFrame(size)
            val radius = CornerRadius(24f, 24f)
            val hole = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(f.left, f.top, f.right, f.bottom, radius))
            }
            clipPath(hole, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.45f)) }
            drawRoundRect(
                color = frameColor,
                topLeft = Offset(f.left, f.top),
                size = Size(f.width, f.height),
                cornerRadius = radius,
                style = Stroke(width = if (capturing) 10f else 6f),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when {
                    paused -> "Pausiert"
                    capturing -> "Stillhalten …"
                    live != null && manual -> "Erkannt: #$live – jetzt auslösen"
                    live != null -> "Erkannt: #$live"
                    else -> "Rückseite mit Nummer in den Rahmen halten"
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (manual) {
                Spacer(Modifier.height(10.dp))
                ShutterButton(capturing = capturing, ready = live != null, onClick = vm::trigger)
            }
        }
        if (!manual) {
            FilledTonalIconButton(
                onClick = vm::togglePause,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (paused) "Scannen fortsetzen" else "Scannen pausieren")
            }
        }
    }
}

/** Runder Auslöser wie in einer Kamera-App. */
@Composable
private fun ShutterButton(capturing: Boolean, ready: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(7.dp)
            .background(if (ready) StatusColors.Owned else Color.White.copy(alpha = 0.85f), CircleShape)
            .clickable(enabled = !capturing, onClickLabel = "Sticker zählen", onClick = onClick)
            .semantics { contentDescription = "Auslösen – Sticker zählen" },
        contentAlignment = Alignment.Center,
    ) {
        if (capturing) {
            CircularProgressIndicator(Modifier.size(36.dp), color = Color.White, strokeWidth = 4.dp)
        }
    }
}

/** Hinweis, wenn ein Auslösen nicht gezählt hat. */
@Composable
private fun HintOverlay(hint: ScanHint?, modifier: Modifier) {
    AnimatedVisibility(visible = hint != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val h = hint
        if (h != null) {
            Surface(color = Color(0xFF424242), shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp) {
                Text(
                    h.text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SessionList(
    session: List<ScanEntry>,
    mode: ScanMode,
    onUndo: (ScanEntry) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val newCount = session.count { it.isNew }
            Text(
                if (session.isEmpty()) "Scan-Sitzung" else "Diese Sitzung: ${session.size} Sticker · $newCount neu · ${session.size - newCount} doppelt",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (session.isNotEmpty()) TextButton(onClick = onClear) { Text("Leeren") }
        }
        if (session.isEmpty()) {
            Text(
                if (mode == ScanMode.MANUELL) {
                    "Sticker-Rückseite in den Rahmen halten und auslösen (Knopf oder Tipp ins Bild). " +
                        "Gezählt wird nur, wenn die Nummer über mehrere Bilder eindeutig erkannt wurde."
                } else {
                    "Automatik: Halte Sticker nacheinander vor die Kamera – jede stabil erkannte Nummer wird gezählt. " +
                        "Derselbe Sticker wird nicht doppelt gezählt, solange er im Bild bleibt."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                items(session, key = { it.id }) { e ->
                    ListItem(
                        leadingContent = {
                            Box(
                                Modifier.size(12.dp).background(
                                    if (e.isNew) StatusColors.Owned else StatusColors.Duplicate, CircleShape,
                                ),
                            )
                        },
                        headlineContent = { Text(e.label) },
                        supportingContent = if (e.manual) ({ Text("manuell eingegeben") }) else null,
                        trailingContent = {
                            IconButton(onClick = { onUndo(e) }) { Icon(Icons.AutoMirrored.Filled.Undo, "Rückgängig") }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionMessage(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onManual: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.CameraAlt, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Kamerazugriff benötigt", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (permanentlyDenied) {
                "Du hast den Kamerazugriff abgelehnt. Um Sticker zu scannen, erlaube die Kamera in den App-Einstellungen " +
                    "unter „Berechtigungen“. Die Bilder werden nur auf dem Gerät ausgewertet und nirgends gespeichert."
            } else {
                "Zum Scannen liest die App die Nummer auf der Sticker-Rückseite mit der Kamera. " +
                    "Die Auswertung passiert komplett offline auf deinem Gerät."
            },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = if (permanentlyDenied) onOpenSettings else onRequest) {
            Text(if (permanentlyDenied) "App-Einstellungen öffnen" else "Kamera erlauben")
        }
        OutlinedButton(onClick = onManual, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Filled.Keyboard, null)
            Spacer(Modifier.width(8.dp))
            Text("Stattdessen manuell eingeben")
        }
    }
}

@Composable
private fun CameraMessage(title: String, text: String, action: Pair<String, () -> Unit>) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = action.second) { Text(action.first) }
    }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

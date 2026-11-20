package de.dritteliga.stickertracker.ui.scan

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import de.dritteliga.stickertracker.domain.OcrBox
import de.dritteliga.stickertracker.domain.OcrToken
import java.util.concurrent.Executors

/**
 * Kamera-Vorschau mit ML-Kit-Texterkennung (on-device, offline).
 * Die Koordinaten der erkannten Wörter beziehen sich direkt auf die Vorschau (VIEW_REFERENCED),
 * sodass sie mit dem gezeichneten Rahmen verglichen werden können.
 */
@Composable
fun CameraPreview(
    torchOn: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onTokens: (List<OcrToken>, Long) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    DisposableEffect(lifecycleOwner) {
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(recognizer), CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED, executor) { result ->
                val text: Text? = result.getValue(recognizer)
                val error = result.getThrowable(recognizer)
                if (error != null) Log.w("CameraPreview", "Texterkennung fehlgeschlagen", error)
                onTokens(text?.let(::toTokens).orEmpty(), System.currentTimeMillis())
            },
        )
        try {
            controller.bindToLifecycle(lifecycleOwner)
        } catch (e: Exception) {
            onError("Kamera konnte nicht gestartet werden: ${e.message}")
        }
        val torchCheck = ContextCompat.getMainExecutor(context)
        controller.initializationFuture.addListener({
            onTorchAvailable(controller.cameraInfo?.hasFlashUnit() == true)
        }, torchCheck)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer.close()
            executor.shutdown()
        }
    }

    LaunchedEffect(torchOn) {
        controller.initializationFuture.addListener({
            runCatching { controller.enableTorch(torchOn) }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                this.controller = controller
            }
        },
    )
}

private fun toTokens(text: Text): List<OcrToken> {
    val out = ArrayList<OcrToken>()
    for (block in text.textBlocks) {
        for (line in block.lines) {
            // Ganze kurze Zeile zusätzlich als ein Token ("1 25" → "125")
            val lb = line.boundingBox
            if (lb != null && line.elements.size in 2..3 && line.text.length <= 7) {
                out += OcrToken(line.text.replace(" ", ""), OcrBox(lb.left.toFloat(), lb.top.toFloat(), lb.right.toFloat(), lb.bottom.toFloat()))
            }
            for (el in line.elements) {
                val b = el.boundingBox ?: continue
                out += OcrToken(el.text, OcrBox(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()))
            }
        }
    }
    return out
}

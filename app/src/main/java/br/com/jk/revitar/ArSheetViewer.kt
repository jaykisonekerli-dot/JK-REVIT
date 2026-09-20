package br.com.jk.revitar

import android.graphics.Point
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import java.util.concurrent.Executors
import kotlin.math.atan2
import kotlin.math.sqrt

data class QrScreenPose(
    val centerX: Float,
    val centerY: Float,
    val size: Float,
    val angleDeg: Float
)

@Composable
fun CameraSheetViewer(
    project: RevitProject,
    trackedQrValue: String,
    onBack: () -> Unit
) {
    var pose by remember { mutableStateOf<QrScreenPose?>(null) }
    var mode by remember { mutableStateOf(ViewerMode.PLANTA) }
    var scaleAdjust by remember { mutableFloatStateOf(1.0f) }
    var yawAdjust by remember { mutableFloatStateOf(0f) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val cameraNode = rememberCameraNode(engine) {
        position = Position(z = 3.2f)
    }
    val modelInstance = rememberModelInstance(
        modelLoader = modelLoader,
        assetFileLocation = project.modelUrl
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraQrPreview(
            trackedQrValue = trackedQrValue,
            onPose = { pose = it }
        )

        // SceneView normal (Filament) transparente por cima da câmera.
        SceneView(
            modifier = Modifier.fillMaxSize(),
            surfaceType = SurfaceType.TextureSurface,
            engine = engine,
            modelLoader = modelLoader,
            cameraNode = cameraNode,
            isOpaque = false,
            autoCenterContent = false,
            autoFitContent = false,
        ) {
            modelInstance?.let { instance ->
                val p = pose
                val isTracking = mode == ViewerMode.PLANTA && p != null

                val x = if (isTracking) ((p!!.centerX - 0.5f) * 2.4f) else 0f
                val y = if (isTracking) ((0.5f - p!!.centerY) * 3.4f) else -0.15f

                // Escala visual baseada no tamanho aparente do QR.
                // O ajuste manual permite acertar exatamente sobre a planta impressa.
                val qrScale = if (isTracking) (0.55f + p!!.size * 2.8f) else 1f
                val units = (1.25f * qrScale * scaleAdjust).coerceIn(0.25f, 5.0f)

                val screenAngle = if (isTracking) p!!.angleDeg else 0f

                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = units,
                    position = Position(x = x, y = y, z = 0f),
                    rotation = Rotation(
                        x = if (mode == ViewerMode.PLANTA) -30f else -15f,
                        y = yawAdjust,
                        z = -screenAngle
                    ),
                    isEditable = mode == ViewerMode.MAQUETE
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color(0xCC111827))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "SEM ARCore • ${project.sheetFormat} • 1:${project.planScale}",
                color = Color.White,
                fontSize = 17.sp
            )
            Text(
                if (pose != null) "QR rastreado • modelo acompanhando a prancha"
                else "Aponte a câmera para o QR impresso na prancha",
                color = if (pose != null) Color(0xFF9FE3B1) else Color.White
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { mode = ViewerMode.PLANTA }) { Text("PLANTA") }
                Button(onClick = { mode = ViewerMode.MAQUETE }) { Text("MAQUETE") }
            }

            if (mode == ViewerMode.PLANTA) {
                Text("Ajuste fino da escala", color = Color.White)
                Slider(
                    value = scaleAdjust,
                    onValueChange = { scaleAdjust = it },
                    valueRange = 0.35f..2.5f
                )
                Text("Giro do projeto", color = Color.White)
                Slider(
                    value = yawAdjust,
                    onValueChange = { yawAdjust = it },
                    valueRange = -180f..180f
                )
            } else {
                Text(
                    "Maquete livre: arraste, gire e use pinça para aproximar.",
                    color = Color.White
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(onClick = {
                scaleAdjust = 1f
                yawAdjust = 0f
            }) { Text("RESETAR") }

            Button(onClick = onBack) { Text("VOLTAR") }
        }
    }
}

@Composable
private fun CameraQrPreview(
    trackedQrValue: String,
    onPose: (QrScreenPose?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val provider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val input = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    scanner.process(input)
                        .addOnSuccessListener { barcodes ->
                            val match = barcodes.firstOrNull {
                                it.rawValue == trackedQrValue && it.cornerPoints?.size ?: 0 >= 4
                            }

                            if (match == null) {
                                onPose(null)
                            } else {
                                val points = match.cornerPoints!!
                                val imageW = if (imageProxy.imageInfo.rotationDegrees % 180 == 0)
                                    imageProxy.width.toFloat() else imageProxy.height.toFloat()
                                val imageH = if (imageProxy.imageInfo.rotationDegrees % 180 == 0)
                                    imageProxy.height.toFloat() else imageProxy.width.toFloat()

                                val cx = points.map(Point::x).average().toFloat() / imageW
                                val cy = points.map(Point::y).average().toFloat() / imageH

                                val p0 = points[0]
                                val p1 = points[1]
                                val dx = (p1.x - p0.x).toFloat()
                                val dy = (p1.y - p0.y).toFloat()
                                val edge = sqrt(dx * dx + dy * dy)
                                val size = (edge / imageW).coerceIn(0.02f, 0.9f)
                                val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()

                                onPose(
                                    QrScreenPose(
                                        centerX = cx.coerceIn(0f, 1f),
                                        centerY = cy.coerceIn(0f, 1f),
                                        size = size,
                                        angleDeg = angle
                                    )
                                )
                            }
                        }
                        .addOnFailureListener { onPose(null) }
                        .addOnCompleteListener { imageProxy.close() }
                }

                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (_: Exception) {
                    onPose(null)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

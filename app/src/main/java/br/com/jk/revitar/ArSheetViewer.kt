package br.com.jk.revitar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ar.core.Anchor
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

@Composable
fun ARSheetViewer(project: RevitProject, onBack: () -> Unit) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    var anchor by remember { mutableStateOf<Anchor?>(null) }
    var activeMarker by remember { mutableStateOf<ArMarker?>(null) }
    var trackingText by remember { mutableStateOf("Aponte para um dos marcadores AR impressos na prancha.") }
    var mode by remember { mutableStateOf(ViewerMode.PLANTA) }
    var databaseConfigured by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            planeRenderer = false,
            sessionConfiguration = { session, config ->
                val database = AugmentedImageDatabase(session)
                project.markers.forEach { marker ->
                    database.addImage(marker.name, marker.bitmap, marker.physicalWidthMeters)
                }
                config.augmentedImageDatabase = database
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.focusMode = Config.FocusMode.AUTO
                config.depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    Config.DepthMode.AUTOMATIC
                } else {
                    Config.DepthMode.DISABLED
                }
                databaseConfigured = true
            },
            onSessionUpdated = { _, frame ->
                val detected = frame.getUpdatedTrackables(AugmentedImage::class.java)
                    .firstOrNull { image ->
                        image.trackingState == TrackingState.TRACKING &&
                            project.markers.any { it.name == image.name }
                    }

                if (detected != null) {
                    val marker = project.markers.first { it.name == detected.name }
                    if (anchor == null || activeMarker?.name != marker.name) {
                        anchor?.detach()
                        anchor = detected.createAnchor(detected.centerPose)
                        activeMarker = marker
                    }
                    trackingText = "${detected.name} reconhecido • projeto alinhado à planta"
                } else if (anchor == null && databaseConfigured) {
                    trackingText = "Procure um dos marcadores AR pretos da prancha."
                }
            }
        ) {
            val currentAnchor = anchor
            val marker = activeMarker
            if (currentAnchor != null && marker != null) {
                key(currentAnchor, marker.name, mode) {
                    AnchorNode(anchor = currentAnchor) {
                        rememberModelInstance(
                            modelLoader = modelLoader,
                            assetFileLocation = project.modelUrl
                        )?.let { instance ->
                            ModelNode(
                                modelInstance = instance,
                                position = Position(
                                    x = marker.modelOffsetX,
                                    y = 0.002f,
                                    z = marker.modelOffsetZ
                                ),
                                rotation = Rotation(y = marker.modelYawDegrees),
                                scale = Scale(project.modelScale),
                                isEditable = mode == ViewerMode.MAQUETE
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .background(Color(0xD9111827))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${project.sheetFormat} • Planta 1:${project.planScale}",
                color = Color.White,
                fontSize = 18.sp
            )
            Text(trackingText, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { mode = ViewerMode.PLANTA }) { Text("PLANTA AR") }
                Button(onClick = { mode = ViewerMode.MAQUETE }) { Text("MAQUETE") }
            }
            Text(
                if (mode == ViewerMode.PLANTA)
                    "Escala travada. Caminhe ao redor da prancha para observar a casa."
                else
                    "Maquete livre: arraste, gire e use pinça para ampliar/reduzir.",
                color = Color.White
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(onClick = {
                anchor?.detach()
                anchor = null
                activeMarker = null
                trackingText = "Alinhamento resetado. Aponte novamente para um marcador."
            }) {
                Text("RESETAR")
            }

            Button(onClick = {
                anchor?.detach()
                onBack()
            }) {
                Text("VOLTAR")
            }
        }
    }
}

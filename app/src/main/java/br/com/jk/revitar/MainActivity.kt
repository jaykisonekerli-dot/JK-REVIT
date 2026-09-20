package br.com.jk.revitar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RevitArApp(
                    ensureCameraPermission = {
                        if (ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.CAMERA
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestCamera.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RevitArApp(ensureCameraPermission: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    var project by remember { mutableStateOf<RevitProject?>(null) }
    var manifestUrl by remember { mutableStateOf<String?>(null) }
    var status by remember {
        mutableStateOf("Esta versão usa a câmera normal e não depende de ARCore.")
    }
    var loading by remember { mutableStateOf(false) }
    var showViewer by remember { mutableStateOf(false) }

    if (showViewer && project != null && manifestUrl != null) {
        CameraSheetViewer(
            project = project!!,
            trackedQrValue = manifestUrl!!,
            onBack = { showViewer = false }
        )
        return
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Revit AR Viewer", fontSize = 30.sp)
            Spacer(Modifier.height(6.dp))
            Text("Modo compatibilidade • Sem ARCore", fontSize = 17.sp)
            Spacer(Modifier.height(22.dp))

            Button(
                enabled = !loading,
                onClick = {
                    ensureCameraPermission()
                    val options = GmsBarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .enableAutoZoom()
                        .build()
                    val scanner = GmsBarcodeScanning.getClient(activity, options)
                    scanner.startScan()
                        .addOnSuccessListener { barcode ->
                            val url = barcode.rawValue
                            if (url.isNullOrBlank() || !url.startsWith("http")) {
                                status = "O QR não contém um endereço Revit AR válido."
                                return@addOnSuccessListener
                            }
                            loading = true
                            status = "Carregando projeto e modelo 3D..."
                            scope.launch {
                                try {
                                    project = ProjectLoader.load(url)
                                    manifestUrl = url
                                    status = "Projeto carregado. Abra a câmera e mantenha o QR visível para o rastreamento."
                                    showViewer = true
                                } catch (e: Exception) {
                                    status = "Não consegui carregar o projeto: ${e.message ?: "erro"}. Confirme que celular e PC estão na mesma rede."
                                } finally {
                                    loading = false
                                }
                            }
                        }
                        .addOnCanceledListener { status = "Leitura do QR cancelada." }
                        .addOnFailureListener { e ->
                            status = "Erro ao ler QR: ${e.message ?: "erro desconhecido"}"
                        }
                }
            ) {
                Text(if (loading) "CARREGANDO..." else "ESCANEAR QR DA PRANCHA")
            }

            project?.let { p ->
                Spacer(Modifier.height(20.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(p.name, fontSize = 20.sp)
                        Text("Prancha: ${p.sheetNumber} - ${p.sheetName}")
                        Text("Formato: ${p.sheetFormat} • escala 1:${p.planScale}")
                        Text("Vista: ${p.planName}")
                        Text("Modelo: %.2f × %.2f × %.2f m".format(p.width, p.depth, p.height))
                        Text("Modo: câmera + QR, sem ARCore")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    ensureCameraPermission()
                    showViewer = true
                }) {
                    Text("ABRIR SOBRE A PRANCHA")
                }
            }

            if (loading) {
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator()
            }

            Spacer(Modifier.height(16.dp))
            Text(status)
            Spacer(Modifier.height(12.dp))
            Text(
                "No modo PLANTA, mantenha o QR impresso dentro da câmera. O modelo acompanha a posição e a rotação do QR. No modo MAQUETE, use os gestos para examinar o projeto."
            )
        }
    }
}

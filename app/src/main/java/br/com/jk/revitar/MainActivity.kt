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
    var status by remember {
        mutableStateOf("No Revit 2027, abra uma prancha A0/A1/A3 e use Revit AR > Preparar Prancha AR.")
    }
    var loading by remember { mutableStateOf(false) }
    var showAr by remember { mutableStateOf(false) }

    if (showAr && project != null) {
        ARSheetViewer(project = project!!, onBack = { showAr = false })
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
            Text("A0 • A1 • A3", fontSize = 18.sp)
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
                            val manifestUrl = barcode.rawValue
                            if (manifestUrl.isNullOrBlank() || !manifestUrl.startsWith("http")) {
                                status = "O QR não contém um endereço Revit AR válido."
                                return@addOnSuccessListener
                            }
                            loading = true
                            status = "Carregando projeto, modelo e marcadores AR..."
                            scope.launch {
                                try {
                                    project = ProjectLoader.load(manifestUrl)
                                    status = "Projeto carregado. Aponte para a prancha para iniciar o AR."
                                    showAr = true
                                } catch (e: Exception) {
                                    status = "Não consegui carregar o projeto: ${e.message ?: "erro"}. Confirme que PC e celular estão no mesmo Wi-Fi e mantenha o Revit aberto."
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
                        Text("Marcadores AR: ${p.markers.size}")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    ensureCameraPermission()
                    showAr = true
                }) {
                    Text("ABRIR REALIDADE AUMENTADA")
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
                "Depois de escanear o QR, aponte a câmera para um dos marcadores AR impressos na folha. O QR identifica o projeto; os marcadores fazem o alinhamento do 3D."
            )
        }
    }
}

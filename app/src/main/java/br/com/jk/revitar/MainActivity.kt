package br.com.jk.revitar

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import com.google.ar.core.ArCoreApk
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
        ArCoreApk.getInstance().checkAvailability(applicationContext)

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

private fun openArCoreStore(activity: Activity) {
    val packageName = "com.google.ar.core"
    try {
        activity.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        activity.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            )
        )
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
    var arAvailability by remember { mutableStateOf<ArCoreApk.Availability?>(null) }

    fun refreshArAvailability(): ArCoreApk.Availability {
        val availability = ArCoreApk.getInstance().checkAvailability(activity.applicationContext)
        arAvailability = availability
        return availability
    }

    fun tryOpenAr() {
        val availability = refreshArAvailability()

        when (availability) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                ensureCameraPermission()
                status = "ARCore pronto. Abrindo realidade aumentada..."
                showAr = true
            }

            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                status = "O Google Play Services para RA precisa ser instalado ou atualizado. Atualize e volte ao app; depois toque novamente em ABRIR RA."
                openArCoreStore(activity)
            }

            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                status = "Este celular não é compatível com ARCore. O QR e o projeto funcionam, mas a realidade aumentada não pode ser iniciada neste aparelho."
            }

            ArCoreApk.Availability.UNKNOWN_CHECKING -> {
                status = "Verificando compatibilidade do celular com ARCore. Aguarde alguns segundos e toque novamente."
            }

            ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> {
                status = "A verificação do ARCore demorou demais. Confirme a internet do celular e toque novamente em ABRIR RA."
            }

            ArCoreApk.Availability.UNKNOWN_ERROR -> {
                status = "Não foi possível verificar o ARCore neste celular. Atualize a Play Store e o Google Play Services para RA."
            }
        }
    }

    if (showAr && project != null) {
        ARSheetViewer(project = project!!, onBack = {
            showAr = false
            refreshArAvailability()
        })
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
                                    val availability = refreshArAvailability()
                                    status = when (availability) {
                                        ArCoreApk.Availability.SUPPORTED_INSTALLED ->
                                            "Projeto carregado. ARCore pronto. Toque em ABRIR REALIDADE AUMENTADA."
                                        ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                                        ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED ->
                                            "Projeto carregado. Falta instalar/atualizar o Google Play Services para RA."
                                        ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE ->
                                            "Projeto carregado, mas este celular não é compatível com ARCore."
                                        else ->
                                            "Projeto carregado. Toque em VERIFICAR / ABRIR RA para concluir a verificação."
                                    }
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

                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (arAvailability) {
                                ArCoreApk.Availability.SUPPORTED_INSTALLED -> "RA: pronta"
                                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> "RA: precisa atualizar"
                                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> "RA: precisa instalar"
                                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> "RA: aparelho incompatível"
                                ArCoreApk.Availability.UNKNOWN_CHECKING -> "RA: verificando..."
                                ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> "RA: verificação expirou"
                                ArCoreApk.Availability.UNKNOWN_ERROR -> "RA: erro de verificação"
                                null -> "RA: ainda não verificada"
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(onClick = { tryOpenAr() }) {
                    Text("VERIFICAR / ABRIR RA")
                }

                if (
                    arAvailability == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD ||
                    arAvailability == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED
                ) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { openArCoreStore(activity) }) {
                        Text("INSTALAR / ATUALIZAR GOOGLE RA")
                    }
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
                "O app só abre a câmera AR quando o Google Play Services para RA estiver instalado e atualizado. Assim ele não trava no aviso do sistema."
            )
        }
    }
}

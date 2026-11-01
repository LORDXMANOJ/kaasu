package com.manoj.kaasu.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.manoj.kaasu.data.upi.UpiUri
import com.manoj.kaasu.ui.navigation.Routes
import com.manoj.kaasu.ui.navigateTopLevel
import com.manoj.kaasu.ui.util.openAppSettings
import com.manoj.kaasu.ui.util.rememberLockManager
import kotlinx.coroutines.delay

@Composable
fun ScanScreen(navController: NavHostController, mode: String) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val lockManager = rememberLockManager()
    val forPerson = mode == Routes.SCAN_MODE_PERSON
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        askedOnce = true
    }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    var handled by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var manualEntry by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(2500)
            message = null
        }
    }

    val deliver: (String, String?) -> Unit = { vpa, name ->
        handled = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (forPerson) {
            navController.previousBackStackEntry?.savedStateHandle?.apply {
                set(Routes.RESULT_SCANNED_VPA, vpa)
                set(Routes.RESULT_SCANNED_NAME, name)
            }
            navController.popBackStack()
        }
    }
    val onCode: (String) -> Unit = { raw ->
        if (!handled) {
            val payload = UpiUri.parse(raw)
            when {
                payload == null -> message = "That's not a UPI payment QR"
                forPerson -> deliver(payload.vpa, payload.name)
                else -> {
                    deliver(payload.vpa, payload.name)
                    navController.navigate(Routes.pay(raw = raw))
                }
            }
        }
    }

    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    DisposableEffect(Unit) { onDispose { scanner.close() } }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { InputImage.fromFilePath(context, uri) }
            .onSuccess { image ->
                scanner.process(image)
                    .addOnSuccessListener { codes ->
                        val raw = codes.firstNotNullOfOrNull { it.rawValue }
                        if (raw != null) onCode(raw) else message = "No QR code found in that photo"
                    }
                    .addOnFailureListener { message = "Couldn't read that photo" }
            }
            .onFailure { message = "Couldn't open that photo" }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasPermission) {
            CameraPreview(
                onCode = onCode,
                torchOn = torchOn,
                onTorchAvailable = { torchAvailable = it },
            )
            ScanOverlay()
        } else {
            PermissionNeeded(
                askedOnce = askedOnce,
                onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    lockManager.markExternalLaunch()
                    context.openAppSettings()
                },
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.Rounded.Close, "Close") { navController.popBackStack() }
            Text(
                if (forPerson) "Scan their UPI QR" else "Scan any UPI QR",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            if (hasPermission && torchAvailable) {
                CircleIconButton(
                    if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    if (torchOn) "Turn off torch" else "Turn on torch",
                ) { torchOn = !torchOn }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = if (message != null) MaterialTheme.colorScheme.errorContainer else Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    message ?: if (forPerson) "Point at your friend's GPay / PhonePe QR" else "Point at a shop's or friend's UPI QR",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message != null) MaterialTheme.colorScheme.onErrorContainer else Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScanAction(Icons.Rounded.Image, "From photo", Modifier.weight(1f)) {
                    lockManager.markExternalLaunch()
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                ScanAction(Icons.Rounded.Keyboard, "Type UPI ID", Modifier.weight(1f)) { manualEntry = true }
                if (!forPerson) {
                    ScanAction(Icons.Rounded.Group, "People", Modifier.weight(1f)) {
                        navController.popBackStack()
                        navController.navigateTopLevel(Routes.PEOPLE)
                    }
                }
            }
        }
    }

    if (manualEntry) {
        ManualUpiDialog(
            onDismiss = { manualEntry = false },
            onConfirm = { vpa, name ->
                manualEntry = false
                if (forPerson) {
                    deliver(vpa, name)
                } else {
                    navController.navigate(Routes.pay(raw = UpiUri.fromVpa(vpa, name).raw))
                }
            },
        )
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit, torchOn: Boolean, onTorchAvailable: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCode by rememberUpdatedState(onCode)
    val latestTorchAvailable by rememberUpdatedState(onTorchAvailable)
    val controller = remember {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_ANALYSIS) }
    }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    DisposableEffect(lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor) { result ->
                result.getValue(scanner)?.firstNotNullOfOrNull { it.rawValue }?.let { latestOnCode(it) }
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        controller.initializationFuture.addListener(
            { latestTorchAvailable(controller.cameraInfo?.hasFlashUnit() == true) },
            executor,
        )
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }
    DisposableEffect(Unit) { onDispose { scanner.close() } }
    LaunchedEffect(torchOn) { runCatching { controller.enableTorch(torchOn) } }
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Dark scrim with a clear rounded window, corner brackets and a gentle scan line. */
@Composable
private fun ScanOverlay() {
    val transition = rememberInfiniteTransition(label = "scanLine")
    val linePosition by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanLinePos",
    )
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val side = size.minDimension * 0.68f
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2.3f
        val radius = 28.dp.toPx()
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(side, side),
            cornerRadius = CornerRadius(radius, radius),
            blendMode = BlendMode.Clear,
        )
        val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        val arm = side * 0.16f
        val right = left + side
        val bottom = top + side
        val corners = Path().apply {
            moveTo(left, top + arm); lineTo(left, top + radius); quadraticTo(left, top, left + radius, top); lineTo(left + arm, top)
            moveTo(right - arm, top); lineTo(right - radius, top); quadraticTo(right, top, right, top + radius); lineTo(right, top + arm)
            moveTo(right, bottom - arm); lineTo(right, bottom - radius); quadraticTo(right, bottom, right - radius, bottom); lineTo(right - arm, bottom)
            moveTo(left + arm, bottom); lineTo(left + radius, bottom); quadraticTo(left, bottom, left, bottom - radius); lineTo(left, bottom - arm)
        }
        drawPath(corners, Color.White, style = stroke)
        val y = top + side * linePosition
        drawLine(accent.copy(alpha = 0.85f), Offset(left + 18.dp.toPx(), y), Offset(right - 18.dp.toPx(), y), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
    ) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

@Composable
private fun ScanAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.14f),
        contentColor = Color.White,
        modifier = modifier.heightIn(min = 64.dp),
    ) {
        Column(
            Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PermissionNeeded(askedOnce: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Camera is needed to scan QR codes", style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "You can also pick a QR photo or type a UPI ID below.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = if (askedOnce) onOpenSettings else onAllow) {
            Text(if (askedOnce) "Open settings" else "Allow camera")
        }
    }
}

@Composable
private fun ManualUpiDialog(onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var vpa by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val valid = UpiUri.isValidVpa(vpa)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay a UPI ID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = vpa,
                    onValueChange = { vpa = it.trim() },
                    label = { Text("UPI ID") },
                    placeholder = { Text("name@okaxis") },
                    isError = vpa.isNotEmpty() && !valid,
                    supportingText = { if (vpa.isNotEmpty() && !valid) Text("Looks like name@bank") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(vpa, name.trim().ifEmpty { null }) }, enabled = valid) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

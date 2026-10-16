package com.stopfat.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.stopfat.app.data.Food
import com.stopfat.app.data.FoodGuess
import com.stopfat.app.data.MealType
import com.stopfat.app.data.OpenFoodFacts
import com.stopfat.app.ui.MainViewModel
import com.stopfat.app.ui.Nav
import com.stopfat.app.ui.Routes
import com.stopfat.app.ui.components.GlassButton
import com.stopfat.app.ui.components.GlassCard
import com.stopfat.app.ui.components.GlassTextField
import com.stopfat.app.ui.components.ScreenHeader
import com.stopfat.app.ui.theme.Glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

private fun Context.hasCamera() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

// ---------------------------------------------------------------- barcode

@Composable
fun BarcodeScreen(vm: MainViewModel, nav: Nav, meal: MealType) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var permission by remember { mutableStateOf(context.hasCamera()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    LaunchedEffect(Unit) { if (!permission) launcher.launch(Manifest.permission.CAMERA) }

    var scanning by remember { mutableStateOf(true) }
    var manual by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Pair<Food, String>?>(null) }
    var notFoundCode by remember { mutableStateOf<String?>(null) }

    fun lookup(code: String) {
        if (busy) return
        scanning = false
        busy = true
        status = "Szukam produktu $code…"
        notFoundCode = null
        scope.launch {
            val local = vm.customByBarcode(code)
            if (local != null) {
                found = local.toFood() to "Moje produkty"
                status = null
            } else {
                when (val r = vm.container.openFoodFacts.lookup(code)) {
                    is OpenFoodFacts.Lookup.Found -> { found = r.food to r.brand; status = null }
                    OpenFoodFacts.Lookup.NotFound -> { status = "Nie znaleziono produktu $code w bazie."; notFoundCode = code }
                    is OpenFoodFacts.Lookup.Error -> { status = r.message; notFoundCode = code }
                }
            }
            busy = false
        }
    }

    Page {
        ScreenHeader("Skaner kodów", "Nakieruj aparat na kod kreskowy", onBack = nav::back)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (permission && scanning) {
                BarcodeCamera(onCode = { lookup(it) })
                Box(
                    Modifier
                        .fillMaxWidth(0.75f)
                        .aspectRatio(1.6f)
                        .border(3.dp, Glass.Teal, RoundedCornerShape(20.dp)),
                )
            } else {
                GlassCard(Modifier.fillMaxSize(), corner = 28.dp) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Icon(Icons.Rounded.QrCodeScanner, null, tint = Glass.Teal, modifier = Modifier.padding(8.dp))
                        Text(
                            if (!permission) "Aby skanować, zezwól na dostęp do aparatu." else status ?: "Skanowanie wstrzymane",
                            color = Glass.TextSecondary, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (!permission) GlassButton("Zezwól na aparat", secondary = true) { launcher.launch(Manifest.permission.CAMERA) }
                        else if (!busy) GlassButton("Skanuj ponownie", secondary = true) { found = null; status = null; notFoundCode = null; scanning = true }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTextField(manual, { manual = it.filter(Char::isDigit).take(14) }, "Lub wpisz kod ręcznie", Modifier.weight(1f), KeyboardType.Number)
            GlassButton("Szukaj", enabled = manual.length >= 6 && !busy) { lookup(manual) }
        }
        status?.let { if (permission && !scanning) Text(it, color = Glass.Amber, modifier = Modifier.padding(top = 10.dp)) }
        notFoundCode?.let { code ->
            Spacer(Modifier.height(10.dp))
            GlassButton("Dodaj produkt ręcznie (zapamiętam kod)", Modifier.fillMaxWidth(), secondary = true) { nav.go(Routes.customFood(meal, code)) }
        }
    }

    found?.let { (food, brand) ->
        AmountDialog(
            title = food.name + if (brand.isNotBlank()) " · $brand" else "",
            per100 = food.scaled(100.0),
            unitGrams = food.unitGrams,
            unitName = food.unitName,
            initialMeal = meal,
            onDismiss = { found = null },
        ) { grams, m ->
            vm.addFood(m, food, grams, MainViewModel.SOURCE_BARCODE)
            if (food.barcode != null && !food.id.startsWith("custom_")) {
                vm.saveCustomFood(com.stopfat.app.data.db.CustomFood(name = food.name, kcal = food.kcal, protein = food.protein, carbs = food.carbs, fat = food.fat, unitGrams = food.unitGrams, barcode = food.barcode))
            }
            found = null
            nav.back()
        }
    }
}

@Composable
private fun BarcodeCamera(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128)
                .build(),
        )
    }
    var delivered by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            runCatching {
                val p = providerFuture.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || delivered) {
                        proxy.close(); return@setAnalyzer
                    }
                    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    scanner.process(input)
                        .addOnSuccessListener { codes ->
                            val value = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                            if (value != null && !delivered) {
                                delivered = true
                                ContextCompat.getMainExecutor(context).execute { onCode(value) }
                            }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { provider?.unbindAll() }
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView({ previewView }, Modifier.fillMaxSize())
}

// ---------------------------------------------------------------- photo AI

private fun loadScaledBitmap(context: Context, uri: Uri, maxSide: Int = 1280): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val rotation = context.contentResolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    val scale = maxSide.toFloat() / max(raw.width, raw.height)
    val m = Matrix().apply {
        if (scale < 1f) postScale(scale, scale)
        if (rotation != 0f) postRotate(rotation)
    }
    if (scale < 1f || rotation != 0f) Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true) else raw
}.getOrNull()

private fun Bitmap.toJpegBase64(): String {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 82, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

@Composable
fun PhotoScanScreen(vm: MainViewModel, nav: Nav, meal: MealType) {
    val context = LocalContext.current
    val profile by vm.profile.collectAsState()
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var hint by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf<String?>(null) }
    var selectedMeal by remember { mutableStateOf(meal) }
    val items = remember { mutableStateListOf<FoodGuess>() }
    val photoUri = remember {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(dir, "meal.jpg"))
    }

    fun load(uri: Uri) {
        scope.launch {
            bitmap = withContext(Dispatchers.IO) { loadScaledBitmap(context, uri) }
            items.clear(); notes = null
            error = if (bitmap == null) "Nie udało się wczytać zdjęcia." else null
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) load(photoUri) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) load(uri) }
    var permission by remember { mutableStateOf(context.hasCamera()) }
    val camPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        if (granted) takePicture.launch(photoUri)
    }

    Page {
        ScreenHeader("Zdjęcie AI", "AI rozpozna posiłek i policzy kalorie", onBack = nav::back)
        if (!profile.hasAi) {
            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
                Text("🔑 Ta funkcja wymaga połączenia z AI", color = Glass.Text, style = MaterialTheme.typography.titleMedium)
                Text("Dodaj klucz API Claude w Ustawieniach. Do tego czasu możesz opisać posiłek tekstem (tryb offline).", color = Glass.TextSecondary)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Ustawienia", Modifier.weight(1f), secondary = true) { nav.go(Routes.SETTINGS) }
                    GlassButton("Opisz tekstem", Modifier.weight(1f), secondary = true) { nav.go(Routes.text(meal)) }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.1f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(bmp.asImageBitmap(), "Zdjęcie posiłku", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                GlassCard(Modifier.fillMaxSize(), corner = 28.dp) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📸", style = MaterialTheme.typography.displaySmall)
                        Text("Zrób zdjęcie talerza z góry,\nw dobrym świetle", color = Glass.TextSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Aparat", Modifier.weight(1f), icon = Icons.Rounded.CameraAlt, secondary = true) {
                if (permission) takePicture.launch(photoUri) else camPermission.launch(Manifest.permission.CAMERA)
            }
            GlassButton("Galeria", Modifier.weight(1f), icon = Icons.Rounded.Image, secondary = true) {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassTextField(hint, { hint = it }, "Dodatkowy opis (opcjonalnie)", placeholder = "np. smażone na oliwie, duża porcja")
        Spacer(Modifier.height(12.dp))
        GlassButton(
            "Analizuj z AI", Modifier.fillMaxWidth(), icon = Icons.Rounded.AutoAwesome, loading = busy,
            enabled = bitmap != null && profile.hasAi,
        ) {
            val bmp = bitmap ?: return@GlassButton
            busy = true
            error = null
            scope.launch {
                try {
                    val service = vm.ai() ?: throw IllegalStateException("Brak klucza API")
                    val b64 = withContext(Dispatchers.Default) { bmp.toJpegBase64() }
                    val r = service.analyzePhoto(b64, hint)
                    items.clear(); items.addAll(r.items)
                    notes = r.notes
                    if (r.items.isEmpty()) error = r.notes.ifBlank { "AI nie rozpoznało jedzenia na zdjęciu." }
                } catch (e: Exception) {
                    error = e.message ?: "Błąd analizy"
                } finally {
                    busy = false
                }
            }
        }
        ErrorBanner(error)
        GuessesEditor(items, selectedMeal, { selectedMeal = it }, notes = notes, onAdd = {
            vm.addGuesses(selectedMeal, items.toList(), MainViewModel.SOURCE_PHOTO)
            nav.back()
        })
        if (items.isNotEmpty()) {
            Text(
                "Suma: ${items.sumOf { it.grams }.roundToInt()} g. Możesz poprawić gramaturę każdego produktu przed dodaniem.",
                color = Glass.TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

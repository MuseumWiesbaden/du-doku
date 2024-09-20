package de.muwi.scan

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.exifinterface.media.ExifInterface.TAG_ARTIST
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.common.Barcode
import de.muwi.scan.ui.theme.MuWiScanTheme
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


typealias BarcodeListener = (barcode: Barcode) -> Unit


class MainActivity : ComponentActivity() {

    private lateinit var executor: ExecutorService

    @ExperimentalPermissionsApi
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MuWiScanTheme {
                BaseComposable()
            }
        }

        executor = Executors.newSingleThreadExecutor()
    }

    override fun onDestroy() {
        super.onDestroy()

        executor.shutdown()
    }

    @Composable
    fun BaseComposable(
        viewModel: AppViewModel = viewModel()
    ) {
        viewModel.createSubdir()

        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = "main") {
            composable(route = "main") {
                MainScreen(viewModel,
                    { navController.navigate("barcode") },
                    { navController.navigate("photos") })
            }
            composable("barcode") {
                BarcodePreviewScreen(viewModel, { navController.popBackStack() })
            }
            composable("photos") {
                PhotoPreviewScreen(viewModel)
            }
        }
    }

    @Composable
    fun MainScreen(
        viewModel: AppViewModel = viewModel(),
        onNavigateToBarcode: () -> Unit,
        onNavigateToPhotos: () -> Unit
    ) {
        requestCameraPermissions()

        val uiState by viewModel.uiState.collectAsState()

        Log.w("muwi", "mainscreen called")

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(color = MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .weight(1f),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(
                        12.dp,
                        Alignment.CenterVertically
                    )
                ) {
                    OutlinedTextField(
                        value = uiState.artist,
                        onValueChange = { viewModel.updateAuthor(it) },
                        label = { Text(stringResource(R.string.label_artist)) },
                        singleLine = true,
                        maxLines = 1
                    )

                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = uiState.code,
                            onValueChange = { viewModel.updateLabel(it) },
                            label = { Text(stringResource(R.string.label_code)) },
                            singleLine = true,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        SmallFloatingActionButton(
                            onClick = {
                                onNavigateToBarcode()
                            },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.secondary,

                            ) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_barcode_reader_24),
                                contentDescription = "Scan Barcode Icon"
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.End,
                ) {
                    LargeFloatingActionButton(
                        onClick = {
                            onNavigateToPhotos()
                        },
                        shape = CircleShape,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_photo_camera_24),
                            contentDescription = "Barcode Scanner Icon"
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun BarcodePreviewScreen(
        viewModel: AppViewModel = viewModel(), onPopBackStack: () -> Unit
    ) {
        val state = viewModel.uiState.collectAsState()

        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current

        val lensFacing = CameraSelector.LENS_FACING_BACK

        val preview = Preview.Builder().build()
        val previewView = remember {
            PreviewView(context)
        }

        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        var barcodeDetected by remember { mutableStateOf(false) }

        val analyzer =
            ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also {
                    it.setAnalyzer(
                        ContextCompat.getMainExecutor(context),
                        BarcodeScannerProcessor { barcode ->
                            viewModel.updateLabel(barcode.rawValue.toString())
                            barcodeDetected = true
                        })
                }

        LaunchedEffect(lensFacing) {
            val cameraProvider = context.getCameraProvider()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analyzer)
            preview.surfaceProvider = previewView.surfaceProvider
        }

        val animatedAlpha by animateFloatAsState(
            targetValue = if (barcodeDetected) 1.0f else 0f,
            label = "alpha",
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            finishedListener = {
                onPopBackStack()
            }
        )

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(factory = { previewView })
            }
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .graphicsLayer {
                        alpha = animatedAlpha
                    }
                    .clip(CircleShape)
                    .background(Color.Black)
                    .align(Alignment.Center)
            ) {
                Icon(
                    modifier = Modifier.align(Alignment.Center),
                    painter = painterResource(id = R.drawable.baseline_check_24),
                    contentDescription = "Barcode found",
                    tint = Color.White
                )
            }
            if (barcodeDetected) {
                Text(
                    modifier = Modifier
                        .padding(36.dp)
                        .align(Alignment.BottomCenter),
                    text = state.value.code
                )
            }
        }
    }

    @Composable
    fun PhotoPreviewScreen(
        viewModel: AppViewModel = viewModel()
    ) {
        var captureMs by remember { mutableStateOf(0L) }
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(captureMs) {
            alpha.animateTo(1f)
            alpha.animateTo(0f)
        }

        var touchOffset by remember { mutableStateOf<Offset?>(null) }
        val alpha2 = remember { Animatable(0f) }
        LaunchedEffect(touchOffset) {
            alpha2.animateTo(1f)
            alpha2.animateTo(0f)
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        val context = LocalContext.current

        val preview = Preview.Builder().build()
        val previewView = remember {
            PreviewView(context)
        }

        val lensFacing = CameraSelector.LENS_FACING_BACK

        val imageCapture: ImageCapture = remember {
            ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
        }

        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        var camera by remember { mutableStateOf<Camera?>(null) }

        LaunchedEffect(lensFacing) {
            val cameraProvider = context.getCameraProvider()
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, preview, imageCapture
            )

            preview.surfaceProvider = previewView.surfaceProvider
            previewView.scaleType = PreviewView.ScaleType.FIT_START
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.Black)
                .windowInsetsPadding(WindowInsets.displayCutout)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {

                        detectTapGestures {
                            Log.d("muwi", "android view ${it.x}, ${it.y}")

                            val factory = previewView.getMeteringPointFactory()
                            val point = factory.createPoint(it.x, it.y)

                            val action = FocusMeteringAction
                                .Builder(point)
                                .build()

                            camera?.cameraControl?.startFocusAndMetering(action)

                            Log.d("muwi", "${it.y} ${previewView.height}")

                            touchOffset = Offset(it.x, it.y)
                        }
                    }, factory = { previewView })


            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = alpha.value)
                    .background(Color.Black)
            ) {}
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = alpha2.value),
            ) {
                touchOffset?.let { touchOffset ->
                    drawCircle(
                        Color.White, center = touchOffset, radius = 64f, style = Stroke(width = 6f)
                    )
                }
            }
            val shapeA = remember {
                RoundedPolygon.circle(
                    6
                )
            }
            val shapeB = remember {
                RoundedPolygon(
                    6, rounding = CornerRounding(0.2f)
                )
            }
            val morph = remember {
                Morph(shapeA, shapeB)
            }
            val interactionSource = remember {
                MutableInteractionSource()
            }
            val isPressed by interactionSource.collectIsPressedAsState()
            val animatedProgress = animateFloatAsState(
                targetValue = if (isPressed) 1f else 0f,
                label = "shutterButtonPressShapeShift",
                animationSpec = spring(dampingRatio = 1.0f, stiffness = Spring.StiffnessHigh)
            )
            val animatedRotation = animateFloatAsState(
                targetValue = if (isPressed) 60f else 0f,
                animationSpec = spring(dampingRatio = 1.0f, stiffness = Spring.StiffnessHigh),
                label = "shutterButtonPressRotation"
            )

            val orientation = LocalConfiguration.current.orientation

            Box(modifier = Modifier
                .padding(36.dp)
                .conditional(orientation == Configuration.ORIENTATION_PORTRAIT) {
                    align(Alignment.BottomCenter)
                }
                .conditional(orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    align(Alignment.CenterEnd)
                }
                .size(80.dp)
                .clip(
                    CustomRotatingMorphShape(
                        morph, animatedProgress.value, animatedRotation.value
                    )
                )
                .background(Color.White)
                .clickable(interactionSource = interactionSource, indication = null) {

                    val file = viewModel.getNextFile()

                    Log.w("muwi", "file=$file")
                    capture(imageCapture, executor, file, viewModel.uiState.value.artist)
                    captureMs = System.currentTimeMillis()

                }) {}
        }
    }

    private fun capture(
        imageCapture: ImageCapture, executor: ExecutorService, file: File, artist: String
    ) {
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        Log.d("muwi", file.absolutePath.toString())

        imageCapture.takePicture(outputFileOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e("muwi", "take photo error", exception)
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                    val savedUri = Uri.fromFile(file)
                    Log.d("muwi", "photo saved, artist=$artist, uri=$savedUri")

                    val exif = ExifInterface(file)
                    exif.setAttribute(TAG_ARTIST, artist)
                    exif.saveAttributes()

                    /*val exif = Exif.createFromFile(file)
                    val rotation = exif.rotation
                    Log.w("muwi", rotation.toString())*/

                    MediaScannerConnection.scanFile(
                        applicationContext, arrayOf(file.toString()), null, null
                    )
                }
            })
    }

    // auxiliary functions /////////////////////////////////////////////////////////////////////////

    @Composable
    @OptIn(ExperimentalPermissionsApi::class)
    fun requestCameraPermissions() {
        val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

        val requestPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Permission granted
            } else {
                // Handle permission denial
            }
        }

        LaunchedEffect(cameraPermissionState) {
            if (cameraPermissionState.status.isGranted && cameraPermissionState.status.shouldShowRationale) {
                // Show rationale if needed
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
        suspendCoroutine { continuation ->
            ProcessCameraProvider.getInstance(this).also { cameraProvider ->
                cameraProvider.addListener({
                    continuation.resume(cameraProvider.get())
                }, ContextCompat.getMainExecutor(this))
            }
        }

    fun Modifier.conditional(condition: Boolean, modifier: Modifier.() -> Modifier): Modifier {
        return if (condition) {
            then(modifier(Modifier))
        } else {
            this
        }
    }
}

//package com.example.studykotlin.activity
//
//import android.Manifest
//import android.content.pm.PackageManager
//import android.graphics.Bitmap
//import android.graphics.ImageFormat
//import android.os.Bundle
//import android.os.Handler
//import android.os.Looper
//import android.util.Log
//import android.util.Size
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.BackHandler
//import androidx.activity.compose.setContent
//import androidx.appcompat.app.AppCompatDelegate
//import androidx.camera.core.*
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.view.PreviewView
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.padding
//import androidx.compose.material3.Button
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.mutableStateMapOf
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.viewinterop.AndroidView
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import com.example.studykotlin.SocketClient
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.SupervisorJob
//import kotlinx.coroutines.cancel
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.isActive
//import kotlinx.coroutines.launch
//import java.io.ByteArrayOutputStream
//import java.io.File
//import java.io.IOException
//import java.nio.ByteBuffer
//import java.util.concurrent.Executors
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.text.style.LineHeightStyle
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import com.example.studykotlin.R
//
//class SecondActivity : ComponentActivity() {
//    //Time Limit = 60*1000ms
//    private var timeRemaining: Long = 60000
//
//    private lateinit var previewView: PreviewView
//    private val cameraExecutor = Executors.newSingleThreadExecutor()
//    private val CAMERA_PERMISSION_CODE = 1
//
//    private val serverAddress = "121.130.81.109"
//    //private val serverAddress = "10.0.2.2"
//    private val serverPort = 8765
//    private val socketClient = SocketClient(serverAddress,serverPort)
//
//    private lateinit var imageAnalysis: ImageAnalysis
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // 권한 확인 및 요청
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
//        } else {
//            setContent {
//                MaterialTheme {
//                    CameraScreen()
//                }
//            }
//        }
//    }
//    @Composable
//    fun CameraScreen() {
//        val context = LocalContext.current
//        var showTimer by remember { mutableStateOf(true) }
//        var timerRunning by remember { mutableStateOf(true) }
//        var timeLeft by remember { mutableStateOf(timeRemaining / 1000) }
//        var showExitDialog by remember { mutableStateOf(false) }
//        // 카메라 프리뷰를 위한 PreviewView 설정
//        val previewView = remember { PreviewView(context) }
//
//        LaunchedEffect(Unit) {
//            startCamera(previewView)
//        }
//
//        // 타이머 시작
//        LaunchedEffect(timerRunning) {
//            if (timerRunning) {
//                while (timeLeft > 0) {
//                    delay(1000L)
//                    timeLeft -= 1
//                }
//                if (timeLeft <= 0) {
//                    finish()
//                }
//            }
//        }
//
//        Column(
//            modifier = Modifier.fillMaxSize()
//        ) {
//            // 위쪽 절반에 이미지 배치
//            Image(
//                painter = painterResource(id = R.drawable.sample_image),
//                contentDescription = null,
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .weight(1f)
//            )
//
//            // 아래쪽 절반에 카메라 뷰
//            AndroidView(
//                factory = { previewView },
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .weight(1f)
//            )
//
//            // 타이머가 보이는 경우에만 표시
//            if (showTimer) {
//                Text(
//                    text = "남은 시간: ${timeLeft}초",
//                    fontSize = 20.sp,
//                    modifier = Modifier
//                        .align(Alignment.CenterHorizontally)
//                        .padding(16.dp)
//                )
//            }
//        }
//        BackHandler {
//            timerRunning = false
//            showExitDialog = true
//        }
//        if (showExitDialog) {
//            ExitConfirmationDialog(
//                onConfirm = {
//                    (context as? ComponentActivity)?.finish()
//                },
//                onDismiss = {
//                    showExitDialog = false
//                    timerRunning = true
//                }
//            )
//        }
//    }
//    @Composable
//    fun ExitConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
//        androidx.compose.material3.AlertDialog(
//            onDismissRequest = onDismiss,
//            title = { Text("종료하시겠습니까?") },
//            confirmButton = {
//                Button(onClick = onConfirm) {
//                    Text("예")
//                }
//            },
//            dismissButton = {
//                Button(onClick = onDismiss) {
//                    Text("아니오")
//                }
//            }
//        )
//    }
//    private fun showExitConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
//        TODO("Not yet implemented")
//    }
//
//    private fun startCamera(previewView: PreviewView) {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//
//        cameraProviderFuture.addListener({
//            try {
//                val cameraProvider = cameraProviderFuture.get()
//                val preview = Preview.Builder()
//                    .setTargetResolution(Size(360, 480)) // 원하는 해상도 설정
//                    .build()
//                    .also {
//                        it.setSurfaceProvider(previewView.surfaceProvider)
//                    }
//                val imageAnalysis = ImageAnalysis.Builder()
//                    .setTargetResolution(Size(360, 480))
//                    //.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
//                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                    .build()
//                    .also {
//                        it.setAnalyzer(cameraExecutor, FrameAnalyzer(socketClient, frameIntervalMs = 1000L))
//                    }
//
//                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
//                val camera = cameraProvider.bindToLifecycle(
//                    this,
//                    cameraSelector,
//                    preview,
//                    imageAnalysis
//                )
//            } catch (e: Exception) {
//                Log.e("CameraX", "Failed to get CameraProvider", e)
//            }
//        }, ContextCompat.getMainExecutor(this))
//    }
//
//
//    private class FrameAnalyzer(private val socketClient: SocketClient,
//                                private val frameIntervalMs: Long) : ImageAnalysis.Analyzer {
//
//        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
//        private var latestBytes: ByteArray? = null
//        private var hasSentResolution = false
//        private var imageWidth: Int = 0
//        private var imageHeight: Int = 0
//
//        init {
//            scope.launch {
//                try {
//                    socketClient.connect()
//                    startPeriodicDataSend()
//                } catch (e: IOException) {
//                    Log.e("SocketClient", "Error connecting to server", e)
//                }
//            }
//        }
//        private fun startPeriodicDataSend() {
//            scope.launch {
//                while (isActive) {
//                    // 데이터 전송 작업 수행
//                    latestBytes?.let { dataToSend ->
//                        try {
////                            if (!hasSentResolution) {
////                                // 첫 전송 시 해상도 전송
////                                val header = createResolutionHeader(imageWidth, imageHeight)
////                                socketClient.sendData(header + dataToSend)
////                                Log.d("Data Size",(header + dataToSend).size.toString())
////                                hasSentResolution = true
////                            } else {
//                                // 해상도 정보를 제외하고 이미지 데이터 전송
//                                socketClient.sendData(dataToSend)
//                                //Log.d("Data Size",dataToSend.size.toString())
//                            //}
//                        } catch (e: IOException) {
//                            Log.e("SocketClient", "Error sending data", e)
//                        }
//                    }
//                    delay(frameIntervalMs) // 지정된 시간 간격으로 반복
//                }
//            }
//        }
//        private fun createResolutionHeader(width: Int, height: Int): ByteArray {
//            val widthBytes = intToByteArray(width)
//            val heightBytes = intToByteArray(height)
//            return widthBytes + heightBytes
//        }
//        private fun intToByteArray(value: Int): ByteArray {
//            return byteArrayOf(
//                (value shr 24).toByte(),
//                (value shr 16).toByte(),
//                (value shr 8).toByte(),
//                value.toByte()
//            )
//        }
//
//        override fun analyze(image: ImageProxy) {
//            //image resolution 정보
//            if (!hasSentResolution) {
//                //Log.d("Resolution: ",image.width.toString()+ "x"+image.height.toString())
//                imageWidth = image.width
//                imageHeight = image.height
//            }
////            val format = image.format
////            when (format) {
////                ImageFormat.YUV_420_888 -> Log.d("ImageAnalyzer", "Image format: YUV_420_888")
////                ImageFormat.NV21 -> Log.d("ImageAnalyzer", "Image format: NV21")
////                ImageFormat.YUV_422_888 -> Log.d("ImageAnalyzer", "Image format: YUV_422_888")
////                ImageFormat.YUV_444_888 -> Log.d("ImageAnalyzer", "Image format: YUV_444_888")
////                ImageFormat.JPEG -> Log.d("ImageAnalyzer", "Image format: JPEG")
////                else -> Log.d("ImageAnalyzer", "Unknown image format: $format")
////            }
////            Log.d("PixelStride","Y Plane PixelStride"+image.planes[0].pixelStride.toString())
////            Log.d("PixelStride","U Plane PixelStride"+image.planes[1].pixelStride.toString())
////            Log.d("PixelStride","V Plane PixelStride"+image.planes[2].pixelStride.toString())
//
//          //YUV BUFFER
////            Log.d("PlaneSize","Y: "+image.planes[0].buffer.remaining())
////            Log.d("PlaneSize","U?: "+image.planes[1].buffer.remaining())
////            Log.d("PlaneSize","V?: "+image.planes[2].buffer.remaining())
//
//            val planes = image.planes
//            val yPlane = planes[0]
//            val uPlane = planes[1]
//            val vPlane = planes[2]
//            val yBuffer = yPlane.buffer
//            val uBuffer = uPlane.buffer
//            val vBuffer = vPlane.buffer
//
//            val numPixels = (image.width * image.height * 1.5).toInt()
//            val nv21 = ByteArray(numPixels)
//
//            val yRowStride = yPlane.rowStride
//            val yPixelStride = yPlane.pixelStride
//            val uvRowStride = uPlane.rowStride
//            val uvPixelStride = uPlane.pixelStride
//
//            var idY = 0
//            var idUV = image.width * image.height
//            val uvWidth = image.width / 2
//            val uvHeight = image.height / 2
//
//            for (y in 0 until image.height) {
//                val yOffset = y * yRowStride
//                val uvOffset = y * uvRowStride
//
//                for (x in 0 until image.width) {
//                    nv21[idY++] = yBuffer[yOffset + x * yPixelStride]
//
//                    if (y < uvHeight && x < uvWidth) {
//                        val uvIndex = uvOffset + (x * uvPixelStride)
//                        nv21[idUV++] = vBuffer[uvIndex]
//                        nv21[idUV++] = uBuffer[uvIndex]
//                    }
//                }
//            }
//
//            latestBytes=nv21
//            //Log.d("PlaneSize","YUV: "+ latestBytes!!.size)
//
//
//
////            val planes = image.planes
////            val yPlane = planes[0]
////            val uPlane = planes[1]
////            val vPlane = planes[2]
////            val ySize = yPlane.buffer.remaining()
////            val uvSize = uPlane.buffer.remaining()
////            val bytes = ByteArray(ySize + uvSize+uvSize)
////
////            yPlane.buffer.get(bytes, 0, ySize)
////            uPlane.buffer.get(bytes,ySize,uvSize)
////            vPlane.buffer.get(bytes,ySize+uvSize,uvSize)
//
//            //Log.d("ImageSize", latestBytes!!.size.toString())
//
////            val buffer: ByteBuffer = image.planes[0].buffer
////
////            // Read the RGBA data from the buffer
////            val width = image.width
////            val height = image.height
////            val pixelCount = width * height
////
////            // Create a ByteArray to hold the RGBA data
////            val rgbaBytes = ByteArray(pixelCount * 4)
////            buffer.get(rgbaBytes)
////            //Log.d("BufferSizes",rgbaBytes.size.toString())
////            latestBytes = rgbaBytes
//            image.close()
//        }
//        fun close(){
//            scope.cancel()
//            //socketClient.close()
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        cameraExecutor.shutdown()
//        socketClient.close()
//    }
//}

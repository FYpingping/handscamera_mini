package com.example.studykotlin.activity

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.studykotlin.SocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studykotlin.R
import com.example.studykotlin.ui.theme.ColorModeTheme
import com.example.studykotlin.util.CameraManager
import kotlinx.coroutines.withContext

class AnswerActivity : ComponentActivity() {
    //Time Limit = 60*1000ms
    private var timeRemaining: Long = 60000

    private lateinit var previewView: PreviewView
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val CAMERA_PERMISSION_CODE = 1

    private lateinit var serverAddress: String
    private var serverPort: Int = 0
    private lateinit var socketClient: SocketClient
    private lateinit var imageAnalysis: ImageAnalysis
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        serverAddress = getString(R.string.server_ip)
        serverPort = resources.getInteger(R.integer.quiz_answer_port)
        socketClient = SocketClient(serverAddress,serverPort,"Answer")
        super.onCreate(savedInstanceState)

        // 권한 확인 및 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        } else {
            scope.launch {
                try {
                    socketClient.connect()
                    //메인 스레드에서 setContent 호출해야 함.
                    withContext(Dispatchers.Main){
                        setContent {
                            ColorModeTheme {
                                CameraScreen()
                            }
                        }
                    }

                } catch (e: IOException) {
                    Toast.makeText(this@AnswerActivity, "연결할 수 없음", Toast.LENGTH_SHORT).show()
                    delay(Toast.LENGTH_SHORT.toLong())
                    Log.e("QuizActivity", "Error connecting to server", e)
                    finish()
                }
            }

        }
    }
    @Composable
    fun CameraScreen() {
        ColorModeTheme {
            val context = LocalContext.current
            var showTimer by remember { mutableStateOf(true) }
            var timerRunning by remember { mutableStateOf(true) }
            var timeLeft by remember { mutableStateOf(timeRemaining / 1000) }
            var showExitDialog by remember { mutableStateOf(false) }
            // 카메라 프리뷰를 위한 PreviewView 설정
            val previewView = remember { PreviewView(context) }

            LaunchedEffect(Unit) {
                startCamera(previewView)
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 위쪽 절반에 상대측 이미지
                Image(
                    painter = painterResource(id = R.drawable.sample_image),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // 아래쪽 절반에 카메라 뷰
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // 타이머가 보이는 경우에만 표시
                if (showTimer) {
                    Text(
                        text = "남은 시간: ${timeLeft}초",
                        fontSize = 20.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(16.dp)
                    )
                }
            }
            BackHandler {
                timerRunning = false
                showExitDialog = true
            }
            if (showExitDialog) {
                ExitConfirmationDialog(
                    onConfirm = {
                        (context as? ComponentActivity)?.finish()
                    },
                    onDismiss = {
                        showExitDialog = false
                        timerRunning = true
                    }
                )
            }
        }

    }
    @Composable
    fun ExitConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("종료하시겠습니까?") },
            confirmButton = {
                Button(onClick = onConfirm) {
                    Text("예")
                }
            },
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text("아니오")
                }
            }
        )
    }
    private fun showExitConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        TODO("Not yet implemented")
    }

    private fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .setTargetResolution(Size(360, 480)) // 원하는 해상도 설정
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(360, 480))
                    //.setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, FrameAnalyzer(socketClient, frameIntervalMs = 1L))
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Failed to get CameraProvider", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }


    private class FrameAnalyzer(private val socketClient: SocketClient,
                                private val frameIntervalMs: Long) : ImageAnalysis.Analyzer {

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private var latestBytes: ByteArray? = null

        init {
            scope.launch {
                try {
                    startPeriodicDataSend()
                } catch (e: IOException) {
                    Log.e("SocketClient", "Error connecting to server", e)
                }
            }
        }
        private fun startPeriodicDataSend() {
            scope.launch {
                while (isActive) {
                    // 데이터 전송 작업 수행
                    latestBytes?.let { dataToSend ->
                        try {
                            socketClient.sendData(dataToSend)
                            Log.d("SocketClient","DATA SENT")
                        } catch (e: IOException) {
                            Log.e("SocketClient", "Error sending data", e)
                        }
                    }
                    delay(frameIntervalMs) // 지정된 시간 간격으로 반복
                }
            }
        }
        private fun createResolutionHeader(width: Int, height: Int): ByteArray {
            val widthBytes = intToByteArray(width)
            val heightBytes = intToByteArray(height)
            return widthBytes + heightBytes
        }
        private fun intToByteArray(value: Int): ByteArray {
            return byteArrayOf(
                (value shr 24).toByte(),
                (value shr 16).toByte(),
                (value shr 8).toByte(),
                value.toByte()
            )
        }

        override fun analyze(image: ImageProxy) {

            val planes = image.planes
            val yPlane = planes[0]
            val uPlane = planes[1]
            val vPlane = planes[2]
            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val numPixels = (image.width * image.height * 1.5).toInt()
            val nv21 = ByteArray(numPixels)

            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            var idY = 0
            var idUV = image.width * image.height
            val uvWidth = image.width / 2
            val uvHeight = image.height / 2

            for (y in 0 until image.height) {
                val yOffset = y * yRowStride
                val uvOffset = y * uvRowStride

                for (x in 0 until image.width) {
                    nv21[idY++] = yBuffer[yOffset + x * yPixelStride]

                    if (y < uvHeight && x < uvWidth) {
                        val uvIndex = uvOffset + (x * uvPixelStride)
                        nv21[idUV++] = vBuffer[uvIndex]
                        nv21[idUV++] = uBuffer[uvIndex]
                    }
                }
            }
            val size = nv21.size
            val width = image.width
            val height = image.height
            val header = createHeader(size, width, height)

            latestBytes = ByteArray(header.size + nv21.size)
            System.arraycopy(header, 0, latestBytes, 0, header.size)
            System.arraycopy(nv21, 0, latestBytes, header.size, nv21.size)
            image.close()
        }
        fun close(){
            scope.cancel()
            //socketClient.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        socketClient.close()
    }
}

fun intToBytes(value: Int): ByteArray {
    return byteArrayOf(
        (value shr 24 and 0xFF).toByte(),
        (value shr 16 and 0xFF).toByte(),
        (value shr 8 and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}

// 헤더를 생성하는 함수
fun createHeader(size: Int, width: Int, height: Int): ByteArray {
    val header = ByteArray(12)
    System.arraycopy(intToBytes(size), 0, header, 0, 4)
    System.arraycopy(intToBytes(width), 0, header, 4, 4)
    System.arraycopy(intToBytes(height), 0, header, 8, 4)
    return header
}
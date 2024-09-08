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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.studykotlin.R
import com.example.studykotlin.ui.theme.ColorModeTheme
import com.example.studykotlin.util.CameraManager
import kotlinx.coroutines.withContext

class QuizWithNetActivity : ComponentActivity() {
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
    private lateinit var cameraManager: CameraManager
    private var receivedBitmap by mutableStateOf<Bitmap?>(null)
    private var isButtonEnabled by mutableStateOf(true)


    override fun onCreate(savedInstanceState: Bundle?) {
        serverAddress = getString(R.string.server_ip)
        serverPort = resources.getInteger(R.integer.quiz_answer_port)
        socketClient = SocketClient(serverAddress,serverPort,"Quiz")
        super.onCreate(savedInstanceState)

        cameraManager = CameraManager(
            socketClient,
            PreviewView(this),
            CoroutineScope(Dispatchers.IO),
            cameraExecutor
        )

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
                                QuizScreen(
                                    cameraManager = cameraManager,
                                    receivedBitmap = receivedBitmap
                                )
                            }
                        }
                    }
                    cameraManager.startCamera(this@QuizWithNetActivity)
                    startReceivingData()

                } catch (e: Exception) {
                    Toast.makeText(this@QuizWithNetActivity, "연결할 수 없음", Toast.LENGTH_SHORT).show()
                    delay(Toast.LENGTH_SHORT.toLong())
                    Log.e("AWNActivity", "Error connecting to server", e)
                    finish()
                }
            }

        }
    }
    private fun startReceivingData() {
        scope.launch {
            try {
                while (true) {
                    try {
                        val result = try{socketClient.receiveDataFromServer()
                        }catch (e:Exception){
                            Log.e("AWNActivity", "Error Occured", e)
                            break
                        }
                        withContext(Dispatchers.Main) {
                            when (result) {
                                is Bitmap -> {
                                    // 이미지가 수신된 경우
                                    receivedBitmap = result
                                }

                                is String -> {
                                    if(result=="Answer") {
                                        withContext(Dispatchers.Main) {
                                            isButtonEnabled = true
                                        }
                                    }
                                }

                                else -> {
                                    Toast.makeText(
                                        this@QuizWithNetActivity,
                                        "연결이 끊어졌습니다. 이전 화면으로 돌아갑니다.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    finish() // 이전 화면으로 돌아가기
                                    return@withContext
                                }
                            }
                        }
                    }catch(e:Exception){
                        Log.d("QWNActivity","Error")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@QuizWithNetActivity, "연결이 끊어졌습니다. 이전 화면으로 돌아갑니다.", Toast.LENGTH_SHORT).show()
                            finish() // 이전 화면으로 돌아가기
                            return@withContext
                        }
                        break
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@QuizWithNetActivity, "연결이 끊어졌습니다. 이전 화면으로 돌아갑니다.", Toast.LENGTH_SHORT).show()
                    finish() // 이전 화면으로 돌아가기
                }
            }
        }
    }

    @Composable
    fun QuizScreen(cameraManager: CameraManager, receivedBitmap: Bitmap?) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top half: Received image
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .height(IntrinsicSize.Min)
            ) {
                if (receivedBitmap != null) {
                    Image(
                        bitmap = receivedBitmap.asImageBitmap(),
                        contentDescription = "Received Image",
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer(scaleX = -1f,rotationZ = 90f )
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.sample_image),
                        contentDescription = "No image received",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom half: Camera preview
            AndroidView(
                factory = { cameraManager.previewView },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Button(
                onClick = {
                    scope.launch {
                        try{
                            socketClient.sendString("Quiz")
                            withContext(Dispatchers.Main) {
                                isButtonEnabled = false // Disable button after sending Quiz
                            }
                        }catch(e:Exception){
                            Log.e("QWNActivity","Error sending Quiz")
                        }
                    }
                },
                enabled = isButtonEnabled, // Bind button enabled state
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = "출제")
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        socketClient.close()
    }
}

//fun intToBytes(value: Int): ByteArray {
//    return byteArrayOf(
//        (value shr 24 and 0xFF).toByte(),
//        (value shr 16 and 0xFF).toByte(),
//        (value shr 8 and 0xFF).toByte(),
//        (value and 0xFF).toByte()
//    )
//}
//
//// 헤더를 생성하는 함수
//fun createHeader(size: Int, width: Int, height: Int): ByteArray {
//    val header = ByteArray(12)
//    System.arraycopy(intToBytes(size), 0, header, 0, 4)
//    System.arraycopy(intToBytes(width), 0, header, 4, 4)
//    System.arraycopy(intToBytes(height), 0, header, 8, 4)
//    return header
//}
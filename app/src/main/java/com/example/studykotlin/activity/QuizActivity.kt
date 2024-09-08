package com.example.studykotlin.activity

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.studykotlin.R
import com.example.studykotlin.SocketClient
import com.example.studykotlin.ui.theme.ColorModeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class QuizActivity : ComponentActivity() {
    private lateinit var socketClient: SocketClient
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        socketClient = SocketClient(getString(R.string.server_ip), resources.getInteger(R.integer.quiz_answer_port), "Quiz")

        scope.launch {
            try {
                socketClient.connect()
                //메인 스레드에서 setContent 호출해야 함.
                withContext(Dispatchers.Main){
                    setContent {
                        ColorModeTheme {
                            ImageReceiverScreen(true)
                        }
                    }
                }
                
            } catch (e: IOException) {
                Toast.makeText(this@QuizActivity, "연결할 수 없음", Toast.LENGTH_SHORT).show()
                delay(Toast.LENGTH_SHORT.toLong())
                Log.e("QuizActivity", "Error connecting to server", e)
                finish()
            }
        }
    }

    @Composable
    fun ImageReceiverScreen(isConnected: Boolean) {
        var receivedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

        LaunchedEffect(Unit) {
            if (isConnected) {
                receiveImage { bitmap ->
                    receivedBitmap = bitmap
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (receivedBitmap != null) {
                // 수신된 이미지를 화면에 표시
                Image(
                    bitmap = receivedBitmap!!.asImageBitmap(),
                    contentDescription = "Received Image",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 기본 이미지를 화면에 표시
                Image(
                    painter = painterResource(id = R.drawable.sample_image),
                    contentDescription = "No image received",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun receiveImage(onImageReceived: (android.graphics.Bitmap) -> Unit) {
        scope.launch {
            try {
                while (true) {
                    val imageData = socketClient.receiveImageFromServer()
                    Log.d("QuizActivity","Receive Image SUCCESS")
                    withContext(Dispatchers.Main) {
                        if(imageData!=null) {
                            onImageReceived(imageData)
                        }else{
                            Log.e("QuizActivity","No image Recieved")
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("QuizActivity", "Error receiving data", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socketClient.close()
    }
}

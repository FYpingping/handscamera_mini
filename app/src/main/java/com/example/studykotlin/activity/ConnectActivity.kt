package com.example.studykotlin.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.studykotlin.R
import com.example.studykotlin.ui.theme.ColorModeTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.IOException
import java.io.PrintWriter
import java.net.Socket
import java.net.UnknownHostException

sealed class Role {
    object Quiz : Role()
    object Answer : Role()
    override fun toString(): String {
        return when (this) {
            Quiz -> "출제자"
            Answer -> "응답자"
        }
    }
}

class SocketViewModelFactory(
    private val serverIp: String,
    private val serverPort: Int
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SocketViewModel::class.java)) {
            return SocketViewModel(serverIp, serverPort) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SocketViewModel(private val serverIp: String, private val serverPort:Int) : ViewModel() {
    private val _role = MutableStateFlow<Role?>(null)
    val role: StateFlow<Role?> = _role

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    private val _startSignalReceived = MutableStateFlow(false)
    val startSignalReceived: StateFlow<Boolean> = _startSignalReceived

    private val _clientCount = MutableStateFlow(0)
    val clientCount: StateFlow<Int> = _clientCount

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    init {
        Log.d("Log","VIEW MODEL ON")
        // Initialize the connection
        viewModelScope.launch(Dispatchers.IO) {
            try {
                socket = Socket(serverIp, serverPort) // 서버 IP와 포트 설정
                writer = socket?.getOutputStream()?.let { PrintWriter(it, true) }
                reader = socket?.getInputStream()?.bufferedReader()
                // Listen for role and start signal
                listenForMessages()
            } catch (e: Exception) {
                Log.d("Log","ERROR INITAILIZING SOCKET")
                e.printStackTrace()
            }
        }
    }

    private suspend fun listenForMessages() {
        Log.d("Socket", "TRY LISTEN")
        withContext(Dispatchers.IO) {
            try {
                val reader = socket?.getInputStream()?.bufferedReader()
                reader?.let {
                    while (true) {
                        val line = it.readLine() ?: break
                        Log.d("Socket", "Received line: $line") // 수신한 전체 라인 출력
                        when {
                            line.startsWith("ROLE:") -> {
                                _role.value = when (line.substringAfter("ROLE:")) {
                                    "Quiz" -> Role.Quiz
                                    "Answer" -> Role.Answer
                                    else -> null
                                }
                                Log.d("Socket", "Role updated: ${_role.value}") // ROLE 업데이트 확인
                            }
                            line.startsWith("CLIENTS:") -> {
                                val clientsInfo = line.substringAfter("CLIENTS:")
                                _clientCount.value = clientsInfo.substringBefore('/').toInt()
                                Log.d("Socket", "Client count updated: ${_clientCount.value}") // CLIENTS 업데이트 확인
                            }
                            line.startsWith("STATUS:") -> {
                                _statusMessage.value = line.substringAfter("STATUS:")
                                Log.d("Socket", "Status message updated: ${_statusMessage.value}") // STATUS 업데이트 확인
                            }
                            line.startsWith("START:") -> {
                                Log.d("Socket", "Start signal received") // START 신호 확인
                                _startSignalReceived.value = true
                                break
                            }
                        }
                    }
                } ?: Log.e("ConnectActivity", "BufferedReader is null")
            } catch (e: Exception) {
                Log.d("ConnectActivity", "NOT CONNECTED")
            } finally {
                socket?.close()
                Log.d("ConnectActivity", "Socket Closed in Finally")
            }
        }
    }

    fun sendReadySignal() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (writer?.checkError() == false) {
                    writer?.println("READY")
                    _isReady.value = true
                    Log.d("Socket", "READY Signal Sent")
                } else {
                    Log.e("Socket", "Writer error, reconnecting...")
                }
            } catch (e: Exception) {
                Log.e("Socket", "Error sending ready signal", e)
            }
        }
        if(socket?.isConnected ==true){
            Log.d("Socket","Still Connected")
        }else{
            Log.d("Socket","DISCONNECTED AFTER SEND")
        }
    }
    fun closeSocket(){
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writer?.close()
                reader?.close()
                socket?.close()
            } catch (e: IOException) {
                Log.e("Connect Activity","ERROR OCCURED")
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("Socket", "Socket Closed in Clear")
    }
}

@Composable
fun AppScreen(viewModel: SocketViewModel) {
    ColorModeTheme{
        val role by viewModel.role.collectAsState()
        val isReady by viewModel.isReady.collectAsState()
        val startSignalReceived by viewModel.startSignalReceived.collectAsState()
        val clientCount by viewModel.clientCount.collectAsState()
        val statusMessage by viewModel.statusMessage.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp), // 전체 레이아웃에 패딩 추가
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Role을 박스 안에 표시
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) // 박스 배경색 설정
                    .padding(24.dp), // 내부 여백 추가
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = role.toString(),
                    style = TextStyle(
                        fontSize = 32.sp, // 큰 글씨로 설정
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary // Role 텍스트 색상 설정
                    )
                )
            }

            // 두 번째 큰 박스 안에 $clientCount/2와 준비 버튼 배치
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface) // 박스 배경색 설정
                    .padding(24.dp), // 내부 여백 추가
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // $clientCount/2를 큰 숫자로 표시
                    Text(
                        text = "$clientCount/2",
                        fontSize = 48.sp, // 큰 숫자로 설정
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp)) // 간격 추가

                    Button(
                        onClick = { viewModel.sendReadySignal() },
                        enabled = statusMessage != null
                    ) {
                        Text("준비")
                    }

                    if (isReady) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "상대 준비 대기중...",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 상태 메시지를 맨 아래에 표시
            statusMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = it, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

}

class ConnectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: SocketViewModel = ViewModelProvider(this,
                SocketViewModelFactory(getString(R.string.server_ip), resources.getInteger(R.integer.select_port)))
                .get(SocketViewModel::class.java)
            AppScreen(viewModel)

            val startSignalReceived by viewModel.startSignalReceived.collectAsState()
            val role by viewModel.role.collectAsState()
            LaunchedEffect(startSignalReceived, role) {
                if (startSignalReceived) {
                    viewModel.closeSocket()
                    val intent = when (role) {
                        is Role.Quiz -> Intent(this@ConnectActivity, QuizWithNetActivity::class.java)
                        is Role.Answer -> Intent(this@ConnectActivity, AnswerWithNetActivity::class.java)
                        else -> null
                    }
                    intent?.let {
                        startActivity(it)
                        finish() // Optional: finish current activity if no longer needed
                    }
                }
            }
        }
    }
}
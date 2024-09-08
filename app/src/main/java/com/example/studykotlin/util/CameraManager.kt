package com.example.studykotlin.util

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.core.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.studykotlin.SocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService

class CameraManager(
    private val socketClient: SocketClient,
    val previewView: PreviewView,
    private val coroutineScope: CoroutineScope,
    private val cameraExecutor: ExecutorService,
    private val frameIntervalMs: Long = 100L
) {
    fun startCamera(context: Context) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
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
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, FrameAnalyzer(socketClient, frameIntervalMs))
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider.bindToLifecycle(
                    context as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Failed to get CameraProvider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private class FrameAnalyzer(
        private val socketClient: SocketClient,
        private val frameIntervalMs: Long
    ) : ImageAnalysis.Analyzer {

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
                    latestBytes?.let { dataToSend ->
                        try {
                            socketClient.sendData(dataToSend)
                            Log.d("SocketClient", "Data Sent")
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
            // 전체 데이터 크기: [8+이미지 크기](4바이트) + 타입(1바이트)
            //                 + width(4바이트) + height(4바이트) + 이미지 데이터
            val dataSize = 4 + 1 + 4 + 4 + nv21.size
            val header = ByteBuffer.allocate(4)
                .putInt(dataSize - 5)  // 데이터의 총 크기에서 헤더 5바이트(4바이트 + 1바이트)를 뺀 값
            val type = 0x01.toByte()
            val widthBytes = ByteBuffer.allocate(4).putInt(image.width).array()  // 너비를 4바이트로 변환
            val heightBytes = ByteBuffer.allocate(4).putInt(image.height).array()  // 높이를 4바이트로 변환

            latestBytes = ByteArray(header.array().size + 1 + widthBytes.size + heightBytes.size + nv21.size)
            System.arraycopy(header.array(), 0, latestBytes, 0, header.array().size)
            latestBytes!![header.array().size] = type
            System.arraycopy(widthBytes, 0, latestBytes, header.array().size + 1, widthBytes.size)
            System.arraycopy(heightBytes, 0, latestBytes, header.array().size + 1 + widthBytes.size, heightBytes.size)
            System.arraycopy(nv21, 0, latestBytes, header.array().size + 1 + widthBytes.size + heightBytes.size, nv21.size)

            image.close()
        }
    }
}
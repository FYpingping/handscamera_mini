package com.example.studykotlin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.BufferedReader
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Scanner
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SocketClient(private val serverAddress: String, private val serverPort: Int, private val role: String) {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    fun connect() {
        socket = Socket(serverAddress, serverPort)
        outputStream = socket?.getOutputStream()
        writer = PrintWriter(socket!!.getOutputStream(), true)
        reader = BufferedReader(InputStreamReader(socket?.getInputStream()))
        writer?.println(role)
        waitForServerReady()
    }
    fun close() {
        try {
            writer?.close()
            reader?.close()
            outputStream?.close()
            socket?.close()
            Log.d("SocketClient", "Socket closed successfully")
        } catch (e: IOException) {
            Log.e("SocketClient", "Error closing socket", e)
        }
    }

    @Throws(IOException::class)
    fun sendData(data: ByteArray) {
        socket?.getOutputStream()?.let { outputStream ->
            outputStream.write(data)
            outputStream.flush()
        }
    }
    fun sendString(string: String){
        val stringBytes = string.toByteArray(Charset.forName("UTF-8"))

        // Prepare header
        val length = stringBytes.size // 4 bytes for length + 1 byte for type + data size
        val header = ByteBuffer.allocate(5) // 4 bytes for length + 1 byte for type
        header.putInt(length) // Length field
        header.put(0x02.toByte()) // Type field (0x02 for string)
        // Send header+string
        sendData(header.array()+stringBytes)
    }
    private fun waitForServerReady() {
        // 서버에서 전송된 "READY" 메시지를 수신 대기
        Log.d("SocketClient","MSG RECEIVE WAIT!")
        val response = reader?.readLine()
        if (response == "READY") {
            Log.d("SocketClient","READY TO START!")
        }
    }

    suspend fun receiveDataFromServer(): Any? {
        return suspendCoroutine { continuation ->
            try {
                if (socket == null || socket!!.isClosed) {
                    continuation.resumeWith(Result.success(null))
                    return@suspendCoroutine
                }
                val inputStream: InputStream? = socket?.getInputStream()
                if (inputStream == null) {
                    continuation.resume(null)
                    return@suspendCoroutine
                }

                // Read header: first 4 bytes for data length, next byte for data type
                val header = inputStream.readBytes(5)
                val dataLength = ByteBuffer.wrap(header, 0, 4).int
                val dataType = header[4].toInt()

                when (dataType) {
                    0x01 -> { // Image data
                        val width = inputStream.readInt()
                        val height = inputStream.readInt()

                        if (dataLength <= 0 || width <= 0 || height <= 0) {
                            Log.e("SocketClient", "Invalid image dimensions: size=$dataLength, width=$width, height=$height")
                            continuation.resume(null)
                            return@suspendCoroutine
                        }

                        val imageBytes = inputStream.readBytes(dataLength - 8) // Subtract 8 bytes for width and height
                        val imageBitmap = nv21ToImageBitmap(imageBytes, width, height)
                        Log.d("SocketClient", "Image Received")
                        continuation.resume(imageBitmap)
                    }
                    0x02 -> { // String data
                        val stringBytes = inputStream.readBytes(dataLength)
                        val message = String(stringBytes, Charsets.UTF_8)
                        Log.d("SocketClient", "String Received: $message")
                        continuation.resume(message)
                    }
                    else -> {
                        Log.e("SocketClient", "Unknown data type: $dataType")
                        continuation.resume(null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                continuation.resume(null)
            }
        }
    }

    suspend fun receiveImageFromServer(): Bitmap? {
        return suspendCoroutine { continuation ->
            try {
                // Ensure that the socket is not null
                val inputStream: InputStream? = socket?.getInputStream()

                if (inputStream == null) {
                    continuation.resume(null)
                    return@suspendCoroutine
                }

                // Read image dimensions
                val imageSize = inputStream.readInt()
                val width = inputStream.readInt()
                val height = inputStream.readInt()

                if (imageSize == null || width == null || height == null || imageSize <= 0 || width <= 0 || height <= 0) {
                    Log.e("SocketClient", "Invalid image dimensions: size=$imageSize, width=$width, height=$height")
                    continuation.resume(null)
                    return@suspendCoroutine
                }

                Log.d("SocketClient", "imageSize: $imageSize")
                Log.d("SocketClient", "Width: $width, height:$height")

                val imageBytes = inputStream.readBytes(imageSize)
                // Decode the image
                val imageBitmap = nv21ToImageBitmap(imageBytes, width, height)
                Log.d("SocketClient","Image Receive")

                continuation.resume(imageBitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                continuation.resume(null)
            }
        }
    }

    companion object {
        private const val TAG = "SocketClient"
    }
}
fun InputStream.readInt(): Int {
    val buffer = ByteArray(4)
    read(buffer)
    return ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN).int
}

fun InputStream.readBytes(n: Int): ByteArray {
    val outputStream = ByteArrayOutputStream()
    val bufferSize = 8192 // 버퍼 크기, 적절한 값으로 조정할 수 있음
    val buffer = ByteArray(bufferSize)
    var totalBytesRead = 0

    while (totalBytesRead < n) {
        val bytesToRead = minOf(bufferSize, n - totalBytesRead)
        val bytesRead = read(buffer, 0, bytesToRead)

        if (bytesRead == -1) {
            // End of stream reached before the required number of bytes was read
            throw IOException("End of stream reached before reading $n bytes")
        }

        outputStream.write(buffer, 0, bytesRead)
        totalBytesRead += bytesRead
    }
    return outputStream.toByteArray()
}

fun ByteArray.toInt(): Int {
    return (this[0].toInt() shl 24) or
            (this[1].toInt() shl 16) or
            (this[2].toInt() shl 8) or
            (this[3].toInt())
}

private fun nv21ToImageBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap {
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val jpegData = out.toByteArray()

    val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
    return bitmap
}

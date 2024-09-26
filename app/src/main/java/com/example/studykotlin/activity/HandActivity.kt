package com.example.studykotlin.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.studykotlin.util.GestureRecognition
import com.example.studykotlin.util.HandLandmarkerHelper
import com.example.studykotlin.util.calcAngles
import com.google.mediapipe.tasks.vision.core.RunningMode

private const val CAMERA_PERMISSION_CODE = 1001

val gestureLabels: Map<Int, String> = mapOf(
    1 to "Middle Finger",
    2 to "Heart",
    3 to "Heart with Two Hands",
    4 to "Thumb Up",
    5 to "V",
    6 to "OK",
    7 to "Call"
)

class HandActivity : ComponentActivity() {

    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private lateinit var gestureRecognition: GestureRecognition

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gestureRecognition = GestureRecognition(this)
        // Initialize the HandLandmarkerHelper
        handLandmarkerHelper = HandLandmarkerHelper(
            context = this,
            runningMode = RunningMode.LIVE_STREAM,
            handLandmarkerHelperListener = object : HandLandmarkerHelper.LandmarkerListener {
                override fun onError(error: String, errorCode: Int) {
                    Log.e("HandActivity", "Hand Landmarker error: $error")
                }

                override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
                    val inferenceTime = resultBundle.inferenceTime
                    val height = resultBundle.inputImageHeight
                    val width = resultBundle.inputImageWidth
                    Log.d("HandActivity","time: $inferenceTime")
                    for (result in resultBundle.results) {
                        if (result.landmarks().isNotEmpty()) {
                            val angles = calcAngles(result)
                            val predictedIndex = gestureRecognition.predict(angles)

                            // predictedIndex가 유효한 경우에만 로그 출력
                            if (predictedIndex >= 0 && predictedIndex < gestureLabels.size) {
                                Log.d("HandActivity", "Predicted index: " + gestureLabels[predictedIndex])
                            }
                        } else {
                            Log.d("HandActivity", "No hand detected")
                        }
                    }
                }
            }
        )

        // Check camera permission and request if necessary
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            setContent {
                CameraPreview(handLandmarkerHelper)
            }
        }
    }

    // Handle permission result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setContent {
                CameraPreview(handLandmarkerHelper)
            }
        }
    }
}

// Composable function to show the camera preview
@Composable
fun CameraPreview(
    handLandmarkerHelper: HandLandmarkerHelper
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    AndroidView({ previewView }) { previewView ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            // Image analysis to feed frames to the HandLandmarker
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(360, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                handLandmarkerHelper.detectLiveStream(
                    imageProxy = imageProxy,
                    isFrontCamera = true
                )
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraPreview", "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}

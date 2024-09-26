package com.example.studykotlin.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.ByteArrayOutputStream
import kotlin.math.*

fun imageToNV21(image: ImageProxy): ByteArray {
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

    return nv21
}

fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap {
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)!!
}

fun calcAngles(result: HandLandmarkerResult): FloatArray {
    val joint = Array(21) { FloatArray(3) }
    // Calculate angles using dot product and arccos
    val angleIndices1 = arrayOf(0, 1, 2, 4, 5, 6, 8, 9, 10, 12, 13, 14, 16, 17, 18)
    val angleIndices2 = arrayOf(1, 2, 3, 5, 6, 7, 9, 10, 11, 13, 14, 15, 17, 18, 19)
    val angles = FloatArray(angleIndices1.size)

    // Compute vectors between specific joints (v1 and v2)
    val indices1 = arrayOf(0, 1, 2, 3, 0, 5, 6, 7, 0, 9, 10, 11, 0, 13, 14, 15, 0, 17, 18, 19)
    val indices2 = arrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)

    for (res in result.landmarks()) {
        for (i in res.indices) {
            // Store x, y, z coordinates for each landmark in the joint array
            joint[i][0] = res[i].x()
            joint[i][1] = res[i].y()
            joint[i][2] = res[i].z()
        }

        val vectors = Array(indices1.size) { FloatArray(3) }

        for (i in indices1.indices) {
            // Calculate v = v2 - v1
            for (j in 0..2) {
                vectors[i][j] = joint[indices2[i]][j] - joint[indices1[i]][j]
            }

            // Normalize the vectors
            val norm = sqrt(vectors[i].map { it * it }.sum())
            for (j in 0..2) {
                vectors[i][j] /= norm
            }
        }

        for (i in angleIndices1.indices) {
            val dotProduct =
                (0..2).map { j -> vectors[angleIndices1[i]][j] * vectors[angleIndices2[i]][j] }
                    .sum()
            angles[i] = Math.toDegrees(acos(dotProduct).toDouble()).toFloat()
        }
    }
    return angles
}
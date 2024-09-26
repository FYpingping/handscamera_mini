package com.example.studykotlin.util

import java.io.BufferedReader
import android.content.Context
import com.example.studykotlin.R
import java.io.FileReader
import smile.classification.KNN
import java.io.InputStreamReader

class GestureRecognition(private val context: Context, private val numNeighbors: Int = 3) {

    private lateinit var trainingData: Array<DoubleArray>
    private lateinit var labels: IntArray
    private lateinit var knnModel: KNN<DoubleArray>

    init{
        loadTrainingData()
        initialize()
    }

    // Load training data from the CSV file
    private fun loadTrainingData() {
        val features = ArrayList<DoubleArray>()
        val labelsList = ArrayList<Int>()

        // Read the CSV file from assets
        context.assets.open("gesture_train.csv").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { br ->
                val header = br.readLine() // Read header if present
                var line: String?

                while (br.readLine().also { line = it } != null) {
                    val values = line!!.split(",").map { it.trim() }
                    val feature = DoubleArray(values.size - 1) // Last column is the label

                    for (i in 0 until values.size - 1) {
                        feature[i] = values[i].toDouble() // Convert feature strings to double
                    }

                    // Parse the label safely as a double and then convert to int
                    val label = values.last().toDouble().toInt()
                    features.add(feature)
                    labelsList.add(label)
                }
            }
        }

        trainingData = features.toTypedArray()
        labels = labelsList.toIntArray()
    }

    // Initialize the KNN model
    fun initialize() {
        loadTrainingData()

        // Convert trainingData to a 2D Double array for the KNN model
        val numFeatures = trainingData[0].size
        val numSamples = trainingData.size
        val trainingData2D = Array(numSamples) { DoubleArray(numFeatures) }

        for (i in trainingData.indices) {
            trainingData2D[i] = trainingData[i]
        }

        // Fit the KNN model
        knnModel = KNN.fit(trainingData2D, labels, numNeighbors)
    }

    // Predict the gesture index based on input angles
    fun predict(angles: FloatArray): Int {
        val inputData = arrayOf(angles.map { it.toDouble() }.toDoubleArray())
        val predictions = knnModel.predict(inputData)
        return predictions[0]
    }
}



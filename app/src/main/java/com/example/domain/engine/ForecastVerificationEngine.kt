package com.example.domain.engine

import com.example.domain.model.ModelVerificationMetrics
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class VerificationBenchmarkRegion(
    val id: String,
    val name: String,
    val category: String,
    val latitude: Double,
    val longitude: Double
)

object ForecastVerificationEngine {

    val indiaAndGlobalBenchmarks: List<VerificationBenchmarkRegion> = listOf(
        VerificationBenchmarkRegion(
            id = "kolkata",
            name = "Kolkata, West Bengal",
            category = "Gangetic Plains · Urban Monsoon",
            latitude = 22.5726,
            longitude = 88.3639
        ),
        VerificationBenchmarkRegion(
            id = "guwahati",
            name = "Guwahati, Assam",
            category = "Northeastern India · Heavy Orographic Rain",
            latitude = 26.1445,
            longitude = 91.7362
        ),
        VerificationBenchmarkRegion(
            id = "darjeeling",
            name = "Darjeeling, West Bengal",
            category = "Eastern Himalayan Region · High Relief",
            latitude = 27.0410,
            longitude = 88.2663
        ),
        VerificationBenchmarkRegion(
            id = "bhubaneswar",
            name = "Bhubaneswar, Odisha",
            category = "Eastern Coastal · Bay of Bengal Belt",
            latitude = 20.2961,
            longitude = 85.8245
        ),
        VerificationBenchmarkRegion(
            id = "visakhapatnam",
            name = "Visakhapatnam, Andhra Pradesh",
            category = "Cyclone-Prone Coastal Region",
            latitude = 17.6868,
            longitude = 83.2185
        ),
        VerificationBenchmarkRegion(
            id = "mumbai",
            name = "Mumbai, Maharashtra",
            category = "Western Coastal Monsoon",
            latitude = 19.0760,
            longitude = 72.8777
        )
    )

    /**
     * Empirically verifies forecast arrays against reanalysis/observation arrays aligned by hour.
     * Computes:
     * - MAE (Mean Absolute Error, °C)
     * - RMSE (Root Mean Square Error, °C)
     * - Temperature Bias (Mean Error: Forecast - Observed, °C)
     * - Precipitation Accuracy (%) at >= 0.2 mm/h wet-hour threshold
     * - POD (Probability of Detection = Hits / (Hits + Misses))
     * - FAR (False Alarm Ratio = False Alarms / (Hits + False Alarms))
     * - CSI (Critical Success Index = Hits / (Hits + Misses + False Alarms))
     * - Brier Score (mean squared probabilistic error for wet-hour detection)
     */
    fun computeMetrics(
        modelId: String,
        modelName: String,
        observedTemp: List<Double?>,
        forecastTemp: List<Double?>,
        observedPrecip: List<Double?>,
        forecastPrecip: List<Double?>
    ): ModelVerificationMetrics? {
        val count = minOf(observedTemp.size, forecastTemp.size)
        if (count == 0) return null

        var tempPairs = 0
        var sumAbsErr = 0.0
        var sumSqErr = 0.0
        var sumBias = 0.0

        for (i in 0 until count) {
            val obs = observedTemp[i]
            val fc = forecastTemp[i]
            if (obs != null && fc != null) {
                val err = fc - obs
                sumAbsErr += abs(err)
                sumSqErr += err.pow(2.0)
                sumBias += err
                tempPairs++
            }
        }
        if (tempPairs == 0) return null

        val pCount = minOf(observedPrecip.size, forecastPrecip.size)
        val wetThresholdMm = 0.2
        var hits = 0
        var misses = 0
        var falseAlarms = 0
        var correctNegatives = 0
        var brierSum = 0.0
        var precipPairs = 0

        for (i in 0 until pCount) {
            val obsP = observedPrecip[i]
            val fcP = forecastPrecip[i]
            if (obsP != null && fcP != null) {
                val obsWet = obsP >= wetThresholdMm
                val fcWet = fcP >= wetThresholdMm
                when {
                    obsWet && fcWet -> hits++
                    obsWet && !fcWet -> misses++
                    !obsWet && fcWet -> falseAlarms++
                    else -> correctNegatives++
                }
                // Calibrated pseudo-probability proxy from deterministic rainfall rate (capped 0..1)
                val fcProb = (fcP / 2.0).coerceIn(0.0, 1.0)
                val obsBinary = if (obsWet) 1.0 else 0.0
                brierSum += (fcProb - obsBinary).pow(2.0)
                precipPairs++
            }
        }

        val precipAccuracy = if (precipPairs > 0) {
            ((hits + correctNegatives).toDouble() / precipPairs) * 100.0
        } else 0.0

        val pod = if (hits + misses > 0) hits.toDouble() / (hits + misses) else 1.0
        val far = if (hits + falseAlarms > 0) falseAlarms.toDouble() / (hits + falseAlarms) else 0.0
        val csi = if (hits + misses + falseAlarms > 0) {
            hits.toDouble() / (hits + misses + falseAlarms)
        } else 1.0
        val brier = if (precipPairs > 0) brierSum / precipPairs else 0.0

        return ModelVerificationMetrics(
            modelId = modelId,
            modelName = modelName,
            sampleHours = tempPairs,
            tempMaeC = sumAbsErr / tempPairs,
            tempRmseC = sqrt(sumSqErr / tempPairs),
            tempBiasC = sumBias / tempPairs,
            precipAccuracyPct = precipAccuracy,
            pod = pod,
            far = far,
            csi = csi,
            brierScore = brier
        )
    }
}

package com.example.test_camera

data class InferenceResult(
    val classification: Map<String, Float>?,   // Classification labels and values
    val objectDetections: List<BoundingBox>?,  // Object detection results
    val visualAnomalyGridCells: List<BoundingBox>?, //TODO not used ? Visual anomaly grid
    val anomalyResult: Map<String, Float>?, // Anomaly values
    val timing: Timing  // Timing information
)
package bg.getsovd.vehicle_detection.model

enum class SpeedUnit(val symbol: String) {
    KPH("km/h"),
    MPH("mph");

    // Optional: conversion logic
    fun convertFromKph(kph: Float): Float {
        return when (this) {
            KPH -> kph
            MPH -> kph * 0.621371f
        }
    }
}
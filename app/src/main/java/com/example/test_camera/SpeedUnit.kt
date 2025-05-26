package com.example.test_camera

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
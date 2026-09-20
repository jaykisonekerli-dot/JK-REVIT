package br.com.jk.revitar

import android.graphics.Bitmap

data class ArMarker(
    val name: String,
    val bitmap: Bitmap,
    val physicalWidthMeters: Float,
    val modelOffsetX: Float,
    val modelOffsetZ: Float,
    val modelYawDegrees: Float
)

data class RevitProject(
    val name: String,
    val sheetNumber: String,
    val sheetName: String,
    val sheetFormat: String,
    val planName: String,
    val planScale: Int,
    val modelUrl: String,
    val modelScale: Float,
    val width: Double,
    val height: Double,
    val depth: Double,
    val markers: List<ArMarker>
)

enum class ViewerMode { PLANTA, MAQUETE }

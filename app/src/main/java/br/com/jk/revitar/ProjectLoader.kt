package br.com.jk.revitar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ProjectLoader {
    suspend fun load(manifestUrl: String): RevitProject = withContext(Dispatchers.IO) {
        val text = httpText(manifestUrl)
        val json = JSONObject(text)
        val protocol = json.optString("protocol")
        if (protocol != "revit-ar-sheet/2") {
            throw IllegalArgumentException("Este QR não é de uma prancha Revit AR compatível.")
        }

        val sheet = json.getJSONObject("sheet")
        val model = json.getJSONObject("model")
        val bounds = model.optJSONObject("boundsMeters") ?: JSONObject()
        val markerArray = json.getJSONArray("markers")
        val markers = ArrayList<ArMarker>(markerArray.length())

        for (i in 0 until markerArray.length()) {
            val m = markerArray.getJSONObject(i)
            val offset = m.getJSONObject("modelOffsetMeters")
            val bitmap = httpBitmap(m.getString("imageUrl"))
            markers += ArMarker(
                name = m.getString("name"),
                bitmap = bitmap,
                physicalWidthMeters = m.getDouble("physicalWidthMeters").toFloat(),
                modelOffsetX = offset.getDouble("x").toFloat(),
                modelOffsetZ = offset.getDouble("z").toFloat(),
                modelYawDegrees = m.optDouble("modelYawDegrees", 0.0).toFloat()
            )
        }

        if (markers.isEmpty()) {
            throw IllegalStateException("O projeto não possui marcadores AR.")
        }

        RevitProject(
            name = json.optString("projectName", "Projeto Revit"),
            sheetNumber = sheet.optString("number", ""),
            sheetName = sheet.optString("name", ""),
            sheetFormat = sheet.optString("format", ""),
            planName = sheet.optString("planView", "Planta"),
            planScale = sheet.optInt("scale", 50),
            modelUrl = model.getString("url"),
            modelScale = model.optDouble("sheetScaleFactor", 0.02).toFloat(),
            width = bounds.optDouble("width", 0.0),
            height = bounds.optDouble("height", 0.0),
            depth = bounds.optDouble("depth", 0.0),
            markers = markers
        )
    }

    private fun httpText(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 45_000
        c.useCaches = false
        return try {
            if (c.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${c.responseCode} ao carregar manifest.json")
            }
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun httpBitmap(url: String): Bitmap {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 30_000
        c.useCaches = false
        return try {
            if (c.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${c.responseCode} ao carregar marcador")
            }
            BitmapFactory.decodeStream(c.inputStream)
                ?: throw IllegalStateException("Imagem de marcador inválida")
        } finally {
            c.disconnect()
        }
    }
}

package com.example.schachhelfer

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Gefundenes Brett in Bildschirm-Pixeln. (x, y) = obere linke Ecke, square = Feldgröße. */
data class BoardRect(val x: Float, val y: Float, val square: Float, val confidence: Float) {
    val size get() = square * 8
    /** file 0..7 = Spalte von links, row 0..7 = Reihe von oben (im Screenshot). */
    fun cellLeft(file: Int) = x + file * square
    fun cellTop(row: Int) = y + row * square
}

/**
 * Klassische Brett-Erkennung ohne ML.
 *
 * Idee: In einem Schachbrett unterscheiden sich benachbarte Felder immer in der Farbe.
 * Jede Feldgrenze erzeugt deshalb eine durchgehende Kante über die ganze Brettbreite/-höhe.
 * Wir zählen Kanten pro Spalte/Zeile und suchen dann das Raster aus 7 gleichmäßig
 * verteilten inneren Linien mit der größten Summe. Die Außenkanten bleiben bewusst
 * außen vor, weil sie bei hellem Hintergrund oft fehlen.
 */
object BoardDetector {
    private const val EDGE_THRESHOLD = 6      // Helligkeitsunterschied (0..255), klein genug für helle/weiße Felder
    private const val MIN_CONFIDENCE = 0.4f

    fun detect(bmp: Bitmap): BoardRect? {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val lum = IntArray(w * h) {
            val c = px[it]
            (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        }

        // 1) Senkrechte Kanten pro Spalte zählen -> x-Position und Feldgröße
        val colEdges = IntArray(w)
        for (y in 0 until h) {
            val row = y * w
            for (x in 1 until w) {
                if (abs(lum[row + x] - lum[row + x - 1]) > EDGE_THRESHOLD) colEdges[x]++
            }
        }
        val colGrid = bestGrid(colEdges, w) ?: return null
        val s = colGrid.square
        val x0 = colGrid.start

        // 2) Waagerechte Kanten, nur innerhalb der gefundenen Spalten -> y-Position
        val xFrom = max(0, x0.roundToInt())
        val xTo = minOf(w, (x0 + 8 * s).roundToInt())
        val rowEdges = IntArray(h)
        for (y in 1 until h) {
            val row = y * w
            val prev = (y - 1) * w
            for (x in xFrom until xTo) {
                if (abs(lum[row + x] - lum[prev + x]) > EDGE_THRESHOLD) rowEdges[y]++
            }
        }
        val rowGrid = bestGrid(rowEdges, h, fixedSquare = s) ?: return null

        val conf = minOf(colGrid.confidence, rowGrid.confidence)
        if (conf < MIN_CONFIDENCE) return null
        if (x0 < -1 || x0 + 8 * s > w + 1 || rowGrid.start + 8 * s > h + 1) return null
        return BoardRect(x0, rowGrid.start, s, conf)
    }

    private class Grid(val start: Float, val square: Float, val confidence: Float)

    /**
     * Sucht Startposition und Feldgröße, sodass die 7 inneren Rasterlinien
     * möglichst viele Kanten sammeln. Mit fixedSquare wird nur noch die Position gesucht.
     */
    private fun bestGrid(profile: IntArray, len: Int, fixedSquare: Float? = null): Grid? {
        val minS = fixedSquare ?: 24f
        val maxS = fixedSquare ?: (len / 8f)
        var bestScore = 0L
        var bestStart = 0f
        var bestS = 0f

        var s = minS
        while (s <= maxS + 0.001f) {
            // p0 = Position der ersten inneren Linie (k = 1)
            var p0 = s.roundToInt()
            val pMax = (len - 1 - 6 * s).toInt()
            while (p0 <= pMax) {
                var score = 0L
                for (k in 0..6) {
                    val i = (p0 + k * s).roundToInt()
                    score += peak(profile, i)
                }
                if (score > bestScore) {
                    bestScore = score
                    bestStart = p0 - s
                    bestS = s
                }
                p0++
            }
            s += 0.25f
        }
        if (bestS == 0f) return null
        // Erwartung: jede innere Linie hat ca. 8 * s Kanten (Brettbreite bzw. -höhe)
        val expected = 7f * 8f * bestS
        return Grid(bestStart, bestS, bestScore / expected)
    }

    /** Maximum in ±1 Pixel, weil Feldgrößen selten ganzzahlig sind. */
    private fun peak(p: IntArray, i: Int): Int {
        var m = 0
        for (j in i - 1..i + 1) if (j in p.indices && p[j] > m) m = p[j]
        return m
    }
}

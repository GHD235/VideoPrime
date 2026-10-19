package org.schabi.newpipe.videoprime

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

/**
 * VideoPrime: YouTube Music songs come with a wide thumbnail: the square cover in the middle
 * and two bands of flat colour on the sides. Cropping it to the cover makes it look right in
 * the notification, the lock screen and the car. Ordinary video frames are left as they are.
 */
object CoverArt {
    /** Bands narrower than this share of the width are not worth cropping. */
    private const val MIN_BAND_SHARE = 0.1f

    /** How much the colour of a band may vary (standard deviation, 0-255) to count as flat. */
    private const val MAX_BAND_DEVIATION = 10.0

    private const val SAMPLES_PER_SIDE = 12

    @JvmStatic
    fun squareIfLetterboxed(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null
        val width = bitmap.width
        val height = bitmap.height
        val band = (width - height) / 2
        if (height <= 0 || band < width * MIN_BAND_SHARE) return bitmap
        return runCatching {
            // hardware bitmaps cannot be read pixel by pixel
            val readable = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                bitmap.config == Bitmap.Config.HARDWARE
            ) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            val bandEdge = (band * 0.8f).toInt().coerceAtLeast(1)
            if (isFlat(readable, 0, bandEdge) && isFlat(readable, width - bandEdge, width)) {
                Bitmap.createBitmap(readable, band, 0, height, height)
            } else {
                bitmap
            }
        }.getOrDefault(bitmap)
    }

    /**
     * The main colour of a cover, to tint the music player: the average of its colourful
     * pixels (neither grey nor too dark or too light), or of all of them for grey covers.
     */
    @JvmStatic
    fun mainColor(bitmap: Bitmap?): Int? {
        if (bitmap == null) return null
        return runCatching {
            val small = Bitmap.createScaledBitmap(
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                    bitmap.config == Bitmap.Config.HARDWARE
                ) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bitmap
                },
                COLOR_SAMPLE_SIZE,
                COLOR_SAMPLE_SIZE,
                true
            )
            val hsv = FloatArray(3)
            val all = ArrayList<Int>()
            val colourful = ArrayList<Int>()
            for (x in 0 until COLOR_SAMPLE_SIZE) {
                for (y in 0 until COLOR_SAMPLE_SIZE) {
                    val pixel = small.getPixel(x, y)
                    all.add(pixel)
                    Color.colorToHSV(pixel, hsv)
                    if (hsv[1] > 0.3f && hsv[2] in 0.25f..0.95f) colourful.add(pixel)
                }
            }
            val pixels = if (colourful.size >= all.size / 10) colourful else all
            Color.rgb(
                pixels.map(Color::red).average().toInt(),
                pixels.map(Color::green).average().toInt(),
                pixels.map(Color::blue).average().toInt()
            )
        }.getOrNull()
    }

    private const val COLOR_SAMPLE_SIZE = 32

    /** Whether the columns from [fromX] to [toX] are (nearly) a single colour. */
    private fun isFlat(bitmap: Bitmap, fromX: Int, toX: Int): Boolean {
        val values = ArrayList<Int>()
        for (i in 0 until SAMPLES_PER_SIDE) {
            for (j in 0 until SAMPLES_PER_SIDE) {
                val x = fromX + (toX - fromX - 1) * i / (SAMPLES_PER_SIDE - 1)
                val y = (bitmap.height - 1) * j / (SAMPLES_PER_SIDE - 1)
                values.add(bitmap.getPixel(x, y))
            }
        }
        return listOf(Color::red, Color::green, Color::blue).all { channel ->
            deviation(values.map(channel)) <= MAX_BAND_DEVIATION
        }
    }

    private fun deviation(values: List<Int>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}

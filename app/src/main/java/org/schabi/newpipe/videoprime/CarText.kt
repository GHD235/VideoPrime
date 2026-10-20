package org.schabi.newpipe.videoprime

/** VideoPrime: names and times as they read best on small screens (the car, the notification). */
object CarText {
    /** "Estopa - Topic" (YouTube's automatic music channels) is just "Estopa". */
    @JvmStatic
    fun artist(name: String?): String = name.orEmpty().removeSuffix(" - Topic")

    /** 3:43, 12:05 or 1:02:09: without the leading zero of "03:43". */
    @JvmStatic
    fun duration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return if (s >= 3600) {
            "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
        } else {
            "%d:%02d".format(s / 60, s % 60)
        }
    }
}

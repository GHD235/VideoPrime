package org.schabi.newpipe.videoprime

import org.junit.Assert.assertEquals
import org.junit.Test

class CarTextTest {
    @Test
    fun artistWithoutTopic() {
        assertEquals("Fito y Fitipaldis", CarText.artist("Fito y Fitipaldis - Topic"))
        assertEquals("Estopa", CarText.artist("Estopa"))
        assertEquals("", CarText.artist(null))
    }

    @Test
    fun durationWithoutLeadingZero() {
        assertEquals("3:43", CarText.duration(223))
        assertEquals("0:07", CarText.duration(7))
        assertEquals("12:05", CarText.duration(725))
        assertEquals("1:02:09", CarText.duration(3729))
    }
}

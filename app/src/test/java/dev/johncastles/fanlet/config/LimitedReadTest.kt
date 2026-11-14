package dev.johncastles.fanlet.config

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class LimitedReadTest {

    @Test
    fun smallStream_isReadCompletely() {
        val data = ByteArray(20_000) { it.toByte() }
        assertArrayEquals(data, ByteArrayInputStream(data).readAtMost(20_000))
    }

    @Test
    fun emptyStream_givesEmptyArray() {
        assertArrayEquals(ByteArray(0), ByteArrayInputStream(ByteArray(0)).readAtMost(10))
    }

    @Test
    fun tooLargeStream_givesNull() {
        assertNull(ByteArrayInputStream(ByteArray(20_001)).readAtMost(20_000))
    }
}

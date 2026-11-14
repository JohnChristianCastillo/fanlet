package dev.johncastles.fanlet.config

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads the whole stream, or returns null as soon as it turns out to be longer than
 * [maxBytes]. Protects imports from reading a huge file into memory.
 */
fun InputStream.readAtMost(maxBytes: Int): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        if (output.size() + count > maxBytes) return null
        output.write(buffer, 0, count)
    }
}

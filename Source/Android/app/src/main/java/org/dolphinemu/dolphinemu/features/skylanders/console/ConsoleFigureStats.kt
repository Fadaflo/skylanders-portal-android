// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.console

import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal data class ConsoleFigureStats(val gold: Int, val level: Int?, val seconds: Long)

/** Read-only snapshot. Never open a figure with a writable handle, including on errors. */
internal object ConsoleFigureStatsReader {
    private val characterTypes = setOf("Skylander", "Giant", "Mini", "Swapper", "TrapMaster")
    private val keySuffix = " Copyright (C) 2010 Activision. All Rights Reserved. ".toByteArray(Charsets.US_ASCII)
    // Cumulative XP, including the extra pools introduced after Spyro's Adventure.
    private val levels = longArrayOf(0, 1000, 2200, 3800, 6000, 9000, 13000, 18200,
        24800, 33000, 42700, 53900, 66600, 80800, 96500, 115735, 134435, 154635, 176335, 199535)

    fun supports(type: String) = type in characterTypes

    fun read(file: File, type: String): ConsoleFigureStats? = if (!supports(type)) null else
        runCatching {
            file.inputStream().use { stream ->
                val bytes = ByteArray(1024)
                var offset = 0
                while (offset < bytes.size) {
                    val count = stream.read(bytes, offset, bytes.size - offset)
                    if (count <= 0) return@use null
                    offset += count
                }
                parse(bytes)
            }
        }.getOrNull()

    fun parse(bytes: ByteArray): ConsoleFigureStats? {
        if (bytes.size != 1024) return null
        fun block(index: Int): ByteArray {
            val raw = bytes.copyOfRange(index * 16, index * 16 + 16)
            if (raw.all { it == 0.toByte() }) return raw
            val key = MessageDigest.getInstance("MD5").digest(bytes.copyOfRange(0, 32) +
                byteArrayOf(index.toByte()) + keySuffix)
            return Cipher.getInstance("AES/ECB/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
                doFinal(raw)
            }
        }
        fun main(index: Int): ByteArray? = block(index).takeIf {
            it.all { b -> b == 0.toByte() } || crc(it.copyOfRange(0, 14) + byteArrayOf(5, 0)) == unsigned(it, 14, 2).toInt()
        }
        fun extended(index: Int): ByteArray? {
            val data = block(index) + block(index + 1) + block(index + 3) + block(index + 4)
            return data.takeIf {
                it.all { b -> b == 0.toByte() } || crc(byteArrayOf(6, 1) + it.copyOfRange(2, 64)) == unsigned(it, 0, 2).toInt()
            }
        }
        val main = newest(main(8), main(36), 9) ?: return null
        val extra = newest(extended(17), extended(45), 2)
        val experience = extra?.let { unsigned(main, 0, 3) + unsigned(it, 3, 2) + unsigned(it, 8, 4) }
        return ConsoleFigureStats(unsigned(main, 3, 2).toInt(),
            experience?.let { xp -> levels.count { xp >= it }.coerceIn(1, 20) }, unsigned(main, 5, 4))
    }

    private fun unsigned(bytes: ByteArray, offset: Int, count: Int): Long =
        (0 until count).fold(0L) { value, index -> value or ((bytes[offset + index].toLong() and 255) shl (8 * index)) }

    private fun newest(a: ByteArray?, b: ByteArray?, sequence: Int): ByteArray? {
        if (a == null) return b
        if (b == null) return a
        if (a.all { it == 0.toByte() }) return b
        if (b.all { it == 0.toByte() }) return a
        val difference = (b[sequence].toInt() - a[sequence].toInt()) and 255
        return if (difference in 1..127) b else a
    }

    private fun crc(bytes: ByteArray): Int {
        var result = 0xffff
        for (byte in bytes) {
            result = result xor ((byte.toInt() and 255) shl 8)
            repeat(8) { result = (if (result and 0x8000 != 0) (result shl 1) xor 0x1021 else result shl 1) and 0xffff }
        }
        return result
    }
}

internal fun figurePlayTime(seconds: Long): String = when {
    seconds < 60 -> "$seconds Sek."
    seconds < 3600 -> "${seconds / 60} Min."
    else -> "${seconds / 3600} Std. ${(seconds % 3600) / 60} Min."
}

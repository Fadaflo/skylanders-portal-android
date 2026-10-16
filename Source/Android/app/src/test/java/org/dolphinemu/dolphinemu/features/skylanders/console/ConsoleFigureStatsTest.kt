package org.dolphinemu.dolphinemu.features.skylanders.console

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class ConsoleFigureStatsTest {
    private fun put(data: ByteArray, at: Int, value: Long, count: Int) {
        repeat(count) { data[at + it] = (value shr (8 * it)).toByte() }
    }

    private fun crc(data: ByteArray): Int {
        var value = 0xffff
        data.forEach { byte ->
            value = value xor ((byte.toInt() and 255) shl 8)
            repeat(8) { value = (if (value and 0x8000 != 0) (value shl 1) xor 0x1021 else value shl 1) and 0xffff }
        }
        return value
    }

    private fun encrypt(file: ByteArray, block: Int, data: ByteArray) {
        val material = file.copyOfRange(0, 32) + byteArrayOf(block.toByte()) +
            " Copyright (C) 2010 Activision. All Rights Reserved. ".toByteArray(Charsets.US_ASCII)
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(MessageDigest.getInstance("MD5").digest(material), "AES"))
        cipher.doFinal(data).copyInto(file, block * 16)
    }

    private fun main(file: ByteArray, block: Int, sequence: Int, gold: Int, xp: Int, seconds: Long) {
        val data = ByteArray(16)
        put(data, 0, xp.toLong(), 3); put(data, 3, gold.toLong(), 2); put(data, 5, seconds, 4)
        data[9] = sequence.toByte()
        put(data, 14, crc(data.copyOfRange(0, 14) + byteArrayOf(5, 0)).toLong(), 2)
        encrypt(file, block, data)
    }

    @Test fun readsUnsignedValuesAndDoesNotMutateInput() {
        val file = ByteArray(1024) { if (it < 32) (it * 7).toByte() else 0 }
        main(file, 8, 4, 6218, 33000, 100000)
        val before = file.copyOf()
        assertEquals(ConsoleFigureStats(6218, 10, 100000), ConsoleFigureStatsReader.parse(file))
        assertArrayEquals(before, file)
    }

    @Test fun newerAreaWinsAcrossCounterRollover() {
        val file = ByteArray(1024)
        main(file, 8, 255, 100, 1000, 10)
        main(file, 36, 0, 102, 2200, 20)
        assertEquals(ConsoleFigureStats(102, 3, 20), ConsoleFigureStatsReader.parse(file))
    }

    @Test fun damagedAreaFallsBackToIntactCopyAndBothDamagedGiveNoInventedStats() {
        val file = ByteArray(1024)
        main(file, 8, 1, 701, 6000, 3600)
        main(file, 36, 2, 703, 6000, 3700)
        file[36 * 16] = (file[36 * 16].toInt() xor 1).toByte()
        assertEquals(701, ConsoleFigureStatsReader.parse(file)?.gold)
        file[8 * 16] = (file[8 * 16].toInt() xor 1).toByte()
        assertNull(ConsoleFigureStatsReader.parse(file))
    }

    @Test fun extendedExperienceHasItsOwnSequenceAndProducesLevel20() {
        val file = ByteArray(1024)
        main(file, 8, 30, 50000, 33000, 0xffffffffL)
        fun extra(block: Int, sequence: Int, xp: Long) {
            val data = ByteArray(64)
            data[2] = sequence.toByte(); put(data, 3, 65535, 2); put(data, 8, xp, 4)
            put(data, 0, crc(byteArrayOf(6, 1) + data.copyOfRange(2, 64)).toLong(), 2)
            listOf(block, block + 1, block + 3, block + 4).forEachIndexed { i, b ->
                encrypt(file, b, data.copyOfRange(i * 16, i * 16 + 16))
            }
        }
        extra(17, 255, 0); extra(45, 0, 101000)
        assertEquals(ConsoleFigureStats(50000, 20, 0xffffffffL), ConsoleFigureStatsReader.parse(file))
    }

    @Test fun blanksUnsupportedKindsAndTruncatedFiles() {
        assertEquals(ConsoleFigureStats(0, 1, 0), ConsoleFigureStatsReader.parse(ByteArray(1024)))
        assertNull(ConsoleFigureStatsReader.parse(ByteArray(900)))
        assertFalse(ConsoleFigureStatsReader.supports("Trap"))
        assertFalse(ConsoleFigureStatsReader.supports("Item"))
        assertFalse(ConsoleFigureStatsReader.supports("Vehicle"))
        assertTrue(ConsoleFigureStatsReader.supports("Swapper"))
        assertEquals("27 Std. 46 Min.", figurePlayTime(100000))
    }
}

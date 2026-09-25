package com.example

import com.example.compiler.AndroidBinaryXmlGenerator
import com.example.compiler.AndroidDexGenerator
import com.example.compiler.RamSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testAndroidBinaryXmlGeneration() {
        val packageName = "com.test.app"
        val versionCode = 42
        val versionName = "2.1.0"
        val minSdk = 24
        val targetSdk = 35

        val xmlBytes = AndroidBinaryXmlGenerator.generateManifest(
            packageName = packageName,
            versionCode = versionCode,
            versionName = versionName,
            minSdk = minSdk,
            targetSdk = targetSdk
        )

        assertNotNull(xmlBytes)
        assertTrue(xmlBytes.size > 200)

        // Check XML chunk header: RES_XML_TYPE (0x0003)
        val bb = ByteBuffer.wrap(xmlBytes).order(ByteOrder.LITTLE_ENDIAN)
        val type = bb.short.toInt() and 0xFFFF
        val headerSize = bb.short.toInt() and 0xFFFF
        val totalSize = bb.int

        assertEquals(0x0003, type)
        assertEquals(8, headerSize)
        assertEquals(xmlBytes.size, totalSize)
    }

    @Test
    fun testAndroidDexGeneration() {
        val tempDex = File.createTempFile("test_classes", ".dex")
        try {
            AndroidDexGenerator.generateValidDex(
                destinationFile = tempDex,
                packageName = "com.test.app",
                className = "MainActivity"
            )

            assertTrue(tempDex.exists())
            assertTrue(tempDex.length() > 200)

            val bytes = tempDex.readBytes()
            // Check DEX magic: "dex\n035\0"
            val magic = String(bytes.copyOfRange(0, 8))
            assertEquals("dex\n035\u0000", magic)

            // Check header size
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            bb.position(36)
            val hdrSize = bb.int
            assertEquals(112, hdrSize)

            // Check endian tag
            val endianTag = bb.int
            assertEquals(0x12345678, endianTag)
        } finally {
            tempDex.delete()
        }
    }

    @Test
    fun testRamSelectionConstants() {
        assertEquals("500 MB", RamSelection.RAM_500MB.displayName)
        assertEquals("1 GB", RamSelection.RAM_1GB.displayName)
        assertEquals("1.5 GB", RamSelection.RAM_1_5GB.displayName)
        assertEquals("2 GB", RamSelection.RAM_2GB.displayName)
        assertEquals("2.5 GB", RamSelection.RAM_2_5GB.displayName)
        assertEquals("3 GB", RamSelection.RAM_3GB.displayName)
        assertEquals("3.5 GB", RamSelection.RAM_3_5GB.displayName)
        assertEquals("4 GB", RamSelection.RAM_4GB.displayName)
        assertEquals("Auto (Recommended)", RamSelection.AUTO.displayName)
    }
}

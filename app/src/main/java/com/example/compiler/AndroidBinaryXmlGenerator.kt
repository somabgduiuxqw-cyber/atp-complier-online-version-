package com.example.compiler

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AndroidBinaryXmlGenerator {

    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
    private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103

    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10

    fun generateManifest(
        packageName: String,
        versionCode: Int,
        versionName: String,
        minSdk: Int,
        targetSdk: Int
    ): ByteArray {
        val stringList = mutableListOf<String>()
        fun addString(str: String): Int {
            val idx = stringList.indexOf(str)
            return if (idx >= 0) idx else {
                stringList.add(str)
                stringList.size - 1
            }
        }

        // Prepopulate strings
        val sAndroid = addString("android")
        val sUri = addString("http://schemas.android.com/apk/res/android")
        val sManifest = addString("manifest")
        val sPackage = addString("package")
        val sPackageVal = addString(packageName)
        val sVersionCode = addString("versionCode")
        val sVersionName = addString("versionName")
        val sVersionNameVal = addString(versionName)
        val sUsesSdk = addString("uses-sdk")
        val sMinSdk = addString("minSdkVersion")
        val sTargetSdk = addString("targetSdkVersion")
        val sApplication = addString("application")
        val sLabel = addString("label")
        val sLabelVal = addString("ATP App")
        val sAllowBackup = addString("allowBackup")
        val sActivity = addString("activity")
        val sName = addString("name")
        val sActivityVal = addString(".MainActivity")
        val sExported = addString("exported")
        val sIntentFilter = addString("intent-filter")
        val sAction = addString("action")
        val sActionVal = addString("android.intent.action.MAIN")
        val sCategory = addString("category")
        val sCategoryVal = addString("android.intent.category.LAUNCHER")

        // Build String Pool
        val spBos = ByteArrayOutputStream()
        val spData = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()

        for (s in stringList) {
            offsets.add(spData.size())
            val chars = s.toCharArray()
            val lenBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(chars.size.toShort()).array()
            spData.write(lenBytes)
            for (ch in chars) {
                val chBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putChar(ch).array()
                spData.write(chBytes)
            }
            spData.write(byteArrayOf(0, 0)) // Null terminator
        }

        // Align string pool data to 4 bytes
        while (spData.size() % 4 != 0) {
            spData.write(0)
        }

        val spHeaderSize = 28
        val spStringsStart = spHeaderSize + (offsets.size * 4)
        val spTotalSize = spStringsStart + spData.size()

        val spHeader = ByteBuffer.allocate(spHeaderSize).order(ByteOrder.LITTLE_ENDIAN)
        spHeader.putShort(RES_STRING_POOL_TYPE.toShort())
        spHeader.putShort(spHeaderSize.toShort())
        spHeader.putInt(spTotalSize)
        spHeader.putInt(stringList.size)
        spHeader.putInt(0) // styles count
        spHeader.putInt(0) // flags
        spHeader.putInt(spStringsStart)
        spHeader.putInt(0) // styles start

        spBos.write(spHeader.array())
        for (off in offsets) {
            val offBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(off).array()
            spBos.write(offBytes)
        }
        spBos.write(spData.toByteArray())
        val stringPoolBytes = spBos.toByteArray()

        // Resource Map Chunk (Android attribute IDs)
        val resMapBos = ByteArrayOutputStream()
        val resIds = listOf(
            0x0101021b, // versionCode
            0x0101021c, // versionName
            0x0101020c, // minSdkVersion
            0x01010270, // targetSdkVersion
            0x01010001, // label
            0x01010280, // allowBackup
            0x01010003, // name
            0x010102b7  // exported
        )
        val resMapHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        resMapHeader.putShort(RES_XML_RESOURCE_MAP_TYPE.toShort())
        resMapHeader.putShort(8.toShort())
        resMapHeader.putInt(8 + (resIds.size * 4))
        resMapBos.write(resMapHeader.array())
        for (id in resIds) {
            val idBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(id).array()
            resMapBos.write(idBytes)
        }
        val resMapBytes = resMapBos.toByteArray()

        // XML Nodes
        val xmlNodesBos = ByteArrayOutputStream()

        fun writeNsStart(prefixIdx: Int, uriIdx: Int) {
            val bb = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            bb.putShort(RES_XML_START_NAMESPACE_TYPE.toShort())
            bb.putShort(16.toShort())
            bb.putInt(24)
            bb.putInt(1) // line
            bb.putInt(-1) // comment
            bb.putInt(prefixIdx)
            bb.putInt(uriIdx)
            xmlNodesBos.write(bb.array())
        }

        fun writeNsEnd(prefixIdx: Int, uriIdx: Int) {
            val bb = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            bb.putShort(RES_XML_END_NAMESPACE_TYPE.toShort())
            bb.putShort(16.toShort())
            bb.putInt(24)
            bb.putInt(1)
            bb.putInt(-1)
            bb.putInt(prefixIdx)
            bb.putInt(uriIdx)
            xmlNodesBos.write(bb.array())
        }

        data class XmlAttr(
            val nsIdx: Int,
            val nameIdx: Int,
            val rawValIdx: Int,
            val type: Int,
            val data: Int
        )

        fun writeStartElement(nsIdx: Int, nameIdx: Int, attrs: List<XmlAttr>) {
            val totalChunkSize = 16 + 20 + (attrs.size * 20)
            val bb = ByteBuffer.allocate(totalChunkSize).order(ByteOrder.LITTLE_ENDIAN)
            bb.putShort(RES_XML_START_ELEMENT_TYPE.toShort())
            bb.putShort(16.toShort())
            bb.putInt(totalChunkSize)
            bb.putInt(1) // line
            bb.putInt(-1) // comment
            bb.putInt(nsIdx)
            bb.putInt(nameIdx)
            bb.putShort(20.toShort()) // attribute start
            bb.putShort(20.toShort()) // attribute size
            bb.putShort(attrs.size.toShort())
            bb.putShort(0.toShort()) // id
            bb.putShort(0.toShort()) // class
            bb.putShort(0.toShort()) // style

            for (a in attrs) {
                bb.putInt(a.nsIdx)
                bb.putInt(a.nameIdx)
                bb.putInt(a.rawValIdx)
                bb.putShort(8.toShort())
                bb.put(0.toByte())
                bb.put(a.type.toByte())
                bb.putInt(a.data)
            }
            xmlNodesBos.write(bb.array())
        }

        fun writeEndElement(nsIdx: Int, nameIdx: Int) {
            val bb = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            bb.putShort(RES_XML_END_ELEMENT_TYPE.toShort())
            bb.putShort(16.toShort())
            bb.putInt(24)
            bb.putInt(1)
            bb.putInt(-1)
            bb.putInt(nsIdx)
            bb.putInt(nameIdx)
            xmlNodesBos.write(bb.array())
        }

        writeNsStart(sAndroid, sUri)

        // <manifest package="..." android:versionCode="..." android:versionName="...">
        writeStartElement(
            -1, sManifest,
            listOf(
                XmlAttr(-1, sPackage, sPackageVal, TYPE_STRING, sPackageVal),
                XmlAttr(sUri, sVersionCode, -1, TYPE_INT_DEC, versionCode),
                XmlAttr(sUri, sVersionName, sVersionNameVal, TYPE_STRING, sVersionNameVal)
            )
        )

        // <uses-sdk android:minSdkVersion="..." android:targetSdkVersion="..." />
        writeStartElement(
            -1, sUsesSdk,
            listOf(
                XmlAttr(sUri, sMinSdk, -1, TYPE_INT_DEC, minSdk),
                XmlAttr(sUri, sTargetSdk, -1, TYPE_INT_DEC, targetSdk)
            )
        )
        writeEndElement(-1, sUsesSdk)

        // <application android:label="..." android:allowBackup="true">
        writeStartElement(
            -1, sApplication,
            listOf(
                XmlAttr(sUri, sLabel, sLabelVal, TYPE_STRING, sLabelVal),
                XmlAttr(sUri, sAllowBackup, -1, 0x12 /* boolean */, 1)
            )
        )

        // <activity android:name=".MainActivity" android:exported="true">
        writeStartElement(
            -1, sActivity,
            listOf(
                XmlAttr(sUri, sName, sActivityVal, TYPE_STRING, sActivityVal),
                XmlAttr(sUri, sExported, -1, 0x12 /* boolean */, 1)
            )
        )

        // <intent-filter>
        writeStartElement(-1, sIntentFilter, emptyList())

        // <action android:name="android.intent.action.MAIN" />
        writeStartElement(-1, sAction, listOf(XmlAttr(sUri, sName, sActionVal, TYPE_STRING, sActionVal)))
        writeEndElement(-1, sAction)

        // <category android:name="android.intent.category.LAUNCHER" />
        writeStartElement(-1, sCategory, listOf(XmlAttr(sUri, sName, sCategoryVal, TYPE_STRING, sCategoryVal)))
        writeEndElement(-1, sCategory)

        // </intent-filter>
        writeEndElement(-1, sIntentFilter)

        // </activity>
        writeEndElement(-1, sActivity)

        // </application>
        writeEndElement(-1, sApplication)

        // </manifest>
        writeEndElement(-1, sManifest)

        writeNsEnd(sAndroid, sUri)

        val xmlNodesBytes = xmlNodesBos.toByteArray()

        // Root XML Chunk Header
        val totalSize = 8 + stringPoolBytes.size + resMapBytes.size + xmlNodesBytes.size
        val rootHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        rootHeader.putShort(RES_XML_TYPE.toShort())
        rootHeader.putShort(8.toShort())
        rootHeader.putInt(totalSize)

        val fullApk = ByteArrayOutputStream()
        fullApk.write(rootHeader.array())
        fullApk.write(stringPoolBytes)
        fullApk.write(resMapBytes)
        fullApk.write(xmlNodesBytes)
        return fullApk.toByteArray()
    }
}

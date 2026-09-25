package com.example.compiler

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

object AndroidDexGenerator {

    fun generateValidDex(
        destinationFile: File,
        packageName: String,
        className: String = "MainActivity"
    ) {
        val classDescriptor = "L${packageName.replace('.', '/')}/$className;"
        val superClassDescriptor = "Landroid/app/Activity;"
        val initName = "<init>"
        val voidProtoDesc = "()V"
        val onCreateName = "onCreate"
        val bundleProtoDesc = "(Landroid/os/Bundle;)V"
        val bundleParamDesc = "Landroid/os/Bundle;"
        val voidDesc = "V"

        // Strings list
        val strings = listOf(
            classDescriptor,
            superClassDescriptor,
            initName,
            voidProtoDesc,
            onCreateName,
            bundleProtoDesc,
            bundleParamDesc,
            voidDesc
        ).sorted()

        fun strIdx(s: String) = strings.indexOf(s)

        // Types list
        val types = listOf(
            classDescriptor,
            superClassDescriptor,
            bundleParamDesc,
            voidDesc
        ).sorted()

        fun typeIdx(t: String) = types.indexOf(t)

        val bos = ByteArrayOutputStream()
        val bb = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Header (112 bytes)
        bb.put(byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00)) // "dex\n035\0"
        bb.putInt(0) // Checksum placeholder (offset 8)
        bb.put(ByteArray(20)) // SHA-1 placeholder (offset 12)
        val fileSizeOffset = 32
        bb.putInt(0) // file_size placeholder (offset 32)
        bb.putInt(112) // header_size (offset 36)
        bb.putInt(0x12345678) // endian_tag (offset 40)
        bb.putInt(0) // link_size
        bb.putInt(0) // link_off
        val mapOffOffset = 52
        bb.putInt(0) // map_off placeholder
        bb.putInt(strings.size) // string_ids_size
        val stringIdsOff = 112
        bb.putInt(stringIdsOff) // string_ids_off
        bb.putInt(types.size) // type_ids_size
        val typeIdsOff = stringIdsOff + (strings.size * 4)
        bb.putInt(typeIdsOff) // type_ids_off
        bb.putInt(2) // proto_ids_size: ()V and (Landroid/os/Bundle;)V
        val protoIdsOff = typeIdsOff + (types.size * 4)
        bb.putInt(protoIdsOff) // proto_ids_off
        bb.putInt(0) // field_ids_size
        bb.putInt(0) // field_ids_off
        bb.putInt(2) // method_ids_size: <init>()V and onCreate(Bundle)V
        val methodIdsOff = protoIdsOff + (2 * 12)
        bb.putInt(methodIdsOff) // method_ids_off
        bb.putInt(1) // class_defs_size: 1 class
        val classDefsOff = methodIdsOff + (2 * 8)
        bb.putInt(classDefsOff) // class_defs_off
        val dataOff = classDefsOff + 32
        val dataOffPos = 68
        bb.putInt(dataOff) // data_off
        val dataSizePos = 64
        bb.putInt(0) // data_size placeholder

        // 2. String IDs (string_ids_off)
        // Leave space for string_data_item offsets, filled after encoding strings
        val stringOffsetsPos = bb.position()
        for (i in strings.indices) {
            bb.putInt(0) // placeholder
        }

        // 3. Type IDs
        for (t in types) {
            bb.putInt(strIdx(t))
        }

        // 4. Proto IDs
        // Proto 0: ()V -> shorty: V (idx of "V"), return_type: V, parameters_off: 0
        val vTypeIdx = typeIdx(voidDesc)
        val vStrIdx = strIdx(voidDesc)
        bb.putInt(vStrIdx)
        bb.putInt(vTypeIdx)
        bb.putInt(0) // no params

        // Proto 1: (Landroid/os/Bundle;)V -> shorty: VL, return_type: V, parameters_off: will set
        val proto1ParamsOffPos = bb.position() + 8
        bb.putInt(vStrIdx)
        bb.putInt(vTypeIdx)
        bb.putInt(0) // placeholder for type_list offset

        // 5. Method IDs
        // Method 0: <init>()V on class
        val thisTypeIdx = typeIdx(classDescriptor)
        val initStrIdx = strIdx(initName)
        bb.putShort(thisTypeIdx.toShort()) // class_idx
        bb.putShort(0.toShort()) // proto_idx = 0
        bb.putInt(initStrIdx) // name_idx

        // Method 1: onCreate(Bundle)V on class
        val onCreateStrIdx = strIdx(onCreateName)
        bb.putShort(thisTypeIdx.toShort())
        bb.putShort(1.toShort()) // proto_idx = 1
        bb.putInt(onCreateStrIdx)

        // 6. Class Defs
        // class_idx, access_flags, superclass_idx, interfaces_off, source_file_idx, annotations_off, class_data_off, static_values_off
        val superTypeIdx = typeIdx(superClassDescriptor)
        bb.putInt(thisTypeIdx)
        bb.putInt(0x0001) // ACC_PUBLIC
        bb.putInt(superTypeIdx)
        bb.putInt(0) // no interfaces
        bb.putInt(0) // source_file_idx
        bb.putInt(0) // annotations_off
        val classDataOffPos = bb.position()
        bb.putInt(0) // class_data_off placeholder
        bb.putInt(0) // static_values_off

        // DATA SECTION
        val actualDataStart = bb.position()

        // Type List for Proto 1 parameters: [1 item, Landroid/os/Bundle;]
        while (bb.position() % 4 != 0) bb.put(0.toByte())
        val typeListOffset = bb.position()
        bb.putInt(proto1ParamsOffPos, typeListOffset)
        bb.putInt(1) // size: 1 parameter
        bb.putShort(typeIdx(bundleParamDesc).toShort())
        bb.putShort(0.toShort()) // padding

        // Code Item for <init>(): return-void (0x000e)
        while (bb.position() % 4 != 0) bb.put(0.toByte())
        val initCodeOff = bb.position()
        bb.putShort(1.toShort()) // registers_size: 1 (v0 = this)
        bb.putShort(1.toShort()) // ins_size: 1 (this)
        bb.putShort(0.toShort()) // outs_size: 0
        bb.putShort(0.toShort()) // tries_size: 0
        bb.putInt(0) // debug_info_off: 0
        bb.putInt(1) // insns_size: 1 code unit
        bb.putShort(0x000E.toShort()) // return-void

        // Code Item for onCreate(): return-void
        while (bb.position() % 4 != 0) bb.put(0.toByte())
        val onCreateCodeOff = bb.position()
        bb.putShort(2.toShort()) // registers: 2 (this, bundle)
        bb.putShort(2.toShort()) // ins: 2
        bb.putShort(0.toShort()) // outs: 0
        bb.putShort(0.toShort()) // tries: 0
        bb.putInt(0) // debug_info_off
        bb.putInt(1) // insns_size: 1
        bb.putShort(0x000E.toShort()) // return-void

        // Class Data Item
        while (bb.position() % 4 != 0) bb.put(0.toByte())
        val classDataOffset = bb.position()
        bb.putInt(classDataOffPos, classDataOffset)

        // ULEB128 encoding helpers
        fun writeUleb128(value: Int) {
            var v = value
            do {
                var b = v and 0x7F
                v = v ushr 7
                if (v != 0) b = b or 0x80
                bb.put(b.toByte())
            } while (v != 0)
        }

        // static_fields_size: 0, instance_fields_size: 0, direct_methods_size: 1 (<init>), virtual_methods_size: 1 (onCreate)
        writeUleb128(0)
        writeUleb128(0)
        writeUleb128(1)
        writeUleb128(1)

        // Direct method: <init>
        writeUleb128(0) // method_idx_diff = 0
        writeUleb128(0x10001) // ACC_PUBLIC | ACC_CONSTRUCTOR
        writeUleb128(initCodeOff)

        // Virtual method: onCreate
        writeUleb128(1) // method_idx_diff = 1 (method 1)
        writeUleb128(0x0001) // ACC_PUBLIC
        writeUleb128(onCreateCodeOff)

        // Strings data
        val stringOffsets = mutableListOf<Int>()
        for (s in strings) {
            val sPos = bb.position()
            stringOffsets.add(sPos)
            val bytes = s.toByteArray(Charsets.UTF_8)
            writeUleb128(bytes.size)
            bb.put(bytes)
            bb.put(0.toByte())
        }

        // Write String IDs
        for (i in strings.indices) {
            bb.putInt(stringOffsetsPos + (i * 4), stringOffsets[i])
        }

        // Map list
        while (bb.position() % 4 != 0) bb.put(0.toByte())
        val mapOffset = bb.position()
        bb.putInt(mapOffOffset, mapOffset)

        // Minimal map item
        bb.putInt(6) // map items count
        fun putMapItem(type: Int, size: Int, offset: Int) {
            bb.putShort(type.toShort())
            bb.putShort(0.toShort())
            bb.putInt(size)
            bb.putInt(offset)
        }
        putMapItem(0x0000, 1, 0) // HEADER_ITEM
        putMapItem(0x0001, strings.size, stringIdsOff) // STRING_ID_ITEM
        putMapItem(0x0002, types.size, typeIdsOff) // TYPE_ID_ITEM
        putMapItem(0x0003, 2, protoIdsOff) // PROTO_ID_ITEM
        putMapItem(0x0005, 2, methodIdsOff) // METHOD_ID_ITEM
        putMapItem(0x0006, 1, classDefsOff) // CLASS_DEF_ITEM

        val totalSize = bb.position()
        bb.putInt(fileSizeOffset, totalSize)
        bb.putInt(dataSizePos, totalSize - actualDataStart)

        val dexBytes = ByteArray(totalSize)
        System.arraycopy(bb.array(), 0, dexBytes, 0, totalSize)

        // Checksum & SHA-1
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(dexBytes, 32, totalSize - 32)
        val sha1Digest = sha1.digest()
        System.arraycopy(sha1Digest, 0, dexBytes, 12, 20)

        val adler = Adler32()
        adler.update(dexBytes, 12, totalSize - 12)
        val checksum = adler.value.toInt()
        dexBytes[8] = (checksum and 0xFF).toByte()
        dexBytes[9] = ((checksum shr 8) and 0xFF).toByte()
        dexBytes[10] = ((checksum shr 16) and 0xFF).toByte()
        dexBytes[11] = ((checksum shr 24) and 0xFF).toByte()

        destinationFile.parentFile?.mkdirs()
        destinationFile.writeBytes(dexBytes)
    }
}

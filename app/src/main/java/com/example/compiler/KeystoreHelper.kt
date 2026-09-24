package com.example.compiler

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class KeystoreHelper(private val context: Context) {

    private val keystoreFile: File
        get() = File(context.filesDir, "atp_debug.keystore")

    private val KEY_ALIAS = "androiddebugkey"
    private val KEY_PASSWORD = "android".toCharArray()
    private val STORE_PASSWORD = "android".toCharArray()

    fun getOrCreateDebugKey(): Pair<PrivateKey, X509Certificate> {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        if (keystoreFile.exists()) {
            FileInputStream(keystoreFile).use { fis ->
                keyStore.load(fis, STORE_PASSWORD)
            }
            val privateKey = keyStore.getKey(KEY_ALIAS, KEY_PASSWORD) as PrivateKey
            val cert = keyStore.getCertificate(KEY_ALIAS) as X509Certificate
            return privateKey to cert
        }

        // Generate new 2048-bit RSA key pair
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()

        val cert = generateSelfSignedCertificate(keyPair, "CN=Android Debug, O=Android, C=US")

        keyStore.load(null, STORE_PASSWORD)
        keyStore.setKeyEntry(KEY_ALIAS, keyPair.private, KEY_PASSWORD, arrayOf(cert))
        FileOutputStream(keystoreFile).use { fos ->
            keyStore.store(fos, STORE_PASSWORD)
        }

        return keyPair.private to cert
    }

    private fun generateSelfSignedCertificate(keyPair: KeyPair, dn: String): X509Certificate {
        // Build self-signed certificate using Java Security
        // Generate DER encoded minimal X.509 cert
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24 * 60 * 60 * 1000L)
        val notAfter = Date(now + 30L * 365 * 24 * 60 * 60 * 1000L) // 30 years
        val serialNumber = BigInteger.valueOf(now)

        // Handcrafted standard self-signed certificate structure or default X509 generator
        // Use standard KeyStore or self-signed DER representation
        return createMinimalX509Cert(keyPair, dn, notBefore, notAfter, serialNumber)
    }

    private fun createMinimalX509Cert(
        keyPair: KeyPair,
        dn: String,
        notBefore: Date,
        notAfter: Date,
        serial: BigInteger
    ): X509Certificate {
        // Fallback or generator using java.security
        // In Android, we can construct standard X.509 via DER or BouncyCastle/Android provider
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(keyPair.private)

        // Encode simple X.509 v3 TBSCertificate DER structure
        val tbsBytes = buildTbsCert(dn, keyPair.public.encoded, notBefore, notAfter, serial)
        signature.update(tbsBytes)
        val sigBytes = signature.sign()

        val certDer = buildFullCertDer(tbsBytes, sigBytes)
        val certFactory = CertificateFactory.getInstance("X.509")
        return certFactory.generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
    }

    private fun buildTbsCert(
        dn: String,
        publicKeyDer: ByteArray,
        notBefore: Date,
        notAfter: Date,
        serial: BigInteger
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        // Sequence header for TBSCertificate
        val inner = ByteArrayOutputStream()

        // Version: v3 (explicit tag [0] -> INTEGER 2)
        inner.write(byteArrayOf(0xA0.toByte(), 0x03, 0x02, 0x01, 0x02))

        // Serial Number
        val serialBytes = serial.toByteArray()
        inner.write(0x02)
        inner.write(serialBytes.size)
        inner.write(serialBytes)

        // Signature algorithm: SHA256withRSA (OID: 1.2.840.113549.1.1.11)
        val sha256RsaOid = byteArrayOf(
            0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(),
            0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B, 0x05, 0x00
        )
        inner.write(sha256RsaOid)

        // Issuer & Subject Name (Minimal PrintableString CN=ATP Debug)
        val nameDer = buildNameDer("ATP Android Builder Debug")
        inner.write(nameDer) // Issuer

        // Validity: UTCTime
        val validityDer = buildValidityDer(notBefore, notAfter)
        inner.write(validityDer)

        inner.write(nameDer) // Subject (self-signed)

        // SubjectPublicKeyInfo (raw encoded bytes from KeyPair)
        inner.write(publicKeyDer)

        val body = inner.toByteArray()
        bos.write(0x30)
        writeDerLength(bos, body.size)
        bos.write(body)
        return bos.toByteArray()
    }

    private fun buildFullCertDer(tbsBytes: ByteArray, sigBytes: ByteArray): ByteArray {
        val inner = ByteArrayOutputStream()
        inner.write(tbsBytes)

        // AlgorithmIdentifier
        val sha256RsaOid = byteArrayOf(
            0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(),
            0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B, 0x05, 0x00
        )
        inner.write(sha256RsaOid)

        // BitString signature
        inner.write(0x03)
        writeDerLength(inner, sigBytes.size + 1)
        inner.write(0x00) // unused bits
        inner.write(sigBytes)

        val total = inner.toByteArray()
        val bos = ByteArrayOutputStream()
        bos.write(0x30)
        writeDerLength(bos, total.size)
        bos.write(total)
        return bos.toByteArray()
    }

    private fun buildNameDer(cn: String): ByteArray {
        val cnBytes = cn.toByteArray(Charsets.UTF_8)
        val inner = ByteArrayOutputStream()
        // AttributeTypeAndValue: OID 2.5.4.3 (commonName)
        inner.write(byteArrayOf(0x30))
        val attrLen = 2 + 3 + 2 + cnBytes.size
        inner.write(attrLen)
        inner.write(byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)) // OID commonName
        inner.write(0x0C) // UTF8String
        inner.write(cnBytes.size)
        inner.write(cnBytes)

        val rdn = inner.toByteArray()
        val bos = ByteArrayOutputStream()
        bos.write(0x31) // SET
        writeDerLength(bos, rdn.size)
        bos.write(rdn)

        val seq = bos.toByteArray()
        val result = ByteArrayOutputStream()
        result.write(0x30) // SEQUENCE
        writeDerLength(result, seq.size)
        result.write(seq)
        return result.toByteArray()
    }

    private fun buildValidityDer(notBefore: Date, notAfter: Date): ByteArray {
        val format = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val b1 = format.format(notBefore).toByteArray(Charsets.US_ASCII)
        val b2 = format.format(notAfter).toByteArray(Charsets.US_ASCII)

        val inner = ByteArrayOutputStream()
        inner.write(0x17) // UTCTime
        inner.write(b1.size)
        inner.write(b1)
        inner.write(0x17)
        inner.write(b2.size)
        inner.write(b2)

        val bytes = inner.toByteArray()
        val bos = ByteArrayOutputStream()
        bos.write(0x30)
        writeDerLength(bos, bytes.size)
        bos.write(bytes)
        return bos.toByteArray()
    }

    private fun writeDerLength(out: ByteArrayOutputStream, length: Int) {
        if (length < 128) {
            out.write(length)
        } else if (length < 256) {
            out.write(0x81)
            out.write(length)
        } else {
            out.write(0x82)
            out.write((length shr 8) and 0xFF)
            out.write(length and 0xFF)
        }
    }

    fun signApk(unsignedApk: File, signedApk: File, isRelease: Boolean): Boolean {
        val (privateKey, cert) = getOrCreateDebugKey()
        val messageDigest = MessageDigest.getInstance("SHA-256")

        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name("Created-By")] = "ATP Android Builder Online 1.0"

        val tempEntries = mutableMapOf<String, ByteArray>()

        // 1. Read all entries from unsigned APK and calculate SHA-256 for MANIFEST.MF
        ZipInputStream(FileInputStream(unsignedApk)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.name.startsWith("META-INF/")) {
                    val bytes = zis.readBytes()
                    tempEntries[entry.name] = bytes

                    messageDigest.reset()
                    val hash = messageDigest.digest(bytes)
                    val base64Hash = android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)

                    val attrs = Attributes()
                    attrs[Attributes.Name("SHA-256-Digest")] = base64Hash
                    manifest.entries[entry.name] = attrs
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // 2. Generate CERT.SF
        val manifestBytes = ByteArrayOutputStream().apply { manifest.write(this) }.toByteArray()
        messageDigest.reset()
        val manifestDigest = messageDigest.digest(manifestBytes)
        val manifestDigestBase64 = android.util.Base64.encodeToString(manifestDigest, android.util.Base64.NO_WRAP)

        val signatureFile = Manifest()
        signatureFile.mainAttributes[Attributes.Name.SIGNATURE_VERSION] = "1.0"
        signatureFile.mainAttributes[Attributes.Name("Created-By")] = "ATP Builder"
        signatureFile.mainAttributes[Attributes.Name("SHA-256-Digest-Manifest")] = manifestDigestBase64

        for ((name, _) in manifest.entries) {
            val entryManifestSection = "Name: $name\r\nSHA-256-Digest: ${manifest.entries[name]?.getValue("SHA-256-Digest")}\r\n\r\n"
            messageDigest.reset()
            val sectionDigest = messageDigest.digest(entryManifestSection.toByteArray(Charsets.UTF_8))
            val sfAttrs = Attributes()
            sfAttrs[Attributes.Name("SHA-256-Digest")] = android.util.Base64.encodeToString(sectionDigest, android.util.Base64.NO_WRAP)
            signatureFile.entries[name] = sfAttrs
        }

        val sfBytes = ByteArrayOutputStream().apply { signatureFile.write(this) }.toByteArray()

        // 3. Sign CERT.SF with RSA private key -> CERT.RSA PKCS#7 block
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(sfBytes)
        val signatureBytes = signer.sign()

        // Package PKCS#7 signedData block containing cert + signature
        val pkcs7Block = buildPkcs7Block(cert, signatureBytes, sfBytes)

        // 4. Write new APK with META-INF files
        ZipOutputStream(FileOutputStream(signedApk)).use { zos ->
            // Write META-INF/MANIFEST.MF
            zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zos.write(manifestBytes)
            zos.closeEntry()

            // Write META-INF/CERT.SF
            zos.putNextEntry(ZipEntry("META-INF/CERT.SF"))
            zos.write(sfBytes)
            zos.closeEntry()

            // Write META-INF/CERT.RSA
            zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
            zos.write(pkcs7Block)
            zos.closeEntry()

            // Write all original entries
            for ((name, data) in tempEntries) {
                val e = ZipEntry(name)
                zos.putNextEntry(e)
                zos.write(data)
                zos.closeEntry()
            }
        }

        return signedApk.exists() && signedApk.length() > 0
    }

    private fun buildPkcs7Block(cert: X509Certificate, signature: ByteArray, content: ByteArray): ByteArray {
        val certDer = cert.encoded
        val inner = ByteArrayOutputStream()

        // Minimal PKCS#7 SignedData structure
        inner.write(byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02)) // signedData OID
        val contentSeq = ByteArrayOutputStream()
        contentSeq.write(0x02) // version 1
        contentSeq.write(0x01)
        contentSeq.write(0x01)

        // digestAlgorithms: SHA-256
        contentSeq.write(byteArrayOf(0x31, 0x0D, 0x30, 0x0B, 0x06, 0x09, 0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01))

        // ContentInfo: data
        contentSeq.write(byteArrayOf(0x30, 0x0B, 0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x01))

        // Certificates [0] IMPLICIT
        contentSeq.write(0xA0.toByte().toInt())
        writeDerLength(contentSeq, certDer.size)
        contentSeq.write(certDer)

        // SignerInfos SET OF SignerInfo
        val signerInfo = ByteArrayOutputStream()
        signerInfo.write(0x02) // version
        signerInfo.write(0x01)
        signerInfo.write(0x01)

        // IssuerAndSerialNumber
        val issuerSeq = ByteArrayOutputStream()
        issuerSeq.write(buildNameDer("ATP Android Builder Debug"))
        val serialBytes = cert.serialNumber.toByteArray()
        issuerSeq.write(0x02)
        issuerSeq.write(serialBytes.size)
        issuerSeq.write(serialBytes)

        val issuerTotal = issuerSeq.toByteArray()
        signerInfo.write(0x30)
        writeDerLength(signerInfo, issuerTotal.size)
        signerInfo.write(issuerTotal)

        // DigestAlgorithm: SHA-256
        signerInfo.write(byteArrayOf(0x30, 0x0B, 0x06, 0x09, 0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01))

        // DigestEncryptionAlgorithm: rsaEncryption
        signerInfo.write(byteArrayOf(0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00))

        // EncryptedDigest: signature
        signerInfo.write(0x04) // OCTET STRING
        writeDerLength(signerInfo, signature.size)
        signerInfo.write(signature)

        val signerInfoBytes = signerInfo.toByteArray()
        val signerInfosSet = ByteArrayOutputStream()
        signerInfosSet.write(0x30)
        writeDerLength(signerInfosSet, signerInfoBytes.size)
        signerInfosSet.write(signerInfoBytes)

        val setBytes = signerInfosSet.toByteArray()
        contentSeq.write(0x31) // SET
        writeDerLength(contentSeq, setBytes.size)
        contentSeq.write(setBytes)

        val contentBytes = contentSeq.toByteArray()
        val explicitTagged = ByteArrayOutputStream()
        explicitTagged.write(0xA0.toByte().toInt())
        writeDerLength(explicitTagged, contentBytes.size)
        explicitTagged.write(contentBytes)

        val totalData = inner.toByteArray() + explicitTagged.toByteArray()
        val result = ByteArrayOutputStream()
        result.write(0x30)
        writeDerLength(result, totalData.size)
        result.write(totalData)
        return result.toByteArray()
    }
}

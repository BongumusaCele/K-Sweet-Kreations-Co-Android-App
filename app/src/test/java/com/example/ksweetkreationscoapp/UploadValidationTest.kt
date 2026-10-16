package com.example.ksweetkreationscoapp

import com.example.ksweetkreationscoapp.data.Upload
import org.junit.Assert.*
import org.junit.Test

class UploadValidationTest {
    private fun invalid(name: String, bytes: ByteArray, proof: Boolean = false) {
        try { Upload.validate(name,bytes,proof); fail("Expected an invalid attachment") } catch (_: IllegalArgumentException) { }
    }
    @Test fun validatesSignaturesExtensionsAndLimits() {
        val jpg = byteArrayOf(255.toByte(),216.toByte(),255.toByte())
        assertEquals("image/jpeg",Upload.validate("cake.JPG",jpg,false).mime)
        assertEquals("image/png",Upload.validate("cake.png",byteArrayOf(137.toByte(),80,78,71,13,10,26,10),false).mime)
        assertEquals("image/webp",Upload.validate("cake.webp","RIFF1234WEBP".toByteArray(),false).mime)
        assertEquals("application/pdf",Upload.validate("EFT.pdf","%PDF-1.4".toByteArray(),true).mime)
        invalid("cake.jpg","not an image".toByteArray())
        invalid("cake.exe",jpg); invalid("cake.png",jpg); invalid("empty.jpg",byteArrayOf())
        invalid("proof.pdf","%PDF-1.4".toByteArray())
        invalid("cake.jpg",ByteArray(2*1024*1024+1)); invalid("proof.pdf",ByteArray(5*1024*1024+1),true)
        assertEquals("attachment.jpg",Upload.validate("a\r\nunsafe.jpg",jpg,false).name)
    }
}

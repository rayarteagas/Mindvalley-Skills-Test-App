package com.mukoapps.urlloader

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException

typealias  DownloadableContent = ByteArray

//Implement interruptibly reading of streams to avoid wasting resources
fun InputStream.readBytesWithCheck(checker:()->Boolean): ByteArray {
    val buffer = ByteArrayOutputStream(maxOf(DEFAULT_BUFFER_SIZE, this.available()))
    copyToWithCheck(checker,buffer)
    return buffer.toByteArray()
}

fun InputStream.copyToWithCheck(checker:()->Boolean,out: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0 ) {
        if(!checker())
        {
            Log.e("Streams","Reading cancelled")
            throw CancellationException()
        }
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        bytes = read(buffer)
    }
    return bytesCopied
}
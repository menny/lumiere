package net.evendanan.lumiere

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL


interface IO {
    fun openUriForReading(uri: Uri): InputStream
    fun openUriForWriting(uri: Uri): OutputStream
}

internal class IOAndroid(private val context: Context) : IO {
    override fun openUriForWriting(uri: Uri): OutputStream {
        return when (uri.scheme) {
            "file" -> FileOutputStream(uri.toFile())
            "content" -> context.contentResolver.openOutputStream(uri)!!
            else -> throw IllegalArgumentException("url scheme ${uri.scheme} for $uri is not supported for writing.")
        }
    }

    override fun openUriForReading(uri: Uri): InputStream {
        return when (uri.scheme) {
            "file" -> FileInputStream(uri.toFile())
            "http", "https" -> URL(uri.toString()).openStream()
            "content" -> context.contentResolver.openInputStream(uri)!!
            else -> throw IllegalArgumentException("url scheme ${uri.scheme} for $uri is not supported for reading.")
        }
    }
}
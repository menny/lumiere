package net.evendanan.lumiere

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import java.io.*
import java.net.URL


interface IO {
    val localStorageFolder: File
    val appStorageFolder: File
    fun openUriForReading(uri: Uri): InputStream
    fun openUriForWriting(uri: Uri): OutputStream
    fun asShareUri(uri: Uri): Uri
    fun grantUriReadAccess(uri: Uri, packageName: String)
}

internal open class IOAndroid(private val context: Context) : IO {
    override fun grantUriReadAccess(uri: Uri, packageName: String) {
        context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override val localStorageFolder = File(context.getExternalFilesDir(null), "LumiereGifs")

    override val appStorageFolder = File(context.filesDir, "media")

    override fun asShareUri(uri: Uri): Uri = FileProvider.getUriForFile(context, context.packageName, uri.toFile())

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
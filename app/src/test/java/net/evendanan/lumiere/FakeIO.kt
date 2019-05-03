package net.evendanan.lumiere

import android.net.Uri
import androidx.core.net.toFile
import androidx.test.core.app.ApplicationProvider
import java.io.InputStream
import java.io.OutputStream

internal class FakeIO : IOAndroid(ApplicationProvider.getApplicationContext()) {
    val readUris = mutableListOf<Uri>()
    val writtenUri = mutableListOf<Uri>()
    private val allowedShareableUris = mutableSetOf<Pair<Uri, String>>()

    override fun openUriForReading(uri: Uri): InputStream {
        readUris.add(uri)
        return super.openUriForReading(uri)
    }

    override fun openUriForWriting(uri: Uri): OutputStream {
        writtenUri.add(uri)
        return super.openUriForWriting(uri)
    }

    fun readContentOfWrittenUri(uri: Uri): String = uri.toFile().readText()

    override fun grantUriReadAccess(uri: Uri, packageName: String) {
        allowedShareableUris.add(uri to packageName)
        super.grantUriReadAccess(uri, packageName)
    }
}

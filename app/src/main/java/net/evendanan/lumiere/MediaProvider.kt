package net.evendanan.lumiere

import android.net.Uri
import androidx.core.net.toUri
import com.giphy.sdk.core.models.enums.MediaType
import com.giphy.sdk.core.network.api.CompletionHandler
import com.giphy.sdk.core.network.api.GPHApiClient
import com.giphy.sdk.core.network.response.ListMediaResponse
import java.io.File
import java.util.concurrent.CountDownLatch

interface MediaProvider {
    fun blockingSearch(phrase: String): List<Media>
    fun blockingTrending(): List<Media>
    fun blockingSaved(): List<Media>
    fun blockingRecents(): List<Media>
    fun blockingGallery(): List<Media>
}

abstract class LocalMediaProvider(private val savedFilesFolder: File, private val recentFilesFolder: File) :
    MediaProvider {

    private fun gifsFromFolder(rootFolder: File) = rootFolder
        .listFiles { _, name -> name?.endsWith(".gif") ?: false }
        ?.map { Media(it.nameWithoutExtension, it.toUri(), it.toUri(), it.toUri(), it.name) }
        ?.toList() ?: emptyList()

    override fun blockingSaved() = gifsFromFolder(savedFilesFolder)

    override fun blockingRecents() = gifsFromFolder(recentFilesFolder)

    override fun blockingGallery(): List<Media> = emptyList()
}

/**
 * Giphy implementation of {@see MediaProvider}.
 * Note that all methods are blocking!
 */
class GiphyMediaProvider(apiKey: String, savedFilesFolder: File, recentFilesFolder: File) :
    LocalMediaProvider(savedFilesFolder, recentFilesFolder) {

    private val client = GPHApiClient(apiKey)

    override fun blockingTrending(): List<Media> {
        val result = BlockingCompletionHandler<ListMediaResponse>()
        client.trending(MediaType.gif, 40, 0, null, result)

        return result.waitAndGet().data!!.map(com.giphy.sdk.core.models.Media::toLumiereMedia)
    }

    override fun blockingSearch(phrase: String): List<Media> {
        val result = BlockingCompletionHandler<ListMediaResponse>()
        client.search(phrase, MediaType.gif, 40, 0, null, null, null, result)

        return result.waitAndGet().data!!.map(com.giphy.sdk.core.models.Media::toLumiereMedia)
    }
}

private fun com.giphy.sdk.core.models.Media.toLumiereMedia(): Media {
    return Media(
        title ?: "image",
        Uri.parse(images.preview?.gifUrl ?: images.original!!.gifUrl),
        Uri.parse(images.original!!.gifUrl!!),
        Uri.parse(images.downsizedMedium?.gifUrl ?: images.original!!.gifUrl),
        "${id}_${Uri.parse(images.original!!.gifUrl!!).lastPathSegment!!}"
    )
}

private class BlockingCompletionHandler<T> : CompletionHandler<T> {
    private val latch = CountDownLatch(1)

    private var result: T? = null
    private var exception: Throwable? = null

    override fun onComplete(result: T?, e: Throwable?) {
        this.result = result
        this.exception = e
        latch.countDown()
    }

    fun waitAndGet(): T {
        latch.await()
        exception?.run { throw this }
        result?.run { return this }

        throw RuntimeException("Failed to load data")
    }
}
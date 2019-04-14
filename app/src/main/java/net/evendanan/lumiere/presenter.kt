package net.evendanan.lumiere

import android.Manifest
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.*
import kotlinx.coroutines.android.Main
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch

interface Presenter {
    fun onUiVisible()
    fun onUiGone()

    fun onMediaActionClicked(media: Media, action: ActionType)

    fun onQuery(query: String)

    fun destroy()
}

enum class ActionType {
    Main,
    Share,
    Save,
    Favorite
}

enum class ProviderType {
    Search,
    Trending,
    Favorites,
    History,
}

interface ItemsProvider {
    val type: ProviderType
    val hasQuery: Boolean
    fun setOnItemsAvailableListener(listener: (List<Media>) -> Unit)
}

class PermissionRequest(val requestId: Int, val permissions: List<String>) {

    private val latch = CountDownLatch(1)
    private var granted = false

    fun onPermissionGranted() {
        granted = true
        latch.countDown()
    }

    fun onPermissionDenied() {
        granted = false
        latch.countDown()
    }

    fun waitForResponse(): Boolean {
        latch.await()
        return granted
    }
}

interface PresenterUI {
    fun setItemsProviders(providers: List<ItemsProvider>)
    fun focusOnSection(providerType: ProviderType)

    fun askForPermission(permissionRequest: PermissionRequest)

    fun showProgress()
    fun hideProgress()

    fun notifyLocalMediaFile(file: File)
}

class PresenterImpl(private val mediaProvider: MediaProvider, private val ui: PresenterUI, private val io: IO) :
    Presenter {
    private val availableProviders =
        mutableMapOf(
            ProviderType.Trending to ItemsProviderImpl(ProviderType.Trending, false),
            ProviderType.Search to ItemsProviderImpl(ProviderType.Search, true)
        )

    private var viewModelJob = Job()
    private var searchJob = Job()
    private var downloadJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main)

    init {
        ui.setItemsProviders(availableProviders.values.toSortedCollection())
    }

    override fun onUiVisible() {
        viewModelJob.cancel()
        viewModelJob = uiScope.launch(Dispatchers.Main.immediate) {
            val trending = withContext(Dispatchers.Default) {
                mediaProvider.blockingTrending()
            }
            availableProviders[ProviderType.Trending]?.setItems(trending)
        }
    }

    override fun onUiGone() {
        viewModelJob.cancel()
        searchJob.cancel()
    }

    override fun onMediaActionClicked(media: Media, action: ActionType) {
        Log.d("PresenterImpl", "onMediaActionClicked for ${media.original} with action $action")
        when (action) {
            ActionType.Save -> saveToLocalStorage(media)
            else -> TODO("Implement onMediaActionClicked for $action")
        }
    }

    private fun saveToLocalStorage(media: Media) {
        downloadJob.cancel()

        downloadJob = uiScope.launch(Dispatchers.Main.immediate) {
            val permissionRequest = PermissionRequest(111, listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            ui.askForPermission(permissionRequest)
            if (withContext(Dispatchers.Default) { permissionRequest.waitForResponse() }) {
                Log.d("presenter", "Starting saving to disk")
                ui.showProgress()

                try {
                    File(Environment.getExternalStorageDirectory(), "LumiereGifs").apply {
                        withContext(Dispatchers.Default) {
                            if (isDirectory || mkdirs()) {
                                copy(media.original, File(this@apply, media.filename).toUri())
                            } else {
                                throw IOException("Was not able to create LumiereGifs folder!")
                            }
                        }
                        ui.notifyLocalMediaFile(this)
                    }
                } catch (e: Exception) {
                    Log.w("presenter", "Failed to store ${media.original}. Error: ${e.message}")
                    e.printStackTrace()
                } finally {
                    ui.hideProgress()
                }
            } else {
                Log.d("presenter", "User did not give permissions to store to disk")
            }
        }
    }

    private fun copy(inputUri: Uri, outputUri: Uri) {
        Log.d("presenter-copy", "will copy from $inputUri to $outputUri...")
        io.openUriForReading(inputUri).use { receivedInputStream ->
            io.openUriForWriting(outputUri).use {
                receivedInputStream.copyTo(it)
            }
        }
        Log.d("presenter-copy", "done copy from $inputUri to $outputUri.")
    }

    override fun onQuery(query: String) {
        searchJob.cancel()

        //consider putting a local-loading GIF image here.
        availableProviders[ProviderType.Search]?.setItems(emptyList())
        searchJob = uiScope.launch(Dispatchers.Main.immediate) {
            val search = if (query.isEmpty()) {
                emptyList()
            } else {
                withContext(Dispatchers.Default) {
                    mediaProvider.blockingSearch(query)
                }
            }
            availableProviders[ProviderType.Search]?.setItems(search)
            ui.focusOnSection(ProviderType.Search)
        }
    }

    override fun destroy() {
        viewModelJob.cancel()
        searchJob.cancel()
        downloadJob.cancel()
    }
}

private class ItemsProviderImpl(override val type: ProviderType, override val hasQuery: Boolean) : ItemsProvider {
    private var items = emptyList<Media>()
    private var listener: ((List<Media>) -> Unit) = this::noOpListener

    override fun setOnItemsAvailableListener(listener: (List<Media>) -> Unit) {
        this.listener = listener
        notifyItems()
    }

    fun setItems(items: List<Media>) {
        this.items = items
        notifyItems()
    }

    private fun notifyItems() {
        listener(items)
    }

    fun noOpListener(items: List<Media>) {}
}

private fun Collection<ItemsProvider>.toSortedCollection() =
    this.toMutableList().apply {
        sortWith(Comparator { o1, o2 ->
            o1.type.ordinal - o2.type.ordinal
        })
    }
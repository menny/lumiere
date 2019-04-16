package net.evendanan.lumiere

import android.Manifest
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.*
import kotlinx.coroutines.android.Main
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch

interface Presenter {
    fun onSearchIconClicked()

    fun onUiVisible()
    fun onUiGone()

    fun onMediaActionClicked(media: Media, action: ActionType)

    fun onQuery(query: String, providerType: ProviderType)

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
    val supportedActions: Set<ActionType>
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

    fun fabVisibility(visible: Boolean)

    fun showProgress()
    fun hideProgress()

    fun notifyLocalMediaFile(file: File, shareUri: Uri)
    fun showShareWindow(shareUri: Uri)
    fun shareToAnySoftKeyboard(shareUri: Uri)
}

class PresenterImpl(
    private val pickerMode: Boolean,
    private val mediaProvider: MediaProvider,
    private val ui: PresenterUI,
    private val io: IO
) :
    Presenter {
    private val availableProviders: MutableMap<ProviderType, ItemsProviderImpl>

    private var viewModelJob = Job()
    private var searchJob = Job()
    private var downloadJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private val defaultActions = if (pickerMode) setOf(ActionType.Main) else setOf(ActionType.Share, ActionType.Save)

    init {
        availableProviders = mutableMapOf(
            ProviderType.Trending to ItemsProviderImpl(ProviderType.Trending, false, defaultActions)
        )

        if (pickerMode) {
            onSearchIconClicked()
        } else {
            ui.setItemsProviders(availableProviders.values.toSortedCollection())
        }

        viewModelJob = uiScope.launch(Dispatchers.Main.immediate) {
            val trending = withContext(Dispatchers.Default) {
                mediaProvider.blockingTrending()
            }
            availableProviders[ProviderType.Trending]?.setItems(trending)
        }
    }

    override fun onUiVisible() {
    }

    override fun onUiGone() {
        viewModelJob.cancel()
        searchJob.cancel()
    }

    override fun onMediaActionClicked(media: Media, action: ActionType) {
        Log.d("PresenterImpl", "onMediaActionClicked for ${media.original} with action $action")
        when (action) {
            ActionType.Save -> saveToLocalStorage(media)
            ActionType.Share -> shareFromAppStorage(media)
            ActionType.Main -> clickOnImage(media)
            else -> TODO("Implement onMediaActionClicked for $action")
        }
    }

    private fun downloadToAppStorage(media: Media): Uri {
        io.appStorageFolder.apply {
            if (isDirectory || mkdirs()) {
                return copy(media.original, File(this@apply, media.filename).toUri())
            } else {
                throw IOException("Was not able to create folder ${this@apply}!")
            }
        }
    }

    private fun clickOnImage(media: Media) {
        if (pickerMode) {
            downloadJob.cancel()

            downloadJob = uiScope.launch(Dispatchers.Main.immediate) {
                Log.d("presenter", "Starting saving to disk")
                ui.showProgress()

                try {
                    withContext(Dispatchers.Default) {
                        downloadToAppStorage(media).apply {
                            io.asShareUri(this).let { uriForAnySoftKeyboard ->
                                io.grantUriReadAccess(uriForAnySoftKeyboard, "com.menny.android.anysoftkeyboard")
                                ui.shareToAnySoftKeyboard(uriForAnySoftKeyboard)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("presenter", "Failed to store ${media.original}. Error: ${e.message}")
                    e.printStackTrace()
                } finally {
                    ui.hideProgress()
                }
            }
        } else {
            shareFromAppStorage(media)
        }
    }

    private fun shareFromAppStorage(media: Media) {
        downloadJob.cancel()

        downloadJob = uiScope.launch(Dispatchers.Main.immediate) {
            Log.d("presenter", "Starting saving to disk")
            ui.showProgress()

            try {
                withContext(Dispatchers.Default) {
                    downloadToAppStorage(media).apply {
                        ui.showShareWindow(io.asShareUri(this))
                    }
                }
            } catch (e: Exception) {
                Log.w("presenter", "Failed to store ${media.original}. Error: ${e.message}")
                e.printStackTrace()
            } finally {
                ui.hideProgress()
            }
        }
    }

    private fun saveToLocalStorage(media: Media) {
        downloadJob.cancel()

        downloadJob = uiScope.launch(Dispatchers.Main.immediate) {
            val havePermission = PermissionRequest(123, listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)).run {
                ui.askForPermission(this)
                return@run waitForResponse()
            }
            if (havePermission) {
                Log.d("presenter", "Starting saving to disk")
                ui.showProgress()

                try {
                    withContext(Dispatchers.Default) {
                        downloadToAppStorage(media).let { appFileUri ->
                            io.localStorageFolder.let { localFileFile ->
                                if (localFileFile.isDirectory || localFileFile.mkdirs()) {
                                    File(localFileFile, media.filename).let { targetFile ->
                                        copy(appFileUri, targetFile.toUri())
                                        ui.notifyLocalMediaFile(targetFile, appFileUri)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("presenter", "Failed to store ${media.original}. Error: ${e.message}")
                    e.printStackTrace()
                } finally {
                    ui.hideProgress()
                }
            }
        }
    }

    private fun copy(inputUri: Uri, outputUri: Uri): Uri {
        Log.d("presenter-copy", "will copy from $inputUri to $outputUri...")
        io.openUriForReading(inputUri).use { receivedInputStream ->
            io.openUriForWriting(outputUri).use {
                receivedInputStream.copyTo(it)
            }
        }
        Log.d("presenter-copy", "done copy from $inputUri to $outputUri.")

        return outputUri
    }

    override fun onSearchIconClicked() {
        availableProviders[ProviderType.Search].let {
            if (it == null) {
                ui.fabVisibility(false)
                availableProviders[ProviderType.Search] = ItemsProviderImpl(ProviderType.Search, true, defaultActions)
                ui.setItemsProviders(availableProviders.values.toSortedCollection())
                ui.focusOnSection(ProviderType.Search)
            }
        }
    }

    override fun onQuery(query: String, providerType: ProviderType) {
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

private class ItemsProviderImpl(
    override val type: ProviderType, override val hasQuery: Boolean,
    override val supportedActions: Set<ActionType>
) : ItemsProvider {
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
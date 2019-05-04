package net.evendanan.lumiere

import android.Manifest
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlin.apply
import kotlin.run

interface Presenter {
    fun setPresenterUi(ui: PresenterUI)

    fun onSearchIconClicked()

    fun onMediaActionClicked(media: Media, action: ActionType)

    fun onQuery(query: String, providerType: ProviderType)

    fun destroy()

    object NOOP : Presenter {

        override fun setPresenterUi(ui: PresenterUI) {}

        override fun onSearchIconClicked() {}

        override fun onMediaActionClicked(media: Media, action: ActionType) {}

        override fun onQuery(query: String, providerType: ProviderType) {}

        override fun destroy() {}
    }
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
    val loadingInProgress: Boolean
    val mediaItems: List<Media>
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

    object NOOP : PresenterUI {
        override fun setItemsProviders(providers: List<ItemsProvider>) {}

        override fun focusOnSection(providerType: ProviderType) {}

        override fun askForPermission(permissionRequest: PermissionRequest) {
            permissionRequest.onPermissionDenied()
        }

        override fun fabVisibility(visible: Boolean) {}

        override fun showProgress() {}

        override fun hideProgress() {}

        override fun notifyLocalMediaFile(file: File, shareUri: Uri) {}

        override fun showShareWindow(shareUri: Uri) {}

        override fun shareToAnySoftKeyboard(shareUri: Uri) {}
    }
}

class PresenterImpl(
    private val pickerMode: Boolean,
    private val mediaProvider: MediaProvider,
    private val io: IO,
    private val dispatchers: DispatchersProvider
) :
    Presenter {
    private var ui: PresenterUI = PresenterUI.NOOP
    private val availableProviders: MutableMap<ProviderType, ItemsProviderImpl> = mutableMapOf()

    private var viewModelJob = Job()
    private var searchJob = Job()
    private var downloadJob = Job()
    private val uiScope = CoroutineScope(dispatchers.main)

    private val defaultActions = if (pickerMode) setOf(ActionType.Main) else setOf(ActionType.Share, ActionType.Save)

    init {
        setProviderItems(ProviderType.Trending, true, emptyList())

        if (pickerMode) {
            onSearchIconClicked()
        }

        loadLocalGifs()
        viewModelJob = uiScope.launch(dispatchers.immediateMain) {
            setProviderItems(ProviderType.Trending, true, emptyList())
            val trending = withContext(dispatchers.background) {
                mediaProvider.blockingTrending()
            }
            setProviderItems(ProviderType.Trending, false, trending)
        }
    }

    private fun loadLocalGifs() {
        viewModelJob.cancel()
        viewModelJob = uiScope.launch(dispatchers.immediateMain) {
            val havePermission = PermissionRequest(123, listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)).run {
                ui.askForPermission(this)
                return@run withContext(dispatchers.background) { waitForResponse() }
            }

            if (havePermission) {
                val recentGifs = withContext(dispatchers.background) {
                    mediaProvider.blockingRecents()
                }
                setProviderItems(ProviderType.History, false, recentGifs)

                val saved = withContext(dispatchers.background) {
                    mediaProvider.blockingSaved()
                }
                setProviderItems(ProviderType.Favorites, false, saved)
            }
        }
    }

    override fun setPresenterUi(ui: PresenterUI) {
        this.ui = ui
        loadLocalGifs()
        ui.setItemsProviders(availableProviders.values.toSortedCollection())
        if (availableProviders.containsKey(ProviderType.Search)) {
            ui.fabVisibility(false)
            ui.focusOnSection(ProviderType.Search)
        } else {
            ui.fabVisibility(true)
        }

    }

    private fun setProviderItems(providerType: ProviderType, loading: Boolean, items: List<Media>) {
        availableProviders.getOrPut(
            providerType,
            {
                ItemsProviderImpl(
                    providerType,
                    providerType == ProviderType.Search,
                    defaultActions.filterForProvider(providerType)
                )
            }).run {
            this.loadingInProgress = loading
            this.items.run {
                clear()
                addAll(items)
            }
        }
        ui.setItemsProviders(availableProviders.values.toSortedCollection())
    }

    private fun Set<ActionType>.filterForProvider(providerType: ProviderType): Set<ActionType> {
        return when (providerType) {
            ProviderType.Favorites -> toMutableSet().apply { remove(ActionType.Save) }
            else -> this
        }
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

            downloadJob = uiScope.launch(dispatchers.immediateMain) {
                Log.d("presenter", "Starting saving to disk")
                ui.showProgress()

                try {
                    withContext(dispatchers.background) {
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

        downloadJob = uiScope.launch(dispatchers.immediateMain) {
            Log.d("presenter", "Starting saving to disk")
            ui.showProgress()

            try {
                withContext(dispatchers.background) {
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

        downloadJob = uiScope.launch(dispatchers.immediateMain) {
            val havePermission = PermissionRequest(123, listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)).run {
                ui.askForPermission(this)
                return@run withContext(dispatchers.background) { waitForResponse() }
            }
            if (havePermission) {
                Log.d("presenter", "Starting saving to disk")
                ui.showProgress()

                try {
                    withContext(dispatchers.background) {
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
                    loadLocalGifs()
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
                setProviderItems(ProviderType.Search, false, emptyList())
                ui.focusOnSection(ProviderType.Search)
            }
        }
    }

    override fun onQuery(query: String, providerType: ProviderType) {
        searchJob.cancel()

        //consider putting a local-loading GIF image here.
        setProviderItems(ProviderType.Search, true, emptyList())

        searchJob = uiScope.launch(dispatchers.immediateMain) {
            val search = if (query.isEmpty()) {
                emptyList()
            } else {
                withContext(dispatchers.background) {
                    mediaProvider.blockingSearch(query)
                }
            }
            setProviderItems(ProviderType.Search, false, search)
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
    internal val items = mutableListOf<Media>()

    override var loadingInProgress = false

    override val mediaItems: List<Media>
        get() = items
}

private fun Collection<ItemsProvider>.toSortedCollection() =
    this.toMutableList().apply {
        sortWith(Comparator { o1, o2 ->
            o1.type.ordinal - o2.type.ordinal
        })
    }
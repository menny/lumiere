package net.evendanan.lumiere

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.android.Main

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

interface PresenterUI {
    fun setItemsProviders(providers: List<ItemsProvider>)
    fun focusOnSection(providerType: ProviderType)
}

class PresenterImpl(private val mediaProvider: MediaProvider, private val ui: PresenterUI) : Presenter {
    private val availableProviders =
        mutableMapOf(
            ProviderType.Trending to ItemsProviderImpl(ProviderType.Trending, false),
            ProviderType.Search to ItemsProviderImpl(ProviderType.Search, true)
        )

    private var viewModelJob = Job()
    private var searchJob = Job()
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
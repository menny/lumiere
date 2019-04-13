package net.evendanan.lumiere

import kotlinx.coroutines.*
import kotlinx.coroutines.android.Main

interface Presenter {
    fun onFabClicked()

    fun onUiVisible()
    fun onUiGone()

    fun destroy()
}

enum class ProviderType {
    Trending,
    Favorites,
    History,
    Search
}

interface ItemsProvider {
    val type: ProviderType
    fun setOnItemsAvailableListener(listener: (List<Media>) -> Unit)
}

interface PresenterUI {
    fun setQueryBoxVisibility(visible: Boolean)
    fun getQueryBoxText(): String
    fun setItemsProviders(providers: List<ItemsProvider>)
    fun focusOnSection(providerType: ProviderType)
}

class PresenterImpl(private val mediaProvider: MediaProvider, private val ui: PresenterUI) : Presenter {

    private val availableProviders =
        mutableMapOf(ProviderType.Trending to ItemsProviderImpl(ProviderType.Trending))

    private var viewModelJob = Job()
    private var searchJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private var inSearchState = false

    init {
        ui.setItemsProviders(availableProviders.values.toSortedCollection())
    }

    override fun onUiVisible() {
        ui.setQueryBoxVisibility(inSearchState)

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

    override fun destroy() {
        viewModelJob.cancel()
        searchJob.cancel()
    }

    override fun onFabClicked() {
        if (inSearchState) {
            searchJob.cancel()

            if (!availableProviders.containsKey(ProviderType.Search)) {
                availableProviders[ProviderType.Search] = ItemsProviderImpl(ProviderType.Search)
                ui.setItemsProviders(availableProviders.values.toSortedCollection())
            }

            //consider putting a local-loading GIF image here.
            availableProviders[ProviderType.Search]?.setItems(emptyList())
            searchJob = uiScope.launch(Dispatchers.Main.immediate) {
                val search = ui.getQueryBoxText().let { queryString ->
                    if (queryString.isEmpty()) {
                        emptyList()
                    } else {
                        withContext(Dispatchers.Default) {
                            mediaProvider.blockingSearch(queryString)
                        }
                    }
                }
                availableProviders[ProviderType.Search]?.setItems(search)
                ui.focusOnSection(ProviderType.Search)
            }
        } else {
            inSearchState = true
            ui.setQueryBoxVisibility(true)
        }
    }
}

private class ItemsProviderImpl(override val type: ProviderType) : ItemsProvider {
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
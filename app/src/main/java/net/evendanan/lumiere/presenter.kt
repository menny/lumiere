package net.evendanan.lumiere

import kotlinx.coroutines.*
import kotlinx.coroutines.android.Main

interface Presenter {
    fun onFabClicked()

    fun onUiVisible()
    fun onUiGone()

    fun destroy()
}

interface PresenterUI {
    fun setQueryBoxVisibility(visible: Boolean)
    fun getQueryBoxText(): String
    fun setSearchResults(entries: List<Media>)
    fun setHistory(entries: List<Media>)
    fun setFavorites(entries: List<Media>)
    fun setTrending(entries: List<Media>)
}

class PresenterImpl(private val mediaProvider: MediaProvider, private val ui: PresenterUI) : Presenter {

    private var viewModelJob = Job()
    private var searchJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private var inSearchState = false

    override fun onUiVisible() {
        ui.setQueryBoxVisibility(inSearchState)

        viewModelJob.cancel()
        viewModelJob = uiScope.launch(Dispatchers.Main.immediate) {
            val trending = withContext(Dispatchers.Default) {
                mediaProvider.blockingTrending()
            }

            ui.setTrending(trending)
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
            ui.setSearchResults(emptyList())
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
                ui.setSearchResults(search)
            }
        } else {
            inSearchState = true
            ui.setQueryBoxVisibility(true)
        }
    }
}

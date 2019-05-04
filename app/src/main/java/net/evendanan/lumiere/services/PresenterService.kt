package net.evendanan.lumiere.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import androidx.annotation.VisibleForTesting
import net.evendanan.lumiere.*

abstract class PresenterServiceBase : Service() {

    @VisibleForTesting
    internal var presenter: Presenter = Presenter.NOOP

    protected abstract fun createPresenter(): Presenter

    override fun onBind(intent: Intent) = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        presenter = createPresenter()
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.destroy()
    }

    inner class LocalBinder : Binder(), Presenter {
        override fun setPresenterUi(ui: PresenterUI) {
            presenter.setPresenterUi(ui)
        }

        override fun onSearchIconClicked() {
            presenter.onSearchIconClicked()
        }

        override fun onMediaActionClicked(media: Media, action: ActionType) {
            presenter.onMediaActionClicked(media, action)
        }

        override fun onQuery(query: String, providerType: ProviderType) {
            presenter.onQuery(query, providerType)
        }

        override fun destroy() {
            presenter.setPresenterUi(PresenterUI.NOOP)
        }
    }
}

private fun createPresenter(pickerMode: Boolean, context: Context): PresenterImpl {
    val io = IOAndroid(context)
    return PresenterImpl(
        pickerMode,
        GiphyMediaProvider(context.getString(R.string.giphy_api_key), io.localStorageFolder, io.appStorageFolder),
        io,
        AndroidDispatchersProvider
    )
}

class DefaultPresenterService : PresenterServiceBase() {
    override fun createPresenter() = createPresenter(false, applicationContext)
}

class PickerPresenterService : PresenterServiceBase() {
    override fun createPresenter() = createPresenter(true, applicationContext)
}
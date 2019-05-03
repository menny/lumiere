package net.evendanan.lumiere.services

import android.app.Service
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

class DefaultPresenterService : PresenterServiceBase() {
    override fun createPresenter() = PresenterImpl(
        false,
        GiphyMediaProvider(getString(R.string.giphy_api_key)),
        IOAndroid(applicationContext),
        AndroidDispatchersProvider
    )
}

class PickerPresenterService : PresenterServiceBase() {
    override fun createPresenter() = PresenterImpl(
        true,
        GiphyMediaProvider(getString(R.string.giphy_api_key)),
        IOAndroid(applicationContext),
        AndroidDispatchersProvider
    )
}
/*
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    return super.onStartCommand(intent, flags, startId).also {
        if (presenter == Presenter.NOOP) {
            presenter = createPresenter(
                intent?.extras?.containsKey(MediaInsertion.INTENT_MEDIA_INSERTION_REQUEST_MEDIA_REQUEST_ID_KEY)
                    ?: false,
                GiphyMediaProvider(getString(R.string.giphy_api_key)),
                IOAndroid(applicationContext)
            )
        }
    }
}

 */


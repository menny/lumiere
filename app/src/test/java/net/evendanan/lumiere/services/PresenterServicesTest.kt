package net.evendanan.lumiere.services

import android.content.Intent
import android.net.Uri
import io.mockk.*
import net.evendanan.lumiere.*
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(org.robolectric.RobolectricTestRunner::class)
class PresenterServicesTest {

    @Test
    fun testCreatesDefaultPresenter() {
        val presenterUi = mockk<PresenterUI>(relaxed = true)
        val service = Robolectric.buildService(DefaultPresenterService::class.java).create().get()
        Assert.assertNotSame(Presenter.NOOP, service.presenter)
        service.presenter.setPresenterUi(presenterUi)

        val providers = slot<List<ItemsProvider>>()
        verify { presenterUi.setItemsProviders(capture(providers)) }
        verify(exactly = 0) { presenterUi.focusOnSection(any()) }

        Assert.assertEquals(1, providers.captured.size)
        Assert.assertFalse(providers.captured[0].hasQuery)
        Assert.assertEquals(ProviderType.Trending, providers.captured[0].type)
    }

    @Test
    fun testCreatesPickerPresenter() {
        val presenterUi = mockk<PresenterUI>(relaxed = true)
        val service = Robolectric.buildService(PickerPresenterService::class.java).create().get()
        Assert.assertNotSame(Presenter.NOOP, service.presenter)
        service.presenter.setPresenterUi(presenterUi)

        val providers = slot<List<ItemsProvider>>()
        verify {
            presenterUi.setItemsProviders(capture(providers))
            presenterUi.fabVisibility(false)
            presenterUi.focusOnSection(ProviderType.Search)
        }

        Assert.assertEquals(2, providers.captured.size)
        Assert.assertTrue(providers.captured[0].hasQuery)
        Assert.assertEquals(ProviderType.Search, providers.captured[0].type)
        Assert.assertFalse(providers.captured[1].hasQuery)
        Assert.assertEquals(ProviderType.Trending, providers.captured[1].type)
    }

    @Test
    fun testBinderDelegateToPresenter() {
        val service = Robolectric.buildService(DefaultPresenterService::class.java).create().get()
        service.presenter = mockk(relaxed = true)

        val localBinder = service.onBind(Intent())

        verify { service.presenter wasNot Called }

        val presenterUi = mockk<PresenterUI>(relaxed = true)
        localBinder.setPresenterUi(presenterUi)
        verify { service.presenter.setPresenterUi(presenterUi) }

        clearMocks(service.presenter)
        Media(
            "title",
            Uri.parse("http://ex.com"),
            Uri.parse("http://ex.com"),
            Uri.parse("http://ex.com"),
            "filesname.gif"
        ).run {
            localBinder.onMediaActionClicked(this, ActionType.Save)
            verify { service.presenter.onMediaActionClicked(this@run, ActionType.Save) }
        }

        clearMocks(service.presenter)
        localBinder.onQuery("hello", ProviderType.Search)
        verify { service.presenter.onQuery("hello", ProviderType.Search) }

        clearMocks(service.presenter)
        localBinder.onSearchIconClicked()
        verify { service.presenter.onSearchIconClicked() }

        clearMocks(service.presenter)
        localBinder.destroy()
        verify { service.presenter.setPresenterUi(PresenterUI.NOOP) }
    }
}
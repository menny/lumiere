package net.evendanan.lumiere.services

import android.content.Intent
import io.mockk.Called
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.evendanan.lumiere.ItemsProvider
import net.evendanan.lumiere.Presenter
import net.evendanan.lumiere.PresenterUI
import net.evendanan.lumiere.ProviderType
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
    }
}
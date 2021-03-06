package net.evendanan.lumiere

import android.app.Application
import android.net.Uri
import androidx.core.net.toFile
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import java.io.IOException

@RunWith(org.robolectric.RobolectricTestRunner::class)
class PresenterImplTest {

    private lateinit var presenterUI: PresenterUI
    private lateinit var io: FakeIO

    @Before
    fun setup() {
        presenterUI = mockk(relaxed = true)
        val capturingRequest = slot<PermissionRequest>()
        every { presenterUI.askForPermission(capture(capturingRequest)) } answers {
            Shadows.shadowOf(ApplicationProvider.getApplicationContext() as Application)
                .grantPermissions(*(capturingRequest.captured.permissions.toTypedArray()))
            capturingRequest.captured.onPermissionGranted()
        }
        io = FakeIO()
    }

    @Test
    fun testAsksForPermissionOnSaveActionAndNotSavingIfDenied() {
        val underTest = PresenterImpl(false, FakeMediaProvider(), io, UnconfinedDispatchersProvider())
        val capturingRequest = slot<PermissionRequest>()
        every { presenterUI.askForPermission(capture(capturingRequest)) } answers {
            capturingRequest.captured.onPermissionDenied()
        }

        underTest.setPresenterUi(presenterUI)

        underTest.onMediaActionClicked(
            Media(
                "search 2",
                Uri.parse("https://media1.giphy.com/media/3oKIPmUUz1MT9u3UA0/giphy.gif"),
                Uri.parse("https://media3.giphy.com/media/3oKIPmUUz1MT9u3UA0/giphy.gif"),
                Uri.parse("https://media3.giphy.com/media/3oKIPmUUz1MT9u3UA0/giphy.gif"),
                "3oKIPmUUz1MT9u3UA0.gif"
            ), ActionType.Save
        )

        coVerify { presenterUI.askForPermission(any()) }

        verify(exactly = 0) { presenterUI.showProgress() }
        Assert.assertTrue("Has readUris: ${io.readUris.joinToString(", ")}", io.readUris.isEmpty())
        Assert.assertTrue("Has writtenUri: ${io.writtenUri.joinToString(", ")}", io.writtenUri.isEmpty())

    }

    @Test
    fun testAsksForPermissionOnSaveActionAndSavingIfGranted() {
        val underTest = PresenterImpl(false, FakeMediaProvider(), io, UnconfinedDispatchersProvider())

        underTest.setPresenterUi(presenterUI)

        underTest.onMediaActionClicked(
            Media(
                "search 2",
                Uri.parse("https://media1.giphy.com/media/3oKIPmUUz1MT9u3UA0/giphy_preview.gif"),
                Uri.parse(PresenterImplTest::class.java.getResource("/fixtures/https___media3.giphy.com_media_3oKIPmUUz1MT9u3UA0_giphy_original.gif")!!.toURI().toString()),
                Uri.parse("https://media3.giphy.com/media/3oKIPmUUz1MT9u3UA0/giphy_medium.gif"),
                "3oKIPmUUz1MT9u3UA0.gif"
            ), ActionType.Save
        )

        coVerifySequence {
            //loading local files
            presenterUI.askForPermission(any())
            presenterUI.setItemsProviders(any())
            presenterUI.setItemsProviders(any())
            //load trending
            presenterUI.setItemsProviders(any())
            presenterUI.fabVisibility(any())
            //user wants to save
            presenterUI.askForPermission(any())
            presenterUI.showProgress()
            presenterUI.notifyLocalMediaFile(io.writtenUri[1].toFile(), io.writtenUri[0])
            presenterUI.hideProgress()
            //refresh local files
            presenterUI.askForPermission(any())
            presenterUI.setItemsProviders(any())
            presenterUI.setItemsProviders(any())
        }

        Assert.assertEquals("Has writtenUri: ${io.writtenUri.joinToString(", ")}", 2, io.writtenUri.size)
        //first is copied to app's storage
        Assert.assertTrue(io.writtenUri[0].toString().endsWith("/files/media/3oKIPmUUz1MT9u3UA0.gif"))
        //second is copied to local storage
        Assert.assertTrue(io.writtenUri[1].toString().endsWith("/external-files/LumiereGifs/3oKIPmUUz1MT9u3UA0.gif"))
        Assert.assertEquals(
            "this is the original file content",
            io.readContentOfWrittenUri(io.writtenUri[1])
        )
    }

    @Test
    fun testDoesNotCrushOnNetworkErrorWithTrending() {
        val mediaProvider = mockk<MediaProvider>()
        every { mediaProvider.blockingSaved() } returns emptyList()
        every { mediaProvider.blockingRecents() } returns emptyList()
        every { mediaProvider.blockingGallery() } returns emptyList()
        every { mediaProvider.blockingSearch(any()) } returns emptyList()
        every { mediaProvider.blockingTrending() } throws IOException("trending network failure")

        val underTest = PresenterImpl(false, mediaProvider, io, UnconfinedDispatchersProvider())
        underTest.setPresenterUi(presenterUI)

        verify {
            presenterUI.setItemsProviders(any())
            presenterUI.setItemsProviders(any())
        }
    }

    @Test
    fun testDoesNotCrushOnNetworkErrorWithSearch() {
        val mediaProvider = mockk<MediaProvider>()
        every { mediaProvider.blockingSaved() } returns emptyList()
        every { mediaProvider.blockingRecents() } returns emptyList()
        every { mediaProvider.blockingGallery() } returns emptyList()
        every { mediaProvider.blockingSearch(any()) } throws IOException("search network failure")
        every { mediaProvider.blockingTrending() } returns emptyList()

        val underTest = PresenterImpl(false, mediaProvider, io, UnconfinedDispatchersProvider())
        underTest.setPresenterUi(presenterUI)

        verify {
            presenterUI.setItemsProviders(any())
            presenterUI.setItemsProviders(any())
        }

        underTest.onSearchIconClicked()
        verify(exactly = 0) { mediaProvider.blockingSearch(any()) }
        underTest.onQuery("testing", ProviderType.Search)
        verify { mediaProvider.blockingSearch("testing") }
    }

    @Test
    fun testRefreshLocalGifsOnSettingUi() {
        val mediaProvider = mockk<MediaProvider>()
        every { mediaProvider.blockingSaved() } returns emptyList()
        every { mediaProvider.blockingRecents() } returns emptyList()
        every { mediaProvider.blockingGallery() } returns emptyList()
        every { mediaProvider.blockingSearch(any()) } returns emptyList()
        every { mediaProvider.blockingTrending() } returns emptyList()

        val capturingRequest = slot<PermissionRequest>()
        every { presenterUI.askForPermission(capture(capturingRequest)) } answers {
            capturingRequest.captured.onPermissionDenied()
        }

        val underTest = PresenterImpl(false, mediaProvider, io, UnconfinedDispatchersProvider())
        clearMocks(mediaProvider)
        every { mediaProvider.blockingSaved() } returns emptyList()
        every { mediaProvider.blockingRecents() } returns emptyList()
        every { mediaProvider.blockingGallery() } returns emptyList()
        every { mediaProvider.blockingSearch(any()) } returns emptyList()
        every { mediaProvider.blockingTrending() } returns emptyList()
        underTest.setPresenterUi(presenterUI)

        verify(exactly = 0) { mediaProvider.blockingRecents() }
        verify(exactly = 0) { mediaProvider.blockingSaved() }

        clearMocks(mediaProvider)
        every { mediaProvider.blockingSaved() } returns emptyList()
        every { mediaProvider.blockingRecents() } returns emptyList()
        every { mediaProvider.blockingGallery() } returns emptyList()
        every { mediaProvider.blockingSearch(any()) } returns emptyList()
        every { mediaProvider.blockingTrending() } returns emptyList()

        every { presenterUI.askForPermission(capture(capturingRequest)) } answers {
            Shadows.shadowOf(ApplicationProvider.getApplicationContext() as Application)
                .grantPermissions(*(capturingRequest.captured.permissions.toTypedArray()))
            capturingRequest.captured.onPermissionGranted()
        }
        underTest.setPresenterUi(presenterUI)

        verify {
            mediaProvider.blockingRecents()
            mediaProvider.blockingSaved()
        }

        clearMocks(mediaProvider)
        every { mediaProvider.blockingSaved() } returns emptyList()
        every { mediaProvider.blockingRecents() } returns emptyList()
        every { mediaProvider.blockingGallery() } returns emptyList()
        every { mediaProvider.blockingSearch(any()) } returns emptyList()
        every { mediaProvider.blockingTrending() } returns emptyList()
        underTest.setPresenterUi(presenterUI)

        verify {
            mediaProvider.blockingRecents()
            mediaProvider.blockingSaved()
        }
    }
}
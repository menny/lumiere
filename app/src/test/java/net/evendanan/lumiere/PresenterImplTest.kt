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
        io = FakeIO()
    }

    @Test
    fun testAsksForPermissionOnSaveActionAndNotSavingIfDenied() {
        val underTest = PresenterImpl(false, FakeMediaProvider(), presenterUI, io, UnconfinedDispatchersProvider())

        val capturingRequest = slot<PermissionRequest>()
        every { presenterUI.askForPermission(capture(capturingRequest)) } answers {
            capturingRequest.captured.onPermissionDenied()
        }

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
        val underTest = PresenterImpl(false, FakeMediaProvider(), presenterUI, io, UnconfinedDispatchersProvider())

        val capturingRequest = slot<PermissionRequest>()
        every { presenterUI.askForPermission(capture(capturingRequest)) } answers {
            Shadows.shadowOf(ApplicationProvider.getApplicationContext() as Application)
                .grantPermissions(*(capturingRequest.captured.permissions.toTypedArray()))
            capturingRequest.captured.onPermissionGranted()
        }

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
            presenterUI.setItemsProviders(any())
            presenterUI.askForPermission(any())
            presenterUI.showProgress()
            presenterUI.notifyLocalMediaFile(io.writtenUri[1].toFile(), io.writtenUri[0])
            presenterUI.hideProgress()
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
    fun testDoesNotCrushOnNetworkError() {
        val mediaProvider = mockk<MediaProvider>()
        every { mediaProvider.blockingSearch(any()) } throws IOException("search network failure")
        every { mediaProvider.blockingTrending() } throws IOException("trending network failure")

        val underTest = PresenterImpl(false, mediaProvider, presenterUI, io, UnconfinedDispatchersProvider())

        verify { presenterUI.setItemsProviders(any()) }
    }
}
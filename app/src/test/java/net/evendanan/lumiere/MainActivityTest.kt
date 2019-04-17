package net.evendanan.lumiere

import android.content.Intent
import com.anysoftkeyboard.api.MediaInsertion
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(org.robolectric.RobolectricTestRunner::class)
class MainActivityTest {

    @Test
    fun doesNotBuildPresenterUntilCreate() {
        val activityController = Robolectric.buildActivity(TestMainActivity::class.java)
        Assert.assertNull(activityController.get().mockPresenter)
        activityController.create()
        Assert.assertNotNull(activityController.get().mockPresenter)
    }

    @Test
    fun notPickerModeWhenDefaultIntent() {
        val activity = Robolectric.buildActivity(TestMainActivity::class.java).setup().get()

        Assert.assertFalse(activity.presenterInitArgs.pickerMode)
    }

    @Test
    fun pickerModeWhenStartedByAnySoftKeyboard() {
        val activity = Robolectric.buildActivity(TestMainActivity::class.java,
            Intent(MediaInsertion.INTENT_MEDIA_INSERTION_REQUEST_ACTION).apply {
                putExtra(MediaInsertion.INTENT_MEDIA_INSERTION_REQUEST_MEDIA_REQUEST_ID_KEY, 123)
                putExtra(MediaInsertion.INTENT_MEDIA_INSERTION_REQUEST_MEDIA_MIMES_KEY, arrayOf("image/png"))
            })
            .setup().get()

        Assert.assertTrue(activity.presenterInitArgs.pickerMode)
    }

    @Test
    fun presenterInitArgsAreReal() {
        val activity = Robolectric.buildActivity(TestMainActivity::class.java).setup().get()

        activity.presenterInitArgs.apply {
            Assert.assertTrue(mediaProvider is GiphyMediaProvider)
            Assert.assertTrue(ui is MainActivity.UiPresenterBridge)
            Assert.assertTrue(io is IOAndroid)
        }
    }
}

data class PresenterInitArgs(
    val pickerMode: Boolean,
    val mediaProvider: MediaProvider,
    val ui: PresenterUI,
    val io: IO
)

private class TestMainActivity : MainActivity() {

    lateinit var presenterInitArgs: PresenterInitArgs
    var mockPresenter: Presenter? = null

    override fun createPresenter(
        pickerMode: Boolean,
        mediaProvider: MediaProvider,
        ui: PresenterUI,
        io: IO
    ): Presenter {
        presenterInitArgs = PresenterInitArgs(pickerMode, mediaProvider, ui, io)

        return mockk<Presenter>(relaxed = true).apply { mockPresenter = this }
    }

    override fun createLoadingDrawable() = resources.getDrawable(R.drawable.ic_media_save, theme)
}
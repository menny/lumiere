package net.evendanan.lumiere

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import com.anysoftkeyboard.api.MediaInsertion
import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import net.evendanan.lumiere.services.DefaultPresenterService
import net.evendanan.lumiere.services.PickerPresenterService
import net.evendanan.lumiere.ui.MainActivity
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowApplication

@RunWith(org.robolectric.RobolectricTestRunner::class)
class MainActivityTest {

    private lateinit var defaultPresenter: PresenterBinder
    private lateinit var pickerPresenter: PresenterBinder
    private lateinit var shadowApplication: ShadowApplication

    @Before
    fun setup() {
        defaultPresenter = mockk(relaxed = true)
        pickerPresenter = mockk(relaxed = true)
        ApplicationProvider.getApplicationContext<Application>().let { app ->
            Shadows.shadowOf(app).run {
                shadowApplication = this
                setComponentNameAndServiceForBindServiceForIntent(
                    Intent(app, DefaultPresenterService::class.java),
                    ComponentName(app, DefaultPresenterService::class.java),
                    defaultPresenter
                )
                setComponentNameAndServiceForBindServiceForIntent(
                    Intent(app, PickerPresenterService::class.java),
                    ComponentName(app, PickerPresenterService::class.java),
                    pickerPresenter
                )
            }
        }
    }

    @Test
    fun notPickerModeWhenDefaultIntent() {
        Robolectric.buildActivity(TestMainActivity::class.java).setup()

        verify {
            defaultPresenter.setPresenterUi(any())
            pickerPresenter wasNot Called
        }
    }

    @Test
    fun pickerModeWhenStartedByAnySoftKeyboard() {
        Robolectric.buildActivity(TestMainActivity::class.java,
            Intent(MediaInsertion.INTENT_MEDIA_INSERTION_REQUEST_ACTION).apply {
                putExtra(MediaInsertion.INTENT_MEDIA_INSERTION_REQUEST_MEDIA_REQUEST_ID_KEY, 123)
                putExtra(MediaInsertion.INTENT_MEDIA_INSERTION_REQUEST_MEDIA_MIMES_KEY, arrayOf("image/png"))
            })
            .setup()

        verify {
            pickerPresenter.setPresenterUi(any())
            defaultPresenter wasNot Called
        }
    }

    @Test
    fun presenterServiceCalls() {
        Assert.assertNull(shadowApplication.nextStartedService)
        Robolectric.buildActivity(TestMainActivity::class.java).create()

        shadowApplication.nextStartedService.run {
            Assert.assertNotNull(this)
            Assert.assertNotNull(component)
            Assert.assertEquals(DefaultPresenterService::class.java.name, component.className)
        }
    }

    @Test
    fun presenterLifeCycle() {
        val activityController = Robolectric.buildActivity(TestMainActivity::class.java)
        activityController.create()
        verify { defaultPresenter.setPresenterUi(any()) }
        verify(exactly = 0) { defaultPresenter.destroy() }
        clearMocks(defaultPresenter)

        activityController.start().visible().resume().postResume()
        verify { defaultPresenter wasNot Called }

        activityController.pause().stop()

        verify { defaultPresenter wasNot Called }

        activityController.start().visible().resume().postResume()

        verify { defaultPresenter wasNot Called }

        activityController.pause().stop()
        verify { defaultPresenter wasNot Called }

        activityController.destroy()
        verify { defaultPresenter.destroy() }
    }
}

private interface PresenterBinder : IBinder, Presenter

private class TestMainActivity : MainActivity() {
    override fun createLoadingDrawable() = resources.getDrawable(R.drawable.ic_media_save, theme)
}
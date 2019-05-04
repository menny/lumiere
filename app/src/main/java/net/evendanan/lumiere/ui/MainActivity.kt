package net.evendanan.lumiere.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anysoftkeyboard.api.MediaInsertion
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.bumptech.glide.request.transition.TransitionFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import net.evendanan.lumiere.*
import net.evendanan.lumiere.services.DefaultPresenterService
import net.evendanan.lumiere.services.PickerPresenterService
import pl.droidsonroids.gif.GifDrawableBuilder
import java.io.File

open class MainActivity : AppCompatActivity() {

    private var runningPermissionsRequest: PermissionRequest? = null
    private lateinit var transitionGlideFactory: TransitionFactory<Drawable>
    private lateinit var loadingPlaceholder: Drawable
    private lateinit var loadingError: Drawable
    private val connectionToLumiere = ConnectionToLumiere(UiPresenterBridge())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fab.setOnClickListener { connectionToLumiere.presenter.onSearchIconClicked() }

        transitionGlideFactory = DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build()
        loadingPlaceholder = createLoadingDrawable()
        loadingError = getDrawable(R.drawable.ic_error_loading)!!

        root_list.adapter =
            SectionsAdapter(
                this,
                loadingPlaceholder,
                loadingError,
                { media, actionType -> connectionToLumiere.presenter.onMediaActionClicked(media, actionType) },
                { query, providerType -> connectionToLumiere.presenter.onQuery(query, providerType) }
            )
        root_list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)

        val serviceIntent = Intent(
            this,
            when (intent?.extras?.containsKey(MediaInsertion.INTENT_MEDIA_INSERTION_REQUEST_MEDIA_REQUEST_ID_KEY)) {
                true -> PickerPresenterService::class.java
                else -> DefaultPresenterService::class.java
            }
        )

        startService(serviceIntent)
        bindService(serviceIntent, connectionToLumiere, Context.BIND_AUTO_CREATE)
    }

    @VisibleForTesting
    internal open fun createLoadingDrawable(): Drawable =
        GifDrawableBuilder().from(resources.openRawResource(R.raw.loading_gif)).build()

    override fun onDestroy() {
        super.onDestroy()
        connectionToLumiere.presenter.destroy()
        unbindService(connectionToLumiere)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        runningPermissionsRequest?.let {
            if (requestCode == it.requestId) {
                runningPermissionsRequest = null
                if (grantResults.all { grantValue -> grantValue == PERMISSION_GRANTED }) {
                    it.onPermissionGranted()
                } else {
                    it.onPermissionDenied()
                }
            }
        }
    }

    inner class UiPresenterBridge : PresenterUI {
        override fun shareToAnySoftKeyboard(shareUri: Uri) {
            val intent = Intent(MediaInsertion.BROADCAST_INTENT_MEDIA_INSERTION_AVAILABLE_ACTION)
            intent.putExtra(
                MediaInsertion.BROADCAST_INTENT_MEDIA_INSERTION_REQUEST_ID_KEY,
                this@MainActivity.intent?.extras?.getInt(MediaInsertion.INTENT_MEDIA_INSERTION_REQUEST_MEDIA_REQUEST_ID_KEY)
                    ?: 0
            )
            intent.putExtra(
                MediaInsertion.BROADCAST_INTENT_MEDIA_INSERTION_MEDIA_MIMES_KEY,
                this@MainActivity.intent?.extras?.getStringArray(MediaInsertion.INTENT_MEDIA_INSERTION_REQUEST_MEDIA_MIMES_KEY)
                    ?: emptyArray<String>()
            )
            intent.putExtra(MediaInsertion.BROADCAST_INTENT_MEDIA_INSERTION_MEDIA_URI_KEY, shareUri)

            sendBroadcast(intent)
            finish()
        }

        override fun fabVisibility(visible: Boolean) {
            if (visible) {
                fab.show()
            } else {
                fab.hide()
            }
        }

        override fun askForPermission(permissionRequest: PermissionRequest) {
            this@MainActivity.runningPermissionsRequest = permissionRequest
            ActivityCompat.requestPermissions(
                this@MainActivity,
                permissionRequest.permissions.toTypedArray(),
                permissionRequest.requestId
            )
        }

        override fun showProgress() {

        }

        override fun hideProgress() {

        }

        override fun showShareWindow(shareUri: Uri) {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                setDataAndType(shareUri, "image/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, getText(R.string.share_image)))
        }

        override fun notifyLocalMediaFile(file: File, shareUri: Uri) {
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                data = Uri.fromFile(file)
            })

            Snackbar.make(root_list, getString(R.string.local_file_available, file.absolutePath), Snackbar.LENGTH_LONG)
                .setAction(R.string.show_downloaded_file_action) {
                    startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(shareUri, "image/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }
                .show()
        }

        override fun focusOnSection(providerType: ProviderType) {
            (providerType.ordinal + 1).let { itemPosition ->
                root_list.postDelayed({
                    root_list.scrollToPosition(itemPosition)
                    root_list.adapter?.notifyItemChanged(itemPosition, Payloads.FocusEditText)
                }, 60)
            }
        }

        override fun setItemsProviders(providers: List<ItemsProvider>) {
            providers.forEach { Log.d("UiPresenterBridge", "provide: ${it.type}") }
            (root_list.adapter as SectionsAdapter).setItemsProviders(providers)
        }
    }

    @VisibleForTesting
    internal class ConnectionToLumiere(private val ui: PresenterUI) : ServiceConnection {
        var presenter: Presenter = Presenter.NOOP

        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            presenter = service as Presenter
            presenter.setPresenterUi(ui)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            presenter = Presenter.NOOP
        }
    }
}

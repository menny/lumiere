package net.evendanan.lumiere

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anysoftkeyboard.api.MediaInsertion
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.transition.DrawableCrossFadeFactory
import com.bumptech.glide.request.transition.TransitionFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import pl.droidsonroids.gif.GifDrawableBuilder
import java.io.File


private typealias ClickMediaActionNotify = (Media, ActionType) -> Unit
private typealias QueryActionNotify = (query: String, ProviderType) -> Unit

open class MainActivity : AppCompatActivity() {

    private var runningPermissionsRequest: PermissionRequest? = null
    private lateinit var presenter: Presenter
    private lateinit var transitionGlideFactory: TransitionFactory<Drawable>
    private lateinit var loadingPlaceholder: Drawable
    private lateinit var loadingError: Drawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fab.setOnClickListener { presenter.onSearchIconClicked() }

        transitionGlideFactory = DrawableCrossFadeFactory.Builder().setCrossFadeEnabled(true).build()
        loadingPlaceholder = createLoadingDrawable()
        loadingError = getDrawable(R.drawable.ic_error_loading)!!

        root_list.adapter =
            SectionsAdapter(this, loadingPlaceholder, loadingError, this::onMediaItemClicked, this::onQuery)
        root_list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)

        presenter = createPresenter(
            intent?.extras?.containsKey(MediaInsertion.INTENT_MEDIA_INSERTION_REQUEST_MEDIA_REQUEST_ID_KEY) ?: false,
            GiphyMediaProvider(getString(R.string.giphy_api_key)),
            UiPresenterBridge(),
            IOAndroid(applicationContext)
        )
    }

    @VisibleForTesting
    internal open fun createLoadingDrawable(): Drawable =
        GifDrawableBuilder().from(resources.openRawResource(R.raw.loading_gif)).build()

    @VisibleForTesting
    internal open fun createPresenter(
        pickerMode: Boolean,
        mediaProvider: MediaProvider,
        ui: PresenterUI,
        io: IO
    ): Presenter = PresenterImpl(pickerMode, mediaProvider, ui, io, AndroidDispatchersProvider)

    override fun onStart() {
        super.onStart()
        presenter.onUiVisible()
    }

    override fun onStop() {
        super.onStop()
        presenter.onUiGone()
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.destroy()
    }

    private fun onMediaItemClicked(media: Media, actionType: ActionType) {
        presenter.onMediaActionClicked(media, actionType)
    }

    private fun onQuery(query: String, providerType: ProviderType) {
        presenter.onQuery(query, providerType)
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
}

sealed class Payloads {
    object FocusEditText : Payloads()
}

private class SectionsAdapter(
    private val activity: Activity,
    private val placeholder: Drawable, private val error: Drawable,
    private val clickMediaActionNotify: ClickMediaActionNotify,
    private val queryNotify: QueryActionNotify
) : RecyclerView.Adapter<SectionViewHolder>() {
    private val layoutInflater = LayoutInflater.from(activity)
    private var itemsProviders = emptyList<ItemsProvider>()

    init {
        setHasStableIds(true)
    }

    override fun getItemViewType(position: Int) = when (position) {
        0 -> APP_SECTION_TYPE
        itemsProviders.size + 1 -> SPACER_SECTION_TYPE
        else -> MEDIA_SECTION_TYPE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        when (viewType) {
            APP_SECTION_TYPE -> SectionViewHolder.StaticSectionViewHolder(
                layoutInflater.inflate(R.layout.app_title, parent, false)
            )
            SPACER_SECTION_TYPE -> SectionViewHolder.StaticSectionViewHolder(
                layoutInflater.inflate(R.layout.bottom_sections_spacer, parent, false)
            )
            else -> SectionViewHolder.MediaSectionViewHolder(
                layoutInflater.inflate(
                    R.layout.section,
                    parent,
                    false
                )
            ).apply {
                recyclerView.apply {
                    adapter = MediaItemsAdapter(activity, placeholder, error, clickMediaActionNotify)
                    layoutManager = LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false)
                }
            }
        }

    override fun getItemCount() = 2 + itemsProviders.size

    override fun getItemId(position: Int): Long = position.toLong()

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        if (holder is SectionViewHolder.MediaSectionViewHolder) {
            itemsProviders[position - 1].run {
                holder.title.text = when (type) {
                    ProviderType.Trending -> activity.getText(R.string.trending_list_title)
                    ProviderType.Favorites -> activity.getText(R.string.favorites_list_title)
                    ProviderType.History -> activity.getText(R.string.history_list_title)
                    ProviderType.Search -> activity.getText(R.string.search_results_title)
                }

                setOnItemsAvailableListener { items ->
                    (holder.recyclerView.adapter as MediaItemsAdapter).setItems(
                        this,
                        items
                    )
                }
                holder.queryBox.apply {
                    visibility = if (hasQuery) View.VISIBLE else View.GONE
                    setOnEditorActionListener { _, actionId, _ ->
                        when (actionId) {
                            EditorInfo.IME_ACTION_SEARCH -> {
                                queryNotify(text.toString(), (this@run).type); true
                            }
                            else -> false
                        }
                    }
                }
            }
        }
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int, payloads: MutableList<Any>) {
        super.onBindViewHolder(holder, position, payloads)
        if (holder is SectionViewHolder.MediaSectionViewHolder) {
            itemsProviders[position - 1].run {
                holder.queryBox.apply {
                    if (hasQuery && payloads.contains(Payloads.FocusEditText)) {
                        postDelayed({
                            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                .toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                            requestFocusFromTouch()
                        }, 60)

                    }
                }
            }
        }
    }

    fun setItemsProviders(providers: List<ItemsProvider>) {
        itemsProviders = providers
        notifyDataSetChanged()
    }

    companion object {
        const val APP_SECTION_TYPE = 0
        const val SPACER_SECTION_TYPE = 1
        const val MEDIA_SECTION_TYPE = 3
    }
}

sealed class SectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    class StaticSectionViewHolder(view: View) : SectionViewHolder(view)
    class MediaSectionViewHolder(view: View) : SectionViewHolder(view) {
        var title: TextView = view.findViewById(R.id.section_title)
        var queryBox: EditText = view.findViewById(R.id.search_query)
        var recyclerView: RecyclerView = view.findViewById(R.id.section_list)
    }
}

private class MediaItemsAdapter(
    private val activity: Activity,
    private val placeholder: Drawable, private val error: Drawable,
    private val clickNotifier: ClickMediaActionNotify
) :
    RecyclerView.Adapter<MediaItemViewHolder>() {

    private lateinit var provider: ItemsProvider
    private val layoutInflater = LayoutInflater.from(activity)
    private var items = emptyList<Media>()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MediaItemViewHolder(layoutInflater.inflate(R.layout.media_item, parent, false), clickNotifier)

    override fun getItemCount() = items.count()

    override fun getItemId(position: Int): Long = items[position].original.hashCode().toLong()

    fun setItems(newProvider: ItemsProvider, newItems: List<Media>) {
        newItems.forEach { Log.d("MediaItemsAdapter", "item: ${it.original} for ${newProvider.type}") }
        items = newItems
        provider = newProvider
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: MediaItemViewHolder, position: Int) {
        Glide.with(activity).clear(holder.image)

        items[position].let {
            holder.media = it
            Glide.with(activity)
                .load(it.preview)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(placeholder)
                .error(error)
                .into(holder.image)

            provider.supportedActions.apply {
                holder.favActionView.visibleIfSupported(ActionType.Favorite)
                holder.saveActionView.visibleIfSupported(ActionType.Save)
                holder.shareActionView.visibleIfSupported(ActionType.Share)
                holder.image.isClickable = provider.supportedActions.contains(ActionType.Main)
            }
        }
    }

    private fun View.visibleIfSupported(actionType: ActionType) {
        visibility = if (provider.supportedActions.contains(actionType))
            View.VISIBLE else View.GONE
    }
}

class MediaItemViewHolder(view: View, private val clickNotifier: ClickMediaActionNotify) :
    RecyclerView.ViewHolder(view) {
    var media: Media? = null
    val image: ImageView = view.findViewById(R.id.image_view)
    val saveActionView: View = view.findViewById(R.id.image_save_action)
    val favActionView: View = view.findViewById(R.id.image_fav_action)
    val shareActionView: View = view.findViewById(R.id.image_share_action)

    init {
        image.setOnClickListener(this::onViewClicked)
        saveActionView.setOnClickListener(this::onViewClicked)
        favActionView.setOnClickListener(this::onViewClicked)
        shareActionView.setOnClickListener(this::onViewClicked)
    }

    private fun onViewClicked(view: View) {
        media?.run {
            clickNotifier(
                this,
                when (view) {
                    saveActionView -> ActionType.Save
                    favActionView -> ActionType.Favorite
                    shareActionView -> ActionType.Share
                    else -> ActionType.Main
                }
            )
        }
    }
}

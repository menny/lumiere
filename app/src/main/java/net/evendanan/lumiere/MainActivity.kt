package net.evendanan.lumiere

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File


private typealias ClickMediaActionNotify = (Media, ActionType) -> Unit
private typealias QueryActionNotify = (query: String) -> Unit

class MainActivity : AppCompatActivity() {

    private var runningPermissionsRequest: PermissionRequest? = null
    private lateinit var presenter: Presenter
    private lateinit var loadingPlaceholder: Drawable
    private lateinit var loadingError: Drawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //fab.setOnClickListener { presenter.onFabClicked() }

        loadingPlaceholder = CircularProgressDrawable(this).apply {
            centerRadius = resources.getDimension(R.dimen.loading_radius)
            strokeWidth = resources.getDimension(R.dimen.loading_stroke_wide)
            start()
        }
        loadingError = getDrawable(R.drawable.ic_error_loading)!!

        root_list.adapter =
            SectionsAdapter(this, loadingPlaceholder, loadingError, this::onMediaItemClicked, this::onQuery)
        root_list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)

        //presenter = PresenterImpl(GiphyMediaProvider(getString(R.string.giphy_api_key)), UiPresenterBridge())
        presenter = PresenterImpl(FakeMediaProvider(), UiPresenterBridge(), IOAndroid(applicationContext))
    }

    override fun onStart() {
        super.onStart()
        presenter.onUiVisible()
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.destroy()
    }

    private fun onMediaItemClicked(media: Media, actionType: ActionType) {
        presenter.onMediaActionClicked(media, actionType)
    }

    private fun onQuery(query: String) {
        presenter.onQuery(query)
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

        override fun notifyLocalMediaFile(file: File) {
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                data = Uri.fromFile(file)
            })

            Snackbar.make(root_list, getString(R.string.local_file_available, file.absolutePath), Snackbar.LENGTH_LONG)
                .setAction(R.string.show_downloaded_file_action) {
                    startActivity(Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                        setDataAndType(Uri.fromFile(file), "image/${file.extension}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }
                .show()
        }

        override fun focusOnSection(providerType: ProviderType) {
            root_list.scrollToPosition(providerType.ordinal + 1)
        }

        override fun setItemsProviders(providers: List<ItemsProvider>) {
            providers.forEach { Log.d("UiPresenterBridge", "provide: ${it.type}") }
            (root_list.adapter as SectionsAdapter).setItemsProviders(providers)
        }
    }
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

    override fun getItemId(position: Int): Long = when (position) {
        0 -> APP_SECTION_TYPE.toLong()
        itemsProviders.size + 1 -> SPACER_SECTION_TYPE.toLong()
        else -> itemsProviders[position - 1].type.ordinal.toLong()
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        if (holder is SectionViewHolder.MediaSectionViewHolder) {
            itemsProviders[position - 1].run {
                holder.title.text = when (type) {
                    ProviderType.Trending -> activity.getText(R.string.trending_list_title)
                    ProviderType.Favorites -> activity.getText(R.string.favorites_list_title)
                    ProviderType.History -> activity.getText(R.string.history_list_title)
                    ProviderType.Search -> activity.getText(R.string.search_results_title)
                }

                setOnItemsAvailableListener((holder.recyclerView.adapter as MediaItemsAdapter)::setItems)
                holder.queryBox.apply {
                    visibility = if (hasQuery) View.VISIBLE else View.GONE
                    setOnEditorActionListener { _, actionId, _ ->
                        when (actionId) {
                            EditorInfo.IME_ACTION_SEARCH -> {
                                queryNotify(text.toString()); true
                            }
                            else -> false
                        }
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

    private val layoutInflater = LayoutInflater.from(activity)
    private var items = emptyList<Media>()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MediaItemViewHolder(layoutInflater.inflate(R.layout.media_item, parent, false), clickNotifier)

    override fun getItemCount() = items.count()

    override fun getItemId(position: Int): Long = items[position].original.hashCode().toLong()

    fun setItems(newItems: List<Media>) {
        newItems.forEach { Log.d("MediaItemsAdapter", "item: ${it.original}") }
        items = newItems
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: MediaItemViewHolder, position: Int) {
        Glide.with(activity).clear(holder.image)

        items[position].let {
            holder.media = it
            Glide.with(activity)
                .load(it.preview)
                .placeholder(placeholder)
                .error(error)
                .into(holder.image)
        }
    }

}

class MediaItemViewHolder(view: View, private val clickNotifier: ClickMediaActionNotify) :
    RecyclerView.ViewHolder(view) {
    var media: Media? = null
    val image: ImageView = view.findViewById(R.id.image_view)
    private val saveActionView: View = view.findViewById(R.id.image_save_action)
    private val favActionView: View = view.findViewById(R.id.image_fav_action)
    private val shareActionView: View = view.findViewById(R.id.image_share_action)

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

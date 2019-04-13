package net.evendanan.lumiere

import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var presenter: Presenter
    private lateinit var loadingPlaceholder: Drawable
    private lateinit var loadingError: Drawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fab.setOnClickListener { presenter.onFabClicked() }
        search_query.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    presenter.onFabClicked(); true
                }
                else -> false
            }
        }

        loadingPlaceholder = CircularProgressDrawable(this).apply {
            centerRadius = resources.getDimension(R.dimen.loading_radius)
            strokeWidth = resources.getDimension(R.dimen.loading_stroke_wide)
            start()
        }
        loadingError = getDrawable(R.drawable.ic_error_loading)!!

        root_list.adapter = SectionsAdapter(this, loadingPlaceholder, loadingError)
        root_list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)

        presenter = PresenterImpl(GiphyMediaProvider(getString(R.string.giphy_api_key)), UiPresenterBridge())
    }

    override fun onStart() {
        super.onStart()
        presenter.onUiVisible()
    }

    override fun onStateNotSaved() {
        super.onStateNotSaved()
        presenter.onFabClicked()
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.destroy()
    }

    inner class UiPresenterBridge : PresenterUI {
        override fun focusOnSection(providerType: ProviderType) {
            root_list.scrollToPosition(root_list.adapter?.itemCount ?: 0 - 1)
        }

        override fun setItemsProviders(providers: List<ItemsProvider>) {
            providers.forEach { Log.d("UiPresenterBridge", "provide: ${it.type}") }
            (root_list.adapter as SectionsAdapter).setItemsProviders(providers)
        }

        override fun setQueryBoxVisibility(visible: Boolean) {
            search_query.visibility = if (visible) View.VISIBLE else View.GONE
        }

        override fun getQueryBoxText() = search_query.text.toString()
    }
}

private class SectionsAdapter(
    private val activity: Activity,
    private val placeholder: Drawable, private val error: Drawable
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
                    adapter = MediaItemsAdapter(activity, placeholder, error)
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
                holder.title.text = when (this.type) {
                    ProviderType.Trending -> activity.getText(R.string.trending_list_title)
                    ProviderType.Favorites -> activity.getText(R.string.favorites_list_title)
                    ProviderType.History -> activity.getText(R.string.history_list_title)
                    ProviderType.Search -> activity.getText(R.string.search_results_title)
                }

                this.setOnItemsAvailableListener((holder.recyclerView.adapter as MediaItemsAdapter)::setItems)
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
        var recyclerView: RecyclerView = view.findViewById(R.id.section_list)
    }
}

private class MediaItemsAdapter(
    private val activity: Activity,
    private val placeholder: Drawable, private val error: Drawable
) :
    RecyclerView.Adapter<MediaItemViewHolder>() {
    private var viewTarget: Target<*>? = null

    private val layoutInflater = LayoutInflater.from(activity)
    private var items = emptyList<Media>()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MediaItemViewHolder(layoutInflater.inflate(R.layout.media_item, parent, false))

    override fun getItemCount() = items.count()

    override fun getItemId(position: Int): Long = items[position].original.hashCode().toLong()

    fun setItems(newItems: List<Media>) {
        newItems.forEach { Log.d("MediaItemsAdapter", "item: ${it.original}") }
        items = newItems
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: MediaItemViewHolder, position: Int) {
        //viewTarget?.run { Glide.with(activity).clear(this) }
        Glide.with(activity).clear(holder.image)

        viewTarget = items[position].let {
            Glide.with(activity)
                .load(it.preview)
                .placeholder(placeholder)
                .error(error)
                .into(holder.image)
        }
    }

}

class MediaItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val image: ImageView = view.findViewById(R.id.image_view)
}

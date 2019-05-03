package net.evendanan.lumiere.ui

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.evendanan.lumiere.*

internal sealed class Payloads {
    object FocusEditText : Payloads()
}

internal class SectionsAdapter(
    private val activity: Activity,
    private val placeholder: Drawable, private val error: Drawable,
    private val clickMediaActionNotify: (Media, ActionType) -> Unit,
    private val queryNotify: (query: String, ProviderType) -> Unit
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
                    adapter =
                        MediaItemsAdapter(activity, placeholder, error, clickMediaActionNotify)
                    layoutManager = LinearLayoutManager(
                        activity,
                        RecyclerView.HORIZONTAL,
                        false
                    )
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
                holder.loading.visibility = if (loadingInProgress) View.VISIBLE else View.GONE

                (holder.recyclerView.adapter as MediaItemsAdapter).setItems(this, mediaItems)

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

internal sealed class SectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    class StaticSectionViewHolder(view: View) : SectionViewHolder(view)
    class MediaSectionViewHolder(view: View) : SectionViewHolder(view) {
        var title: TextView = view.findViewById(R.id.section_title)
        var loading: ProgressBar = view.findViewById(R.id.section_loading)
        var queryBox: EditText = view.findViewById(R.id.search_query)
        var recyclerView: RecyclerView = view.findViewById(R.id.section_list)
    }
}
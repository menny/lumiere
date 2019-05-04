package net.evendanan.lumiere.ui

import android.app.Activity
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import net.evendanan.lumiere.ActionType
import net.evendanan.lumiere.ItemsProvider
import net.evendanan.lumiere.Media
import net.evendanan.lumiere.R

internal class MediaItemsAdapter(
    private val activity: Activity,
    private val placeholder: Drawable, private val error: Drawable,
    private val clickNotifier: (media: Media, actionType: ActionType) -> Unit
) :
    RecyclerView.Adapter<MediaItemViewHolder>() {

    private lateinit var provider: ItemsProvider
    private val layoutInflater = LayoutInflater.from(activity)
    private var items = emptyList<Media>()

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MediaItemViewHolder(
            layoutInflater.inflate(
                R.layout.media_item,
                parent,
                false
            ), clickNotifier
        )

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

internal class MediaItemViewHolder(
    view: View,
    private val clickNotifier: (media: Media, actionType: ActionType) -> Unit
) :
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
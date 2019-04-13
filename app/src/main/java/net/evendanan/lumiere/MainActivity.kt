package net.evendanan.lumiere

import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var presenter: Presenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { presenter.onFabClicked() }
        search_query.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> { presenter.onFabClicked(); true }
                else -> false
            }
        }

        val placeholder = CircularProgressDrawable(this).apply {
            centerRadius = resources.getDimension(R.dimen.loading_radius)
            strokeWidth = resources.getDimension(R.dimen.loading_stroke_wide)
            start()
        }
        trending_list.adapter = MediaItemsAdapter(this, placeholder)
        trending_list.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        search_results_list.adapter = MediaItemsAdapter(this, placeholder)
        search_results_list.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    inner class UiPresenterBridge : PresenterUI {
        override fun setQueryBoxVisibility(visible: Boolean) {
            search_query.visibility = if (visible) View.VISIBLE else View.GONE
        }

        override fun getQueryBoxText() = search_query.text.toString()

        override fun setSearchResults(entries: List<Media>) {
            entries.forEach { Log.d("UiPresenterBridge", "search result: ${it.original}") }
            search_results_card.visibility = View.VISIBLE
            (search_results_list.adapter as MediaItemsAdapter).setItems(entries)
        }

        override fun setHistory(entries: List<Media>) {
            entries.forEach { Log.d("UiPresenterBridge", "history: ${it.original}") }
        }

        override fun setFavorites(entries: List<Media>) {
            entries.forEach { Log.d("UiPresenterBridge", "favorite: ${it.original}") }
        }

        override fun setTrending(entries: List<Media>) {
            entries.forEach { Log.d("UiPresenterBridge", "trending: ${it.original}") }
            (trending_list.adapter as MediaItemsAdapter).setItems(entries)
        }

    }
}

private class MediaItemsAdapter(private val activity: Activity, private val placeholder: Drawable) :
    RecyclerView.Adapter<MediaItemViewHolder>() {
    private var viewTarget: Target<*>? = null

    private val layoutInflater = LayoutInflater.from(activity)
    private var items = emptyList<Media>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        MediaItemViewHolder(layoutInflater.inflate(R.layout.media_item, parent, false))

    override fun getItemCount() = items.count()

    fun setItems(newItems: List<Media>) {
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
                .error(R.drawable.ic_error_loading)
                .into(holder.image)
        }
    }

}

class MediaItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val image: ImageView = view.findViewById(R.id.image_view)
}

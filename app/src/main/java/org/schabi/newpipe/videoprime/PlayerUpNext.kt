package org.schabi.newpipe.videoprime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.PlayQueueItem
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.image.CoilHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * PulsePlay: the "Up next" row of the full-screen player, the same on TV, tablet and a turned
 * phone (the TV mock-up): the next videos of the queue or, when the queue ends here, the videos
 * YouTube/Twitch suggest for this one. Choosing one plays it in the same player.
 */
class PlayerUpNext(private val player: Player, private val list: RecyclerView) {

    private class Entry(val title: String, val thumbnail: String?, val queueItem: PlayQueueItem)

    private val adapter = Adapter()

    init {
        list.adapter = adapter
        list.itemAnimator = null
    }

    /** Reads again what comes next (after a change of video or of the queue). */
    fun refresh(): Boolean {
        adapter.submit(entries())
        return adapter.itemCount > 0
    }

    private fun entries(): List<Entry> {
        val queue = player.playQueue ?: return emptyList()
        val index = queue.index
        val next = queue.streams.drop(index + 1).take(MAX_ITEMS)
        if (next.isNotEmpty()) {
            return next.map { Entry(it.title, ImageStrategy.choosePreferredImage(it.thumbnails), it) }
        }
        val info = player.currentStreamInfo.orElse(null) ?: return emptyList()
        val current = queue.item?.url
        return info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter { it.url != current }
            .take(MAX_ITEMS)
            .mapNotNull { item ->
                val queueItem = SinglePlayQueue(item).item ?: return@mapNotNull null
                Entry(item.name, ImageStrategy.choosePreferredImage(item.thumbnails), queueItem)
            }
    }

    private fun play(entry: Entry) {
        val queue = player.playQueue ?: return
        if (queue.indexOf(entry.queueItem) == -1) {
            queue.append(listOf(entry.queueItem))
        }
        player.selectQueueItem(entry.queueItem)
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        private var items: List<Entry> = emptyList()

        fun submit(newItems: List<Entry>) {
            items = newItems
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.pulseplay_up_next_item, parent, false)
        )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = items[position]
            holder.title.text = entry.title
            CoilHelper.loadThumbnail(holder.thumbnail, entry.thumbnail)
            holder.itemView.setOnClickListener { play(entry) }
        }
    }

    private class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.upNextThumbnail)
        val title: TextView = view.findViewById(R.id.upNextTitle)
    }

    private companion object {
        const val MAX_ITEMS = 12
    }
}

package org.schabi.newpipe.videoprime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import org.schabi.newpipe.R
import org.schabi.newpipe.player.PlayerType
import org.schabi.newpipe.player.helper.PlayerHolder
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper

/**
 * VideoPrime: several play queues at once, like Musicolet. Switching to a queue saves the one
 * that was playing (item and position) and resumes the other where it was left.
 */
class QueuesFragment : Fragment() {
    private lateinit var store: QueueStore
    private var state by mutableStateOf(QueuesState())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        store = QueueStore(requireContext())
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val dark = !ThemeHelper.isLightThemeSelected(requireContext())
            val actions = QueuesActions(
                onSaveCurrent = ::saveCurrent,
                onSwitch = ::switchTo,
                onMore = ::showMenu
            )
            setContent { VideoPrimeTheme(dark = dark) { QueuesScreen(state, actions) } }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.queues_title)
        refresh()
    }

    private fun refresh() {
        val content = store.load()
        val holder = PlayerHolder.getInstance()
        val current = holder.currentPlayQueue
        state = QueuesState(
            playing = current?.item?.title,
            playingCount = current?.size() ?: 0,
            queues = content.queues.sortedByDescending { it.updated },
            activeId = content.activeId
        )
    }

    /** Saves the playing queue into the active saved queue, so that it can be resumed. */
    private fun rememberActiveQueue(content: QueueStore.Content): QueueStore.Content {
        val activeId = content.activeId ?: return content
        val current = PlayerHolder.getInstance().currentPlayQueue ?: return content
        val active = content.queues.firstOrNull { it.id == activeId } ?: return content
        val updated = SavedQueue.from(
            active.id,
            active.name,
            current,
            PlayerHolder.getInstance().currentPositionMillis
        )
        return content.copy(queues = content.queues.map { if (it.id == activeId) updated else it })
    }

    private fun saveCurrent() {
        val current = PlayerHolder.getInstance().currentPlayQueue ?: return
        val content = rememberActiveQueue(store.load())
        val id = System.currentTimeMillis()
        val name = getString(R.string.queues_default_name, content.queues.size + 1)
        val queue = SavedQueue.from(id, name, current, PlayerHolder.getInstance().currentPositionMillis)
        store.save(QueueStore.Content(content.queues + queue, id))
        Toast.makeText(requireContext(), getString(R.string.queues_saved, name), Toast.LENGTH_SHORT)
            .show()
        refresh()
    }

    private fun switchTo(queue: SavedQueue) {
        if (queue.items.isEmpty()) return
        val content = rememberActiveQueue(store.load())
        val target = content.queues.firstOrNull { it.id == queue.id } ?: queue
        store.save(content.copy(activeId = target.id))

        val playQueue = target.toPlayQueue()
        val context = requireContext()
        when (PlayerHolder.getInstance().type) {
            PlayerType.MAIN -> NavigationHelper.playOnMainPlayer(
                requireActivity() as AppCompatActivity,
                playQueue
            )

            PlayerType.POPUP -> NavigationHelper.playOnPopupPlayer(context, playQueue, false)

            else -> NavigationHelper.playOnBackgroundPlayer(context, playQueue, false)
        }
        refresh()
    }

    private fun showMenu(queue: SavedQueue) {
        val context = requireContext()
        AlertDialog.Builder(context)
            .setTitle(queue.name)
            .setItems(
                arrayOf(getString(R.string.rename), getString(R.string.delete))
            ) { _, which ->
                when (which) {
                    0 -> rename(queue)

                    1 -> {
                        val content = store.load()
                        store.save(
                            QueueStore.Content(
                                content.queues.filter { it.id != queue.id },
                                content.activeId?.takeIf { it != queue.id }
                            )
                        )
                        refresh()
                    }
                }
            }
            .show()
    }

    private fun rename(queue: SavedQueue) {
        val input = EditText(requireContext()).apply { setText(queue.name) }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim().ifEmpty { queue.name }
                val content = store.load()
                store.save(
                    content.copy(
                        queues = content.queues.map {
                            if (it.id == queue.id) it.copy(name = name) else it
                        }
                    )
                )
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        @JvmStatic
        fun open(fragmentManager: FragmentManager) {
            fragmentManager.beginTransaction()
                .replace(R.id.fragment_holder, QueuesFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}

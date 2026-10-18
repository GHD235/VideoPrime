package org.schabi.newpipe.videoprime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.fragments.BackPressable
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper

/**
 * VideoPrime: library of the app's downloads, by folder like Musicolet, played in the app's own
 * player. Videos open in the main player and audio in the background player.
 */
class DownloadsFragment : Fragment(), BackPressable {
    private val disposables = CompositeDisposable()
    private var state by mutableStateOf(DownloadsState())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val dark = !ThemeHelper.isLightThemeSelected(requireContext())
        val actions = DownloadsActions(
            onTab = { state = state.copy(tab = it) },
            onQuery = { state = state.copy(query = it) },
            onOpenFolder = {
                state = state.copy(openFolder = it, query = "")
                updateTitle()
            },
            onPlay = ::play,
            onShuffle = { files -> play(files.shuffled(), 0) },
            onMore = ::showMenu,
            onManager = { NavigationHelper.openDownloads(requireActivity()) }
        )
        setContent { VideoPrimeTheme(dark = dark) { DownloadsScreen(state, actions) } }
    }

    override fun onResume() {
        super.onResume()
        updateTitle()
        load()
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    override fun onBackPressed(): Boolean {
        if (state.openFolder != null) {
            state = state.copy(openFolder = null, query = "")
            updateTitle()
            return true
        }
        return false
    }

    private fun updateTitle() {
        (activity as? AppCompatActivity)?.supportActionBar?.title =
            state.openFolder?.name ?: getString(R.string.downloads)
    }

    private fun load() {
        val context = requireContext().applicationContext
        disposables.add(
            Single.fromCallable { LocalMedia.listDownloads(context) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { files ->
                        val folders = files.groupBy { it.folderPath }
                            .map { (path, inFolder) ->
                                DownloadFolder(inFolder.first().folder, path, inFolder)
                            }
                            .sortedBy { it.name.lowercase() }
                        val open = state.openFolder?.let { old ->
                            folders.firstOrNull { it.path == old.path }
                        }
                        state = state.copy(
                            loading = false,
                            all = files,
                            folders = folders,
                            openFolder = open
                        )
                    },
                    { state = state.copy(loading = false) }
                )
        )
    }

    private fun play(files: List<LocalFile>, index: Int) {
        if (files.isEmpty()) return
        val queue = SinglePlayQueue(files.map { it.toInfoItem() }, index)
        if (files[index].isAudio) {
            NavigationHelper.playOnBackgroundPlayer(requireContext(), queue, true)
        } else {
            NavigationHelper.playOnMainPlayer(requireActivity() as AppCompatActivity, queue)
        }
    }

    private fun showMenu(file: LocalFile) {
        val context = requireContext()
        val queue = SinglePlayQueue(file.toInfoItem())
        val options = arrayOf(
            getString(R.string.downloads_play),
            getString(R.string.controls_background_title),
            getString(R.string.controls_popup_title),
            getString(R.string.enqueue_stream)
        )
        AlertDialog.Builder(context)
            .setTitle(file.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> NavigationHelper.playOnMainPlayer(activity as AppCompatActivity, queue)
                    1 -> NavigationHelper.playOnBackgroundPlayer(context, queue, true)
                    2 -> NavigationHelper.playOnPopupPlayer(context, queue, true)
                    3 -> NavigationHelper.enqueueOnPlayer(context, queue)
                }
            }
            .show()
    }

    companion object {
        @JvmStatic
        fun open(fragmentManager: FragmentManager) {
            fragmentManager.beginTransaction()
                .replace(R.id.fragment_holder, DownloadsFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}

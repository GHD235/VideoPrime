package org.schabi.newpipe.videoprime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity
import org.schabi.newpipe.local.playlist.LocalPlaylistManager
import org.schabi.newpipe.local.playlist.RemotePlaylistManager
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.image.ImageStrategy

/**
 * VideoPrime: the "Library" section: shortcuts and the playlists (the user's own and the saved
 * YouTube ones) as a grid of covers. A long press offers renaming, keeping offline and removing.
 */
class LibraryFragment : Fragment() {
    private val disposables = CompositeDisposable()
    private var state by mutableStateOf(LibraryState())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val dark = !ThemeHelper.isLightThemeSelected(requireContext())
        val fm = { requireActivity().supportFragmentManager }
        val actions = LibraryActions(
            onDownloads = { DownloadsFragment.open(fm()) },
            onOffline = { OfflineFragment.open(fm()) },
            onQueues = { QueuesFragment.open(fm()) },
            onHistory = { NavigationHelper.openStatisticFragment(fm()) },
            onOpen = ::open,
            onMore = ::showMenu
        )
        setContent { VideoPrimeTheme(dark = dark) { LibraryScreen(state, actions) } }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext().applicationContext
        val database = NewPipeDatabase.getInstance(context)
        val local = LocalPlaylistManager(database)
        val remote = RemotePlaylistManager(database)
        disposables.add(
            Flowable.combineLatest(local.playlists, remote.playlists) { mine, saved ->
                merge(mine, saved, local)
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { playlists -> state = LibraryState(loading = false, playlists = playlists) },
                    { state = state.copy(loading = false) }
                )
        )
    }

    override fun onResume() {
        super.onResume()
        // the "available offline" badges may have changed in the offline screen
        val offline = OfflineMusic.load(requireContext())
        state = state.copy(
            playlists = state.playlists.map { it.copy(offline = isOffline(it, offline)) }
        )
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    /** The user's playlists and the saved ones, in the order the user gave them. */
    private fun merge(
        mine: List<PlaylistMetadataEntry>,
        saved: List<PlaylistRemoteEntity>,
        local: LocalPlaylistManager
    ): List<LibraryPlaylist> {
        val offline = OfflineMusic.load(requireContext().applicationContext)
        val items = ArrayList<Pair<Long, LibraryPlaylist>>()
        mine.forEach { entry ->
            val covers = runCatching {
                local.getPlaylistStreams(entry.uid).blockingFirst()
                    .mapNotNull { it.streamEntity.thumbnailUrl }
                    .distinct()
                    .take(COVERS)
            }.getOrDefault(emptyList())
                .ifEmpty { listOfNotNull(entry.thumbnailUrl) }
            val playlist = LibraryPlaylist(
                id = entry.uid,
                isRemote = false,
                name = entry.orderingName ?: "",
                uploader = null,
                url = null,
                serviceId = -1,
                streamCount = entry.streamCount,
                covers = covers.map { url ->
                    ImageStrategy.choosePreferredImage(ImageStrategy.dbUrlToImageList(url)) ?: url
                },
                offline = false
            )
            items += (entry.displayIndex ?: Long.MAX_VALUE) to
                playlist.copy(offline = isOffline(playlist, offline))
        }
        saved.forEach { entry ->
            val playlist = LibraryPlaylist(
                id = entry.uid,
                isRemote = true,
                name = entry.orderingName ?: "",
                uploader = entry.uploader,
                url = entry.url,
                serviceId = entry.serviceId,
                streamCount = entry.streamCount ?: -1,
                covers = listOfNotNull(entry.thumbnailUrl),
                offline = false
            )
            items += entry.displayIndex to playlist.copy(offline = isOffline(playlist, offline))
        }
        return items.sortedBy { it.first }.map { it.second }
    }

    private fun isOffline(playlist: LibraryPlaylist, index: OfflineMusic.Index): Boolean {
        val collection = collectionOf(playlist) ?: return false
        return index.collections.any { it.type == collection.type && it.ref == collection.ref }
    }

    private fun collectionOf(playlist: LibraryPlaylist): OfflineCollection? = if (playlist.isRemote) {
        playlist.url?.let { OfflineCollection(OfflineCollection.TYPE_REMOTE, it, playlist.name) }
    } else {
        OfflineCollection(OfflineCollection.TYPE_LOCAL, playlist.id.toString(), playlist.name)
    }

    private fun open(playlist: LibraryPlaylist) {
        val fm = requireActivity().supportFragmentManager
        if (playlist.isRemote) {
            playlist.url?.let { NavigationHelper.openPlaylistFragment(fm, playlist.serviceId, it, playlist.name) }
        } else {
            NavigationHelper.openLocalPlaylistFragment(fm, playlist.id, playlist.name)
        }
    }

    private fun showMenu(playlist: LibraryPlaylist) {
        val context = requireContext()
        val collection = collectionOf(playlist)
        val options = buildList {
            if (!playlist.isRemote) add(getString(R.string.rename) to { rename(playlist) })
            if (collection != null) {
                add(
                    getString(if (playlist.offline) R.string.offline_unpin else R.string.offline_available) to {
                        OfflineMusic.togglePinned(context, collection)
                        onResume()
                    }
                )
            }
            add(getString(R.string.delete) to { confirmDelete(playlist) })
        }
        AlertDialog.Builder(context)
            .setTitle(playlist.name)
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
            .show()
    }

    private fun rename(playlist: LibraryPlaylist) {
        val input = EditText(requireContext()).apply { setText(playlist.name) }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rename_playlist)
            .setView(input)
            .setPositiveButton(R.string.rename) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    disposables.add(
                        LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()))
                            .renamePlaylist(playlist.id, name)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ }, { })
                    )
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(playlist: LibraryPlaylist) {
        val context = requireContext()
        AlertDialog.Builder(context)
            .setTitle(playlist.name)
            .setMessage(
                if (playlist.isRemote) R.string.library_remove_saved else R.string.delete_playlist_prompt
            )
            .setPositiveButton(R.string.delete) { _, _ ->
                val database = NewPipeDatabase.getInstance(context)
                disposables.add(
                    Completable.fromAction {
                        if (playlist.isRemote) {
                            database.playlistRemoteDAO().deletePlaylist(playlist.id)
                        } else {
                            database.playlistDAO().deletePlaylist(playlist.id)
                        }
                        collectionOf(playlist)?.let { OfflineMusic.setPinned(context, it, false) }
                    }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            { Toast.makeText(context, R.string.library_deleted, Toast.LENGTH_SHORT).show() },
                            { }
                        )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val COVERS = 4
    }
}

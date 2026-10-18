package org.schabi.newpipe.videoprime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File
import java.net.URI
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.fragments.BackPressable
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.ThemeHelper
import us.shandian.giga.get.DownloadMission
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService

/**
 * VideoPrime: library of the app's downloads, split into music, videos and folders like
 * Musicolet, played in the app's own player. Music plays in the background player and videos in
 * the main one. Files can be selected to play, queue, share or delete them.
 */
class DownloadsFragment : Fragment(), BackPressable {
    private val disposables = CompositeDisposable()
    private var state by mutableStateOf(DownloadsState())

    private var manager: DownloadManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingCount = 0
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            manager = (binder as? DownloadManagerService.DownloadManagerBinder)?.downloadManager
            pollRunning()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            manager = null
        }
    }
    private val poller = object : Runnable {
        override fun run() {
            pollRunning()
            handler.postDelayed(this, POLL_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireContext().bindService(
            Intent(requireContext(), DownloadManagerService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onDestroy() {
        requireContext().unbindService(connection)
        super.onDestroy()
    }

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
            onToggleSearch = { state = state.copy(searching = !state.searching, query = "") },
            onOpenFolder = {
                state = state.copy(openFolder = it, query = "", searching = false)
                updateTitle()
            },
            onPlay = ::play,
            onShuffle = { files -> play(files.shuffled(), 0) },
            onMenu = { state = state.copy(menuFor = it) },
            onFileAction = ::onFileAction,
            onToggleSelected = { file ->
                val selected = state.selected
                state = state.copy(
                    selected = if (file.url in selected) selected - file.url else selected + file.url
                )
            },
            onSelectAll = { files -> state = state.copy(selected = files.map { it.url }.toSet()) },
            onClearSelection = { state = state.copy(selected = emptySet()) },
            onPlaySelected = {
                play(state.selectedFiles, 0)
                state = state.copy(selected = emptySet())
            },
            onQueueSelected = { addToQueue(state.selectedFiles) },
            onShareSelected = { share(state.selectedFiles) },
            onDeleteSelected = { state = state.copy(confirmDelete = state.selectedFiles) },
            onConfirmDelete = { confirmed ->
                val files = state.confirmDelete.orEmpty()
                state = state.copy(confirmDelete = null)
                if (confirmed) delete(files)
            },
            onManager = { NavigationHelper.openDownloads(requireActivity()) }
        )
        setContent { VideoPrimeTheme(dark = dark) { DownloadsScreen(state, actions) } }
    }

    override fun onResume() {
        super.onResume()
        updateTitle()
        load()
        handler.post(poller)
    }

    override fun onPause() {
        handler.removeCallbacks(poller)
        super.onPause()
    }

    override fun onDestroyView() {
        disposables.clear()
        super.onDestroyView()
    }

    override fun onBackPressed(): Boolean {
        when {
            state.menuFor != null -> state = state.copy(menuFor = null)

            state.selecting -> state = state.copy(selected = emptySet())

            state.searching -> state = state.copy(searching = false, query = "")

            state.openFolder != null -> {
                state = state.copy(openFolder = null, query = "")
                updateTitle()
            }

            else -> return false
        }
        return true
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
                        val urls = files.map { it.url }.toSet()
                        state = state.copy(
                            loading = false,
                            all = files,
                            folders = folders,
                            openFolder = open,
                            selected = state.selected.filter { it in urls }.toSet(),
                            freeBytes = freeBytes(),
                            // a library with no music yet opens on the videos
                            tab = if (state.loading && files.none { it.isAudio } &&
                                files.any { !it.isAudio }
                            ) {
                                DownloadsTab.VIDEOS
                            } else {
                                state.tab
                            }
                        )
                    },
                    { state = state.copy(loading = false) }
                )
        )
    }

    /** Shows the downloads in progress, and reloads the library when one finishes. */
    private fun pollRunning() {
        val missions = manager?.pendingMissions ?: return
        val running = missions.map { mission ->
            val total = runCatching { mission.getLength() }.getOrDefault(0L)
            RunningDownload(
                name = mission.storage?.name?.substringBeforeLast('.') ?: "",
                progress = if (total > 0) (mission.done.toFloat() / total).coerceIn(0f, 1f) else 0f,
                done = mission.done,
                total = total,
                failed = mission.errCode != DownloadMission.ERROR_NOTHING
            )
        }
        if (running.size < pendingCount) load()
        pendingCount = running.size
        if (running != state.running) state = state.copy(running = running)
    }

    private fun freeBytes(): Long = runCatching {
        StatFs(Environment.getExternalStorageDirectory().path).availableBytes
    }.getOrDefault(0L)

    private fun play(files: List<LocalFile>, index: Int, audioOnly: Boolean = false) {
        if (files.isEmpty()) return
        val queue = SinglePlayQueue(files.map { it.toInfoItem() }, index)
        if (audioOnly || files[index].isAudio) {
            NavigationHelper.playOnBackgroundPlayer(requireContext(), queue, true)
        } else {
            NavigationHelper.playOnMainPlayer(requireActivity() as AppCompatActivity, queue)
        }
    }

    private fun onFileAction(file: LocalFile, action: FileAction) {
        state = state.copy(menuFor = null)
        when (action) {
            FileAction.PLAY -> play(listOf(file), 0)

            FileAction.AUDIO_ONLY -> play(listOf(file), 0, audioOnly = true)

            FileAction.ADD_TO_QUEUE -> addToQueue(listOf(file))

            FileAction.OPEN_SOURCE -> file.sourceUrl?.let { url ->
                NavigationHelper.openVideoDetailFragment(
                    requireContext(),
                    parentFragmentManager,
                    file.sourceServiceId,
                    url,
                    file.title,
                    null,
                    false
                )
            }

            FileAction.SHARE -> share(listOf(file))

            FileAction.DELETE -> state = state.copy(confirmDelete = listOf(file))
        }
    }

    /** Asks which queue to add the files to: the one playing, a saved one or a new one. */
    private fun addToQueue(files: List<LocalFile>) {
        if (files.isEmpty()) return
        val context = requireContext()
        val store = QueueStore(context)
        val saved = store.load().queues.sortedByDescending { it.updated }
        val names = listOf(getString(R.string.downloads_queue_current)) +
            saved.map { it.name } + getString(R.string.downloads_queue_new)
        AlertDialog.Builder(context)
            .setTitle(R.string.downloads_to_queue)
            .setItems(names.toTypedArray()) { _, which ->
                val items = files.map { SavedItem.from(it.toInfoItem()) }
                when (which) {
                    0 -> NavigationHelper.enqueueOnPlayer(
                        context,
                        SinglePlayQueue(files.map { it.toInfoItem() }, 0)
                    )

                    names.lastIndex -> store.create(
                        getString(R.string.queues_default_name, saved.size + 1),
                        items
                    )

                    else -> store.append(saved[which - 1].id, items)
                }
                Toast.makeText(
                    context,
                    getString(R.string.downloads_added_to_queue, names[which]),
                    Toast.LENGTH_SHORT
                ).show()
                state = state.copy(selected = emptySet())
            }
            .show()
    }

    private fun share(files: List<LocalFile>) {
        if (files.isEmpty()) return
        val context = requireContext()
        val uris = ArrayList(files.mapNotNull { shareableUri(context, it) })
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.type = when {
            files.all { it.isAudio } -> "audio/*"
            files.none { it.isAudio } -> "video/*"
            else -> "*/*"
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, getString(R.string.share_dialog_title)))
        state = state.copy(selected = emptySet())
    }

    private fun shareableUri(context: Context, file: LocalFile): Uri? = runCatching {
        val uri = Uri.parse(file.url)
        if (uri.scheme == "file") {
            FileProvider.getUriForFile(
                context,
                BuildConfig.APPLICATION_ID + ".provider",
                File(URI.create(file.url))
            )
        } else {
            uri
        }
    }.getOrNull()

    private fun delete(files: List<LocalFile>) {
        val manager = manager ?: return
        val context = requireContext().applicationContext
        disposables.add(
            Single.fromCallable { files.count { manager.deleteFinishedMission(it.url) } }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { deleted ->
                        if (deleted > 0) {
                            Toast.makeText(context, R.string.downloads_deleted, Toast.LENGTH_SHORT)
                                .show()
                        }
                        state = state.copy(selected = emptySet())
                        load()
                    },
                    { load() }
                )
        )
    }

    companion object {
        private const val POLL_MILLIS = 1000L

        @JvmStatic
        fun open(fragmentManager: FragmentManager) {
            fragmentManager.beginTransaction()
                .replace(R.id.fragment_holder, DownloadsFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}
